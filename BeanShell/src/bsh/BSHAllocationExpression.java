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

/**
	New object, new array, or inner class style allocation with body.
*/
class BSHAllocationExpression extends SimpleNode
{
    BSHAllocationExpression(int id) { super(id); }

    public Object eval( CallStack callstack, Interpreter interpreter) 
		throws EvalError
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
					callstack, interpreter );
            else
                return objectArrayAllocation(name, (BSHArrayDimensions)args, 
					callstack, interpreter );
        }
        else
            return primitiveArrayAllocation((BSHPrimitiveType)type,
                (BSHArrayDimensions)args, callstack, interpreter );
    }

    private Object objectAllocation(
		BSHAmbiguousName nameNode, BSHArguments argumentsNode, 
		CallStack callstack, Interpreter interpreter 
	) 
		throws EvalError
    {
		NameSpace namespace = callstack.top();
        Class type = nameNode.toClass(namespace);

		/* toClass throws this
        if (type == null)
            throw new EvalError(
				"Class " + nameNode.getName(namespace) + " not found.", this);
		*/

        Object[] args = argumentsNode.getArguments(callstack, interpreter);
        if(args == null)
            throw new EvalError("Trying to new a class...?", this);

		// Is an inner class style object allocation
		boolean hasBody = jjtGetNumChildren() > 2;

		if ( hasBody ) {
        	BSHBlock body = (BSHBlock)jjtGetChild(2);
			return constructWithBody( 
				type, args, body, callstack, interpreter );
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
            throw new TargetError(
				"Object constructor", e.getTargetException(), this, true);
        }
	}

	private Object constructWithBody( 
		Class type, Object[] args, BSHBlock body,
		CallStack callstack, Interpreter interpreter ) 
		throws EvalError
	{
		if ( ! type.isInterface() )
			throw new EvalError(
				"BeanShell cannot extend class types: "+ type );

		NameSpace namespace = callstack.top();
// Maybe we should swap in local namespace for the top?
// who is the caller?
		NameSpace local = new NameSpace(namespace, "anonymous block object");
		callstack.push(local);
		body.eval( callstack, interpreter, true );
		callstack.pop();
		return local.getThis(interpreter).getInterface( type );
	}

// combine part of this with primitiveArrayAllocation
    private Object objectArrayAllocation(
		BSHAmbiguousName nameNode, BSHArrayDimensions dimensionsNode, 
		CallStack callstack, Interpreter interpreter 
	) 
		throws EvalError
    {
		NameSpace namespace = callstack.top();
        Class type = nameNode.toClass(namespace);
        if(type == null)
            throw new EvalError(
				"Class " + nameNode.getName(namespace) + " not found.", this);

		// dimensionsNode can return either an intialized version or none.
        Object result = dimensionsNode.eval( type, callstack, interpreter );
        if(result != Primitive.VOID)
            return result;
		else
			return arrayNewInstance( type, dimensionsNode );
    }

// combine part of this with objectArrayAllocation
    private Object primitiveArrayAllocation(
		BSHPrimitiveType typeNode, BSHArrayDimensions dimensionsNode, 
		CallStack callstack, Interpreter interpreter 
	) 
		throws EvalError
    {
        Class type = typeNode.getType();

		// dimensionsNode can return either an intialized version or none.
        Object result = dimensionsNode.eval( type, callstack, interpreter );
        if (result != Primitive.VOID) 
            return result;

		return arrayNewInstance( type, dimensionsNode );
    }

	private Object arrayNewInstance( 
		Class type, BSHArrayDimensions dimensionsNode )
		throws EvalError
	{
        try {
            return Array.newInstance(type, dimensionsNode.dimensions);
        } catch( NegativeArraySizeException e1) {
			throw new TargetError("Negative Array Size", e1);
        } catch(Exception e) {
            throw new EvalError("Can't construct primitive array: " +
                e.getMessage(), this);
        }
	}
}
