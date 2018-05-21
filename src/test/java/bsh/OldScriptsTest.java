/*****************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one                *
 * or more contributor license agreements.  See the NOTICE file              *
 * distributed with this work for additional information                     *
 * regarding copyright ownership.  The ASF licenses this file                *
 * to you under the Apache License, Version 2.0 (the                         *
 * "License"); you may not use this file except in compliance                *
 * with the License.  You may obtain a copy of the License at                *
 *                                                                           *
 *     http://www.apache.org/licenses/LICENSE-2.0                            *
 *                                                                           *
 * Unless required by applicable law or agreed to in writing,                *
 * software distributed under the License is distributed on an               *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY                    *
 * KIND, either express or implied.  See the License for the                 *
 * specific language governing permissions and limitations                   *
 * under the License.                                                        *
 *                                                                           *
/****************************************************************************/
package bsh;

import static bsh.KnownIssue.KNOWN_FAILING_TESTS;
import static bsh.KnownIssue.SKIP_KNOWN_ISSUES;

import java.io.File;
import java.io.FileReader;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import junit.framework.TestCase;
import junit.framework.TestSuite;

/** Run the old test scripts inherited from beanshell. It's not always clear
 * what the test cases do so this will need some more investigations for failing
 * tests. */
@RunWith(AllTestsJUnit4Runner.class)
public class OldScriptsTest {

    /** The Constant baseDir. */
    private static final File baseDir = new File(
            "src/test/resources/test-scripts");
    static {
        KNOWN_FAILING_TESTS.add("blockscope.bsh");
        KNOWN_FAILING_TESTS.add("class.bsh");
        KNOWN_FAILING_TESTS.add("class3.bsh");
        KNOWN_FAILING_TESTS.add("classinner.bsh");
        KNOWN_FAILING_TESTS.add("classinterf1.bsh");
        KNOWN_FAILING_TESTS.add("classser.bsh");
        KNOWN_FAILING_TESTS.add("commands.bsh");
        KNOWN_FAILING_TESTS.add("externalNameSpace.bsh");
        KNOWN_FAILING_TESTS.add("forscope.bsh");
        KNOWN_FAILING_TESTS.add("forscope4.bsh");
        KNOWN_FAILING_TESTS.add("iftest.bsh");
        KNOWN_FAILING_TESTS.add("newscoping.bsh");
        KNOWN_FAILING_TESTS.add("tryscope.bsh");
    }

    @Test
    public void testFailScript() throws Exception {
        try {
            new TestBshScript(new File(baseDir, "Fail.bsh")).runTest();
            Assert.fail("Fail.bsh should fail!");
        } catch (final AssertionError e) {
            // expected
        }
    }
    /** Suite.
     * @return the junit.framework. test
     * @throws Exception the exception */
    public static junit.framework.Test suite() throws Exception {
        final TestSuite suite = new TestSuite();
        addTests(suite);
        return suite;
    }

    /** Adds the tests.
     * @param suite the suite */
    private static void addTests(final TestSuite suite) {
        final File[] files = baseDir.listFiles();
        if (!isUnderScrutiny(suite) && files != null)
            for (final File file : files) {
                final String name = file.getName();
                if (file.isFile() && name.endsWith(".bsh")
                        && !"TestHarness.bsh".equals(name)
                        && !"RunAllTests.bsh".equals(name)
                        && !"Fail.bsh".equals(name)) {
                    suite.addTest(new TestBshScript(file));
                }
            }
    }

    /** Checks if is under scrutiny. Helper function to simplify singling out
     * a specific test for scrutiny when fault finding. Simply assign a value
     * to trouble_maker and the other tests will go on hold.
     * @param suite the suite
     * @return true, if is under scrutiny */
    private static boolean isUnderScrutiny(final TestSuite suite) {
        final String trouble_maker = System.getProperty("script"); // test file(s) under scrutiny
        if (trouble_maker.isEmpty())
            return false;
        for (String f:trouble_maker.split(","))
            suite.addTest(new TestBshScript(new File(baseDir, f)));
        return true;
    }

    /** The Class TestBshScript. */
    static class TestBshScript extends TestCase {

        /** The file. */
        private final File _file;

        /** Instantiates a new test bsh script.
         * @param file the file */
        public TestBshScript(final File file) {
            this._file = file;
        }

        /** {@inheritDoc} */
        @Override
        public String getName() {
            return this._file.getName();
        }

        /** {@inheritDoc} */
        @Override
        public void runTest() throws Exception {
            Capabilities.setAccessibility(Boolean.valueOf(System.getProperty("accessibility")));

            Assume.assumeFalse("skipping test " + getName(), SKIP_KNOWN_ISSUES
                    && KNOWN_FAILING_TESTS.contains(getName()));

            final Interpreter interpreter = new Interpreter();
            final String path = '\"' + this._file.getParentFile()
                    .getAbsolutePath().replace('\\', '/') + '\"';
            interpreter.eval("path=" + path + ';');
            interpreter.eval("cd(" + path + ");");
            try {
                interpreter.eval(new FileReader(this._file));
            } catch (final Exception e) {
                if (!System.getProperty("script").isEmpty())
                    e.printStackTrace(System.out);
                throw new RuntimeException(getName(), e);
            }
            assertTrue("Test did not complete."+interpreter.get("test_message"),
                    (Boolean)interpreter.get("test_completed"));
            assertFalse(""+interpreter.get("test_message"),
                    (Boolean)interpreter.get("test_failed"));

        }
    }
}
