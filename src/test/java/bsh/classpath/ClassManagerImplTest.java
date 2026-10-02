package bsh.classpath;

import static bsh.BshClassManager.Listener;
import static bsh.TestUtil.measureConcurrentTime;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import bsh.Interpreter;

public class ClassManagerImplTest {
    static {
        // set up static class path
        new BshClassPathTest();
    }
    static class ClassLoaderListenerImpl implements Listener {
        public boolean changed = false;
        @Override
        public void classLoaderChanged() { changed = true; }
    }

    @Test
    public void cm_class_loader_listener() throws Exception {
        final Interpreter bsh = new Interpreter();
        ClassManagerImpl cm = (ClassManagerImpl) bsh.getNameSpace().getClassManager();
        final AtomicInteger counter = new AtomicInteger();
        final Set<WeakReference<byte[]>> heap = ConcurrentHashMap.newKeySet();
        final Runnable runnable = new Runnable() {
            public void run() {
                counter.incrementAndGet();
                ClassLoaderListenerImpl listener = new ClassLoaderListenerImpl();
                cm.addListener(listener);
                cm.reset();
                assertTrue(listener.changed);
                heap.add(new WeakReference<byte[]>(new byte[1024*1000]));
            }
        };
        measureConcurrentTime(runnable, 30, 30, 100);
        heap.clear();
        cm.reset();
        bsh.getNameSpace().clear();
    }

    @Test
    public void cm_add_class_path_after_failed_lookup() throws Exception {
        final Interpreter bsh = new Interpreter();
        ClassManagerImpl cm = (ClassManagerImpl) bsh.getNameSpace().getClassManager();
        assertThat(cm.classForName("AddClass"), nullValue());

        File jar = bsh.pathToFile("src/test/resources/test-scripts/Data/addclass.jar");
        cm.addClassPath(jar.toURI().toURL());

        assertThat(cm.classForName("AddClass"), notNullValue());
        bsh.getNameSpace().clear();
    }

    @Test
    public void cm_reload_package() throws Exception {
        final Interpreter bsh = new Interpreter();
        ClassManagerImpl cm = (ClassManagerImpl) bsh.getNameSpace().getClassManager();
        assertThat(cm.classForName("java.lang.String"), equalTo(String.class));
        cm.reloadPackage("java.lang");
        assertThat(cm.classForName("java.lang.String"), equalTo(String.class));
        bsh.getNameSpace().clear();
    }

    /**
        On a case-insensitive file system (e.g. Windows) a request to load a
        class named "foo" can resolve to a resource "Foo.class", causing the
        JVM to throw NoClassDefFoundError: foo (wrong name: Foo) instead of
        simply reporting the class as not found. wrongcase.jar reproduces the
        same "wrong name" mismatch (independent of the host OS) by storing a
        class file under an entry name that differs from its real internal
        name. classForName() should treat this as "not found" rather than
        letting the error propagate.
    */
    @Test
    public void cm_class_for_name_falls_through_on_wrong_name_mismatch() throws Exception {
        final Interpreter bsh = new Interpreter();
        ClassManagerImpl cm = (ClassManagerImpl) bsh.getNameSpace().getClassManager();

        File jar = bsh.pathToFile("src/test/resources/test-scripts/Data/wrongcase.jar");
        cm.addClassPath(jar.toURI().toURL());

        assertThat(cm.classForName("wrongcase"), nullValue());
        bsh.getNameSpace().clear();
    }
}

