package bsh;

import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import bsh.util.ReferenceCache;
import static bsh.util.ReferenceCache.Type.Hard;
import static bsh.util.ReferenceCache.Type.Soft;
import static bsh.util.ReferenceCache.Type.Weak;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;


@RunWith(FilteredTestRunner.class)
public class ReferenceCacheTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void hard_reference_key() throws Exception {
        ReferenceCache<String,Void> cache = new ReferenceCache<String,Void>(Hard, Hard) {
            protected Void create(String key) { return null; }};
        cache.init("foo");

        assertThat(cache.size(), equalTo(1));
        assertThat(cache.get("foo"), nullValue());
        System.gc();
    }

    @Test
    public void hard_reference_value() throws Exception {
        ReferenceCache<String,String> cache = new ReferenceCache<String,String>(Hard, Hard) {
            protected String create(String key) { return "bar"; }};
        cache.init("foo");

        assertThat(cache.size(), equalTo(1));
        assertThat(cache.get("foo"), equalTo("bar"));
        System.gc();
    }

    @Test
    public void soft_reference_key() throws Exception {
        ReferenceCache<String,Void> cache = new ReferenceCache<String,Void>(Soft, Soft) {
            protected Void create(String key) { return null; }};
        cache.init("foo");

        assertThat(cache.size(), equalTo(1));
        assertThat(cache.get("foo"), nullValue());
        System.gc();
    }

    @Test
    public void soft_reference_value() throws Exception {
        ReferenceCache<String,String> cache = new ReferenceCache<String,String>(Soft, Soft) {
            protected String create(String key) { return "bar"; }};
        cache.init("foo");

        assertThat(cache.size(), equalTo(1));
        assertThat(cache.get("foo"), equalTo("bar"));
        System.gc();
    }

    @Test
    public void weak_reference_key() throws Exception {
        ReferenceCache<String,Void> cache = new ReferenceCache<String,Void>(Weak, Weak){
            protected Void create(String key) { return null; }};
        cache.init("foo");

        assertThat(cache.size(), equalTo(1));
        assertThat(cache.get("foo"), nullValue());
        System.gc();
    }

    @Test
    public void weak_reference_value() throws Exception {
        ReferenceCache<String,String> cache = new ReferenceCache<String,String>(Weak, Weak){
            protected String create(String key) { return "bar"; }};
        cache.init("foo");

        assertThat(cache.size(), equalTo(1));
        assertThat(cache.get("foo"), equalTo("bar"));
        System.gc();
    }

    @Test
    public void null_cache_key() throws Exception {
        ReferenceCache<String,String> cache = new ReferenceCache<String,String>(Weak, Weak, 2){
            protected String create(String key) { return "bar"; }};
        cache.init(null);

        assertThat(cache.get(null), nullValue());
        System.gc();
    }


    @Test
    public void remove_cache_entry() throws Exception {
        ReferenceCache<String,String> cache = new ReferenceCache<String,String>(Weak, Weak){
            protected String create(String key) { return "bar"; }};
        cache.init("foo");

        assertThat(cache.size(), equalTo(1));
        assertThat(cache.get("foo"), equalTo("bar"));
        assertTrue(cache.remove("foo"));
        assertThat(cache.size(), equalTo(0));
        System.gc();
    }
}

