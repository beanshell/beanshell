package bsh.classpath;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import bsh.ClassPathException;
import bsh.EvalError;
import bsh.Interpreter;
import bsh.NameSource;
import bsh.classpath.BshClassPath.ClassSource;
import bsh.classpath.BshClassPath.DirClassSource;
import bsh.classpath.BshClassPath.GeneratedClassSource;
import bsh.classpath.BshClassPath.JarClassSource;
import bsh.classpath.BshClassPath.JrtClassSource;
import bsh.classpath.BshClassPath.MappingFeedback;


public class BshClassPathTest {
    static class ClassPathListenerImpl implements ClassPathListener {
        public boolean changed = false;
        @Override
        public void classPathChanged() { changed = true; }
    }
    static class NameSourceListener implements NameSource.Listener {
        List<NameSource> names = new ArrayList<>();
        public String [] getAllNames() {
            return names.stream().map(Object::getClass)
                    .map(Class::getSimpleName).toArray(String[]::new);
        }
        @Override
        public void nameSourceChanged(NameSource src) {
            names.add(src);
        }
    }
    static class ClassPathMappingFeedback implements MappingFeedback {
        public boolean start = false;
        public boolean end = false;
        public String fs = "";
        public String err = "";
        @Override
        public void startClassMapping() { start = true; }
        @Override
        public void classMapping(String msg) {
            if (msg.startsWith("FileSystem")) fs = msg;
        }
        @Override
        public void errorWhileMapping(String msg) { err = msg; }
        @Override
        public void endClassMapping() { end = true; }
    }
    static final ClassPathMappingFeedback cpmf = new ClassPathMappingFeedback();
    static {
        BshClassPath.addMappingFeedback(cpmf);
    }

    @Test
    public void classpath_mapping_feedback() throws ClassPathException {
        final Interpreter bsh = new Interpreter();
        ClassManagerImpl cm = (ClassManagerImpl) bsh.getNameSpace().getClassManager();
        BshClassPath bcp =  cm.getClassPath();
        /*
         * Since this is a unit test we will need to clear
         * cached values for this test to work  reliably
         */
        BshClassPath.getBootClassPath().classPathChanged();
        bcp.getAllNames();
        assertTrue("Got feedback start", cpmf.start);
        assertTrue("Got feedback end", cpmf.end);
        assertThat("Got feedback FILESYSTEM jrt or rt.jar", cpmf.fs,
                anyOf(containsString("jrt:/java.base"), containsString("rt.jar!/")));
    }

    @Test
    public void classpath_mapping_feedback_error() throws ClassPathException, MalformedURLException, IOException {
        final Interpreter bsh = new Interpreter();
        ClassManagerImpl cm = (ClassManagerImpl) bsh.getNameSpace().getClassManager();
        BshClassPath bcp =  cm.getClassPath();
        bcp.insureInitialized();
        bcp.add(new URL[] { new URL("file:/unknown/path") });
        bcp.add(new URL("file:/unknown/path"));
        assertThat("Got feedback error", cpmf.err,
                equalTo("Not a classpath component: /unknown/path"));
    }

    @Test
    public void classpath_mapping_feedback_null() {
        RuntimeException e = assertThrows(RuntimeException.class, () ->
            BshClassPath.addMappingFeedback(null));
        assertThat(e.getMessage(), containsString("Unimplemented: already a listener"));
    }

    @Test
    public void classpath_map_filesystem_exception() throws ClassPathException {

        final Interpreter bsh = new Interpreter();
        ClassManagerImpl cm = (ClassManagerImpl) bsh.getNameSpace().getClassManager();
        BshClassPath bcp =  cm.getClassPath();
        RuntimeException e = assertThrows(RuntimeException.class, () ->
            bcp.map(new URL[] { new URL("jar:file:/unknown/path!/") }));
        assertThat(e.getMessage(), containsString("Failed to map"));
    }

    @Test
    public void classpath_map_jar_filesystem() throws IOException, ClassPathException {
        final Interpreter bsh = new Interpreter();
        File jarFile = bsh.pathToFile("src/test/resources/test-scripts/Data/addclass.jar");
        URL jarURL = new URL("jar:file:"+jarFile.toString()+"!/");
        ClassManagerImpl cm = (ClassManagerImpl) bsh.getNameSpace().getClassManager();

        BshClassPath bcp =  cm.getClassPath();
        bcp.map(new URL[] { jarURL });
        assertThat(cpmf.fs, containsString("addclass.jar"));
    }

