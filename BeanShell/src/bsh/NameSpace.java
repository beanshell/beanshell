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
    A namespace	in which methods and variables live.  This is package public 
	because it is used in the implementation of some bsh commands.  However
	for normal use you should be using methods on bsh.Interpreter to interact
	with your scripts.
	<p>

	A bsh.This object is a thin layer over a NameSpace.  Together they 
	comprise a bsh scripted object context.
	<p>

	Note: I'd really like to use collections here, but we have to keep this
	compatible with JDK1.1 
*/
public class NameSpace 
	implements java.io.Serializable, BshClassManager.Listener, 
	NameSource
{
	public static final NameSpace JAVACODE = 
		new NameSpace("Called from compiled Java code");

	public String name; 
    private NameSpace parent;
    private Hashtable variables;
    private Hashtable methods;
    private Hashtable importedClasses;
    private This thisReference;
    private Vector importedPackages;
	//String debugInfo;

	/** "import *;" operation has been performed */
	transient private static boolean superImport;

	// Local class cache for classes resolved through this namespace using
	// getClass() (taking into account imports)
    transient private Hashtable classCache;

    public NameSpace( String name ) { 
		this( null, name );
	}

    public NameSpace( NameSpace parent, String name ) {
		setName(name);
		setParent(parent);
		// Register for notification of classloader change
		BshClassManager.addCMListener(this);
    }

	public void setName( String name ) {
		this.name = name;
	}
	public String getName() {
		return this.name;
	}

	// experimental
	SimpleNode callerInfoNode;
	/**
		Set the node associated with the creation of this namespace.
		This is used in debugging.
	*/
	void setNode( SimpleNode node ) {
		this.callerInfoNode= node;
	}
	SimpleNode getNode() {
		return this.callerInfoNode;
	}
	// experimental

	/**
		Resolve name to an object through this namespace.
	*/
	public Object get( String name, Interpreter interpreter ) 
		throws EvalError 
	{
		CallStack callstack = new CallStack();
		return getNameResolver( name ).toObject( callstack, interpreter );
	}


	/**
		Set a variable in this namespace.
		<p>
		Note: this method is primarily intended for use internally.  If you use
		this method outside of the bsh package and wish to set variables with
		primitive values you will have to wrap them using bsh.Primitive.
		@see bsh.Primitive
	*/
    public void	setVariable(String name, Object	o) throws EvalError 
	{
		if ( variables == null )
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

	private String [] enumerationToStringArray( Enumeration e ) {
		Vector v = new Vector();
		while ( e.hasMoreElements() )
			v.addElement( e.nextElement() );
		String [] sa = new String [ v.size() ];
		v.copyInto( sa );
		return sa;
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
		if(parent != null)
			return parent.getGlobal();
		else
			return this;
    }

	
	/**
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

	/**
		Used for serialization
	*/
	public void prune() {
		parent = null;

	/*
	Do we need this?
	If so, fix the loop... can get Vectors of methods as well as methods

		if ( methods != null )
			// Prune the methods of this namespace - detach the nodes
			// from their parent nodes. 
			for( Enumeration e=methods.elements(); e.hasMoreElements(); )
				((BshMethod)e.nextElement()).method.prune();
	*/
	}

	public void setParent( NameSpace parent ) {
		this.parent = parent;
	}

	/**
		Get the specified variable in this namespace or a parent namespace.
		<p>
		Note: this method is primarily intended for use internally.  If you use
		this method outside of the bsh package you will have to use 
		Primitive.unwrap() to get primitive values.
		@see Primitive.unwrap()

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
		@see Primitive.unwrap()

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
			val	= parent.getVariable(name, recurse);

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
		throws EvalError 
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
					throw new EvalError( "Typed variable: "+name
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
		@see Interpreter.source()
		@see Interpreter.eval()
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

    private String getImportedClass(String name)
		throws ClassPathException
    {
		String s = null;

		// try imported in our space
		if ( importedClasses != null )
			s =	(String)importedClasses.get(name);

		// try imported in our parent's space
		if ((s == null) && (parent != null) )
			return (String)parent.getImportedClass(name);

		return s;
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
	*/
    public String[] getImportedPackages()
    {
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

		String[] packages = new	String[ v.size() ];
		v.copyInto(packages);
		return packages;
    }

	/**
		Load class through this namespace, taking into account imports.

		Notes: This method does the caching for getClassImpl().
		The lazy instantiation of cache here parallels that in get()
	*/
    public Class getClass(String name)
		throws ClassPathException
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
		throws ClassPathException
    {
		// Simple (non compound name) check if imported
		if ( !Name.isCompound(name) )
		{
			String fullname = getImportedClass(name);

			if ( fullname != null ) 
			{
				/*
					Found the full name in imported classes.
				*/
				// Try to make the full imported name
				Class clas=classForName(fullname);
				
				// If the imported name we found is compound, try to resolve
				// to an inner class.  
				if ( clas == null ) 
				{
					if ( Name.isCompound( fullname ) )
						try {
							clas = getNameResolver( fullname ).toClass();
						} catch ( EvalError e ) { /* not a class */ }
					else 
						Interpreter.debug(
							"imported unpackaged name not found:" +fullname);
				}

				// Found something?  Cache the class and return it.
				if ( clas != null ) {
					// (should we cache info in not a class case too?)
					BshClassManager.cacheClassInfo( fullname, clas );
					return clas;
				}

				// It was explicitly imported, but we don't know what it is.
				// should we throw an error here??
				return null;  
			}

			/*
				Try imported packages (.*)
				in reverse order of import...
				(give later imports precedence...)
			*/
			String[] packages =	getImportedPackages();
			//for(int i=0; i<packages.length; i++)
			for(int i=packages.length-1; i>=0; i--)
			{
				String s = packages[i] + "." + name;
				Class c=classForName(s);
				if ( c != null )
					return c;
			}

			/*
				Try super imported if available

				Note: we do this last to allow explicitly imported classes
				and packages to take priority.  This method will also throw an
				error indicating ambiguity if it exists...
			*/
			if ( superImport ) {
				BshClassManager bcm = BshClassManager.getClassManager();
				if ( bcm != null ) {
					String s = bcm.getClassNameByUnqName( name );
					if ( s != null )
						return classForName( s );
				}
			}
		}

		Class c = classForName( name );
		if ( c != null )
			return c;

		Interpreter.debug("getClass(): " + name	+ " not	found in "+this);
		return null;
    }

	private Class classForName( String name ) 
	{
		return BshClassManager.classForName( name );
	}

	// Fix this - can't use List in core
	/**
		Implements NameSource
		@return all class and variable names in this and all parent
		namespaces
	*/
	public String [] getAllNames() {
		/*
		Set all = new HashSet();
		getAllNamesAux( all );
		return all;
		*/
		return new String [0];
	}
	/**
		Helper for implementing NameSource
	protected void getAllNamesAux( Set set ) {
		set.addAll( variables.keySet() );
		set.addAll( methods.keySet() );
		if ( parent != null )
			parent.getAllNamesAux( set );
	}
	*/

	/**
		Implements NameSource
		Add a listener who is notified upon changes to names in this space.
	*/
	public void addNameSourceListener( NameSource.Listener listener ) {
	}
	
	/**
		Perform "import *;" causing the entire classpath to be mapped.
		This can take a while.  Feedback will be sent to the interpreter.
	*/
	public static void doSuperImport( Interpreter feedback ) 
	{
		BshClassManager bcm = BshClassManager.getClassManager();
		if ( bcm != null )
			bcm.doSuperImport( feedback );
		superImport = true;
	}

    static class TypedVariable implements java.io.Serializable {
		Class type;
		Object value = null; // uninitiailized
		boolean	isFinal;

		TypedVariable(Class type, Object value,	boolean	isFinal)
			throws EvalError
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
		void setValue(Object val) throws EvalError
		{
			if ( isFinal && value != null )
				throw new EvalError ("Final variable, can't assign");

			// do basic assignability check
			val = getAssignableForm(val, type);
			
			// If we are a numeric primitive type we want to convert to the 
			// actual numeric type of this variable...  Being assignable is 
			// not good enough.
			if ( val instanceof Primitive && ((Primitive)val).isNumber() )
				try {
					val = BSHCastExpression.castPrimitive( 
						(Primitive)val, type );
				} catch ( EvalError e ) {
					throw new InterpreterError("auto assignment cast failed");
				}

			this.value= val;
		}

		Object getValue() { return value; }
		Class getType() { return type;	}
    }

	/**
		@deprecated name changed.
		@see getAssignableForm()
	*/
    public static Object checkAssignableFrom(Object rhs, Class lhsType)
		throws EvalError
    {
		return getAssignableForm( rhs, lhsType );
	}

	/**
		<p>
		Determine if the RHS object can be assigned to the LHS type (as is,
		through widening, promotion, etc. ) and if so, return the 
		assignable form of the RHS.  Note that this is *not* a cast operation.
		Only assignments which are always legal (upcasts, promotion) are 
		passed.
		<p>

		In normal cases this functions as a simple check for assignability
		and the value is returned unchanged.  e.g. a String is assignable to
		an Object, but no conversion is necessary.  Similarly an int is 
		assignable to a long, so no conversion is done.
		In this sense assignability is in terms of what the Java reflection API
		will allow since the reflection api will do widening conversions in the 
		case of sets on fields and arrays.
		<p>
		The primary purpose of the abstraction "returning the assignable form"			abstraction is to allow non standard bsh assignment conversions. e.g.
		the wrapper stuff.  I'm still not sure how much of that we should
		be doing.
		<p>

		This method is used in many places throughout bsh including assignment
		operations and method selection.
		<p>

		@returns an assignable form of the RHS or throws EvalError
		@throws EvalError on non assignable
		@see BSHCastExpression.castObject();
	*/
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
    static Object getAssignableForm(Object rhs, Class lhsType)
		throws EvalError
    {
		Class originalType;

		if ( lhsType == null )
			throw new InterpreterError("check assignable to null type");

		if(rhs == null)
			throw new InterpreterError("null value in getAssignableForm");

		if(rhs == Primitive.VOID)
			throw new EvalError(
			"void cannot be used in an assignment statement");

		if (rhs == Primitive.NULL)
			if(!lhsType.isPrimitive())
				return rhs;
			else
				throw new EvalError(
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
		if ( Capabilities.haveProxyMechanism() && 
			lhsType.isInterface() && ( rhs instanceof bsh.This ) ) 
		{
			return ((bsh.This)rhs).getInterface( lhsType );
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
        throws IOException {

		// do something here
		s.defaultWriteObject();
	}

	/**
		Invoke a method in this namespace with the specified args and
		interpreter reference.  The caller namespace is set to this namespace.
		This is a convenience for users outside of this package.
		<p>
		Note: this method is primarily intended for use internally.  If you use
		this method outside of the bsh package and wish to use variables with
		primitive values you will have to wrap them using bsh.Primitive.
		@see bsh.Primitive
	*/
	public Object invokeMethod( 
		String methodName, Object [] args, Interpreter interpreter ) 
		throws EvalError
	{
		return invokeMethod( methodName, args, interpreter, null, null );
	}

	/**
		invoke a method in this namespace with the specified args,
		interpreter reference, and callstack
		This is a convenience for users outside of this package.
		<p>
		Note: this method is primarily intended for use internally.  If you use
		this method outside of the bsh package and wish to use variables with
		primitive values you will have to wrap them using bsh.Primitive.
		@param if callStack is null a new CallStack will be created and
			initialized with this namespace.
		@see bsh.Primitive
	*/
	public Object invokeMethod( 
		String methodName, Object [] args, Interpreter interpreter, 
		CallStack callstack, SimpleNode callerInfo ) 
		throws EvalError
	{
		if ( callstack == null ) {
			callstack = new CallStack();
			callstack.push( this );
		}

		// Look for method in the bsh object
        BshMethod meth = getMethod( methodName, Reflect.getTypes( args ) );
        if ( meth != null )
           return meth.invokeDeclaredMethod( args, interpreter, callstack, callerInfo );

		// Look for a default invoke() handler method
		meth = getMethod( "invoke", new Class [] { null, null } );

		// Call script "invoke( String methodName, Object [] args );
		if ( meth != null )
			return meth.invokeDeclaredMethod( 
				new Object [] { methodName, args }, interpreter, callstack, callerInfo );

		throw new EvalError( "No locally declared method: " 
			+ methodName + " in namespace: " + this );
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
		This supports name resolver caching, allowing Name objects to 
		cache info about the resolution of names for performance reasons.
		(This would be called getName() if it weren't already used for the
		simple name of the NameSpace)
	*/
	Name getNameResolver( String name ) {
		// no caching yet
		return new Name(this,name);
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

}

