package bsh;

import java.net.*;

public class BshClassLoader extends URLClassLoader 
{

	public BshClassLoader( URL [] urls ) {
		super(urls);
	}

	public void addURL(URL url) {
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
