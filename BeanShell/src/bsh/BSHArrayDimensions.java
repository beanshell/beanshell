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

class BSHArrayDimensions extends SimpleNode
{
	public Class baseType;
    private int arrayDims;

	// The Length in each dimension
	// this value is only meaningfull after an eval()
    transient public int [] dimensions;  

    BSHArrayDimensions(int id) { super(id); }

    public void addArrayDimension() { arrayDims++; }

    public Object eval( Class type, NameSpace namespace, Interpreter interpreter ) throws EvalError {
		Interpreter.debug("array base type = "+type);
		baseType = type;
		return eval(namespace, interpreter);
	}

    public Object eval(NameSpace namespace, Interpreter interpreter)  throws EvalError
    {
        int n = jjtGetNumChildren();
        if(n > 0) {
            SimpleNode child = (SimpleNode)jjtGetChild(0);
            if(child instanceof BSHArrayInitializer)
            {
				if ( baseType == null )
					throw new EvalError( "Internal Array Eval err:  unknown base type", this);
                Object initValue = ((BSHArrayInitializer)child).eval(baseType, namespace, interpreter);

                Class arrayClass = initValue.getClass();
                dimensions = new int[
					Reflect.getArrayDimensions(arrayClass) ];

                // compare with number of dimensions explicitly specified
                if(dimensions.length != arrayDims)
                    throw new EvalError(
					"Incompatible initializer. Allocation calls for a " + 
					arrayDims + " dimensional array, but initializer is a " +
                        dimensions.length + " dimensional array", this);

				// fill in dimensions[] lengths
                Object arraySlice = initValue;
				for(int i = 0; i < dimensions.length; i++) {
					dimensions[i] = Array.getLength( arraySlice );
					if ( dimensions[i] > 0 )
						arraySlice = Array.get(arraySlice, 0);
				}

                return initValue;
            }
            else {
                dimensions = new int[n];
                for(int i = 0; i < dimensions.length; i++)
                {
                    try
                    {
                        Object length = ((SimpleNode)jjtGetChild(i)).eval(namespace, interpreter);
                        dimensions[i] = ((Primitive)length).intValue();
                    }
                    catch(Exception e)
                    {
                        throw new EvalError(
							"Array index: " + i + 
							" does not evaluate to an integer", this);
                    }
                }
            }
        }

        return Primitive.VOID;
    }
}