    @Test
    public void classpath_get_jar_source() throws IOException, ClassPathException {
        final Interpreter bsh = new Interpreter();
        File jarFile = bsh.pathToFile("src/test/resources/test-scripts/Data/addclass.jar");
        URL jarURL = new URL("jar:file:"+jarFile.toString()+"!/");
        ClassManagerImpl cm = (ClassManagerImpl) bsh.getNameSpace().getClassManager();

        BshClassPath bcp =  cm.getClassPath();
        bcp.map(new URL[] { jarURL });

        JarClassSource jarSrc = (JarClassSource)bcp.getClassSource("AddClass");
        assertThat(jarSrc, notNullValue());
        assertThat(jarSrc, instanceOf(JarClassSource.class));
        assertThat(jarSrc.toString(), containsString("addclass.jar"));
        assertThat(jarSrc.getURL(), equalTo(jarURL));
        assertThat(jarSrc.getCode("AddClass"), instanceOf(byte[].class));
        assertThat(jarSrc.getCode("Unknown"), equalTo(new byte[0]));
    }

    @Test
    public void classpath_listener() throws ClassPathException {
        final Interpreter bsh = new Interpreter();
        ClassManagerImpl cm = (ClassManagerImpl) bsh.getNameSpace().getClassManager();
        BshClassPath bcp =  cm.getClassPath();
        ClassPathListenerImpl listener = new ClassPathListenerImpl();
        bcp.addListener(listener);
        assertFalse("has not changed", listener.changed);
        bcp.classPathChanged();
        assertTrue("has changed", listener.changed);
        ClassPathListenerImpl listener2 = new ClassPathListenerImpl();
        bcp.addListener(listener2);
        bcp.removeListener(listener2);
        bcp.notifyListeners();
        assertFalse("has not changed", listener2.changed);
        assertThat(bcp.listeners, hasSize(1));
        listener = null;
        System.gc();
        bcp.classPathChanged();
        assertThat(bcp.listeners, hasSize(0));
    }

    @Test
    public void classpath_namesource_listener() throws ClassPathException {
        final Interpreter bsh = new Interpreter();
        ClassManagerImpl cm = (ClassManagerImpl) bsh.getNameSpace().getClassManager();
        BshClassPath bcp =  cm.getClassPath();
        NameSourceListener listener = new NameSourceListener();
        bcp.addNameSourceListener(new NameSourceListener());
        bcp.addNameSourceListener(listener);
        assertThat("names 0 length", listener.getAllNames(), arrayWithSize(0));
        bcp.nameSpaceChanged();
        assertThat("names 1 length", listener.getAllNames(), arrayWithSize(1));
        assertThat("names has BshClassPath", listener.getAllNames(), arrayContaining("BshClassPath"));
    }

    @Test
    public void classpath_get_unq_name() throws ClassPathException {
        final Interpreter bsh = new Interpreter();
        ClassManagerImpl cm = (ClassManagerImpl) bsh.getNameSpace().getClassManager();
        BshClassPath bcp =  cm.getClassPath();
        assertThat("Short is java.lang.Short",
                bcp.getClassNameByUnqName("Short"), equalTo("java.lang.Short"));
    }

    @Test
    public void classpath_to_string() throws ClassPathException {
        final Interpreter bsh = new Interpreter();
        ClassManagerImpl cm = (ClassManagerImpl) bsh.getNameSpace().getClassManager();
        BshClassPath bcp =  cm.getClassPath();
        assertThat("to string start with BshClassPath",
                bcp.toString(), startsWith("BshClassPath"));
    }

    @Test
    public void classpath_get_unq_name_ambigous() throws ClassPathException {
        final Interpreter bsh = new Interpreter();
        ClassManagerImpl cm = (ClassManagerImpl) bsh.getNameSpace().getClassManager();
        BshClassPath bcp =  cm.getClassPath();
        ClassPathException e = assertThrows(ClassPathException.class, () ->
            bcp.getClassNameByUnqName("Handler"));
        assertThat(e.getMessage(), containsString("Ambiguous class names"));
    }

    @Test
    public void classpath_get_full_path() throws ClassPathException {
        final Interpreter bsh = new Interpreter();
        ClassManagerImpl cm = (ClassManagerImpl) bsh.getNameSpace().getClassManager();
        BshClassPath bcp =  cm.getClassPath();
        assertThat(Arrays.asList(BshClassPath.getBootClassPath().getFullPath().toArray()), hasSize(1));
        assertThat(Arrays.asList(bcp.getFullPath().toArray()),
                hasItem(BshClassPath.getBootClassPath().getFullPath().get(0)));
    }

