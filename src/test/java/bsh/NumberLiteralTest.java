package bsh;

import org.junit.Test;
import org.junit.runner.RunWith;

import static bsh.TestUtil.eval;
import static org.junit.Assert.assertEquals;

import java.math.BigInteger;

@RunWith(FilteredTestRunner.class)
public class NumberLiteralTest {

    @Test
    public void integer_literal_enhancements_hex() throws Exception {
        assertEquals("0x99", 153, eval("return 0x99;"));
    }

    @Test
    public void integer_literal_enhancements_octal() throws Exception {
        assertEquals("0231", 153, eval("return 0231;"));
    }

    @Test
    public void integer_literal_enhancements_binary() throws Exception {
        assertEquals("0b10011001", 153, eval("return 0b10011001;"));
    }

    @Test
    public void integer_literal_enhancements_binary_underscore() throws Exception {
        assertEquals("0b_1001_1001", 153, eval("return 0b_1001_1001;"));
    }

    @Test
    public void integer_literal_enhancements_hex_underscore() throws Exception {
        assertEquals("0x_9_9", 153, eval("return 0x_9_9;"));
    }

    @Test
    public void integer_literal_enhancements_decimal_underscore() throws Exception {
        assertEquals("15_500_000_000L", 15500000000L, eval("return 15_500_000_000L;"));
    }

    /** <a href="http://sourceforge.net/tracker/index.php?func=detail&aid=1897015&group_id=4075&atid=1950677">Sourceforge issue "parsing number 0xff000000 fails" - ID: 1950677</a>. */
    @Test
    public void parsing_hex_literal() throws Exception {
        assertEquals(0xff0000, eval("return 0xff0000;"));
    }

    @Test
    public void parsing_large_hex_literal() throws Exception {
        assertEquals(0xff000000L, eval("return 0xff000000;"));
    }

    @Test
    public void parsing_very_large_hex_literal() throws Exception {
        assertEquals(new BigInteger("ff00000000000000", 16), eval("return 0xff00000000000000;"));
    }

    /** <a href="http://sourceforge.net/tracker/?func=detail&aid=2945459&group_id=4075&atid=104075">Sourceforge issue "Parsing of long hex literals fails" - ID: 2945459</a>. */
    @Test
    public void parse_long_hex_literal() throws Exception {
        assertEquals(0x0000000001L, eval("return 0x0000000001L;"));
    }


}
