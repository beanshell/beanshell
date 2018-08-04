/*****************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one                *
 * or more contributor license agreements.  See the NOTICE file              *
 * distributed with this work for additional information                     *
 * regarding copyright ownership.  The ASF licenses this file                *
 * to you under the Apache License, Version 2.0 (the                         *
 * "License"); you may not use this file except in compliance                *
 * with the License.  You may obtain a copy of the License at                *
 *                                                                           *
 *     http://www.apache.org/licenses/LICENSE-2.0                            *
 *                                                                           *
 * Unless required by applicable law or agreed to in writing,                *
 * software distributed under the License is distributed on an               *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY                    *
 * KIND, either express or implied.  See the License for the                 *
 * specific language governing permissions and limitations                   *
 * under the License.                                                        *
 *                                                                           *
 *                                                                           *
 * This file is part of the BeanShell Java Scripting distribution.           *
 * Documentation and updates may be found at http://www.beanshell.org/       *
 * Patrick Niemeyer (pat@pat.net)                                            *
 * Author of Learning Java, O'Reilly & Associates                            *
 *                                                                           *
 *****************************************************************************/
package bsh.classpath;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import bsh.ClassPathException;
import bsh.NameSource;

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
    Feedback on mapping progress is provided through the MappingFeedback
    interface.

    Design notes:
    Several times here we traverse ourselves and our component paths to
    produce a composite view of some thing relating to the path.  This would
    be an opportunity for a visitor pattern.
