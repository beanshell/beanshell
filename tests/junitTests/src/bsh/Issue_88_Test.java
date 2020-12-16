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

import static bsh.TestUtil.eval;
import static junit.framework.Assert.assertEquals;

@RunWith(FilteredTestRunner.class)
@SuppressWarnings("UnusedDeclaration")
public class Issue_88_Test {

	@Test
	public void call_of_public_inherited_method_from_non_public_class_without_accessibilty() throws Exception {
		Capabilities.setAccessibility(false);
		final Interpreter interpreter = new Interpreter();
		interpreter.set("x", new Implementation());
		assertEquals("public String", interpreter.eval("x.method(\"foo\");"));
	}


	@Test
	public void call_of_public_inherited_method_from_non_public_class_with_accessibilty() throws Exception {
		Capabilities.setAccessibility(true);
		final Interpreter interpreter = new Interpreter();
		interpreter.set("x", new Implementation());
		assertEquals("public String", interpreter.eval("x.method(\"foo\");"));
	}


	@Test
	public void community_test_cases() throws Exception {
		assertEquals(0, eval("Collections.unmodifiableList(new ArrayList()).size();"));
		assertEquals(0, eval("new HashMap().entrySet().size();"));
		assertEquals(Boolean.FALSE, eval("new HashMap().keySet().iterator().hasNext();"));
	}


	public interface Public {
		Object method(String param);
	}


	public interface PublicWithoutMethod extends Public {
	}


	private abstract class AbstractImplementation implements PublicWithoutMethod {

		public Object method(final String param) {
			return "public String";
		}

		private Object method(final Object param) {
			return "private Object";
		}

	}


	public class Implementation extends AbstractImplementation {

		public Object method(final CharSequence param) {
			return "public CharSequence";
		}


		public Object method(final Object param) {
			return "public Object";
		}

	}

}
