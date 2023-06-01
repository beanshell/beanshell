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
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Implement a simple cache that automatically purges unused values
 */
public class WeakValueMap<K,V> {

    private Function<K,V> creator;

    private HashMap<K,WeakReference<V>> map = new HashMap<>();
    private HashMap<WeakReference<V>,K> reverse = new HashMap<>();
    private ReferenceQueue<V> queue = new ReferenceQueue<>();
    private int counter;
    private int found;
    private int missed;

    /**
     * @param creator a function that creates the value object for
     * a given key in the map
     */
    public WeakValueMap(Function<K,V> creator) {
        requireNonNull(creator, "creator must not be null");
        this.creator = creator;
    }

    /**
     * Get the value for a given key.  If the value for a given key
     * does not exist then create one using the creator function.
     * @param key the key for the required value
     */
    public synchronized V get(K key) {
        requireNonNull(key, "key must not be null");
        if (((++counter)%1000)==0) {
            // periodically clean up the entries
            clean();
            counter=found=missed=0;
        }

        WeakReference<V> ref = map.get(key);
        if (ref != null) {
            V obj = ref.get();
            if (obj != null) {
                found++;
                return obj;
            }
        }
        missed++;
        V obj = creator.apply(key);
        ref = new WeakReference<V>(obj, queue);
        map.put(key, ref);
        reverse.put(ref, key);
        return obj;
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
        // System.err.println("cleaned "+cleaned+" found="+found+" missed="+missed);
    }
}
