package bsh.classpath;

import java.util.*;
import java.util.zip.*;
import java.io.*;
import java.net.*;
import java.io.File;
import bsh.Interpreter;
import bsh.StringUtil;
import bsh.ClassPathException;

/**
	A BshClassPath encapsulates knowledge about a class path of URLs.
	It can maps all classes the path which may include:
		jar/zip files and base dirs

	A BshClassPath may composite other BshClassPaths as components of its
	path and will reflect changes in those components through its methods
	and listener interface.

	Classpath traversal is done lazily when a call is made to 
		getClassesForPackage() or getClassSource()
	or can be done explicitily through insureInitialized().
*/
public class BshClassPath 
{
	String name;

	/** Set of all classes in a package */
	Map packageMap;

	/** Map of source (URL or File dir) of every clas */
	Map classSource;

	/**  Do lazy initialization of the maps... */
	boolean mapsInitialized;

	/** The URL path components */
	private List path;

	/** Ordered list of components BshClassPaths */
	private List compPaths;

	// constructors

	public BshClassPath( String name ) { 
		this.name = name;
		reset();
	}

	public BshClassPath(  String name, URL [] urls ) {
		this( name );
		add( urls, null );
	}

	// end constructors

	// mutators

	public void setPath( URL[] urls ) {
		reset();
		add( urls, null );
	}

	/**
		Add the specified BshClassPath as a component of our path.
		Changes in the bcp will be reflected through us.
	*/
	public void addComponent( BshClassPath bcp ) { 
		if ( compPaths == null )
			compPaths = new ArrayList();
		compPaths.add( bcp );
	}

	public void add( URL [] urls, Interpreter feedback  ) { 
		path.addAll( Arrays.asList(urls) );
		if ( mapsInitialized )
			map( urls, feedback );
	}

	public void add( URL url, Interpreter feedback  ) throws IOException { 
		path.add(url);
		if ( mapsInitialized )
			map( url, feedback );
	}

	/**
		Get the path components including any component paths.
	*/
	public URL [] getPathComponents() {
		return (URL[])getFullPath().toArray( new URL[0] );
	}

	/**
		Return the set of classes in the specified package
	*/
	public Collection getClassesForPackage( String pack ) {
		insureInitialized( null );
		Collection col = (Collection)packageMap.get( pack );
		if ( col == null && compPaths != null )
			for (int i=0; i<compPaths.size() && col==null; i++)
				col = ((BshClassPath)compPaths.get(i)).getClassesForPackage( pack );
		return col;
	}

	/**
		Return the source of the specified class
	*/
	public ClassSource getClassSource( String className ) {
		insureInitialized( null );
		ClassSource cs = (ClassSource)classSource.get( className );
		if ( cs == null && compPaths != null )
			for (int i=0; i<compPaths.size() && cs==null; i++)
				cs = ((BshClassPath)compPaths.get(i)).getClassSource( className );
		return cs;
	}

	/**
		If the claspath map is not initialized, do it now.
		If component maps are not do them as well...

		If interpreter is non-null it will be used to print feedback to
		the user on the progress of mapping.
		
		Random note:
		Should this be "insure" or "ensure".  I know I've seen "ensure" used
		in the JDK source.  Here's what Webster has to say:

			Main Entry:ensure Pronunciation:in-'shur
			Function:transitive verb Inflected
			Form(s):ensured; ensuring : to make sure,
			certain, or safe : GUARANTEE synonyms ENSURE,
			INSURE, ASSURE, SECURE mean to make a thing or
			person sure. ENSURE, INSURE, and ASSURE are
			interchangeable in many contexts where they
			indicate the making certain or inevitable of an
			outcome, but INSURE sometimes stresses the
			taking of necessary measures beforehand, and
			ASSURE distinctively implies the removal of
			doubt and suspense from a person's mind. SECURE
			implies action taken to guard against attack or
			loss.
	*/
	public void insureInitialized( Interpreter feedback ) {
		if ( compPaths != null )
			for (int i=0; i< compPaths.size(); i++)
				((BshClassPath)compPaths.get(i)).insureInitialized( feedback );

		if ( !mapsInitialized )
			map( (URL[])path.toArray( new URL[0] ), feedback );

		mapsInitialized = true;
	}

