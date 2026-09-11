package bsh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(FilteredTestRunner.class)
public class ParserInputTest {

    private static final class TrackingReader extends StringReader {
        private int readsAfterEnd;
        private boolean ended;
        private boolean closed;

        TrackingReader(String s) {
            super(s);
        }

        @Override
        public int read(char[] buf, int off, int len) throws IOException {
            if (ended)
                readsAfterEnd++;
            int n = super.read(buf, off, len);
            if (n == -1)
                ended = true;
            return n;
        }

        @Override
        public void close() {
            closed = true;
            super.close();
        }
    }

    @Test
    public void eval_stops_reading_input_at_end_of_stream() throws Exception {
        Interpreter interpreter = new Interpreter();
        TrackingReader in = new TrackingReader("x = 1;\ny = x + 1;\n");
        interpreter.eval(in, interpreter.getNameSpace(), "test");
        assertEquals(Integer.valueOf(2), interpreter.get("y"));
        assertTrue("eval should not close a reader it does not own", !in.closed);
        // The wrapper probes the underlying reader once after EOF to support
        // resettable/reusable readers.
        assertEquals("reads after end of stream", 1, in.readsAfterEnd);
    }

    @Test
    public void end_of_input_is_signalled_without_a_stack_trace() throws Exception {
        TrackingReader source = new TrackingReader("a");
        Reader in = StacklessEofReader.wrap(source);
        char[] buf = new char[4];
        assertEquals(1, in.read(buf, 0, buf.length));
        try {
            in.read(buf, 0, buf.length);
            fail("Expected end of input");
        } catch (IOException end) {
            assertEquals(0, end.getStackTrace().length);
            assertTrue("source should be closed by caller, not by wrapper", !source.closed);
            try {
                in.read(buf, 0, buf.length);
                fail("Expected end of input");
            } catch (IOException again) {
                assertSame(end, again);
            }
        }
        // The wrapper probes the underlying reader again after EOF to support
        // resettable readers, so one extra read after end is expected.
        assertEquals("reads after end of stream", 1, source.readsAfterEnd);
        assertSame(in, StacklessEofReader.wrap(in));
    }

    @Test
    public void underlying_reader_can_be_reused_after_stackless_eof() throws Exception {
        StringReader source = new StringReader("x = 1;\ny = 2;\n");
        Reader wrapped = StacklessEofReader.wrap(source);
        char[] buf = new char[64];
        // drain first parse: the wrapper signals EOF by throwing IOException
        drain(wrapped, buf);
        // reset and drain again: the wrapper must not have closed source
        source.reset();
        assertEquals("x = 1;\ny = 2;\n", drain(wrapped, buf));
    }

    private static String drain(Reader in, char[] buf) throws IOException {
        StringBuilder sb = new StringBuilder();
        try {
            int n;
            while ((n = in.read(buf, 0, buf.length)) != -1)
                sb.append(buf, 0, n);
        } catch (IOException eof) {
            // StacklessEofReader signals end of input with an IOException.
        }
        return sb.toString();
    }

    @Test
    public void interpreter_can_eval_twice_from_reusable_reader() throws Exception {
        Interpreter interpreter = new Interpreter();
        StringReader in = new StringReader("a = 1;\nb = 2;\n");
        interpreter.eval(in, interpreter.getNameSpace(), "test1");
        assertEquals(Integer.valueOf(1), interpreter.get("a"));
        assertEquals(Integer.valueOf(2), interpreter.get("b"));
        in.reset();
        interpreter.eval(in, interpreter.getNameSpace(), "test2");
        assertEquals(Integer.valueOf(1), interpreter.get("a"));
        assertEquals(Integer.valueOf(2), interpreter.get("b"));
    }
}
