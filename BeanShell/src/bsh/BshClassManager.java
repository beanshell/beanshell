/*****************************************************************************
 *                                                                           *
 *  This file is part of the BeanShell Java Scripting distribution.          *
 *  Documentation and updates may be found at http://www.beanshell.org/      *
 *                                                                           *
 *  Sun Public License Notice:                                               *
 *                                                                           *
 *  The contents of this file are subject to the Sun Public License Version  *
 *  1.0 (the "License"); you may not use this file except in compliance with *
 *  the License. A copy of the License is available at http://www.sun.com    * 
 *                                                                           *
 *  The Original Code is BeanShell. The Initial Developer of the Original    *
 *  Code is Pat Niemeyer. Portions created by Pat Niemeyer are Copyright     *
 *  (C) 2000.  All Rights Reserved.                                          *
 *                                                                           *
 *  GNU Public License Notice:                                               *
 *                                                                           *
 *  Alternatively, the contents of this file may be used under the terms of  *
 *  the GNU Lesser General Public License (the "LGPL"), in which case the    *
 *  provisions of LGPL are applicable instead of those above. If you wish to *
 *  allow use of your version of this file only under the  terms of the LGPL *
 *  and not to allow others to use your version of this file under the SPL,  *
 *  indicate your decision by deleting the provisions above and replace      *
 *  them with the notice and other provisions required by the LGPL.  If you  *
 *  do not delete the provisions above, a recipient may use your version of  *
 *  this file under either the SPL or the LGPL.                              *
 *                                                                           *
 *  Patrick Niemeyer (pat@pat.net)                                           *
 *  Author of Learning Java, O'Reilly & Associates                           *
 *  http://www.pat.net/~pat/                                                 *
 *                                                                           *
 *****************************************************************************/

package bsh;

import java.net.*;
import java.util.*;
import java.io.IOException;
import java.io.*;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

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

	Note on jdk1.2 dependency:
	<p>

	We are forced to use weak references in the full featured implementation
	(bsh.classpath package) to accomodate all of the fleeting namespace 
	listeners as they fall out of scope.  (NameSpaces must be informed if the 
	class space changes so that they can un-cache names).  
	<p>

	Perhaps a simpler idea would be to have entities that reference cached
	types always perform a light weight check with a counter / reference
	value and use that to detect changes in the namespace.  This puts the 
	burden on the consumer to check at appropriate times, but could eliminate
	the need for the listener system in many places and the necessity of weak 
	references in this package.
	<p>
*/
public class BshClassManager
{
	/** Identifier for no value item.  Use a hashtable as a Set. */
	private static Object NOVALUE = new Object(); 
	
	/**
		An external classloader supplied by the setClassLoader() command.
	*/
	private ClassLoader externalClassLoader;

	/**
		Global cache for things we know are classes.
		Note: these should probably be re-implemented with Soft references.
		(as opposed to strong or Weak)
	*/
    protected transient Hashtable absoluteClassCache = new Hashtable();
	/**
		Global cache for things we know are *not* classes.
		Note: these should probably be re-implemented with Soft references.
		(as opposed to strong or Weak)
	*/
    protected transient Hashtable absoluteNonClasses = new Hashtable();

	/**
		Caches for resolved object and static methods.
		We keep these maps separate to support fast lookup in the general case
		where the method may be either.
	*/
	protected transient Hashtable resolvedObjectMethods = new Hashtable();
	protected transient Hashtable resolvedStaticMethods = new Hashtable();

	/**
		Create a new instance of the class manager.  
		Class manager instnaces are now associated with the interpreter.

		@see bsh.Interpreter.getClassManager()
		@see bsh.Interpreter.setClassLoader( ClassLoader )
	*/
	public static BshClassManager createClassManager() 
	{
//System.out.println("creating class manager");

		BshClassManager manager;

		// Do we have the necessary jdk1.2 packages?
		if ( Capabilities.classExists("java.lang.ref.WeakReference") 
			&& Capabilities.classExists("java.util.HashMap") 
			&& Capabilities.classExists("bsh.classpath.ClassManagerImpl") 
		) 
			try {
				// Try to load the module
				// don't refer to it directly here or we're dependent upon it
				Class clas = Class.forName( "bsh.classpath.ClassManagerImpl" );
				manager = (BshClassManager)clas.newInstance();
			} catch ( Exception e ) {
				throw new InterpreterError("Error loading classmanager: "+e);
			}
		else 
			manager = new BshClassManager();

		return manager;
	}

