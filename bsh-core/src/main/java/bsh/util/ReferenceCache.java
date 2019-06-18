
/** Copyright 2018 Nick nickl- Lombard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. */
package bsh.util;

import static java.util.Objects.requireNonNull;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

/** Asynchronous reference cache with weak, soft and hard reference support.
 * Implementations supply values via the abstract create method, which is
 * called from a future asynchronously. Garbage collected references are
 * monitored and removed from the cache once cleared.
 * @param <K> the type of keys maintained by this cache
 * @param <V> the type of cached values */
public abstract class ReferenceCache<K,V> {
    private final ConcurrentMap<CacheReference<K>,
                         Future<CacheReference<V>>> cache;
    private final ReferenceFactory<K,V> keyFactory;
    private final ReferenceFactory<K,V> valueFactory;
    private final ReferenceFactory<K,V> lookupFactory;
    private final ReferenceQueueMonitor<? super Object> queue;

    /** Definition of reference types. */
    public static enum Type { Weak, Soft, Hard }

    /** Instantiates a new cache of key type and value type references.
     * @param keyType the type of key reference
     * @param valueType the type of value reference */
    public ReferenceCache(Type keyType, Type valueType) {
        this(keyType, valueType, 0);
    }

    /** New cache with initial size of key type and value type references.
     * @param keyType the type of key reference
     * @param valueType the type of value reference
     * @param initialSize initial cache size */
    public ReferenceCache(Type keyType, Type valueType, int initialSize) {
        keyFactory = toFactory(keyType);
        valueFactory = toFactory(valueType);
        lookupFactory = new HardReferenceFactory();
        cache = new ConcurrentHashMap<>(initialSize);
        queue = new ReferenceQueueMonitor<>();
        Thread t = new Thread(queue);
        t.setDaemon(true);
        t.start();
    }

    /** Implementations create a value to associated with the supplied key.
     * @param key the key for which a value needs to be created
     * @return the value to cache */
    abstract protected V create(K key);

    /** Get a value from the cache for associated with the supplied key.
     * New entries will be initialized if they don't exist or if they were
     * cleared and will block to wait for a value to return.
     * For asynchronous non blocking value creation use init.
     * @param key associated with cache value
     * @return value associated with the key */
    public V get(K key) {
        if (null == key)
            return null;
        CacheReference<K> refKey = lookupFactory.createKey(key, queue);
        if (cache.containsKey(refKey)) {
            V value = dereferenceValue(cache.get(refKey));
            if (null != value)
                return value;
            cache.remove(refKey);
        }
        init(key);
        return dereferenceValue(cache.get(refKey));
    }

    /** Asynchronously initialize a new cache value to associate with key.
     * If key is null or key already exist will do nothing.
     * Wraps the create method in a future task and starts a new process.
     * @param key associated with cache value */
    public void init(K key) {
        if (null == key)
            return;
        CacheReference<K> refKey = keyFactory.createKey(key, queue);
        if (cache.containsKey(refKey))
            return;
        FutureTask<CacheReference<V>> task = new FutureTask<>(()-> {
            V created = requireNonNull(create(key));
            return valueFactory.createValue(created, queue);
        });
        cache.put(refKey, task);
        task.run();
    }

    /** Remove cache entry associated with the given key.
     * @param key associated with cache value
     * @return true if there was an entry to remove */
    public boolean remove(K key) {
        if (null == key)
            return false;
        CacheReference<K> keyRef = lookupFactory.createKey(key, queue);
        return CacheKey.class.cast(keyRef).removeCacheEntry();
    }

    /** Returns the number of cached entries in the cache.
     * @return the number of entries cached */
    public int size() { return cache.size(); }


    /** Clears the cache and removes all of the cached entries.
     * The cache will be empty after this call returns. */
    public void clear() { cache.clear(); }

    /** Create a reference factory of the given type.
     * @param type type of reference factory
     * @return a reference factory */
    private final ReferenceFactory<K,V> toFactory(Type type) {
        switch (type) {
            case Hard : return new HardReferenceFactory();
            case Weak : return new WeakReferenceFactory();
            case Soft : return new SoftReferenceFactory();
            default : return null;
        }
    }

    /** Dereference a referenced cache value to retrieve the value.
     * @param refValue referenced value
     * @return dereferenced value */
    private V dereferenceValue(CacheReference<V> refValue) {
        return refValue.get();
    }

    /** Retrieve a referenced value from a future and dereference it.
     * @param futureValue a future value
     * @return dereferenced value */
    private V dereferenceValue(Future<CacheReference<V>> futureValue) {
        try {
            return dereferenceValue(futureValue.get());
        } catch (final Throwable e) {
            return null;
        }
    }


    // Member classes


    /** A generic type for all types of cache references.
     * @param <T> the type of the value */
    private interface CacheReference<T> {
        /** Method to dereference the referenced value.
         * @return the dereferenced value */
        T get();
    }

    /** Defines a reference factory with create key and create value methods. */
    private interface ReferenceFactory<K,V> {
        /** Create a referenced cache key.
         * @param key to reference
         * @param queue associated reference queue
         * @return cache key type reference */
        CacheReference<K> createKey(K key, ReferenceQueue<? super K> queue);

