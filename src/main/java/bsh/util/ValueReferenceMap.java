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
package bsh.util;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Implement a simple cache that automatically removes unused
 * values from the map.  References can be hard or soft.
 */
public class ValueReferenceMap<K,V> {

    public static enum Type { Weak, Soft }

    private Function<K,V> creator;
    private Type type;

    private HashMap<K,Reference<V>> map = new HashMap<>();
    private HashMap<Reference<V>,K> reverse = new HashMap<>();
    private ReferenceQueue<V> queue = new ReferenceQueue<>();
    private int counter;
    private int found;
    private int missed;

    /**
     * @param creator a function that creates the value object for
     * a given key in the map
     * @param type the type of reference: Hard or Soft
     */
    public ValueReferenceMap(Function<K,V> creator, Type type) {
        requireNonNull(creator, "creator must not be null");
        requireNonNull(type, "type must not be null");
        assert(type == Type.Weak || type == Type.Soft);

        this.creator = creator;
        this.type = type;
    }

    /**
     * Get the value for a given key.  If the value for a given key
     * does not exist then create one using the creator function.
     * @param key the key for the required value.  Must not be null.
     */
    public synchronized V get(K key) {
        requireNonNull(key, "key must not be null");

        /*
         * Periodically clean up the entries.
         * Could probably just unconditionally call clean() without a
         * noticable performance penalty.
         */
        if (++counter == 1000) {
            clean();
            counter=found=missed=0;
        }

        /*
         * Do not use computeIfAbsent because we need to
         * maintain a hard reference to the object at all times.
         */
        Reference<V> ref = map.get(key);
        if (ref != null) {
            V obj = ref.get();
            if (obj != null) {
                found++;
                return obj;
            }
        }

        missed++;
        V obj = requireNonNull(creator.apply(key),
                               "ValueReference cache create value may not return null.");
        if (type == Type.Weak)
            ref = new WeakReference<V>(obj, queue);
        else
            ref = new SoftReference<V>(obj, queue);

        map.put(key, ref);
        reverse.put(ref, key);
        return obj;
    }

    /**
     * Remove an entry from the map
     * @param key the key for the entry
     */
    public synchronized boolean remove(K key) {
        Reference<V> ref = map.remove(key);
        boolean result = ref != null;
        if (result)
            reverse.remove(ref);
        return result;
    }

    /**
     * Remove all entries from the map
     */
    public synchronized void clear() {
        clean();
        map.clear();
        reverse.clear();
        counter=found=missed=0;
    }

    /**
     * Get map size
     */
    public synchronized int size() {
        clean();
        return map.size();
    }

    /**
     * Process events in the reference queue
     */
    private void clean() {
        int cleaned = 0;
        Reference<? extends V> wr;
        while ((wr = queue.poll()) != null) {
            K key = reverse.get(wr);
            if (key != null)
                map.remove(key);
            reverse.remove(wr);
            cleaned++;
        }
        // System.err.println("counter="+counter+" cleaned="+cleaned+" found="+found+" missed="+missed+" map size="+map.size()+" reverse size="+reverse.size());
    }
}
