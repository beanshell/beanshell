package bsh;

import org.junit.Test;
import org.junit.runner.RunWith;

import bsh.util.ReferenceCache;
import static bsh.util.ReferenceCache.Type.Hard;
import static bsh.util.ReferenceCache.Type.Soft;
import static bsh.util.ReferenceCache.Type.Weak;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CompletionException;

@RunWith(FilteredTestRunner.class)
public class ReferenceCacheTest {

    @Test
    public void hard_reference_key() {
        ReferenceCache<String,Integer> cache = new ReferenceCache<String,Integer>(Hard, Hard) {
            protected Integer create(String key) { return 0; }};
        cache.init("foo");

        assertThat(cache.size(), equalTo(1));
        assertThat(cache.get("foo"), equalTo(0));
        System.gc();
    }

    @Test
    public void hard_reference_null_value() {
        ReferenceCache<String,Void> cache = new ReferenceCache<String,Void>(Hard, Hard) {
            protected Void create(String key) { return null; }};
        cache.init("foo");

        assertThat(cache.size(), equalTo(1));
        Exception e = assertThrows(CompletionException.class, () -> cache.get("foo"));
        assertThat(e.getCause(), instanceOf(NullPointerException.class));
        assertThat(e.getCause().getMessage(),
            containsString("Reference cache create value may not return null."));
        System.gc();
    }

    @Test
    public void hard_reference_value() {
        ReferenceCache<String,String> cache = new ReferenceCache<String,String>(Hard, Hard) {
            protected String create(String key) { return "bar"; }};
        cache.init("foo");

        assertThat(cache.size(), equalTo(1));
        assertThat(cache.get("foo"), equalTo("bar"));
        System.gc();
    }

    @Test
    public void soft_reference_key() {
        ReferenceCache<String,Integer> cache = new ReferenceCache<String,Integer>(Soft, Soft) {
            protected Integer create(String key) { return 0; }};
        cache.init("foo");

        assertThat(cache.size(), equalTo(1));
        assertThat(cache.get("foo"), equalTo(0));
        System.gc();
    }

    @Test
    public void soft_reference_null_value() {
        ReferenceCache<String,Void> cache = new ReferenceCache<String,Void>(Soft, Soft) {
            protected Void create(String key) { return null; }};
        cache.init("foo");

        assertThat(cache.size(), equalTo(1));
        Exception e = assertThrows(CompletionException.class, () -> cache.get("foo"));
        assertThat(e.getCause(), instanceOf(NullPointerException.class));
        assertThat(e.getCause().getMessage(),
            containsString("Reference cache create value may not return null."));
        System.gc();
    }

    @Test
    public void soft_reference_value() {
        ReferenceCache<String,String> cache = new ReferenceCache<String,String>(Soft, Soft) {
            protected String create(String key) { return "bar"; }};
        cache.init("foo");

        assertThat(cache.size(), equalTo(1));
        assertThat(cache.get("foo"), equalTo("bar"));
        System.gc();
    }

    @Test
    public void weak_reference_key() {
        ReferenceCache<String,Integer> cache = new ReferenceCache<String,Integer>(Weak, Weak) {
            protected Integer create(String key) { return 0; }};
        cache.init("foo");

        assertThat(cache.size(), equalTo(1));
        assertThat(cache.get("foo"), equalTo(0));
        System.gc();
    }

    @Test
    public void weak_reference_null_value() {
        ReferenceCache<String,Void> cache = new ReferenceCache<String,Void>(Weak, Weak){
            protected Void create(String key) { return null; }};
        cache.init("foo");

        assertThat(cache.size(), equalTo(1));
        Exception e = assertThrows(CompletionException.class, () -> cache.get("foo"));
        assertThat(e.getCause(), instanceOf(NullPointerException.class));
        assertThat(e.getCause().getMessage(),
            containsString("Reference cache create value may not return null."));
        System.gc();
    }

    @Test
    public void weak_reference_value() {
        ReferenceCache<String,String> cache = new ReferenceCache<String,String>(Weak, Weak){
            protected String create(String key) { return "bar"; }};
        cache.init("foo");

        assertThat(cache.size(), equalTo(1));
        assertThat(cache.get("foo"), equalTo("bar"));
        System.gc();
    }

    @Test
    public void null_cache_key() {
        ReferenceCache<String,String> cache = new ReferenceCache<String,String>(Weak, Weak, 2){
            protected String create(String key) { return "bar"; }};
        cache.init(null);

        assertThat(cache.get(null), nullValue());
        assertThat(cache.size(), equalTo(0));
        System.gc();
    }


    @Test
    public void remove_cache_entry() {
        ReferenceCache<String,String> cache = new ReferenceCache<String,String>(Weak, Weak){
            protected String create(String key) { return "bar"; }};
        cache.init("foo");

        assertThat(cache.size(), equalTo(1));
        assertThat(cache.get("foo"), equalTo("bar"));
        assertTrue(cache.remove("foo"));
        assertThat(cache.size(), equalTo(0));
        assertFalse(cache.remove(null));
        System.gc();
    }

    @Test
    public void hard_garbage_collect() {
        final int[] cnt = new int[1];
        ReferenceCache<Integer,byte[]> cache = new ReferenceCache<Integer,byte[]>(Hard, Hard){
            protected byte[] create(Integer key) { return new byte[1024*100]; }};
        while (cnt[0]++ < 10) {
            cache.init(cnt[0]);
            System.gc();
            assertArrayEquals(cache.get(cnt[0]), new byte[1024*100]);
        }
        System.gc();
        assertNotEquals(cache.size(), cnt[0]);
        assertArrayEquals(cache.get(cnt[0]), new byte[1024*100]);
    }

    @Test
    public void soft_garbage_collect() {
        final int[] cnt = new int[1];
        ReferenceCache<Integer,byte[]> cache = new ReferenceCache<Integer,byte[]>(Soft, Soft){
            protected byte[] create(Integer key) { return new byte[1024*100]; }};
        while (cnt[0]++ < 10) {
            cache.init(cnt[0]);
            System.gc();
            assertArrayEquals(cache.get(cnt[0]), new byte[1024*100]);
        }
        System.gc();
        assertNotEquals(cache.size(), cnt[0]);
        assertArrayEquals(cache.get(cnt[0]), new byte[1024*100]);
    }

    @Test
    public void weak_garbage_collect() {
        final int[] cnt = new int[1];
        ReferenceCache<Integer,byte[]> cache = new ReferenceCache<Integer,byte[]>(Weak, Weak){
            protected byte[] create(Integer key) { return new byte[1024*100]; }};
        while (cnt[0]++ < 10) {
            cache.init(cnt[0]);
            System.gc();
            assertArrayEquals(cache.get(cnt[0]), new byte[1024*100]);
        }
        System.gc();
        assertNotEquals(cache.size(), cnt[0]);
        assertArrayEquals(cache.get(cnt[0]), new byte[1024*100]);
    }
}

