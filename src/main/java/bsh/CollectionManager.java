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
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The default CollectionManager supports iteration over objects of type:
 * Enumeration, Iterator, Iterable, CharSequence, and array. */
public final class CollectionManager {

    /** The Constant manager. */
    private static final CollectionManager manager = new CollectionManager();

    /** Gets the collection manager.
     * @return the collection manager */
    public static synchronized CollectionManager getCollectionManager() {
        return manager;
    }

    /** Checks if is bsh iterable.
     * @param obj the obj
     * @return true, if is bsh iterable */
    public boolean isBshIterable(final Object obj) {
        // This could be smarter...
        try {
            this.getBshIterator(obj);
            return true;
        } catch (final IllegalArgumentException e) {
            return false;
        }
    }

    /** Empty it.
     * @param <T> the generic type
     * @return the iterator */
    private <T> Iterator<T> emptyIt() {
        return new Iterator<T>() {
            @Override
            public boolean hasNext() {
                return false;
            }
            @Override
            public T next() {
                return null;
            }
        };
    }

    /** Arrayit.
     * @param array the array
     * @return the iterator */
    private Iterator<Object> arrayit(final Object array) {
        return new Iterator<Object>() {
            private int index = 0;
            private final int length = Array.getLength(array);
            @Override
            public boolean hasNext() {
                return this.index < this.length;
            }
            @Override
            public Object next() {
                return Array.get(array, this.index++);
            }
        };
    }

    /** Gets the bsh iterator.
     * @param obj the obj
     * @return the bsh iterator */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Iterator getBshIterator(final Enumeration obj) {
        return Collections.list(obj).iterator();
    }

    /** Gets the bsh iterator.
     * @param obj the obj
     * @return the bsh iterator */
    @SuppressWarnings("rawtypes")
    public Iterator getBshIterator(final Iterator obj) {
        return obj;
    }

    /** Gets the bsh iterator.
     * @param obj the obj
     * @return the bsh iterator */
    public Iterator<Object> getBshIterator(final Object[] obj) {
        return Arrays.asList(obj).iterator();
    }

    /** Gets the bsh iterator.
     * @param obj the obj
     * @return the bsh iterator */
    public Iterator<Object> getBshIterator(final CharSequence obj) {
        return this.arrayit(obj.toString().toCharArray());
    }

    public Iterator<?> getBshIterator(final String obj) {
        return this.getBshIterator(new StringBuilder(obj));
    }
    /** Reflect name.
     * @param obj the obj
     * @return the stream */
    public Stream<String> reflectNames(Object obj) {
        return Stream.concat(
                Stream.of(obj.getClass().getFields())
                    .map(field -> field.getName()),
                Stream.of(obj.getClass().getDeclaredMethods())
                    .map(method -> method.getName() + "("))
                .sorted().distinct();
    }
    public Stream<SimpleImmutableEntry<String, Object>> reflectionEntries(Object obj) {
        return Stream.concat(
                Stream.of(obj.getClass().getFields())
                    .map(field -> {
                            Object val = null; 
                        try {
                            field.setAccessible(true);
                            val = field.get(obj);
                        } catch (Exception e) {}
                        return new SimpleImmutableEntry<>(field.getName(), val);
                    }),
                Stream.of(obj.getClass().getDeclaredMethods())
                    .map(method -> { 
                        Object val = null; 
                        try {
                            method.setAccessible(true);
                            val = method.invoke(obj, new Object[method.getParameterCount()]);
                        } catch (Exception e) {}
                        return new SimpleImmutableEntry<>(method.getName(), val);
                    }));
    }

    /** Gets the bsh iterator.
     * @param obj the obj
     * @return the bsh iterator
     * @throws IllegalArgumentException the illegal argument exception */
    @SuppressWarnings("rawtypes")
    public Iterator getBshIterator(final Object obj)
            throws IllegalArgumentException {
        if (obj == null)
            return this.emptyIt();
        if (obj instanceof Primitive)
            if (((Primitive) obj).getType() == null)
                return this.emptyIt();
            else
                return getBshIterator(Primitive.unwrap(obj));
        if (obj instanceof String)
            return getBshIterator((String) obj);
        if (obj instanceof Object[])
            return getBshIterator((Object[]) obj);
        if (obj.getClass().isArray())
            return this.arrayit(obj);
        if (obj instanceof Iterable)
            return ((Iterable) obj).iterator();
        if (obj instanceof Object)
            return reflectionEntries(obj).collect(Collectors.toList()).iterator();
        throw new IllegalArgumentException(
                "Cannot iterate over object of type " + obj.getClass());
    }
}
