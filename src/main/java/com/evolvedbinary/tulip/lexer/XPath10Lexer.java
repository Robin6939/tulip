package com.evolvedbinary.tulip.lexer;

import com.evolvedbinary.tulip.source.Source;
import com.evolvedbinary.tulip.spec.XmlSpecification;
import com.evolvedbinary.tulip.trie.Trie;
import com.evolvedbinary.tulip.trie.TrieNode;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * A lexer for tokenizing XPath 1.0 expressions.
 *
 * It reads characters from a {@link Source} and produces a stream of {@link Token} objects.
 * It utilizes a Trie for efficient recognition of keywords (Axis Names and Function Names).
 */
public class XPath10Lexer extends AbstractLexer {

    // Trie for efficient keyword lookup (Axis Names and Function Names)
    private static final Trie keywordTrie = buildKeywordTrie();

    /**
     * Builds and populates the Trie with XPath 1.0 keywords.
     *
     * @return The populated Trie.
     */
    private static Trie buildKeywordTrie() {
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
     * Constructs an XPath10Lexer.
     *
     * @param source           The source of characters.
     * @param bufferSize       The size of the internal buffer.
     * @param xmlSpecification The XML specification rules (e.g., for whitespace).
     * @throws IOException If an I/O error occurs during initial read.
     */
    public XPath10Lexer(final Source source, final int bufferSize, final XmlSpecification xmlSpecification) throws IOException {
        super(source, bufferSize, xmlSpecification);
    }

    /**
     * Retrieves the next token from the input source.
     * Skips whitespace before identifying the token.
     *
     * @return The next {@link Token}, or a Token with {@link TokenType#EOF} if the end of the source is reached.
     * @throws IOException If an I/O error occurs during reading or if an invalid sequence is encountered.
     */
    @Override
    public Token next() throws IOException {
        resetLexemeBegin(); // Begin to be set ahead of forward
        skipWhitespaceAndResetBegin();

        final byte firstByte = forwardBuffer[forward];
        TokenType tokenType;

        if (firstByte == -1) {
            tokenType = TokenType.EOF;
        } else if (isLetter(firstByte)) {
            tokenType = handleIdentifierOrKeyword();
        } else if (firstByte == UNDERSCORE) {
            tokenType = handleIdentifierStartingWithUnderscore();
        } else if (isDigit(firstByte)) {
            tokenType = handleNumberStartingWithDigit();
        } else if (firstByte == FULL_STOP) {
            tokenType = handleDotOrNumberStartingWithDot();
        } else if(firstByte == MINUS) {
            tokenType = handleNegativeNumbers();
        }else if (firstByte == QUOTATION_MARK || firstByte == APOSTROPHE) {
            tokenType = handleLiteral(firstByte);
        } else {
            // Handle operators and punctuation
            tokenType = handleOperatorOrPunctuation(firstByte);
        }

        // Use try-with-resources assuming Token implements AutoCloseable for pooling/cleanup
        try (Token token = getFreeToken()) {
            token.tokenType = tokenType;
            token.forwardBuffer = forwardBuffer;
            token.beginBuffer = beginBuffer;
            token.forward = forward;
            token.lexemeBegin = lexemeBegin;
            token.beginOffset = beginOffset;
            token.forwardOffset = forwardOffset;
            return token;
        } catch (Exception e) {
            System.err.println("Error creating or finalizing token: " + e.getMessage());
            throw new IOException("Failed to process token", e);
        }
    }

    /**
     * Skips XML whitespace characters and resets the lexeme beginning pointer.
     *
     * @throws IOException If an I/O error occurs.
     */
    private void skipWhitespaceAndResetBegin() throws IOException {
        readNextChar();
        byte currentByte = forwardBuffer[forward];
        while (xmlSpecification.isWhiteSpace(currentByte)) {
            readNextChar();
            currentByte = forwardBuffer[forward];
            resetLexemeBegin(); // Reset lexeme start to the beginning of the *next* potential token
            decrementBegin(); // Move begin back to match forward before potential token start
        }
    }

    /**
     * Handles tokens starting with a letter, which could be an identifier,
     * an axis name, or a function name. Uses the Trie for keyword detection.
     *
     * @return The determined TokenType (IDENTIFIER, AXIS_NAME, or FUNCTION,or Arithmetic Keyword).
     * @throws IOException If an I/O error occurs.
     */
    private TokenType handleIdentifierOrKeyword() throws IOException {
        TrieNode node = keywordTrie.getRoot();
        node = keywordTrie.traverse(forwardBuffer[forward], node); // Traverse the first letter

        while (true) {
            readNextChar();
            byte currentByte = forwardBuffer[forward];

            if (isLetter(currentByte) || currentByte == MINUS || currentByte == FULL_STOP || isDigit(currentByte)) {
                // Continue traversing if it's a letter or could be an identifier
                if (node != null) {
                    node = keywordTrie.traverse(currentByte, node);
                }
            } else {
                // Not a letter or valid hyphen sequence, end of potential identifier/keyword
                break;
            }
        }
        decrementForward(); // Backtrack to the last token
        // Determine token type based on final Trie node state
        if(node!=null && node.isKeyword)
            return node.tokenType;
        return TokenType.IDENTIFIER;
    }

    TokenType handleNCNameorQNameorFunctionNameorAxisNameorKeyword() throws IOException {
        TrieNode node = keywordTrie.getRoot();
        node = keywordTrie.traverse(forwardBuffer[forward], node); // Traverse the first letter
        int colonCount = 0;
        while (true) {
            readNextChar();
            byte currentByte = forwardBuffer[forward];



            if (isLetter(currentByte) || currentByte == MINUS || currentByte == FULL_STOP || isDigit(currentByte) || currentByte == COLON || currentByte == UNDERSCORE || currentByte == EQUALS || currentByte == GREATER_THAN || currentByte == LESS_THAN) {
                // Continue traversing if it's a letter or could be an identifier
                if(currentByte == COLON) {
                    readNextChar();
                    if(forwardBuffer[forward] == COLON) { // Needs to be done so as to nto confuse an axis seperator with QName
                        decrementForward();
                        break;
                    }
                    decrementForward();
                    colonCount++;
                }
                if (node != null) {
                    node = keywordTrie.traverse(currentByte, node);
                }

            } else {
                // Not a letter or valid hyphen sequence, end of potential identifier/keyword
                break;
            }
        }
        decrementForward(); // Backtrack to the last token
        if(colonCount>1) {
            throw new IOException("Invalid Identifier");
        }
        if(node != null && node.isKeyword) {
            return node.tokenType;
        }
        return colonCount==1?TokenType.QName:TokenType.NCName; // Matched prefix or not a keyword
    }

    /**
     * Handles tokens starting with a underscore, which could be an identifier,
     * calls upon handleIdentifierOrKeyword() after reading underscore
     *
     * @return The determined TokenType (IDENTIFIER, AXIS_NAME, or FUNCTION, or Arithmetic Keyword).
     * @throws IOException If an I/O error occurs.
     */
    public TokenType handleIdentifierStartingWithUnderscore() throws IOException {
        readNextChar();
        if (isLetter(forwardBuffer[forward])) {
            return handleIdentifierOrKeyword();
        } else {
            throw new IOException("Underscore should be followed by a letter for it to be identified as a proper identifier in XPath 1.0");
        }
    }

    /**
     * Handles tokens starting with a digit (Integer or Decimal).
     *
     * @return TokenType.DIGITS (representing a number literal).
     * @throws IOException If an I/O error occurs.
     */
    TokenType handleNumberStartingWithDigit() throws IOException {
        TokenType tokenType = TokenType.DIGITS;
        // Consume initial sequence of digits
        do {
            readNextChar();
        } while (isDigit(forwardBuffer[forward]));

        // Check for a decimal part
        if (forwardBuffer[forward] == FULL_STOP) {
            tokenType = TokenType.NUMBER; // If dot is present then TokenType is NUMBER
            readNextChar(); // Consume the dot
            // Consume digits after the dot
            if(isDigit(forwardBuffer[forward])) {
                while (isDigit(forwardBuffer[forward])) {
                    readNextChar();
                }
            } else {
                decrementForward();
            }
        }

        // Backtrack to the last character belonging to the number
        decrementForward();
        return tokenType;
    }

    /**
     * Handles tokens starting with a FULL_STOP (.), which could be the
     * current node context selector (.), the parent step (..), or a
     * decimal number starting with a dot (e.g., .5).
     *
     * @return The determined TokenType (CURRENT_AXIS, PARENT_AXIS, or NUMBER).
     * @throws IOException If an I/O error occurs.
     */
    private TokenType handleDotOrNumberStartingWithDot() throws IOException {
        readNextChar(); // Consume the first dot

        if (isDigit(forwardBuffer[forward])) {
            // It's a number starting with a dot (e.g., .5)
            do {
                readNextChar();
            } while (isDigit(forwardBuffer[forward]));
            decrementForward(); // Backtrack to the last digit
            return TokenType.NUMBER;
        } else if (forwardBuffer[forward] == FULL_STOP) {
            // It's the parent axis operator '..'
            return TokenType.PARENT_AXIS;
        } else {
            // It's the current node selector '.'
            decrementForward(); // Backtrack as we only consumed the first dot
            return TokenType.CURRENT_AXIS;
        }
    }

    /**
     * Handles tokens starting with a minus, which could be a number or digit,
     *
     * @return The determined TokenType (MINUS).
     * @throws IOException If an I/O error occurs.
     */
    private TokenType handleNegativeNumbers() throws IOException {
        return TokenType.MINUS;
    }

    /**
     * Handles literal strings enclosed in single or double quotes.
     *
     * @param quoteByte The starting quote character (APOSTROPHE or QUOTATION_MARK).
     * @return TokenType.LITERAL.
     * @throws IOException If an I/O error occurs or the closing quote is not found.
     */
    public TokenType handleLiteral(final byte quoteByte) throws IOException {
        do {
            readNextChar();
            // Check for EOF before finding the closing quote
            if (forwardBuffer[forward] == -1) {
                throw new IOException("Unterminated literal string");
            }
        } while (forwardBuffer[forward] != quoteByte);
        return TokenType.LITERAL;
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


    /**
     * Checks if a byte represents an ASCII digit ('0'-'9').
     *
     * @param b The byte to check.
     * @return True if the byte is a digit, false otherwise.
     */
    private boolean isDigit(final byte b) {
        return b >= ZERO && b <= NINE; // Assuming ZERO and NINE constants exist
    }

    /**
     * Checks if a byte represents an ASCII letter ('a'-'z' or 'A'-'Z').
     * Note: XPath 1.0 NCName rules are more complex (allow '_', non-ASCII letters),
     * but this lexer seems to only handle basic ASCII letters based on the original code.
     *
     * @param b The byte to check.
     * @return True if the byte is an ASCII letter, false otherwise.
     */
    private boolean isLetter(final byte b) {
        return (b >= LOWERCASE_A && b <= LOWERCASE_Z) || (b >= UPPERCASE_A && b <= UPPERCASE_Z);
    }
}