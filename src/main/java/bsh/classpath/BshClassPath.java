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
package bsh.classpath;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import bsh.ClassPathException;
import bsh.NameSource;
import bsh.StringUtil;

/**
 * A BshClassPath encapsulates knowledge about a class path of URLs.
 * It can maps all classes the path which may include:
 * jar/zip files and base dirs
 *
 * A BshClassPath may composite other BshClassPaths as components of its
 * path and will reflect changes in those components through its methods
 * and listener interface.
 *
 * Classpath traversal is done lazily when a call is made to
 * getClassesForPackage() or getClassSource()
 * or can be done explicitily through insureInitialized().
 * Feedback on mapping progress is provided through the MappingFeedback
 * interface.
 *
 * Design notes:
 * Several times here we traverse ourselves and our component paths to
 * produce a composite view of some thing relating to the path. This would
 * be an opportunity for a visitor pattern.
 */
public class BshClassPath implements ClassPathListener, NameSource {

    /** The name. */
    String name;
    /** The URL path components. */
    private List path;
    /** Ordered list of components BshClassPaths. */
    private List compPaths;
    /** Set of classes in a package mapped by package name. */
    private Map packageMap;
    /** Map of source (URL or File dir) of every clas. */
    private Map classSource;
    /** The packageMap and classSource maps have been built. */
    private boolean mapsInitialized;
    /** The unq name table. */
    private UnqualifiedNameTable unqNameTable;
    /**
     * This used to be configurable, but now we always include them.
     */
    private final boolean nameCompletionIncludesUnqNames = true;
    /** The listeners. */
    Vector listeners = new Vector();

    /**
     * Instantiates a new bsh class path.
     *
     * @param name
     *            the name
     */
    public BshClassPath(final String name) {
        this.name = name;
        this.reset();
    }

    /**
     * Instantiates a new bsh class path.
     *
     * @param name
     *            the name
     * @param urls
     *            the urls
     */
    public BshClassPath(final String name, final URL[] urls) {
        this(name);
        this.add(urls);
    }
    // end constructors

    /**
     * Sets the path.
     *
     * @param urls
     *            the new path
     */
    public void setPath(final URL[] urls) {
        this.reset();
        this.add(urls);
    }

    /**
     * Add the specified BshClassPath as a component of our path.
     * Changes in the bcp will be reflected through us.
     *
     * @param bcp
     *            the bcp
     */
    public void addComponent(final BshClassPath bcp) {
        if (this.compPaths == null)
            this.compPaths = new ArrayList();
        this.compPaths.add(bcp);
        bcp.addListener(this);
    }

    /**
     * Adds the.
     *
     * @param urls
     *            the urls
     */
    public void add(final URL[] urls) {
        this.path.addAll(Arrays.asList(urls));
        if (this.mapsInitialized)
            this.map(urls);
    }

    /**
     * Adds the.
     *
     * @param url
     *            the url
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    public void add(final URL url) throws IOException {
        this.path.add(url);
        if (this.mapsInitialized)
            this.map(url);
    }

    /**
     * Get the path components including any component paths.
     *
     * @return the path components
     */
    public URL[] getPathComponents() {
        return (URL[]) this.getFullPath().toArray(new URL[0]);
    }

    /**
     * Return the set of class names in the specified package
     * including all component paths.
     *
     * @param pack
     *            the pack
     * @return the classes for package
     */
    public synchronized Set getClassesForPackage(final String pack) {
        this.insureInitialized();
        final Set set = new HashSet();
        Collection c = (Collection) this.packageMap.get(pack);
        if (c != null)
            set.addAll(c);
        if (this.compPaths != null)
            for (int i = 0; i < this.compPaths.size(); i++) {
                c = ((BshClassPath) this.compPaths.get(i))
                        .getClassesForPackage(pack);
                if (c != null)
                    set.addAll(c);
            }
        return set;
    }

