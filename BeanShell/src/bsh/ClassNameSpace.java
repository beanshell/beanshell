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

import java.util.Vector;

/**
	A ClassNameSpace represents the body a scripted class definition or
	scripted class instance.  In the case of the class definition it serves as
	the Class object and holds the class initializer method (called before the
	constructor).
	<p/>
	When serving as a class definition, only static members are visible and
	attempting to access an instance member will cause a "can't reach instance
	from static context" exception.
	<p/>
	When serving as a class instance static members are dissallowed (except
	by the initializer) and are ignored, allowing them to be seen in the class
	definition.
	<p/>
	Class instances also serve as the top scope of instance variables declared
	within the class (by the initializer).
*/
/*
	This would obviously be cleaner if we made a second subclass of NameSpace:
	ClassInstanceNameSpace, however with code generation I think we'll only
	need one entity (the instance namespace) so let's leave both in here 
	for now. 
*/
class ClassNameSpace extends NameSpace 
{
	public static final int CLASS=1, INSTANCE=2;
	int type;
	//Class [] interfaces;

    public ClassNameSpace( 
		NameSpace parent, String name, int type ) 
		throws EvalError
	{
		super( parent, name );
		this.type = type;
    }

	/**
		Set the interfaces.  This is used by constructClassInstance() on 
		CLASS types.
	public setInterfaces( Class [] interfaces ) {
		this.interfaces = interfaces;
	}
	*/

	/**
		@return may return null
	Class [] getInterfaces() 
	{
		if ( getParent() instanceof ClassNameSpace
			&& ((ClassNameSpace)getParent()).isClass() 
		)
		{
			// collect all interfaces from super chain
			ClassNameSpace cns = (ClassNameSpace)getParent();
			Vector v = new Vector();
		}
		else
			return interfaces;
	}
	*/

    public void	setVariable( String name, Object value, boolean strictJava ) 
		throws UtilEvalError 
	{
		// class instance is top scope for loose vars
		if ( isClassInstance() ) {
			boolean recurse = false;
			super.setVariable( name, value, strictJava, recurse );
		}
		super.setVariable( name, value, strictJava );
	}

	/**
		This is a hook to allow ClassNameSpace to add functionality in
		getVariableImpl()
	*/
	protected boolean isVisible( Variable var )
		throws UtilEvalError
	{
		// If we are a class declaration space being asked for an instance
		// variable throw an exception.
		if ( isClass() && var != null && !var.hasModifier("static") )
			throw new UtilEvalError( "Can't reach instance var: "+var.name
					+" from static context: "+this);

		// If we are a class instance ignore any static variables and allow
		// them to be found in the static class space.
		if ( isClassInstance() && var != null && var.hasModifier("static") )
			return false;

		return true;
	}

	/**
		This is a hook to allow ClassNameSpace to add functionality to 
		getMethod() 
	*/
	protected boolean isVisible( BshMethod method )
		throws UtilEvalError
	{
		String name = null;
		if ( method != null )
			name = method.getName();

		// If we are a class def space being asked for an instance
		// method throw an exception.
		if ( isClass() && method != null && !method.hasModifier("static") 
			// Bit of a hack, allow us to see default constructor
			&& !name.equals( BSHClassDeclaration.CLASSINITNAME )
			// See regular constructors
			&& !nsName.equals( name )
		)
			throw new UtilEvalError(
				"Can't reach instance method: "+name
				+" from static context: "+this);

		// If we are a class instance ignore any static methods and allow
		// them to be found in the static class space.
		if ( isClassInstance() && method != null 
			&& method.hasModifier("static") 
		)
			return false;

		return true;
	}

	public boolean isClassInstance() { return type == INSTANCE ; }
	public boolean isClass() { return type == CLASS; }