        /** Create a referenced cache value.
         * @param value to reference
         * @param queue associated reference queue
         * @return cache value type reference */
        CacheReference<V> createValue(V value, ReferenceQueue<? super V> queue);
    }

    /** A generic type for all types of cache key references.
     * Provides equality based on the hash code of the actual key without
     * needing to dereference the referenced key. Also implements remove
     * key from cache.
     * @param <T> the type of the value */
    private abstract class CacheKey<T> implements CacheReference<T> {
        private final int hashCode;

        /** Create a new cache key and capture its hash code.
         * @param key to provide equality for */
        public CacheKey(T key) {
            hashCode = key.hashCode();
        }

        /** Provides dereference for the key.
         * {@inheritDoc} */
        abstract public T get();

        /** Remove cache entry associated with this cache key.
         * @return true if there was an entry to remove */
        public boolean removeCacheEntry() {
            return null != ReferenceCache.this.cache.remove(this);
        }

        /** Compares the captured hash code with object hash code for equality.
         *  {@inheritDoc} */
        @Override
        public boolean equals(final Object obj) {
            return hashCode == obj.hashCode();
        }

        /** Return the captured hash code for the referenced key.
         * {@inheritDoc} */
        @Override
        public int hashCode() { return hashCode; }
    }

    /** Hard or strongly referenced key and value factory implementation. */
    private class HardReferenceFactory implements ReferenceFactory<K,V> {
        /** Provides a cache key for an unreferenced key.
         *  {@inheritDoc} */
        @Override
        public CacheReference<K> createKey(
                final K key, ReferenceQueue<? super K> queue) {
            return new CacheKey<K>(key) {
                @Override
                public K get() { return key; }
            };
        }

        /** Provides a cache value for an unreferenced value.
         *  {@inheritDoc} */
        @Override
        public CacheReference<V> createValue(
                final V value, ReferenceQueue<? super V> queue) {
            return new CacheReference<V>() {
                @Override
                public V get() { return value; }
            };
        }
    }

    /** Weakly referenced key and value factory implementation. */
    private class WeakReferenceFactory implements ReferenceFactory<K,V> {
        /** Cache value implementation of a weak reference. */
        private class WeakReferenceValue<T> extends WeakReference<T>
                implements CacheReference<T> {
            WeakReferenceValue(T value, ReferenceQueue<? super T> queue) {
                super(value, queue);
            }
        }

        /** Provides a weakly referenced cache key.
         * Key removes itself from the cache when cleared.
         *  {@inheritDoc} */
        @Override
        public CacheReference<K> createKey(
                final K key, final ReferenceQueue<? super K> queue) {
            return new CacheKey<K>(key) {
                final Reference<K> ref = new WeakReference<K>(key, queue) {
                    @Override
                    public void clear() {
                        removeCacheEntry();
                        super.clear();
                    }
                };
                @Override
                public K get() { return ref.get(); }
            };
        }

        /** Provides a weakly referenced cache value.
         *  {@inheritDoc} */
        @Override
        public CacheReference<V> createValue(
                V value, ReferenceQueue<? super V> queue) {
            return new WeakReferenceValue<>(value, queue);
        }
    }

    /** Softly referenced key and value factory implementation. */
   private class SoftReferenceFactory implements ReferenceFactory<K,V> {
       /** Cache value implementation of a soft reference. */
        private class SoftReferenceValue<T> extends SoftReference<T>
                implements CacheReference<T> {
            SoftReferenceValue(T value, ReferenceQueue<? super T> queue) {
                super(value, queue);
            }
        }

        /** Provides a softly referenced cache key.
         * Key removes itself from the cache when cleared.
         *  {@inheritDoc} */
        @Override
        public CacheReference<K> createKey(
                final K key, final ReferenceQueue<? super K> queue) {
            return new CacheKey<K>(key) {
                final Reference<K> ref = new SoftReference<K>(key, queue) {
                    @Override
                    public void clear() {
                        removeCacheEntry();
                        super.clear();
                    }
                };
                @Override
                public K get() { return ref.get(); }
            };
        }

        /** Provides a softly referenced cache value.
         *  {@inheritDoc} */
        @Override
        public CacheReference<V> createValue(
                V value, ReferenceQueue<? super V> queue) {
            return new SoftReferenceValue<>(value, queue);
        }
    }

   /** Runnable monitor of the reference queue associated to all references.
    * Registered reference objects are appended to this queue by the garbage
    * collector after the appropriate reachability changes were detected.
    * Ensures that all references are cleared and removed from the cache.
    * @param <T> the type of the reference value */
    private class ReferenceQueueMonitor<T> extends ReferenceQueue<T>
            implements Runnable {
        /** Uses the parent's remove method which is blocking until a cleared
         * reference is added to the queue.
         * Calls the overwritten clear method of the associated reference which
         * will remove the key from the cache.
         *  {@inheritDoc} */
        @Override
        public void run() {
            for (;;) try {
                Reference<? extends T> ref = super.remove();
                if (ref != null) ref.clear();
            } catch (InterruptedException e) { /* ignore try again */ System.out.println(e+" ooops");}
        }
    }
}
