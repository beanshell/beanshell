/*****************************************************************************
 *                                                                           *
 *  This file is part of the BeanShell Java Scripting distribution.          *
 *  Documentation and updates may be found at http://www.beanshell.org/      *
 *                                                                           *
 *  BeanShell is distributed under the terms of the LGPL:                    *
 *  GNU Library Public License http://www.gnu.org/copyleft/lgpl.html         *
 *                                                                           *
 *  Patrick Niemeyer (pat@pat.net)                                           *
 *  Author of Exploring Java, O'Reilly & Associates                          *
 *  http://www.pat.net/~pat/                                                 *
 *                                                                           *
 *****************************************************************************/

package bsh.classpath;

import java.io.*;
import java.io.File;
import java.util.*;
import java.awt.*;

public class DiscreteFilesClassLoader extends BshClassLoader {
	/**
		Map of class sources which also implies our coverage space.
	*/
	ClassSourceMap map;

	public static class ClassSourceMap extends HashMap {
		public void put( String name, File baseDir ) {
			super.put( name, baseDir );
		}
		public File get( String name ) {
			return (File)super.get( name );
		}
	}
	
	public DiscreteFilesClassLoader( ClassSourceMap map ) { 
		this.map = map;
	}

	/*
	public DiscreteFilesClassLoader( ClassSourceMap map, ClassLoader parent ) 
	{ 
		super( parent );
		this.map = map;
	}
	*/

	/**
	*/
	public Class findClass( String name ) throws ClassNotFoundException {
		// Load it if it's one of our classes
		File base = map.get( name );
		if ( base != null )
			return loadClassFromFile( base, name );
		else
			// Let BshClassLoader try to find appropriate source
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

	public String toString() {
		return super.toString() + "for files: "+map;
	}

}
