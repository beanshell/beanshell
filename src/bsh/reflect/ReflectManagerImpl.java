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
 *                                                                           *
 * This file is part of the BeanShell Java Scripting distribution.           *
 * Documentation and updates may be found at http://www.beanshell.org/       *
 * Patrick Niemeyer (pat@pat.net)                                            *
 * Author of Learning Java, O'Reilly & Associates                            *
 *                                                                           *
 *****************************************************************************/


package bsh.reflect;

import bsh.ReflectManager;
import java.lang.reflect.AccessibleObject;

/**
	This is the implementation of:
	ReflectManager - a dynamically loaded extension that supports extended
	reflection features supported by JDK1.2 and greater.

	In particular it currently supports accessible method and field access 
	supported by JDK1.2 and greater.
*/
public class ReflectManagerImpl extends ReflectManager
{
	/**
		Set a java.lang.reflect Field, Method, Constructor, or Array of
		accessible objects to accessible mode.
		If the object is not an AccessibleObject then do nothing.
		@return true if the object was accessible or false if it was not.
	*/
// Arrays incomplete... need to use the array setter
	public boolean setAccessible( Object obj ) 
	{
		if ( obj instanceof AccessibleObject ) {
			((AccessibleObject)obj).setAccessible(true);
			return true;
		} else
			return false;
	}
}

