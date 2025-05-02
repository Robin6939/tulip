package com.evolvedbinary.tulip.lexer;

import com.evolvedbinary.tulip.source.Source;
import com.evolvedbinary.tulip.spec.XmlSpecification;

import java.io.IOException;


/**
 * XPath 2.0 Lexer extending the basic XPath 1.0 Lexer.
 */
public class XPath20Lexer extends XPath10Lexer {

    public XPath20Lexer(final Source source, final int bufferSize, final XmlSpecification xmlSpecification) throws IOException {
        super(source, bufferSize, xmlSpecification);
    }

    @Override
    public Token next() throws IOException {
        resetLexemeBegin();
        skipWhitespaceAndResetBegin();

        final byte firstByte = forwardBuffer[forward];
        TokenType tokenType = null;

        if (firstByte == -1) {
            tokenType = TokenType.EOF;
        } else if (firstByte == DOLLAR) {
            tokenType = handleVariableReference();
        } else if (isLetter(firstByte)) {
            tokenType = handleNCNameorQNameorFunctionNameorAxisNameorKeyword();
        } else if(firstByte == UNDERSCORE) {
            tokenType = handleNCNameorQNameorFunctionNameorAxisNameorKeyword();
        } else if (isDigit(firstByte)) {
            tokenType = handleNumberStartingWithDigit();
        } else if(firstByte == FULL_STOP) {
            readNextChar();
            if (isDigit(forwardBuffer[forward])) {
                tokenType = handleNumberStartingWithDigit();
            }
        } else if (firstByte == QUOTATION_MARK || firstByte == APOSTROPHE) {
            tokenType = handleLiteral(firstByte);
//        } else if (firstByte == MINUS) {
//            tokenType = handleNegativeNumbers();
        } else if (firstByte == LPAREN) {
            readNextChar();
            if(forwardBuffer[forward] == COLON) {
                tokenType = handleComment();
            } else {
                decrementForward();
                tokenType = TokenType.LPAREN;
            }
        } else {
            tokenType = handleOperatorOrPunctuation(firstByte);
        }

        if(tokenType == TokenType.COMMENT)
            return next();

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
     * Handles nested comments `(: ... :)`.
     *
     * @return TokenType.COMMENT when a full comment is parsed
     * @throws IOException If an unterminated comment is detected
     */
    private TokenType handleComment() throws IOException {
        //it must be the start of a comment
        int nestingLevel = 1;
        while (true) {
            readNextChar();
            if (forwardBuffer[forward] == -1) {
                throw new IOException("Unterminated comment");
            }
            if (forwardBuffer[forward] == LPAREN && forwardBuffer[forward + 1] == COLON) {
                readNextChar();
                if(forwardBuffer[forward] == COLON) {
                    nestingLevel++;
                } else {
                    decrementForward();
                }
            } else if (forwardBuffer[forward] == COLON) {
                readNextChar();
                if(forwardBuffer[forward] == RPAREN) {
                    nestingLevel--;
                    if(nestingLevel == 0)
                        return TokenType.COMMENT;
                }
            }
        }
    }

    /**
     * Handles variable references (e.g., $varName).
     *
     * @return TokenType.VARIABLE_REFERENCE if valid, or throws an IOException otherwise
     * @throws IOException If an invalid variable name is encountered
     */
    private TokenType handleVariableReference() throws IOException {
        readNextChar();
        if (isLetter(forwardBuffer[forward]) || forwardBuffer[forward] == UNDERSCORE) {
            TokenType temp = handleNCNameorQNameorFunctionNameorAxisNameorKeyword();
            if(temp == TokenType.QName || temp == TokenType.NCName) {
                 return TokenType.VARIABLE_REFERENCE;
            }
            else {
                throw new IOException("Invalid variable name");
            }
        } else {
            throw new IOException("Invalid variable name");
        }
    }

    /**
     * Handles numbers starting with a digit.
     *
     * @return TokenType representing the number type (DIGITS, DECIMAL, or DOUBLE)
     * @throws IOException If an invalid number format is encountered
     */
    public TokenType handleNumberStartingWithDigit() throws IOException {
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

    /**
     * Checks if a byte represents an ASCII digit ('0'-'9').
     *
     * @param b The byte to check.
     * @return True if the byte is a digit, false otherwise.
     */
    private boolean isDigit(final byte b) {
        return b >= ZERO && b <= NINE;
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