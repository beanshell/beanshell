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

import java.util.Vector;

/*
	This shouldn't have to be public.	
	We should add to bsh.This allowing us to invoke a method.
	If we do that we should probably use it in Reflect.java
*/
class BSHMethodDeclaration extends SimpleNode
{
	String name;
	BSHFormalParameters params;
	BSHBlock block;
	Object returnType; 	// null (none), Primitive.VOID, or a Class

	BSHMethodDeclaration(int id)
	{
		super(id);
	}

	/**
		Evaluate the declaration of the method.  That is, determine the
		structure of the method and install it into the caller's namespace.
	*/
	public Object eval(NameSpace namespace, Interpreter interpreter)  
		throws EvalError
	{
		if(block == null) {
			// We will allow methods to be re-written.
			/*  
			if( namespace has method )
				throw new EvalError(
				"Method: " + name + " already defined in scope", this);
			*/

			if(jjtGetNumChildren() == 3)
			{
				returnType = 
					((BSHReturnType)jjtGetChild(0)).getReturnType(
						namespace, interpreter);
				params = (BSHFormalParameters)jjtGetChild(1);
				block = (BSHBlock)jjtGetChild(2);
			}
			else
			{
				params = (BSHFormalParameters)jjtGetChild(0);
				block = (BSHBlock)jjtGetChild(1);
			}
			params.eval(namespace, interpreter);
		}

		// Install an *instance* of this method in the namespace.
		// See notes in BshMethod 
		namespace.setMethod( name, new BshMethod( this, namespace ) );

		return Primitive.VOID;
	}

	public String toString() {
		return "MethodDeclaration: "+name;
	}
}
