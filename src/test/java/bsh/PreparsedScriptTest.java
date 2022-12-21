package bsh;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static bsh.TestUtil.script;
import static bsh.TestUtil.measureConcurrentTime;
import static bsh.TestUtil.emptyMap;
import static bsh.TestUtil.toMap;
import static bsh.TestUtil.mapOf;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class PreparsedScriptTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private ClassLoader _classLoader = new ClassLoader() {};

    @Test
    public void empty_script() throws Exception {
        final PreparsedScript preparsedScript = new PreparsedScript("", _classLoader);
        assertNull(preparsedScript.invoke(emptyMap()));
    }

    @Test
    public void static_class() throws Exception {
        final PreparsedScript times = new PreparsedScript(script(
                "class K {",
                    "static int a;",
                    "static int b;",
                "}",
                "class L extends K {}",
                "global.c = new K();",
                "return c.a * c.b;"
                ), _classLoader);
        assertEquals(0,   times.invoke(emptyMap()));
        assertEquals(42,  times.invoke(mapOf("K.a", 6,       "K.b", 7)));
        assertEquals(72,  times.invoke(mapOf("K.a", 9,       "K.b", 8)));
        assertEquals(782, times.invoke(mapOf("L.a", 23,      "L.b", 34)));
        assertEquals(6,   times.invoke(mapOf("c.a", 2,       "c.b", 3)));
        assertEquals(20,  times.invoke(mapOf("global.c.a", 4,"global.c.b", 5)));
        assertEquals(110, times.invoke(mapOf("this.c.a", 10, "this.c.b", 11)));
        assertEquals(726, times.invoke(mapOf("c.a", 22,      "c.b", 33)));
    }

    @Test
    public void throws_exception() throws Exception {
        thrown.expect(Exception.class);
        thrown.expectMessage(containsString("This is an error"));

        final PreparsedScript preparsedScript = new PreparsedScript(
                "throw new Exception('This is an error');", _classLoader);
        preparsedScript.invoke(emptyMap());
    }


    @Test
    public void return_param() throws Exception {
        final PreparsedScript f = new PreparsedScript("return x;", _classLoader);
        assertEquals("hurz", f.invoke(toMap("x", "hurz")));
        assertEquals("foo",  f.invoke(toMap("x", "foo")));
        assertEquals("bar",  f.invoke(toMap("this.x", "bar")));
        assertEquals("baz",  f.invoke(toMap("global.x", "baz")));
    }

    @Test
    public void return_method_invocation() throws Exception {
        final PreparsedScript f = new PreparsedScript(script(
                "int foo() { return bar; }",
                "return foo();"), _classLoader);
        assertEquals(42, f.invoke(toMap("bar", 42)));
        assertEquals(4711,  f.invoke(toMap("bar", 4711)));
        assertEquals(5,  f.invoke(toMap("bar", 5)));
    }

    @Test
    public void inner_class() throws Exception {
        final PreparsedScript f = new PreparsedScript(script(
                "import javax.crypto.*;",
                "import javax.crypto.interfaces.*;",
                "import javax.crypto.spec.*;",
                "if (foo != void) a = 'check';",
                "class Echo {",
                    "Object echo() {",
                        "return param;",
                     "}",
                "}",
                "return new Echo().echo();"),
                _classLoader
        );
        assertEquals("bla", f.invoke(toMap("param", "bla")));
        assertEquals("blubb", f.invoke(toMap("param", "blubb")));
    }


    @Test
    /** Run with serial garbage collector -XX:+UseSerialGC */
    public void multi_threaded() throws Exception {
        final AtomicInteger counter = new AtomicInteger();
        final String script = "return v;";
        final PreparsedScript f = new PreparsedScript(script, _classLoader);
        assertEquals("x", f.invoke(toMap("v", "x")));
        final Runnable runnable = new Runnable() {
            public void run() {
                final int value = counter.incrementAndGet();
                try {
                    assertEquals(value, f.invoke(toMap("v", value)));
                } catch (final EvalError evalError) {
                    throw new RuntimeException(evalError);
                }
            }
        };
        measureConcurrentTime(runnable, 5, 10, 100);
        assertEquals(1000, counter.get());
    }


    @Test
    public void param_with_name_result() throws Exception {
        final AtomicInteger result = new AtomicInteger();
        final PreparsedScript f = new PreparsedScript(
                "result.set(result.get() + 42);",
                _classLoader);
        f.invoke(toMap("result", result));
        assertEquals(42, result.get());
        f.invoke(toMap("result", result));
        assertEquals(84, result.get());
    }

    @Test
    public void testZero() throws Exception {
        final PreparsedScript f = new PreparsedScript("return 0 * 2;", _classLoader);
        assertEquals(0, f.invoke(emptyMap()));
    }

    @Test
    public void testZeroFloat() throws Exception {
        final PreparsedScript f = new PreparsedScript(
                "double d = 0.0; float f = (float) d; return f * 2;", _classLoader);
        assertEquals(0.0, f.invoke(emptyMap()));
    }
}
