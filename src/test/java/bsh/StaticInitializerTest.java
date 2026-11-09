package bsh;

import java.net.URL;
import java.net.URLClassLoader;

import javax.script.ScriptEngine;
import javax.script.ScriptException;

import bsh.engine.BshScriptEngine;
import fixture.FailingStaticInit;
import fixture.RecordingStaticInit;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

/** A Java class's static initializer runs at the class's first use, as in
 * Java, and an initializer that throws gives errors a script can catch
 * (#928). */
@RunWith(FilteredTestRunner.class)
public class StaticInitializerTest {
    private static final String FAILING = FailingStaticInit.class.getName();
    private static final String RECORDING = RecordingStaticInit.class.getName();
    private static final URL FIXTURES = FailingStaticInit.class
            .getProtectionDomain().getCodeSource().getLocation();

    /** Where a script finds the fixtures: through the context class loader,
     * or on a path added with addClassPath. Each interpreter gets its own
     * copy of the fixtures, as a class whose initializer failed stays
     * failed in its loader. */
    enum Path { CONTEXT, ADDED }

    @Rule
    public TemporaryFolder emptyDir = new TemporaryFolder();

    private final ClassLoader contextLoader =
            Thread.currentThread().getContextClassLoader();

    @Before
    public void clearRecord() {
        System.clearProperty(RECORDING);
    }

    @After
    public void restoreContextLoader() {
        Thread.currentThread().setContextClassLoader(contextLoader);
        System.clearProperty(RECORDING);
    }

    private Interpreter interpreter(Path path) throws Exception {
        Interpreter bsh = new Interpreter();
        if (Path.CONTEXT == path)
            Thread.currentThread().setContextClassLoader(
                    new URLClassLoader(new URL[] {FIXTURES}, null));
        else
            bsh.getClassManager().addClassPath(FIXTURES);
        return bsh;
    }

    private static String initialized() {
        return System.getProperty(RECORDING);
    }

    @Test
    public void fixtures_load_in_their_own_loader() throws Exception {
        for (Path path : Path.values())
            assertNotSame(path.name(), FailingStaticInit.class,
                    interpreter(path).eval("return " + FAILING + ".class;"));
    }

    @Test
    public void first_use_throws_initializer_error_and_next_no_class_def_found()
            throws Exception {
        String[] uses = {
            FAILING + ".x;",
            FAILING + ".x = 1;",
            "new " + FAILING + "();",
            FAILING + ".m();",
            FAILING + ".K;",
        };
        for (Path path : Path.values()) for (String use : uses) {
            Interpreter bsh = interpreter(path);
            bsh.eval("first = null; second = null;"
                + " try { " + use + " }"
                + " catch (ExceptionInInitializerError e) { first = e; }"
                + " try { " + use + " }"
                + " catch (NoClassDefFoundError e) { second = e; }");
            String row = path + ": " + use;
            Throwable first = (Throwable) bsh.get("first");
            assertThat(row, first, instanceOf(ExceptionInInitializerError.class));
            assertThat(row, first.getCause(), instanceOf(IllegalStateException.class));
            assertThat(row, first.getCause().getMessage(), equalTo("init failed"));
            assertThat(row, bsh.get("second"), instanceOf(NoClassDefFoundError.class));
        }
    }

    @Test
    public void naming_a_class_does_not_run_its_initializer() throws Exception {
        for (Path path : Path.values()) {
            Interpreter bsh = interpreter(path);
            bsh.eval("import " + FAILING + ";");
            bsh.eval(FAILING + " f = null;");
            assertThat(path.name(), bsh.eval("return " + FAILING + ".class;"),
                    instanceOf(Class.class));
            assertThat(path.name(), bsh.eval("return \"s\" instanceof " + FAILING + ";"),
                    equalTo(false));
            assertThat(path.name(), bsh.get("f"), nullValue());
        }
    }

    @Test
    public void initializer_runs_at_first_use() throws Exception {
        for (Path path : Path.values()) {
            System.clearProperty(RECORDING);
            Interpreter bsh = interpreter(path);
            bsh.eval(RECORDING + " r = null;");
            bsh.eval("c = " + RECORDING + ".class;");
            bsh.eval("b = r instanceof " + RECORDING + ";");
            assertThat(path.name(), initialized(), nullValue());
            assertThat(path.name(), bsh.eval("return " + RECORDING + ".x;"), equalTo(5));
            assertThat(path.name(), initialized(), equalTo("initialized"));
        }
    }

    /** The other two lookups find the application class loader's copy, so
     * this only names the class: using it would leave it failed for the
     * rest of the JVM. Naming it throws if the lookup initializes it. */
    @Test
    public void application_class_lookups_do_not_run_initializer() throws Exception {
        // with a path added, found through BshClassManager.plainClassForName
        Interpreter added = new Interpreter();
        added.getClassManager().addClassPath(emptyDir.getRoot().toURI().toURL());
        added.eval(FAILING + " f = null;");
        assertSame(FailingStaticInit.class, added.eval("return " + FAILING + ".class;"));

        // without a context class loader, found by the plain Class.forName
        Thread.currentThread().setContextClassLoader(null);
        Interpreter plain = new Interpreter();
        plain.eval(FAILING + " f = null;");
        assertSame(FailingStaticInit.class, plain.eval("return " + FAILING + ".class;"));
    }

    @Test
    public void java_caller_gets_target_error() throws Exception {
        for (Path path : Path.values()) {
            Interpreter bsh = interpreter(path);
            TargetError first = assertThrows(TargetError.class,
                    () -> bsh.eval(FAILING + ".x;"));
            assertThat(path.name(), first.getTarget(),
                    instanceOf(ExceptionInInitializerError.class));
            TargetError second = assertThrows(TargetError.class,
                    () -> bsh.eval(FAILING + ".x;"));
            assertThat(path.name(), second.getTarget(),
                    instanceOf(NoClassDefFoundError.class));
        }
    }

    @Test
    public void script_engine_throws_script_exception() throws Exception {
        Thread.currentThread().setContextClassLoader(
                new URLClassLoader(new URL[] {FIXTURES}, null));
        ScriptEngine engine = new BshScriptEngine();
        assertThrows(ScriptException.class, () -> engine.eval(FAILING + ".m();"));
        assertThrows(ScriptException.class, () -> engine.eval(FAILING + ".m();"));
    }
}
