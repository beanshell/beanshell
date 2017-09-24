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
package bsh;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.Hashtable;

/**
 * BshClassManager manages all classloading in BeanShell.
 * It also supports a dynamically loaded extension (bsh.classpath package)
 * which allows classpath extension and class file reloading.
 *
 * Currently the extension relies on 1.2 for BshClassLoader and weak
 * references.
 *
 * See http://www.beanshell.org/manual/classloading.html for details
 * on the bsh classloader architecture.
 * <p>
 *
 * Bsh has a multi-tiered class loading architecture. No class loader is
 * used unless/until the classpath is modified or a class is reloaded.
 * <p>
 *
 * Implementation notes:
 * Note: we may need some synchronization in here
 * Note on version dependency: This base class is JDK 1.1 compatible,
 * however we are forced to use weak references in the full featured
 * implementation (the optional bsh.classpath package) to accommodate all of
 * the fleeting namespace listeners as they fall out of scope. (NameSpaces
 * must be informed if the class space changes so that they can un-cache
 * names).
 * <p>
 * Perhaps a simpler idea would be to have entities that reference cached
 * types always perform a light weight check with a counter / reference
 * value and use that to detect changes in the namespace. This puts the
 * burden on the consumer to check at appropriate times, but could eliminate
 * the need for the listener system in many places and the necessity of weak
 * references in this package.
 * <p>
 */
public class BshClassManager {

    /** Identifier for no value item. Use a hashtable as a Set. */
    private static Object NOVALUE = new Object();
    /**
     * The interpreter which created the class manager
     * This is used to load scripted classes from source files.
     */
    private Interpreter declaringInterpreter;
    /**
     * An external classloader supplied by the setClassLoader() command.
     */
    protected ClassLoader externalClassLoader;
    /**
     * Global cache for things we know are classes.
     * Note: these should probably be re-implemented with Soft references.
     * (as opposed to strong or Weak)
     */
    protected transient Hashtable absoluteClassCache = new Hashtable();
    /**
     * Global cache for things we know are *not* classes.
     * Note: these should probably be re-implemented with Soft references.
     * (as opposed to strong or Weak)
     */
    protected transient Hashtable absoluteNonClasses = new Hashtable();
    /**
     * Caches for resolved object and static methods.
     * We keep these maps separate to support fast lookup in the general case
     * where the method may be either.
     */
    protected transient Hashtable resolvedObjectMethods = new Hashtable();
    /** The resolved static methods. */
    protected transient Hashtable resolvedStaticMethods = new Hashtable();
    /** The defining classes. */
    protected transient Hashtable definingClasses = new Hashtable();
    /** The defining classes base names. */
    protected transient Hashtable definingClassesBaseNames = new Hashtable();
    /**
     * The associated classes.
     *
     * @see #associateClass(Class)
     */
    protected transient Hashtable associatedClasses = new Hashtable();

    /**
     * Create a new instance of the class manager.
     * Class manager instnaces are now associated with the interpreter.
     *
     * @param interpreter
     *            the interpreter
     * @return the bsh class manager
     * @see bsh.Interpreter.getClassManager()
     * @see bsh.Interpreter.setClassLoader(ClassLoader)
     */
    public static BshClassManager createClassManager(Interpreter interpreter) {
        BshClassManager manager;
        // Do we have the necessary jdk1.2 packages and optional package?
        if (Capabilities.classExists("java.lang.ref.WeakReference")
                && Capabilities.classExists("java.util.HashMap")
                && Capabilities.classExists("bsh.classpath.ClassManagerImpl"))
            try {
                // Try to load the module
                // don't refer to it directly here or we're dependent upon it
                Class clas = Class.forName("bsh.classpath.ClassManagerImpl");
                manager = (BshClassManager) clas.newInstance();
            } catch (final Exception e) {
                throw new InterpreterError("Error loading classmanager: "+e);
            }
        else
            manager = new BshClassManager();
        if (interpreter == null)
            interpreter = new Interpreter();
        manager.declaringInterpreter = interpreter;
        return manager;
    }

    /**
     * Class exists.
     *
     * @param name
     *            the name
     * @return true, if successful
     */
    public boolean classExists(String name) {
        return this.classForName(name) != null;
    }