*/
public class BshClassPath
    implements ClassPathListener, NameSource
{
    String name;

    /** The URL path components */
    private List path;
    /** Ordered list of components BshClassPaths */
    private List compPaths;

    /** Set of classes in a package mapped by package name */
    private Map packageMap;
    /** Map of source (URL or File dir) of every clas */
    private Map classSource;
    /**  The packageMap and classSource maps have been built. */
    private boolean mapsInitialized;

    private UnqualifiedNameTable unqNameTable;

    /**
        This used to be configurable, but now we always include them.
    */
    private boolean nameCompletionIncludesUnqNames = true;

    Vector<WeakReference<ClassPathListener>> listeners = new Vector<>();

    // constructors

    public BshClassPath( String name ) {
        this.name = name;
        reset();
    }

    public BshClassPath(  String name, URL [] urls ) {
        this( name );
        add( urls );
    }

    // end constructors

    // mutators

    public void setPath( URL[] urls ) {
        reset();
        add( urls );
    }

    /**
        Add the specified BshClassPath as a component of our path.
        Changes in the bcp will be reflected through us.
    */
    public void addComponent( BshClassPath bcp ) {
        if (bcp == null)
            return;
        if ( compPaths == null )
            compPaths = new ArrayList();
        compPaths.add( bcp );
        bcp.addListener( this );
    }

    public void add( URL [] urls ) {
        path.addAll( Arrays.asList(urls) );
        synchronized (this) {
            if ( mapsInitialized )
                map( urls );
        }
    }

    public void add( URL url ) throws IOException {
        path.add(url);
        synchronized (this) {
            if ( mapsInitialized )
                map( url );
        }
    }

    /**
        Get the path components including any component paths.
    */
    public URL [] getPathComponents() {
        return (URL[])getFullPath().toArray( new URL[0] );
    }

    /**
        Return the set of class names in the specified package
        including all component paths.
    */
    synchronized public Set getClassesForPackage( String pack ) {
        insureInitialized();
        Set set = new HashSet();
        Collection c = (Collection)packageMap.get( pack );
        if ( c != null )
            set.addAll( c );

        if ( compPaths != null )
            for (int i=0; i<compPaths.size(); i++) {
                c = ((BshClassPath)compPaths.get(i)).getClassesForPackage(
                    pack );
                if ( c != null )
                    set.addAll( c );
            }
        return set;
    }

    /**
        Return the source of the specified class which may lie in component
        path.
    */
    synchronized public ClassSource getClassSource( String className )
    {
        // Before triggering classpath mapping (initialization) check for
        // explicitly set class sources (e.g. generated classes).  These would
        // take priority over any found in the classpath anyway.
        ClassSource cs = (ClassSource)classSource.get( className );
        if ( cs != null )
            return cs;

        insureInitialized(); // trigger possible mapping

        cs = (ClassSource)classSource.get( className );
        if ( cs == null && compPaths != null )
            for (int i=0; i<compPaths.size() && cs==null; i++)
                cs = ((BshClassPath)compPaths.get(i)).getClassSource(className);
        return cs;
    }

    /**
        Explicitly set a class source.  This is used for generated classes, but
        could potentially be used to allow a user to override which version of
        a class from the classpath is located.
    */
    synchronized public void setClassSource( String className, ClassSource cs )
    {
        classSource.put( className, cs );
    }

    /**
        If the claspath map is not initialized, do it now.
        If component maps are not do them as well...

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
    public void insureInitialized()
    {
        insureInitialized( true );
    }

    /**
        @param topPath indicates that this is the top level classpath
        component and it should send the startClassMapping message
    */
    protected synchronized void insureInitialized( boolean topPath )
    {
        // If we are the top path and haven't been initialized before
        // inform the listeners we are going to do expensive map
        if ( topPath && !mapsInitialized )
            startClassMapping();

        // initialize components
        if ( compPaths != null )
            for (int i=0; i< compPaths.size(); i++)
                ((BshClassPath)compPaths.get(i)).insureInitialized( false );

        // initialize ourself
        if ( !mapsInitialized )
            map( (URL[])path.toArray( new URL[0] ) );

        if ( topPath && !mapsInitialized )
            endClassMapping();

        mapsInitialized = true;
    }

    /**
        Get the full path including component paths.
        (component paths listed first, in order)
        Duplicate path components are removed.
    */
    protected List getFullPath()
    {
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
        insureInitialized();
        UnqualifiedNameTable unqNameTable = getUnqualifiedNameTable();

        Object obj = unqNameTable.get( name );
        if ( obj instanceof AmbiguousName )
            throw new ClassPathException("Ambigous class names: "+
                ((AmbiguousName)obj).get() );

        return (String)obj;
    }

    /*
        Note: we could probably do away with the unqualified name table
        in favor of a second name source
    */
    private UnqualifiedNameTable getUnqualifiedNameTable() {
        if ( unqNameTable == null )
            unqNameTable = buildUnqualifiedNameTable();
        return unqNameTable;
    }

    private UnqualifiedNameTable buildUnqualifiedNameTable()
    {
        UnqualifiedNameTable unqNameTable = new UnqualifiedNameTable();

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

        return unqNameTable;
    }

    public String [] getAllNames()
    {
        insureInitialized();

        List names = new ArrayList();
        Iterator it = getPackagesSet().iterator();
        while( it.hasNext() ) {
            String pack = (String)it.next();
            names.addAll(
                removeInnerClassNames( getClassesForPackage( pack ) ) );
        }

        if ( nameCompletionIncludesUnqNames )
            names.addAll( getUnqualifiedNameTable().keySet() );

        return (String [])names.toArray(new String[0]);
    }

    /**
        call map(url) for each url in the array
    */
    synchronized void map( URL [] urls )
    {
        for(int i=0; i< urls.length; i++)
            try{
                map( urls[i] );
            } catch ( IOException e ) {
                String s = "Error constructing classpath: " +urls[i]+": "+e;
                errorWhileMapping( s );
            }
    }

    synchronized void map( URL url )
        throws IOException
    {
        if ("jrt".equals(url.getProtocol())) {
            classMapping("FileSystem: "+url );
            map( searchJrtFSForClasses( url ), new JrtClassSource(url) );
        } else  if ("jar".equals(url.getProtocol())) {
            classMapping("FileSystem: "+url );
            map( searchJarFSForClasses( url ), new JarClassSource(url) );
        } else {
            String name = url.getFile();
            File f = new File( name );

            if ( f.isDirectory() ) {
                classMapping( "Directory "+ f.toString() );
                map( traverseDirForClasses( f ), new DirClassSource(f) );
            } else if ( isArchiveFileName( name ) ) {
                classMapping("Archive: "+url );
                map( searchArchiveForClasses( url ), new JarClassSource(url) );
            } else {
                String s = "Not a classpath component: "+ name ;
                errorWhileMapping( s );
            }
        }
    }

    private void map( String [] classes, Object source ) {
        for(int i=0; i< classes.length; i++) {
            //System.out.println( classes[i] +": "+ source );
            mapClass( classes[i], source );
        }
    }

    private void mapClass( String className, Object source )
    {
        // add to package map
        String [] sa = splitClassname( className );
        String pack = sa[0];
        Set set = (Set)packageMap.get( pack );
        if ( set == null ) {
            set = new HashSet();
            packageMap.put( pack, set );
        }
        set.add( className );

        // Add to classSource map
        Object obj = classSource.get( className );
        // don't replace previously set (found earlier in classpath or
        // explicitly set via setClassSource() )
        if ( obj == null )
            classSource.put( className, source );
    }

    /**
        Clear everything and reset the path to empty.
    */
    synchronized private void reset() {
        path = new ArrayList();
        compPaths = null;
        clearCachedStructures();
    }

    /**
        Clear anything cached.  All will be reconstructed as necessary.
    */
    synchronized private void clearCachedStructures() {
        mapsInitialized = false;
        packageMap = new HashMap();
        classSource = new HashMap();
        unqNameTable = null;
        nameSpaceChanged();
    }

    public void classPathChanged() {
        clearCachedStructures();
        notifyListeners();
    }

/*
    public void setNameCompletionIncludeUnqNames( boolean b ) {
        if ( nameCompletionIncludesUnqNames != b ) {
            nameCompletionIncludesUnqNames = b;
            nameSpaceChanged();
        }
    }
*/

    // Begin Static stuff

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
        if ( null == children )
            children = new File[0];
        for (int i=0; i< children.length; i++)  {
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

    /** Search jrt file system for module classes.
     * @param the jrt: file system url
     * @return array of class names found
     * @throws IOException */
    static String [] searchJrtFSForClasses( URL url ) throws IOException {
        try {
            Path path = FileSystems.getFileSystem(new URI("jrt:/")).getPath("modules", url.getPath());

            return Files.walk(path).map(Path::toString)
                    .filter(BshClassPath::isClassFileName)
                    .map(BshClassPath::canonicalizeClassName)
                    .toArray(String[]::new);
        } catch (URISyntaxException e) { /* ignore */ }
        return new String[0];
    }

    /** Search jar file system for classes.
     * @param the jar: file system url
     * @return array of class names found
     * @throws IOException */
    static String [] searchJarFSForClasses( URL url ) throws IOException {
        try {
            try {
                FileSystems.newFileSystem(url.toURI(), new HashMap<>());
            } catch (FileSystemAlreadyExistsException e) { /* ignore */ }

            Path path = FileSystems.getFileSystem(url.toURI()).getPath("/");

            return Files.walk(path).map(Path::toString)
                    .filter(BshClassPath::isClassFileName)
                    .map(BshClassPath::canonicalizeClassName)
                    .toArray(String[]::new);
        } catch (URISyntaxException e) { /* ignore */ }
        return new String[0];
    }

    /** Search Archive for classes.
     * @param the archive file location
     * @return array of class names found
     * @throws IOException */
    static String [] searchArchiveForClasses( URL url ) throws IOException {
        List<String> list = new ArrayList<>();
        ZipInputStream zip = new ZipInputStream(url.openStream());

        ZipEntry ze;
        while( zip.available() == 1 )
            if ( (ze = zip.getNextEntry()) != null
                    && isClassFileName( ze.getName() ) )
                list.add( canonicalizeClassName( ze.getName() ) );
        zip.close();

        return list.toArray( new String[list.size()] );
    }

    public static boolean isClassFileName( String name ){
        return name.toLowerCase().endsWith(".class");
            //&& (name.indexOf('$')==-1) );
    }

    public static boolean isArchiveFileName( String name ){
        name = name.toLowerCase();
        return name.endsWith(".jar") || name.endsWith(".zip") || name.endsWith(".jmod");
    }

    /**
        Create a proper class name from a messy thing.
        Turn / or \ into .,  remove leading class and trailing .class

        Note: this makes lots of strings... could be faster.
    */
    public static String canonicalizeClassName( String name )
    {
        String classname = name;
        if ( classname.startsWith("modules/"))
            classname = classname.replaceFirst("^modules/[^/]+/", "");
        classname = classname.replaceAll("[/\\\\]", ".");
        if ( classname.startsWith(".") )
            classname = classname.substring(1);
        if ( classname.startsWith("class ") )
            classname = classname.substring(6);
        if ( classname.startsWith("classes.") )
            classname = classname.substring(8);
        if ( classname.endsWith(".class") )
            classname = classname.replaceFirst("\\.[^\\.]+$", "");
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
        Return a new collection without any inner class names
    */
    public static Collection removeInnerClassNames( Collection col ) {
        List list = new ArrayList();
        list.addAll(col);
        Iterator it = list.iterator();
        while(it.hasNext()) {
            String name =(String)it.next();
            if (name.indexOf("$") != -1 )
                it.remove();
        }
        return list;
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
        String [] paths = cp.split(File.pathSeparator);

        URL [] urls = new URL[ paths.length ];
        try {
            for ( int i=0; i<paths.length; i++)
                // We take care to get the canonical path first.
                // Java deals with relative paths for it's bootstrap loader
                // but JARClassLoader doesn't.
                urls[i] = new File(
                    new File(paths[i]).getCanonicalPath() ).toURI().toURL();
        } catch ( IOException e ) {
            throw new ClassPathException("can't parse class path: "+e, e);
        }

        userClassPathComp = urls;
        return urls;
    }

    /**
        Get a list of all of the known packages
    */
    public Set getPackagesSet()
    {
        insureInitialized();
        Set set = new HashSet();
        set.addAll( packageMap.keySet() );

        if ( compPaths != null )
            for (int i=0; i<compPaths.size(); i++)
                set.addAll(
                    ((BshClassPath)compPaths.get(i)).packageMap.keySet() );
        return set;
    }

    public void addListener( ClassPathListener l ) {
        listeners.addElement( new WeakReference<ClassPathListener>(l) );
    }
    public void removeListener( ClassPathListener l ) {
        for ( Iterator<WeakReference<ClassPathListener>> it = listeners.iterator() ;
                it.hasNext() ; )
            if (it.next().get() == l)
                it.remove();
    }
    void notifyListeners() {
        ClassPathListener l;
        for ( Iterator<WeakReference<ClassPathListener>> it = listeners.iterator() ;
                it.hasNext() ; )
            if ((l = it.next().get()) == null)
                it.remove();
            else
                l.classPathChanged();
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
        {
            try
            {
                bootClassPath = new BshClassPath(
                        "Boot Class Path", new URL[] { getRTJarPath() } );
            } catch ( MalformedURLException e ) {
                throw new ClassPathException(" can't find boot jar: "+e, e);
            }
        }
        return bootClassPath;
    }


    private static URL getRTJarPath() throws MalformedURLException
    {
        String urlString =
            Class.class.getResource("/java/lang/String.class").toExternalForm();

        if ( urlString.startsWith("jrt:/") )
            return new URL(urlString.substring(0, urlString.indexOf('/', 5)));

        return new URL(urlString.replaceFirst("[^!]*$", "/"));
    }

    public abstract static class ClassSource {
        Object source;
        abstract byte [] getCode( String className );
    }

    public static class JarClassSource extends ClassSource {
        JarClassSource( URL url ) { source = url; }
        public URL getURL() { return (URL)source; }
        public byte [] getCode( String className ) {
            String n = '/' + className.replace( '.', '/' ) + ".class";
            try (DataInputStream in = new DataInputStream(
                    this.getClass().getResourceAsStream(n))) {
                byte[] bytes = new byte[in.available()];
                in.readFully(bytes);
                return bytes;
            } catch (IOException e) { /* ignore */ }
            return new byte[0];
        }
        public String toString() { return "Jar: "+source; }
    }

    public static class DirClassSource extends ClassSource
    {
        DirClassSource( File dir ) { source = dir; }
        public File getDir() { return (File)source; }
        public String toString() { return "Dir: "+source; }

        public byte [] getCode( String className ) {
            return readBytesFromFile( getDir(), className );
        }

        public static byte [] readBytesFromFile( File base, String className )
        {
            String n = className.replace( '.', File.separatorChar ) + ".class";
            File file = new File( base, n );

            if ( !file.exists() )
                return null;

            byte [] bytes;
            try ( FileInputStream fis = new FileInputStream(file);
                    DataInputStream dis = new DataInputStream( fis ) ) {

                bytes = new byte [ (int)file.length() ];

                dis.readFully( bytes );
                dis.close();
            } catch(IOException ie ) {
                throw new RuntimeException("Couldn't load file: "+file, ie);
            }

            return bytes;
        }

    }

    public static class JrtClassSource extends ClassSource {

        JrtClassSource( URL url ) { source = url; }

        public URL getURL() { return (URL) source; }

        public byte [] getCode( String className ) {
            String n = '/' + className.replace( '.', '/' ) + ".class";
            try (DataInputStream in = new DataInputStream(
                    (InputStream) new URL(source + n).getContent())) {
                byte[] bytes = new byte[in.available()];
                in.readFully(bytes);
                return bytes;
            } catch (IOException e) { /* ignore */ }
            return new byte[0];
        }

        public String toString() { return "Jrt: "+source; }
    }

    public static class GeneratedClassSource extends ClassSource
    {
        GeneratedClassSource( byte [] bytecode ) { source = bytecode; }
        public byte [] getCode( String className ) {
            return (byte [])source;
        }
    }

    public String toString() {
        return "BshClassPath "+name+"("+super.toString()+") path= "+path +"\n"
            + "compPaths = {" + compPaths +" }";
    }


    /*
        Note: we could probably do away with the unqualified name table
        in favor of a second name source
    */
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

    /**
        Fire the NameSourceListeners
    */
    void nameSpaceChanged()
    {
        if ( nameSourceListeners == null )
            return;

        for(int i=0; i<nameSourceListeners.size(); i++)
            ((NameSource.Listener)(nameSourceListeners.get(i)))
                .nameSourceChanged( this );
    }

    List nameSourceListeners;
    /**
        Implements NameSource
        Add a listener who is notified upon changes to names in this space.
    */
    public void addNameSourceListener( NameSource.Listener listener ) {
        if ( nameSourceListeners == null )
            nameSourceListeners = new ArrayList();
        nameSourceListeners.add( listener );
    }

    /** only allow one for now */
    static MappingFeedback mappingFeedbackListener;

    /**
    */
    public static void addMappingFeedback( MappingFeedback mf )
    {
        if ( mappingFeedbackListener != null )
            throw new RuntimeException("Unimplemented: already a listener");
        mappingFeedbackListener = mf;
    }

    void startClassMapping() {
        if ( mappingFeedbackListener != null )
            mappingFeedbackListener.startClassMapping();
        else
            System.err.println( "Start ClassPath Mapping" );
    }

    void classMapping( String msg ) {
        if ( mappingFeedbackListener != null ) {
            mappingFeedbackListener.classMapping( msg );
        } else
            System.err.println( "Mapping: "+msg );
    }

    void errorWhileMapping( String s ) {
        if ( mappingFeedbackListener != null )
            mappingFeedbackListener.errorWhileMapping( s );
        else
            System.err.println( s );
    }

    void endClassMapping() {
        if ( mappingFeedbackListener != null )
            mappingFeedbackListener.endClassMapping();
        else
            System.err.println( "End ClassPath Mapping" );
    }

    static interface MappingFeedback
    {
        void startClassMapping();

        /**
            Provide feedback on the progress of mapping the classpath
            @param msg is a message about the path component being mapped
            @perc is an integer in the range 0-100 indicating percentage done
        public void classMapping( String msg, int perc );
        */

        /**
            Provide feedback on the progress of mapping the classpath
        */
        void classMapping( String msg );

        void errorWhileMapping( String msg );

        void endClassMapping();
    }

}
