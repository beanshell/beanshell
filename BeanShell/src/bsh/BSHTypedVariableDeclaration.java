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

class BSHTypedVariableDeclaration extends SimpleNode
{
    public boolean isFinal;
	
    BSHTypedVariableDeclaration(int id) { super(id); }

	/**
		evaluate the type and one or more variable declarators, e.g.:
			int a, b=5, c;

	*/
    public Object eval( CallStack callstack, Interpreter interpreter)  
		throws EvalError
    {
		try {
			NameSpace namespace = callstack.top();
			BSHType typeNode = ((BSHType)jjtGetChild(0));
			Class type = typeNode.getType( callstack, interpreter );

			int n = jjtGetNumChildren();
			for (int i = 1; i < n; i++)
			{
				BSHVariableDeclarator dec = 
					(BSHVariableDeclarator)jjtGetChild(i);

				// Type node is passed down the chain for array initializers
				// which need it under some circumstances
				Object value = dec.eval( typeNode, callstack, interpreter);

				// simple declaration with no value, e.g. int a;
				if ( value == null ) 
				{
					// Leave the value as null.
					// This will prompt defaulting in setTypedVariable
				}
				else 
				// true null value being assigned
				if ( value == Primitive.NULL ) {
					// leave as Primitive.NULL
				}
				else
				// allow specific numeric conversions on declaration
				if ( canCastToDeclaredType( value, type ) )
					try {
						value = BSHCastExpression.castObject( value, type );
					} catch ( UtilEvalError e ) { 
						throw e.toEvalError( this, callstack  ); 
					}
				else {
					// leave value alone
				}

				try {
					namespace.setTypedVariable( dec.name, type, value, isFinal);
				} catch ( UtilEvalError e ) { 
					throw e.toEvalError( this, callstack ); 
				}
			
			}
		} catch ( EvalError e ) {
			e.reThrow( "Typed variable declaration" );
		}

        return Primitive.VOID;
    }

	/**
		Determine if a cast would be legitimate in order to handle the 
		special cases where a numeric declared var is assigned a type larger 
		than it can handle. (JLS cite??)

			byte b = 5;
			byte b1 = 5*10;

		Normally the above would be int types.
	*/
	/*
		Note: in theory this probably shouldn't be considered a cast, but 
		should be taken into account during literal and expression evaluation
		where the result type is guided by the context.  However this is much
		simpler to deal with and there is no other use for the other that I'm
		aware of.
	*/
	boolean canCastToDeclaredType( Object value, Class toType ) {
		if ( !(value instanceof Primitive) )
			return false;
		Class fromType = ((Primitive)value).getType();
		
		if ( (toType==Byte.TYPE || toType==Short.TYPE || toType==Character.TYPE)
			&& fromType == Integer.TYPE 
		)
			return true;
		else
			return false;
	}

}
