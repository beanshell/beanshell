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
