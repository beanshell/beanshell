package bsh;

import org.junit.Test;
import org.junit.runner.RunWith;

import static bsh.TestUtil.eval;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;


@RunWith(FilteredTestRunner.class)
public class SafeVoidTest {

    @Test
    public void void_to_null_returns_null() throws Exception {
        Capabilities.setVoidToNull(true);
        try {
            assertTrue(Capabilities.haveVoidToNull());
            String CODE_void_to_null = "b = a; return b";
            assertNull(eval(CODE_void_to_null));
            Capabilities.setVoidToNull(false);
            try {
                eval(CODE_void_to_null);
                fail("Void to null check should have failed with capability disabled");
            } catch(EvalError e) {
                /* This is what we expect so ignore it */
            }
        } finally {
            Capabilities.setVoidToNull(false);
        }
    }

}
