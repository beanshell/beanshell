package bsh;

import org.junit.Test;
import org.junit.runner.RunWith;

import static bsh.TestUtil.eval;
import static bsh.TestUtil.toMap;
import static org.junit.Assert.assertEquals;

@RunWith(FilteredTestRunner.class)
public class AccessibilityTest {

    @Test
    public void call_of_public_inherited_method_from_non_public_class_with_or_without_accessibilty() throws Exception {
        assertEquals("public String", eval(
                toMap("x", new Implementation()),
                "x.method(\"foo\");"));
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


    class Implementation extends AbstractImplementation {
        public Object method(final CharSequence param) {
            return "public CharSequence";
        }

        public Object method(final Object param) {
            return "public Object";
        }
    }
}
