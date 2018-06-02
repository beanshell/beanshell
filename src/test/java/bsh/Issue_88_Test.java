package bsh;

import org.junit.Test;
import org.junit.runner.RunWith;

import static bsh.TestUtil.eval;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

@RunWith(FilteredTestRunner.class)
public class Issue_88_Test {

    @Test
    public void call_of_public_inherited_method_from_non_public_class_without_accessibilty() throws Exception {
        assumeTrue("testing illegal access assumes accessibility", Capabilities.haveAccessibility());
        Capabilities.setAccessibility(false);
        final Interpreter interpreter = new Interpreter();
        interpreter.set("x", new Implementation());
        assertEquals("public String", interpreter.eval("x.method(\"foo\");"));
        Capabilities.setAccessibility(true);
    }

    @Test
    public void call_of_public_inherited_method_from_non_public_class_with_accessibilty() throws Exception {
        assumeTrue("testing illegal access assumes accessibility", Capabilities.haveAccessibility());
        Capabilities.setAccessibility(true);
       final Interpreter interpreter = new Interpreter();
        interpreter.set("x", new Implementation());
        assertEquals("public String", interpreter.eval("x.method(\"foo\");"));
    }

    @Test
    public void community_test_cases() throws Exception {
        assertEquals(0, eval("Collections.unmodifiableList(new ArrayList()).size();"));
        assertEquals(0, eval("new HashMap().entrySet().size();"));
        assertEquals(Boolean.FALSE, eval("new HashMap().keySet().iterator().hasNext();"));
    }


    public interface Public {
        Object method(String param);
    }


    public interface PublicWithoutMethod extends Public {
    }


    private abstract class AbstractImplementation implements PublicWithoutMethod {
        public Object method(final String param) {
            return "public String";
        }

        @SuppressWarnings("unused")
        private Object method(final Object param) {
            return "private Object";
        }
    }


    public class Implementation extends AbstractImplementation {
        public Object method(final CharSequence param) {
            return "public CharSequence";
        }

        public Object method(final Object param) {
            return "public Object";
        }
    }
}
