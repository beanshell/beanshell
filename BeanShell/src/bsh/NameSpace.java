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


package	bsh;

import java.util.*;

import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

// import Name.ClassIdentifier;   // Doesn't work under 1.2.2 ?

/**
    A namespace	in which methods and variables live.  This is package public 
	because it is used in the implementation of some bsh commands.

	A bsh.This object is a thin layer over a NameSpace.  Together they 
	comprise a bsh scripted object context.
*/
public class NameSpace 
	implements java.io.Serializable, BshClassManager.Listener
{
    private NameSpace parent;
    private Hashtable variables;
    private Hashtable methods;
    private Hashtable importedClasses;
    private Vector importedPackages;
    private This thisReference;
	public String name; 

	// Local class cache for classes resolved through this namespace using
	// getClass() (taking into account imports)
    transient private Hashtable classCache;

    public NameSpace( String name ) { 
		this( null, name );
	}

    public NameSpace( NameSpace parent, String name ) {
		this.name = name;
		this.parent = parent;
		// Register for notification of classloader change
		BshClassManager.getClassManager().addListener(this);
    }

	/**
		Resolve name to an object through this namespace.
		Note: the lazy instantiation here parallels that in getClass()
	*/
	public Object get( String name, Interpreter interpreter ) 
		throws EvalError 
	{
		return new Name( this, name ).toObject( interpreter );
	}

    public void	setVariable(String name, Object	o) throws EvalError {

		if(variables ==	null)
			variables =	new Hashtable();

		if ( o == null )
			variables.remove(name);
		else {
			Object current = variables.get(name);
			if ( (current!= null) && (current instanceof TypedVariable) )
			try {
				((TypedVariable)current).setValue(o);
			} catch(EvalError e) {
				throw new EvalError(
					"Typed variable: " + name + ": " + e.getMessage());
			} else
				variables.put(name, o);
		}
    }

	public String [] getVariableNames() {
		if ( variables == null )
			return new String [0];
		else
			return enumerationToStringArray( variables.keys() );
	}

	public String [] getMethodNames() {
		if ( methods == null )
			return new String [0];
		else
			return enumerationToStringArray( methods.keys() );
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
		if(parent != null)
			return parent.getGlobal();
		else
			return this;
    }

	
	/*
		A This object is a thin layer over a namespace, comprising a bsh object
		context.  We create it here only if needed for the namespace.

		Note: that This is factoried for different capabilities.  When we
		add classpath modification we'll have to have a listener here to
		uncache the This reference and allow it to be refactoried.
	*/
    This getThis( Interpreter declaringInterpreter ) {

		if ( thisReference == null )
			thisReference = This.getThis( this, declaringInterpreter );

		return thisReference;
    }

	/*
		Used for serialization
	*/
	public void prune() {
		parent = null;

		if ( methods != null )
			// Prune the methods of this namespace - detach the nodes
			// from their parent nodes. 
			// Is this correct?
			for( Enumeration e=methods.elements(); e.hasMoreElements(); )
				((BshMethod)e.nextElement()).method.prune();
	}

	public void setParent( NameSpace parent ) {
		this.parent = parent;
	}

	/**
		Get the specified variable in this namespace or a parent namespace.
		There are four magic variable references: 
			"this", "super", "global"
	*/
    public Object getVariable( String name ) {
		return getVariable( name, true );
	}

	/**
		Get the specified variable in this namespace.
		If recurse is true extend search through parent namespaces.
		
		There are four magic variable references: 
			"this", "super", "global"
	*/
    public Object getVariable( String name, boolean recurse ) {
		Object val = null;
		if(variables !=	null)
			val	= variables.get(name);

		if ( recurse && (val == null) && (parent != null) )
			val	= parent.getVariable(name);

		if (val instanceof TypedVariable)
			val	= ((TypedVariable)val).getValue();

		return (val == null) ? Primitive.VOID :	val;
    }

    /**
	 	If value is null, you'll get the default value for the type
    */
    public void	setTypedVariable(
		String	name, Class type, Object value,	boolean	isFinal) 
		throws EvalError 
	{

		if(variables ==	null)
			variables =	new Hashtable();

		if(variables.containsKey(name))
			// if this throws an error,	the var	was defined differently
			setVariable(name, value);

		if(value == null)
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

		variables.put(name, new	TypedVariable(type, value, isFinal));
    }

    public void	setMethod(String name, BshMethod method) {
		if(methods == null)
			methods = new Hashtable();
		//System.out.println("setting method: "+method+"in namespace: "+this);
		methods.put(name, method);
		
    }

    public BshMethod getMethod(String name) {
		BshMethod method = null;

		if(methods != null)
			method = (BshMethod)(methods.get(name));

		if((method == null) && (parent != null))
			return parent.getMethod(name);

		return method;
    }

    public void	importClass(String name)
    {
		if(importedClasses == null)
			importedClasses = new Hashtable();

		importedClasses.put(Name.suffix(name, 1), name);
    }

    public String getImportedClass(String name)
    {
		String s = null;

		if(importedClasses != null)
			s =	(String)importedClasses.get(name);

		if((s == null) && (parent != null))
			return (String)parent.getImportedClass(name);

		return s;
    }


    public void	importPackage(String name)
    {
		if(importedPackages == null)
			importedPackages = new Vector();

		importedPackages.addElement(name);
    }

    // This may be messed up....  if you import packages within a namespace
    // they shadow all inherited ones.
    public String[] getImportedPackages()
    {
		if(importedPackages == null)
			if(parent != null)
			return parent.getImportedPackages();
			else
			return new String[] { };

		String[] packages = new	String[importedPackages.size()];
		importedPackages.copyInto(packages);
		return packages;
    }

	/**
		Load class through this namespace, taking into account imports.

		Notes: This method does the caching for getClassImpl().
		The lazy instantiation of cache here parallels that in get()
	*/
    public Class getClass(String name)
    {
		Class c	= null;

		if(classCache != null)
			c =	(Class)classCache.get(name);

		if(c ==	null) {
			c =	getClassImpl( name );

			if(c != null) {
				if(classCache == null)
					classCache = new Hashtable();

				classCache.put(name, c);
			}
		}

		return c;
    }

	/**
		Implementation for getClass()
		If not a compound name look for imported class or package.
		Else try to load name.
	*/
    private Class getClassImpl( String name)
    {
		// Simple (non compound name) check if imported
		if ( !Name.isCompound(name) )
		{
			String fullname = getImportedClass(name);

			if ( fullname != null ) {
				/*
					Found the full name in imported classes.
				*/
				// Try to make the name
				Class c=classForName(fullname);
				if ( c!= null)
					return c;

				// Try imported inner class.  
				try {
					// Use null here for interp... we only care if it resolves
					// to a class.  Note we could use Name toClass() here but it
					// would just throw a more detailed error message.
					Object obj = get( fullname, null );
					Class clas = ((Name.ClassIdentifier)obj).getTargetClass();

					BshClassManager.absoluteClassCache.put(fullname, clas);
					return clas;
				} catch ( EvalError e ) { 
				} catch ( ClassCastException e2 ) { }

				return null;  // ?  it was imported, right cannot be .*
			}

			/*
				Try imported packages (.*)
			*/
			String[] packages =	getImportedPackages();
			for(int i=0; i<packages.length; i++)
			{
				String s = packages[i] + "." + name;
				Class c=classForName(s);
				if ( c != null )
					return c;
			}
		}

		Class c = classForName( name );
		if ( c != null )
			return c;

		Interpreter.debug("getClass(): " + name	+ " not	found.");
		return null;
    }


	private Class classForName( String name ) 
	{
		return BshClassManager.classForName( name );
	}

    static class TypedVariable implements java.io.Serializable {
		Class type;
		Object value;
		boolean	isFinal;

		TypedVariable(Class type, Object value,	boolean	isFinal)
		{
			this.type =	type;
			this.value = value;
			this.isFinal = isFinal;
		}

		void setValue(Object val) throws EvalError
		{
			if(isFinal)
			throw new EvalError ("Final variable, can't assign");

			val	= checkAssignableFrom(val, type); // throws error on failure
			value = val;
		}

		Object getValue() { return value; }
		Object getType() { return type;	}
    }

	/**
		Determine if the RHS object can be assigned to the LHS type.

		If a conversion can be performed to make it assignable do it and
		return a new form of the RHS.

		In addition to assignments this methods is used in method selection.

		@returns an assignable form of the RHS
	*/
    public static Object checkAssignableFrom(Object rhs, Class lhsType)
		throws EvalError
    {
		Class originalType;
		Class rhsType;

		/*
			This probably means we are trying to assign to a loose type
			should we accept it here?
		*/
		if ( lhsType == null )
			throw new InterpreterError("check assignable to null type");

		if(rhs == null)
			throw new InterpreterError("null value in checkAssignable");

		if(rhs == Primitive.VOID)
			throw new EvalError("void cannot be	used in	an assignment statement");

		if(rhs == Primitive.NULL)
			if(!lhsType.isPrimitive())
				return rhs;
			else
				throw new EvalError(
					"Can't assign null to primitive type " + lhsType.getName());

		if( rhs instanceof Primitive ) {
			originalType = rhsType = ((Primitive)rhs).getType();

			// check for primitive/non-primitive mismatch
			if(!lhsType.isPrimitive()) {
				// attempt promotion to	a primitive wrapper
				if( Boolean.class.isAssignableFrom(lhsType) ||
					Character.class.isAssignableFrom(lhsType) ||
					Number.class.isAssignableFrom(lhsType) )
				{
					rhs	= ((Primitive)rhs).getValue();
					rhsType = rhs.getClass();
				}
				else
					assignmentError(lhsType, originalType);
			}
		} else {
			originalType = rhsType = rhs.getClass();

			// check for primitive/non-primitive mismatch
			if ( lhsType.isPrimitive() ) {
				// attempt unwrapping primitive	wrapper
				if(rhsType == Boolean.class)
				{
					rhs	= new Primitive((Boolean)rhs);
					rhsType = Boolean.TYPE;
				}
				else if(rhsType	== Character.class)
				{
					rhs	= new Primitive((Character)rhs);
					rhsType = Character.TYPE;
				}
				else if(Number.class.isAssignableFrom(rhsType))
				{
					rhs	= new Primitive((Number)rhs);
					rhsType = ((Primitive)rhs).getType();
				}
				else
					assignmentError(lhsType, originalType);
			}
		}

		if ( Reflect.isAssignableFrom(lhsType, rhsType) )
			return rhs;

		/* 
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
		if ( Capabilities.haveProxyMechanism() && 
			lhsType.isInterface() && ( rhs instanceof bsh.This ) ) 
		{
			return ((XThis)rhs).getInterface( lhsType );
		}

		assignmentError(lhsType, originalType);

		return rhs;
    }

    private static void	assignmentError(Class lhs, Class rhs) throws EvalError
    {
		String lhsType = Reflect.normalizeClassName(lhs);
		String rhsType = Reflect.normalizeClassName(rhs);
		throw new EvalError ("Can't assign " + rhsType + " to "	+ lhsType);
    }

	public String toString() {
		return "NameSpace: "+ 
			( name==null ? super.toString(): name + " : " + super.toString() );
	}

	/*
		For serialization.
		Don't serialize non-serializable objects.
	*/
    private synchronized void writeObject(java.io.ObjectOutputStream s)
        throws IOException {

		// do something here
		s.defaultWriteObject();
	}

	/**
		Convenience for users outside of this package.
	*/
	public Object invokeMethod( 
		String methodName, Object [] args, Interpreter interpreter ) 
		throws EvalError
	{
		// Look for method in the bsh object
        BshMethod meth = getMethod(methodName);
        if ( meth != null )
           return meth.invokeDeclaredMethod( args, interpreter );

		// Look for a default invoke() handler method
		meth = getMethod( "invoke" );

		// Call script "invoke( String methodName, Object [] args );
		if ( meth != null )
			return meth.invokeDeclaredMethod( 
				new Object [] { methodName, args }, interpreter );

		throw new EvalError( "No locally declared method: " 
			+ methodName + " in namespace: " + this );
	}

	/**
		Clear all cached classes and names
	*/
	public void classLoaderChanged() {
		classCache = null;
	}

	/**
		re-evaluate the usefullness of this...
	*/
    public void loadDefaultImports() throws IOException
    {
		String res = "lib/defaultImports";
		InputStream in = NameSpace.class.getResourceAsStream(res);
		if(in == null)
			throw new IOException("couldn't load resource: " + res);
		BufferedReader bin = new BufferedReader(new InputStreamReader(in));

		String s;
		try {
			while((s = bin.readLine()) != null)
			importClass(s);

			bin.close();
		} catch(IOException e) {
			Interpreter.debug("failed to load default imports...");
		}
    }

}

