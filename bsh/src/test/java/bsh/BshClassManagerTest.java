package bsh;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import fixture.OptionalMethodFixture;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

@RunWith(FilteredTestRunner.class)
public class BshClassManagerTest {

    @Test
    public void class_literal_does_not_resolve_unused_method_dependencies()
            throws Exception {
        ClassLoader loader = new MissingDependencyClassLoader();
        Class<?> fixture = loader.loadClass(OptionalMethodFixture.class.getName());
        assertSame(loader, fixture.getClassLoader());
        assertThrows(NoClassDefFoundError.class, fixture::getDeclaredMethods);

        Interpreter interpreter = new Interpreter();
        interpreter.setClassLoader(loader);
        assertSame(fixture, interpreter.eval("return " + fixture.getName() + ".class;"));
    }

    private static class MissingDependencyClassLoader extends ClassLoader {
        MissingDependencyClassLoader() {
            super(BshClassManagerTest.class.getClassLoader());
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (name.equals(OptionalMethodFixture.MissingDependency.class.getName()))
                throw new ClassNotFoundException(name);
            if (!name.equals(OptionalMethodFixture.class.getName()))
                return super.loadClass(name, resolve);

            // Define the fixture here so its method dependency uses this loader.
            Class<?> fixture = findLoadedClass(name);
            if (fixture == null) {
                String resource = name.replace('.', '/') + ".class";
                try (InputStream input = getParent().getResourceAsStream(resource)) {
                    if (input == null)
                        throw new ClassNotFoundException(name);
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int count;
                    while ((count = input.read(buffer)) != -1)
                        bytes.write(buffer, 0, count);
                    byte[] definition = bytes.toByteArray();
                    fixture = defineClass(name, definition, 0, definition.length);
                } catch (IOException e) {
                    throw new ClassNotFoundException(name, e);
                }
            }
            if (resolve)
                resolveClass(fixture);
            return fixture;
        }
    }
}
