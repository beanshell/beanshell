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

package bsh;

/**
	This represents an *instance* of a bsh method declaration in a particular
	namespace.  This is a thin wrapper around the BSHMethodDeclaration
	with a pointer to the declaring namespace.

	The issue is that when a method is located in a derived namespace or
	called from an arbitrary namespace it must nontheless execute with its
	'super' as the context in which it was declared.
*/
class BshMethod implements java.io.Serializable 
{
	public BSHMethodDeclaration method;
	NameSpace declaringNameSpace;

	public BshMethod( 
		BSHMethodDeclaration method, NameSpace declaringNameSpace ) 
	{
		this.method = method;
		this.declaringNameSpace = declaringNameSpace;
		
	}

	/**
		Invoke the bsh method with the specified args and interpreter ref.
	*/
	public Object invokeDeclaredMethod( 
		Object[] argValues, Interpreter interpreter ) throws EvalError 
	{

		if ( argValues == null )
			argValues = new Object [] { };

		// Cardinality (number of args) mismatch
		if ( argValues.length != method.params.numArgs ) {
			// look for help string
			try {
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

		// Make the local namespace of the method invocation
		NameSpace localNameSpace = new NameSpace( 
			declaringNameSpace, method.name );

		for(int i=0; i<method.params.numArgs; i++)
		{
			// Set typed variable
			if ( method.params.argTypes[i] != null ) 
			{
				try {
					argValues[i] = NameSpace.checkAssignableFrom(argValues[i],
					    method.params.argTypes[i]);
				}
				catch(EvalError e) {
					throw new EvalError(
						"Incorrect argument type for parameter: " +
						method.params.argNames[i] + " in method: " + method.name + ": " + 
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

		Object ret = method.block.eval( 
			localNameSpace, interpreter );

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
			try
			{
				ret = NameSpace.checkAssignableFrom(ret, (Class)method.returnType);
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
