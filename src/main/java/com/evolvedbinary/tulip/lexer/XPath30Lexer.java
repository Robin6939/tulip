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

import java.io.IOException;

public class XPath30Lexer extends XPath20Lexer {

    public XPath30Lexer(final Source source, final int bufferSize, final XmlSpecification xmlSpecification) throws IOException {
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
            } else {
                decrementForward();
                tokenType = TokenType.DOT;
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

    // TODO(AR) override relevant rules from XPath 1.0
}
