package bsh;

import java.net.*;
import java.util.*;
import java.lang.ref.WeakReference;
import java.io.IOException;
import BshClassPath.DirClassSource;

/**
	Manage all classloading in BeanShell.
	Allows classpath extension and class file reloading.

	Currently relies on 1.2 for BshClassLoader and weak references.
	Is there a workaround for weak refs?  If so we could make this work
	with 1.1 by supplying our own classloader code...

	@see http://www.beanshell.org/manual/classloading.html for details
	on the bsh classloader architecture.
*/
public class BshClassManager
{
	// Singleton for now
	private BshClassManager() { }
	private static BshClassManager manager = new BshClassManager();
	public static BshClassManager getClassManager() {
		return manager;
	}
	
	// Note it's kind of goofy having both of these, but as it stands,
	// constructing BshClassPath is expensive... should move lazy instantiation
	// inside there...
	/** Primary store for the classpath components */
	List classPath = new ArrayList();
	/** Lazily constructed full parse of the class path */
	BshClassPath bshClassPath;

	Vector listeners = new Vector();

	/**
		This handles extension / modification of the base classpath

		The loader to use where no mapping of reloaded classes exists.
		baseLoader is initially null.
	*/
	BshClassLoader baseLoader;

	/**
		Map by classname of loaders to use for reloaded classes
	*/
	Map loaderMap = new HashMap();

	public Class getClassForName( String name ) {
		Class c = null;

		//ClassLoader overlayLoader = (ClassLoader)loaderMap.get( name );
		ClassLoader overlayLoader = getLoaderForClass( name );
		if ( overlayLoader != null ) {
			try {
				c = overlayLoader.loadClass(name);
			} catch ( Exception e ) {
			} catch ( NoClassDefFoundError e2 ) {
			}

			// Should be there since it was explicitly mapped
			// throw an error?
			if ( c != null )
				return c;
		}

		if ( baseLoader == null )
			try {
				c = Class.forName(name);
			} catch ( Exception e ) {
			} catch ( NoClassDefFoundError e2 ) {
			}
		else
			try {
				c = baseLoader.loadClass(name);
			} catch ( Exception e ) {
			} catch ( NoClassDefFoundError e2 ) {
			}

		return c;
	}

	public ClassLoader getLoaderForClass( String name ) {
		return (ClassLoader)loaderMap.get( name );
	}

	// Classpath mutators

	/**
	*/
// ioexception is for adding to bshclasspath... remove if we fix lazy
// instantiaion inside of it
	public void addClassPath( URL path ) throws IOException {
		if ( baseLoader == null )
			setClassPath( new URL [] { path } );
		else {
			baseLoader.addURL( path );
			classPath.add( path );
			bshClassPath.add( path );
			classLoaderChanged();
		}
	}

	/**
		Clear all loaders and start over.
	*/
	public void reset() {
		baseLoader = null;
		loaderMap = new HashMap();
		classPath = new ArrayList();
		bshClassPath = null;
		classLoaderChanged();
	}

	/**
		Set the base classpath with a new classloader.
		This means all types change.  This also resets the bsh classpath 
		object which may be expensive to regenerate.
	*/
	public void setClassPath( URL [] cp ) {
		reset();
		classPath.addAll( Arrays.asList( cp ) );
		baseLoader = new BshClassLoader( cp );

		// fire after change...  semantics are "has changed"
		classLoaderChanged();
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

		BshClassPath bcp = getClassPath();

		DiscreteFilesClassLoader.ClassSourceMap map = 
			new DiscreteFilesClassLoader.ClassSourceMap();

		for (int i=0; i< classNames.length; i++) {
			String name = classNames[i];
			Object o = bcp.getClassSource( name );
			if ( o == null )
				throw new ClassPathException("Nothing known about class: "
					+name );
			if ( ! (o instanceof DirClassSource) )
				throw new ClassPathException("Cannot reload class: "+name+
					" from source: "+o);
			map.put( name, ((DirClassSource)o).getDir() );
		}

		// Create classloader for the set of classes with the base loader
		// as the parent
		ClassLoader cl = new DiscreteFilesClassLoader( map, baseLoader );

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
	public void reloadPackage( String pack ) throws ClassPathException {
		BshClassPath bcp = getClassPath();
		Collection classes = bcp.getClassesForPackage( pack );
		if ( classes == null )
			throw new ClassPathException("No classes found for package: "+pack);

		reloadClasses( (String[])classes.toArray( new String[0] ) );
	}

	public void reloadPathComponent( URL pc ) {
	}

	// end reloading

	/**
		Do lazy instantiation.  This may be expensive to create and we only
		need it for class reloading or tools that require mapping the class
		space.
	*/
	public BshClassPath getClassPath() {
		if ( bshClassPath == null )
			bshClassPath = new BshClassPath( 
				(URL[])classPath.toArray(new URL[0]) );

		return bshClassPath;
	}

	public static Class classForName( String name ) {
		return getClassManager().getClassForName( name );
	}

	static interface Listener {
		public void classLoaderChanged();
	}

	public void addListener( Listener l ) {
		listeners.addElement( new WeakReference(l) );
	}
	public void removeListener( Listener l ) {
		listeners.removeElement( l );
	}

	/**
		Clear global class cache and notify namespaces to clear their 
		class caches.

		The listener list is implemented with weak references so that we 
		will not keep every namespace in existence forever.
	*/
	void classLoaderChanged() {
    	NameSpace.absoluteNonClasses = new Hashtable();
    	NameSpace.absoluteClassCache = new Hashtable();

		for (Enumeration e = listeners.elements(); e.hasMoreElements(); ) {
			WeakReference wr = (WeakReference)e.nextElement();
			Listener l = (Listener)wr.get();
			if ( l == null )  // garbage collected
				listeners.removeElement( wr );
			else
				l.classLoaderChanged();
		}

	}

	public void dump( Interpreter i ) {
		i.println("Class Manager Dump: ");
		i.println("------------------- ");
		i.println("baseLoader = "+baseLoader);
		i.println("loaderMap= "+loaderMap);
	}


	public static class ClassPathException extends Exception {
		public ClassPathException( String msg ) { super(msg); }
	}

}