    /**
     * Load the specified class by name, taking into account added classpath
     * and reloaded classes, etc.
     * Note: Again, this is just a trivial implementation.
     * See bsh.classpath.ClassManagerImpl for the fully functional class
     * management package.
     *
     * @param name
     *            the name
     * @return the class or null
     */
    public Class classForName(String name) {
        if (this.isClassBeingDefined(name))
            throw new InterpreterError(
                    "Attempting to load class in the process of being defined: "
                            + name);
        Class clas = null;
        try {
            clas = this.plainClassForName(name);
        } catch (ClassNotFoundException e) { /* ignore */ }
        // try scripted class
        if (clas == null)
            clas = this.loadSourceClass(name);
        return clas;
    }

    /**
     * Load source class.
     *
     * @param name
     *            the name
     * @return the class
     */
    // Move me to classpath/ClassManagerImpl???
    protected Class loadSourceClass(String name) {
        final String fileName = "/" + name.replace('.', '/') + ".java";
        final InputStream in = this.getResourceAsStream(fileName);
        if (in == null)
            return null;
        try {
            System.out.println("Loading class from source file: " + fileName);
            this.declaringInterpreter.eval(new InputStreamReader(in));
        } catch (final EvalError e) {
            // ignore
            System.err.println(e);
        }
        try {
            return this.plainClassForName(name);
        } catch (final ClassNotFoundException e) {
            System.err.println("Class not found in source file: " + name);
            return null;
        }
    }

    /**
     * Perform a plain Class.forName() or call the externally provided
     * classloader.
     * If a BshClassManager implementation is loaded the call will be
     * delegated to it, to allow for additional hooks.
     * <p/>
     *
     * This simply wraps that bottom level class lookup call and provides a
     * central point for monitoring and handling certain Java version
     * dependent bugs, etc.
     *
     * @param name
     *            the name
     * @return the class
     * @throws ClassNotFoundException
     *             the class not found exception
     * @see #classForName(String)
     */
    public Class plainClassForName(String name)
            throws ClassNotFoundException {
        Class c = null;
        try {
            if (this.externalClassLoader != null)
                c = this.externalClassLoader.loadClass(name);
            else
                c = Class.forName(name);
            this.cacheClassInfo(name, c);
            /*
             * Original note: Jdk under Win is throwing these to
             * warn about lower case / upper case possible mismatch.
             * e.g. bsh.console bsh.Console
             * Update: Prior to 1.3 we were squeltching NoClassDefFoundErrors
             * which was very annoying. I cannot reproduce the original problem
             * and this was never a valid solution. If there are legacy VMs that
             * have problems we can include a more specific test for them here.
             */
        } catch (final NoClassDefFoundError e) {
            throw noClassDefFound(name, e);
        }
        return c;
    }

    /**
     * Get a resource URL using the BeanShell classpath.
     *
     * @param path
     *            should be an absolute path
     * @return the resource
     */
    public URL getResource(String path) {
        URL url = null;
        if (this.externalClassLoader != null)
            // classloader wants no leading slash
            url = this.externalClassLoader.getResource(path.substring(1));
        if (url == null)
            url = Interpreter.class.getResource(path);
        return url;
    }

    /**
     * Get a resource stream using the BeanShell classpath.
     *
     * @param path
     *            should be an absolute path
     * @return the resource as stream
     */
    public InputStream getResourceAsStream(String path) {
        InputStream in = null;
        if (this.externalClassLoader != null)
            // classloader wants no leading slash
            in = this.externalClassLoader
                    .getResourceAsStream(path.substring(1));
        if (in == null)
            in = Interpreter.class.getResourceAsStream(path);
        return in;
    }

    /**
     * Cache info about whether name is a class or not.
     *
     * @param name
     *            the name
     * @param value
     *            if value is non-null, cache the class
     *            if value is null, set the flag that it is *not* a class to
     *            speed later resolution
     */
    public void cacheClassInfo(String name, Class value) {
        if (value != null)
            this.absoluteClassCache.put(name, value);
        else
            this.absoluteNonClasses.put(name, NOVALUE);
    }

