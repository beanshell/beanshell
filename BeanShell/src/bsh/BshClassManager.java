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
Do we need this at all?  why not map all reloaded?

		The loader to use where no mapping of reloaded classes exists.
		baseLoader is initially null.
	*/
	BshClassLoader baseLoader;

	/**
		Map of loaders to use for reloaded packages and classes
	*/
	Map classLoaderMap = new HashMap();

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
		else
			baseLoader.addURL( path );

		// fire after change...  semantics are "has changed"
		classLoaderChanged();
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

	/**
		The user classpath from system property
			java.class.path
	*/
	URL [] getJavaClassPath() 
		throws IOException
	{
		String cp=System.getProperty("java.class.path");
		String [] paths=StringUtil.split(cp, File.pathSeparator);

		URL [] urls = new URL[ paths.length ];
		for ( int i=0; i<paths.length; i++)
			urls[i] = new File( paths[i] ).toURL();

		return urls;
	}

	public static String canonicalizeClassName( String name ) {
		String classname=name.replace('/', '.');
		if ( classname.startsWith("class ") )
			classname=classname.substring(6);
		if ( classname.endsWith(".class") )
			classname=classname.substring(0,classname.length()-6);
		return classname;
	}

	/**
		Split class name into package and name
	*/
	public static String [] splitClassname ( String classname ) {
		classname = canonicalizeClassName( classname );

		int i=classname.lastIndexOf(".");
		String classn, packn;
		if ( i == -1 )  {
			// top level class
			classn = classname;
			packn="<unpackaged>";
		} else {
			packn = classname.substring(0,i);
			classn = classname.substring(i+1);
		}
		return new String [] { packn, classn };
	}
	

}
