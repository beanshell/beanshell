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

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;

class BSHAllocationExpression extends SimpleNode
{
    BSHAllocationExpression(int id) { super(id); }

    public Object eval(NameSpace namespace, Interpreter interpreter)  throws EvalError
    {
        // type is either a class name or a primitive type
        SimpleNode type = (SimpleNode)jjtGetChild(0);

        // args is either constructor arguments or array dimensions
        SimpleNode args = (SimpleNode)jjtGetChild(1);

        if ( type instanceof BSHAmbiguousName )
        {
            BSHAmbiguousName name = (BSHAmbiguousName)type;

            if(args instanceof BSHArguments)
                return objectAllocation(name, (BSHArguments)args, 
					namespace, interpreter );
            else
                return objectArrayAllocation(name, (BSHArrayDimensions)args, 
					namespace, interpreter );
        }
        else
            return primitiveArrayAllocation((BSHPrimitiveType)type,
                (BSHArrayDimensions)args, namespace, interpreter );
    }

    private Object objectAllocation(
		BSHAmbiguousName nameNode, BSHArguments argumentsNode, 
		NameSpace namespace, Interpreter interpreter 
	) 
		throws EvalError
    {
        Class type = nameNode.toClass(namespace);

		/* toClass throws this
        if (type == null)
            throw new EvalError(
				"Class " + nameNode.getName(namespace) + " not found.", this);
		*/

        Object[] args = argumentsNode.getArguments(namespace, interpreter);
        if(args == null)
            throw new EvalError("Trying to new a class...?", this);

		// Is an inner class style object allocation
		boolean hasBody = jjtGetNumChildren() > 2;

		if ( hasBody ) {
        	BSHBlock body = (BSHBlock)jjtGetChild(2);
			return constructWithBody( 
				type, args, body, namespace, interpreter );
		} else
			return constructObject( type, args );
    }

	private Object constructObject( Class type, Object[] args ) 
		throws EvalError
	{
        try {
            return Reflect.constructObject(type, args);
        } catch(ReflectError e) {
            throw new EvalError("Constructor error: " + e.getMessage(), this);
        } catch(InvocationTargetException e) {
            Interpreter.debug("The constructor threw an exception:\n\t" +
                e.getTargetException());
            throw new TargetError(e.getTargetException(), this);
        }
	}

	private Object constructWithBody( 
		Class type, Object[] args, BSHBlock body,
		NameSpace namespace, Interpreter interpreter ) 
		throws EvalError
	{
		if ( ! type.isInterface() )
			throw new EvalError(
				"BeanShell cannot extend class types: "+ type );

		NameSpace local = new NameSpace(namespace, "anonymous block object");
		body.eval( local, interpreter );
		return local.getThis(interpreter).getInterface( type );
	}

    private Object objectArrayAllocation(
		BSHAmbiguousName nameNode, BSHArrayDimensions dimensionsNode, 
		NameSpace namespace, Interpreter interpreter 
	) 
		throws EvalError
    {
        Class type = nameNode.toClass(namespace);
        if(type == null)
            throw new EvalError(
				"Class " + nameNode.getName(namespace) + " not found.", this);

        Object result = dimensionsNode.eval( type, namespace, interpreter );
        if(result != Primitive.VOID)
        {
            // check the BASE type for assignment compatibility
            // NOT IMPLEMENTED

            return result;
        }

        try {
            return Array.newInstance(type, dimensionsNode.dimensions);
        }
        catch(Exception e) {
            throw new EvalError(
				"Can't construct array: " + e.getMessage(), this);
        }
    }

    private Object primitiveArrayAllocation(
		BSHPrimitiveType typeNode, BSHArrayDimensions dimensionsNode, 
		NameSpace namespace, Interpreter interpreter 
	) 
		throws EvalError
    {
        Class type = typeNode.getType();

        Object result = dimensionsNode.eval( type, namespace, interpreter );
        if(result != Primitive.VOID) {
            // check the BASE type for assignment compatibility
            // NOT IMPLEMENTED

            return result;
        }

        try {
            return Array.newInstance(type, dimensionsNode.dimensions);
        } catch(Exception e) {
            throw new EvalError("Can't construct primitive array: " +
                e.getMessage(), this);
        }
    }
}
