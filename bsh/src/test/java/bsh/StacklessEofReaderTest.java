package bsh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import org.junit.Test;

public class StacklessEofReaderTest {

    @Test
    public void reused_reader_after_underlying_close_rethrows_cached_eof() throws Exception {
        StringReader in = new StringReader("");
        Reader reader = StacklessEofReader.wrap(in);
        char[] buf = new char[16];

        IOException first = readToEof(reader, buf);
        in.close();

        IOException second = readToEof(reader, buf);

        assertSame("cached EOF exception should be reused, not replaced", first, second);
        assertEquals(0, second.getStackTrace().length);
    }

    private static IOException readToEof(Reader reader, char[] buf) throws IOException {
        try {
            reader.read(buf);
            fail("expected end-of-input IOException");
            return null;
        } catch (IOException e) {
            return e;
        }
    }
}
