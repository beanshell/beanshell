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

package bsh.classpath;

import java.net.*;
import java.util.*;
import java.lang.ref.*;
import java.io.IOException;
import java.io.*;
import bsh.classpath.BshClassPath.DirClassSource;
import bsh.BshClassManager;
import bsh.ClassPathException;
import bsh.Interpreter;  // for debug()
import bsh.EvalError; 

/**
	<pre>
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
	used unless/until the classpath is modified or a class is reloaded.

	Note: we may need some synchronization in here

	Note on jdk1.2 dependency:

	We are forced to use weak references here to accomodate all of the 
	fleeting namespace listeners.  (NameSpaces must be informed if the class 
	space changes so that they can un-cache names).  I had the interesting 
	thought that a way around this would be to implement BeanShell's own 
	garbage collector...  Then I came to my senses and said - screw it, 
	class re-loading will require 1.2.
	</pre>
*/
public class ClassManagerImpl extends BshClassManager
{
	/**
		The classpath of the base loader.  Initially empty.
		This grows as paths are added or is reset when the classpath 
		is explicitly set.
		This could also be called the "extension" class path, but is not
		strictly confined to added path (could be set arbitrarily by
		setClassPath())
	*/
	private BshClassPath baseClassPath;

	/**
		This is the full blown classpath including baseClassPath (extensions),
		user path, and java bootstrap path (rt.jar)

		This is lazily constructed and further (and more importantly) lazily 
		intialized in components because mapping the full path could be 
		expensive.

		The full class path is a composite of:
			baseClassPath (user extension) : userClassPath : bootClassPath 
		in that order.
	*/
	private BshClassPath fullClassPath;

	// ClassPath Change listeners
	private Vector listeners = new Vector();
	private ReferenceQueue refQueue = new ReferenceQueue();

	/**
		This handles extension / modification of the base classpath
		The loader to use where no mapping of reloaded classes exists.

		The baseLoader is initially null meaning no class loader is used.
	*/
	private BshClassLoader baseLoader;

	/**
		Map by classname of loaders to use for reloaded classes
	*/
	private Map loaderMap;

	/**
		Used by BshClassManager singleton constructor
	*/
	public ClassManagerImpl() { 
		reset();
	}

	/**
		@return the class or null
	*/
	public Class getClassForName( String name ) 
	{
		// check cache 
		Class c = (Class)absoluteClassCache.get(name);
		if (c != null )
			return c;

		if ( absoluteNonClasses.get(name)!=null ) {
			Interpreter.debug("absoluteNonClass list hit: "+name);
			return null;
		}

		// Try to load the class
		Interpreter.debug("Trying to load class: "+name);

		ClassLoader overlayLoader = getLoaderForClass( name );
		if ( overlayLoader != null ) {
			try {
				c = overlayLoader.loadClass(name);
			} catch ( Exception e ) {
			} catch ( NoClassDefFoundError e2 ) {
			}

			// Should be there since it was explicitly mapped
			// throw an error?
		}

		if ( c == null )
			// insure that core classes are always loaded from the same loader
			if ( name.startsWith("bsh.") )
				try {
					c = Interpreter.class.getClassLoader().loadClass( name );
				} catch ( ClassNotFoundException e ) {}
			// If there is a base loader use it
			else if ( baseLoader != null )
				try {
					c = baseLoader.loadClass( name );
				} catch ( ClassNotFoundException e ) {}
			else
				try {
					c = plainClassForName( name );
				} catch ( ClassNotFoundException e ) {}

		// cache results
		/* Note: plainClassForName already caches, so it will be redundant
			in that case, however this process only happens once */
		BshClassManager.cacheClassInfo( name, c );

		return c;
	}

	/**
		Delegate for bottom level implementation of Class.forName().
		This is here solely to provide for Java version specific features.
		In this case - the Thread getContextClassLoader() which is required
		to get bsh to see user classpath when it's installed in the lib/ext
		directory.
		@see BshClassManager.plainClassForName()
	*/
	public Class getPlainClassForName( String name )  
		throws ClassNotFoundException
	{
		// Requires JDK 1.2+
		ClassLoader contextClassLoader = 
			Thread.currentThread().getContextClassLoader();
		if ( contextClassLoader != null )
			return Class.forName( name, true, contextClassLoader );
		else
			return Class.forName( name );
	}

	public ClassLoader getBaseLoader() {
		return baseLoader;
	}

	public ClassLoader getLoaderForClass( String name ) {
		return (ClassLoader)loaderMap.get( name );
	}


	// Classpath mutators

	/**
	*/
	public void addClassPath( URL path ) 
		throws IOException 
	{
		if ( baseLoader == null )
			setClassPath( new URL [] { path } );
		else {
			// opportunity here for listener in classpath
			baseLoader.addURL( path );
			baseClassPath.add( path );
			classLoaderChanged();
		}
	}

	/**
		Clear all loaders and start over.  No class loading.
	*/
	public void reset()
	{
		baseClassPath = new BshClassPath("baseClassPath");
		baseLoader = null;
		loaderMap = new HashMap();
		classLoaderChanged();
	}

	/**
		Set a new base classpath and create a new base classloader.
		This means all types change. 
	*/
	public void setClassPath( URL [] cp ) {
		baseClassPath.setPath( cp );
		initBaseLoader();
		loaderMap = new HashMap();
		classLoaderChanged();
	}

	/**
		Overlay the entire path with a new class loader.
		Set the base path to the user path + base path.

		No point in including the boot class path (can't reload thos).
	*/
	public void reloadAllClasses() throws ClassPathException 
	{
		BshClassPath bcp = new BshClassPath("temp");
		bcp.addComponent( baseClassPath );
		bcp.addComponent( BshClassPath.getUserClassPath() );
		setClassPath( bcp.getPathComponents() );
	}

