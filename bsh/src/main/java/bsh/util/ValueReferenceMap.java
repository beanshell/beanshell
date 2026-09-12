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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Implement a simple cache that automatically removes unused
 * entries once their key or their value is no longer otherwise
 * reachable. References can be weak or soft; both the key and the
 * value are held at the same strength.
 */
public class ValueReferenceMap<K,V> {

    public static enum Type { Weak, Soft }

    private Function<K,V> creator;
    private Type type;

    /** One entry per live cache mapping, bucketed by the key's hash code
     * (captured at creation time so it survives key collection). Lookup
     * always compares against the live key via equals(), never against a
     * cached hash code alone -- a lone hash match is not equality. */
    private final class Entry {
        final int hash;
        final Reference<K> keyRef;
        final Reference<V> valueRef;
        Entry(K key, V value) {
            this.hash = key.hashCode();
            this.keyRef = newReference(key, keyQueue);
            this.valueRef = newReference(value, valueQueue);
        }
        K key() { return keyRef.get(); }
        V value() { return valueRef.get(); }
    }

    private final Map<Integer,List<Entry>> buckets = new HashMap<>();
    /** Reverse lookup from either a key or a value reference back to its
     * entry, so a queued (collected) reference can be removed in O(1). */
    private final Map<Reference<?>,Entry> byReference = new HashMap<>();
    private final ReferenceQueue<K> keyQueue = new ReferenceQueue<>();
    private final ReferenceQueue<V> valueQueue = new ReferenceQueue<>();
    private int counter;
    private int found;
    private int missed;

    /**
     * @param creator a function that creates the value object for
     * a given key in the map
     * @param type the type of reference: Weak or Soft
     */
    public ValueReferenceMap(Function<K,V> creator, Type type) {
        requireNonNull(creator, "creator must not be null");
        requireNonNull(type, "type must not be null");
        assert(type == Type.Weak || type == Type.Soft);

        this.creator = creator;
        this.type = type;
    }

    private <T> Reference<T> newReference(T obj, ReferenceQueue<T> queue) {
        return type == Type.Weak
            ? new WeakReference<T>(obj, queue) : new SoftReference<T>(obj, queue);
    }

    /**
     * Get the value for a given key.  If the value for a given key
     * does not exist then create one using the creator function.
     * @param key the key for the required value.  Must not be null.
     */
    public synchronized V get(K key) {
        requireNonNull(key, "key must not be null");

        /*
         * Could probably just unconditionally call clean() without a
         * noticable performance penalty, but only pay for the full
         * counter/found/missed bookkeeping periodically.
         */
        clean();
        if (++counter == 1000) {
            counter = found = missed = 0;
        }

        int hash = key.hashCode();
        List<Entry> bucket = buckets.get(hash);
        if (bucket != null)
            for (Entry entry : bucket) {
                K candidate = entry.key();
                if (key.equals(candidate)) {
                    V value = entry.value();
                    if (value != null) {
                        found++;
                        return value;
                    }
                    break;
                }
            }

        missed++;
        V value = requireNonNull(creator.apply(key),
                               "ValueReference cache create value may not return null.");
        Entry entry = new Entry(key, value);
        buckets.computeIfAbsent(hash, h -> new ArrayList<>()).add(entry);
        byReference.put(entry.keyRef, entry);
        byReference.put(entry.valueRef, entry);
        return value;
    }

    /**
     * Remove an entry from the map
     * @param key the key for the entry
     */
    public synchronized boolean remove(K key) {
        if (null == key)
            return false;
        List<Entry> bucket = buckets.get(key.hashCode());
        if (bucket == null)
            return false;
        for (Entry entry : bucket) {
            K candidate = entry.key();
            if (key.equals(candidate)) {
                removeEntry(entry);
                return true;
            }
        }
        return false;
    }

    /**
     * Remove all entries from the map
     */
    public synchronized void clear() {
        buckets.clear();
        byReference.clear();
        drain(keyQueue);
        drain(valueQueue);
        counter = found = missed = 0;
    }

    /**
     * Get map size
     */
    public synchronized int size() {
        clean();
        int size = 0;
        for (List<Entry> bucket : buckets.values())
            size += bucket.size();
        return size;
    }

    /** Remove an entry from both the bucket and reverse lookup. */
    private void removeEntry(Entry entry) {
        List<Entry> bucket = buckets.get(entry.hash);
        if (bucket != null) {
            bucket.remove(entry);
            if (bucket.isEmpty())
                buckets.remove(entry.hash);
        }
        byReference.remove(entry.keyRef);
        byReference.remove(entry.valueRef);
    }

    /** Discard queued references without processing them, e.g. after clear(). */
    private void drain(ReferenceQueue<?> queue) {
        while (queue.poll() != null) { /* discard */ }
    }

    /**
     * Process events in both reference queues, removing entries whose
     * key or value has become unreachable.
     */
    private void clean() {
        Reference<?> ref;
        while ((ref = keyQueue.poll()) != null) {
            Entry entry = byReference.get(ref);
            if (entry != null)
                removeEntry(entry);
        }
        while ((ref = valueQueue.poll()) != null) {
            Entry entry = byReference.get(ref);
            if (entry != null)
                removeEntry(entry);
        }
    }
}
