package bsh;

import java.io.*;
import java.io.File;
import java.util.*;
import java.awt.*;

public class DiscreteFilesClassLoader extends BshClassLoader {
	File base;
	Set classNames = new HashSet();
	
	public DiscreteFilesClassLoader( 
		File base, String [] classNames, ClassLoader parent ) 
	{ 
		super( parent );
		this.base = base;
		this.classNames.addAll( Arrays.asList(classNames) );
	}

	public Class findClass( String name ) throws ClassNotFoundException {
		if ( classNames.contains( name ) )
			return loadClassFromFile( base, name );
		else
			return super.findClass( name );
	}
  
	public Class loadClassFromFile( File base, String className ) 
	{
		String n = className.replace( '.', File.separatorChar ) + ".class";
		File file = new File( base, n );

		if ( file == null || !file.exists() )
			return null;

		byte [] bytes;
		try {
			FileInputStream fis = new FileInputStream(file);
			DataInputStream dis = new DataInputStream( fis );
     
			bytes = new byte [ (int)file.length() ];

			dis.readFully( bytes );
			dis.close();
		} catch(IOException ie ) {
			throw new RuntimeException("Couldn't load file: "+file);
		}

		Class c =defineClass(className, bytes, 0, bytes.length);
		return c;
    }

}
