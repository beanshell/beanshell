package bsh;

import org.junit.Test;

import java.io.File;

public class Issue_8_Test {

    @Test
    public void run_script_class3() throws Exception {
        new OldScriptsTest.TestBshScript( new File("src/test/resources/test-scripts/class3.bsh")).runTest();
    }

}
