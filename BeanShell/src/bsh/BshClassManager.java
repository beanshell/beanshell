package bsh;

import java.net.*;
import java.util.*;
import java.lang.ref.WeakReference;
import java.io.IOException;

/**
	Manage all classloading in BeanShell.
	Allows classpath extension and class file reloading.

	Currently relies on 1.2 for BshClassLoader and weak references.
	Is there a workaround for weak refs?  If so we could make this work
	with 1.1 by supplying our own classloader code...

*/
public class BshClassManager
{
	// Singleton for now
	private BshClassManager() { }
	private static BshClassManager manager = new BshClassManager();
	public static BshClassManager getClassManager() {
		return manager;
	}
	
	URL [] classpath;
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
	Map overlayLoaderMap = new HashMap();

	public Class getClassForName( String name ) {
		Class c = null;
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

	public void addClassPath( URL path ) {
		if ( baseLoader == null )
			setClassPath( new URL [] { path } );
		else {
			baseLoader.addURL( path );
			classLoaderChanged();
		}

	}

	/**
		Resetting the classpath means a new classloader which means
		all types change.
	*/
	public void setClassPath( URL [] cp ) {
		classpath = cp;
		baseLoader = new BshClassLoader( classpath );

		// fire after change...  semantics are "has changed"
		classLoaderChanged();
	}

	/**
		Reloading classes means creating a new classloader and using it
		whenever we are asked for a class in the appropriate space.
	*/
	public void reloadClasses( String path ) {
		if ( path.endsWith(".*") )  {
			// validate that it is a package here?
		} else { 
			// validate that it is a class here?
		}
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


}