    /**
     * Associate a persistent generated class implementation with this
     * interpreter. An associated class will be used in lieu of generating
     * bytecode when a scripted class of the same name is encountered.
     * When such a class is defined in the script it will cause the associated
     * existing class implementation to be initialized (with the static
     * initializer field). This is utilized by the persistent class generator
     * to allow a generated class to bootstrap an interpreter and rendesvous
     * with its implementation script.
     *
     * Class associations currently last for the life of the class manager.
     *
     * @param clas
     *            the clas
     */
    public void associateClass(Class clas) {
        // TODO should check to make sure it's a generated class here
        // just need to add a method to classgenerator API to test it
        this.associatedClasses.put(clas.getName(), clas);
    }

    /**
     * Gets the associated class.
     *
     * @param name
     *            the name
     * @return the associated class
     */
    public Class getAssociatedClass(String name) {
        return (Class) this.associatedClasses.get(name);
    }

    /**
     * Cache a resolved (possibly overloaded) method based on the
     * argument types used to invoke it, subject to classloader change.
     * Static and Object methods are cached separately to support fast lookup
     * in the general case where either will do.
     *
     * @param clas
     *            the clas
     * @param types
     *            the types
     * @param method
     *            the method
     */
    public void cacheResolvedMethod(Class clas, Class[] types, Method method) {
        if (Interpreter.DEBUG)
            Interpreter.debug(
                    "cacheResolvedMethod putting: " + clas + " " + method);
        final SignatureKey sk = new SignatureKey(clas, method.getName(), types);
        if (Modifier.isStatic(method.getModifiers()))
            this.resolvedStaticMethods.put(sk, method);
        else
            this.resolvedObjectMethods.put(sk, method);
    }

    /**
     * Return a previously cached resolved method.
     *
     * @param clas
     *            the clas
     * @param methodName
     *            the method name
     * @param types
     *            the types
     * @param onlyStatic
     *            specifies that only a static method may be returned.
     * @return the Method or null
     */
    protected Method getResolvedMethod(Class clas, String methodName,
            Class[] types, boolean onlyStatic) {
        final SignatureKey sk = new SignatureKey(clas, methodName, types);
        // Try static and then object, if allowed
        // Note that the Java compiler should not allow both.
        Method method = (Method) this.resolvedStaticMethods.get(sk);
        if (method == null && !onlyStatic)
            method = (Method) this.resolvedObjectMethods.get(sk);
        if (Interpreter.DEBUG)
            if (method == null)
                Interpreter.debug("getResolvedMethod cache MISS: " + clas
                        + " - " + methodName);
            else
                Interpreter.debug("getResolvedMethod cache HIT: " + clas
                        + " - " + method);
        return method;
    }

    /**
     * Clear the caches in BshClassManager.
     *
     * @see public void #reset() for external usage
     */
    protected void clearCaches() {
        this.absoluteNonClasses = new Hashtable();
        this.absoluteClassCache = new Hashtable();
        this.resolvedObjectMethods = new Hashtable();
        this.resolvedStaticMethods = new Hashtable();
    }

    /**
     * Set an external class loader. BeanShell will use this at the same
     * point it would otherwise use the plain Class.forName().
     * i.e. if no explicit classpath management is done from the script
     * (addClassPath(), setClassPath(), reloadClasses()) then BeanShell will
     * only use the supplied classloader. If additional classpath management
     * is done then BeanShell will perform that in addition to the supplied
     * external classloader.
     * However BeanShell is not currently able to reload
     * classes supplied through the external classloader.
     *
     * @param externalCL
     *            the new class loader
     */
    public void setClassLoader(ClassLoader externalCL) {
        this.externalClassLoader = externalCL;
        this.classLoaderChanged();
    }

    /**
     * Adds the class path.
     *
     * @param path
     *            the path
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    public void addClassPath(URL path) throws IOException {}

    /**
     * Clear all loaders and start over. No class loading.
     */
    public void reset() {
        this.clearCaches();
    }

    /**
     * Set a new base classpath and create a new base classloader.
     * This means all types change.
     *
     * @param cp
     *            the new class path
     * @throws UtilEvalError
     *             the util eval error
     */
    public void setClassPath(URL[] cp) throws UtilEvalError {
        throw cmUnavailable();
    }

