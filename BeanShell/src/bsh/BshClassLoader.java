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

	/**
		For use by children
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
		the parent classloader first.
	*/
	public Class loadClass(String name, boolean resolve)
        throws ClassNotFoundException
    {
        Class c = null;

        c = findLoadedClass(name);
		if ( c != null )
			return c;

		// wish we didn't have to catch the exception here... slow
		try {
			c = findClass( name );
		} catch ( ClassNotFoundException e ) { }

		if ( c == null )
			return super.loadClass( name, resolve );

		if ( resolve )
			resolveClass( c );

		return c;
	}

// see if we can resolve classes through class manager rather than parent

	public Class findClass( String name ) throws ClassNotFoundException {
		ClassLoader cl = 	
			BshClassManager.getClassManager().getLoaderForClass( name );

		// Is the designated loader for the class us?
		if ( cl!= null && cl != this )
			// delegate
			return cl.loadClass( name );
		else
			return super.findClass(name);
	}

	/*
		The original superclass code does something like this

        c = findLoadedClass(name);
        if null
            try {
                if parent not null
                    c = parent.loadClass(name, false);
                else
                    c = findBootstrapClass(name);
            } catch ClassNotFoundException 
                c = findClass(name);
            }
        }
	*/

}