	/**
		Get the full path including component paths.
		(component paths listed first, in order)
		Duplicate path components are removed.
	*/
	protected List getFullPath() {
		List list = new ArrayList();
		if ( compPaths != null ) {
			for (int i=0; i<compPaths.size(); i++) {
				List l = ((BshClassPath)compPaths.get(i)).getFullPath();
				// take care to remove dups
				// wish we had an ordered set collection
				Iterator it = l.iterator();
				while ( it.hasNext() ) {
					Object o = it.next();
					if ( !list.contains(o) )
						list.add( o );
				}
			}
		}
		list.addAll( path );
		return list;
	}


	/**
		Support for super import "*";
		Get the full name associated with the unqualified name in this 
		classpath.  Returns either the String name or an AmbiguousName object
		encapsulating the various names.
	*/
	public String getClassNameByUnqName( String name ) 
		throws ClassPathException
	{
		if ( unqNameTable == null )
			buildUnqualifiedNameTable();

		Object obj = unqNameTable.get( name );
		if ( obj instanceof AmbiguousName )
			throw new ClassPathException("Ambigous class names: "+
				((AmbiguousName)obj).get() );

		return (String)obj;
	}

	static UnqualifiedNameTable unqNameTable;
	private void buildUnqualifiedNameTable() {
		unqNameTable = new UnqualifiedNameTable();

		// add component names
		if ( compPaths != null )
			for (int i=0; i<compPaths.size(); i++) {
				Set s = ((BshClassPath)compPaths.get(i)).classSource.keySet();
				Iterator it = s.iterator();
				while(it.hasNext()) 
					unqNameTable.add( (String)it.next() );
			}

		// add ours
		Iterator it = classSource.keySet().iterator();
		while(it.hasNext()) 
			unqNameTable.add( (String)it.next() );
	}

	void map( URL [] urls, Interpreter feedback ) { 
		for(int i=0; i< urls.length; i++)
			try{
				map( urls[i], feedback );
			} catch ( IOException e ) {
				String s = "Error constructing classpath: " +urls[i]+": "+e;
				if ( feedback != null )
					feedback.println( s );
				else
					System.err.println(s );
			}
	}

	void map( URL url, Interpreter feedback ) throws IOException { 
		String name = url.getFile();
		File f = new File( name );

		if ( f.isDirectory() ) {
			if ( feedback != null )
				feedback.println("Mapping directory: "+f );
			map( traverseDirForClasses( f ), new DirClassSource(f) );
		} else if ( isArchiveFileName( name ) ) {
			if ( feedback != null )
				feedback.println("Mapping archive: "+url );
			map( searchJarForClasses( url ), new JarClassSource(url) );
		} 
		/*
		else if ( isClassFileName( name ) )
			map( looseClass( name ), url );
		*/
		else {
			String s = "Not a classpath component: "+ name ;
			if ( feedback != null )
				feedback.println( s );
			else
				System.err.println( s  );
		}
	}

	private void map( String [] classes, Object source ) {
		for(int i=0; i< classes.length; i++) {
			//System.out.println( classes[i] +": "+ source );
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

	private void reset() {
		packageMap = new HashMap();
		classSource = new HashMap();
		mapsInitialized = false;
		path = new ArrayList();
		compPaths = null;
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
		return ( name.toLowerCase().endsWith(".class") );
			//&& (name.indexOf('$')==-1) );
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
		classname=classname.replace('\\', '.');
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
		throws ClassPathException
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
			throw new ClassPathException("can't parse class path: "+e);
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
		throws ClassPathException
	{
		if ( userClassPath == null )
			userClassPath = new BshClassPath( 
				"User Class Path", getUserClassPathComponents() );
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
				bootClassPath = new BshClassPath( 
					"Boot Class Path", new URL[] { url } );
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
		BshClassPath bcp = new BshClassPath( "Test", urls );
	}

	public String toString() {
		return "BshClassPath "+name+"("+super.toString()+") path= "+path +"\n"
			+ "compPaths = {" + compPaths +" }";
	}


	static class UnqualifiedNameTable extends HashMap {
		void add( String fullname ) {
			String name = splitClassname( fullname )[1];
			Object have = super.get( name );

			if ( have == null )
				super.put( name, fullname );
			else
				if ( have instanceof AmbiguousName )
					((AmbiguousName)have).add( fullname );
				else  // String
				{
					AmbiguousName an = new AmbiguousName();
					an.add( (String)have );
					an.add( fullname );
					super.put( name, an );
				}
		}
	}

	public static class AmbiguousName {
		List list = new ArrayList();
		public void add( String name ) { 
			list.add( name ); 
		}
		public List get() {
			//return (String[])list.toArray(new String[0]);
			return list;
		}
	}

}
