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
	/**
		Use a BshClassPath internally to keep track of the subset of the
		classpath consisting of individually specified class files.
		This maps sources to names for us.
	*/
	BshClassPath looseClasses;

	/**
		Static factory method that allows us to sort out individual class
		file urls from dirs and jars before creating the URLClassLoader
	*/
	public static BshClassLoader getClassLoaderFor( URL [] urls ) 
	{
		// sort classes from base dirs and jars
		List bases = new ArrayList();
		List files = new ArrayList();

		for (int i=0; i< urls.length; i++)
			if ( BshClassPath.isClassFileName( urls[i].getFile() ) )
				files.add( urls[i] );
			else
				bases.add( urls[i] );

		return new BshClassLoader( 
			(URL [])bases.toArray( new URL[0] ),
			(URL [])files.toArray( new URL[0] )
		);
	}

	private BshClassLoader( URL [] bases, URL [] files ) {
		super(bases);
		try {
			looseClasses = new BshClassPath ( files );
		} catch ( IOException e ) {
			// shouldn't happen...  no traversal of classpath for loose files
			throw new RuntimeException("can't make classpath for files");
		}
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
        Class c = findLoadedClass(name);
		if ( c != null )
			return c;

		// wish we didn't have to catch the exception here... slow
		try {
			c = super.findClass( name );
		} catch ( Exception e ) { }

		if ( c == null )
			return super.loadClass( name, resolve );
		else
			if ( resolve )
				resolveClass( c );

		return c;
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
