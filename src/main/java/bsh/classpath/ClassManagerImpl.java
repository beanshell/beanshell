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

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;

import bsh.BshClassManager;
import bsh.ClassPathException;
import bsh.Interpreter; // for debug()
import bsh.UtilEvalError;
import bsh.classpath.BshClassPath.ClassSource;
import bsh.classpath.BshClassPath.GeneratedClassSource;
import bsh.classpath.BshClassPath.JarClassSource;

/**
 * <pre>
 Manage all classloading in BeanShell.
 Allows classpath extension and class file reloading.

 This class holds the implementation of the BshClassManager so that it
 can be separated from the core package.

 This class currently relies on 1.2 for BshClassLoader and weak references.
 Is there a workaround for weak refs?  If so we could make this work
 with 1.1 by supplying our own classloader code...

 See "http://www.beanshell.org/manual/classloading.html" for details
 on the bsh classloader architecture.

 Bsh has a multi-tiered class loading architecture.  No class loader is
 created unless/until a class is generated, the classpath is modified,
 or a class is reloaded.

 Note: we may need some synchronization in here

 Note on jdk1.2 dependency:

 We are forced to use weak references here to accommodate all of the
 fleeting namespace listeners.  (NameSpaces must be informed if the class
 space changes so that they can un-cache names).  I had the interesting
 thought that a way around this would be to implement BeanShell's own
 garbage collector...  Then I came to my senses and said - screw it,
 class re-loading will require 1.2.

 ---------------------

 Classloading precedence:

 in-script evaluated class (scripted class)
 in-script added / modified classpath

 optionally, external classloader
 optionally, thread context classloader

 plain Class.forName()
 source class (.java file in classpath)
 *
 * </pre>
 *
 */
public class ClassManagerImpl extends BshClassManager {

    /** The Constant BSH_PACKAGE. */
    static final String BSH_PACKAGE = "bsh";
    /**
     * The classpath of the base loader. Initially and upon reset() this is
     * an empty instance of BshClassPath. It grows as paths are added or is
     * reset when the classpath is explicitly set. This could also be called
     * the "extension" class path, but is not strictly confined to added path
     * (could be set arbitrarily by setClassPath())
     */
    private BshClassPath baseClassPath;
    /** The super import. */
    private boolean superImport;
    /**
     * This is the full blown classpath including baseClassPath (extensions),
     * user path, and java bootstrap path (rt.jar)
     *
     * This is lazily constructed and further (and more importantly) lazily
     * intialized in components because mapping the full path could be
     * expensive.
     *
     * The full class path is a composite of:
     * baseClassPath (user extension) : userClassPath : bootClassPath
     * in that order.
     */
    private BshClassPath fullClassPath;
    /** The listeners. */
    // ClassPath Change listeners
    private final Vector listeners = new Vector();
    /** The ref queue. */
    private final ReferenceQueue refQueue = new ReferenceQueue();
    /**
     * This handles extension / modification of the base classpath
     * The loader to use where no mapping of reloaded classes exists.
     *
     * The baseLoader is initially null meaning no class loader is used.
     */
    private BshClassLoader baseLoader;
    /** Map by classname of loaders to use for reloaded classes. */
    private Map loaderMap;

    /**
     * Used by BshClassManager singleton constructor.
     */
    public ClassManagerImpl() {
        this.reset();
    }

