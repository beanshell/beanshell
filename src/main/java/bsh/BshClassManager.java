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
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import bsh.util.ReferenceCache;

import static bsh.Reflect.isPublic;
import static bsh.util.ReferenceCache.Type;
import static bsh.Reflect.isPrivate;
import static bsh.Reflect.isPackageAccessible;
import static bsh.Reflect.isPackageScope;
import static bsh.Capabilities.haveAccessibility;

/**
    BshClassManager manages all classloading in BeanShell.
    It also supports a dynamically loaded extension (bsh.classpath package)
    which allows classpath extension and class file reloading.

    Currently the extension relies on 1.2 for BshClassLoader and weak
    references.

    See http://www.beanshell.org/manual/classloading.html for details
    on the bsh classloader architecture.
    <p>

    Bsh has a multi-tiered class loading architecture.  No class loader is
    used unless/until the classpath is modified or a class is reloaded.
    <p>
*/
/*
    Implementation notes:

    Note: we may need some synchronization in here

    Note on version dependency:  This base class is JDK 1.1 compatible,
    however we are forced to use weak references in the full featured
    implementation (the optional bsh.classpath package) to accommodate all of
    the fleeting namespace listeners as they fall out of scope.  (NameSpaces
    must be informed if the class space changes so that they can un-cache
    names).
    <p>

    Perhaps a simpler idea would be to have entities that reference cached
    types always perform a light weight check with a counter / reference
    value and use that to detect changes in the namespace.  This puts the
    burden on the consumer to check at appropriate times, but could eliminate
    the need for the listener system in many places and the necessity of weak
    references in this package.
    <p>
*/
public class BshClassManager {
    /** Class member soft key and soft value reference cache */
    static final ReferenceCache<Class<?>, MemberCache> memberCache
        = new ReferenceCache<Class<?>, MemberCache>(Type.Soft, Type.Soft, 50) {
            @Override
            protected MemberCache create(Class<?> key) {
                return new MemberCache(key);
            }
    };

    /** Class member cached value instance **/
    static final class MemberCache {
        private final Map<String,List<Invocable>> cache
                            = new ConcurrentHashMap<>();
        private final Map<String,Invocable> fields
                            = new ConcurrentHashMap<>();

        /** Constructor iterates through interfaces and super classes
         * collect and cache field, constructor and method members.
         * Members are wrapped as invocable, each class has its own
         * member cache entry and inherited methods and constructors
         * are stored by reference only.
         * Ensures the package is accessible, for default package we
         * import all non private otherwise public only unless have
         * accessibility is true.
         * @param clazz for whom members are collected */
        public MemberCache(Class<?> clazz) {
            Class<?> type = clazz;
            while (type != null) {
                if (isPackageAccessible(type)
                    && ((isPackageScope(type) && !isPrivate(type))
                        || isPublic(type) || haveAccessibility())) {
                    for (Field f : type.getDeclaredFields())
                        if (isPublic(f) || haveAccessibility())
                            cacheMember(Invocable.get(f));
                    for (Method m : type.getDeclaredMethods())
                        if (isPublic(m) || haveAccessibility())
                            if (clazz == type) cacheMember(Invocable.get(m));
                            else cacheMember(memberCache.get(type)
                            .findMethod(m.getName(), m.getParameterTypes()));
                    for (Constructor<?> c: type.getDeclaredConstructors())
                        if (clazz == type) cacheMember(Invocable.get(c));
                        else cacheMember(memberCache.get(type)
                            .findMethod(c.getName(), c.getParameterTypes()));
                }
                processInterfaces(type.getInterfaces());
                type = type.getSuperclass();
                memberCache.init(type);
            }
        }

