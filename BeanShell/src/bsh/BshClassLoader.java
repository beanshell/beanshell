package bsh;

import java.net.*;
import java.util.*;
import java.io.*;

/**
	One of the things BshClassLoader does is to address a deficiency in
	URLClassLoader that prevents us from specifying individual classes
	via URLs.
*/
public class BshClassLoader extends URLClassLoader 
{
	public BshClassLoader( URL [] bases ) {
		super(bases);
	}

	public BshClassLoader( BshClassPath bcp ) {
		super( bcp.getPathComponents() );
	}

	/**
		For use by children
// NO longer using parent? get rid of this...
	*/
	protected BshClassLoader( ClassLoader parent ) { 
		super( new URL [] { }, parent );
	}

	public void addURL( URL url ) {
		super.addURL( url );
	}

	/**
		This modification allows us to reload classes which are in the 
		Java VM user classpath.  We search first rather than delegate to
		the parent classloader (or bootstrap path) first.
	*/
	public Class loadClass(String name, boolean resolve)
        throws ClassNotFoundException
    {
        Class c = null;

		/*
			Check first for classes loaded through this loader.
			The VM will not allow a class to be loaded twice.
		*/
        c = findLoadedClass(name);
		if ( c != null )
			return c;

		/*
			Try to find the class using our classloading mechanism.
			Note: I wish we didn't have to catch the exception here... slow
		*/
		try {
			c = findClass( name );
		} catch ( ClassNotFoundException e ) { }

		/*
			Checks parent or bootstrap path 
			unfortunately also tries findClass() again if those fail
			also we don't use parent... right?
		if ( c == null )
			return super.loadClass( name, resolve );
		*/
		if ( c == null )
			throw new ClassNotFoundException("here in loaClass");

		if ( resolve )
			resolveClass( c );

		return c;
	}

	/**
		Find the correct source for the class...

		Try designated loader if any
		Try our URLClassLoader paths if any
		Try base loader if any
		Try system ???
	*/
	// add some caching for not found classes?
	public Class findClass( String name ) throws ClassNotFoundException {

		// Should we try to load the class ourselves or delegate?
		// look for overlay loader

		ClassLoader cl = 	
			BshClassManager.getClassManager().getLoaderForClass( name );

		Class c;

		// If there is a designated loader and it's not us delegate to it
		if ( cl != null && cl != this )
			try {
				return cl.loadClass( name );
			} catch ( ClassNotFoundException e ) {
				throw new ClassNotFoundException(
					"Designated loader could not find class: "+e );
			}

		// Let URLClassLoader try any paths it may have
		if ( getURLs().length > 0 )
			try {
				return super.findClass(name);
			} catch ( ClassNotFoundException e ) { }


		// If there is a baseLoader and it's not us delegate to it
		BshClassManager bcm = BshClassManager.getClassManager();
		cl = bcm.getBaseLoader();

		if ( cl != null && cl != this )
			try {
				return cl.loadClass( name );
			} catch ( ClassNotFoundException e ) { }
		
		// Try system loader
		return bcm.loadSystemClass( name );
	}

	/*
		The superclass does something like this

        c = findLoadedClass(name);
        if null
            try
                if parent not null
                    c = parent.loadClass(name, false);
                else
                    c = findBootstrapClass(name);
            catch ClassNotFoundException 
                c = findClass(name);
	*/

}
