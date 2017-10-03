package bsh;

import org.junit.Test;

import java.io.File;

public class Issue_7_Test {

    @Test
    public void run_script_class13() throws Exception {
        new OldScriptsTest.TestBshScript( new File("src/test/resources/test-scripts/class13.bsh")).runTest();
    }

}