    /**
     * Class for name.
     *
     * @param name
     *            the name
     * @return the class or null
     */
    @Override
    public Class classForName(final String name) {
        // check positive cache
        Class c = (Class) this.absoluteClassCache.get(name);
        if (c != null)
            return c;
        // check negative cache
        if (this.absoluteNonClasses.get(name) != null) {
            if (Interpreter.DEBUG)
                Interpreter.debug("absoluteNonClass list hit: " + name);
            return null;
        }
        if (Interpreter.DEBUG)
            Interpreter.debug("Trying to load class: " + name);
        // Check explicitly mapped (reloaded) class...
        final ClassLoader overlayLoader = this.getLoaderForClass(name);
        if (overlayLoader != null)
            try {
                c = overlayLoader.loadClass(name);
            } catch (final Exception e) {
                // used to squeltch this... changed for 1.3
                // see BshClassManager
            } catch (final NoClassDefFoundError e2) {
                throw noClassDefFound(name, e2);
            }
        // insure that core classes are loaded from the same loader
        if (c == null)
            if (name.startsWith(BSH_PACKAGE))
                try {
                    c = Interpreter.class.getClassLoader().loadClass(name);
                } catch (final ClassNotFoundException e) {}
        // Check classpath extension / reloaded classes
        if (c == null)
            if (this.baseLoader != null)
                try {
                    c = this.baseLoader.loadClass(name);
                } catch (final ClassNotFoundException e) {}
        // Optionally try external classloader
        if (c == null)
            if (this.externalClassLoader != null)
                try {
                    c = this.externalClassLoader.loadClass(name);
                } catch (final ClassNotFoundException e) {}
        // Optionally try context classloader
        // Note that this might be a security violation
        // is catching the SecurityException sufficient for all environments?
        // or do we need a way to turn this off completely?
        if (c == null)
            try {
                final ClassLoader contextClassLoader = Thread.currentThread()
                        .getContextClassLoader();
                if (contextClassLoader != null)
                    c = Class.forName(name, true, contextClassLoader);
            } catch (final ClassNotFoundException e) { // fall through
            } catch (final SecurityException e) {} // fall through
        // try plain class forName()
        if (c == null)
            try {
                c = this.plainClassForName(name);
            } catch (final ClassNotFoundException e) {}
        // Try .java source file
        if (c == null)
            c = this.loadSourceClass(name);
        // Cache result (or null for not found)
        // Note: plainClassForName already caches, so it will be redundant
        // in that case, however this process only happens once
        this.cacheClassInfo(name, c);
        return c;
    }

    /**
     * Get a resource URL using the BeanShell classpath.
     *
     * @param path
     *            should be an absolute path
     * @return the resource
     */
    @Override
    public URL getResource(final String path) {
        URL url = null;
        if (this.baseLoader != null)
            // classloader wants no leading slash
            url = this.baseLoader.getResource(path.substring(1));
        if (url == null)
            url = super.getResource(path);
        return url;
    }

    /**
     * Get a resource stream using the BeanShell classpath.
     *
     * @param path
     *            should be an absolute path
     * @return the resource as stream
     */
    @Override
    public InputStream getResourceAsStream(final String path) {
        InputStream in = null;
        if (this.baseLoader != null)
            // classloader wants no leading slash
            in = this.baseLoader.getResourceAsStream(path.substring(1));
        if (in == null)
            in = super.getResourceAsStream(path);
        return in;
    }

    /**
     * Gets the loader for class.
     *
     * @param name
     *            the name
     * @return the loader for class
     */
    ClassLoader getLoaderForClass(final String name) {
        return (ClassLoader) this.loaderMap.get(name);
    }

    // Classpath mutators
    /** {@inheritDoc} */
    @Override
    public void addClassPath(final URL path) throws IOException {
        if (this.baseLoader == null)
            this.setClassPath(new URL[] {path});
        else {
            // opportunity here for listener in classpath
            this.baseLoader.addURL(path);
            this.baseClassPath.add(path);
            this.classLoaderChanged();
        }
    }

    /**
     * Clear all classloading behavior and class caches and reset to
     * initial state.
     */
    @Override
    public void reset() {
        this.baseClassPath = new BshClassPath("baseClassPath");
        this.baseLoader = null;
        this.loaderMap = new HashMap();
        this.classLoaderChanged(); // calls clearCaches() for us.
    }

    /**
     * Set a new base classpath and create a new base classloader.
     * This means all types change.
     *
     * @param cp
     *            the new class path
     */
    @Override
    public void setClassPath(final URL[] cp) {
        this.baseClassPath.setPath(cp);
        this.initBaseLoader();
        this.loaderMap = new HashMap();
        this.classLoaderChanged();
    }

