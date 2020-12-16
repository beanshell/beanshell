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

import org.junit.Assert;
import org.junit.Test;

public class StringLiteralTest {

	private static final String ESCAPE_CHAR = "\\";

	private enum DelimiterMode {
		SINGLE_LINE("\""),

		MULTI_LINE("\"\"\"");

		private final String _delimiter;

		DelimiterMode(String delimiter) {
			_delimiter = delimiter;
		}


		public String delimiter() {
			return _delimiter;
		}
	}


	@Test
	public void parse_string_literal() throws Exception {
		assertStringParsing("test", DelimiterMode.SINGLE_LINE);
	}


	@Test
	public void parse_long_string_literal_singleline() throws Exception {
		assertStringParsing("test", DelimiterMode.MULTI_LINE);
	}


	@Test
	public void parse_string_literal_with_escaped_chars() throws Exception {
		assertStringParsing(
				"\\\n\t\r\"\'",
				ESCAPE_CHAR + '\\' +
				ESCAPE_CHAR + "n" +
				ESCAPE_CHAR + "t" +
				ESCAPE_CHAR + "r" +
				ESCAPE_CHAR + '"' +
				ESCAPE_CHAR + "'",
				DelimiterMode.SINGLE_LINE);
	}


	@Test
	public void parse_string_literal_with_special_chars_multiline() throws Exception {
		assertStringParsing(
				"\t\n\\\"\'",
				"\t\n\\\"\'",
				DelimiterMode.MULTI_LINE);
	}


	/** http://sourceforge.net/tracker/?func=detail&aid=1898217&group_id=4075&atid=104075 */
	@Test
	public void parse_unicode_literals() throws Exception {
		assertStringParsing("\u00FF", "\\u00FF", DelimiterMode.SINGLE_LINE);
	}


	@Test
	public void parse_long_string_literal_multiline() throws Exception {
		assertStringParsing("test\ntest", DelimiterMode.MULTI_LINE);
	}


	private void assertStringParsing(final String s, final DelimiterMode mode) throws EvalError {
		assertStringParsing(s, s, mode);
	}


	private void assertStringParsing(final String expected, final String source, final DelimiterMode mode) throws EvalError {
		final Interpreter interpreter = new Interpreter();
		Assert.assertEquals(expected, interpreter.eval("return " + mode.delimiter() + source + mode.delimiter() + ""));
	}

}
