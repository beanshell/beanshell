/*****************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one				*
 * or more contributor license agreements.  See the NOTICE file			  *
 * distributed with this work for additional information					 *
 * regarding copyright ownership.  The ASF licenses this file				*
 * to you under the Apache License, Version 2.0 (the						 *
 * "License"); you may not use this file except in compliance				*
 * with the License.  You may obtain a copy of the License at				*
 *																		   *
 *	 http://www.apache.org/licenses/LICENSE-2.0							*
 *																		   *
 * Unless required by applicable law or agreed to in writing,				*
 * software distributed under the License is distributed on an			   *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY					*
 * KIND, either express or implied.  See the License for the				 *
 * specific language governing permissions and limitations				   *
 * under the License.														*
 *																		   *
 * This file is part of the BeanShell Java Scripting distribution.		   *
 * Documentation and updates may be found at								 *
 *	  https://github.com/beanshell/beanshell								 *
 *																		   *
 * This file is part of the BeanShell Java Scripting distribution.		   *
 * Documentation and updates may be found at http://www.beanshell.org/	   *
 * Patrick Niemeyer (pat@pat.net)											*
 * Author of Learning Java, O'Reilly & Associates							*
 *																		   *
 *****************************************************************************/

package bsh;


import static bsh.TestUtil.eval;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RunWith(FilteredTestRunner.class)
public class ReflectTest {

	/**
	 * See {@link bsh.SourceForgeIssuesTest#sourceforge_issue_2562805()}.
	 * This test checks the resolving of PrintStream.println(null). This may be resolved to
	 * {@link java.io.PrintStream#println(char[])}, depending on the ordering of the methods when using reflection.
	 * This will result in a {@code NullPointerException}.
	 */
	@Test
	public void findMostSpecificSignature() {
		int value = Reflect.findMostSpecificSignature(new Class[]{null}, new Class[][]{
			{Double.TYPE}, {char[].class}, {String.class}, {Object.class}
		});
		assertEquals("most specific String class", 2, value);
		value = Reflect.findMostSpecificSignature(new Class[]{null}, new Class[][]{
			{Double.TYPE}, {char[].class}, {Object.class}, {String.class}
		});
		assertEquals("most specific String class", 3, value);
		value = Reflect.findMostSpecificSignature(new Class[]{null}, new Class[][]{
			{Double.TYPE}, {char[].class}, {Integer.class}, {String.class}
		});
		assertEquals("most specific String class", 3, value);
		value = Reflect.findMostSpecificSignature(new Class[]{null}, new Class[][]{
			{Double.TYPE}, {char[].class}, {Number.class}, {Integer.class}
		});
		assertEquals("most specific Integer class", 3, value);
		value = Reflect.findMostSpecificSignature(new Class[]{null}, new Class[][]{
			{Double.TYPE}, {char[].class}, {Object.class}, {Boolean.TYPE}
		});
		assertEquals("most specific Object class", 2, value);
		value = Reflect.findMostSpecificSignature(new Class[]{null}, new Class[][]{
			{Double.TYPE}, {char[].class}, {Boolean.TYPE}
		});
		assertEquals("most specific char[] class", 1, value);
	}
}
