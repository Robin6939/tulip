package com.evolvedbinary.tulip;

import com.evolvedbinary.tulip.lexer.Token;
import com.evolvedbinary.tulip.lexer.TokenType;
import com.evolvedbinary.tulip.lexer.XPath31Lexer;
import com.evolvedbinary.tulip.source.FileSource;
import com.evolvedbinary.tulip.source.Source;
import com.evolvedbinary.tulip.spec.XmlSpecification;
import com.evolvedbinary.tulip.spec.XmlSpecification_1_0;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import static com.evolvedbinary.tulip.constants.LexerConstants.BUFFER_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class XPath31LexerTest {
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
             XPath31Lexer lexer = new XPath31Lexer(source, BUFFER_SIZE, xmlSpecification)) {

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

    @Test
    void testArrayAppendFunction() throws IOException {
        String input = "array:append([1, 2], 3)";
        List<TokenInfo> expected = List.of(
                new TokenInfo(TokenType.FUNCTION_NAME, "array:append"),
                new TokenInfo(TokenType.LEFT_PAREN, "("),
                new TokenInfo(TokenType.ARRAY_START, "["),
                new TokenInfo(TokenType.INTEGER_LITERAL, "1"),
                new TokenInfo(TokenType.COMMA, ","),
                new TokenInfo(TokenType.INTEGER_LITERAL, "2"),
                new TokenInfo(TokenType.ARRAY_END, "]"),
                new TokenInfo(TokenType.COMMA, ","),
                new TokenInfo(TokenType.INTEGER_LITERAL, "3"),
                new TokenInfo(TokenType.RIGHT_PAREN, ")"),
                new TokenInfo(TokenType.EOF, "")
        );
        assertEquals(expected, lex(input));
    }

    @Test
    void testArrayRemoveFunction() throws IOException {
        String input = "array:remove([1, 2, 3], 2)";
        List<TokenInfo> expected = List.of(
                new TokenInfo(TokenType.FUNCTION_NAME, "array:remove"),
                new TokenInfo(TokenType.LEFT_PAREN, "("),
                new TokenInfo(TokenType.ARRAY_START, "["),
                new TokenInfo(TokenType.INTEGER_LITERAL, "1"),
                new TokenInfo(TokenType.COMMA, ","),
                new TokenInfo(TokenType.INTEGER_LITERAL, "2"),
                new TokenInfo(TokenType.COMMA, ","),
                new TokenInfo(TokenType.INTEGER_LITERAL, "3"),
                new TokenInfo(TokenType.ARRAY_END, "]"),
                new TokenInfo(TokenType.COMMA, ","),
                new TokenInfo(TokenType.INTEGER_LITERAL, "2"),
                new TokenInfo(TokenType.RIGHT_PAREN, ")"),
                new TokenInfo(TokenType.EOF, "")
        );
        assertEquals(expected, lex(input));
    }

    @Test
    void testArrowOperator() throws IOException {
        String input = "$seq => sum()";
        List<TokenInfo> expected = List.of(
                new TokenInfo(TokenType.VARIABLE_REFERENCE, "$seq"),
                new TokenInfo(TokenType.ARROW, "=>"),
                new TokenInfo(TokenType.FUNCTION_NAME, "sum"),
                new TokenInfo(TokenType.LEFT_PAREN, "("),
                new TokenInfo(TokenType.RIGHT_PAREN, ")"),
                new TokenInfo(TokenType.EOF, "")
        );
        assertEquals(expected, lex(input));
    }

    @Test
    void testStringConcatOperator() throws IOException {
        String input = "\"Hello\" || \" World\"";
        List<TokenInfo> expected = List.of(
                new TokenInfo(TokenType.STRING_LITERAL, "\"Hello\""),
                new TokenInfo(TokenType.CONCAT, "||"),
                new TokenInfo(TokenType.STRING_LITERAL, "\" World\""),
                new TokenInfo(TokenType.EOF, "")
        );
        assertEquals(expected, lex(input));
    }

    // --- Map Functions ---
    @Test
    void testMapContainsFunction() throws IOException {
        String input = "map:contains(map { 'a': 1, 'b': 2 }, 'a')";
        List<TokenInfo> expected = List.of(
                new TokenInfo(TokenType.FUNCTION_NAME, "map:contains"),
                new TokenInfo(TokenType.OPEN_PAREN, "("),
                new TokenInfo(TokenType.MAP, "map"),
                new TokenInfo(TokenType.OPEN_BRACE, "{"),
                new TokenInfo(TokenType.STRING_LITERAL, "'a'"),
                new TokenInfo(TokenType.COLON, ":"),
                new TokenInfo(TokenType.DIGITS, "1"),
                new TokenInfo(TokenType.COMMA, ","),
                new TokenInfo(TokenType.STRING_LITERAL, "'b'"),
                new TokenInfo(TokenType.COLON, ":"),
                new TokenInfo(TokenType.DIGITS, "2"),
                new TokenInfo(TokenType.CLOSE_BRACE, "}"),
                new TokenInfo(TokenType.COMMA, ","),
                new TokenInfo(TokenType.STRING_LITERAL, "'a'"),
                new TokenInfo(TokenType.CLOSE_PAREN, ")"),
                new TokenInfo(TokenType.EOF, "")
        );
        assertEquals(expected, lex(input));
    }

    @Test
    void testArrayAppendFunction() throws IOException {
        String input = "array:append([1, 2, 3], 4)";
        List<TokenInfo> expected = List.of(
                new TokenInfo(TokenType.FUNCTION_NAME, "array:append"),
                new TokenInfo(TokenType.OPEN_PAREN, "("),
                new TokenInfo(TokenType.OPEN_BRACKET, "["),
                new TokenInfo(TokenType.DIGITS, "1"),
                new TokenInfo(TokenType.COMMA, ","),
                new TokenInfo(TokenType.DIGITS, "2"),
                new TokenInfo(TokenType.COMMA, ","),
                new TokenInfo(TokenType.DIGITS, "3"),
                new TokenInfo(TokenType.CLOSE_BRACKET, "]"),
                new TokenInfo(TokenType.COMMA, ","),
                new TokenInfo(TokenType.DIGITS, "4"),
                new TokenInfo(TokenType.CLOSE_PAREN, ")"),
                new TokenInfo(TokenType.EOF, "")
        );
        assertEquals(expected, lex(input));
    }

    // --- Safe Navigation Operator ---
    @Test
    void testSafeNavigationOperator() throws IOException {
        String input = "$map?key";
        List<TokenInfo> expected = List.of(
                new TokenInfo(TokenType.VARIABLE_REFERENCE, "$map"),
                new TokenInfo(TokenType.SAFE_NAVIGATION, "?"),
                new TokenInfo(TokenType.IDENTIFIER, "key"),
                new TokenInfo(TokenType.EOF, "")
        );
        assertEquals(expected, lex(input));
    }

    @Test
    void testMapLiteral() throws IOException {
        String input = "map{\"key\": 42}";
        List<TokenInfo> expected = List.of(
                new TokenInfo(TokenType.MAP, "map"),
                new TokenInfo(TokenType.LEFT_BRACE, "{"),
                new TokenInfo(TokenType.STRING_LITERAL, "\"key\""),
                new TokenInfo(TokenType.COLON, ":"),
                new TokenInfo(TokenType.INTEGER_LITERAL, "42"),
                new TokenInfo(TokenType.RIGHT_BRACE, "}"),
                new TokenInfo(TokenType.EOF, "")
        );
        assertEquals(expected, lex(input));
    }

    @Test
    void testSafeNavigationOperator() throws IOException {
        String input = "$user?name";
        List<TokenInfo> expected = List.of(
                new TokenInfo(TokenType.VARIABLE_REFERENCE, "$user"),
                new TokenInfo(TokenType.QUESTION_MARK, "?"),
                new TokenInfo(TokenType.IDENTIFIER, "name"),
                new TokenInfo(TokenType.EOF, "")
        );
        assertEquals(expected, lex(input));
    }
}
