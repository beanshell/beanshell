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

    public Object eval(CallStack callstack, Interpreter interpreter)  
		throws EvalError 
	{
		throw new EvalError("array initializer Internal error, no base type");
	}

	/**
		Construct the array from the initializer syntax.
		baseType may be null to indicate an untyped allocation, in which case
		the base type is determined as follows:
			< unimplemented >
	*/
    public Object eval( 
		Class baseType, CallStack callstack, Interpreter interpreter ) 
		throws EvalError
    {
        int numChildren = jjtGetNumChildren();
		// allocate the array to store the initializers
        Object initializers = 
			Array.newInstance( baseType, numChildren );

		// Evaluate the initializers
        for(int i = 0; i < numChildren; i++)
        {
            Object currentInitializer = 
				((SimpleNode)jjtGetChild(i)).eval( callstack, interpreter);

			if ( currentInitializer == Primitive.VOID )
				throw new EvalError(
					"Void in array initializer, position"+i, this);

			// unwrap primitive to the wrapper type
			Object value;
			if ( currentInitializer instanceof Primitive )
				value = ((Primitive)currentInitializer).getValue();
			else
				value = currentInitializer;

			// store the value in the array
            try {
				Array.set(initializers, i, value);

            } catch( IllegalArgumentException e ) {
				Interpreter.debug("illegal arg"+e);
				throwTypeError( baseType, currentInitializer, i );
            } catch( ArrayStoreException e ) { // I think this can happen
				Interpreter.debug("arraystore"+e);
				throwTypeError( baseType, currentInitializer, i );
            }
        }

        return initializers;
    }

	private void throwTypeError( 
		Class baseType, Object initializer, int argNum ) 
		throws EvalError
	{
		String lhsType = Reflect.normalizeClassName(baseType);

		String rhsType;
		if (initializer instanceof Primitive)
			rhsType = 
				((Primitive)initializer).getType().getName();
		else
			rhsType = Reflect.normalizeClassName(
				initializer.getClass());

		throw new EvalError ( "Incompatible type: " + rhsType 
			+" in initializer of array type: "+ baseType
			+" at position: "+argNum, this );
	}
}