        /** Recursive processing of interfaces.
         * Methods are stored by reference.
         * @param interfaces for whom members are collected */
        private void processInterfaces(Class<?>[] interfaces) {
            for (Class<?> intr : interfaces) {
                if (isPackageAccessible(intr)) {
                    memberCache.init(intr);
                    for (Field f : intr.getDeclaredFields())
                            cacheMember(Invocable.get(f));
                    for (Method m: intr.getDeclaredMethods())
                        if (isPublic(m) || haveAccessibility())
                            cacheMember(memberCache.get(intr)
                            .findMethod(m.getName(), m.getParameterTypes()));
                }
                processInterfaces(intr.getInterfaces());
            }
        }

        /** Cache a field member.
         * @param member to cache
         * @return true if this is a new member */
        private boolean cacheMember(FieldAccess member) {
            if (!hasField(member.getName()))
                return null == fields.put(member.getName(), member);
            return false;
        }

        /** Cache constructors and methods.
         * Identifies properties and caches an additional cache entry
         * referenced by property name, as defined by bean specification.
         * @param member to cache
         * @return true if the cache changed */
        private boolean cacheMember(Invocable member) {
            if (null == member) return false;
            if (!member.isGetter() && !member.isSetter())
                return cacheMember(member.getName(), member);
            String name = member.getName();
            String propName = name.replaceFirst("[gs]et|is", "");
            if (propName.length() == 1 // double caps are skipped
                    || Character.isLowerCase(name.charAt(1))) {
                char[] ch = propName.toCharArray();
                ch[0] = Character.toLowerCase(ch[0]);
                propName = new String(ch);
            }
            return cacheMember(name, member)
                && cacheMember(propName, member);
        }

        /** Cache name associated with a list of members.
         * @param name of member
         * @param member invocable instance
         * @return true if the cache changed */
        private boolean cacheMember(String name, Invocable member) {
            if (!hasMember(name))
                return null == cache.put(name,
                        Collections.singletonList(member));
            else if (memberCount(name) == 1)
                cache.put(name, new ArrayList<>(members(name)));
            return members(name).add(member);
        }

        /** Find the most specific member for the given parameter types.
         * If there is only 1 member it will always be returned.
         * @param list of possible members
         * @param types of parameters
         * @return the most specific member or null */
        private Invocable findBest(List<Invocable> list, Class<?>[] types) {
            if (list.isEmpty())
                return null;
            if (list.size() == 1)
                return list.get(0);
            return Reflect.findMostSpecificInvocable(types, list);
        }

        /** Find invocable for the given name and arguments.
         * Arguments are converted to type parameters.
         * @param name of member
         * @param args parameter argument values
         * @return the most specific member or null */
        public Invocable findMethod(String name, Object... args) {
            return findMethod(name, Types.getTypes(args));
        }

        /** Find invocable for the given name and parameter types.
         * @param name of member
         * @param types of parameters
         * @return the most specific member or null */
        public Invocable findMethod(String name, Class<?>... types) {
            if (!hasMember(name))
                return null;
            return findBest(members(name), types);
        }

        /** Find static method for name. Used for static import.
         * @param name of static member
         * @return the most specific member or null */
        public Invocable findStaticMethod(String name) {
            if (!hasMember(name))
                return null;
            return members(name).stream()
                .filter(Invocable::isStatic).findFirst().get();
        }

        /** Find property read method or getter for property name.
         * No need for expensive method name conversion, member is
         * cached by property name.
         * @param name of property
         * @return the property read method or null */
        public Invocable findGetter(String propName) {
            if (hasMember(propName))
                for (Invocable property: members(propName))
                    if (property.isGetter())
                        return property;
            return null;
        }

        /** Find property write method or setter for property name.
         * No need for expensive method name conversion, member is
         * cached by property name.
         * @param name of property
         * @return the property write method or null */
        public Invocable findSetter(String propName) {
            if (hasMember(propName))
                for (Invocable property: members(propName))
                    if (property.isSetter())
                        return property;
            return null;
        }

        /** Find the index of the most appropriate member.
         * Used for class constructor switch.
         * @param name of member
         * @param types of parameters
         * @return index of the most specific member or -1 */
        public int findMemberIndex(String name, Class<?>[] types) {
           return Reflect.findMostSpecificInvocableIndex(types, members(name));
        }

