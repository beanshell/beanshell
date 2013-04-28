package bsh;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(FilteredTestRunner.class)
public class Class13Test {

    @Test
    @Category(KnownIssue.class)
    public void run_script_class13() throws Exception {
        new OldScriptsTest.TestBshScript(new File("tests/test-scripts/class13.bsh")).runTest();
    }

}
