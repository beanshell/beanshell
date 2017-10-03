package bsh;

import org.junit.Assert;
import org.junit.Test;

public class StringLiteralTest {

    private static final String ESCAPE_CHAR = "\\";

    private enum DelimiterMode {
        SINGLE_LINE("\""),

        MULTI_LINE("\"\"\"");

        private final String _delimiter;

        DelimiterMode(String delimiter) {
            _delimiter = delimiter;
        }


        public String delimiter() {
            return _delimiter;
        }
    }


    @Test
    public void parse_string_literal() throws Exception {
        assertStringParsing("test", DelimiterMode.SINGLE_LINE);
    }


    @Test
    public void parse_long_string_literal_singleline() throws Exception {
        assertStringParsing("test", DelimiterMode.MULTI_LINE);
    }


    @Test
    public void parse_string_literal_with_escaped_chars() throws Exception {
        assertStringParsing(
                "\\\n\t\r\"\'",
                ESCAPE_CHAR + '\\' +
                ESCAPE_CHAR + "n" +
                ESCAPE_CHAR + "t" +
                ESCAPE_CHAR + "r" +
                ESCAPE_CHAR + '"' +
                ESCAPE_CHAR + "'",
                DelimiterMode.SINGLE_LINE);
    }


    @Test
    public void parse_string_literal_with_special_chars_multiline() throws Exception {
        assertStringParsing(
                "\t\n\\\"\'",
                "\t\n\\\"\'",
                DelimiterMode.MULTI_LINE);
    }


    @Test
    public void parse_long_string_literal_multiline() throws Exception {
        assertStringParsing("test\ntest", DelimiterMode.MULTI_LINE);
    }


    private void assertStringParsing(final String s, final DelimiterMode mode) throws EvalError {
        assertStringParsing(s, s, mode);
    }


    private void assertStringParsing(final String expected, final String source, final DelimiterMode mode) throws EvalError {
        final Interpreter interpreter = new Interpreter();
        Assert.assertEquals(expected, interpreter.eval("return " + mode.delimiter() + source + mode.delimiter() + ""));
    }

}