        /** Retrieve list of member associated with name.
         * @param name of member
         * @return list of members or null */
        public List<Invocable> members(String name) {
            return cache.get(name);
        }

        /** Retrieve the number of members associated with name.
         * @param name of member
         * @return number of members */
        public int memberCount(String name) {
            return members(name).size();
        }

        /** Does the member exist.
         * @param name of member
         * @return true if members exists */
        public boolean hasMember(String name) {
            return cache.containsKey(name);
        }

        /** Does field exist.
         * @param name of field
         * @return true if field exists */
        public boolean hasField(String name) {
            return fields.containsKey(name);
        }

        /** Find field associated to name.
         * @param name of field
         * @return the field invocable or null */
        public Invocable findField(String name) {
            if (!hasField(name))
                return null;
            return fields.get(name);
        }
    }

    /**
        The interpreter which created the class manager
        This is used to load scripted classes from source files.
    */
    private Interpreter declaringInterpreter;

    public boolean getStrictJava() {
        return null != declaringInterpreter && declaringInterpreter.getStrictJava();
    }

    /**
        An external classloader supplied by the setClassLoader() command.
    */
    protected ClassLoader externalClassLoader;

    /**
        Global cache for things we know are classes.
        Note: these should probably be re-implemented with Soft references.
        (as opposed to strong or Weak)
    */
    protected final transient Map<String,Class<?>> absoluteClassCache = new ConcurrentHashMap<>();
    /**
        Global cache for things we know are *not* classes.
        Note: these should probably be re-implemented with Soft references.
        (as opposed to strong or Weak)
    */
    protected final transient Set<String> absoluteNonClasses = ConcurrentHashMap.newKeySet();
    private final transient Set<String> definingClasses =  ConcurrentHashMap.newKeySet();
    protected final transient Map<String,String> definingClassesBaseNames = new ConcurrentHashMap<>();

    /** @see #associateClass( Class ) */
    protected final transient Map<String, Class<?>> associatedClasses = new ConcurrentHashMap<>();

    /**
        Create a new instance of the class manager.
        Class manager instnaces are now associated with the interpreter.

        @see bsh.Interpreter.getClassManager()
        @see bsh.Interpreter.setClassLoader( ClassLoader )
    */
    public static BshClassManager createClassManager( Interpreter interpreter )
    {
        BshClassManager manager;

        // Do we have the optional package?
        if ( Capabilities.classExists("bsh.classpath.ClassManagerImpl") )
            try {
                // Try to load the module
                // don't refer to it directly here or we're dependent upon it
                Class<?> clazz = Capabilities.getExisting("bsh.classpath.ClassManagerImpl");
                manager = (BshClassManager) clazz.getConstructor().newInstance();
            } catch ( IllegalArgumentException | ReflectiveOperationException | SecurityException e) {
                throw new InterpreterError("Error loading classmanager", e);
            }
        else
            manager = new BshClassManager();

        manager.declaringInterpreter = interpreter;
        return manager;
    }

    public boolean classExists( String name ) {
        return classForName( name ) != null ;
    }

    /**
        Load the specified class by name, taking into account added classpath
        and reloaded classes, etc.
        Note: Again, this is just a trivial implementation.
        See bsh.classpath.ClassManagerImpl for the fully functional class
        management package.
        @return the class or null
    */
    public Class<?> classForName( String name ) {
        if ( isClassBeingDefined( name ) )
            throw new InterpreterError(
                "Attempting to load class in the process of being defined: "
                +name );

        Class<?> clas = null;
        try {
            clas = plainClassForName( name );
        } catch ( ClassNotFoundException e ) { /*ignore*/ }

        // try scripted class
        if ( clas == null && declaringInterpreter.getCompatibility() )
            clas = loadSourceClass( name );

        return clas;
    }

