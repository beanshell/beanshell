package bsh;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import static bsh.TestUtil.eval;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(FilteredTestRunner.class)
public class Project_Coin_Test {


    @Test
    @Category(ProjectCoinFeature.class)
    public void try_with_resource_parsing() throws Exception {
        eval(
                "try (ByteArrayOutputStream x = new ByteArrayOutputStream()) {",
                "} catch (Exception e) {",
                "}\n"
        );
        eval(
                "try (ByteArrayOutputStream x = new ByteArrayOutputStream(); ByteArrayOutputStream y = new ByteArrayOutputStream()) {",
                "} catch (Exception e) {",
                "}\n"
        );
        eval(
                "try (x = new ByteArrayOutputStream(); y = new ByteArrayOutputStream()) {",
                "} catch (Exception e) {",
                "}\n"
        );
    }


    @Test
    @Category(ProjectCoinFeature.class)
    public void try_with_resource() throws Exception {
        final Interpreter interpreter = new Interpreter();
        final AtomicBoolean closed = new AtomicBoolean(false);
        final IOException fromWrite = new IOException("exception from write");
        final IOException fromClose = new IOException("exception from close");
        final OutputStream autoclosable = new OutputStream() {

            @Override
            public void write(final int b) throws IOException {
                throw fromWrite;
            }


            @Override
            public void close() throws IOException {
                closed.set(true);
                throw fromClose;
            }
        };
        try {
            interpreter.set("autoclosable", autoclosable);
            interpreter.eval(
                    "try (x = new BufferedOutputStream(autoclosable)) {\n" +
                    "   x.write(42);\n" +
                    "} catch (Exception e) {\n" +
                    "   thrownException = e;\n" +
                    "}\n"
            );
            fail("expected exception");
        } catch (final EvalError evalError) {
            if (evalError instanceof ParseException) {
                throw evalError;
            }
            final Throwable e = evalError.getCause();
            assertSame(fromWrite, e);
            interpreter.set("exception", e);
            final Object suppressed = interpreter.eval("return exception.getSuppressed();"); // avoid java 7 syntax in java code ;)
            assertSame(fromClose, suppressed);
        }
        assertTrue("stream should be closed", closed.get());
    }


    @Test
    public void switch_on_strings() throws Exception {
        final Object result = eval(
                    "switch(\"hurz\") {\n",
                    "   case \"bla\": return 1;",
                    "   case \"foo\": return 2;",
                    "   case \"hurz\": return 3;",
                    "   case \"igss\": return 4;",
                    "   default: return 5;",
                    "}\n");
        assertEquals(result, 3);
    }

}
