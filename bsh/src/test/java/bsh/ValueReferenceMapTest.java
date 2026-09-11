package bsh;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import bsh.util.ValueReferenceMap;
import static bsh.util.ValueReferenceMap.Type.Soft;
import static bsh.util.ValueReferenceMap.Type.Weak;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(FilteredTestRunner.class)
public class ValueReferenceMapTest {


    @Test
    public void soft_reference_null_value() {
        ValueReferenceMap<String,Void> cache = new ValueReferenceMap<String,Void>(key -> null, Soft);
        Exception e = assertThrows(NullPointerException.class, () -> cache.get("foo"));
        assertThat(e.getMessage(),
            containsString("Reference cache create value may not return null."));
        System.gc();
    }

    @Test
    public void soft_reference_value() {
        ValueReferenceMap<String,String> cache = new ValueReferenceMap<String,String>(key -> "bar", Soft);

        cache.get("foo");

        assertThat(cache.size(), equalTo(1));
        assertThat(cache.get("foo"), equalTo("bar"));
        assertTrue(cache.remove("foo"));
        System.gc();
    }

    @Test
    public void weak_reference_key_gced() {
        ValueReferenceMap<String,byte[]> cache = new ValueReferenceMap<String,byte[]>(key -> new byte[1024*1000], Weak);
        cache.get("foo");

        // GC is unpredictable, add some pressure
        TestUtil.cleanUp();
        TestUtil.cleanUp();
        int[][] array = new int[1000][];
        for (int i=0; i<array.length; i++)
            array[i] = new int[5000];
        TestUtil.cleanUp();
        TestUtil.cleanUp();
        assertThat(cache.size(), equalTo(0));
        assertArrayEquals(new byte[1024*1000], cache.get("foo"));
        assertTrue(cache.remove("foo"));
        System.gc();
    }

    @Test
    public void weak_reference_null_value() {
        ValueReferenceMap<String,Void> cache = new ValueReferenceMap<String,Void>(key -> null, Weak);
        Exception e = assertThrows(NullPointerException.class, () -> cache.get("foo"));
        assertThat(e.getMessage(),
            containsString("Reference cache create value may not return null."));
        assertThat(cache.size(), equalTo(0));
        System.gc();
    }

    @Test
    public void weak_reference_value() {
        ValueReferenceMap<String,String> cache = new ValueReferenceMap<String,String>(key -> "bar", Weak);
        cache.get("foo");

        assertThat(cache.size(), equalTo(1));
        assertThat(cache.get("foo"), equalTo("bar"));
        assertTrue(cache.remove("foo"));
        System.gc();
    }

    @Test
    public void null_cache_key() {
        ValueReferenceMap<String,String> cache = new ValueReferenceMap<String,String>(key -> "bar", Weak);

        Exception e = assertThrows(NullPointerException.class, () -> cache.get(null));
        assertThat(e.getMessage(),
                   containsString("key must not be null"));
        assertThat(cache.size(), equalTo(0));
        System.gc();
    }


    @Test
    public void remove_cache_entry() {
        ValueReferenceMap<String,String> cache = new ValueReferenceMap<String,String>(key -> "bar", Weak);
        cache.get("foo");

        assertThat(cache.size(), equalTo(1));
        assertThat(cache.get("foo"), equalTo("bar"));
        assertTrue(cache.remove("foo"));
        assertThat(cache.size(), equalTo(0));
        assertFalse(cache.remove(null));
        System.gc();
    }

    @Test
    public void weak_reference_cleanup_preserves_replacement() throws Exception {
        assertCleanupPreservesReplacement(Weak);
    }

    @Test
    public void soft_reference_cleanup_preserves_replacement() throws Exception {
        assertCleanupPreservesReplacement(Soft);
    }

    private void assertCleanupPreservesReplacement(ValueReferenceMap.Type type)
            throws Exception {
        AtomicInteger creations = new AtomicInteger();
        ValueReferenceMap<String,Object> cache = new ValueReferenceMap<>(key -> {
            creations.incrementAndGet();
            return new Object();
        }, type);
        Object original = cache.get("key");
        Reference<?> oldReference = cachedReference(cache, "key");

        // Recreate a collected value before its queued reference is processed.
        oldReference.clear();
        assertTrue(oldReference.enqueue());
        Object replacement = cache.get("key");
        assertNotSame(original, replacement);

        assertThat(cache.size(), equalTo(1));
        assertSame(replacement, cache.get("key"));
        assertThat(creations.get(), equalTo(2));
    }

