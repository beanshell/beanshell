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

package bsh;

/**
	This represents an *instance* of a bsh method declaration in a particular
	namespace.  This is a thin wrapper around the BSHMethodDeclaration
	with a pointer to the declaring namespace.

	The issue is that when a method is located in a subordinate namespace or
	invoked from an arbitrary namespace it must nontheless execute with its
	'super' as the context in which it was declared.

	i.e.
	The local method context is a child namespace of the declaring namespace.
*/
class BshMethod implements java.io.Serializable 
{
	public BSHMethodDeclaration method;

	/* 
		I believe this is always the namespace in which the method is
		defined...  It is a back-reference for the node, which needs to
		execute under this namespace.
		So it is not necessary to declare this transient, because we can
		only be saved as part of our namespace anyway... (currently).
	*/
	NameSpace declaringNameSpace;

	private Class [] argTypes;

	public BshMethod( 
		BSHMethodDeclaration method, NameSpace declaringNameSpace ) 
	{
		this.method = method;
		this.declaringNameSpace = declaringNameSpace;
		
	}

	/**
		Note: bshmethod needs to re-evaluate arg types here
		This is broken
	*/
	public Class [] getArgTypes() {
		if ( argTypes == null )
			// should re-eval here...
			argTypes = method.params.argTypes ;

		return argTypes;
	}

	/**
		Invoke the bsh method with the specified args, interpreter ref,
		and callstack.  This method 
	public Object invokeDeclaredMethod( 
		Object[] argValues, Interpreter interpreter, CallStack callstack )
		throws EvalError 
	{
		return invokeDeclaredMethod( argValues, interpreter, callstack, null );
	}
	*/

	/**
		Invoke the bsh method with the specified args, interpreter ref,
		and callstack.
		callerInfo is the node representing the method invocation
		It is used primarily for debugging in order to provide access to the 
		text of the construct that invoked the method through the namespace.
		@param callerInfo is the node representing the method invocation
			This is used primarily for debugging and may be null.
		@param callstack is the callstack of course.  If you are using a 
		hacked version of BeanShell that exposed this method take a look
		at NameSpace invokeMethod to see how to make a fake callstack...
	*/
	public Object invokeDeclaredMethod( 
		Object[] argValues, Interpreter interpreter, CallStack callstack,
			SimpleNode callerInfo ) 
		throws EvalError 
	{

		if ( argValues == null )
			argValues = new Object [] { };

		// Cardinality (number of args) mismatch
		if ( argValues.length != method.params.numArgs ) {
			// look for help string
			try {
// should check for null here
				String help = 
					(String)declaringNameSpace.get(
					"bsh.help."+method.name, interpreter );

				interpreter.println(help);
				return Primitive.VOID;
			} catch ( Exception e ) {
				throw new EvalError( 
					"Wrong number of arguments for local method: " 
					+ method.name, method);
			}
		}

		// Make the local namespace for the method invocation
		NameSpace localNameSpace = new NameSpace( 
			declaringNameSpace, method.name );
		localNameSpace.setNode( callerInfo );

		// set the method parameters in the local namespace
		for(int i=0; i<method.params.numArgs; i++)
		{
			// Set typed variable
			if ( method.params.argTypes[i] != null ) 
			{
				try {
					argValues[i] = NameSpace.getAssignableForm(argValues[i],
					    method.params.argTypes[i]);
				}
				catch(EvalError e) {
					throw new EvalError(
						"Incorrect argument type for parameter: " 
						+ method.params.argNames[i] + " in method: " 
						+ method.name + ": " + 
						e.getMessage(), method);
				}
				localNameSpace.setTypedVariable( method.params.argNames[i], 
					method.params.argTypes[i], argValues[i], false);
			} 
			// Set untyped variable
			else  // untyped param
				// checkAssignable would catch this for typed param
				if ( argValues[i] == Primitive.VOID)
					throw new EvalError("Attempt to pass void parameter: " +
						method.params.argNames[i] + " to method: " 
						+ method.name, method);
				else
					localNameSpace.setVariable(
						method.params.argNames[i], argValues[i]);
		}

		// Push the new namespace on the call stack
		callstack.push( localNameSpace );
		// Invoke the method
		Object ret = method.block.eval( callstack, interpreter, true );
		// pop back to caller namespace
		callstack.pop();

		if ( ret instanceof ReturnControl )
		{
			ReturnControl rs = (ReturnControl)ret;
			if(rs.kind == rs.RETURN)
				ret = ((ReturnControl)ret).value;
			else 
				throw new EvalError("continue or break in method body", method);
		}

		// there should be a check in here for an explicit value return 
		// from a void type method... (throw evalerror)

		if(method.returnType != null)
		{
			// if void return type throw away any value
			// ideally, we'd error on an explicit 'return' of value
			if(method.returnType == Primitive.VOID)
				return method.returnType;

			// return type is a class
			try {
				ret = NameSpace.getAssignableForm(
					ret, (Class)method.returnType);
			}
			catch(EvalError e) {
				throw new EvalError(
					"Incorrect type returned from method: " 
					+ method.name + e.getMessage(), method);
			}
		}

		return ret;
	}

	public String toString() {
		return "Bsh Method: "+method.name;
	}
}
