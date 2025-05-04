/*
 * Tulip - XPath and XQuery Parser
 * Copyright (c) 2025 Evolved Binary
 *
 * Use of this software is governed by the Business Source License included
 * in the LICENSE file and at www.mariadb.com/bsl11.
 *
 * Change Date: 2028-03-23
 *
 * On the date above, in accordance with the Business Source License, use
 * of this software will be governed by the Apache License, Version 2.0.
 *
 * Additional Use Grant: None
 */
package com.evolvedbinary.tulip.lexer;

import com.evolvedbinary.tulip.source.Source;
import com.evolvedbinary.tulip.spec.XmlSpecification;
import com.evolvedbinary.tulip.trie.Trie;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import static com.evolvedbinary.tulip.constants.LexerConstants.BUFFER_SIZE;

/**
 * Base class for a lexer.
 */
abstract class AbstractLexer implements Lexer {

    private final Source source;
    private final int bufferSize;
    protected final XmlSpecification xmlSpecification;

    // --- Buffering ---
    // The two buffers used for reading data from the source.
    private final byte[] buffer1;
    private final byte[] buffer2;

    // Pointers to the currently active buffers for forward scanning and lexeme beginning.
    // These will point to either buffer1 or buffer2.
    @Nullable // Nullable only before constructor finishes
    protected byte[] forwardBuffer;
    @Nullable // Nullable only before constructor finishes
    protected byte[] beginBuffer;

    // --- Pointer Management ---
    // Pointers relative to the start of their respective buffers (forwardBuffer, beginBuffer).
    protected int lexemeBegin = 0; // Start position of the current lexeme within beginBuffer
    protected int forward = -1;    // Current read position within forwardBuffer

    // Absolute character position from the start of the input stream.
    private int lexemeBeginOriginal = 0;
    private int forwardOriginal = -1;

    // Offset of the start of the current beginBuffer/forwardBuffer from the input stream start.
    int beginOffset = 0;
    int forwardOffset = 0;


    // --- Token Pooling ---
    private final Deque<Token> freeTokens = new ArrayDeque<>();



    /**
     * Constructs the AbstractLexer.
     *
     * @param source           The source to read characters from.
     * @param bufferSize       The size of each internal buffer. Must be large enough to hold any single token.
     * @param xmlSpecification Rules for character classification (e.g., whitespace).
     * @throws IOException If an error occurs during initial buffer loading.
     */
    protected AbstractLexer(final Source source, final int bufferSize, final XmlSpecification xmlSpecification) throws IOException {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("Buffer size must be positive.");
        }
        this.source = source;
        this.bufferSize = bufferSize;
        this.xmlSpecification = xmlSpecification;

        // Allocate buffers
        this.buffer1 = new byte[bufferSize];
        this.buffer2 = new byte[bufferSize];

        // Initial load and setup
        loadBuffer(this.buffer1); // Load first chunk
        loadBuffer(this.buffer2); // Load second chunk (lookahead)