    private Reference<?> cachedReference(ValueReferenceMap<?,?> cache, Object key)
            throws ReflectiveOperationException {
        Field bucketsField = ValueReferenceMap.class.getDeclaredField("buckets");
        bucketsField.setAccessible(true);
        Map<?,?> buckets = (Map<?,?>) bucketsField.get(cache);
        List<?> bucket = (List<?>) buckets.get(key.hashCode());

        Class<?> entryClass = Class.forName("bsh.util.ValueReferenceMap$Entry");
        Field keyRefField = entryClass.getDeclaredField("keyRef");
        keyRefField.setAccessible(true);
        Field valueRefField = entryClass.getDeclaredField("valueRef");
        valueRefField.setAccessible(true);

        for (Object entry : bucket) {
            Reference<?> keyRef = (Reference<?>) keyRefField.get(entry);
            if (key.equals(keyRef.get()))
                return (Reference<?>) valueRefField.get(entry);
        }
        throw new AssertionError("no cached entry for key: " + key);
    }

    @Test
    public void soft_garbage_collect() {
        final int[] cnt = new int[1];
        ValueReferenceMap<Integer,byte[]> cache = new ValueReferenceMap<Integer,byte[]>(key -> new byte[1024*100], Soft);

        while (cnt[0]++ < 10) {
            cache.get(cnt[0]);
            System.gc();
            assertArrayEquals(new byte[1024*100], cache.get(cnt[0]));
        }
        System.gc();
        assertNotEquals(cache.size(), cnt[0]);
        assertArrayEquals(new byte[1024*100], cache.get(cnt[0]));
    }

    /** Soft key collection isn't separately tested: SoftReferences only
     * clear under real memory pressure, not a bare System.gc(), so a
     * dedicated test would be flaky. Key and value references share the
     * same Entry/newReference code, parameterized only by Type, so this
     * proof for Weak covers the mechanism Soft relies on too. */
    @Test
    public void weak_key_collected_once_unreferenced() throws InterruptedException {
        ValueReferenceMap<Object,String> cache = new ValueReferenceMap<>(k -> "value", Weak);
        Object key = new Object();
        WeakReference<Object> observedKey = new WeakReference<>(key);

        cache.get(key);
        key = null;

        // Clearing and enqueueing a Reference are separate JVM steps; the
        // enqueue happens on a background thread with unspecified timing,
        // so poll cache.size() rather than assume one round of GC suffices.
        int size = cache.size();
        for (int i = 0; i < 20 && size != 0; i++) {
            TestUtil.cleanUp();
            Thread.sleep(10);
            size = cache.size();
        }

        assertThat(observedKey.get(), nullValue());
        assertThat(size, equalTo(0));
    }

    @Test
    public void equal_hash_unequal_keys_map_to_distinct_values() {
        ValueReferenceMap<CollidingKey,String> cache =
            new ValueReferenceMap<>(k -> "value-for-" + k.id, Weak);
        CollidingKey a = new CollidingKey("a");
        CollidingKey b = new CollidingKey("b");
        assertThat(a.hashCode(), equalTo(b.hashCode()));

        assertThat(cache.get(a), equalTo("value-for-a"));
        assertThat(cache.get(b), equalTo("value-for-b"));
        assertThat(cache.get(a), equalTo("value-for-a"));
        assertThat(cache.size(), equalTo(2));
    }

    /** A key whose hashCode() deliberately collides with every other instance. */
    private static final class CollidingKey {
        final String id;
        CollidingKey(String id) { this.id = id; }
        @Override public int hashCode() { return 42; }
        @Override public boolean equals(Object o) {
            return o instanceof CollidingKey && ((CollidingKey) o).id.equals(id);
        }
    }

    @Test
    public void weak_garbage_collect() {
        final int[] cnt = new int[1];
        ValueReferenceMap<Integer,byte[]> cache = new ValueReferenceMap<Integer,byte[]>(key -> new byte[1024*100], Weak);
        while (cnt[0]++ < 10) {
            cache.get(cnt[0]);
            System.gc();
            assertArrayEquals(new byte[1024*100], cache.get(cnt[0]));
        }
        System.gc();
        assertNotEquals(cache.size(), cnt[0]);
        assertArrayEquals(new byte[1024*100], cache.get(cnt[0]));
    }
}
