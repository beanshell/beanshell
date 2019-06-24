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
import static java.lang.System.err;
import static java.lang.System.out;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.isA;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeFalse;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.AssumptionViolatedException;
import org.junit.Test;
import org.junit.runner.RunWith;

import junit.framework.TestCase;
import junit.framework.TestSuite;

/** Run the old test scripts inherited from beanshell. It's not always clear
 * what the test cases do so this will need some more investigations for failing
 * tests. */
@RunWith(AllTestsJUnit4Runner.class)
public class BshScriptTestCase {

    /** System property verbose */
    public static final boolean _VERBOSE =
        Boolean.valueOf(System.getProperty("verbose"));
    /** System property accessibility */
    public static final boolean _ACCESSIBILITY =
        Boolean.valueOf(System.getProperty("accessibility"));
    /** System property script */
    public static final String _SCRIPT = System.getProperty("script");
    /** The Constant baseDir. */
    public static final File _TEST_SCRIPTS_DIR = new File(
        "src/test/resources/test-scripts").getAbsoluteFile();
    static {
        // List KNOWN_FAILING_TESTS here
        //KNOWN_FAILING_TESTS.add("Fail.bsh");

        // Set capabilities accessibility
        Capabilities.instance.accept(_ACCESSIBILITY);
    }
    /** Constant padded spaces */
    private static final String[] _PADS = new String[] {"", " ", "  ", "   "};
    /** Constant tabs */
    private static final String[] _TABS = new String[] {"", "\t", "\t\t",
        "\t\t\t", "\t\t\t\t", "\t\t\t\t\t", "\t\t\t\t\t\t", "\t\t\t\t\t\t\t"};

    @Test
    public void testFailScript() throws Throwable {
        try {
            new Script("Fail.bsh").runTest();
        } catch (final AssertionError e) {
            assertThat("test expected to fail", e, isA(Error.class));
            assertThat("with message", e.getMessage(),
                    containsString("Test FAILED: Line: 11 : assert ( false )"));
            if ( _VERBOSE )
                System.out.println("success");
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /** Test suite entry method for framework.
     * @return the unit test populated test suite.
     * @throws Exception the exception */
    public static junit.framework.Test suite() throws Throwable {
        final TestSuite suite = new TestSuite();
        addTests(suite);
        return suite;
    }

    /** Adds the test scripts to the provided test suite.
     * @param suite the suite */
    private static void addTests(final TestSuite suite) {
        if ( !isUnderScrutiny(suite) ) {
            final File[] files = _TEST_SCRIPTS_DIR.listFiles(
                new FilenameFilter() {
                    public boolean accept(File dir, String name) {
                        return name.endsWith(".bsh")
                            && !"TestHarness.bsh".equals(name)
                            && !"RunAllTests.bsh".equals(name)
                            && !"Assert.bsh".equals(name)
                            && !"Fail.bsh".equals(name);
                    }
                });
            if ( _VERBOSE )
                Arrays.sort(files);
            if ( null != files ) for ( final File file : files )
                if ( file.isFile() )
                    suite.addTest(new Script(file));
        }
    }

    /** Checks if is under scrutiny. Helper function to simplify singling out
     * a specific test for scrutiny when fault finding. Simply assign a value
     * to trouble_maker and the other tests will go on hold.
     * @param suite the suite
     * @return true, if is under scrutiny */
    private static boolean isUnderScrutiny(final TestSuite suite) {
        // test file(s) under scrutiny
        final String trouble_maker = _SCRIPT;
        if (trouble_maker.isEmpty())
            return false;
        for (String f:trouble_maker.split(","))
            suite.addTest(new Script(f));
        return true;
    }

    /** The bsh script unit test case. */
    static class Script extends TestCase {
        /** The file. */
        private final File _file;
        /** script timer **/
        private long _start;
        /** skipped test **/
        private boolean _skipped = false;

        /** Instantiates a new test bsh script.
         * @param file the file */
        public Script(final File file) {
            super(file.getName());
            _file = file;
        }

        /** Instantiates a new test bsh script.
         * @param script the script file name */
        public Script(String script) {
            this(new File(_TEST_SCRIPTS_DIR, script));
        }

        /** Get the duration stop time padded with spaces. */
        private String stopTime() {
            long stop = (System.nanoTime() - _start) / 1000000;
            int cnt = 3 - (stop > 0 ? (int) Math.log10(stop) : 0);
            return _PADS[cnt] +  stop + "ms";
        }

        /** Determine if the exception is assumed for skipping a test.
         * Either thrown locally from the list of known failed scripts or by
         * script as the cause of the exception argument.
         * @param assumption the exception argument to check.
         * @throws Throwable re-thrown identified assumption. */
        private void skipAssumptions(Throwable assumption) throws Throwable {
            while ( null != assumption )
                if ( assumption instanceof AssumptionViolatedException ) {
                    if ( _VERBOSE ) {
                        System.out.println(stopTime() + " - SKIPPED *");
                        _skipped = true;
                    }
                    throw assumption;
                } else
                    assumption = assumption.getCause();
        }

        /** {@inheritDoc} */
        @Override
        public void runTest() throws Throwable {
            if ( _VERBOSE ) {
                int len = 64 - getName().length();
                int cnt = len / 8 - (len % 8 == 0 ? 1 : 0);
                System.out.print(getName() + _TABS[cnt] + " - ");
                _start = System.nanoTime();
            }

            try ( final Reader in = new InputStreamReader(
                    new FileInputStream(_file), StandardCharsets.UTF_8) ) {
                final Interpreter interpreter = new Interpreter(in, out, err, false);
                try {
                    assumeFalse("skipping test " + getName(), SKIP_KNOWN_ISSUES
                            && KNOWN_FAILING_TESTS.contains(getName()));

                    interpreter.sourceFileInfo = getName();
                    interpreter.set("bsh.cwd", _TEST_SCRIPTS_DIR.getPath());

                    interpreter.eval(in);
                } catch (final Throwable e) {
                    skipAssumptions(e);
                    if (!_SCRIPT.isEmpty())
                        e.printStackTrace(System.out);
                    throw new RuntimeException(getName(), e);
                }

                assertTrue("Test did not complete."+interpreter.get("test_message"),
                        (Boolean) interpreter.get("test_completed"));
                assertFalse(""+interpreter.get("test_message"),
                        (Boolean) interpreter.get("test_failed"));

            } catch (Throwable failed_exception) {
                if ( _VERBOSE ) {
                    if ( getName().equals("Fail.bsh") )
                        System.out.print(stopTime() + " - ");
                    else if ( !_skipped )
                        System.out.println(stopTime() + " - FAILED **");
                    _skipped = false;
                }
                throw failed_exception;
            }

            if ( _VERBOSE )
                System.out.println(stopTime() + " - success");
        }
    }
}
