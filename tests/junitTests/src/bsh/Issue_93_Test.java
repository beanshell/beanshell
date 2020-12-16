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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static bsh.TestUtil.eval;
import static junit.framework.Assert.assertEquals;

public class Issue_93_Test {

	@Test
	public void try_catch_finally() throws Exception {
		final List<String> calls = new ArrayList<String>();
		final Object result = eval(
				Collections.singletonMap("calls", calls),
				"calls.add(\"start\");",
				"try {",
				"	calls.add(\"try\");",
				"} catch (Exception e) {",
				"	calls.add(\"catch\");",
				"} finally {",
				"	calls.add(\"finally\");",
				"}",
				"calls.add(\"after\");",
				"return \"return after try..catch..finally\";"
		);
		assertEquals("return after try..catch..finally", result);
		assertEquals("calls are :" + calls.toString(),
				Arrays.asList("start", "try", "finally", "after"),
				calls);
	}

	@Test
	public void execute_finally_when_try_block_contains_return() throws Exception {
		final List<String> calls = new ArrayList<String>();
		final Object result = eval(
				Collections.singletonMap("calls", calls),
				"calls.add(\"start\");",
				"try {",
				"	calls.add(\"try\");",
				"	return \"return from try\";",
				"} catch (Exception e) {",
				"	calls.add(\"catch\");",
				"} finally {",
				"	calls.add(\"finally\");",
				"}",
				"calls.add(\"after\");",
				"return \"return after try..catch..finally\";"
		);
		assertEquals("return from try", result);
		assertEquals("calls are :" + calls.toString(),
				Arrays.asList("start", "try", "finally"),
				calls);
	}


	@Test
	public void execute_finally_block_when_catch_block_throws_exception() throws Exception {
		final List<String> calls = new ArrayList<String>();
		final Object result = eval(
				Collections.singletonMap("calls", calls),
				"calls.add(\"start\");",
				"try {",
				"	calls.add(\"try\");",
				"	throw new Exception(\"inside try\");",
				"} catch (Exception e) {",
				"	calls.add(\"catch\");",
				"	throw new Exception(\"inside catch\");",
				"} finally {",
				"	calls.add(\"finally\");",
				"	return \"return from finally\";",
				"}",
				"calls.add(\"after\");",
				"return \"return after try..catch..finally\";"
		);
		assertEquals("return from finally", result);
		assertEquals("calls are :" + calls.toString(),
				Arrays.asList("start", "try", "catch", "finally"),
				calls);
	}


	@Test
	public void execute_finally_block_when_catch_block_contains_return_statement() throws Exception {
		final List<String> calls = new ArrayList<String>();
		final Object result = eval(
				Collections.singletonMap("calls", calls),
				"calls.add(\"start\");",
				"try {",
				"	calls.add(\"try\");",
				"	throw new Exception(\"inside try\");",
				"} catch (Exception e) {",
				"	calls.add(\"catch\");",
				"	return \"return from catch\";",
				"} finally {",
				"	calls.add(\"finally\");",
				"	return \"return from finally\";",
				"}",
				"calls.add(\"after\");",
				"return \"return after try..catch..finally\";"
		);
		assertEquals("return from finally", result);
		assertEquals("calls are :" + calls.toString(),
				Arrays.asList("start", "try", "catch", "finally"),
				calls);
	}


	@Test
	public void execute_finally_block_when_try_block_contains_return_statement() throws Exception {
		final Object result = eval(
				"try {",
				"	return \"return from try\";",
				"} finally {",
				"	return \"return from finally\";",
				"}",
				"return \"return after try..finally\";"
		);
		assertEquals("return from finally", result);
	}

}
