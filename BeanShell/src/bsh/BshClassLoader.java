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
	}
*/

/*
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
*/

}
