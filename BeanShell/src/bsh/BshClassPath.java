package bsh;

import java.util.*;
import java.util.zip.*;
import java.io.*;
import java.net.URL;
import java.io.File;

/**
	Maps all classes in a specified set of URLs which may include:
		Jar files, base dirs, individual class files

*/
public class BshClassPath 
{
	public BshClassPath( URL [] urls ) {
		add( urls );
	}

	/**
		Set of all classes in a package 
	*/
	Map packageMap = new HashMap();
	/**
		Map of source (URL or File dir) of every clas
	*/
	Map classSource = new HashMap();

	public void add( URL [] urls ) { 
		for(int i=0; i< urls.length; i++)
			try{
				add( urls[i] );
			} catch ( IOException e ) {
				System.err.println("Error constructing classpath: "
					+urls[i]+": "+e );
			}
	}

	public void add( URL url ) throws IOException { 
		String name = url.getFile();
		File f = new File( name );

		if ( f.isDirectory() )
			add( traverseDirForClasses( f ), new DirClassSource(f) );
		else if ( isArchiveFileName( name ) )
			add( searchJarForClasses( url ), new JarClassSource(url) );
/*
		else if ( isClassFileName( name ) )
			add( looseClass( name ), url );
*/
		else
			System.out.println("Not a classpath component: "+ name );
	}

	private void add( String [] classes, Object source ) {
		for(int i=0; i< classes.length; i++) {
			System.out.println( classes[i] +": "+ source );
			addClass( classes[i], source );
		}
	}

	void addClass( String className, Object source ) {
		// add to package map
		String [] sa = splitClassname( className );
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
	public Collection getClassesForPackage( String pack ) {
		return (Collection)packageMap.get( pack );
	}

	/**
		Return the source of the specified class
		
	*/
	public ClassSource getClassSource( String className ) {
		return (ClassSource)classSource.get( className );
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
				if ( isClassFileName( name ) ) {
					/* 
						Remove absolute (topdir) portion of path and leave 
						package-class part 
					*/
					if ( name.startsWith( top ) )
						name = name.substring( top.length()+1 );
					else
						throw new IOException( "problem parsing paths" );

					name = canonicalizeClassName(name);
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
			if ( isClassFileName( name ) )
				v.addElement( canonicalizeClassName(name) );
		}
		zin.close();

		String [] sa = new String [v.size()];
		v.copyInto(sa);
		return sa;
	}

	public static boolean isClassFileName( String name ){
		return ( name.toLowerCase().endsWith(".class") 
			&& (name.indexOf('$')==-1) );
	}

	public static boolean isArchiveFileName( String name ){
		name = name.toLowerCase();
		return ( name.endsWith(".jar") || name.endsWith(".zip") );
	}

	/**
		turn / into .,  remove leading class and trailing .class
	*/
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


	public static class ClassSource { 
		Object source;
	}
	public static class JarClassSource extends ClassSource { 
		JarClassSource( URL url ) { source = url; }
		public URL getURL() { return (URL)source; }
		public String toString() { return "Jar: "+source; }
	}
	public static class DirClassSource extends ClassSource { 
		DirClassSource( File dir ) { source = dir; }
		public File getDir() { return (File)source; }
		public String toString() { return "Dir: "+source; }
	}

	public static void main( String [] args ) throws Exception {
		URL [] urls = new URL [ args.length ];
		for(int i=0; i< args.length; i++)
			urls[i] =  new File(args[i]).toURL();
		BshClassPath bcp = new BshClassPath( urls );
	}

}