        // Initial buffer pointers
        this.beginBuffer = this.buffer1;
        this.forwardBuffer = this.buffer1;
    }

    // ========================================================================
    // Pointer Advancement and Management
    // ========================================================================

    /**
     * Advances the forward pointer one character, handling buffer switches if necessary.
     *
     * @throws IOException If an error occurs reading from the source.
     */
    protected void readNextChar() throws IOException {
        incrementForwardPointer(1);
//        System.out.println("Next character read is:" + forwardBuffer[forward]);
    }

    /**
     * Advances the forward pointer by a specified number of characters.
     * Use with caution, mainly intended for internal lookahead mechanisms.
     *
     * @param count The number of characters to advance.
     * @throws IOException If an error occurs reading from the source.
     */
    protected void readNextChars(final int count) throws IOException {
        if (count < 0) {
            throw new IllegalArgumentException("Count cannot be negative.");
        }
        incrementForwardPointer(count);
    }

    /**
     * Decrements the forward pointer by one position. Used for backtracking after lookahead.
     * Does not cross buffer boundaries backward (assumes lookahead is within current/next buffer).
     */
    public void decrementForward() {
        // TODO: Decrementing forward may push it to the begin buffer, this edge case hasn't been handled yet
        forward--;
        forwardOriginal--;
    }

    /**
     * Decrements the lexeme begin pointer by one position.
     * Primarily used internally, e.g., during whitespace skipping setup.
     */
    public void decrementBegin() {
        lexemeBegin--;
        lexemeBeginOriginal--;
    }

    /**
     * Core logic to advance the forward pointer, handling buffer switches.
     *
     * @param count Number of positions to advance.
     */
    private void incrementForwardPointer(int count) {
        if(forward+count >= bufferSize) { // Assumption being that the count wouldn't exceed the bufferSize itself
            forwardOffset += bufferSize;
            switchForwardBuffer();
        }
        forwardOriginal += count;
        forward = forwardOriginal - forwardOffset;
    }

    /**
     * Switches the `forwardBuffer` to the other buffer.
     * Assumes the other buffer was pre-loaded by `switchBeginBuffer`.
     */
    private void switchForwardBuffer() {
        if(forwardBuffer==buffer1) {
            forwardBuffer = buffer2;
        } else {
            forwardBuffer = buffer1;
        }
    }

    /**
     * Might have utility in future
     *
     * @param count Number of positions to advance.
     */
    private void incrementBeginPointer(int count) throws IOException {
        if(lexemeBegin+count>=bufferSize) {
            beginOffset += bufferSize;
            switchBeginBuffer();
        }
        lexemeBeginOriginal += count;
        lexemeBegin = lexemeBeginOriginal - beginOffset;
    }

    /**
     * Resets the `lexemeBegin` pointer to match the position *after* the `forward` pointer.
     * Typically called after a token is recognized or whitespace is skipped.
     * Handles switching the `beginBuffer` and loading new data if `forward` has
     * moved into the next buffer.
     *
     * @throws IOException If an error occurs loading data into the buffer.
     */
    void resetLexemeBegin() throws IOException {
        // If forward pointer has moved into the next buffer compared to begin pointer
        if(forwardOffset>beginOffset) {
            switchBeginBuffer(); // This loads data into the now inactive buffer
            beginOffset = forwardOffset;
        }
        lexemeBeginOriginal = forwardOriginal + 1;
        lexemeBegin = lexemeBeginOriginal - beginOffset;
    }

    /**
     * Switches the `beginBuffer` to the other buffer and triggers loading
     * data into the buffer that `beginBuffer` previously pointed to.
     *
     * @throws IOException If an error occurs during buffer loading.
     */
    private void switchBeginBuffer() throws IOException {
        if(beginBuffer==buffer1) {
            beginBuffer=buffer2;
            loadBuffer(buffer1);
        } else {
            beginBuffer=buffer1;
            loadBuffer(buffer2);
        }
    }

    /**
     * Loads data from the source into the specified buffer.
     * Marks the end of the stream within the buffer using -1 if a partial read occurs.
     *
     * @param buffer The byte buffer to load data into.
     * @throws IOException If an I/O error occurs reading from the source.
     */
    private void loadBuffer(final byte[] buffer) throws IOException {
        // TODO: Consider making this asynchronous using executorService if performance critical.
        // Current implementation is synchronous.
        if (source == null) {
            throw new IllegalStateException("Source is null");
        }
        int bytesRead = source.read(buffer);

        if(bytesRead != BUFFER_SIZE && bytesRead>=0) {
            buffer[bytesRead] = -1; // Use -1 to indicate EOF or end of valid data in the buffer
        }
    }

    // ========================================================================
    // Lexeme Extraction - Currently not in use
    // ========================================================================

    /**
     * Extracts the bytes of the current lexeme (from lexemeBegin to forward).
     * Handles cases where the lexeme spans across the two buffers.
     *
     * @return A byte array containing the lexeme data. Returns an empty array if pointers are invalid.
     */
    private byte[] getCurrentLexemeBytes() {
        int lexemeLength = (int) (forwardOriginal - lexemeBeginOriginal + 1);
        byte[] lexeme = new byte[lexemeLength];
        int lexemeIndex = 0;

        if (beginOffset == forwardOffset) {
            // Lexeme is entirely within a single buffer (the current forwardBuffer/beginBuffer)
            for (int i = lexemeBegin; i <= forward; i++) {
                if(i >= beginBuffer.length) break;
                lexeme[lexemeIndex++] = beginBuffer[i];
            }
        } else {
            // Lexeme spans across beginBuffer and forwardBuffer
            for (int i = lexemeBegin; i < bufferSize; i++) {
                if (lexemeIndex >= lexemeLength) break;
                if(i >= beginBuffer.length) break;
                lexeme[lexemeIndex++] = beginBuffer[i];
            }

            for (int i = 0; i <= forward; i++) {
                if (lexemeIndex >= lexemeLength) break;
                if(i >= forwardBuffer.length) break;
                lexeme[lexemeIndex++] = forwardBuffer[i];
            }
        }

        return lexeme;
    }

    /**
     * Populates a pre-allocated byte array with the current lexeme's content.
     * Note: Caller must ensure the provided array `lexeme` is large enough.
     * Consider using {@link #getCurrentLexeme()} for safer string extraction.
     *
     * @param lexeme The byte array to populate.
     */
    public void populateLexeme(byte[] lexeme) {
        byte[] currentLexemeBytes = getCurrentLexemeBytes();
        System.arraycopy(currentLexemeBytes, 0, lexeme, 0, Math.min(lexeme.length, currentLexemeBytes.length));
        // Consider warning or error if provided lexeme array is too small.
    }

    /**
     * Gets the current lexeme as a String.
     * Handles cases where the lexeme spans across the two buffers.
     *
     * @return The String representation of the current lexeme.
     */
    public String getCurrentLexeme() {
        return new String(getCurrentLexemeBytes());
    }

    /**
     * Builds and populates the Trie with XPath 1.0 keywords.
     *
     * @return The populated Trie.
     */
    public static Trie buildKeywordTrie() {
        Trie trie = new Trie();

        // --- Identifier Names ---
        List<String> identifier = Arrays.asList(
                "=>", "eq", "ne", "lt",
                "le", "gt", "ge",
                "is", "<<", ">>",
                ":="
        );
        for (String axis : identifier) {
            trie.insert(axis, TokenType.IDENTIFIER);
        }

        // --- Operator Names ---
        trie.insert("=>", TokenType.ARROW);
        trie.insert("<<", TokenType.NODE_BEFORE);
        trie.insert(">>", TokenType.NODE_AFTER);
        trie.insert(":=", TokenType.NAMESPACE_SEPARATOR);


        // --- Axis Names ---
        List<String> axisNames = Arrays.asList(
                "ancestor", "ancestor-or-self", "attribute", "child",
                "descendant", "descendant-or-self", "following",
                "following-sibling", "namespace", "parent",
                "preceding", "preceding-sibling", "self"
        );
        for (String axis : axisNames) {
            trie.insert(axis, TokenType.AXIS_NAME);
        }

        // --- Function Names ---
        List<String> functionNames = Arrays.asList(
                // Node set functions
                "last", "position", "count", "id", "local-name",
                "namespace-uri", "name",
                // String functions
                "string", "concat", "starts-with", "contains",
                "substring-before", "substring-after", "substring",
                "string-length", "normalize-space", "translate",
                // Boolean functions
                "boolean", "not", "true", "false", "lang",
                // Number functions
                "number", "sum", "floor", "ceiling", "round",
                // Node Test Functions
                "node", "text", "comment", "processing-instruction"
        );
        for (String function : functionNames) {
            trie.insert(function, TokenType.FUNCTION);
        }

        // --- Arithmetic Operator Names ---
        trie.insert("and", TokenType.AND);
        trie.insert("or", TokenType.OR);
        trie.insert("div", TokenType.DIV);
        trie.insert("mod", TokenType.MOD);

        // --- XPath 2.0 Keywords ---
        trie.insert("instance", TokenType.INSTANCE_OF);
        trie.insert("castable", TokenType.CASTABLE);
        trie.insert("of", TokenType.OF);
        trie.insert("cast", TokenType.CAST);
        trie.insert("as", TokenType.AS);
        trie.insert("treat", TokenType.TREAT);
        trie.insert("return", TokenType.RETURN);
        trie.insert("for", TokenType.FOR);
        trie.insert("in", TokenType.IN);
        trie.insert("some", TokenType.SOME);
        trie.insert("every", TokenType.EVERY);
        trie.insert("if", TokenType.IF);
        trie.insert("then", TokenType.THEN);
        trie.insert("else", TokenType.ELSE);
        trie.insert("typeswitch", TokenType.TYPESWITCH);
        trie.insert("case", TokenType.CASE);
        trie.insert("default", TokenType.DEFAULT);
        trie.insert("at", TokenType.AT);
        trie.insert("where", TokenType.WHERE);
        trie.insert("order", TokenType.ORDER);
        trie.insert("by", TokenType.BY);
        trie.insert("ascending", TokenType.ASCENDING);
        trie.insert("descending", TokenType.DESCENDING);
        trie.insert("stable", TokenType.STABLE);
        trie.insert("union", TokenType.UNION);
        trie.insert("intersect", TokenType.INTERSECT);
        trie.insert("except", TokenType.EXCEPT);
        trie.insert("to", TokenType.TO);
        trie.insert("satisfies", TokenType.SATISFIES);
        trie.insert("collation", TokenType.COLLATION);
        trie.insert("import", TokenType.IMPORT);
        trie.insert("schema", TokenType.SCHEMA);
        trie.insert("module", TokenType.MODULE);
        trie.insert("preserve", TokenType.PRESERVE);
        trie.insert("strip", TokenType.STRIP);
        trie.insert("copy-of", TokenType.COPY_OF);
        trie.insert("deep-equal", TokenType.DEEP_EQUAL);
        trie.insert("exactly-one", TokenType.EXACTLY_ONE);
        trie.insert("zero-or-one", TokenType.ZERO_OR_ONE);
        trie.insert("one-or-more", TokenType.ONE_OR_MORE);

        // System.out.println("Trie has been created and populated"); // Keep commented out or remove for production
        return trie;
    }

    /**
     * Handles single and multi-character operators and punctuation.
     *
     * @param firstByte The first byte of the potential operator/punctuation.
     * @return The corresponding TokenType.
     * @throws IOException If an I/O error occurs or an invalid sequence is found (e.g., '!' not followed by '=').
     */
    public TokenType handleOperatorOrPunctuation(final byte firstByte) throws IOException {
        switch (firstByte) {
            case SLASH:
                readNextChar();
                if (forwardBuffer[forward] == SLASH) {
                    return TokenType.DOUBLE_SLASH; // '//'
                } else {
                    decrementForward(); // Backtrack, it's just '/'
                    return TokenType.SLASH;
                }
            case PLUS:
                return TokenType.PLUS;
            case MINUS:
                return TokenType.MINUS;
            case MULTIPLY_OPERATOR:
                return TokenType.MULTIPLY_OPERATOR;
            case EQUALS:
                readNextChar();
                if(forwardBuffer[forward] == GREATER_THAN) {
                    return TokenType.ARROW;
                }
                decrementForward();
                return TokenType.EQUAL_TO;
            case LPAREN:
                return TokenType.LPAREN;
            case RPAREN:
                return TokenType.RPAREN;
            case LBRACKET:
                return TokenType.LBRACKET;
            case RBRACKET:
                return TokenType.RBRACKET;
            case AT_OPERATOR:
                return TokenType.AT_OPERATOR;
            case COMMA:
                return TokenType.COMMA;
            case UNION_OPERATOR:
                readNextChar();
                if(forwardBuffer[forward] == UNION_OPERATOR) {
                    return TokenType.CONCAT_OPERATOR;
                }
                decrementForward();
                return TokenType.UNION_OPERATOR;
            case LBRACE:
                return TokenType.OPEN_BRACE;
            case RBRACE:
                return TokenType.CLOSE_BRACE;
            case SEMICOLON:
                return TokenType.SEMICOLON;
            case NOT:
                readNextChar();
                if (forwardBuffer[forward] == EQUALS) {
                    return TokenType.NOT_EQUAL_TO; // '!='
                } else {
                    throw new IOException("Invalid character sequence: '!' must be followed by '=' in XPath 1.0 for '!=' operator.");
                }
            case GREATER_THAN:
                readNextChar();
                if (forwardBuffer[forward] == EQUALS) {
                    return TokenType.GREATER_THAN_EQUAL_TO; // '>='
                } else if(forwardBuffer[forward] == GREATER_THAN) {
                    return TokenType.NODE_AFTER;
                } else {
                    decrementForward(); // Backtrack, it's just '>'
                    return TokenType.GREATER_THAN;
                }
            case LESS_THAN:
                readNextChar();
                if (forwardBuffer[forward] == EQUALS) {
                    return TokenType.LESS_THAN_EQUAL_TO; // '<='
                } else if(forwardBuffer[forward] == LESS_THAN) {
                    return TokenType.NODE_BEFORE;
                } else {
                    decrementForward(); // Backtrack, it's just '<'
                    return TokenType.LESS_THAN;
                }
            case COLON:
                readNextChar();
                if (forwardBuffer[forward] == COLON) {
                    return TokenType.AXIS_SEPARATOR; // '::'
                } else if(forwardBuffer[forward] == EQUALS) {
                    return TokenType.NAMESPACE_SEPARATOR;
                } else {
                    // Backtrack and return forward
                    decrementForward();
                    return TokenType.COLON;
                }
            default:
                // If none of the known characters match, it's an unexpected character.
                throw new IOException("Unexpected character encountered: " + (char) firstByte + " (byte value: " + firstByte + ")");
        }
    }


    // Resource management
    @Override
    public void close() {
        freeTokens.clear();
    }

    // ========================================================================
    // Token Pooling
    // ========================================================================

    /**
     * Retrieves a reusable Token object from the pool or creates a new one if the pool is empty.
     *
     * @return A Token object ready for population.
     */
    protected Token getFreeToken() {
        @Nullable Token freeToken = freeTokens.peek(); // Either use an already existing object
        if (freeToken == null) { // Or create a new one if not present
            freeToken = new Token(this);
        }
        return freeToken;
    }

    /**
     * Provide a token for reuse by the lexer.
     *
     * @param freeToken a token that is free and can be reused.
     */
    void reuseToken(final Token freeToken) {
        freeTokens.push(freeToken);
    }


    // ========================================================================
    // Constants (Moved from original location for better grouping)
    // ========================================================================

    protected static final byte QUOTATION_MARK = 0x22;
    protected static final byte APOSTROPHE     = 0x27;
    protected static final byte ZERO           = 0x30;
    protected static final byte NINE           = 0x39;
    protected static final byte LOWERCASE_A    = 0x61;
    protected static final byte LOWERCASE_Z    = 0x7A;
    protected static final byte UPPERCASE_A    = 0x41;
    protected static final byte UPPERCASE_Z    = 0x5A;

    // Arithmetic Operators
    protected static final byte PLUS        = 0x2B; // '+'
    protected static final byte MINUS       = 0x2D; // '-'
    protected static final byte UNDERSCORE       = 0x5F; // '-'
    protected static final byte MULTIPLY_OPERATOR    = 0x2A; // '*'

    // Comparison Operators
    protected static final byte EQUALS      = 0x3D; // '='
    protected static final byte NOT   = 0x21; // '!' (for "!=")
    protected static final byte LESS_THAN   = 0x3C; // '<'
    protected static final byte GREATER_THAN = 0x3E; // '>'


    // Path Operators
    protected static final byte SLASH       = 0x2F; // '/'
    protected static final byte FULL_STOP   = 0x2E;


    // Parentheses & Other Symbols
    protected static final byte LPAREN      = 0x28; // '('
    protected static final byte RPAREN      = 0x29; // ')'
    protected static final byte LBRACKET    = 0x5B; // '['
    protected static final byte RBRACKET    = 0x5D; // ']'
    protected static final byte AT_OPERATOR = 0x40; // '@'
    protected static final byte COMMA       = 0x2C; // ','
    protected static final byte UNION_OPERATOR = 0x7C; // '|'
    protected static final byte COLON          = 0x3A; // ':'
    protected static final byte DOLLAR      = 0x24; // '-'
    protected static final byte LOWERCASE_E          = 0x65; // 'e'
    protected static final byte UPPERCASE_E      = 0x45; // 'E'
    protected static final byte LBRACE           = 0x7B; // '{'
    protected static final byte RBRACE           = 0x7D; // '}'
    protected static final byte SEMICOLON        = 0x3B; // ';'

}
