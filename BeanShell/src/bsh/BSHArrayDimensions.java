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

/**
	The name of this class is somewhat misleading.  This covers both the case
	where there is an array initializer and 
*/
class BSHArrayDimensions extends SimpleNode
{
	public Class baseType;
    private int arrayDims;

	/** The Length in each dimension.  This value set by the eval() */
	// is it ok to cache this here?
	// it can't change, right?
	public int [] dimensions;  

    BSHArrayDimensions(int id) { super(id); }

    public void addArrayDimension() { arrayDims++; }

    public Object eval( 
			Class type, CallStack callstack, Interpreter interpreter ) 
		throws EvalError 
	{
		if ( Interpreter.DEBUG ) Interpreter.debug("array base type = "+type);
		baseType = type;
		return eval( callstack, interpreter );
	}

	/**
		Evaluate the structure of the array in one of two ways:

			a) an initializer exists, evaluate it and return
			the fully constructed array object, also record the dimensions
			of that array
			
			b) evaluate and record the lengths in each dimension and 
			return void.

		The structure of the array dims is maintained in dimensions.
	*/
    public Object eval( CallStack callstack, Interpreter interpreter )  
		throws EvalError
    {
		SimpleNode child = (SimpleNode)jjtGetChild(0);

		if (child instanceof BSHArrayInitializer)
		// evaluate the initializer and the dimensions it returns
		{
			if ( baseType == null )
				throw new EvalError( 
					"Internal Array Eval err:  unknown base type", 
					this, callstack );

			Object initValue = ((BSHArrayInitializer)child).eval(
				baseType, arrayDims, callstack, interpreter);

			Class arrayClass = initValue.getClass();
			dimensions = new int[
				Reflect.getArrayDimensions(arrayClass) ];

			// compare with number of dimensions explicitly specified
			if (dimensions.length != arrayDims)
				throw new EvalError(
				"Incompatible initializer. Allocation calls for a " + 
				arrayDims + " dimensional array, but initializer is a " +
					dimensions.length + " dimensional array", this, callstack );

			// fill in dimensions[] lengths
			Object arraySlice = initValue;
			for(int i = 0; i < dimensions.length; i++) {
				dimensions[i] = Array.getLength( arraySlice );
				if ( dimensions[i] > 0 )
					arraySlice = Array.get(arraySlice, 0);
			}

			return initValue;
		}
		else 
		// evaluate the dimensions of the array
		{
			dimensions = new int[ jjtGetNumChildren() ];
			for(int i = 0; i < dimensions.length; i++)
			{
				try {
					Object length = ((SimpleNode)jjtGetChild(i)).eval(
						callstack, interpreter);
					dimensions[i] = ((Primitive)length).intValue();
				}
				catch(Exception e)
				{
					throw new EvalError(
						"Array index: " + i + 
						" does not evaluate to an integer", this, callstack );
				}
			}
		}

        return Primitive.VOID;
    }
}
