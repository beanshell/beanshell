package bsh.classpath;

import bsh.EvalError;
import bsh.Interpreter;
import org.junit.Test;

public class ExternalClassLoaderTest {

    private final ClassLoader testLoader = new ClassLoader() {
        @Override
        public Class<?> loadClass(final String name) throws ClassNotFoundException {
            if (name.equals("java.lang.String")) {
                throw new ClassNotFoundException();
            }
            return super.loadClass(name);
        }
    };

    @Test
    public void testExternalClassManagerOptional() throws EvalError {
        final Interpreter interpreter = new Interpreter();
        interpreter.setClassLoader(testLoader);
        interpreter.eval("String tst = \"Hello, World!\";");
    }

    @Test(expected = EvalError.class)
    public void testExternalClassManagerForced() throws EvalError {
        final Interpreter interpreter = new Interpreter();
        interpreter.setClassLoader(testLoader, true);
        interpreter.eval("String tst = \"Hello, World!\";");
    }

}
