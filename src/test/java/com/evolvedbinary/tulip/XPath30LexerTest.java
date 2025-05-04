package com.evolvedbinary.tulip;

import com.evolvedbinary.tulip.lexer.Token;
import com.evolvedbinary.tulip.lexer.TokenType;
import com.evolvedbinary.tulip.lexer.XPath20Lexer;
import com.evolvedbinary.tulip.lexer.XPath30Lexer;
import com.evolvedbinary.tulip.source.FileSource;
import com.evolvedbinary.tulip.source.Source;
import com.evolvedbinary.tulip.spec.XmlSpecification;
import com.evolvedbinary.tulip.spec.XmlSpecification_1_0;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import static com.evolvedbinary.tulip.constants.LexerConstants.BUFFER_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class XPath30LexerTest {
    private final XmlSpecification xmlSpecification = new XmlSpecification_1_0();

    // --- Helper Record ---
    record TokenInfo(TokenType type, String lexeme) {
        @Override
        public String toString() {
            return String.format("(%s, \"%s\")", type, lexeme);
        }
    }

    // --- Helper Method to Lex Input ---
    private List<XPath30LexerTest.TokenInfo> lex(String input) throws IOException {
        Path path = Paths.get("src/test/java/com/evolvedbinary/tulip/file.txt");
        try {
            // Overwrite the file content
            Files.write(path, input.getBytes(), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
            System.out.println("File successfully overwritten.");
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }

        List<XPath30LexerTest.TokenInfo> tokens = new ArrayList<>();
        try (Source source = FileSource.open(path); // Use try-with-resources for source
             XPath30Lexer lexer = new XPath30Lexer(source, BUFFER_SIZE, xmlSpecification)) {

            while (true) {
                Token t = lexer.next();
                String lexeme = (t.getTokenType() == TokenType.EOF) ? "" : t.getLexeme();
                tokens.add(new XPath30LexerTest.TokenInfo(t.getTokenType(), lexeme));
                if (t.getTokenType() == TokenType.EOF) {
                    break;
                }
            }
        } // Source and Lexer are closed here
        System.out.println(tokens);
        return tokens;
    }

    // ------------------------------------------------------------------------
    // XPath 3.0 Lexer Test Cases
    // ------------------------------------------------------------------------

    // Test inline function declarations
    @Test
    void testInlineFunctionExpression() throws IOException {
        String input = "function($x as xs:integer) as xs:integer { $x + 1 }";
        List<TokenInfo> expected = List.of(
                new TokenInfo(TokenType.FUNCTION, "function"),
                new TokenInfo(TokenType.LPAREN, "("),
                new TokenInfo(TokenType.VARIABLE_REFERENCE, "$x"),
                new TokenInfo(TokenType.AS, "as"),
                new TokenInfo(TokenType.QName, "xs:integer"),
                new TokenInfo(TokenType.RPAREN, ")"),
                new TokenInfo(TokenType.AS, "as"),
                new TokenInfo(TokenType.QName, "xs:integer"),
                new TokenInfo(TokenType.OPEN_BRACE, "{"),
                new TokenInfo(TokenType.VARIABLE_REFERENCE, "$x"),
                new TokenInfo(TokenType.PLUS, "+"),
                new TokenInfo(TokenType.DIGITS, "1"),
                new TokenInfo(TokenType.CLOSE_BRACE, "}"),
                new TokenInfo(TokenType.EOF, "")
        );
        assertEquals(expected, lex(input));
    }

    // Test simple map operator (!)
    @Test
    void testSimpleMapOperator() throws IOException {
        String input = "(1 to 3) ! (. * 2)";
        List<TokenInfo> expected = List.of(
                new TokenInfo(TokenType.LPAREN, "("),
                new TokenInfo(TokenType.DIGITS, "1"),
                new TokenInfo(TokenType.TO, "to"),
                new TokenInfo(TokenType.DIGITS, "3"),
                new TokenInfo(TokenType.RPAREN, ")"),
                new TokenInfo(TokenType.SIMPLE_MAP, "!"),
                new TokenInfo(TokenType.LPAREN, "("),
                new TokenInfo(TokenType.DOT, "."),
                new TokenInfo(TokenType.MULTIPLY_OPERATOR, "*"),
                new TokenInfo(TokenType.DIGITS, "2"),
                new TokenInfo(TokenType.RPAREN, ")"),
                new TokenInfo(TokenType.EOF, "")
        );
        assertEquals(expected, lex(input));
    }

    // Test array literal syntax
    @Test
    void testArrayLiteral() throws IOException {
        String input = "[1, 2, 3]";
        List<TokenInfo> expected = List.of(
                new TokenInfo(TokenType.LBRACKET, "["),
                new TokenInfo(TokenType.DIGITS, "1"),
                new TokenInfo(TokenType.COMMA, ","),
                new TokenInfo(TokenType.DIGITS, "2"),
                new TokenInfo(TokenType.COMMA, ","),
                new TokenInfo(TokenType.DIGITS, "3"),
                new TokenInfo(TokenType.RBRACKET, "]"),
                new TokenInfo(TokenType.EOF, "")
        );
        assertEquals(expected, lex(input));
    }

    // Test map literal syntax
    @Test
    void testMapLiteral() throws IOException {
        String input = "map { 'name': 'Alice', 'age': 30 }";
        List<TokenInfo> expected = List.of(
                new TokenInfo(TokenType.MAP, "map"),
                new TokenInfo(TokenType.OPEN_BRACE, "{"),
                new TokenInfo(TokenType.LITERAL, "'name'"),
                new TokenInfo(TokenType.COLON, ":"),
                new TokenInfo(TokenType.LITERAL, "'Alice'"),
                new TokenInfo(TokenType.COMMA, ","),
                new TokenInfo(TokenType.LITERAL, "'age'"),
                new TokenInfo(TokenType.COLON, ":"),
                new TokenInfo(TokenType.DIGITS, "30"),
                new TokenInfo(TokenType.CLOSE_BRACE, "}"),
                new TokenInfo(TokenType.EOF, "")
        );
        assertEquals(expected, lex(input));
    }

    // Test function lookup call
    @Test
    void testFunctionLookup() throws IOException {
        String input = "function-lookup(\"fn:upper-case\", 1)";
        List<TokenInfo> expected = List.of(
                new TokenInfo(TokenType.IDENTIFIER, "function-lookup"),
                new TokenInfo(TokenType.LPAREN, "("),
                new TokenInfo(TokenType.LITERAL, "\"fn:upper-case\""),
                new TokenInfo(TokenType.COMMA, ","),
                new TokenInfo(TokenType.DIGITS, "1"),
                new TokenInfo(TokenType.RPAREN, ")"),
                new TokenInfo(TokenType.EOF, "")
        );
        assertEquals(expected, lex(input));
    }

    // Test arrow operator
    @Test
    void testArrowOperator() throws IOException {
        String input = "'abc' => upper-case()";
        List<TokenInfo> expected = List.of(
                new TokenInfo(TokenType.LITERAL, "'abc'"),
                new TokenInfo(TokenType.ARROW, "=>"),
                new TokenInfo(TokenType.IDENTIFIER, "upper-case"),
                new TokenInfo(TokenType.LPAREN, "("),
                new TokenInfo(TokenType.RPAREN, ")"),
                new TokenInfo(TokenType.EOF, "")
        );
        assertEquals(expected, lex(input));
    }

    // Test array/map access syntax
    @Test
    void testArrayAndMapAccess() throws IOException {
        String input = "$arr?1 $map?\"key\"";
        List<TokenInfo> expected = List.of(
                new TokenInfo(TokenType.VARIABLE_REFERENCE, "$arr"),
                new TokenInfo(TokenType.QUESTION_MARK, "?"),
                new TokenInfo(TokenType.DIGITS, "1"),
                new TokenInfo(TokenType.VARIABLE_REFERENCE, "$map"),
                new TokenInfo(TokenType.QUESTION_MARK, "?"),
                new TokenInfo(TokenType.LITERAL, "\"key\""),
                new TokenInfo(TokenType.EOF, "")
        );
        assertEquals(expected, lex(input));
    }
}
