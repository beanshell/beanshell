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

class BSHArrayInitializer extends SimpleNode
{
    BSHArrayInitializer(int id) { super(id); }

    public Object eval(NameSpace namespace, Interpreter interpreter)  throws EvalError {
		throw new EvalError("array initializer Internal error, no base type");
	}

    public Object eval( Class baseType, NameSpace namespace, Interpreter interpreter ) throws EvalError
    {
        int n = jjtGetNumChildren();
        Class initializerType = null;
        Object initializers = null;

        for(int i = 0; i < n; i++)
        {
            Object currentInitializer = ((SimpleNode)jjtGetChild(i)).eval(namespace, interpreter);

            // allocate the array to store the initializers
            if(initializers == null)
            {
                // determine the type of the initializer
                if(currentInitializer instanceof Primitive)
                    initializerType = ((Primitive)currentInitializer).getType();
                else
                    initializerType = currentInitializer.getClass();

                initializers = Array.newInstance(initializerType, jjtGetNumChildren());
            }

            try
            {
                // store the initializer in the array
                if((initializerType.isArray()) || (!initializerType.isPrimitive()))
                    ((Object[])initializers)[i] = currentInitializer;
                else
                {
                    Object primitive = ((Primitive)currentInitializer).getValue();

                    if(initializerType == Boolean.TYPE)
                        ((boolean[])initializers)[i] = ((Boolean)primitive).booleanValue();
                    else if(initializerType == Character.TYPE)
                        ((char[])initializers)[i] = ((Character)primitive).charValue();
                    else if(initializerType == Byte.TYPE)
                        ((byte[])initializers)[i] = ((Byte)primitive).byteValue();
                    else if(initializerType == Short.TYPE)
                        ((short[])initializers)[i] = ((Short)primitive).shortValue();
                    else if(initializerType == Integer.TYPE)
                        ((int[])initializers)[i] = ((Integer)primitive).intValue();
                    else if(initializerType == Long.TYPE)
                        ((long[])initializers)[i] = ((Long)primitive).longValue();
                    else if(initializerType == Float.TYPE)
                        ((float[])initializers)[i] = ((Float)primitive).floatValue();
                    else if(initializerType == Double.TYPE)
                        ((double[])initializers)[i] = ((Double)primitive).doubleValue();
                }
            }
            catch(Exception e)
            {
                String lhsType = Reflect.normalizeClassName(initializerType);

                String rhsType;
                if(currentInitializer instanceof Primitive)
                    rhsType = ((Primitive)currentInitializer).getType().getName();
                else
                    rhsType = Reflect.normalizeClassName(currentInitializer.getClass());

                throw new EvalError ("Incompatible types in initializer. Initializer contains both " + lhsType + " and " + rhsType, this);
            }
        }

		// zero length array init
		if ( initializers == null )
			initializers = Array.newInstance( baseType, 0 );

        return initializers;
    }
}
