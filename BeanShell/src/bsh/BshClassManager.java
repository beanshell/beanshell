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

	Bsh has a multi-tiered class loading architecture.  No class loader is
	used unless/until the classpath is modified or a class is reloaded.
*/
public class BshClassManager
{
	// Singleton for now
	private static BshClassManager manager = new BshClassManager();
	public static BshClassManager getClassManager() {
		return manager;
	}
	private BshClassManager() { 
		reset();
	}
	
	/**
		The class path.  Initially this is the java user class path.
	*/
	BshClassPath classPath;

	Vector listeners = new Vector();

	/**
		This handles extension / modification of the base classpath
		The loader to use where no mapping of reloaded classes exists.

		The baseLoader is initially null meaning no class loader is used.
	*/
	BshClassLoader baseLoader;

	/**
		Map by classname of loaders to use for reloaded classes
	*/
	Map loaderMap;

	public Class getClassForName( String name ) {
		Class c = null;

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

fix me!!!!!!
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
		ClassLoader loader = (ClassLoader)loaderMap.get( name );
		if ( loader != null )
			return loader;
		else
			if ( baseLoader != null )
				return baseLoader;
			else
				return ??
	}

	// Classpath mutators

	/**
	*/
	public void addClassPath( URL path ) throws IOException {
		if ( baseLoader == null )
			setClassPath( new URL [] { path } );
		else {
			baseLoader.addURL( path );
			classPath.add( path );
			classLoaderChanged();
		}
	}

	/**
		Clear all loaders and start over.
	*/
	public void reset()
	{
		classPath = BshClassPath.getUserClassPath();
		baseLoader = null;
		loaderMap = new HashMap();
		classLoaderChanged();
	}

	/**
		Set the base classpath with a new classloader.
		This means all types change.  This also resets the bsh classpath 
		object which may be expensive to regenerate.
	*/
	public void setClassPath( URL [] cp ) {
		reset();
		classPath.add( cp );
		initBaseLoader();

		// fire after change...  semantics are "has changed"
		classLoaderChanged();
	}

	/**
		init the base loader from the current classpath
	*/
	private void initBaseLoader() {
		baseLoader = new BshClassLoader( classPath );
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
			Object o = classPath.getClassSource( name );
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
		Collection classes = classPath.getClassesForPackage( pack );
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
	*/
	public BshClassPath getClassPath() {
		return classPath;
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
		i.println("Bsh Class Manager Dump: ");
		i.println("----------------------- ");
		i.println("baseLoader = "+baseLoader);
		i.println("loaderMap= "+loaderMap);
		i.println("----------------------- ");
		i.println("ClassPath = "+classPath);
	}

}
