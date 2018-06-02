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
        assertEquals(result, 3);
    }

}