    @Test
    public void classpath_get_class_source() throws ClassPathException, IllegalAccessException,
            InvocationTargetException, NoSuchMethodException, SecurityException, EvalError {
        final Interpreter bsh = new Interpreter();
        ClassManagerImpl cm = (ClassManagerImpl) bsh.getNameSpace().getClassManager();

        BshClassPath bcp =  cm.getClassPath();
        bcp.addComponent(null);
        assertThat(bcp.getClassSource("VeryUnknown"), nullValue());
        ClassSource rtSrc = bcp.getClassSource("java.lang.String");
        assertThat(rtSrc, anyOf(instanceOf(JrtClassSource.class), instanceOf(JarClassSource.class)));
        assertThat(rtSrc.toString(),
                containsString(""+BshClassPath.getBootClassPath().getFullPath().get(0)));
        assertThat(rtSrc.getClass().getMethod("getURL", new Class[0]).invoke(rtSrc, new Object[0]),
                equalTo(BshClassPath.getBootClassPath().getFullPath().get(0)));
        assertThat(rtSrc.getCode("java.lang.String"), instanceOf(byte[].class));
        assertThat(rtSrc.getCode("Unknown"), equalTo(new byte[0]));
        ClassSource dirSrc = bcp.getClassSource(this.getClass().getName());
        assertThat(dirSrc, instanceOf(DirClassSource.class));
        assertThat(dirSrc.toString(), endsWith("test-classes"));
        assertThat(((DirClassSource) dirSrc).getDir().getAbsolutePath(), endsWith("test-classes"));
        assertThat(dirSrc.getCode(this.getClass().getName()), instanceOf(byte[].class));
        assertThat(dirSrc.getCode("Unknown"), nullValue());
        bsh.eval("class ABC {}");
        ClassSource genSrc = bcp.getClassSource("ABC");
        assertThat(genSrc, instanceOf(GeneratedClassSource.class));
        assertThat(genSrc.getCode(""), instanceOf(byte[].class));
    }

    @Test
    public void classpath_is_archive_filename() {
        assertTrue(BshClassPath.isArchiveFileName("abc.zip"));
        assertTrue(BshClassPath.isArchiveFileName("abc.jar"));
        assertTrue(BshClassPath.isArchiveFileName("abc.jmod"));
        assertFalse(BshClassPath.isArchiveFileName("abc.abc"));
    }

    @Test
    public void classpath_canon_classname() {
        assertEquals(String.class.getName(), BshClassPath.canonicalizeClassName(""+String.class));
        assertEquals("abc.ABC", BshClassPath.canonicalizeClassName("classes.abc.ABC"));
        assertEquals("abc.ABC", BshClassPath.canonicalizeClassName("abc/ABC"));
        assertEquals("abc.ABC", BshClassPath.canonicalizeClassName("abc\\ABC"));
        assertEquals("abc.ABC", BshClassPath.canonicalizeClassName("/abc/ABC"));
        assertEquals("abc.ABC", BshClassPath.canonicalizeClassName("\\abc\\ABC"));
        assertEquals("abc.ABC", BshClassPath.canonicalizeClassName("abc/ABC.class"));
        assertEquals("abc.ABC", BshClassPath.canonicalizeClassName("modules/some.mod/abc.ABC"));
    }

    @Test
    public void classpath_name_completion_includes_unq_names() throws ClassPathException {
        final Interpreter bsh = new Interpreter();
        ClassManagerImpl cm = (ClassManagerImpl) bsh.getNameSpace().getClassManager();

        BshClassPath bcp =  cm.getClassPath();
        assertTrue(bcp.isNameCompletionIncludesUnqNames());
        assertThat(Arrays.asList(bcp.getAllNames()), hasItem("BSHCastExpression"));
        bcp.setNameCompletionIncludesUnqNames(false);
        assertFalse(bcp.isNameCompletionIncludesUnqNames());
        assertThat(Arrays.asList(bcp.getAllNames()), not(hasItem("BSHCastExpression")));
        bcp.setNameCompletionIncludesUnqNames(true);
    }

    @Test
    public void classpath_map_initialized() throws ClassPathException {
        final Interpreter bsh = new Interpreter();
        ClassManagerImpl cm = (ClassManagerImpl) bsh.getNameSpace().getClassManager();

        BshClassPath bcp =  cm.getClassPath();
        bcp.insureInitialized();
        assertTrue(bcp.isMapsInitialized());
        bcp.setPath(new URL[0]);
        assertFalse(bcp.isMapsInitialized());
        bcp.setMapsInitialized(true);
        assertTrue(bcp.isMapsInitialized());
    }

    @Test
    public void classpath_unq_name_table() throws ClassPathException {
        final Interpreter bsh = new Interpreter();
        ClassManagerImpl cm = (ClassManagerImpl) bsh.getNameSpace().getClassManager();

        BshClassPath bcp =  cm.getClassPath();

        assertThat(bcp.getUnqNameTable(), nullValue());
        bcp.getClassNameByUnqName("");
        assertThat(bcp.getUnqNameTable(), notNullValue());
        bcp.setUnqNameTable(null);
        assertThat(bcp.getUnqNameTable(), nullValue());
    }

    @Test
    public void classpath_name_source_listeners() throws ClassPathException {
        final Interpreter bsh = new Interpreter();
        ClassManagerImpl cm = (ClassManagerImpl) bsh.getNameSpace().getClassManager();

        BshClassPath bcp =  cm.getClassPath();
        assertThat(bcp.getNameSourceListeners(), nullValue());
        bcp.addNameSourceListener(new NameSourceListener());
        assertThat(bcp.getNameSourceListeners(), notNullValue());
        bcp.setNameSourceListeners(null);
        assertThat(bcp.getNameSourceListeners(), nullValue());
    }
}

