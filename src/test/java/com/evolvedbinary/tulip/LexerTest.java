package com.evolvedbinary.tulip;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.evolvedbinary.tulip.LexerConstants.BUFFER_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.DisplayName;

public class LexerTest {

    @DisplayName("Testing input buffering")
    @org.junit.jupiter.api.Test
    public void testInputBuffering() throws IOException {
        Path path = Paths.get("src/test/java/com/evolvedbinary/tulip/file.txt");
        Source fs = FileSource.open(path);
        XmlSpecification xmlSpecification = new XmlSpecification_1_0();
        XPath10Lexer lexer = new XPath10Lexer(fs, BUFFER_SIZE, xmlSpecification);
        String testing[] = {"\"What\"", "\"are\"", "\"You\"", "5", "\"Planning\"", "10", "\"gugu\"", "56", "31", "23", "42", "\"my name is robin\"", "88", "11111", "904802", "\"this is an alphanumberic 123\""};
        int count = 0;
        while(true) {
            Token t = lexer.next();
            if(t.getTokenType()==null)
                break;
            String lexeme = new String(t.getLexeme(), t.lexemeBegin, t.length, StandardCharsets.UTF_8);
            System.out.println(lexeme);
            assertEquals(lexeme, testing[count++]);
        }
    }
}
