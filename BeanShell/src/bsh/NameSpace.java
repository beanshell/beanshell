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
public class NameSpace implements java.io.Serializable
{
    private NameSpace parent;
    private Hashtable variables;
    private Hashtable methods;
    private Hashtable importedClasses;
    private Vector importedPackages;
    private This thisReference;
	public String name; 
    transient private Hashtable classCache;

    // A cache for things we know are *not* classes... actual value is unused
    transient static Hashtable absoluteNonClasses;
    transient static Hashtable absoluteClassCache	= new Hashtable();

    public NameSpace( String name ) { 
		this( null, name );
	}

    public NameSpace( NameSpace parent, String name ) {
		this.name = name;
		this.parent = parent;
    }

    public void	setVariable(String name, Object	o) throws EvalError {

		if(variables ==	null)
			variables =	new Hashtable();

		if(o ==	null)
			variables.remove(name);
		else
		{
			Object current = variables.get(name);
			if((current	!= null) && (current instanceof	TypedVariable))
			try
			{
				((TypedVariable)current).setValue(o);
			}
			catch(EvalError	e)
			{
				throw new EvalError("Typed variable: " + name + ": " + e.getMessage());
			}
			else
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

	/*
		Note: this isn't quite the same as getSuper().
		getSuper() returns 'this' if we are at the root namespace.
	*/
	public NameSpace getParent() {
		return parent;
	}


	/*
		getThis() is a factory for bsh.This type references.  The capabilities
		of ".this" references in bsh are version dependent up until jdk1.3.
		The version dependence was to support different default interface
		implementations.  i.e. different sets of listener interfaces which
		scripted objects were capable of implementing.  In jdk1.3 the 
		reflection proxy mechanism was introduced which allowed us to 
		implement arbitrary interfaces.  This is fantastic.

		A This object is a thin layer over a namespace, comprising a bsh object
		context.  We create it here only if needed for the namespace.
		
		Note: I keep thinking about combining This and NameSpace, but I just 
		don't want to.  They have different semantics...

	*/
    This getThis( Interpreter declaringInterpreter ) {

		if ( thisReference == null ) {
			if ( haveProxyMechanism() )
				thisReference = new XThis( this, declaringInterpreter );
			else if ( haveSwing() )
				thisReference = new JThis( this, declaringInterpreter );
			else
				thisReference = new This( this, declaringInterpreter );
		}

		return thisReference;
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

    /*
	if value is null, you'll get the default value for the type
    */
    public void	setTypedVariable(
		String	name, Class type, Object value,	boolean	isFinal) 
		throws EvalError {

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

    // This may be fucked up....  if you import packages within a namespace
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

    public Class getClass(String name)
    {
		Class c	= null;

		if(classCache != null)
			c =	(Class)classCache.get(name);

		if(c ==	null)
		{
			c =	getClass(this, name);
			if(c != null)
			{
			if(classCache == null)
				classCache = new Hashtable();

			classCache.put(name, c);
			}
		}

		return c;
    }

    static Class getAbsoluteClass(String name)
    {
		return getClass(null, name);
    }

    private static Class getClass(NameSpace namespace, String name)
    {
		// name	does not contain a . check for imported	names
		if( (namespace != null) && !Name.isCompound(name) )
		{
			String fullname = namespace.getImportedClass(name);

			// Was explicitly imported single class name (not a package).
			if (fullname != null) {
				Class c	= (Class)absoluteClassCache.get(fullname);
				if(c !=	null)
					return c;

				// Try to make the name
				c=classForName(fullname);
				if ( c!= null)
					return c;

				/* 
					Try inner class.  
				*/
				try {
					// use null here for interp... we only care if it resolve
					// to a class
					Object obj = new Name(namespace, fullname).toObject( null );
					Class clas = ((Name.ClassIdentifier)obj).getTargetClass();
					absoluteClassCache.put(fullname, clas);
					return clas;
				} catch ( Exception e ) {
				}

				return null;  // ?  it was imported, right cannot be .*
			}

			// Try imported packages (.*)
			String[] packages =	namespace.getImportedPackages();
			for(int i=0; i<packages.length; i++)
			{
				String s = packages[i] + "." + name;
				Class c	= (Class)absoluteClassCache.get(s);
				if(c !=	null)
					return c;

				c=classForName(s);
				if ( c != null )
					return c;
			}
		}

		// Try whatever
		// why this construct?
		if( absoluteNonClasses.get(name) == null) {
			Class c	=(Class)absoluteClassCache.get(name);
			if(c !=	null)
				return c;

			c = classForName(name);
			if ( c != null )
				return c;
		}


		Interpreter.debug("getClass(): " + name	+ " not	found.");
		return null;
    }

	static Class classForName( String name ) {
		if ( absoluteNonClasses.get(name) != null )
			return null;

	    try{
			Class c	=(Class)absoluteClassCache.get(name);
			if(c !=	null)
				return c;

			c = Class.forName(name);
			absoluteClassCache.put(name, c);
			return c;
	    }
	    catch ( Exception e ) {
			absoluteNonClasses.put(name, "unused");
	    } catch ( NoClassDefFoundError e2 ) {
			/*
				This is weird... jdk under Win is throwing these to
				warn about lower case / upper case possible mismatch.
				e.g. bsh.console bsh.Connsole
			*/
			absoluteNonClasses.put(name, "unused");
		}
		return null;
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

		In addition to assignments this methods is used in method matching.
	*/
    public static Object checkAssignableFrom(Object rhs, Class lhsType)
		throws EvalError
    {
		Class originalType;
		Class rhsType;

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
		if ( haveProxyMechanism() && 
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

    public static void loadDefaultImports( 
		NameSpace namespace) throws IOException
    {
		String res = "lib/defaultImports";
		InputStream in = NameSpace.class.getResourceAsStream(res);
		if(in == null)
			throw new IOException("couldn't load resource: " + res);
		BufferedReader bin = new BufferedReader(new InputStreamReader(in));

		String s;
		try
		{
			while((s = bin.readLine()) != null)
			namespace.importClass(s);

			bin.close();
		}
		catch(IOException e)
		{
			Interpreter.debug("failed to load default imports...");
		}
    }

    public static void loadJavaPackagesOptimization() throws IOException
    {
		if(absoluteNonClasses != null)
			return;

		absoluteNonClasses = new Hashtable();
		String res = "lib/javaPackages";
		InputStream in = NameSpace.class.getResourceAsStream(res);
		if(in == null)
			throw new IOException("couldn't load resource: " + res);
		BufferedReader bin = new BufferedReader(new InputStreamReader(in));

		String s;
		try {
			while((s = bin.readLine()) != null)
			absoluteNonClasses.put(s, "unused");

			bin.close();
		} catch(IOException e) {
			Interpreter.debug("failed to load java package names...");
		}
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

	static boolean checkedForSwing, haveSwing;
	public static boolean haveSwing() {

		if ( checkedForSwing )
			return haveSwing;

		haveSwing = classExists( "javax.swing.JButton" );
		checkedForSwing = true;
		return ( haveSwing );
	}

	static boolean checkedForProxyMech, haveProxyMech;
	public static boolean haveProxyMechanism() {
		if ( checkedForProxyMech )
			return haveProxyMech;

		/*
		haveProxyMech = 
			Package.getPackage("java.lang").isCompatibleWith("1.3");
		*/
		haveProxyMech = classExists( "java.lang.reflect.Proxy" );
		checkedForProxyMech = true;
		return haveProxyMech;
	}

	static boolean classExists( String name ) {
		Class c = null;
		try { 
			c = Class.forName( name ); 
		} catch ( Exception e ) { }
		return ( c != null );
	}

	// Convenience method
	public Object resolveName( String name, Interpreter interpreter ) 
		throws EvalError 
	{
		return new Name( this, name ).toObject( interpreter );
	}

}