    // Move me to classpath/ClassManagerImpl???
    protected Class<?> loadSourceClass( final String name ) {
        final String fileName = '/' + name.replace('.', '/') + ".java";
        final URL url = getResource( fileName );
        if ( url == null )
            return null;
        try (FileReader reader
                = new FileReader((InputStream) url.getContent())) {
            Interpreter.debug("Loading class from source file: " + fileName);
            declaringInterpreter.eval( reader );
        } catch ( IOException | EvalError e ) {
            if (Interpreter.DEBUG.get())
                e.printStackTrace();
        }
        try {
            return plainClassForName( name );
        } catch ( final ClassNotFoundException e ) {
            Interpreter.debug("Class not found in source file: " + name);
            return null;
        }
    }

    /**
        Perform a plain Class.forName() or call the externally provided
        classloader.
        If a BshClassManager implementation is loaded the call will be
        delegated to it, to allow for additional hooks.
        <p/>

        This simply wraps that bottom level class lookup call and provides a
        central point for monitoring and handling certain Java version
        dependent bugs, etc.

        @see #classForName( String )
        @return the class
    */
    public Class<?> plainClassForName( String name )
            throws ClassNotFoundException {
        Class<?> c = null;

        if ( externalClassLoader != null )
            c = externalClassLoader.loadClass( name );
        else
            c = Class.forName( name );

        cacheClassInfo( name, c );

        return c;
    }

    /**
        Get a resource URL using the BeanShell classpath
        @param path should be an absolute path
    */
    public URL getResource( String path ) {
        URL url = null;
        if ( externalClassLoader != null )
            // classloader wants no leading slash
            url = externalClassLoader.getResource( path.substring(1) );
        if ( url == null )
            return Interpreter.class.getResource( path );

        return url;
    }
    /**
        Get a resource stream using the BeanShell classpath
        @param path should be an absolute path
    */
    public InputStream getResourceAsStream( String path ) {
        Object in = null;
        if ( externalClassLoader != null )
            // classloader wants no leading slash
            in = externalClassLoader.getResourceAsStream( path.substring(1) );
        if ( in == null )
            return Interpreter.class.getResourceAsStream( path );

        return (InputStream) in;
    }

    /**
        Cache info about whether name is a class or not.
        @param value
            if value is non-null, cache the class
            if value is null, set the flag that it is *not* a class to
            speed later resolution
    */
    public void cacheClassInfo( String name, Class<?> value ) {
        if ( value != null ) {
            absoluteClassCache.put(name, value);
            // eagerly start the member cache
            memberCache.init(value);
        }
        else
            absoluteNonClasses.add( name );
    }

    /**
     * Associate a persistent generated class implementation with this
     * interpreter.  An associated class will be used in lieu of generating
     * bytecode when a scripted class of the same name is encountered.
     * When such a class is defined in the script it will cause the associated
     * existing class implementation to be initialized (with the static
     * initializer field).  This is utilized by the persistent class generator
     * to allow a generated class to bootstrap an interpreter and rendesvous
     * with its implementation script.
     *
     * Class associations currently last for the life of the class manager.
     */
    public void associateClass( Class<?> clas ) {
        if ( Reflect.isGeneratedClass(clas) )
            associatedClasses.put( clas.getName(), clas );
    }

    public Class<?> getAssociatedClass( String name ) {
        return associatedClasses.get( name );
    }

    /**
        Clear the caches in BshClassManager
        @see public void #reset() for external usage
    */
    protected void clearCaches() {
        absoluteNonClasses.clear();
        absoluteClassCache.clear();
        memberCache.clear();
    }

    /**
        Set an external class loader.  BeanShell will use this at the same
        point it would otherwise use the plain Class.forName().
        i.e. if no explicit classpath management is done from the script
        (addClassPath(), setClassPath(), reloadClasses()) then BeanShell will
        only use the supplied classloader.  If additional classpath management
        is done then BeanShell will perform that in addition to the supplied
        external classloader.
        However BeanShell is not currently able to reload
        classes supplied through the external classloader.
    */
    public void setClassLoader( ClassLoader externalCL ) {
        externalClassLoader = externalCL;
        classLoaderChanged();
    }

    public void addClassPath( URL path ) throws IOException { }

