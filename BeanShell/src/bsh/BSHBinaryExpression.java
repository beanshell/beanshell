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

/**
*/
class BSHBinaryExpression extends SimpleNode 
	implements ParserConstants 
{
    public int kind;

    BSHBinaryExpression(int id) { super(id); }

    public Object eval(NameSpace namespace, Interpreter interpreter)  throws EvalError
    {
        Object lhs = ((SimpleNode)jjtGetChild(0)).eval(namespace, interpreter);

		/*
			Doing instanceof?  Next node is a type.
		*/
        if(kind == INSTANCEOF)
        {
            if(lhs instanceof Primitive)
            {
				// primitive can't be instance of anything, right?
				return new Primitive(false);

            }

            Class rhs = ((BSHType)jjtGetChild(1)).getType(namespace);
            boolean ret = (Reflect.isAssignableFrom(rhs, lhs.getClass()));
            return new Primitive(ret);
        }


		// The following two boolean checks were tacked on.
		// This could probably be smoothed out.

		/*
			Look ahead and short circuit evaluation of the rhs if:
				we're a boolean AND and the lhs is false.
		*/
		if ( kind == BOOL_AND || kind == BOOL_ANDX ) {
			Object obj = lhs;
			if ( isPrimitiveValue(lhs) )
				obj = ((Primitive)lhs).getValue();
			if ( obj instanceof Boolean && 
				( ((Boolean)obj).booleanValue() == false ) )
				return new Primitive(false);
		}
		/*
			Look ahead and short circuit evaluation of the rhs if:
				we're a boolean AND and the lhs is false.
		*/
		if ( kind == BOOL_OR || kind == BOOL_ORX ) {
			Object obj = lhs;
			if ( isPrimitiveValue(lhs) )
				obj = ((Primitive)lhs).getValue();
			if ( obj instanceof Boolean && 
				( ((Boolean)obj).booleanValue() == true ) )
				return new Primitive(true);
		}

		// end stuff that was tacked on.

		/*
			Are both the lhs and rhs either wrappers or primitive values?
		*/
		boolean isLhsWrapper = isWrapper( lhs );
        Object rhs = ((SimpleNode)jjtGetChild(1)).eval(namespace, interpreter);
		boolean isRhsWrapper = isWrapper( rhs );

		if ( ( isLhsWrapper || isPrimitiveValue( lhs ) ) &&
			 ( isRhsWrapper || isPrimitiveValue( rhs ) ) )
        {
			// Special case for EQ on two wrapper objects
			if ( isLhsWrapper && isRhsWrapper && kind == EQ ) {
				/*  
					Don't auto-unwrap wrappers (preserve identity semantics)
					FALL THROUGH TO OBJECT OPERATIONS BELOW.
				*/
			} else
				try {
					return Primitive.binaryOperation(lhs, rhs, kind);
				} catch ( TargetError e ) {
					// this doesn't really help...  need to catch it higher?
					e.reThrow( this );
				}
        }

		/*
			Treat lhs and rhs as arbitrary objects
		*/
        switch(kind)
        {
            case EQ:
                return new Primitive((lhs == rhs));

            case NE:
                return new Primitive((lhs != rhs));

            case PLUS:
                if(lhs instanceof String || rhs instanceof String)
                    return lhs.toString() + rhs.toString();

            // FALL THROUGH TO DEFAULT CASE!!!

            default:
                if(lhs instanceof Primitive || rhs instanceof Primitive)
                    if(lhs == Primitive.VOID || rhs == Primitive.VOID)
                        throw new EvalError("illegal use of undefined object or 'void' literal", this);
                    else if(lhs == Primitive.NULL || rhs == Primitive.NULL)
                        throw new EvalError("illegal use of null object or 'null' literal", this);

                throw new EvalError("Operator: '" + tokenImage[kind] +
                    "' inappropriate for objects", this);
        }
    }


	/*
		object is a non-null non-void Primitive type
	*/
	private boolean isPrimitiveValue( Object obj ) {
        return ( (obj instanceof Primitive) && (obj != Primitive.VOID && 
		obj != Primitive.NULL) );
	}

	/*
		object is a java.lang wrapper for boolean, char, or number type
	*/
	private boolean isWrapper( Object obj ) {
        return ( obj instanceof Boolean || 
			obj instanceof Character || obj instanceof Number );
	}
}
