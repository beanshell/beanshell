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

	Note: currently class loading features affect all instances of the
	Interpreter.  However the basic design of this class will allow for
	per instance class management in the future if it is desired.
*/
/*
	Implementation notes:

	Note: we may need some synchronization in here

	Note on jdk1.2 dependency:
	<p>

	We are forced to use weak references here to accomodate all of the 
	fleeting namespace listeners as they fall out of scope.  (NameSpaces must 
	be informed if the class space changes so that they can un-cache names).  
	I had the thought that a way around this would be to implement BeanShell's 
	own garbage collector...  Then I came to my senses.
	<p>

	Perhaps a simpler idea would be to have entities that reference cached
	types always perform a light weight check with a counter / reference
	value and use that to detect changes in the namespace.  This puts the 
	burden on the consumer to check at appropriate times, but could eliminate
	the need for the listener system in many places and the necessity of weak 
	references in this package.
	<p>
*/
public abstract class BshClassManager
{
	/** Singleton class manager */
	private static BshClassManager manager;
	private static boolean checkedForManager;
	// Use a hashtable as a Set...
	private static Object NOVALUE = new Object(); 
	
	/**
		An external classloader supplied by the setClassLoader() command.
	*/
	private static ClassLoader externalClassLoader;

	/**
		Global cache for things we know are classes.
		Note: these should probably be re-implemented with Soft references.
		(as opposed to strong or Weak)
	*/
    protected transient static Hashtable absoluteClassCache = new Hashtable();
	/**
		Global cache for things we know are *not* classes.
		Note: these should probably be re-implemented with Soft references.
		(as opposed to strong or Weak)
	*/
    protected transient static Hashtable absoluteNonClasses = new Hashtable();

	// Begin static methods

	/**
		@return the BshClassManager singleton or null, indicating no
		class manager is available.
	*/
// Note: this should probably throw Capabilities.Unavailable instead of
// returning null
	public static BshClassManager getClassManager() 
	{
		// Bootstrap the class manager if it exists

		// have we loaded it before?
		if ( !checkedForManager && manager == null )
			// Do we have the necessary jdk1.2 packages?
			try {
				if ( plainClassForName("java.lang.ref.WeakReference") != null
					&& plainClassForName("java.util.HashMap")  != null )
				{
					// try to load the implementation
					Class bcm = plainClassForName(
						"bsh.classpath.ClassManagerImpl");
					manager = (BshClassManager)bcm.newInstance();
				}
			} catch ( ClassNotFoundException e ) {
				//System.err.println("No class manager available.");
			} catch ( Exception e ) {
				System.err.println("Error loading classmanager: "+e);
			}

		checkedForManager = true;
		return manager;
	}

	public static boolean classExists( String name ) {
		return ( classForName( name ) != null );
	}

	/**
		Load the specified class by name, taking into account added classpath
		and reloaded classes, etc.
		@return the class or null
	*/
	public static Class classForName( String name ) {
		BshClassManager manager = getClassManager(); // prime the singleton
		if ( manager != null )
			return manager.getClassForName( name );
		else
			try {
				return plainClassForName( name );
			} catch ( ClassNotFoundException e ) {
				return null;
			}
	}

	/**
		Perform a plain Class.forName() 
		This simply wraps that method call and provides a central point for
		monitoring and handling certain Java version dependent bugs, etc.
		Note: this used to be called loadSystemClass()
		@return the class
	*/
	public static Class plainClassForName( String name ) 
		throws ClassNotFoundException 
	{
		try {
			Class c;
			if ( externalClassLoader != null ) {
				c = externalClassLoader.loadClass( name );
			}else
				c = Class.forName(name);

			cacheClassInfo( name, c );
			return c;
		/*
			This is weird... jdk under Win is throwing these to
			warn about lower case / upper case possible mismatch.
			e.g. bsh.console bsh.Console
		*/
		} catch ( NoClassDefFoundError e ) {
			cacheClassInfo( name, null ); // non-class
			throw new ClassNotFoundException( e.toString() );
		}
	}

	/**
		Cache info about whether name is a class or not.
		@param value 
			if value is non-null, cache the class
			if value is null, set the flag that it is *not* a class to
			speed later resolution
	*/
	public static void cacheClassInfo( String name, Class value ) {
		if ( value != null )
			absoluteClassCache.put( name, value );
		else
			absoluteNonClasses.put( name, NOVALUE );
	}

	/**
		Clear the static caches in BshClassManager
	*/
	protected void clearCaches() {
    	absoluteNonClasses = new Hashtable();
    	absoluteClassCache = new Hashtable();
	}

	/**
		Add a BshClassManager.Listener to the class manager.
		The listener is informed upon changes to the classpath.
		This is a static convenience form of BshClassManager addListener().
		If there is no class manager the listener will be ignored.
	*/
	public static void addCMListener( Listener l ) {
		getClassManager(); // prime it
		if ( manager != null )
			manager.addListener( l );
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
	public static void setClassLoader( ClassLoader externalCL ) 
	{
		externalClassLoader = externalCL;
		getClassManager().classLoaderChanged();
	}

	// end static methods

	public static interface Listener 
	{
		public void classLoaderChanged();
	}

	// Begin interface methods

	public abstract Class getClassForName( String name );

	public abstract ClassLoader getBaseLoader();

	public abstract ClassLoader getLoaderForClass( String name );

	public abstract void addClassPath( URL path )
		throws IOException;

	/**
		Clear all loaders and start over.  No class loading.
	*/
	public abstract void reset();

	/**
		Set a new base classpath and create a new base classloader.
		This means all types change. 
	*/
	public abstract void setClassPath( URL [] cp );

	/**
		Overlay the entire path with a new class loader.
		Set the base path to the user path + base path.

		No point in including the boot class path (can't reload thos).
	*/
	public abstract void reloadAllClasses() throws ClassPathException;

	/**
		Reloading classes means creating a new classloader and using it
		whenever we are asked for classes in the appropriate space.
		For this we use a DiscreteFilesClassLoader
	*/
	public abstract void reloadClasses( String [] classNames )
		throws ClassPathException;

	/**
		Reload all classes in the specified package: e.g. "com.sun.tools"

		The special package name "<unpackaged>" can be used to refer 
		to unpackaged classes.
	*/
	public abstract void reloadPackage( String pack ) 
		throws ClassPathException ;

	/**
		This has been removed from the interface to shield the core from the
		rest of the classpath package. If you need the classpath you will have
		to cast the classmanager to its impl.

		public abstract BshClassPath getClassPath() throws ClassPathException;
	*/

	/**
		Support for "import *;"
		Hide details in here as opposed to NameSpace.
	Note: this used to be package private...
	*/
	public abstract void doSuperImport() throws EvalError;

	/**
		Return the name or null if none is found,
		Throw an ClassPathException containing detail if name is ambigous.
	Note: this used to be package private...
	*/
	public abstract String getClassNameByUnqName( String name ) 
		throws ClassPathException;

	public abstract void addListener( Listener l );

	public abstract void removeListener( Listener l );

	public abstract void dump( PrintWriter pw );

	protected abstract void classLoaderChanged();
}
