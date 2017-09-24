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
package bsh;

import java.lang.reflect.Array;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

/**
 * The default CollectionManager (which remains Java 1.1 compatible)
 * supports iteration over objects of type:
 * Enumeration, Vector, String, StringBuffer and array.
 * The dynamically loaded CollectionManagerImpl supports additional types when
 * it is present.
 *
 * @see BshIterable.java
 */
public class CollectionManager {

    /** The manager. */
    private static CollectionManager manager;

    /**
     * Gets the collection manager.
     *
     * @return the collection manager
     */
    public static synchronized CollectionManager getCollectionManager() {
        if (manager == null
                && Capabilities.classExists("java.util.Collection")) {
            Class clas;
            try {
                clas = Class.forName("bsh.collection.CollectionManagerImpl");
                manager = (CollectionManager) clas.newInstance();
            } catch (final Exception e) {
                Interpreter.debug("unable to load CollectionManagerImpl: " + e);
            }
        }
        if (manager == null)
            manager = new CollectionManager(); // default impl
        return manager;
    }

    /**
     * Checks if is bsh iterable.
     *
     * @param obj
     *            the obj
     * @return true, if is bsh iterable
     */
    public boolean isBshIterable(final Object obj) {
        // This could be smarter...
        try {
            this.getBshIterator(obj);
            return true;
        } catch (final IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Gets the bsh iterator.
     *
     * @param obj
     *            the obj
     * @return the bsh iterator
     * @throws IllegalArgumentException
     *             the illegal argument exception
     */
    public BshIterator getBshIterator(final Object obj)
            throws IllegalArgumentException {
        return new BasicBshIterator(obj);
    }

    /**
     * Checks if is map.
     *
     * @param obj
     *            the obj
     * @return true, if is map
     */
    public boolean isMap(final Object obj) {
        return obj instanceof Hashtable;
    }

    /**
     * Gets the from map.
     *
     * @param map
     *            the map
     * @param key
     *            the key
     * @return the from map
     */
    public Object getFromMap(final Object map, final Object key) {
        return ((Hashtable) map).get(key);
    }

    /**
     * Put in map.
     *
     * @param map
     *            the map
     * @param key
     *            the key
     * @param value
     *            the value
     * @return the object
     */
    public Object putInMap(final Object map, final Object key,
            final Object value) {
        return ((Hashtable) map).put(key, value);
    }

    /**
     * Determine dynamically if the target is an iterator by the presence of a
     * pair of next() and hasNext() methods.
     * public static boolean isIterator() { }
     **
     * An implementation that works with JDK 1.1
     */
    public static class BasicBshIterator implements BshIterator {

        /** The enumeration. */
        Enumeration enumeration;

        /**
         * Construct a basic BasicBshIterator.
         *
         * @param iterateOverMe
         *            the iterate over me
         * @throws java.lang.IllegalArgumentException
         *             If the argument is not a
         *             supported (i.e. iterable) type.
         * @throws java.lang.NullPointerException
         *             If the argument is null
         */
        public BasicBshIterator(final Object iterateOverMe) {
            this.enumeration = this.createEnumeration(iterateOverMe);
        }

        /**
         * Create an enumeration over the given object.
         *
         * @param iterateOverMe
         *            Object of type Enumeration, Vector, String,
         *            StringBuffer or an array
         * @return an enumeration
         * @throws java.lang.IllegalArgumentException
         *             If the argument is not a
         *             supported (i.e. iterable) type.
         * @throws java.lang.NullPointerException
         *             If the argument is null
         */
        protected Enumeration createEnumeration(final Object iterateOverMe) {
            if (iterateOverMe == null)
                throw new NullPointerException("Object arguments passed to "
                        + "the BasicBshIterator constructor cannot be null.");
            if (iterateOverMe instanceof Enumeration)
                return (Enumeration) iterateOverMe;
            if (iterateOverMe instanceof Vector)
                return ((Vector) iterateOverMe).elements();
            if (iterateOverMe.getClass().isArray()) {
                final Object array = iterateOverMe;
                return new Enumeration() {

                    int index = 0, length = Array.getLength(array);

                    public Object nextElement() {
                        return Array.get(array, this.index++);
                    }

                    public boolean hasMoreElements() {
                        return this.index < this.length;
                    }
                };
            }
            if (iterateOverMe instanceof String)
                return this.createEnumeration(
                        ((String) iterateOverMe).toCharArray());
            if (iterateOverMe instanceof StringBuffer)
                return this.createEnumeration(
                        iterateOverMe.toString().toCharArray());
            throw new IllegalArgumentException(
                    "Cannot enumerate object of type "
                            + iterateOverMe.getClass());
        }

        /**
         * Fetch the next object in the iteration.
         *
         * @return The next object
         */
        public Object next() {
            return this.enumeration.nextElement();
        }

        /**
         * Returns true if and only if there are more objects available
         * via the <code>next()</code> method.
         *
         * @return The next object
         */
        public boolean hasNext() {
            return this.enumeration.hasMoreElements();
        }
    }
}
