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
 * This file is part of the BeanShell Java Scripting distribution.           *
 * Documentation and updates may be found at http://www.beanshell.org/       *
 * Patrick Niemeyer (pat@pat.net)                                            *
 * Author of Learning Java, O'Reilly & Associates                            *
 *                                                                           *
 *****************************************************************************/

package bsh;

import org.junit.Test;
import org.junit.runner.RunWith;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import static bsh.TestUtil.eval;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(FilteredTestRunner.class)
public class GoogleReports {

	@Test
	@SuppressWarnings({"ConstantIfStatement"})
    public void while_loop() throws Exception {
		int loopCount = 0;
		do {
			loopCount++;
            if (loopCount > 100) {
                break;
            }
            if (true) {
                continue;
            }
		} while (false);
		assertEquals(1, loopCount);
        loopCount = (Integer) eval("int loopCount = 0;", "do{", "	loopCount++;", "	if (loopCount > 100) break;", "	if (true) continue;", "} while (false);", "return loopCount");
		assertEquals(1, loopCount);
        loopCount = (Integer) eval("int loopCount = 0;", "while (loopCount < 1) {", "	loopCount++;", "	if (loopCount > 100) return loopCount;", "	if (true) continue;", "}", "return loopCount");
		assertEquals(1, loopCount);
		assertEquals(Boolean.TRUE, eval("while(true) { break; return false; } return true;"));
		assertEquals(Boolean.TRUE, eval("do { break; return false; } while(true); return true;"));
        loopCount = (Integer) eval("int loopCount = 0;", "while (++loopCount < 2);", "return loopCount");
		assertEquals(2, loopCount);
        loopCount = (Integer) eval("int loopCount = 0;", "do { } while (++loopCount < 2);", "return loopCount");
		assertEquals(2, loopCount);
	}


	/**
     * <a href="http://code.google.com/p/beanshell2/issues/detail?id=6">issue#60</a>
     */
    @Test
    public void accessibility_issue_a() throws Exception {
        final Interpreter interpreter = new Interpreter();
        interpreter.set("x", this);
        Capabilities.setAccessibility(true);
        assertEquals("private-Integer", interpreter.eval("return x.issue6(new Integer(9));"));
        Capabilities.setAccessibility(false);
        assertEquals("public-Number", interpreter.eval("return x.issue6(new Integer(9));"));
    }


    /**
     * <a href="http://code.google.com/p/beanshell2/issues/detail?id=6">issue#60</a>
     */
    @Test
    public void accessibility_issue_b() throws Exception {
        final Interpreter interpreter = new Interpreter();
        interpreter.set("x", this);
        assertEquals("public-Number", interpreter.eval("return x.issue6(new Integer(9));"));
        Capabilities.setAccessibility(true);
        assertEquals("private-Integer", interpreter.eval("return x.issue6(new Integer(9));"));
    }


    /**
     * <a href="http://code.google.com/p/beanshell2/issues/detail?id=10">issue#10</a>
     */
    @Test(expected = ParseException.class)
    public void parse_error() throws Exception {
        eval("\1;");
    }


    /**
     * <a href="http://code.google.com/p/beanshell2/issues/detail?id=11">issue#11</a>
     */
    @Test
    public void return_in_try_block_does_not_return() throws Exception {
        assertEquals(
                "in try block",
                eval(
                    /*1*/ "try {",
                    /*2*/ "   return \"in try block\";",
                    /*3*/ "} finally {}" +
                    /*4*/ "return \"after try block\";"));
    }


    /**
     * <a href="http://code.google.com/p/beanshell2/issues/detail?id=12">issue#12</a>
	 */
	@Test
    public void override_method() throws Exception {
        assertEquals(
                "changed",
                eval(
                    /*1*/ "foo() { return \"original\";}",
                    /*2*/ "foo() { return \"changed\";}",
                    /*3*/ "return foo();"));

    }


    /**
     * <a href="http://code.google.com/p/beanshell2/issues/detail?id=32">issue#32</a>
     */
    @Test
    public void binary_operator_or() throws Exception {
        assertEquals(true, eval("return true | true"));
        assertEquals(true, eval("return true | false"));
        assertEquals(true, eval("return false | true"));
        assertEquals(false, eval("return false | false"));
    }


    /**
     * <a href="http://code.google.com/p/beanshell2/issues/detail?id=32">issue#32</a>
     */
    @Test
    public void binary_operator_and() throws Exception {
        assertEquals(true, eval("return true & true"));
        assertEquals(false, eval("return true & false"));
        assertEquals(false, eval("return false & true"));
        assertEquals(false, eval("return false & false"));
    }



    /**
     * <a href="http://code.google.com/p/beanshell2/issues/detail?id=32">issue#32</a>
     */
    @Test
    public void binary_operator_xor() throws Exception {
        assertEquals(false, eval("return true ^ true"));
        assertEquals(true, eval("return true ^ false"));
        assertEquals(true, eval("return false ^ true"));
        assertEquals(false, eval("return false ^ false"));
    }


    /*
    * helpers
    */
    private static String issue6(Integer ignored) {
        return "private-Integer";
    }


    public static String issue6(Number ignored) {
        return "public-Number";
	}

}