	public This constructClassInstance( Object[] args, 
		Interpreter interpreter, CallStack callstack, SimpleNode callerInfo ) 
		throws EvalError
	{
		NameSpace classNameSpace = this;
		String className = classNameSpace.getName();

		// Get the default constructor
		BshMethod classInitializer = null;
		try {
			classInitializer = 
				classNameSpace.getMethod( 
					BSHClassDeclaration.CLASSINITNAME, new Class[0] );
		} catch ( UtilEvalError e ) { // shouldn't happen
			throw e.toEvalError(
				"Error getting default constructor", callerInfo, callstack );
		}
		if ( classInitializer == null )
			throw new EvalError("Unable to find initializer for class.",
				callerInfo, callstack);

		// Get the (non-initializer) constructor, if any
		Class [] sig = Reflect.getTypes( args );
		BshMethod constructor = null;
		try {
			constructor = 
				classNameSpace.getMethod( className, sig );
		} catch ( UtilEvalError e ) { // shouldn't happen
			throw e.toEvalError(
				"Error getting constructor", callerInfo, callstack );
		}
		// No constructor found
		if ( constructor == null )
		{
			// args constructor not found
			if ( args.length > 0 )
				throw new EvalError("Constructor not found: "+
					StringUtil.methodString( className, sig ),
					callerInfo, callstack );
			else
			// no args constructor missing. Ok if no other constructors exist
			{
				BshMethod [] methods = classNameSpace.getMethods();
				for(int i=0; i<methods.length; i++)
					if ( methods[i].getName().equals( className ) )
						throw new EvalError(
							"Default (no args) constructor not found: "+
							StringUtil.methodString( className, sig ), 
							callerInfo, callstack );
			}
		}

		// Invoke the constructors

		// Recurse to handle superclasses 
		NameSpace superNameSpace = null; 
		if ( classNameSpace.getParent() instanceof ClassNameSpace
			&& ((ClassNameSpace)classNameSpace.getParent()).isClass()
		)
		{
			// Call the superClass's default constructor

			// Note: this isn't always right -
			// We need to allow super() in the constructor()

			This superInstance = 
				((ClassNameSpace)classNameSpace.getParent())
				.constructClassInstance( 
					new Object[0], interpreter, callstack, callerInfo );

			superNameSpace = superInstance.getNameSpace();
		}

		// Chain the instance namespaces
		NameSpace instanceNameSpace;
		if ( superNameSpace != null )
			instanceNameSpace = new ClassNameSpace( 
				superNameSpace, className, INSTANCE );
		else
			instanceNameSpace = new ClassNameSpace( 
				classNameSpace, className, INSTANCE );

		callstack.push( instanceNameSpace );

		// Invoke the class initializer method
		try {
			classInitializer.invoke( 
				new Object[0], interpreter, callstack, callerInfo, 
				true/*overrideNameSpace*/ );
		} catch ( EvalError e ) {
			e.reThrow("Exception in default constructor: "+e);
		}

		// Call the specific constructor if any
		if ( constructor != null )
		{
			try {
				constructor.invoke( 
					args, interpreter, callstack, callerInfo, 
					true/*overrideNameSpace*/ );
			} catch ( EvalError e ) {
				e.reThrow("Exception in constructor: "+e);
			}
		}

		callstack.pop();
		
		// return the initialized object
		This instance = instanceNameSpace.getThis( interpreter );
		return instance;
	}

	protected void checkVariableModifiers( String name, Modifiers modifiers )
		throws UtilEvalError
	{
		// allow all valid inside class
	}


	protected void checkMethodModifiers( BshMethod method )
		throws UtilEvalError
	{
		// allow all valid inside class
	}


	public String toString() 
	{
		return
			"Scripted Class "
			+(isClassInstance() ?  "Instance " : "")
			+" : " +super.toString();
	}

	public static boolean isScriptedClass( Object obj )
	{
		return 
			obj instanceof This 
			&& ((This)obj).getNameSpace() instanceof ClassNameSpace 
			&& ((ClassNameSpace)((This)obj).getNameSpace()).isClass();
	}
}

