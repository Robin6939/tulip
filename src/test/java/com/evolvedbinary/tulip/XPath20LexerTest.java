package com.evolvedbinary.tulip;

import com.evolvedbinary.tulip.lexer.Token;
import com.evolvedbinary.tulip.lexer.TokenType;
import com.evolvedbinary.tulip.lexer.XPath20Lexer;
import com.evolvedbinary.tulip.source.FileSource;
import com.evolvedbinary.tulip.source.Source;
import com.evolvedbinary.tulip.spec.XmlSpecification;
import com.evolvedbinary.tulip.spec.XmlSpecification_1_0; // Assuming a 2.0 spec might exist or use a common one
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class XPath20LexerTest {
    private final XmlSpecification xmlSpecification = new XmlSpecification_1_0(); // Replace with XPath 2.0 spec if available
    private static final int BUFFER_SIZE = 1024; // Define a buffer size

    // --- Helper Record ---
    record TokenInfo(TokenType type, String lexeme) {
        @Override
        public String toString() {
            // Nicer formatting for assertion failures
            return String.format("(%s, \"%s\")", type, lexeme);
        }
    }

    // --- Helper Method to Lex Input ---
    private List<TokenInfo> lex(String input) throws IOException {
        Path path = Paths.get("src/test/java/com/evolvedbinary/tulip/file.txt");
        try {
            // Overwrite the file content
            Files.write(path, input.getBytes(), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
            System.out.println("File successfully overwritten.");
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }

        List<TokenInfo> tokens = new ArrayList<>();
        try (Source source = FileSource.open(path); // Use try-with-resources for source
             XPath20Lexer lexer = new XPath20Lexer(source, BUFFER_SIZE, xmlSpecification)) {

            while (true) {
                Token t = lexer.next();
                String lexeme = (t.getTokenType() == TokenType.EOF) ? "" : t.getLexeme();
                tokens.add(new TokenInfo(t.getTokenType(), lexeme));
                if (t.getTokenType() == TokenType.EOF) {
                    break;
                }
            }
        } // Source and Lexer are closed here
        System.out.println(tokens);
        return tokens;
    }

    // ========================================================================
    // Test Methods for XPath 2.0 Differences
    // ========================================================================

    // --- Comments ---
    @Test
    void testXPath20Comments() throws IOException {
        String input = " (: This is a comment :) ";
        assertEquals(List.of(new TokenInfo(TokenType.EOF, "")), lex(input)); // ignore comments
    }

    @Test
    void testXPath20NestedComments() throws IOException {
        String input = " (: Outer (: Inner :) :) ";
        assertEquals(List.of(new TokenInfo(TokenType.EOF, "")), lex(input));
    }

    @Test
    void testXPath20CommentWithOtherTokens() throws IOException {
        String input = "1 (: comment :) 2";
        List<TokenInfo> expected = List.of(
                new TokenInfo(TokenType.DIGITS, "1"),
//                new TokenInfo(TokenType.COMMENT, "(: comment :)"), -> This should be ignored
                new TokenInfo(TokenType.DIGITS, "2"),
                new TokenInfo(TokenType.EOF, "")
        );
        assertEquals(expected, lex(input));
    }

    // --- Variable References ---
    @Test
    void testVariableReferences() throws IOException {
        String input = " $myVar $prefix:localName ";
        List<TokenInfo> expected = List.of(
                new TokenInfo(TokenType.VARIABLE_REFERENCE, "$myVar"),
                new TokenInfo(TokenType.VARIABLE_REFERENCE, "$prefix:localName"),
                new TokenInfo(TokenType.EOF, "")
        );
        assertEquals(expected, lex(input));
    }

    // --- QNames ---
    @Test
    void testQNames() throws IOException {
        String input = " prefix:localName another:element ";
        List<TokenInfo> expected = List.of(
                new TokenInfo(TokenType.QName, "prefix:localName"),
                new TokenInfo(TokenType.QName, "another:element"),
                new TokenInfo(TokenType.EOF, "")
        );
        assertEquals(expected, lex(input));
    }

    // --- Numeric Literals (Double) ---
    @Test
    void testDoubleLiterals() throws IOException {
        String input = " 123E2 45.67e-3 .5E10 ";
        List<TokenInfo> expected = List.of(
                new TokenInfo(TokenType.DOUBLE, "123E2"),
                new TokenInfo(TokenType.DOUBLE, "45.67e-3"),
                new TokenInfo(TokenType.DOUBLE, ".5E10"),
                new TokenInfo(TokenType.EOF, "")
        );
        assertEquals(expected, lex(input));
    }

    // --- String Literals (Escaped Quotes) ---
    @Test
    void testStringLiteralWithEscapedQuotesXPath20() throws IOException {
        String input = " \"title = 'Harry''s Book'\" ";
        List<TokenInfo> expected = List.of(
                new TokenInfo(TokenType.LITERAL, "\"title = 'Harry''s Book'\""),
                new TokenInfo(TokenType.EOF, "")
        );
        assertEquals(expected, lex(input));
    }
//
//    // --- Operators ---
//    @Test
//    void testXPath20Operators() throws IOException {
//        String input = " => eq ne lt le gt ge is << >> := ";
//        List<TokenInfo> expected = List.of(
//                new TokenInfo(TokenType.ARROW, "=>"),
//                new TokenInfo(TokenType.IDENTIFIER, "eq"),
//                new TokenInfo(TokenType.IDENTIFIER, "ne"),
//                new TokenInfo(TokenType.IDENTIFIER, "lt"),
//                new TokenInfo(TokenType.IDENTIFIER, "le"),
//                new TokenInfo(TokenType.IDENTIFIER, "gt"),
//                new TokenInfo(TokenType.IDENTIFIER, "ge"),
//                new TokenInfo(TokenType.IDENTIFIER, "is"),
//                new TokenInfo(TokenType.NODE_BEFORE, "<<"),
//                new TokenInfo(TokenType.NODE_AFTER, ">>"),
//                new TokenInfo(TokenType.NAMESPACE_SEPARATOR, ":="),
//                new TokenInfo(TokenType.EOF, "")
//        );
//        assertEquals(expected, lex(input));
//    }
//
//    // --- Keywords ---
//    @Test
//    void testXPath20Keywords() throws IOException {
//        String input = " instance of cast as treat return for in some every if then else typeswitch case default at where order by ascending descending stable union intersect except to satisfies collation import schema module namespace preserve strip copy-of deep-equal exactly-one zero-or-one one-or-more ";
//        List<TokenInfo> expected = List.of(
//                new TokenInfo(TokenType.INSTANCE_OF, "instance"),
//                new TokenInfo(TokenType.OF, "of"),
//                new TokenInfo(TokenType.CAST, "cast"),
//                new TokenInfo(TokenType.AS, "as"),
//                new TokenInfo(TokenType.TREAT, "treat"),
//                new TokenInfo(TokenType.RETURN, "return"),
//                new TokenInfo(TokenType.FOR, "for"),
//                new TokenInfo(TokenType.IN, "in"),
//                new TokenInfo(TokenType.SOME, "some"),
//                new TokenInfo(TokenType.EVERY, "every"),
//                new TokenInfo(TokenType.IF, "if"),
//                new TokenInfo(TokenType.THEN, "then"),
//                new TokenInfo(TokenType.ELSE, "else"),
//                new TokenInfo(TokenType.TYPESWITCH, "typeswitch"),
//                new TokenInfo(TokenType.CASE, "case"),
//                new TokenInfo(TokenType.DEFAULT, "default"),
//                new TokenInfo(TokenType.AT, "at"),
//                new TokenInfo(TokenType.WHERE, "where"),
//                new TokenInfo(TokenType.ORDER, "order"),
//                new TokenInfo(TokenType.BY, "by"),
//                new TokenInfo(TokenType.ASCENDING, "ascending"),
//                new TokenInfo(TokenType.DESCENDING, "descending"),
//                new TokenInfo(TokenType.STABLE, "stable"),
//                new TokenInfo(TokenType.UNION, "union"),
//                new TokenInfo(TokenType.INTERSECT, "intersect"),
//                new TokenInfo(TokenType.EXCEPT, "except"),
//                new TokenInfo(TokenType.TO, "to"),
//                new TokenInfo(TokenType.SATISFIES, "satisfies"),
//                new TokenInfo(TokenType.COLLATION, "collation"),
//                new TokenInfo(TokenType.IMPORT, "import"),
//                new TokenInfo(TokenType.SCHEMA, "schema"),
//                new TokenInfo(TokenType.MODULE, "module"),
//                new TokenInfo(TokenType.NAMESPACE, "namespace"),
//                new TokenInfo(TokenType.PRESERVE, "preserve"),
//                new TokenInfo(TokenType.STRIP, "strip"),
//                new TokenInfo(TokenType.COPY_OF, "copy-of"),
//                new TokenInfo(TokenType.DEEP_EQUAL, "deep-equal"),
//                new TokenInfo(TokenType.EXACTLY_ONE, "exactly-one"),
//                new TokenInfo(TokenType.ZERO_OR_ONE, "zero-or-one"),
//                new TokenInfo(TokenType.ONE_OR_MORE, "one-or-more"),
//                new TokenInfo(TokenType.EOF, "")
//        );
//        assertEquals(expected, lex(input));
//    }
//
//    // --- Other Punctuation ---
//    @Test
//    void testXPath20Braces() throws IOException {
//        String input = " { } ";
//        List<TokenInfo> expected = List.of(
//                new TokenInfo(TokenType.OPEN_BRACE, "{"),
//                new TokenInfo(TokenType.CLOSE_BRACE, "}"),
//                new TokenInfo(TokenType.EOF, "")
//        );
//        assertEquals(expected, lex(input));
//    }
//
//    @Test
//    void testXPath20Semicolon() throws IOException {
//        String input = " ; ";
//        List<TokenInfo> expected = List.of(
//                new TokenInfo(TokenType.SEMICOLON, ";"),
//                new TokenInfo(TokenType.EOF, "")
//        );
//        assertEquals(expected, lex(input));
//    }
//
//    // --- Braced URILiterals (Basic Test) ---
//    @Test
//    void testBracedURILiteralXPath20() throws IOException {
//        String input = " Q{http://example.com}local ";
//        List<TokenInfo> expected = List.of(
//                new TokenInfo(TokenType.IDENTIFIER, "Q"),
//                new TokenInfo(TokenType.OPEN_BRACE, "{"),
//                new TokenInfo(TokenType.IDENTIFIER, "http"),
//                new TokenInfo(TokenType.COLON, ":"),
//                new TokenInfo(TokenType.SLASH, "/"),
//                new TokenInfo(TokenType.SLASH, "/"),
//                new TokenInfo(TokenType.IDENTIFIER, "example"),
//                new TokenInfo(TokenType.FULL_STOP, "."),
//                new TokenInfo(TokenType.IDENTIFIER, "com"),
//                new TokenInfo(TokenType.CLOSE_BRACE, "}"),
//                new TokenInfo(TokenType.IDENTIFIER, "local"),
//                new TokenInfo(TokenType.EOF, "")
//        );
//        assertEquals(expected, lex(input));
//    }
}