    /**
     * Overlay the entire path with a new class loader.
     * Set the base path to the user path + base path.
     *
     * No point in including the boot class path (can't reload thos).
     *
     * @throws ClassPathException
     *             the class path exception
     */
    @Override
    public void reloadAllClasses() throws ClassPathException {
        final BshClassPath bcp = new BshClassPath("temp");
        bcp.addComponent(this.baseClassPath);
        bcp.addComponent(BshClassPath.getUserClassPath());
        this.setClassPath(bcp.getPathComponents());
    }

    /**
     * init the baseLoader from the baseClassPath.
     */
    private void initBaseLoader() {
        this.baseLoader = new BshClassLoader(this, this.baseClassPath);
    }

    // class reloading
    /**
     * Reloading classes means creating a new classloader and using it
     * whenever we are asked for classes in the appropriate space.
     * For this we use a DiscreteFilesClassLoader
     *
     * @param classNames
     *            the class names
     * @throws ClassPathException
     *             the class path exception
     */
    @Override
    public void reloadClasses(final String[] classNames)
            throws ClassPathException {
        // validate that it is a class here?
        // init base class loader if there is none...
        if (this.baseLoader == null)
            this.initBaseLoader();
        final DiscreteFilesClassLoader.ClassSourceMap map = new DiscreteFilesClassLoader.ClassSourceMap();
        for (final String name : classNames) {
            // look in baseLoader class path
            ClassSource classSource = this.baseClassPath.getClassSource(name);
            // look in user class path
            if (classSource == null) {
                BshClassPath.getUserClassPath().insureInitialized();
                classSource = BshClassPath.getUserClassPath()
                        .getClassSource(name);
            }
            // No point in checking boot class path, can't reload those.
            // else we could have used fullClassPath above.
            if (classSource == null)
                throw new ClassPathException(
                        "Nothing known about class: " + name);
            // JarClassSource is not working... just need to implement it's
            // getCode() method or, if we decide to, allow the BshClassManager
            // to handle it... since it is a URLClassLoader and can handle JARs
            if (classSource instanceof JarClassSource)
                throw new ClassPathException("Cannot reload class: " + name
                        + " from source: " + classSource);
            map.put(name, classSource);
        }
        // Create classloader for the set of classes
        final ClassLoader cl = new DiscreteFilesClassLoader(this, map);
        // map those classes the loader in the overlay map
        final Iterator it = map.keySet().iterator();
        while (it.hasNext())
            this.loaderMap.put(it.next(), cl);
        this.classLoaderChanged();
    }

    /**
     * Reload all classes in the specified package: e.g. "com.sun.tools"
     *
     * The special package name "<unpackaged>" can be used to refer
     * to unpackaged classes.
     *
     * @param pack
     *            the pack
     * @throws ClassPathException
     *             the class path exception
     */
    @Override
    public void reloadPackage(final String pack) throws ClassPathException {
        Collection classes = this.baseClassPath.getClassesForPackage(pack);
        if (classes == null)
            classes = BshClassPath.getUserClassPath()
                    .getClassesForPackage(pack);
        // no point in checking boot class path, can't reload those
        if (classes == null)
            throw new ClassPathException(
                    "No classes found for package: " + pack);
        this.reloadClasses((String[]) classes.toArray(new String[0]));
    }
    // end reloading
    /**
     * Get the full blown classpath.
     */

    /**
     * Unimplemented
     * For this we'd have to store a map by location as well as name...
     *
     * public void reloadPathComponent(URL pc) throws ClassPathException {
     * throw new ClassPathException("Unimplemented!");
     * }
     *
     * @return the class path
     * @throws ClassPathException
     *             the class path exception
     */
    public BshClassPath getClassPath() throws ClassPathException {
        if (this.fullClassPath != null)
            return this.fullClassPath;
        this.fullClassPath = new BshClassPath("BeanShell Full Class Path");
        this.fullClassPath.addComponent(BshClassPath.getUserClassPath());
        try {
            this.fullClassPath.addComponent(BshClassPath.getBootClassPath());
        } catch (final ClassPathException e) {
            System.err.println("Warning: can't get boot class path");
        }
        this.fullClassPath.addComponent(this.baseClassPath);
        return this.fullClassPath;
    }

