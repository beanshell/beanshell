/*****************************************************************************
 *                                                                           *
 *  This file is part of the BeanShell Java Scripting distribution.          *
 *  Documentation and updates may be found at http://www.beanshell.org/      *
 *                                                                           *
 *  Sun Public License Notice:                                               *
 *                                                                           *
 *  The contents of this file are subject to the Sun Public License Version  *
 *  1.0 (the "License"); you may not use this file except in compliance with *
 *  the License. A copy of the License is available at http://www.sun.com    * 
 *                                                                           *
 *  The Original Code is BeanShell. The Initial Developer of the Original    *
 *  Code is Pat Niemeyer. Portions created by Pat Niemeyer are Copyright     *
 *  (C) 2000.  All Rights Reserved.                                          *
 *                                                                           *
 *  GNU Public License Notice:                                               *
 *                                                                           *
 *  Alternatively, the contents of this file may be used under the terms of  *
 *  the GNU Lesser General Public License (the "LGPL"), in which case the    *
 *  provisions of LGPL are applicable instead of those above. If you wish to *
 *  allow use of your version of this file only under the  terms of the LGPL *
 *  and not to allow others to use your version of this file under the SPL,  *
 *  indicate your decision by deleting the provisions above and replace      *
 *  them with the notice and other provisions required by the LGPL.  If you  *
 *  do not delete the provisions above, a recipient may use your version of  *
 *  this file under either the SPL or the LGPL.                              *
 *                                                                           *
 *  Patrick Niemeyer (pat@pat.net)                                           *
 *  Author of Learning Java, O'Reilly & Associates                           *
 *  http://www.pat.net/~pat/                                                 *
 *                                                                           *
 *****************************************************************************/


package	bsh;

import java.util.*;

import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