    /**
     * Return the source of the specified class which may lie in component
     * path.
     *
     * @param className
     *            the class name
     * @return the class source
     */
    public synchronized ClassSource getClassSource(final String className) {
        // Before triggering classpath mapping (initialization) check for
        // explicitly set class sources (e.g. generated classes). These would
        // take priority over any found in the classpath anyway.
        ClassSource cs = (ClassSource) this.classSource.get(className);
        if (cs != null)
            return cs;
        this.insureInitialized(); // trigger possible mapping
        cs = (ClassSource) this.classSource.get(className);
        if (cs == null && this.compPaths != null)
            for (int i = 0; i < this.compPaths.size() && cs == null; i++)
                cs = ((BshClassPath) this.compPaths.get(i))
                        .getClassSource(className);
        return cs;
    }

    /**
     * Explicitly set a class source. This is used for generated classes, but
     * could potentially be used to allow a user to override which version of
     * a class from the classpath is located.
     *
     * @param className
     *            the class name
     * @param cs
     *            the cs
     */
    public synchronized void setClassSource(final String className,
            final ClassSource cs) {
        this.classSource.put(className, cs);
    }

    /**
     * If the claspath map is not initialized, do it now.
     * If component maps are not do them as well...
     *
     * Random note:
     * Should this be "insure" or "ensure". I know I've seen "ensure" used
     * in the JDK source. Here's what Webster has to say:
     *
     * Main Entry:ensure Pronunciation:in-'shur
     * Function:transitive verb Inflected
     * Form(s):ensured; ensuring : to make sure,
     * certain, or safe : GUARANTEE synonyms ENSURE,
     * INSURE, ASSURE, SECURE mean to make a thing or
     * person sure. ENSURE, INSURE, and ASSURE are
     * interchangeable in many contexts where they
     * indicate the making certain or inevitable of an
     * outcome, but INSURE sometimes stresses the
     * taking of necessary measures beforehand, and
     * ASSURE distinctively implies the removal of
     * doubt and suspense from a person's mind. SECURE
     * implies action taken to guard against attack or
     * loss.
     */
    public void insureInitialized() {
        this.insureInitialized(true);
    }

    /**
     * Insure initialized.
     *
     * @param topPath
     *            indicates that this is the top level classpath
     *            component and it should send the startClassMapping message
     */
    protected synchronized void insureInitialized(final boolean topPath) {
        // If we are the top path and haven't been initialized before
        // inform the listeners we are going to do expensive map
        if (topPath && !this.mapsInitialized)
            this.startClassMapping();
        // initialize components
        if (this.compPaths != null)
            for (int i = 0; i < this.compPaths.size(); i++)
                ((BshClassPath) this.compPaths.get(i)).insureInitialized(false);
        // initialize ourself
        if (!this.mapsInitialized)
            this.map((URL[]) this.path.toArray(new URL[0]));
        if (topPath && !this.mapsInitialized)
            this.endClassMapping();
        this.mapsInitialized = true;
    }

    /**
     * Get the full path including component paths.
     * (component paths listed first, in order)
     * Duplicate path components are removed.
     *
     * @return the full path
     */
    protected List getFullPath() {
        final List list = new ArrayList();
        if (this.compPaths != null)
            for (int i = 0; i < this.compPaths.size(); i++) {
                final List l = ((BshClassPath) this.compPaths.get(i))
                        .getFullPath();
                // take care to remove dups
                // wish we had an ordered set collection
                final Iterator it = l.iterator();
                while (it.hasNext()) {
                    final Object o = it.next();
                    if (!list.contains(o))
                        list.add(o);
                }
            }
        list.addAll(this.path);
        return list;
    }

    /**
     * Support for super import "*";
     * Get the full name associated with the unqualified name in this
     * classpath. Returns either the String name or an AmbiguousName object
     * encapsulating the various names.
     *
     * @param name
     *            the name
     * @return the class name by unq name
     * @throws ClassPathException
     *             the class path exception
     */
    public String getClassNameByUnqName(final String name)
            throws ClassPathException {
        this.insureInitialized();
        final UnqualifiedNameTable unqNameTable = this
                .getUnqualifiedNameTable();
        final Object obj = unqNameTable.get(name);
        if (obj instanceof AmbiguousName)
            throw new ClassPathException(
                    "Ambigous class names: " + ((AmbiguousName) obj).get());
        return (String) obj;
    }