    /**
     * Support for "import *;"
     * Hide details in here as opposed to NameSpace.
     *
     * @throws UtilEvalError
     *             the util eval error
     */
    @Override
    public void doSuperImport() throws UtilEvalError {
        // Should we prevent it from happening twice?
        try {
            this.getClassPath().insureInitialized();
            // prime the lookup table
            this.getClassNameByUnqName("");
            // always true now
            // getClassPath().setNameCompletionIncludeUnqNames(true);
        } catch (final ClassPathException e) {
            throw new UtilEvalError("Error importing classpath " + e);
        }
        this.superImport = true;
    }

    /** {@inheritDoc} */
    @Override
    protected boolean hasSuperImport() {
        return this.superImport;
    }

    /**
     * Return the name or null if none is found,
     * Throw an ClassPathException containing detail if name is ambigous.
     *
     * @param name
     *            the name
     * @return the class name by unq name
     * @throws ClassPathException
     *             the class path exception
     */
    @Override
    public String getClassNameByUnqName(final String name)
            throws ClassPathException {
        return this.getClassPath().getClassNameByUnqName(name);
    }

    /** {@inheritDoc} */
    @Override
    public void addListener(final Listener l) {
        this.listeners.addElement(new WeakReference(l, this.refQueue));
        // clean up old listeners
        Reference deadref;
        while ((deadref = this.refQueue.poll()) != null) {
            final boolean ok = this.listeners.removeElement(deadref);
            if (ok) {
                // System.err.println("cleaned up weak ref: "+deadref);
            } else if (Interpreter.DEBUG)
                Interpreter.debug(
                        "tried to remove non-existent weak ref: " + deadref);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void removeListener(final Listener l) {
        throw new Error("unimplemented");
    }

    /**
     * Gets the base loader.
     *
     * @return the base loader
     */
    public ClassLoader getBaseLoader() {
        return this.baseLoader;
    }

    /**
     * Get the BeanShell classloader.
     * public ClassLoader getClassLoader() {
     * }
     *
     * @param name
     *            the name
     * @param code
     *            the code
     * @return the class
     *
     *         Impl Notes:
     *         We add the bytecode source and the "reload" the class, which
     *         causes the
     *         BshClassLoader to be initialized and create a
     *         DiscreteFilesClassLoader
     *         for the bytecode.
     */
    @Override
    public Class defineClass(final String name, final byte[] code) {
        // System.out.println("defineClass: "+name);
        this.baseClassPath.setClassSource(name, new GeneratedClassSource(code));
        try {
            this.reloadClasses(new String[] {name});
        } catch (final ClassPathException e) {
            throw new bsh.InterpreterError("defineClass: " + e);
        }
        return this.classForName(name);
    }

    /**
     * Clear global class cache and notify namespaces to clear their
     * class caches.
     *
     * The listener list is implemented with weak references so that we
     * will not keep every namespace in existence forever.
     */
    @Override
    protected void classLoaderChanged() {
        // clear the static caches in BshClassManager
        this.clearCaches();
        final Vector toRemove = new Vector(); // safely remove
        for (final Enumeration e = this.listeners.elements();
                e.hasMoreElements();) {
            final WeakReference wr = (WeakReference) e.nextElement();
            final Listener l = (Listener) wr.get();
            if (l == null) // garbage collected
                toRemove.add(wr);
            else
                l.classLoaderChanged();
        }
        for (final Enumeration e = toRemove.elements(); e.hasMoreElements();)
            this.listeners.removeElement(e.nextElement());
    }

    /** {@inheritDoc} */
    @Override
    public void dump(final PrintWriter i) {
        i.println("Bsh Class Manager Dump: ");
        i.println("----------------------- ");
        i.println("baseLoader = " + this.baseLoader);
        i.println("loaderMap= " + this.loaderMap);
        i.println("----------------------- ");
        i.println("baseClassPath = " + this.baseClassPath);
    }
}
