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

import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.lang.reflect.Array;

/**
	The default CollectionManager
	supports iteration over objects of type:
	Enumeration, Iterator, Iterable, CharSequence, and array.
*/
public final class CollectionManager
{
	private static final CollectionManager manager = new CollectionManager();

	public synchronized static CollectionManager getCollectionManager()
	{
		return manager;
	}

	/**
	*/
	public boolean isBshIterable( Object obj ) 
	{
		// This could be smarter...
		try { 
			getBshIterator( obj ); 
			return true;
		} catch( IllegalArgumentException e ) { 
			return false;
		}
	}

	public Iterator getBshIterator( Object obj ) 
		throws IllegalArgumentException
	{
		if(obj==null)
			throw new NullPointerException("Cannot iterate over null.");

		if (obj instanceof Enumeration) {
			final Enumeration enumeration = (Enumeration)obj;
			return new Iterator<Object>() {
				public boolean hasNext() {
					return enumeration.hasMoreElements();
				}
				public Object next() {
					return enumeration.nextElement();
				}
				public void remove() {
					throw new UnsupportedOperationException();
				}
			};
		}

		if (obj instanceof Iterator)
			return (Iterator)obj;

		if (obj instanceof Iterable)
			return ((Iterable)obj).iterator();

		if (obj.getClass().isArray()) {
			final Object array = obj;
			return new Iterator() {
				private int index = 0;
				private final int length = Array.getLength(array);

				public boolean hasNext() {
					return index < length;
				}
				public Object next() {
					return Array.get(array, index++);
				}
				public void remove() {
					throw new UnsupportedOperationException();
				}
			};
		} 
		
		if (obj instanceof CharSequence)
			return getBshIterator(
				obj.toString().toCharArray());

		throw new IllegalArgumentException(
			"Cannot iterate over object of type "+obj.getClass());
	}

	public boolean isMap( Object obj ) {
		return obj instanceof Map;
	}

	public Object getFromMap( Object map, Object key ) {
		return ((Map)map).get(key);
	}

	public Object putInMap( Object map, Object key, Object value ) 
	{
		return ((Map)map).put(key, value);
	}

}
