package bsh;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(FilteredTestRunner.class)
public class Issue_88_Test {

    @Test
    public void call_of_public_inherited_method_from_non_public_class() throws Exception {
        final Interpreter interpreter = new Interpreter();
        interpreter.set("x", new Implementation());
        assertEquals("public", interpreter.eval("x.method(\"foo\");"));
    }


    public interface Public {
        Object method(String param);
    }

    abstract class AbstractImplementation implements Public {
        public Object method(final String param) {
            return "public";
        }
    }

    public class Implementation extends AbstractImplementation {
        private Object method(final Object param) {
            return "private";
        }
    }

}
