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
