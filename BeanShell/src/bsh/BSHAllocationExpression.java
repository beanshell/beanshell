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
