package com.evolvedbinary.tulip.lexer;

import com.evolvedbinary.tulip.source.Source;
import com.evolvedbinary.tulip.spec.XmlSpecification;
import com.evolvedbinary.tulip.trie.Trie;
import com.evolvedbinary.tulip.trie.TrieNode;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class XPath20Lexer extends AbstractLexer {

    private static final Trie keywordTrie = buildKeywordTrie();

    private static Trie buildKeywordTrie() {
        Trie trie = new Trie();

        // --- Axis Names (XPath 1.0 and 2.0) ---
        List<String> axisNames = Arrays.asList(
                "ancestor", "ancestor-or-self", "attribute", "child",
                "descendant", "descendant-or-self", "following",
                "following-sibling", "namespace", "parent",
                "preceding", "preceding-sibling", "self"
        );
        for (String axis : axisNames) {
            trie.insert(axis, false, true);
        }

        // --- Function Names (XPath 1.0 and some from 2.0) ---
        List<String> functionNames = Arrays.asList(
                // Node set functions
                "last", "position", "count", "id", "local-name",
                "namespace-uri", "name",
                // String functions
                "string", "concat", "starts-with", "contains",
                "substring-before", "substring-after", "substring",
                "string-length", "normalize-space", "translate",
                "upper-case", "lower-case", "string-join",
                "encode-for-uri", "iri-to-uri", "escape-uri",
                "tokenize",
                // Boolean functions
                "boolean", "not", "true", "false", "lang",
                // Number functions
                "number", "sum", "floor", "ceiling", "round",
                "abs", "avg", "max", "min",
                // Date and Time functions (basic support)
                "current-date", "current-time", "current-dateTime",
                "date", "time", "dateTime",
                // Node Test Functions
                "node", "text", "comment", "processing-instruction",
                "document-node", "element", "schema-attribute", "schema-element",
                "attribute"
        );
        for (String function : functionNames) {
            trie.insert(function, true, false);
        }

        // --- Other Keywords (XPath 2.0 specific) ---
        List<String> otherKeywords = Arrays.asList(
                "instance", "of", "cast", "as", "treat", "return", "for", "in",
                "some", "every", "if", "then", "else", "typeswitch", "case",
                "default", "as", "at", "where", "order", "by", "ascending",
                "descending", "stable", "union", "intersect", "except", "to",
                "satisfies", "collation", "import", "schema", "module",
                "namespace", "preserve", "strip", "copy-of", "deep-equal",
                "exactly-one", "zero-or-one", "one-or-more"
        );
        for (String keyword : otherKeywords) {
            trie.insert(keyword, false, false);
        }

        // --- Arithmetic Operator Names ---
        List<String> arithmeticOperatorNames = Arrays.asList(
                "and", "or", "div", "mod"
        );
        for (String op : arithmeticOperatorNames) {
            trie.insert(op, false, false);
        }

        return trie;
    }

    public XPath20Lexer(final Source source, final int bufferSize, final XmlSpecification xmlSpecification) throws IOException {
        super(source, bufferSize, xmlSpecification);
    }

    @Override
    public Token next() throws IOException {
        resetLexemeBegin();
        skipWhitespaceAndCommentsAndResetBegin();

        final byte firstByte = forwardBuffer[forward];
        TokenType tokenType;

        if (firstByte == -1) {
            tokenType = TokenType.EOF;
        } else if (firstByte == DOLLAR) {
            tokenType = handleVariableReference();
        } else if (isLetter(firstByte) || firstByte == UNDERSCORE) {
            tokenType = handleIdentifierOrKeywordOrQName();
        } else if (isDigit(firstByte)) {
            tokenType = handleNumberStartingWithDigit();
        } else if (firstByte == FULL_STOP) {
            tokenType = handleDotOrNumberStartingWithDot();
        } else if (firstByte == MINUS) {
            tokenType = handleNegativeNumbers();
        } else if (firstByte == QUOTATION_MARK || firstByte == APOSTROPHE) {
            tokenType = handleLiteral(firstByte);
        } else if (firstByte == LPAREN && forwardBuffer[forward + 1] == COLON) {
            tokenType = handleComment();
        } else {
            tokenType = handleOperatorOrPunctuation(firstByte);
        }

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

    private void skipWhitespaceAndCommentsAndResetBegin() throws IOException {
        readNextChar();
        byte currentByte = forwardBuffer[forward];
        while (xmlSpecification.isWhiteSpace(currentByte) || (currentByte == LPAREN && forwardBuffer[forward + 1] == COLON)) {
            if (xmlSpecification.isWhiteSpace(currentByte)) {
                readNextChar();
                currentByte = forwardBuffer[forward];
                resetLexemeBegin();
                decrementBegin();
            } else if (currentByte == LPAREN && forwardBuffer[forward + 1] == COLON) {
                handleComment();
                readNextChar();
                currentByte = forwardBuffer[forward];
                resetLexemeBegin();
                decrementBegin();
            }
        }
    }

    private TokenType handleComment() throws IOException {
        readNextChar(); // Consume '('
        readNextChar(); // Consume ':'
        int nestingLevel = 1;
        while (true) {
            readNextChar();
            if (forwardBuffer[forward] == -1) {
                throw new IOException("Unterminated comment");
            }
            if (forwardBuffer[forward] == LPAREN && forwardBuffer[forward + 1] == COLON) {
                readNextChar(); // Consume '('
                readNextChar(); // Consume ':'
                nestingLevel++;
            } else if (forwardBuffer[forward] == COLON && forwardBuffer[forward + 1] == RPAREN) {
                readNextChar(); // Consume ':'
                readNextChar(); // Consume ')'
                nestingLevel--;
                if (nestingLevel == 0) {
                    return TokenType.COMMENT;
                }
            }
        }
    }

    private TokenType handleVariableReference() throws IOException {
        readNextChar(); // Consume '$'
        if (isLetter(forwardBuffer[forward]) || forwardBuffer[forward] == UNDERSCORE) {
            return handleIdentifierOrKeywordOrQName();
        } else {
            throw new IOException("Invalid variable name");
        }
    }

    private TokenType handleIdentifierOrKeywordOrQName() throws IOException {
        TrieNode node = keywordTrie.getRoot();
        int initialForward = forward;
        int identifierEnd = forward;

        while (true) {
            byte currentByte = forwardBuffer[forward];
            if (isLetter(currentByte) || isDigit(currentByte) || currentByte == UNDERSCORE || currentByte == MINUS || currentByte == FULL_STOP) {
                if (node != null) {
                    node = keywordTrie.traverse(currentByte, node);
                }
                readNextChar();
                identifierEnd = forward;
            } else if (currentByte == COLON && forwardBuffer[forward + 1] != COLON) {
                // Potential QName
                readNextChar(); // Consume ':'
                if (isLetter(forwardBuffer[forward]) || forwardBuffer[forward] == UNDERSCORE) {
                    // Continue reading the local part of the QName
                    while (true) {
                        byte nextByte = forwardBuffer[forward];
                        if (isLetter(nextByte) || isDigit(nextByte) || nextByte == UNDERSCORE || nextByte == MINUS || nextByte == FULL_STOP) {
                            readNextChar();
                        } else {
                            decrementForward(); // Backtrack the last read char
                            return TokenType.QNAME;
                        }
                    }
                } else {
                    decrementForward(); // Backtrack the ':'
                    break;
                }
            } else {
                break;
            }
        }
        decrementForward(); // Backtrack the last read char

        String lexeme = new String(beginBuffer, lexemeBegin, identifierEnd - lexemeBegin);
        TrieNode exactNode = keywordTrie.lookup(lexeme);

        if (exactNode != null && exactNode.isFunction) {
            return TokenType.FUNCTION;
        } else if (exactNode != null && exactNode.isAxis) {
            return TokenType.AXIS_NAME;
        } else if (exactNode != null && exactNode.isKeyword) {
            switch (lexeme) {
                case "and": return TokenType.AND;
                case "or": return TokenType.OR;
                case "div": return TokenType.DIV;
                case "mod": return TokenType.MOD;
                case "instance": return TokenType.INSTANCE_OF;
                case "of": return TokenType.OF;
                case "cast": return TokenType.CAST;
                case "as": return TokenType.AS;
                case "treat": return TokenType.TREAT;
                case "return": return TokenType.RETURN;
                case "for": return TokenType.FOR;
                case "in": return TokenType.IN;
                case "some": return TokenType.SOME;
                case "every": return TokenType.EVERY;
                case "if": return TokenType.IF;
                case "then": return TokenType.THEN;
                case "else": return TokenType.ELSE;
                case "typeswitch": return TokenType.TYPESWITCH;
                case "case": return TokenType.CASE;
                case "default": return TokenType.DEFAULT;
                case "at": return TokenType.AT;
                case "where": return TokenType.WHERE;
                case "order": return TokenType.ORDER;
                case "by": return TokenType.BY;
                case "ascending": return TokenType.ASCENDING;
                case "descending": return TokenType.DESCENDING;
                case "stable": return TokenType.STABLE;
                case "union": return TokenType.UNION;
                case "intersect": return TokenType.INTERSECT;
                case "except": return TokenType.EXCEPT;
                case "to": return TokenType.TO;
                case "satisfies": return TokenType.SATISFIES;
                case "collation": return TokenType.COLLATION;
                case "import": return TokenType.IMPORT;
                case "schema": return TokenType.SCHEMA;
                case "module": return TokenType.MODULE;
                case "namespace": return TokenType.NAMESPACE;
                case "preserve": return TokenType.PRESERVE;
                case "strip": return TokenType.STRIP;
                case "copy-of": return TokenType.COPY_OF;
                case "deep-equal": return TokenType.DEEP_EQUAL;
                case "exactly-one": return TokenType.EXACTLY_ONE;
                case "zero-or-one": return TokenType.ZERO_OR_ONE;
                case "one-or-more": return TokenType.ONE_OR_MORE;
                default: return TokenType.IDENTIFIER;
            }
        } else {
            return TokenType.IDENTIFIER;
        }
    }

    private TokenType handleNumberStartingWithDigit() throws IOException {
        TokenType tokenType = TokenType.DIGITS;
        boolean hasDecimal = false;
        boolean hasExponent = false;

        do {
            readNextChar();
        } while (isDigit(forwardBuffer[forward]));

        if (forwardBuffer[forward] == FULL_STOP) {
            tokenType = TokenType.DECIMAL;
            hasDecimal = true;
            readNextChar();
            while (isDigit(forwardBuffer[forward])) {
                readNextChar();
            }
        }

        if (forwardBuffer[forward] == LOWERCASE_E || forwardBuffer[forward] == UPPERCASE_E) {
            tokenType = TokenType.DOUBLE;
            hasExponent = true;
            readNextChar();
            if (forwardBuffer[forward] == PLUS || forwardBuffer[forward] == MINUS) {
                readNextChar();
            }
            if (!isDigit(forwardBuffer[forward])) {
                throw new IOException("Exponent part of a double literal must contain at least one digit.");
            }
            while (isDigit(forwardBuffer[forward])) {
                readNextChar();
            }
        }

        decrementForward();
        return tokenType;
    }

    private TokenType handleDotOrNumberStartingWithDot() throws IOException {
        readNextChar();

        if (isDigit(forwardBuffer[forward])) {
            do {
                readNextChar();
            } while (isDigit(forwardBuffer[forward]));
            if (forwardBuffer[forward] == LOWERCASE_E || forwardBuffer[forward] == UPPERCASE_E) {
                readNextChar();
                if (forwardBuffer[forward] == PLUS || forwardBuffer[forward] == MINUS) {
                    readNextChar();
                }
                if (!isDigit(forwardBuffer[forward])) {
                    throw new IOException("Exponent part of a double literal must contain at least one digit.");
                }
                while (isDigit(forwardBuffer[forward])) {
                    readNextChar();
                }
                decrementForward();
                return TokenType.DOUBLE;
            }
            decrementForward();
            return TokenType.DECIMAL;
        } else if (forwardBuffer[forward] == FULL_STOP) {
            return TokenType.PARENT_AXIS;
        } else {
            decrementForward();
            return TokenType.CURRENT_AXIS;
        }
    }

    private TokenType handleNegativeNumbers() throws IOException {
        return TokenType.MINUS;
    }

    private TokenType handleLiteral(final byte quoteByte) throws IOException {
        do {
            readNextChar();
            if (forwardBuffer[forward] == -1) {
                throw new IOException("Unterminated literal string");
            }
            if (forwardBuffer[forward] == quoteByte) {
                if (forwardBuffer[forward + 1] == quoteByte) {
                    readNextChar(); // Consume the second quote (escaped quote)
                } else {
                    return TokenType.LITERAL;
                }
            }
        } while (true);
    }

    private TokenType handleOperatorOrPunctuation(final byte firstByte) throws IOException {
        switch (firstByte) {
            case SLASH:
                readNextChar();
                if (forwardBuffer[forward] == SLASH) {
                    return TokenType.DOUBLE_SLASH;
                } else {
                    decrementForward();
                    return TokenType.SLASH;
                }
            case PLUS:
                return TokenType.PLUS;
            case MINUS:
                return TokenType.MINUS;
            case MULTIPLY_OPERATOR:
                return TokenType.MULTIPLY_OPERATOR;
            case EQUALS:
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
                return TokenType.UNION_OPERATOR;
            case NOT:
                readNextChar();
                if (forwardBuffer[forward] == EQUALS) {
                    return TokenType.NOT_EQUAL_TO;
                } else {
                    throw new IOException("Invalid character sequence: '!' must be followed by '=' in XPath 2.0 for '!=' operator.");
                }
            case GREATER_THAN:
                readNextChar();
                if (forwardBuffer[forward] == EQUALS) {
                    return TokenType.GREATER_THAN_EQUAL_TO;
                } else if (forwardBuffer[forward] == GREATER_THAN) {
                    return TokenType.NODE_AFTER; // >>
                } else {
                    decrementForward();
                    return TokenType.GREATER_THAN;
                }
            case LESS_THAN:
                readNextChar();
                if (forwardBuffer[forward] == EQUALS) {
                    return TokenType.LESS_THAN_EQUAL_TO;
                } else if (forwardBuffer[forward] == LESS_THAN) {
                    return TokenType.NODE_BEFORE; // <<
                } else {
                    decrementForward();
                    return TokenType.LESS_THAN;
                }
            case COLON:
                readNextChar();
                if (forwardBuffer[forward] == COLON) {
                    return TokenType.AXIS_SEPARATOR;
                } else if (forwardBuffer[forward] == EQUALS) {
                    return TokenType.NAMESPACE_SEPARATOR; // := (for variable assignment in XQuery) - might need separate handling
                } else {
                    decrementForward();
                    return TokenType.COLON;
                }
            case PIPE:
                return TokenType.UNION_OPERATOR;
            case AMPERSAND:
                return TokenType.INTERSECT_EXCEPT_OPERATOR; // & (can be intersect or except depending on context in XPath 2.0)
            case QUESTION_MARK:
                return TokenType.QUESTION_MARK;
            case ASTERISK:
                return TokenType.MULTIPLY_OPERATOR;
            case DOLLAR:
                // Handled in the main next() method
                throw new IOException("Unexpected '$' encountered here.");
            case OPEN_BRACE:
                return TokenType.OPEN_BRACE;
            case CLOSE_BRACE:
                return TokenType.CLOSE_BRACE;
            case SEMICOLON:
                return TokenType.SEMICOLON;
            case ARROW:
                readNextChar();
                if (forwardBuffer[forward] == GREATER_THAN) {
                    return TokenType.ARROW; // =>
                } else {
                    throw new IOException("Invalid character sequence: '=' must be followed by '>' for the arrow operator.");
                }
            default:
                throw new IOException("Unexpected character encountered: " + (char) firstByte + " (byte value: " + firstByte + ")");
        }
    }

    private boolean isDigit(final byte b) {
        return b >= ZERO && b <= NINE;
    }

    private boolean isLetter(final byte b) {
        return (b >= LOWERCASE_A && b <= LOWERCASE_Z) || (b >= UPPERCASE_A && b <= UPPERCASE_Z);
    }
}