    /**
        Clear all loaders and start over.  No class loading.
    */
    public void reset() {
        clearCaches();
    }

    /**
        Set a new base classpath and create a new base classloader.
        This means all types change.
    */
    public void setClassPath( URL [] cp ) throws UtilEvalError {
        throw cmUnavailable();
    }

    /**
        Overlay the entire path with a new class loader.
        Set the base path to the user path + base path.

        No point in including the boot class path (can't reload thos).
    */
    public void reloadAllClasses() throws UtilEvalError {
        throw cmUnavailable();
    }

    /**
        Reloading classes means creating a new classloader and using it
        whenever we are asked for classes in the appropriate space.
        For this we use a DiscreteFilesClassLoader
    */
    public void reloadClasses( String [] classNames ) throws UtilEvalError {
        throw cmUnavailable();
    }

    /**
        Reload all classes in the specified package: e.g. "com.sun.tools"

        The special package name "<unpackaged>" can be used to refer
        to unpackaged classes.
    */
    public void reloadPackage( String pack ) throws UtilEvalError {
        throw cmUnavailable();
    }

    /**
        Support for "import *;"
        Hide details in here as opposed to NameSpace.
    */
    protected void doSuperImport() throws UtilEvalError {
        throw cmUnavailable();
    }

    /**
        A "super import" ("import *") operation has been performed.
    */
    protected boolean hasSuperImport() {
        return false;
    }

    /**
        Return the name or null if none is found,
        Throw an ClassPathException containing detail if name is ambigous.
    */
    protected String getClassNameByUnqName( String name )
            throws UtilEvalError {
        throw cmUnavailable();
    }

    public void addListener( Listener l ) { }

    public void removeListener( Listener l ) { }

    public void dump( PrintWriter pw ) {
        pw.println("BshClassManager: no class manager.");
    }

    /**
        Flag the class name as being in the process of being defined.
        The class manager will not attempt to load it.
    */
    /*
        Note: this implementation is temporary. We currently keep a flat
        namespace of the base name of classes.  i.e. BeanShell cannot be in the
        process of defining two classes in different packages with the same
        base name.  To remove this limitation requires that we work through
        namespace imports in an analogous (or using the same path) as regular
        class import resolution.  This workaround should handle most cases
        so we'll try it for now.
    */
    protected void definingClass( String className ) {
        String baseName = Name.suffix(className,1);
        int i = baseName.indexOf("$");
        if ( i != -1 )
            baseName = baseName.substring(i+1);
        String cur = definingClassesBaseNames.get( baseName );
        if ( cur != null )
            throw new InterpreterError("Defining class problem: "+className
                +": BeanShell cannot yet simultaneously define two or more "
                +"dependent classes of the same name.  Attempt to define: "
                + className +" while defining: "+cur
            );
        definingClasses.add( className );
        definingClassesBaseNames.put( baseName, className );
    }

    protected boolean isClassBeingDefined( String className ) {
        return definingClasses.contains( className );
    }

    /**
        This method is a temporary workaround used with definingClass.
        It is to be removed at some point.
    */
    protected String getClassBeingDefined( String className ) {
        String baseName = Name.suffix(className,1);
        return definingClassesBaseNames.get( baseName );
    }

    /**
        Indicate that the specified class name has been defined and may be
        loaded normally.
    */
    protected void doneDefiningClass( String className ) {
        String baseName = Name.suffix(className,1);
        definingClasses.remove( className );
        definingClassesBaseNames.remove( baseName );
    }

    /*
        The real implementation in the classpath.ClassManagerImpl handles
        reloading of the generated classes.
    */
    public Class<?> defineClass( String name, byte [] code ) {
        throw new InterpreterError("Can't create class ("+name
            +") without class manager package.");
    }

    protected void classLoaderChanged() { }

    protected static UtilEvalError cmUnavailable() {
        return new Capabilities.Unavailable(
            "ClassLoading features unavailable.");
    }

    public static interface Listener {
        void classLoaderChanged();
    }

}
