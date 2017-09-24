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
package bsh.collection;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Iterator;

import bsh.CollectionManager;
import bsh.InterpreterError;

/**
 * This is the implementation of:
 * BshIterator - a dynamically loaded extension that supports the collections
 * API supported by JDK1.2 and greater.
 *
 * @author Daniel Leuck
 * @author Pat Niemeyer
 */
public class CollectionIterator implements bsh.BshIterator {

    /** The iterator. */
    private final Iterator iterator;

    /**
     * Construct a basic CollectionIterator.
     *
     * @param iterateOverMe
     *            the iterate over me
     * @throws java.lang.IllegalArgumentException
     *             If the argument is not a
     *             supported (i.e. iterable) type.
     * @throws java.lang.NullPointerException
     *             If the argument is null
     */
    public CollectionIterator(final Object iterateOverMe) {
        this.iterator = this.createIterator(iterateOverMe);
    }

    /**
     * Create an iterator over the given object.
     *
     * @param iterateOverMe
     *            Object of type Iterator, Collection,
     *            Iterable, or types supported by
     *            CollectionManager.BasicBshIterator
     * @return an Iterator
     * @throws IllegalArgumentException
     *             the illegal argument exception
     * @throws java.lang.IllegalArgumentException
     *             If the argument is not a
     *             supported (i.e. iterable) type.
     * @throws java.lang.NullPointerException
     *             If the argument is null
     */
    protected Iterator createIterator(final Object iterateOverMe)
            throws java.lang.IllegalArgumentException {
        if (iterateOverMe == null)
            throw new NullPointerException("Object arguments passed to "
                    + "the CollectionIterator constructor cannot be null.");
        if (iterateOverMe instanceof Iterator)
            return (Iterator) iterateOverMe;
        if (iterateOverMe instanceof Collection)
            return ((Collection) iterateOverMe).iterator();
        final Iterator it = this.getIteratorForIterable(iterateOverMe);
        if (it != null)
            return it;
        final CollectionManager.BasicBshIterator bbi = new CollectionManager.BasicBshIterator(
                iterateOverMe);
        return new Iterator() {

            public boolean hasNext() {
                return bbi.hasNext();
            }

            public Object next() {
                return bbi.next();
            }

            public void remove() {
                throw new UnsupportedOperationException(
                        "remove() is not supported");
            }
        };
    }

    /**
     * Get an Iterator for a Java 5 Iterable object.
     * Rather than resorting to another loadable module for Java 5 we'll
     * use reflection here to invoke the Iterable iterator() method.
     *
     * @author Daniel Leuck
     * @param iterateOverMe
     *            the iterate over me
     * @return the Iterator or null if the object is not an Iterable.
     */
    Iterator getIteratorForIterable(final Object iterateOverMe) {
        Iterator it = null;
        try {
            final Class c = Class.forName("java.lang.Iterable");
            if (c.isInstance(iterateOverMe))
                try {
                    final Method m = c.getMethod("iterator", new Class[0]);
                    it = (Iterator) m.invoke(iterateOverMe, new Object[0]);
                } catch (final Exception e) {
                    throw new InterpreterError("Unexpected problem calling "
                            + "\"iterator()\" on instance of java.lang.Iterable."
                            + e);
                }
        } catch (final ClassNotFoundException cnfe) {
            // we are pre-Java 5 and should continue without an exception
        }
        return it;
    }

    /**
     * Fetch the next object in the iteration.
     *
     * @return The next object
     */
    public Object next() {
        return this.iterator.next();
    }

    /**
     * Returns true if and only if there are more objects available
     * via the <code>next()</code> method.
     *
     * @return The next object
     */
    public boolean hasNext() {
        return this.iterator.hasNext();
    }
}
