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
	<p>

	The issue is that when a method is located in a subordinate namespace or
	invoked from an arbitrary namespace it must nontheless execute with its
	'super' as the context in which it was declared.

	i.e.
	The local method context is a child namespace of the declaring namespace.
*/
public class BshMethod 
	implements /*bsh.Reflect.MethodInvoker,*/ java.io.Serializable 
{
	BSHMethodDeclaration method;

	/* 
		I believe this is always the namespace in which the method is
		defined...  It is a back-reference for the node, which needs to
		execute under this namespace.
		So it is not necessary to declare this transient, because we can
		only be saved as part of our namespace anyway... (currently).
	*/
	NameSpace declaringNameSpace;

	private Class [] argTypes;

	BshMethod( 
		BSHMethodDeclaration method, NameSpace declaringNameSpace ) 
	{
		this.method = method;
		this.declaringNameSpace = declaringNameSpace;
		
	}

	/**
		@deprecated  See #getArgumentTypes()
	*/
	public Class [] getArgTypes() 
	{
		return getArgumentTypes();
	}

	/**
		Get the argument types of this method.
		loosely typed (untyped) arguments will be represented by null argument
		types.
	*/
	/*
		Note: bshmethod needs to re-evaluate arg types here
		This is broken
	*/
	public Class [] getArgumentTypes() 
	{
		if ( argTypes == null )
			// should re-eval here...
			argTypes = method.params.argTypes ;

		return argTypes;
	}

	public String getName() {
		return method.name;
	}

	/**
		Invoke the declared method with the specified arguments and interpreter
		reference.  This is the simplest form of invoke() for BshMethod 
		intended to be used in reflective style access to bsh scripts.
	*/
	public Object invoke( 
		Object[] argValues, Interpreter interpreter ) 
		throws EvalError 
	{
		return invoke( argValues, interpreter, null, null );
	}

	/**
		Invoke the declared method with the specified arguments, interpreter
		reference, and callstack.
		<p/>
		Note: this form of invoke() uses a null Node for the caller and a null
		node for the CallStack.  This method is for scripts performing 
		relective style access to scripted methods.
	*/
	public Object invoke( 
		Object[] argValues, Interpreter interpreter, CallStack callstack ) 
		throws EvalError 
	{
		return invoke( argValues, interpreter, callstack, null );
	}

	/**
		Invoke the bsh method with the specified args, interpreter ref,
		and callstack.
		callerInfo is the node representing the method invocation
		It is used primarily for debugging in order to provide access to the 
		text of the construct that invoked the method through the namespace.
		@param callerInfo is the BeanShell AST node representing the method 
			invocation.  It is used to print the line number and text of 
			errors in EvalError exceptions.  If the node is null here error
			messages may not be able to point to the precise location and text
			of the error.
		@param callstack is the callstack.  If callstack is null a new one
			will be created with the declaring namespace of the method on top
			of the stack (i.e. it will look for purposes of the method 
			invocation like the method call occurred in the declaring 
			(enclosing) namespace in which the method is defined).
	*/
	public Object invoke( 
		Object[] argValues, Interpreter interpreter, CallStack callstack,
			SimpleNode callerInfo ) 
		throws EvalError 
	{
		// If null callstack
		if ( callstack == null )
			callstack = new CallStack( declaringNameSpace );

		if ( argValues == null )
			argValues = new Object [] { };

		// Cardinality (number of args) mismatch
		if ( argValues.length != method.params.numArgs ) {
			// look for help string
			try {
				// should check for null namespace here
				String help = 
					(String)declaringNameSpace.get(
					"bsh.help."+method.name, interpreter );

				interpreter.println(help);
				return Primitive.VOID;
			} catch ( Exception e ) {
				throw new EvalError( 
					"Wrong number of arguments for local method: " 
					+ method.name, callerInfo, callstack );
			}
		}

		// Make the local namespace for the method invocation
		NameSpace localNameSpace = new NameSpace( 
			declaringNameSpace, method.name );
		localNameSpace.setNode( callerInfo );
		localNameSpace.isMethod = true;

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
				catch( UtilEvalError e) {
					throw new EvalError(
						"Invalid argument: " 
						+ "`"+method.params.argNames[i]+"'" + " for method: " 
						+ method.name + " : " + 
						e.getMessage(), callerInfo, callstack );
				}
				try {
					localNameSpace.setTypedVariable( method.params.argNames[i], 
						method.params.argTypes[i], argValues[i], false);
				} catch ( UtilEvalError e2 ) {
					throw e2.toEvalError( "Typed method parameter assignment", 
						callerInfo, callstack  );
				}
			} 
			// Set untyped variable
			else  // untyped param
			{
				// getAssignable would catch this for typed param
				if ( argValues[i] == Primitive.VOID)
					throw new EvalError(
						"Undefined variable or class name, parameter: " +
						method.params.argNames[i] + " to method: " 
						+ method.name, callerInfo, callstack );
				else
					try {
						localNameSpace.setVariable(
							method.params.argNames[i], argValues[i],
							interpreter.getStrictJava() );
					} catch ( UtilEvalError e3 ) {
						throw e3.toEvalError( callerInfo, callstack );
					}
			}
		}

		// Push the new namespace on the call stack
		callstack.push( localNameSpace );
		// Invoke the method
		Object ret = method.block.eval( callstack, interpreter, true );
		// save the callstack including the called method, just for error mess
		CallStack returnStack = callstack.copy();
		// pop back to caller namespace
		callstack.pop();

		ReturnControl retControl = null;
		if ( ret instanceof ReturnControl )
		{
			retControl = (ReturnControl)ret;

			// Method body can only use 'return' statment type return control.
			if ( retControl.kind == retControl.RETURN )
				ret = ((ReturnControl)ret).value;
			else 
				// retControl.returnPoint is the Node of the return statement
				throw new EvalError("continue or break in method body", 
					retControl.returnPoint, returnStack );

			// Check for explicit return of value from void method type.
			// retControl.returnPoint is the Node of the return statement
			if ( method.returnType == Primitive.VOID && ret != Primitive.VOID )
				throw new EvalError( "Cannot return value from void method", 
				retControl.returnPoint, returnStack);
		}

		if ( method.returnType != null )
		{
			// If return type void, return void as the value.
			if ( method.returnType == Primitive.VOID )
				return Primitive.VOID;

			// return type is a class
			try {
				ret = NameSpace.getAssignableForm(
					ret, (Class)method.returnType);
			} catch( UtilEvalError e ) 
			{
				// Point to return statement point if we had one.
				// (else it was implicit return? What's the case here?)
				SimpleNode node = callerInfo;
				if ( retControl != null )
					node = retControl.returnPoint;
				throw e.toEvalError(
					"Incorrect type returned from method: " 
					+ method.name + e.getMessage(), node, callstack );
			}
		}

		return ret;
	}

	public String toString() {
		return "Bsh Method: "+method.name;
	}
}
