package bsh;

import java.util.*;
import java.util.zip.*;
import java.io.*;
import java.net.*;
import java.io.File;

/**
	Maps all classes in a specified set of URLs which may include:
		jar/zip files and base dirs

	Note: No classpath traversal is done unless/util a call is made to 
		getClassesForPackage() or getClassSource()
*/
public class BshClassPath 
{
	/** Set of all classes in a package */
	Map packageMap = new HashMap();

	/** Map of source (URL or File dir) of every clas */
	Map classSource = new HashMap();

	/**  Do lazy initialization of the maps... */
	boolean mapsInitialized = false;
	List path = new ArrayList();

	public BshClassPath( URL [] urls ) {
		add( urls );
	}
	public BshClassPath() { }

	public URL [] getPathComponents() {
		return (URL[])path.toArray( new URL[0] );
	}

	/*
	public BshClassPath( BshClassPath bcp ) {
		path = bcp.path;
	}
	*/

	public void add( URL [] urls ) { 
		path.addAll( Arrays.asList(urls) );
		if ( mapsInitialized )
			map( urls );
	}

	public void add( URL url ) throws IOException { 
		path.add(url);
		if ( mapsInitialized )
			map( url );
	}

	/**
		Return the set of classes in the specified package
	*/
	public Collection getClassesForPackage( String pack ) {
		insureInitialized();
		return (Collection)packageMap.get( pack );
	}

	/**
		Return the source of the specified class
		
	*/
	public ClassSource getClassSource( String className ) {
		insureInitialized();
		return (ClassSource)classSource.get( className );
	}

	/**
		If the claspath map is not initialized, do it.
		
		Should this be "insure" or "ensure".  I know I've seen "ensure" used
		in the JDK source.  Here's what Webster has to say:

		Main Entry:ensure
		Pronunciation:in-'shur
		Function:transitive verb
		Inflected Form(s):ensured; ensuring
		: to make sure,	certain, or safe : GUARANTEE
		synonyms ENSURE, INSURE, ASSURE, SECURE	mean to	make a thing or	person
		sure. ENSURE, INSURE, and ASSURE are interchangeable in	many contexts
		where they indicate the	making certain or inevitable of	an outcome, but
		INSURE sometimes stresses the taking of	necessary measures beforehand,
		and ASSURE distinctively implies the removal of	doubt and suspense from
		a person's mind. SECURE	implies	action taken to	guard against attack or
		loss.
	*/
	public void insureInitialized() {
		if ( !mapsInitialized )
			map( getPathComponents() );
		mapsInitialized = true;
	}

	public boolean isInitialized() {
		return mapsInitialized;
	}

	void map( URL [] urls ) { 
		for(int i=0; i< urls.length; i++)
			try{
				map( urls[i] );
			} catch ( IOException e ) {
				System.err.println("Error constructing classpath: "
					+urls[i]+": "+e );
			}
	}

	void map( URL url ) throws IOException { 
		System.out.println("Mapping class path: "+url);

		String name = url.getFile();
		File f = new File( name );

		if ( f.isDirectory() )
			map( traverseDirForClasses( f ), new DirClassSource(f) );
		else if ( isArchiveFileName( name ) )
			map( searchJarForClasses( url ), new JarClassSource(url) );
		/*
		else if ( isClassFileName( name ) )
			map( looseClass( name ), url );
		*/
		else
			System.out.println("Not a classpath component: "+ name );
	}

	private void map( String [] classes, Object source ) {
		for(int i=0; i< classes.length; i++) {
			System.out.println( classes[i] +": "+ source );
			mapClass( classes[i], source );
		}
	}

	private void mapClass( String className, Object source ) {
		// add to package map
		String [] sa = splitClassname( className );
		String pack = sa[0];
		String clas = sa[1];
		Set set = (Set)packageMap.get( pack );
		if ( set == null ) {
			set = new HashSet();
			packageMap.put( pack, set );
		}
		set.add( className );

		// Add to classSource map
		Object obj = classSource.get( className );
		// don't replace already parsed (earlier in classpath)
		if ( obj == null )
			classSource.put( className, source );
	}


	static String [] traverseDirForClasses( File dir ) 
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
		Create a proper class name from a messy thing.
		Turn / or \ into .,  remove leading class and trailing .class

		Note: this makes lots of strings... could be faster.
	*/
	public static String canonicalizeClassName( String name ) {
		String classname=name.replace('/', '.');
		classname=name.replace('\\', '.');
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
	static URL [] userClassPathComp;
	public static URL [] getUserClassPathComponents() 
	{
		if ( userClassPathComp != null )
			return userClassPathComp;

		String cp=System.getProperty("java.class.path");
		String [] paths=StringUtil.split(cp, File.pathSeparator);

		URL [] urls = new URL[ paths.length ];
		try {
			for ( int i=0; i<paths.length; i++)
				urls[i] = new File( paths[i] ).toURL();
		} catch ( MalformedURLException e ) {
			throw new InterpreterError("can't parse class path: "+e);
		}

		userClassPathComp = urls;
		return urls;
	}

	static BshClassPath userClassPath;
	/**
		A BshClassPath initialized to the user path
		from java.class.path
	*/
	public static BshClassPath getUserClassPath() 
	{
		if ( userClassPath == null )
			userClassPath = new BshClassPath( getUserClassPathComponents() );
		return userClassPath;
	}

	static BshClassPath bootClassPath;
	/**
		Get the boot path including the lib/rt.jar if possible.
	*/
	public static BshClassPath getBootClassPath() 
		throws ClassPathException
	{
		if ( bootClassPath == null )
			try {
				String rtjar = System.getProperty("java.home")+"/lib/rt.jar";
				URL url = new File( rtjar ).toURL();
				bootClassPath = new BshClassPath( new URL[] { url } );
			} catch ( MalformedURLException e ) {
				throw new ClassPathException(" can't find boot jar: "+e);
			}
		return bootClassPath;
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


	public String toString() {
		return "BshClassPath: "+path;
	}
}
