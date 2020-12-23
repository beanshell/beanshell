package bsh.classpath;

import bsh.EvalError;
import bsh.Interpreter;
import org.junit.Assert;
import org.junit.Test;

public class BshClassManagerTest {

    // Loads every class
    private final ClassLoader defaultExternalClassLoader = new ClassLoader() {
        @Override
        public Class<?> loadClass(final String name) throws ClassNotFoundException {
            return super.loadClass(name);
        }
    };

    // Loads every class except 'System' and 'Math'
    private final ClassLoader filteringClassLoader = new ClassLoader() {
        @Override
        public Class<?> loadClass(final String name) throws ClassNotFoundException {
            if (name.equals("java.lang.System"))
                return null;
            if (name.equals("java.lang.Math"))
                throw new ClassNotFoundException();
            return super.loadClass(name);
        }
    };

    @Test
    public void external_class_loader_returns_null_should_fail() {
        try {
            final Interpreter interpreter = new Interpreter();
            interpreter.setClassLoader(filteringClassLoader);
            interpreter.eval("System.out.println();");

            Assert.fail("External classLoader returned null but execution was successful");
        } catch (final EvalError e) {
            // Ok
        }
    }

    @Test
    public void external_class_loader_throws_should_fail() {
        try {
            final Interpreter interpreter = new Interpreter();
            interpreter.setClassLoader(filteringClassLoader);
            interpreter.eval("Math.max(1.0, 2.0);");

            Assert.fail("External classLoader returned threw but execution was successful");
        } catch (final EvalError e) {
            // Ok
        }
    }

    @Test
    public void external_class_loader_returns_class_should_work() {
        try {
            final Interpreter interpreter = new Interpreter();
            interpreter.setClassLoader(defaultExternalClassLoader);
            interpreter.eval("Math.max(1.0, 2.0);");
        } catch (final EvalError e) {
            Assert.fail("Failed with exception: " + e.getMessage());
        }
    }

    @Test
    public void external_class_loader_returns_class_should_work_2() {
        try {
            final Interpreter interpreter = new Interpreter();
            interpreter.setClassLoader(defaultExternalClassLoader);
            interpreter.eval("System.out.println();");
        } catch (final EvalError e) {
            Assert.fail("Failed with exception: " + e.getMessage());
        }
    }

}
