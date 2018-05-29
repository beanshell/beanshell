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
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.stream.IntStream;
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

    /** Checks if supplied value is iterable.
     * @param obj the value to iterate
     * @return true, everything is iterable */
    public boolean isBshIterable(final Object obj) {
        return true;
    }

    /** An empty iterator with hasNext always false.
     * @param <T> generic type,
     * since java can't infer it will be Object
     * @return the null iterator */
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

    /** Obfuscated Object type or primitive array iterator.
     * @param array the object array
     * @return the iterator */
    private Iterator<Object> arrayIt(final Object array) {
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

    /** Apply reflection to supplied value returning an
     * iterable stream of strings. Allows iteration over
     * true string representations of the class and member
     * definitions. For generated classes the true or bsh
     * definitions are retrieved and not the java reflection
     * of the mock instances.
     * @param obj the type to reflect
     * @return an iterable stream of String definitions */
    private Stream<String> reflectNames(Object obj) {
        Class<?> type = obj.getClass();
        if (obj instanceof Class<?>)
            type = (Class<?>) obj;
        if (obj instanceof ClassIdentifier)
            type = ((ClassIdentifier)obj).getTargetClass();
        if (Reflect.isGeneratedClass(type))
            return Stream.concat(Stream.concat(
                Stream.of(StringUtil.classString(type)),
                Stream.concat(
                    Stream.of(Reflect.getDeclaredVariables(type))
                        .map(StringUtil::variableString)
                        .map("    "::concat),
                    Stream.of(Reflect.getDeclaredMethods(type))
                        .map(StringUtil::methodString)
                        .map("    "::concat))), Stream.of("}"));
        else
            return Stream.concat(Stream.concat(
                Stream.of(StringUtil.classString(type)),
                Stream.concat(
                    Stream.of(type.getFields())
                        .map(StringUtil::variableString)
                        .map("    "::concat),
                    Stream.of(type.getMethods())
                        .map(StringUtil::methodString)
                        .map("    "::concat))), Stream.of("}"));
    }

    /** Gets iterator for enumerated elements.
     * @param obj the enumerated elements
     * @return the bsh iterator */
    public <T> Iterator<T> getBshIterator(final Enumeration<T> obj) {
        return Collections.list(obj).iterator();
    }


    /** Returns iterator from supplied iterable collection.
     * @param obj an iterable collection instance
     * @return the bsh iterator */
    public <T> Iterator<T> getBshIterator(final Iterable<T> obj) {
        return obj.iterator();
    }

    /** Returns iterator from supplied iterator.
     * @param obj the iterator
     * @return the bsh iterator */
    public <T> Iterator<T> getBshIterator(final Iterator<T> obj) {
        return obj;
    }

    /** Collect a char array iterator from supplied char sequence.
     * @param obj the char sequence value
     * @return the bsh iterator */
    public Iterator<Object> getBshIterator(final CharSequence obj) {
        return this.arrayIt(obj.toString().toCharArray());
    }

    /** Collect a char array sequence iterator from supplied string.
     * @param obj the string value
     * @return the bsh iterator */
    public Iterator<Object> getBshIterator(final String obj) {
        return this.arrayIt(obj.toCharArray());
    }


    /** Iterator from T[] method bound type Object array as value.
     * @param obj the T[] generic typed Object array.
     * @return the iterator */
    public <T> Iterator<T> getBshIterator(final T[] obj) {
        return Arrays.asList(obj).iterator();
    }

    /** Gets iterator for Number range from 0.
     * Range from 0 up to number or 0 down to number inclusive.
     * Empty Iterator if number is 0.
     * @param obj the top range number value
     * @return the bsh iterator */
    public Iterator<Integer> getBshIterator(final Number obj) {
        int number = obj.intValue();
        if (number == 0)
            return this.emptyIt();
        if (number > 0)
            return IntStream.rangeClosed(0, number).iterator();
        return IntStream.rangeClosed(number, 0).map(i -> number - i).iterator();
    }

    /** Starting positions of unicode block sets */
    private static final int[] unicodeBlockStarts = new int[] {48,58,65,91,97,123,129,256,384,592,688,768,880,1024,
        1329,1425,1536,1792,1872,1920,1984,2048,2112,2144,2208,2304,2432,2561,2689,2817,2946,3072,3200,3328,3458,3584,
        3712,3840,4096,2256,4352,4608,5024,5120,5760,5792,5888,5920,5952,5984,6016,6144,6320,6400,6480,6528,6624,6656,
        6688,6832,6912,7040,7104,7168,7248,7296,7360,7376,7424,7616,7680,7936,8192,8304,8352,8400,8448,8528,8592,8704,
        8960,9216,9280,9312,9472,9600,9632,9728,9984,10176,10224,10240,10496,10624,10752,11008,11264,11360,11392,11520,
        11568,11648,11744,11776,11904,12032,12272,12288,12352,12448,12544,12592,12688,12704,12736,12784,12800,13056,
        13312,19904,19968,40960,42128,42192,42240,42560,42656,42752,42784,43008,43056,43072,43136,43232,43264,43312,
        43360,43392,43488,43520,43616,43648,43744,43776,43824,43888,43968,44032,55216,55296,56320,57344,63744,64256,
        64336,65024,65040,65056,65072,65104,65136,65280,65520,65536,65664,65792,65856,65936,66000,66176,66208,66272,
        66304,66352,66384,66432,66464,66560,66640,66688,66736,66816,66864,67072,67584,67648,67680,67712,67808,67840,
        67872,67968,68000,68096,68192,68224,68288,68352,68416,68448,68480,68608,68736,69216,69632,69760,69840,69888,
        69968,70016,70112,70144,70272,70320,70400,70656,70784,71040,71168,71264,71296,71424,71840,72192,72272,72384,
        72704,72816,72960,73728,74752,74880,77824,82944,92160,92736,92880,92928,93952,94176,94208,100352,110592,110848,
        110960,113664,113824,118784,119040,119296,119552,119648,119808,120832,122880,124928,125184,126464,126976,127024,
        127136,127232,127488,127744,128512,128592,128640,128768,128896,129024,129280,131072,173824,177984,178208,183984,
        194560,917504,917760,983040,1048576};

    /** Gets iterator for character range from highest unicode block set starting position.
     * Range from highest starting position which is less than supplied value code point.
     * @param obj the top of the character range
     * @return the bsh iterator */
    public Iterator<String> getBshIterator(final Character obj) {
        Integer value = Integer.valueOf(obj.charValue());
        int check = 33, start = 0;
        for (int i : unicodeBlockStarts) if (check <= value) {
            start = check;
            check = i;
        } else break;
        return IntStream.rangeClosed(start, value).boxed()
                .map(Character::toChars)
                .map(String::valueOf).iterator();
    }

    /** Inspect the supplied object and cast type to delegates.
     * @param obj the value to iterate of unknown type.
     * @return the bsh iterator */
    public Iterator<?> getBshIterator(final Object obj) {
        if (obj == null)
            return this.emptyIt();
        if (obj instanceof Primitive)
            return this.getBshIterator(Primitive.unwrap(obj));
        if (obj.getClass().isArray())
            return this.arrayIt(obj);
        if (obj instanceof Iterable)
            return this.getBshIterator((Iterable<?>) obj);
        if (obj instanceof Iterator)
            return this.getBshIterator((Iterator<?>) obj);
        if (obj instanceof Enumeration)
            return this.getBshIterator((Enumeration<?>) obj);
        if (obj instanceof CharSequence)
            return this.getBshIterator((CharSequence) obj);
        if (obj instanceof Number)
            return this.getBshIterator((Number) obj);
        if (obj instanceof Character)
            return this.getBshIterator((Character) obj);
        if (obj instanceof String)
            return this.getBshIterator((String) obj);
        return this.reflectNames(obj).iterator();
    }
}
