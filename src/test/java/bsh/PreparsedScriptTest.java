package bsh;

import static org.junit.Assert.assertEquals;

import java.net.URL;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

public class PreparsedScriptTest {

    private ClassLoader _classLoader = new ClassLoader() {
        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
//            System.out.println("find class " + name);
            // Thread.dumpStack();
            return super.findClass(name);
        }


        @Override
        protected URL findResource(String name) {
//            System.out.println("find resource " + name);
            return super.findResource(name);
        }
    };


    @Test
    public void x() throws Exception {
        final PreparsedScript preparsedScript = new PreparsedScript("", _classLoader);
        preparsedScript.invoke(Collections.<String, Object>emptyMap());
    }


    @Test
    public void y() throws Exception {
        final PreparsedScript f = new PreparsedScript("return x;", _classLoader);
        assertEquals("hurz", f.invoke(Collections.singletonMap("x", "hurz")));
        assertEquals("foo", f.invoke(Collections.singletonMap("x", "foo")));
    }


    @Test
    public void z() throws Exception {
        final PreparsedScript f = new PreparsedScript(
                "import javax.crypto.*;" +
                "import javax.crypto.interfaces.*;" +
                "import javax.crypto.spec.*;" +
                "if (foo != void) a = \"check\";" +
                "class Echo {\n" +
                "\n" +
                "   Object echo() {\n" +
                "      return param;\n" +
                "   }\n" +
                "\n" +
                "}\n" +
                "\n" +
                "return new Echo().echo();",
                _classLoader
        );
        assertEquals("bla", f.invoke(Collections.singletonMap("param", "bla")));
//        System.out.println("second call");
        assertEquals("blubb", f.invoke(Collections.singletonMap("param", "blubb")));
    }


    @Test
    public void multi_threaded() throws Exception {
        final AtomicInteger counter = new AtomicInteger();
        final String script = "return v;";
        final PreparsedScript f = new PreparsedScript(script, _classLoader);
        Assert.assertEquals("x", f.invoke(Collections.singletonMap("v", "x")));
        final Runnable runnable = new Runnable() {
            public void run() {
                final int value = counter.incrementAndGet();
                try {
                    Assert.assertEquals(value, f.invoke(Collections.singletonMap("v", value)));
                } catch (final EvalError evalError) {
                    throw new RuntimeException(evalError);
                }
            }
        };
//        final long time = TestUtil.measureConcurrentTime(runnable, 30, 30, 1000);
//        System.out.println(TimeUnit.NANOSECONDS.toMillis(time));
    }


    @Test
    public void param_with_name_result() throws Exception {
        final AtomicInteger result = new AtomicInteger();
        final PreparsedScript f = new PreparsedScript(
                "result.set(result.get() + 42);",
                _classLoader);
        f.invoke(Collections.singletonMap("result", result));
        Assert.assertEquals(42, result.get());
        f.invoke(Collections.singletonMap("result", result));
        Assert.assertEquals(84, result.get());
    }

    @Test
    public void testZero() throws Exception {
        final PreparsedScript f = new PreparsedScript("return 0 * 2;",_classLoader);
        assertEquals(0, f.invoke(Collections.emptyMap()));
    }

    @Test
    public void testZeroFloat() throws Exception {
        final PreparsedScript f = new PreparsedScript("double d = 0.0;float f = (float) d; return f * 2;",_classLoader);
        assertEquals(0, f.invoke(Collections.emptyMap()));
    }
}
