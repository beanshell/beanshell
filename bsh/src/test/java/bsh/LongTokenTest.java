/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package bsh;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.StringReader;

import org.junit.Test;

public class LongTokenTest {

    private static final int[] LENGTHS = {
        4093, 4094, 4095, 4096, 4097, 8191, 8192, 8193, 32768
    };

    @Test
    public void evaluatesStringsAcrossBufferBoundaries() throws Exception {
        for (int length : LENGTHS) {
            String value = letters(length);
            for (int offset : new int[] { 0, 2051, 4093 }) {
                Interpreter interpreter = new Interpreter();
                assertEquals("length=" + length + ", offset=" + offset, value,
                        interpreter.eval(spaces(offset) + "value = \"" + value
                                + "\"; after = 42; value;"));
                assertEquals(42, interpreter.get("after"));
            }
        }
    }

    @Test
    public void evaluatesLongIdentifiers() throws Exception {
        for (int length : LENGTHS) {
            String name = letters(length);
            assertEquals(42, new Interpreter().eval(name + " = 42; " + name + ";"));
        }
    }

    @Test
    public void evaluatesAfterLongBlockComments() throws Exception {
        assertComment("/*", "*/\n");
    }

    @Test
    public void evaluatesAfterLongFormalComments() throws Exception {
        assertComment("/**", "*/\n");
    }

    @Test
    public void evaluatesAfterLongLineComments() throws Exception {
        assertComment("//", "\n");
    }

    @Test
    public void decodesEscapesAcrossBufferBoundaries() throws Exception {
        StringBuilder source = new StringBuilder("\"");
        StringBuilder expected = new StringBuilder();
        for (int i = 0; i < 6000; i++) {
            source.append("a").append('\\').append("u0062\\n\\\\");
            expected.append("ab\n\\");
        }
        source.append("\";");
        assertEquals(expected.toString(), new Interpreter().eval(source.toString()));
    }

    @Test
    public void handlesReadersReturningShortChunks() throws Exception {
        String value = letters(32768);
        String source = "/*" + value + "*/ value = \"" + value + "\"; value;";
        for (final int chunkSize : new int[] { 1, 7, 511, 4096 }) {
            try (StringReader reader = new StringReader(source) {
                @Override
                public int read(char[] buffer, int offset, int length) throws IOException {
                    return super.read(buffer, offset, Math.min(length, chunkSize));
                }
            }) {
                assertEquals("chunkSize=" + chunkSize, value, new Interpreter().eval(reader));
            }
        }
    }

    @Test
    public void preservesTokenImagesAndPositionsAfterGrowth() {
        String value = letters(32768);
        ParserTokenManager tokens = new ParserTokenManager(new JavaCharStream(
                new StringReader("\"" + value + "\"\r\n/*" + value + "\r\n*/\r\n12345")));
        Token string = tokens.getNextToken();
        assertEquals("\"" + value + "\"", string.image);
        assertEquals(1, string.beginLine);
        assertEquals(1, string.beginColumn);
        assertEquals(1, string.endLine);
        assertEquals(value.length() + 2, string.endColumn);
        Token number = tokens.getNextToken();
        assertEquals("/*" + value + "\r\n*/", number.specialToken.image);
        assertEquals("12345", number.image);
        assertEquals(4, number.beginLine);
        assertEquals(1, number.beginColumn);
        assertEquals(4, number.endLine);
        assertEquals(5, number.endColumn);
        assertEquals(ParserConstants.EOF, tokens.getNextToken().kind);
    }

    @Test
    public void reinitializesInputAfterReadingLongTokens() {
        JavaCharStream stream = new JavaCharStream(new StringReader(letters(32768)));
        ParserTokenManager tokens = new ParserTokenManager(stream);
        assertEquals(letters(32768), tokens.getNextToken().image);
        assertEquals(ParserConstants.EOF, tokens.getNextToken().kind);

        stream.reInit(new StringReader("42"), 3, 7);
        tokens.ReInit(stream);
        Token number = tokens.getNextToken();
        assertEquals("42", number.image);
        assertEquals(3, number.beginLine);
        assertEquals(7, number.beginColumn);
        assertEquals(8, number.endColumn);
        assertEquals(ParserConstants.EOF, tokens.getNextToken().kind);
    }

    private void assertComment(String start, String end) throws Exception {
        for (int length : LENGTHS) {
            assertEquals("length=" + length, 42, new Interpreter().eval(
                    "value = 21; " + start + letters(length) + end + "value * 2;"));
        }
    }

    private static String letters(int length) {
        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++)
            result.append((char) ('a' + i % 26));
        return result.toString();
    }

    private static String spaces(int length) {
        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++)
            result.append(' ');
        return result.toString();
    }
}