	public boolean classExists( String name ) {
		return ( classForName( name ) != null );
	}

	/**
		Load the specified class by name, taking into account added classpath
		and reloaded classes, etc.
		@return the class or null
	*/
	public Class classForName( String name ) {
		try {
			return plainClassForName( name );
		} catch ( ClassNotFoundException e ) {
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
	public Class plainClassForName( String name ) 
		throws ClassNotFoundException
	{
		Class c = null;

		try {
			if ( externalClassLoader != null )
				c = externalClassLoader.loadClass( name );
			else
				c = Class.forName( name );

			cacheClassInfo( name, c );

		/*
			Original note: Jdk under Win is throwing these to
			warn about lower case / upper case possible mismatch.
			e.g. bsh.console bsh.Console
	
			Update: Prior to 1.3 we were squeltching NoClassDefFoundErrors 
			which was very annoying.  I cannot reproduce the original problem 
			and this was never a valid solution.  If there are legacy VMs that
			have problems we can include a more specific test for them here.
		*/
		} catch ( NoClassDefFoundError e ) {
			throw noClassDefFound( name, e );
		}

		return c;
	}

	/**
		Cache info about whether name is a class or not.
		@param value 
			if value is non-null, cache the class
			if value is null, set the flag that it is *not* a class to
			speed later resolution
	*/
	public void cacheClassInfo( String name, Class value ) {
		if ( value != null )
			absoluteClassCache.put( name, value );
		else
			absoluteNonClasses.put( name, NOVALUE );
	}

	/**
		Cache a resolved (possibly overloaded) method based on the 
		argument types used to invoke it, subject to classloader change.
		Static and Object methods are cached separately to support fast lookup
		in the general case where either will do.
	*/
	public void cacheResolvedMethod( 
		Class clas, Object [] args, Method method ) 
	{
		String methodName = method.getName();

		if ( Interpreter.DEBUG )
			Interpreter.debug(
				"cacheResolvedMethod putting: " + clas +" - "+methodName );
		
		SignatureKey sk = new SignatureKey( clas, methodName, args );
		if ( Modifier.isStatic( method.getModifiers() ) )
			resolvedStaticMethods.put( sk, method );
		else
			resolvedObjectMethods.put( sk, method );
	}

	/**
		Return a previously cached resolved method.
		@param onlyStatic specifies that only a static method may be returned.
		@return the Method or null
	*/
	public Method getResolvedMethod( 
		Class clas, String methodName, Object [] args, boolean onlyStatic  ) 
	{
		SignatureKey sk = new SignatureKey( clas, methodName, args );

		// Try static and then object, if allowed
		// Note that the Java compiler should not allow both.
		Method method = (Method)resolvedStaticMethods.get( sk );
		if ( method == null && !onlyStatic)
			method = (Method)resolvedObjectMethods.get( sk );

		if ( Interpreter.DEBUG )
		{
			if ( method == null )
				Interpreter.debug(
					"getResolvedMethod cache MISS: " + clas +" - "+methodName );
			else
				Interpreter.debug(
					"getResolvedMethod cache HIT: " + clas +" - " +method );
		}
		return method;
	}

	/**
		Clear the caches in BshClassManager
		@see public void #reset() for external usage
	*/
	protected void clearCaches() 
	{
    	absoluteNonClasses = new Hashtable();
    	absoluteClassCache = new Hashtable();
    	resolvedObjectMethods = new Hashtable();
    	resolvedStaticMethods = new Hashtable();
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
	public void setClassLoader( ClassLoader externalCL ) 
	{
		externalClassLoader = externalCL;
		classLoaderChanged();
	}

	public void addClassPath( URL path )
		throws IOException {
	}

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
	public void setClassPath( URL [] cp ) 
		throws UtilEvalError
	{
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
	public void reloadClasses( String [] classNames )
		throws UtilEvalError 
	{
		throw cmUnavailable();
	}

	/**
		Reload all classes in the specified package: e.g. "com.sun.tools"

		The special package name "<unpackaged>" can be used to refer 
		to unpackaged classes.
	*/
	public void reloadPackage( String pack ) 
		throws UtilEvalError 
	{
		throw cmUnavailable();
	}

	/**
		This has been removed from the interface to shield the core from the
		rest of the classpath package. If you need the classpath you will have
		to cast the classmanager to its impl.

		public BshClassPath getClassPath() throws ClassPathException;
	*/

	/**
		Support for "import *;"
		Hide details in here as opposed to NameSpace.
	*/
	protected void doSuperImport() 
		throws UtilEvalError 
	{
		throw cmUnavailable();
	}

	/**
		A "super import" ("import *") operation has been performed.
	*/
	protected boolean hasSuperImport() 
	{
		return false;
	}

	/**
		Return the name or null if none is found,
		Throw an ClassPathException containing detail if name is ambigous.
	*/
	protected String getClassNameByUnqName( String name ) 
		throws UtilEvalError 
	{
		throw cmUnavailable();
	}

	public void addListener( Listener l ) { }

	public void removeListener( Listener l ) { }

	public void dump( PrintWriter pw ) { 
		pw.println("BshClassManager: no class manager."); 
	}

	protected void classLoaderChanged() { }

	/**
		Annotate the NoClassDefFoundError with some info about the class
		we were trying to load.
	*/
	protected static Error noClassDefFound( String className, Error e ) {
		return new NoClassDefFoundError(
			"A class required by class: "+className +" could not be loaded:\n"
			+e.toString() );
	}

	protected static UtilEvalError cmUnavailable() {
		return new Capabilities.Unavailable(
			"ClassLoading features unavailable.");
	}

	public static interface Listener 
	{
		public void classLoaderChanged();
	}

	/**
		SignatureKey serves as a hash of a method signature on a class 
		for fast lookup of overloaded and general resolved Java methods. 
		<p>
	*/
	/*
		Note: is using SignatureKey in this way dangerous?  In the pathological
		case a user could eat up memory caching every possible combination of
		argument types to an untyped method.  Maybe we could be smarter about
		it by ignoring the types of untyped parameter positions?  The method
		resolver could return a set of "hints" for the signature key caching?

		There is also the overhead of creating one of these for every method
		dispatched.  What is the alternative?
	*/
	static class SignatureKey
	{
		Class clas;
		Class [] types;
		String methodName;
		int hashCode = 0;

		SignatureKey( Class clas, String methodName, Object [] args ) {
			this.clas = clas;
			this.methodName = methodName;
			this.types = Reflect.getTypes( args );
		}

		public int hashCode() 
		{ 
			if ( hashCode == 0 ) 
			{
				hashCode = clas.hashCode() * methodName.hashCode();
				if ( types == null ) // no args method
					return hashCode; 
				for( int i =0; i < types.length; i++ ) {
					int hc = types[i] == null ? 21 : types[i].hashCode();
					hashCode = hashCode*(i+1) + hc;
				}
			}
			return hashCode;
		}

		public boolean equals( Object o ) { 
			SignatureKey target = (SignatureKey)o;
			if ( types == null )
				return target.types == null;
			if ( clas != target.clas )
				return false;
			if ( !methodName.equals( target.methodName ) )
				return false;
			if ( types.length != target.types.length )
				return false;
			for( int i =0; i< types.length; i++ )
			{
				if ( types[i]==null ) 
				{
					if ( !(target.types[i]==null) )
						return false;
				} else 
					if ( !types[i].equals( target.types[i] ) )
						return false;
			}

			return true;
		}
	}
}
