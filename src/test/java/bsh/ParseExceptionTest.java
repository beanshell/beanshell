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

import static bsh.TestUtil.eval;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(FilteredTestRunner.class)
public class ParseExceptionTest {

    @Test
    public void message_only_constructor_returns_exact_message() {
        ParseException pe = new ParseException("Parse error at line 3 : bad thing");
        assertEquals("Parse error at line 3 : bad thing", pe.getMessage());
    }

    @Test
    public void message_only_constructor_error_line_number_is_negative_one() {
        ParseException pe = new ParseException("some message");
        assertEquals(-1, pe.getErrorLineNumber());
    }

    @Test
    public void message_only_constructor_with_debug_enabled_does_not_throw() {
        Interpreter.DEBUG.set(true);
        try {
            ParseException pe = new ParseException("some message");
            assertEquals("some message", pe.getMessage());
        } finally {
            Interpreter.DEBUG.set(false);
        }
    }

    /** Token-carrying parse errors (raised by the generated parser itself) keep
     *  their existing "Unable to parse code syntax..." format and line number. */
    @Test
    public void token_carrying_parse_error_message_is_unchanged() throws Exception {
        try {
            eval("x = 2 + ;");
            fail("expected a ParseException");
        } catch (bsh.ParseException pe) {
            assertThat(pe.getMessage(), startsWith("Unable to parse code syntax. Encountered:"));
            assertThat(pe.getMessage(), containsString("at line"));
            assertThat(pe.getMessage(), containsString("column"));
            assertEquals(1, pe.getErrorLineNumber());
        }
    }

    /** Modifier-conflict errors (e.g. issue #839) go through the message-only
     *  ParseException constructor, so currentToken is null; getMessage() must
     *  return the real message instead of the bare "Encountered:" header, and
     *  getErrorLineNumber() must not throw. */
    @Test
    public void modifier_conflict_parse_error_has_useful_message() throws Exception {
        try {
            eval("private public int x = 1;");
            fail("expected a ParseException");
        } catch (bsh.ParseException pe) {
            assertThat(pe.getMessage(), not(equalToBareHeader()));
            assertThat(pe.getMessage(), containsString("public/private/protected cannot be used in combination"));
            assertEquals(-1, pe.getErrorLineNumber());
        }
    }

    private static org.hamcrest.Matcher<String> equalToBareHeader() {
        return org.hamcrest.Matchers.equalTo("Unable to parse code syntax. Encountered:");
    }
}