    /**
     * Gets the unqualified name table.
     *
     * @return the unqualified name table
     *
     * Note: we could probably do away with the unqualified name table
     * in favor of a second name source
     */
    private UnqualifiedNameTable getUnqualifiedNameTable() {
        if (this.unqNameTable == null)
            this.unqNameTable = this.buildUnqualifiedNameTable();
        return this.unqNameTable;
    }

    /**
     * Builds the unqualified name table.
     *
     * @return the unqualified name table
     */
    private UnqualifiedNameTable buildUnqualifiedNameTable() {
        final UnqualifiedNameTable unqNameTable = new UnqualifiedNameTable();
        // add component names
        if (this.compPaths != null)
            for (int i = 0; i < this.compPaths.size(); i++) {
                final Set s = ((BshClassPath) this.compPaths.get(i)).classSource
                        .keySet();
                final Iterator it = s.iterator();
                while (it.hasNext())
                    unqNameTable.add((String) it.next());
            }
        // add ours
        final Iterator it = this.classSource.keySet().iterator();
        while (it.hasNext())
            unqNameTable.add((String) it.next());
        return unqNameTable;
    }

    /** {@inheritDoc} */
    public String[] getAllNames() {
        this.insureInitialized();
        final List names = new ArrayList();
        final Iterator it = this.getPackagesSet().iterator();
        while (it.hasNext()) {
            final String pack = (String) it.next();
            names.addAll(
                    removeInnerClassNames(this.getClassesForPackage(pack)));
        }
        if (this.nameCompletionIncludesUnqNames)
            names.addAll(this.getUnqualifiedNameTable().keySet());
        return (String[]) names.toArray(new String[0]);
    }

    /**
     * call map(url) for each url in the array.
     *
     * @param urls
     *            the urls
     */
    synchronized void map(final URL[] urls) {
        for (final URL url : urls)
            try {
                this.map(url);
            } catch (final IOException e) {
                final String s = "Error constructing classpath: " + url + ": "
                        + e;
                this.errorWhileMapping(s);
            }
    }

    /**
     * Map.
     *
     * @param url
     *            the url
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    synchronized void map(final URL url) throws IOException {
        final String name = url.getFile();
        final File f = new File(name);
        if (f.isDirectory()) {
            this.classMapping("Directory " + f.toString());
            this.map(traverseDirForClasses(f), new DirClassSource(f));
        } else if (isArchiveFileName(name)) {
            this.classMapping("Archive: " + url);
            this.map(searchJarForClasses(url), new JarClassSource(url));
        } else {
            final String s = "Not a classpath component: " + name;
            this.errorWhileMapping(s);
        }
    }

    /**
     * Map.
     *
     * @param classes
     *            the classes
     * @param source
     *            the source
     */
    private void map(final String[] classes, final Object source) {
        for (final String classe : classes)
            // System.out.println(classes[i] +": "+ source);
            this.mapClass(classe, source);
    }

    /**
     * Map class.
     *
     * @param className
     *            the class name
     * @param source
     *            the source
     */
    private void mapClass(final String className, final Object source) {
        // add to package map
        final String[] sa = splitClassname(className);
        final String pack = sa[0];
        Set set = (Set) this.packageMap.get(pack);
        if (set == null) {
            set = new HashSet();
            this.packageMap.put(pack, set);
        }
        set.add(className);
        // Add to classSource map
        final Object obj = this.classSource.get(className);
        // don't replace previously set (found earlier in classpath or
        // explicitly set via setClassSource())
        if (obj == null)
            this.classSource.put(className, source);
    }

    /**
     * Clear everything and reset the path to empty.
     */
    private synchronized void reset() {
        this.path = new ArrayList();
        this.compPaths = null;
        this.clearCachedStructures();
    }

