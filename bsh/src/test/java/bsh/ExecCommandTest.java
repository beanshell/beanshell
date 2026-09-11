package bsh;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(FilteredTestRunner.class)
public class ExecCommandTest {

    private Interpreter bsh;
    private ByteArrayOutputStream out;

    /** The running JVM is the one external application every supported
        platform is guaranteed to have, and java -version writes to stderr. */
    private static String java() {
        return new File(new File(System.getProperty("java.home"), "bin"), "java").getPath();
    }

    @Before
    public void setUp() {
        assumeTrue("java executable is present", new File(java()).canExecute());
        out = new ByteArrayOutputStream();
        bsh = new Interpreter();
        bsh.setOut(new PrintStream(out));
    }

    @Test
    public void exec_prints_what_the_application_writes_to_stderr() throws Exception {
        Object status = bsh.eval("exec(new String[] { \"" + java() + "\", \"-version\" });");
        assertEquals(0, status);
        assertThat(out.toString(), containsString("version"));
    }

    @Test
    public void exec_returns_the_exit_status() throws Exception {
        assertEquals(0, bsh.eval("exec(new String[] { \"" + java() + "\", \"-version\" });"));
        assertThat(bsh.eval("exec(new String[] { \"" + java() + "\", \"--no-such-option\" });"),
                not(equalTo(0)));
    }

    @Test
    public void exec_splits_the_string_form_on_whitespace() throws Exception {
        assertEquals(0, bsh.eval("exec(\"" + java() + " -version\");"));
        assertThat(out.toString(), containsString("version"));
    }
}
