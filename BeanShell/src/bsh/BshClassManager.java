package bsh;

import java.net.*;
import java.util.*;
import java.lang.ref.WeakReference;

/**
	Manage all classloading in BeanShell.
	Allows classpath extension and class file reloading.

	Currently relies on 1.2 for URLClassLoader and weak references.
	Is there a workaround for weak refs?  If so we could make this work
	with 1.1 by supplying our own classloader...
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
	ClassLoader loader;
	Vector listeners = new Vector();

	public Class getClassForName( String name ) {
		Class c = null;
		if ( loader == null )
			try {
				c = Class.forName(name);
			} catch ( Exception e ) {
			} catch ( NoClassDefFoundError e2 ) {
			}
		else
			try {
				c = loader.loadClass(name);
			} catch ( Exception e ) {
			} catch ( NoClassDefFoundError e2 ) {
			}

		return c;
	}

/*
	public void addClassPath( URL path ) {
		classpath = cp;
		loader = new URLClassLoader( classpath );
		// fire after change...  semantics are "has changed"
		classLoaderChanged();
	}
*/


	/**
		Resetting the classpath means a new classloader which means
		all types change.
	*/
	public void setClassPath( URL [] cp ) {
		classpath = cp;
		loader = new URLClassLoader( classpath );
		// fire after change...  semantics are "has changed"
		classLoaderChanged();
	}

	public static Class classForName( String name ) {
		return getClassManager().getClassForName( name );
	}

/*
	public Class loadClass(String name, boolean resolve)
        throws ClassNotFoundException
    {
		Class c = null;
		try {
			c = super.findClass( name );
		} catch ( Exception e ) { }

		if ( c == null )
			return super.loadClass( name, resolve );

		if ( resolve )
			resolveClass( c );

		return c;

        // First, check if the class has already been loaded
        Class c = findLoadedClass(name);
        if (c == null) {
            try {
                if (parent != null) {
                    c = parent.loadClass(name, false);
                } else {
                    c = findBootstrapClass0(name);
                }
            } catch (ClassNotFoundException e) {
                // If still not found, then call findClass in order
                // to find the class.
                c = findClass(name);
            }
        }
	}
*/

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
	*/
	void classLoaderChanged() {
    	NameSpace.absoluteNonClasses = new Hashtable();
    	NameSpace.absoluteClassCache = new Hashtable();

		for (Enumeration e = listeners.elements(); e.hasMoreElements(); ) {
			WeakReference wr = (WeakReference)e.nextElement();
			((Listener)wr.get()).classLoaderChanged();
		}

	}

}