    /**
     * Clear anything cached. All will be reconstructed as necessary.
     */
    private synchronized void clearCachedStructures() {
        this.mapsInitialized = false;
        this.packageMap = new HashMap();
        this.classSource = new HashMap();
        this.unqNameTable = null;
        this.nameSpaceChanged();
    }

    /** {@inheritDoc} */
    public void classPathChanged() {
        this.clearCachedStructures();
        this.notifyListeners();
    }
    /*
     * public void setNameCompletionIncludeUnqNames(boolean b) {
     * if (nameCompletionIncludesUnqNames != b) {
     * nameCompletionIncludesUnqNames = b;
     * nameSpaceChanged();
     * }
     * }
     */

    /**
     * Traverse dir for classes.
     *
     * @param dir
     *            the dir
     * @return the string[]
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    // Begin Static stuff
    static String[] traverseDirForClasses(final File dir) throws IOException {
        final List list = traverseDirForClassesAux(dir, dir);
        return (String[]) list.toArray(new String[0]);
    }

    /**
     * Traverse dir for classes aux.
     *
     * @param topDir
     *            the top dir
     * @param dir
     *            the dir
     * @return the list
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    static List traverseDirForClassesAux(final File topDir, final File dir)
            throws IOException {
        final List list = new ArrayList();
        final String top = topDir.getAbsolutePath();
        final File[] children = dir.listFiles();
        for (final File child : children)
            if (child.isDirectory())
                list.addAll(traverseDirForClassesAux(topDir, child));
            else {
                String name = child.getAbsolutePath();
                if (isClassFileName(name)) {
                    /*
                     * Remove absolute (topdir) portion of path and leave
                     * package-class part
                     */
                    if (name.startsWith(top))
                        name = name.substring(top.length() + 1);
                    else
                        throw new IOException("problem parsing paths");
                    name = canonicalizeClassName(name);
                    list.add(name);
                }
            }
        return list;
    }

    /**
     * Get the class file entries from the Jar.
     *
     * @param jar
     *            the jar
     * @return the string[]
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    static String[] searchJarForClasses(final URL jar) throws IOException {
        final Vector v = new Vector();
        final InputStream in = jar.openStream();
        final ZipInputStream zin = new ZipInputStream(in);
        ZipEntry ze;
        while ((ze = zin.getNextEntry()) != null) {
            final String name = ze.getName();
            if (isClassFileName(name))
                v.addElement(canonicalizeClassName(name));
        }
        zin.close();
        final String[] sa = new String[v.size()];
        v.copyInto(sa);
        return sa;
    }

    /**
     * Checks if is class file name.
     *
     * @param name
     *            the name
     * @return true, if is class file name
     */
    public static boolean isClassFileName(final String name) {
        return name.toLowerCase().endsWith(".class");
        // && (name.indexOf('$')==-1));
    }

    /**
     * Checks if is archive file name.
     *
     * @param name
     *            the name
     * @return true, if is archive file name
     */
    public static boolean isArchiveFileName(String name) {
        name = name.toLowerCase();
        return name.endsWith(".jar") || name.endsWith(".zip");
    }

    /**
     * Create a proper class name from a messy thing.
     * Turn / or \ into ., remove leading class and trailing .class
     *
     * Note: this makes lots of strings... could be faster.
     *
     * @param name
     *            the name
     * @return the string
     */
    public static String canonicalizeClassName(final String name) {
        String classname = name.replace('/', '.');
        classname = classname.replace('\\', '.');
        if (classname.startsWith("class "))
            classname = classname.substring(6);
        if (classname.endsWith(".class"))
            classname = classname.substring(0, classname.length() - 6);
        return classname;
    }

    /**
     * Split class name into package and name.
     *
     * @param classname
     *            the classname
     * @return the string[]
     */
    public static String[] splitClassname(String classname) {
        classname = canonicalizeClassName(classname);
        final int i = classname.lastIndexOf(".");
        String classn, packn;
        if (i == -1) {
            // top level class
            classn = classname;
            packn = "<unpackaged>";
        } else {
            packn = classname.substring(0, i);
            classn = classname.substring(i + 1);
        }
        return new String[] {packn, classn};
    }

    /**
     * Return a new collection without any inner class names.
     *
     * @param col
     *            the col
     * @return the collection
     */
    public static Collection removeInnerClassNames(final Collection col) {
        final List list = new ArrayList();
        list.addAll(col);
        final Iterator it = list.iterator();
        while (it.hasNext()) {
            final String name = (String) it.next();
            if (name.indexOf("$") != -1)
                it.remove();
        }
        return list;
    }

    /**
     * The user classpath from system propertiy.
     * java.class.path
     */
    static URL[] userClassPathComp;

    /**
     * Gets the user class path components.
     *
     * @return the user class path components
     * @throws ClassPathException
     *             the class path exception
     */
    public static URL[] getUserClassPathComponents() throws ClassPathException {
        if (userClassPathComp != null)
            return userClassPathComp;
        final String cp = System.getProperty("java.class.path");
        final String[] paths = StringUtil.split(cp, File.pathSeparator);
        final URL[] urls = new URL[paths.length];
        try {
            for (int i = 0; i < paths.length; i++)
                // We take care to get the canonical path first.
                // Java deals with relative paths for it's bootstrap loader
                // but JARClassLoader doesn't.
                urls[i] = new File(new File(paths[i]).getCanonicalPath())
                        .toURI().toURL();
        } catch (final IOException e) {
            throw new ClassPathException("can't parse class path: " + e);
        }
        userClassPathComp = urls;
        return urls;
    }

    /**
     * Get a list of all of the known packages.
     *
     * @return the packages set
     */
    public Set getPackagesSet() {
        this.insureInitialized();
        final Set set = new HashSet();
        set.addAll(this.packageMap.keySet());
        if (this.compPaths != null)
            for (int i = 0; i < this.compPaths.size(); i++)
                set.addAll(((BshClassPath) this.compPaths.get(i)).packageMap
                        .keySet());
        return set;
    }

    /**
     * Adds the listener.
     *
     * @param l
     *            the l
     */
    public void addListener(final ClassPathListener l) {
        this.listeners.addElement(new WeakReference(l));
    }

    /**
     * Removes the listener.
     *
     * @param l
     *            the l
     */
    public void removeListener(final ClassPathListener l) {
        this.listeners.removeElement(l);
    }

    /**
     * Notify listeners.
     */
    void notifyListeners() {
        for (final Enumeration e = this.listeners.elements();
                e.hasMoreElements();) {
            final WeakReference wr = (WeakReference) e.nextElement();
            final ClassPathListener l = (ClassPathListener) wr.get();
            if (l == null) // garbage collected
                this.listeners.removeElement(wr);
            else
                l.classPathChanged();
        }
    }

    /** The user class path. */
    static BshClassPath userClassPath;

    /**
     * A BshClassPath initialized to the user path.
     * from java.class.path
     *
     * @return the user class path
     * @throws ClassPathException
     *             the class path exception
     */
    public static BshClassPath getUserClassPath() throws ClassPathException {
        if (userClassPath == null)
            userClassPath = new BshClassPath("User Class Path",
                    getUserClassPathComponents());
        return userClassPath;
    }

    /** The boot class path. */
    static BshClassPath bootClassPath;

    /**
     * Get the boot path including the lib/rt.jar if possible.
     *
     * @return the boot class path
     * @throws ClassPathException
     *             the class path exception
     */
    public static BshClassPath getBootClassPath() throws ClassPathException {
        if (bootClassPath == null)
            try {
                // String rtjar = System.getProperty("java.home")+"/lib/rt.jar";
                final String rtjar = getRTJarPath();
                final URL url = new File(rtjar).toURI().toURL();
                bootClassPath = new BshClassPath("Boot Class Path",
                        new URL[] {url});
            } catch (final MalformedURLException e) {
                throw new ClassPathException(" can't find boot jar: " + e);
            }
        return bootClassPath;
    }

    /**
     * Gets the RT jar path.
     *
     * @return the RT jar path
     */
    private static String getRTJarPath() {
        final String urlString = Class.class
                .getResource("/java/lang/String.class").toExternalForm();
        if (!urlString.startsWith("jar:file:"))
            return null;
        final int i = urlString.indexOf("!");
        if (i == -1)
            return null;
        return urlString.substring("jar:file:".length(), i);
    }

    /**
     * The Class ClassSource.
     */
    public abstract static class ClassSource {

        /** The source. */
        Object source;

        /**
         * Gets the code.
         *
         * @param className
         *            the class name
         * @return the code
         */
        abstract byte[] getCode(String className);
    }

    /**
     * The Class JarClassSource.
     */
    public static class JarClassSource extends ClassSource {

        /**
         * Instantiates a new jar class source.
         *
         * @param url
         *            the url
         */
        JarClassSource(final URL url) {
            this.source = url;
        }

        /**
         * Gets the url.
         *
         * @return the url
         */
        public URL getURL() {
            return (URL) this.source;
        }

        /** {@inheritDoc} *
         * Note: we should implement this for consistency, however our
         * BshClassLoader can natively load from a JAR because it is a
         * URLClassLoader... so it may be better to allow it to do it.
         */
        @Override
        public byte[] getCode(final String className) {
            throw new Error("Unimplemented");
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return "Jar: " + this.source;
        }
    }

    /**
     * The Class DirClassSource.
     */
    public static class DirClassSource extends ClassSource {

        /**
         * Instantiates a new dir class source.
         *
         * @param dir
         *            the dir
         */
        DirClassSource(final File dir) {
            this.source = dir;
        }

        /**
         * Gets the dir.
         *
         * @return the dir
         */
        public File getDir() {
            return (File) this.source;
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return "Dir: " + this.source;
        }

        /** {@inheritDoc} */
        @Override
        public byte[] getCode(final String className) {
            return readBytesFromFile(this.getDir(), className);
        }

        /**
         * Read bytes from file.
         *
         * @param base
         *            the base
         * @param className
         *            the class name
         * @return the byte[]
         */
        public static byte[] readBytesFromFile(final File base,
                final String className) {
            final String n = className.replace('.', File.separatorChar)
                    + ".class";
            final File file = new File(base, n);
            if (file == null || !file.exists())
                return null;
            byte[] bytes;
            try {
                final FileInputStream fis = new FileInputStream(file);
                final DataInputStream dis = new DataInputStream(fis);
                bytes = new byte[(int) file.length()];
                dis.readFully(bytes);
                dis.close();
            } catch (final IOException ie) {
                throw new RuntimeException("Couldn't load file: " + file);
            }
            return bytes;
        }
    }

    /**
     * The Class GeneratedClassSource.
     */
    public static class GeneratedClassSource extends ClassSource {

        /**
         * Instantiates a new generated class source.
         *
         * @param bytecode
         *            the bytecode
         */
        GeneratedClassSource(final byte[] bytecode) {
            this.source = bytecode;
        }

        /** {@inheritDoc} */
        @Override
        public byte[] getCode(final String className) {
            return (byte[]) this.source;
        }
    }

    /**
     * The main method.
     *
     * @param args
     *            the arguments
     * @throws Exception
     *             the exception
     */
    public static void main(final String[] args) throws Exception {
        final URL[] urls = new URL[args.length];
        for (int i = 0; i < args.length; i++)
            urls[i] = new File(args[i]).toURI().toURL();
        new BshClassPath("Test", urls);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "BshClassPath " + this.name + "(" + super.toString() + ") path= "
                + this.path + "\n" + "compPaths = {" + this.compPaths + " }";
    }

    /**
     * The Class UnqualifiedNameTable.
     *
     * Note: we could probably do away with the unqualified name table
     * in favor of a second name source
     */
    static class UnqualifiedNameTable extends HashMap {

        /** The Constant serialVersionUID. */
        private static final long serialVersionUID = 1L;

        /**
         * Adds the.
         *
         * @param fullname
         *            the fullname
         */
        void add(final String fullname) {
            final String name = splitClassname(fullname)[1];
            final Object have = super.get(name);
            if (have == null)
                super.put(name, fullname);
            else if (have instanceof AmbiguousName)
                ((AmbiguousName) have).add(fullname);
            else { // String
                final AmbiguousName an = new AmbiguousName();
                an.add((String) have);
                an.add(fullname);
                super.put(name, an);
            }
        }
    }

    /**
     * The Class AmbiguousName.
     */
    public static class AmbiguousName {

        /** The list. */
        List list = new ArrayList();

        /**
         * Adds the.
         *
         * @param name
         *            the name
         */
        public void add(final String name) {
            this.list.add(name);
        }

        /**
         * Gets the.
         *
         * @return the list
         */
        public List get() {
            // return (String[])list.toArray(new String[0]);
            return this.list;
        }
    }

    /**
     * Fire the NameSourceListeners.
     */
    void nameSpaceChanged() {
        if (this.nameSourceListeners == null)
            return;
        for (int i = 0; i < this.nameSourceListeners.size(); i++)
            ((NameSource.Listener) this.nameSourceListeners.get(i))
                    .nameSourceChanged(this);
    }

    /** The name source listeners. */
    List nameSourceListeners;

    /**
     * Implements NameSource
     * Add a listener who is notified upon changes to names in this space.
     *
     * @param listener
     *            the listener
     */
    public void addNameSourceListener(final NameSource.Listener listener) {
        if (this.nameSourceListeners == null)
            this.nameSourceListeners = new ArrayList();
        this.nameSourceListeners.add(listener);
    }

    /** only allow one for now. */
    static MappingFeedback mappingFeedbackListener;

    /**
     * Adds the mapping feedback.
     *
     * @param mf
     *            the mf
     */
    public static void addMappingFeedback(final MappingFeedback mf) {
        if (mappingFeedbackListener != null)
            throw new RuntimeException("Unimplemented: already a listener");
        mappingFeedbackListener = mf;
    }

    /**
     * Start class mapping.
     */
    void startClassMapping() {
        if (mappingFeedbackListener != null)
            mappingFeedbackListener.startClassMapping();
        else
            System.err.println("Start ClassPath Mapping");
    }

    /**
     * Class mapping.
     *
     * @param msg
     *            the msg
     */
    void classMapping(final String msg) {
        if (mappingFeedbackListener != null)
            mappingFeedbackListener.classMapping(msg);
        else
            System.err.println("Mapping: " + msg);
    }

    /**
     * Error while mapping.
     *
     * @param s
     *            the s
     */
    void errorWhileMapping(final String s) {
        if (mappingFeedbackListener != null)
            mappingFeedbackListener.errorWhileMapping(s);
        else
            System.err.println(s);
    }

    /**
     * End class mapping.
     */
    void endClassMapping() {
        if (mappingFeedbackListener != null)
            mappingFeedbackListener.endClassMapping();
        else
            System.err.println("End ClassPath Mapping");
    }

    /**
     * The Interface MappingFeedback.
     */
    public static interface MappingFeedback {

        /**
         * Start class mapping.
         */
        public void startClassMapping();

        /**
         * Provide feedback on the progress of mapping the classpath.
         *
         * @param msg
         *            is a message about the path component being mapped
         * @perc is an integer in the range 0-100 indicating percentage done
         *       public void classMapping(String msg, int perc);
         **
         * Provide feedback on the progress of mapping the classpath
         */
        public void classMapping(String msg);

        /**
         * Error while mapping.
         *
         * @param msg
         *            the msg
         */
        public void errorWhileMapping(String msg);

        /**
         * End class mapping.
         */
        public void endClassMapping();
    }
}