	/**
		init the baseLoader from the baseClassPath
	*/
	private void initBaseLoader() {
		baseLoader = new BshClassLoader( baseClassPath );
	}

	// class reloading

	/**
		Reloading classes means creating a new classloader and using it
		whenever we are asked for classes in the appropriate space.
		For this we use a DiscreteFilesClassLoader
	*/
	public void reloadClasses( String [] classNames ) 
		throws ClassPathException
	{
		// validate that it is a class here?

		// init base class loader if there is none...
		if ( baseLoader == null )
			initBaseLoader();

		DiscreteFilesClassLoader.ClassSourceMap map = 
			new DiscreteFilesClassLoader.ClassSourceMap();

		for (int i=0; i< classNames.length; i++) {
			String name = classNames[i];

			// look in baseLoader class path 
			baseClassPath.insureInitialized();
			Object o = baseClassPath.getClassSource( name );

			// look in user class path 
			if ( o == null ) {
				BshClassPath.getUserClassPath().insureInitialized();
				o = BshClassPath.getUserClassPath().getClassSource( name );
			}

			// No point in checking boot class path, can't reload those.
			// else we could have used fullClassPath above.
				
			if ( o == null )
				throw new ClassPathException("Nothing known about class: "
					+name );

			if ( ! (o instanceof DirClassSource) )
				throw new ClassPathException("Cannot reload class: "+name+
					" from source: "+o);

			map.put( name, ((DirClassSource)o).getDir() );
		}

		// Create classloader for the set of classes
		ClassLoader cl = new DiscreteFilesClassLoader( map );

		// map those classes the loader in the overlay map
		Iterator it = map.keySet().iterator();
		while ( it.hasNext() )
			loaderMap.put( (String)it.next(), cl );

		classLoaderChanged();
	}

	/**
		Reload all classes in the specified package: e.g. "com.sun.tools"

		The special package name "<unpackaged>" can be used to refer 
		to unpackaged classes.
	*/
	public void reloadPackage( String pack ) 
		throws ClassPathException 
	{
		Collection classes = 
			baseClassPath.getClassesForPackage( pack );

		if ( classes == null )
			classes = 
				BshClassPath.getUserClassPath().getClassesForPackage( pack );

		// no point in checking boot class path, can't reload those

		if ( classes == null )
			throw new ClassPathException("No classes found for package: "+pack);

		reloadClasses( (String[])classes.toArray( new String[0] ) );
	}

	/**
		Unimplemented
		For this we'd have to store a map by location as well as name...

	public void reloadPathComponent( URL pc ) throws ClassPathException {
		throw new ClassPathException("Unimplemented!");
	}
	*/

	// end reloading

	/**
		Get the full blown classpath.
	*/
	public BshClassPath getClassPath() throws ClassPathException
	{
		if ( fullClassPath != null )
			return fullClassPath;
	
		fullClassPath = new BshClassPath("BeanShell Full Class Path");
		fullClassPath.addComponent( BshClassPath.getUserClassPath() );
		try {
			fullClassPath.addComponent( BshClassPath.getBootClassPath() );
		} catch ( ClassPathException e ) { 
			System.err.println("Warning: can't get boot class path");
		}
		fullClassPath.addComponent( baseClassPath );

		return fullClassPath;
	}

	/**
		Support for "import *;"
		Hide details in here as opposed to NameSpace.
	*/
	public void doSuperImport() 
		throws EvalError
	{
		try {
			getClassPath().insureInitialized();
			// prime the lookup table
			getClassNameByUnqName( "" ) ;

			// always true now
			//getClassPath().setNameCompletionIncludeUnqNames(true);

		} catch ( ClassPathException e ) {
			throw new EvalError("Error importing classpath "+ e );
		}
	}

	/**
		Return the name or null if none is found,
		Throw an ClassPathException containing detail if name is ambigous.
	*/
	public String getClassNameByUnqName( String name ) 
		throws ClassPathException
	{
		return getClassPath().getClassNameByUnqName( name );
	}

	public void addListener( Listener l ) {
		listeners.addElement( new WeakReference( l, refQueue) );

		// clean up old listeners
		Reference deadref;
		while ( (deadref = refQueue.poll()) != null ) {
			boolean ok = listeners.removeElement( deadref );
			if ( ok ) {
				//System.err.println("cleaned up weak ref: "+deadref);
			} else {
				Interpreter.debug(
					"tried to remove non-existent weak ref: "+deadref);
			}
		}
	}

	public void removeListener( Listener l ) {
		throw new Error("unimplemented");
	}

	/**
		Clear global class cache and notify namespaces to clear their 
		class caches.

		The listener list is implemented with weak references so that we 
		will not keep every namespace in existence forever.
	*/
	protected void classLoaderChanged() 
	{
		// clear the static caches in BshClassManager
		clearCaches();

		for (Enumeration e = listeners.elements(); e.hasMoreElements(); ) {
			WeakReference wr = (WeakReference)e.nextElement();
			Listener l = (Listener)wr.get();
			if ( l == null )  // garbage collected
				listeners.removeElement( wr );
			else
				l.classLoaderChanged();
		}

	}

	public void dump( PrintWriter i ) 
	{
		i.println("Bsh Class Manager Dump: ");
		i.println("----------------------- ");
		i.println("baseLoader = "+baseLoader);
		i.println("loaderMap= "+loaderMap);
		i.println("----------------------- ");
		i.println("baseClassPath = "+baseClassPath);
	}

}
