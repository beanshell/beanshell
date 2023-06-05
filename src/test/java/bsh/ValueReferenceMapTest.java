package bsh;

import org.junit.Test;
import org.junit.runner.RunWith;

import bsh.util.ValueReferenceMap;
import static bsh.util.ValueReferenceMap.Type.Soft;
import static bsh.util.ValueReferenceMap.Type.Weak;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
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