    /**
     * Overlay the entire path with a new class loader.
     * Set the base path to the user path + base path.
     *
     * No point in including the boot class path (can't reload thos).
     *
     * @throws UtilEvalError
     *             the util eval error
     */
    public void reloadAllClasses() throws UtilEvalError {
        throw cmUnavailable();
    }

    /**
     * Reloading classes means creating a new classloader and using it
     * whenever we are asked for classes in the appropriate space.
     * For this we use a DiscreteFilesClassLoader
     *
     * @param classNames
     *            the class names
     * @throws UtilEvalError
     *             the util eval error
     */
    public void reloadClasses(String[] classNames) throws UtilEvalError {
        throw cmUnavailable();
    }

    /**
     * Reload all classes in the specified package: e.g. "com.sun.tools"
     *
     * The special package name "<unpackaged>" can be used to refer
     * to unpackaged classes.
     *
     * @param pack
     *            the pack
     * @throws UtilEvalError
     *             the util eval error
     */
    public void reloadPackage(String pack) throws UtilEvalError {
        throw cmUnavailable();
    }

    /**
     * This has been removed from the interface to shield the core from the
     * rest of the classpath package. If you need the classpath you will have
     * to cast the classmanager to its impl.
     *
     * public BshClassPath getClassPath() throws ClassPathException;
     *
     * @throws UtilEvalError
     *             the util eval error
     **
     * Support for "import *;"
     * Hide details in here as opposed to NameSpace.
     */
    protected void doSuperImport() throws UtilEvalError {
        throw cmUnavailable();
    }

    /**
     * A "super import" ("import *") operation has been performed.
     *
     * @return true, if successful
     */
    protected boolean hasSuperImport() {
        return false;
    }

    /**
     * Return the name or null if none is found,
     * Throw an ClassPathException containing detail if name is ambigous.
     *
     * @param name
     *            the name
     * @return the class name by unq name
     * @throws UtilEvalError
     *             the util eval error
     */
    protected String getClassNameByUnqName(String name)
            throws UtilEvalError {
        throw cmUnavailable();
    }

    /**
     * Adds the listener.
     *
     * @param l
     *            the l
     */
    public void addListener(Listener l) {}

    /**
     * Removes the listener.
     *
     * @param l
     *            the l
     */
    public void removeListener(Listener l) {}

    /**
     * Dump.
     *
     * @param pw
     *            the pw
     */
    public void dump(PrintWriter pw) {
        pw.println("BshClassManager: no class manager.");
    }

    /**
     * Flag the class name as being in the process of being defined.
     * The class manager will not attempt to load it.
     *
     * @param className
     *            the class name
     *
     * Note: this implementation is temporary. We currently keep a flat
     * namespace of the base name of classes. i.e. BeanShell cannot be in the
     * process of defining two classes in different packages with the same
     * base name. To remove this limitation requires that we work through
     * namespace imports in an analogous (or using the same path) as regular
     * class import resolution. This workaround should handle most cases
     * so we'll try it for now.
     */
    protected void definingClass(String className) {
        String baseName = Name.suffix(className, 1);
        final int i = baseName.indexOf("$");
        if (i != -1)
            baseName = baseName.substring(i + 1);
        final String cur = (String) this.definingClassesBaseNames.get(baseName);
        if (cur != null)
            throw new InterpreterError("Defining class problem: " + className
                    + ": BeanShell cannot yet simultaneously define two or more "
                    + "dependent classes of the same name.  Attempt to define: "
                    + className + " while defining: " + cur);
        this.definingClasses.put(className, NOVALUE);
        this.definingClassesBaseNames.put(baseName, className);
    }

    /**
     * Checks if is class being defined.
     *
     * @param className
     *            the class name
     * @return true, if is class being defined
     */
    protected boolean isClassBeingDefined(String className) {
        return this.definingClasses.get(className) != null;
    }

    /**
     * This method is a temporary workaround used with definingClass.
     * It is to be removed at some point.
     *
     * @param className
     *            the class name
     * @return the class being defined
     */
    protected String getClassBeingDefined(String className) {
        final String baseName = Name.suffix(className, 1);
        return (String) this.definingClassesBaseNames.get(baseName);
    }

    /**
     * Indicate that the specified class name has been defined and may be
     * loaded normally.
     *
     * @param className
     *            the class name
     */
    protected void doneDefiningClass(String className) {
        final String baseName = Name.suffix(className, 1);
        this.definingClasses.remove(className);
        this.definingClassesBaseNames.remove(baseName);
    }

