package bsh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
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
        JavaCharStream in = new JavaCharStream(source);
        assertEquals('a', in.BeginToken());
        IOException end = readToEnd(in);
        assertEquals(0, end.getStackTrace().length);
        assertTrue("source should be closed by caller, not by the char stream", !source.closed);
        assertSame("end of input should reuse one exception", end, readToEnd(in));
    }

    @Test
    public void underlying_reader_can_be_reused_after_stackless_eof() throws Exception {
        StringReader source = new StringReader("x = 1;\ny = 2;\n");
        JavaCharStream in = new JavaCharStream(source);
        assertEquals("x = 1;\ny = 2;\n", drain(in));
        // reset and drain again: the char stream must not have closed source
        source.reset();
        in.ReInit(source);
        assertEquals("x = 1;\ny = 2;\n", drain(in));
    }

    @Test
    public void closed_reader_after_end_of_input_still_reports_end_of_input() throws Exception {
        StringReader source = new StringReader("");
        JavaCharStream in = new JavaCharStream(source);
        IOException first = readToEnd(in);
        source.close();
        IOException second = readToEnd(in);
        assertSame("cached end-of-input exception should be reused, not replaced", first, second);
        assertEquals(0, second.getStackTrace().length);
    }

    private static IOException readToEnd(JavaCharStream in) throws IOException {
        try {
            in.readChar();
            fail("Expected end of input");
            return null;
        } catch (IOException end) {
            return end;
        }
    }

    private static String drain(JavaCharStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        try {
            while (true)
                sb.append(in.readChar());
        } catch (IOException eof) {
            // the char stream signals end of input with an IOException
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
