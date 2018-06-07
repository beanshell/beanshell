package bsh;

import org.junit.Test;
import org.junit.runner.RunWith;

import static bsh.TestUtil.eval;
import static org.junit.Assert.assertEquals;

@RunWith(FilteredTestRunner.class)
public class StatementsTest {

    @Test
    public void switch_on_strings() throws Exception {
        final Object result = eval(
            "switch('hurz') {",
            "   case 'bla': return 1;",
            "   case 'foo': return 2;",
            "   case 'hurz': return 3;",
            "   case 'igss': return 4;",
            "   default: return 5;",
            "}");
        assertEquals("hurz matches hurz", 3, result);
    }

    @Test
    public void switch_to_default() throws Throwable {

        final Object result = eval(
            "switch('hurzzzz') {",
            "   case 'bla': return 1;",
            "   case 'foo': return 2;",
            "   case 'hurz': return 3;",
            "   case 'igss': return 4;",
            "   default: return 5;",
            "}");
        assertEquals("hurzzzzz doesn't match any case", 5, result);
    }

}
