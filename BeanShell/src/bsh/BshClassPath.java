package bsh;

import java.util.*;
import java.util.zip.*;
import java.io.*;
import java.net.URL;
import java.io.File;

public class BshClassPath 
{
	/**
		Set of all classes in a package 
	*/
	Map packageMap = new HashMap();
	/**
		Map of source (URL or File dir) of every clas
	*/
	Map classSource = new HashMap();

	public void add( List urls ) throws IOException { 
		for(int i=0; i< urls.size(); i++)
			add( (URL)urls.get(i) );
	}

	public void add( URL url ) throws IOException { 
		File f = new File( url.getFile() );
		if ( f.isDirectory() )
			add( traverseDirForClasses( f ), f );
		else
			add( searchJarForClasses( url ), url );
	}

	private void add( String [] classes, Object source ) {
		for(int i=0; i< classes.length; i++)
			System.out.println( classes[i] +": "+ source );
	}

	void addClass( String className, Object source ) {
		// add to package map
		String [] sa = BshClassManager.splitClassname( className );
		String pack = sa[0];
		String clas = sa[1];
		Set set = (Set)packageMap.get( pack );
		if ( set == null ) {
			set = new HashSet();
			packageMap.put( pack, set );
		}
		set.add( clas );

		// Add to classSource map
		Object obj = classSource.get( className );
		// don't replace already parsed (earlier in classpath)
		if ( obj == null )
			classSource.put( className, source );
	}

	/**
		Return the set of classes in the specified package
	*/
	public Set getClassesForPackage( String pack ) {
		return (Set)packageMap.get( pack );
	}

	/**
		Return the source of the specified class
		  1) a URL for jar file 
		or
		  2) a File for directory
	*/
	public Object getClassSource( String className ) {
		return classSource.get( className );
	}


	public static String [] traverseDirForClasses( File dir ) 
		throws IOException	
	{
		List list = traverseDirForClassesAux( dir, dir );
		return (String[])list.toArray( new String[0] );
	}

	static List traverseDirForClassesAux( File topDir, File dir ) 
		throws IOException
	{
		List list = new ArrayList();
		String top = topDir.getAbsolutePath();

		File [] children = dir.listFiles();
		for (int i=0; i< children.length; i++)	{
			File child = children[i];
			if ( child.isDirectory() )
				list.addAll( traverseDirForClassesAux( topDir, child ) );
			else {
				String name = child.getAbsolutePath();
				if ( isClassName( name ) ) {
					/* 
						Remove absolute (topdir) portion of path and leave 
						package-class part 
					*/
					if ( name.startsWith( top ) )
						name = name.substring( top.length()+1 );
					else
						throw new IOException( "problem parsing paths" );

					name = BshClassManager.canonicalizeClassName(name);
					list.add( name );
				}
			}
		}
		
		
		return list;
	}

	/**
		Get the class file entries from the Jar
	*/
	static String [] searchJarForClasses( URL jar ) 
		throws IOException 
	{
		Vector v = new Vector();
		InputStream in = jar.openStream(); 
		ZipInputStream zin = new ZipInputStream(in);

		ZipEntry ze;
		while( (ze= zin.getNextEntry()) != null ) {
			String name=ze.getName();
			if ( isClassName( name ) )
				v.addElement( BshClassManager.canonicalizeClassName(name) );
		}
		zin.close();

		String [] sa = new String [v.size()];
		v.copyInto(sa);
		return sa;
	}

	static boolean isClassName( String name ){
			return ( name.endsWith(".class") && (name.indexOf('$')==-1) );
	}

	public static void main( String [] args ) throws Exception {
		List urls = new ArrayList();
		for(int i=0; i< args.length; i++)
			urls.add (new File(args[i]).toURL());
		BshClassPath bcp = new BshClassPath();
		bcp.add( urls );
	}

}
