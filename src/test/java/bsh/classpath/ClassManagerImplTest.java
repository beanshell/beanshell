package bsh.classpath;

import static bsh.BshClassManager.Listener;
import static bsh.TestUtil.measureConcurrentTime;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertTrue;

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
    public void cm_reload_package() throws Exception {
        final Interpreter bsh = new Interpreter();
        ClassManagerImpl cm = (ClassManagerImpl) bsh.getNameSpace().getClassManager();
        assertThat(cm.classForName("java.lang.String"), equalTo(String.class));
        cm.reloadPackage("java.lang");
        assertThat(cm.classForName("java.lang.String"), equalTo(String.class));
        bsh.getNameSpace().clear();
    }
}