/**
    A namespace	in which methods, variables, and imports (class names) live.  
	This is package public because it is used in the implementation of some 
	bsh commands.  However for normal use you should be using methods on 
	bsh.Interpreter to interact with your scripts.
	<p>

	A bsh.This object is a thin layer over a NameSpace that associates it with
	an Interpreter instance.  Together they comprise a Bsh scripted object 
	context.
	<p>

	Note: I'd really like to use collections here, but we have to keep this
	compatible with JDK1.1 
*/
/*
	Thanks to Slava Pestov (of jEdit fame) for import caching enhancements.
	Note: This class has gotten too big.  It should be broken down a bit.
*/
public class NameSpace 
	implements java.io.Serializable, BshClassManager.Listener, 
	NameSource
{
	public static final NameSpace JAVACODE = 
		new NameSpace((BshClassManager)null, "Called from compiled Java code.");
	static {
		JAVACODE.isMethod = true;
	}

	// Begin instance data
	// Note: if we add something here we should reset it in the clear() method.

	public String name; 
    private NameSpace parent;
    private Hashtable variables;
    private Hashtable methods;
    private Hashtable importedClasses;
    private Vector importedPackages;
	transient private BshClassManager classManager;

	// See notes in getThis()
    private This thisReference;

	/** Name resolver objects */
    private Hashtable names;

	/** The node associated with the creation of this namespace.
		This is used support getInvocationLine() and getInvocationText(). */
	SimpleNode callerInfoNode;

	/** 
		Note that the namespace is a method body namespace.  This is used for
		printing stack traces in exceptions.  
	*/
	public boolean isMethod;

	/**
		Local class cache for classes resolved through this namespace using 
		getClass() (taking into account imports).  Only unqualified class names
		are cached here (those which might be imported).  Qualified names are 
		always absolute and are cached by BshClassManager.
	*/
    transient private Hashtable classCache;

	// End instance data

	// Begin constructors

	/**
		@parent the parent namespace of this namespace.  Child namespaces
		inherit all variables and methods of their parent and can (of course)
		override / shadow them.
	*/
    public NameSpace( NameSpace parent, String name ) 
	{
		// Note: in this case parent must have a class manager.
		this( parent, null, name );
	}

    public NameSpace( BshClassManager classManager, String name ) 
	{
		this( null, classManager, name );
	}

    public NameSpace( 
		NameSpace parent, BshClassManager classManager, String name ) 
	{
		// We might want to do this here rather than explicitly in Interpreter
		// for global (see also prune())
		//if ( classManager == null && (parent == null ) )
			// create our own class manager?

		setName(name);
		setParent(parent);
		setClassManager( classManager );

		// Register for notification of classloader change
		if ( classManager != null )
			classManager.addListener(this);
    }

	// End constructors

	public void setName( String name ) {
		this.name = name;
	}
	public String getName() {
		return this.name;
	}

	/**
		Set the node associated with the creation of this namespace.
		This is used in debugging and to support the getInvocationLine()
		and getInvocationText() methods.
	*/
	void setNode( SimpleNode node ) {
		this.callerInfoNode= node;
	}

	SimpleNode getNode() {
		return this.callerInfoNode;
	}

	/**
		Resolve name to an object through this namespace.
	*/
	public Object get( String name, Interpreter interpreter ) 
		throws UtilEvalError 
	{
		CallStack callstack = new CallStack( this );
		return getNameResolver( name ).toObject( callstack, interpreter );
	}

	/**
		@deprecated  Use the form specifying strict java.  This method 
			assumes strict java is false.
		@see #setVariable( String, Object, boolean );
	*/
    public void	setVariable( String name, Object value ) 
		throws UtilEvalError 
	{
		setVariable( name, value, false );
	}

	/**
		Set a variable in this namespace.
		<p>
		Note: this method is primarily intended for use internally.  If you use
		this method outside of the bsh package and wish to set variables with
		primitive values you will have to wrap them using bsh.Primitive.
		@see bsh.Primitive
		<p>
		Setting a new variable (which didn't exist before) or removing
		a variable causes a namespace change.

		@param value a value of null will remove the variable definition.
		@param strictJava specifies whether strict java rules are applied.
	*/
    public void	setVariable( String name, Object value, boolean strictJava ) 
		throws UtilEvalError 
	{
		if ( variables == null )
			variables =	new Hashtable();

		// hack... should factor this out...
		if ( value == null ) {
			variables.remove(name);
			nameSpaceChanged();
			return;
		}

		// Locate the variable definition if it exists
		// if strictJava then recurse, else default local scope
		boolean recurse = strictJava;
		Object orig = getVariableImpl( name, recurse );

		// Found a typed variable
		if ( (orig != null) && (orig instanceof TypedVariable) )
		{
			try {
				((TypedVariable)orig).setValue( value );
			} catch ( UtilEvalError e ) {
				throw new UtilEvalError(
					"Typed variable: " + name + ": " + e.getMessage());
			} 
		} else
			// Untyped or non-existent.  
			// (Allow assignment to existing untyped var even with strictJava)
			if ( strictJava && orig == null )
				throw new UtilEvalError(
					"(Strict Java mode) Assignment to undeclared variable: "
					+name );
			else
				variables.put(name, value);

		if ( orig == null )
			nameSpaceChanged();
    }

	/**
		Get the names of variables defined in this namespace.
		(This does not show variables in parent namespaces).
	*/
	public String [] getVariableNames() {
		if ( variables == null )
			return new String [0];
		else
			return enumerationToStringArray( variables.keys() );
	}

	/**
		Get the names of methods defined in this namespace.
		(This does not show methods in parent namespaces).
	*/
	public String [] getMethodNames() {
		if ( methods == null )
			return new String [0];
		else
			return enumerationToStringArray( methods.keys() );
	}

	/**
		Get the methods defined in this namespace.
		(This does not show methods in parent namespaces).
	*/
	public BshMethod [] getMethods() {
		if ( methods == null )
			return new BshMethod [0];
		else
			return flattenMethodCollection( methods.elements() );
	}

	private String [] enumerationToStringArray( Enumeration e ) {
		Vector v = new Vector();
		while ( e.hasMoreElements() )
			v.addElement( e.nextElement() );
		String [] sa = new String [ v.size() ];
		v.copyInto( sa );
		return sa;
	}

	/**
		Support for friendly getMethods();
	*/
    private BshMethod [] flattenMethodCollection( Enumeration e ) {
        Vector v = new Vector();
        while ( e.hasMoreElements() ) {
            Object o = e.nextElement();
            if ( o instanceof BshMethod )
                v.addElement( o );
            else {
                Vector ov = (Vector)o;
                for(int i=0; i<ov.size(); i++)
                    v.addElement( ov.elementAt( i ) );
            }
        }
        BshMethod [] bma = new BshMethod [ v.size() ];
        v.copyInto( bma );
        return bma;
    }

	/**
		Get the parent namespace.
		Note: this isn't quite the same as getSuper().
		getSuper() returns 'this' if we are at the root namespace.
	*/
	public NameSpace getParent() {
		return parent;
	}

    public NameSpace getSuper()
    {
		if(parent != null)
			return parent;
		else
			return this;
    }

    public NameSpace getGlobal()
    {
		if ( parent != null )
			return parent.getGlobal();
		else
			return this;
    }

	
	/**
		A This object is a thin layer over a namespace, comprising a bsh object
		context.  It handles things like the interface types the bsh object
		supports and aspects of method invocation on it.  
		<p>

		The declaringInterpreter is here to support callbacks from Java through
		generated proxies.  The scripted object "remembers" who created it for
		things like printing messages and other per-interpreter phenomenon
		when called externally from Java.
	*/
	/*
		Note: we need a singleton here so that things like 'this == this' work
		(and probably a good idea for speed).

		Caching a single instance here seems technically incorrect,
		considering the declaringInterpreter could be different under some
		circumstances.  (Case: a child interpreter running a source() / eval() 
		command ).  However the effect is just that the main interpreter that
		executes your script should be the one involved in call-backs from Java.

		I do not know if there are corner cases where a child interpreter would
		be the first to use a This reference in a namespace or if that would
		even cause any problems if it did...  We could do some experiments
		to find out... and if necessary we could cache on a per interpreter
		basis if we had weak references...  We might also look at skipping 
		over child interpreters and going to the parent for the declaring 
		interpreter, so we'd be sure to get the top interpreter.
	*/
    This getThis( Interpreter declaringInterpreter ) 
	{
		if ( thisReference == null )
			thisReference = This.getThis( this, declaringInterpreter );
		return thisReference;
    }

	public BshClassManager getClassManager() 
	{
		if ( classManager != null )
			return classManager;
		if ( parent != null && parent != JAVACODE )
			return parent.getClassManager();

		System.out.println("No class manager:" +this.getName());
		return null;
		//throw new InterpreterError("No class manager:" +this.getName());
	}

	void setClassManager( BshClassManager classManager ) {
		this.classManager = classManager;
	}

	/**
		Used for serialization
	*/
	public void prune() 
	{
		// Cut off from parent, we must have our own class manager.
		// Can't do this in the run() command (needs to resolve stuff)
		// Should we do it by default when we create a namespace will no
		// parent of class manager?

		if ( this.classManager == null )
			setClassManager( BshClassManager.createClassManager() );

		setParent( null );
	}

	public void setParent( NameSpace parent ) 
	{
		this.parent = parent;

		// If we are disconnected from root we need to handle the def imports
		if ( parent == null )
			loadDefaultImports();
	}

	/**
		Get the specified variable in this namespace or a parent namespace.
		<p>
		Note: this method is primarily intended for use internally.  If you use
		this method outside of the bsh package you will have to use 
		Primitive.unwrap() to get primitive values.
		@see Primitive#unwrap( Object )

		@return The variable value or Primitive.VOID if it is not defined.
	*/
    public Object getVariable( String name ) {
		return getVariable( name, true );
	}

	/**
		Get the specified variable in this namespace.
		If recurse is true extend search through parent namespaces.
		<p>
		Note: this method is primarily intended for use internally.  If you use
		this method outside of the bsh package you will have to use 
		Primitive.unwrap() to get primitive values.
		@see Primitive#unwrap( Object )

		@return The variable value or Primitive.VOID if it is not defined.
	*/
    public Object getVariable( String name, boolean recurse ) {
		Object val = getVariableImpl( name, recurse );
		return unwrapVariable( val );
    }

	/**
		Unwrap a typed variable to its value.
		Turn null into Primitive.VOID
	*/
	protected Object unwrapVariable( Object val ) {
		if (val instanceof TypedVariable)
			val	= ((TypedVariable)val).getValue();

		return (val == null) ? Primitive.VOID :	val;
	}

	/**
		Return the raw variable retrieval (TypedVariable object or for untyped
		the simple value) with optional recursion.
		@return the raw variable value or null if it is not defined
	*/
    protected Object getVariableImpl( String name, boolean recurse ) {
		Object val = null;

		if(variables !=	null)
			val	= variables.get(name);

		if ( recurse && (val == null) && (parent != null) )
			val	= parent.getVariableImpl(name, recurse);

		return val;
    }

    /**
		Set the typed variable with the value.  
		An existing typed variable may only be set to the same type.
		If an untyped variable exists it will be overridden with the new
		typed var.
		The set will perform a getAssignableForm() on the value if necessary.

		<p>
		Note: this method is primarily intended for use internally.  If you use
		this method outside of the bsh package and wish to set variables with
		primitive values you will have to wrap them using bsh.Primitive.
		@see bsh.Primitive

		@param value If value is null, you'll get the default value for the type
    */
    public void	setTypedVariable(
		String	name, Class type, Object value,	boolean	isFinal) 
		throws UtilEvalError 
	{
		if (variables == null)
			variables =	new Hashtable();

		if (value == null)
		{
			// initialize variable to appropriate default value	- JLS 4.5.4
			if(type.isPrimitive())
			{
			if(type	== Boolean.TYPE)
				value = new	Primitive(Boolean.FALSE);
			else if(type ==	Byte.TYPE)
				value = new	Primitive((byte)0);
			else if(type ==	Short.TYPE)
				value = new	Primitive((short)0);
			else if(type ==	Character.TYPE)
				value = new	Primitive((char)0);
			else if(type ==	Integer.TYPE)
				value = new	Primitive((int)0);
			else if(type ==	Long.TYPE)
				value = new	Primitive(0L);
			else if(type ==	Float.TYPE)
				value = new	Primitive(0.0f);
			else if(type ==	Double.TYPE)
				value = new	Primitive(0.0d);
			}
			else
				value =	Primitive.NULL;
		}

		// does the variable already exist?
		if ( variables.containsKey(name) ) 
		{
			Object existing = getVariableImpl( name, false );
			// is it typed?
			if ( existing instanceof TypedVariable ) 
			{
				// if it had a different type throw error
				if ( ((TypedVariable)existing).getType() != type )
					throw new UtilEvalError( "Typed variable: "+name
						+" was previously declared with type: " 
						+ ((TypedVariable)existing).getType() );
				else {
					// else set it and return
					((TypedVariable)existing).setValue( value );
					return;
				}
			}
			// else fall through to override and install the new typed version
		} 

		// add the new typed var
		variables.put(name, new	TypedVariable(type, value, isFinal));
    }

	/**
		Note: this is primarily for internal use.
		@see Interpreter#source( String )
		@see Interpreter#eval( String )
	*/
    public void	setMethod(String name, BshMethod method) 
	{
		if(methods == null)
			methods = new Hashtable();

		Object m = methods.get(name);

		if ( m == null )
			methods.put(name, method);
		else 
		if ( m instanceof BshMethod ) {
			Vector v = new Vector();
			v.addElement( m );
			v.addElement( method );
			methods.put( name, v );
		} else // Vector
			((Vector)m).addElement( method );
    }

	/**
		Get the bsh method matching the specified signature declared in 
		this name space or a parent.
		<p>
		Note: this method is primarily intended for use internally.  If you use
		this method outside of the bsh package you will have to be familiar
		with BeanShell's use of the Primitive wrapper class.
		@see bsh.Primitive
		@return apparently the method or null
	*/
    public BshMethod getMethod( String name, Class [] sig ) 
	{
		BshMethod method = null;

		Object m = null;
		if ( methods != null )
			m = methods.get(name);

		if ( m instanceof Vector ) {
			Vector vm = (Vector)m;
			BshMethod [] ma = new BshMethod[ vm.size() ];
			vm.copyInto( ma );

			Class [][] candidates = new Class[ ma.length ][];
			for( int i=0; i< ma.length; i++ )
				candidates[i] = ma[i].getArgTypes();

			int match = Reflect.findMostSpecificSignature( sig, candidates );
			if ( match != -1 )
				method = ma[match];
		} else
			method = (BshMethod)m;
			
		if ((method == null) && (parent != null))
			return parent.getMethod( name, sig );

		return method;
    }

	/**
		Import a class name.
		Subsequent imports override earlier ones
	*/
    public void	importClass(String name)
    {
		if(importedClasses == null)
			importedClasses = new Hashtable();

		importedClasses.put(Name.suffix(name, 1), name);
		nameSpaceChanged();
    }

	/**
		subsequent imports override earlier ones
	*/
    public void	importPackage(String name)
    {
		if(importedPackages == null)
			importedPackages = new Vector();

		importedPackages.addElement(name);
		nameSpaceChanged();
    }

	/**
		Get a list of all imported packages including parents.
		in the order in which they were imported...
		Note that the resolver may use them in the reverse order for
		precedece reasons.
		@deprecated
	*/
    public String[] getImportedPackages()
    {
		Vector v = getImportedPackages(true);
		String[] packages = new	String[ v.size() ];
		v.copyInto(packages);
		return packages;
    }

	/**
		Get a list of all imported packages in the order in which they were 
		imported...  If recurse is true, also include the parent's.
	*/
    public Vector getImportedPackages( boolean recurse )
    {
		if ( !recurse )
			return importedPackages;
		else {
			Vector v = new Vector();
			// add parent's
			if ( parent != null ) {
				String [] psa = parent.getImportedPackages();
				for(int i=0; i<psa.length; i++)
					v.addElement(psa[i]);
			}
			// add ours
			if ( importedPackages != null )
				for(int i=0; i< importedPackages.size(); i++)
					v.addElement( importedPackages.elementAt(i) );

			return v;
		}
    }

// debug
//public static int cacheCount = 0;

	/**
		Helper that caches class.
	*/
	private void cacheClass( Class c ) {
		if ( classCache == null ) {
			classCache = new Hashtable();
			//cacheCount++; // debug
		}

		classCache.put(name, c);
	}

	/**
		Load a class through this namespace taking into account imports.
		The class search will proceed through the parent namespaces if
		necessary.

		@return null if not found.
	*/
    public Class getClass( String name)
		throws UtilEvalError
    {
		Class c = getClassImpl(name);
		if ( c != null )
			return c;
		else
			// implement the recursion for getClassImpl()
			if ( parent != null )
				return parent.getClass( name );
			else
				return null;
	}

	/**
		Implementation of getClass() 

		Load a class through this namespace taking into account imports.
		<p>

		Check the cache first.  If an unqualified name look for imported 
		class or package.  Else try to load absolute name.
		<p>

		This method implements caching of unqualified names (normally imports).
		Qualified names are cached by the BshClassManager.
		Unqualified absolute class names (e.g. unpackaged Foo) are cached too
		so that we don't go searching through the imports for them each time.

		@return null if not found.
	*/
    private Class getClassImpl( String name )
		throws UtilEvalError
    {
		// Unqualified (simple, non-compound) name
		boolean unqualifiedName = !Name.isCompound(name);
		Class c = null;

		// Check the cache
		if (classCache != null) {
			c =	(Class)classCache.get(name);

			if ( c != null )
				return c;
		}
			
		// Unqualified name check imported
		if ( unqualifiedName ) {
			c = getImportedClassImpl( name );

			// if found as imported also cache it
			if ( c != null ) {
				cacheClass( c );
				return c;
			}
		}

		// Try absolute
		c = classForName( name );
		if ( c != null ) {
			// Cache unqualified names to prevent import check again
			if ( unqualifiedName )
				cacheClass( c );
			return c;
		}

		// Not found
		if ( Interpreter.DEBUG ) 
			Interpreter.debug("getClass(): " + name	+ " not	found in "+this);
		return null;
    }

	/**
		Try to make the name into an imported class.
		This method takes into account only imports (class or package)
		found directly in this NameSpace (no parent chain).
	*/
    private Class getImportedClassImpl( String name )
		throws UtilEvalError
    {
		// Try explicitly imported class, e.g. import foo.Bar;
		String fullname = null;
		if ( importedClasses != null )
			fullname = (String)importedClasses.get(name);

		if ( fullname != null ) 
		{
			/*
				Found the full name in imported classes.
			*/
			// Try to make the full imported name
			Class clas=classForName(fullname);
			
			// Handle imported inner class case
			if ( clas == null ) 
			{
				// Imported full name wasn't found as an absolute class
				// If it is compound, try to resolve to an inner class.  
				// (maybe this should happen in the BshClassManager?)

				if ( Name.isCompound( fullname ) )
					try {
						clas = getNameResolver( fullname ).toClass();
					} catch ( ClassNotFoundException e ) { /* not a class */ }
				else 
					if ( Interpreter.DEBUG ) Interpreter.debug(
						"imported unpackaged name not found:" +fullname);

				// If found cache the full name in the BshClassManager
				if ( clas != null ) {
					// (should we cache info in not a class case too?)
					getClassManager().cacheClassInfo( fullname, clas );
					return clas;
				}
			} else
				return clas;

			// It was explicitly imported, but we don't know what it is.
			// should we throw an error here??
			return null;  
		}

		/*
			Try imported packages, e.g. "import foo.bar.*;"
			in reverse order of import...
			(give later imports precedence...)
		*/
		if ( importedPackages != null )
			for(int i=importedPackages.size()-1; i>=0; i--)
			{
				String s = ((String)importedPackages.elementAt(i)) + "." + name;
				Class c=classForName(s);
				if ( c != null )
					return c;
			}

		BshClassManager bcm = getClassManager();
		/*
			Try super import if available
			Note: we do this last to allow explicitly imported classes
			and packages to take priority.  This method will also throw an
			error indicating ambiguity if it exists...
		*/
		if ( bcm.hasSuperImport() ) 
		{
			String s = bcm.getClassNameByUnqName( name );
			if ( s != null )
				return classForName( s );
		}

		return null;
    }

	private Class classForName( String name ) 
	{
		return getClassManager().classForName( name );
	}

	/**
		Implements NameSource
		@return all variable and method names in this and all parent
		namespaces
	*/
	public String [] getAllNames() 
	{
		Vector vec = new Vector();
		getAllNamesAux( vec );
		String [] names = new String [ vec.size() ];
		vec.copyInto( names );
		return names;
	}

	/**
		Helper for implementing NameSource
	*/
	protected void getAllNamesAux( Vector vec ) 
	{
		Enumeration varNames = variables.keys();
		while( varNames.hasMoreElements() )
			vec.addElement( varNames.nextElement() );

		Enumeration methodNames = methods.keys();
		while( methodNames.hasMoreElements() )
			vec.addElement( methodNames.nextElement() );

		if ( parent != null )
			parent.getAllNamesAux( vec );
	}

	Vector nameSourceListeners;
	/**
		Implements NameSource
		Add a listener who is notified upon changes to names in this space.
	*/
	public void addNameSourceListener( NameSource.Listener listener ) {
		if ( nameSourceListeners == null )
			nameSourceListeners = new Vector();
		nameSourceListeners.addElement( listener );
	}
	
	/**
		Perform "import *;" causing the entire classpath to be mapped.
		This can take a while.
	*/
	public void doSuperImport() 
		throws UtilEvalError
	{
		getClassManager().doSuperImport();
	}

    static class TypedVariable implements java.io.Serializable 
	{
		Class type;
		Object value = null; // uninitiailized
		boolean	isFinal;

		TypedVariable(Class type, Object value,	boolean	isFinal)
			throws UtilEvalError
		{
			this.type =	type;
			if ( type == null )
				throw new InterpreterError("null type in typed var: "+value);
			this.isFinal = isFinal;
			setValue( value );
		}

		/**
			Set the value of the typed variable.
		*/
		void setValue(Object val) throws UtilEvalError
		{
			if ( isFinal && value != null )
				throw new UtilEvalError ("Final variable, can't assign");

			// do basic assignability check
			val = getAssignableForm(val, type);
			
			// If we are a numeric primitive type we want to convert to the 
			// actual numeric type of this variable...  Being assignable is 
			// not good enough.
			if ( val instanceof Primitive && ((Primitive)val).isNumber() )
				try {
					val = BSHCastExpression.castPrimitive( 
						(Primitive)val, type );
				} catch ( UtilEvalError e ) {
					throw new InterpreterError("Auto assignment cast failed");
				}

			this.value= val;
		}

		Object getValue() { return value; }

		Class getType() { return type;	}

		public String toString() { 
			return "TypedVariable: "+type+", value:"+value;
		}
    }

	/**
		@deprecated name changed.
		@see #getAssignableForm( Object, Class )
	*/
    public static Object checkAssignableFrom(Object rhs, Class lhsType)
		throws UtilEvalError
    {
		return getAssignableForm( rhs, lhsType );
	}

	/**
		Determine if the RHS object can be assigned to the LHS type (as is,
		through widening, promotion, etc) and if so, return the assignable 
		form of the RHS.  
	
		Note that this is *not* a cast operation.  Only assignments which are 
		always legal (upcasts, promotion) are passed.
		<p>

		In normal cases this functions as a simple check for assignability
		and the value is returned unchanged.  e.g. a String is assignable to
		an Object, but no conversion is necessary.  Similarly an int is 
		assignable to a long, so no conversion is done.
		In this sense assignability is in terms of what the Java reflection API
		will allow since the reflection api will do widening conversions in the 
		case of sets on fields and arrays.
		<p>
		The primary purpose of the "returning the assignable form"
		abstraction is to allow non standard bsh assignment conversions. e.g.
		the wrapper stuff.  I'm still not sure how much of that we should
		be doing.
		<p>

		This method is used in many places throughout bsh including assignment
		operations and method selection.
		<p>

		@return an assignable form of the RHS or throws UtilEvalError
		@throws UtilEvalError on non assignable
		@see BSHCastExpression#castObject( java.lang.Object, java.lang.Class )
	*/
    public static Object getAssignableForm( Object rhs, Class lhsType )
		throws UtilEvalError
    {
	/*
		Notes:
	
		Need to define the exact behavior here:
			Does this preserve Primitive types to Primitives, etc.?

		This is very confusing in general...  need to simplify and clarify the
		various places things are happening:
			Reflect.isAssignableFrom()
			Primitive?
			here?
	*/
		Class originalType;

		if ( lhsType == null )
			throw new InterpreterError(
				"Null value for type in getAssignableForm");

		if(rhs == null)
			throw new InterpreterError("Null value in getAssignableForm.");

		if(rhs == Primitive.VOID)
			throw new UtilEvalError( "Undefined variable or class name");

		if (rhs == Primitive.NULL)
			if(!lhsType.isPrimitive())
				return rhs;
			else
				throw new UtilEvalError(
					"Can't assign null to primitive type " + lhsType.getName());

		Class rhsType;

		if ( rhs instanceof Primitive ) 
		{
			// set the rhsType to the type of the primitive
			rhsType = originalType = ((Primitive)rhs).getType();

			// check for primitive/non-primitive mismatch
			if ( lhsType.isPrimitive() ) {
				// not doing this yet...  leaving as the assignable orig type
				/*
					We have two primitive types.  If Reflect.isAssignableFrom()
					which knows about primitive widening conversions says they
					are assignable, we will do a cast to change the value
				if ( Reflect.isAssignableFrom(
					((Primitive)lhs).getType(), ((Primitive)rhs).getType() )
				*/
			} else
			{
				// attempt promotion to	a primitive wrapper
				// if lhs a wrapper type, get the rhs as wrapper value
				// else error
				if( Boolean.class.isAssignableFrom(lhsType) ||
					Character.class.isAssignableFrom(lhsType) ||
					Number.class.isAssignableFrom(lhsType) )
				{
					rhs	= ((Primitive)rhs).getValue();
					// type is the wrapper class type
					rhsType = rhs.getClass();
				}
				else
					assignmentError(lhsType, originalType);
			}
		} else 
		{
			// set the rhs type
			rhsType = originalType = rhs.getClass();

			// check for primitive/non-primitive mismatch
			if ( lhsType.isPrimitive() ) {

				// attempt unwrapping wrapper class for assignment 
				// to a primitive

				if (rhsType == Boolean.class)
				{
					rhs	= new Primitive((Boolean)rhs);
					rhsType = Boolean.TYPE;
				}
				else if (rhsType == Character.class)
				{
					rhs	= new Primitive((Character)rhs);
					rhsType = Character.TYPE;
				}
				else if (Number.class.isAssignableFrom(rhsType))
				{
					rhs	= new Primitive((Number)rhs);
					rhsType = ((Primitive)rhs).getType();
				}
				else
					assignmentError(lhsType, originalType);
			}
		}

		// This handles both class types and primitive .TYPE types
		if ( Reflect.isAssignableFrom(lhsType, rhsType) )
			return rhs;

		/* 
			bsh extension -
			Attempt widening conversions as defined in JLS 5.1.2
			except perform them on primitive wrapper objects.
		*/
		if(lhsType == Short.class)
			if(rhsType == Byte.class)
				return new Short(((Number)rhs).shortValue());

		if(lhsType == Integer.class) {
			if(rhsType == Byte.class || rhsType == Short.class)
				return new Integer(((Number)rhs).intValue());

			if(rhsType == Character.class)
				return new Integer(((Number)rhs).intValue());
		}

		if(lhsType == Long.class) {
			if(rhsType == Byte.class || rhsType == Short.class ||
				rhsType == Integer.class)
				return new Long(((Number)rhs).longValue());

			if(rhsType == Character.class)
				return new Long(((Number)rhs).longValue());
		}

		if(lhsType == Float.class) {
			if(rhsType == Byte.class || rhsType == Short.class ||
				rhsType == Integer.class ||	rhsType	== Long.class)
				return new Float(((Number)rhs).floatValue());

			if(rhsType == Character.class)
				return new Float(((Number)rhs).floatValue());
		}

		if(lhsType == Double.class) {
			if(rhsType == Byte.class || rhsType == Short.class ||
				rhsType == Integer.class ||	rhsType	== Long.class ||
				rhsType == Float.class)
				return new Double(((Number)rhs).doubleValue());

			if(rhsType == Character.class)
				return new Double(((Number)rhs).doubleValue());
		}

		/*
			Bsh This objects may be able to use the proxy mechanism to 
			become an LHS type.
		*/
		if ( Capabilities.canGenerateInterfaces() && 
			lhsType.isInterface() && ( rhs instanceof bsh.This ) ) 
		{
			return ((bsh.This)rhs).getInterface( lhsType );
		}

		assignmentError(lhsType, originalType);

		return rhs;
    }

    private static void	assignmentError(Class lhs, Class rhs) throws UtilEvalError
    {
		String lhsType = Reflect.normalizeClassName(lhs);
		String rhsType = Reflect.normalizeClassName(rhs);
		throw new UtilEvalError ("Can't assign " + rhsType + " to "	+ lhsType);
    }

	public String toString() {
		return
			"NameSpace: "
			+ ( name==null
				? super.toString()
				: name + " (" + super.toString() +")" );
	}

	/*
		For serialization.
		Don't serialize non-serializable objects.
	*/
    private synchronized void writeObject(java.io.ObjectOutputStream s)
        throws IOException 
	{
		// clear name resolvers... don't know if this is necessary.
		names = null;
	
		s.defaultWriteObject();
	}

	/**
		Invoke a method in this namespace with the specified args and
		interpreter reference.  No caller information or call stack is
		required.  The method will appear as if called externally from Java.
		<p>

		@see bsh.This.invokeMethod( 
			String methodName, Object [] args, Interpreter interpreter, 
			CallStack callstack, SimpleNode callerInfo )
	*/
	public Object invokeMethod( 
		String methodName, Object [] args, Interpreter interpreter ) 
		throws EvalError
	{
		return invokeMethod( methodName, args, interpreter, null, null );
	}

	/**
		This method simply delegates to This.invokeMethod();
		<p>
		@see bsh.This.invokeMethod( 
			String methodName, Object [] args, Interpreter interpreter, 
			CallStack callstack, SimpleNode callerInfo )
	*/
	public Object invokeMethod( 
		String methodName, Object [] args, Interpreter interpreter, 
		CallStack callstack, SimpleNode callerInfo ) 
		throws EvalError
	{
		return getThis( interpreter ).invokeMethod( 
			methodName, args, interpreter, callstack, callerInfo);
	}

	/**
		Clear all cached classes and names
	*/
	public void classLoaderChanged() {
		nameSpaceChanged();
	}

	/**
		Clear all cached classes and names
	*/
	public void nameSpaceChanged() {
		classCache = null;
		names = null;
	}

	/**
		Import standard packages.  Currently:
		<pre>
			importClass("bsh.EvalError");
			importPackage("javax.swing.event");
			importPackage("javax.swing");
			importPackage("java.awt.event");
			importPackage("java.awt");
			importPackage("java.net");
			importPackage("java.util");
			importPackage("java.io");
			importPackage("java.lang");
		</pre>
	*/
    public void loadDefaultImports()
    {
		/**
			Note: the resolver looks through these in reverse order, per
			precedence rules...  so for max efficiency put the most common
			ones later.
		*/
		importClass("bsh.EvalError");
		importPackage("javax.swing.event");
		importPackage("javax.swing");
		importPackage("java.awt.event");
		importPackage("java.awt");
		importPackage("java.net");
		importPackage("java.util");
		importPackage("java.io");
		importPackage("java.lang");

	/*
		String res = "lib/defaultImports";
		InputStream in = NameSpace.class.getResourceAsStream(res);
		if(in == null)
			throw new IOException("couldn't load resource: " + res);
		BufferedReader bin = new BufferedReader(new InputStreamReader(in));

		String s;
		try {
			while((s = bin.readLine()) != null)
			importPackage(s);

			bin.close();
		} catch(IOException e) {
			Interpreter.debug("failed to load default imports...");
		}
	*/

    }

	/**
		This is the factory for Name objects which resolve names within
		this namespace (e.g. toObject(), toClass(), toLHS()).
		<p>

		This was intended to support name resolver caching, allowing 
		Name objects to cache info about the resolution of names for 
		performance reasons.  However this not proven useful yet.  
		<p>

		We'll leave the caching as it will at least minimize Name object
		creation.
		<p>

		(This method would be called getName() if it weren't already used for 
		the simple name of the NameSpace)
		<p>

		This method was public for a time, which was a mistake.  
		Use get() instead.
	*/
	Name getNameResolver( String ambigname ) 
	{
		if ( names == null )
			names = new Hashtable();
		Name name = (Name)names.get( ambigname );
		if ( name == null ) {
			name = new Name( this, ambigname );
			names.put( ambigname, name );
		} else {
		}

		return name;
	}

	public int getInvocationLine() {
		SimpleNode node = getNode();
		if ( node != null )
			return node.getLineNumber();
		else
			return -1;
	}
	public String getInvocationText() {
		SimpleNode node = getNode();
		if ( node != null )
			return node.getText();
		else
			return "<invoked from Java code>";
	}

	/**
		This is a helper method for working inside of bsh scripts and commands.
		In that context it is impossible to see a ClassIdentifier object
		for what it is.  Attempting to access a method on it will look like
		a static method invocation.
	*/
	public static Class identifierToClass( Name.ClassIdentifier ci ) 
	{
		return ci.getTargetClass();
	}

	/**
		Clear all variables, methods, and imports from this namespace.
		If this namespace is the root, it will be reset to the default 
		imports.
		@see #loadDefaultImports()
	*/
	public void clear() 
	{
		variables = null;
		methods = null;
		importedClasses = null;
		importedPackages = null;
		if ( parent == null )
			loadDefaultImports();	
    	classCache = null;
		names = null;
	}
}