    /**
     * Define class.
     *
     * @param name
     *            the name
     * @param code
     *            the code
     * @return the class
     *
     * The real implementation in the classpath.ClassManagerImpl handles
     * reloading of the generated classes.
     */
    public Class defineClass(String name, byte[] code) {
        throw new InterpreterError("Can't create class (" + name
                + ") without class manager package.");
        /*
         * Old implementation injected classes into the parent classloader.
         * This was incorrect behavior for several reasons. The biggest problem
         * is that classes could therefore only be defined once across all
         * executions of the script...
         * ClassLoader cl = this.getClass().getClassLoader();
         * Class clas;
         * try {
         * clas = (Class)Reflect.invokeObjectMethod(
         * cl, "defineClass",
         * new Object [] {
         * name, code,
         * new Primitive((int)0)/offset/,
         * new Primitive(code.length)/len/
         * },
         * (Interpreter)null, (CallStack)null, (SimpleNode)null
         * );
         * } catch (Exception e) {
         * e.printStackTrace();
         * throw new InterpreterError("Unable to define class: "+ e);
         * }
         * absoluteNonClasses.remove(name); // may have been axed previously
         * return clas;
         */
    }

    /**
     * Class loader changed.
     */
    protected void classLoaderChanged() {}

    /**
     * Annotate the NoClassDefFoundError with some info about the class
     * we were trying to load.
     *
     * @param className
     *            the class name
     * @param e
     *            the e
     * @return the error
     */
    protected static Error noClassDefFound(String className,
            final Error e) {
        return new NoClassDefFoundError("A class required by class: "
                + className + " could not be loaded:\n" + e.toString());
    }

    /**
     * Cm unavailable.
     *
     * @return the util eval error
     */
    protected static UtilEvalError cmUnavailable() {
        return new Capabilities.Unavailable(
                "ClassLoading features unavailable.");
    }

    /**
     * The Interface Listener.
     */
    public static interface Listener {

        /**
         * Class loader changed.
         */
        public void classLoaderChanged();
    }

    /**
     * SignatureKey serves as a hash of a method signature on a class
     * for fast lookup of overloaded and general resolved Java methods.
     * <p>
     *
     * Note: is using SignatureKey in this way dangerous? In the pathological
     * case a user could eat up memory caching every possible combination of
     * argument types to an untyped method. Maybe we could be smarter about
     * it by ignoring the types of untyped parameter positions? The method
     * resolver could return a set of "hints" for the signature key caching?
     * There is also the overhead of creating one of these for every method
     * dispatched. What is the alternative?
     */
    static class SignatureKey {

        /** The clas. */
        Class clas;
        /** The types. */
        Class[] types;
        /** The method name. */
        String methodName;
        /** The hash code. */
        int hashCode = 0;

        /**
         * Instantiates a new signature key.
         *
         * @param clas
         *            the clas
         * @param methodName
         *            the method name
         * @param types
         *            the types
         */
        SignatureKey(Class clas, String methodName, Class[] types) {
            this.clas = clas;
            this.methodName = methodName;
            this.types = types;
        }

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            if (this.hashCode == 0) {
                this.hashCode = this.clas.hashCode()
                        * this.methodName.hashCode();
                if (this.types == null) // no args method
                    return this.hashCode;
                for (int i = 0; i < this.types.length; i++) {
                    final int hc = this.types[i] == null
                            ? 21
                            : this.types[i].hashCode();
                    this.hashCode = this.hashCode * (i + 1) + hc;
                }
            }
            return this.hashCode;
        }

        /** {@inheritDoc} */
        @Override
        public boolean equals(Object o) {
            final SignatureKey target = (SignatureKey) o;
            if (this.types == null)
                return target.types == null;
            if (this.clas != target.clas)
                return false;
            if (!this.methodName.equals(target.methodName))
                return false;
            if (this.types.length != target.types.length)
                return false;
            for (int i = 0; i < this.types.length; i++)
                if (this.types[i] == null) {
                    if (!(target.types[i] == null))
                        return false;
                } else if (!this.types[i].equals(target.types[i]))
                    return false;
            return true;
        }
    }
}
