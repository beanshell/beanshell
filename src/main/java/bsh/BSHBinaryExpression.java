/*****************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one                *
 * or more contributor license agreements.  See the NOTICE file              *
 * distributed with this work for additional information                     *
 * regarding copyright ownership.  The ASF licenses this file                *
 * to you under the Apache License, Version 2.0 (the                         *
 * "License"); you may not use this file except in compliance                *
 * with the License.  You may obtain a copy of the License at                *
 *                                                                           *
 *     http://www.apache.org/licenses/LICENSE-2.0                            *
 *                                                                           *
 * Unless required by applicable law or agreed to in writing,                *
 * software distributed under the License is distributed on an               *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY                    *
 * KIND, either express or implied.  See the License for the                 *
 * specific language governing permissions and limitations                   *
 * under the License.                                                        *
 *                                                                           *
 *                                                                           *
 * This file is part of the BeanShell Java Scripting distribution.           *
 * Documentation and updates may be found at http://www.beanshell.org/       *
 * Patrick Niemeyer (pat@pat.net)                                            *
 * Author of Learning Java, O'Reilly & Associates                            *
 *                                                                           *
 *****************************************************************************/



package bsh;

/**
    Implement binary expressions...
    @see Primitive.binaryOperation
*/
class BSHBinaryExpression extends SimpleNode implements ParserConstants {
    public int kind;

    BSHBinaryExpression(int id) { super(id); }

    public Object eval( CallStack callstack, Interpreter interpreter)
        throws EvalError
    {
        Object lhs = ((SimpleNode)jjtGetChild(0)).eval(callstack, interpreter);

        /*
            Doing instanceof?  Next node is a type.
        */
        if (kind == INSTANCEOF)
        {
            // null object ref is not instance of any type
            if ( lhs == Primitive.NULL )
                return Primitive.FALSE;

            Class<?> rhs = ((BSHType)jjtGetChild(1)).getType(
                callstack, interpreter );

            /*
                Primitive (number or void) is not normally an instanceof
                anything.  But for convenience we'll test true for the
                bsh.Primitive class.
                i.e. (5 instanceof bsh.Primitive) will be true
                otherwise unwrap the primitive and test assignable
            */
            if ( lhs instanceof Primitive )
                if ( rhs == bsh.Primitive.class )
                    return Primitive.TRUE;
                else
                    lhs = Primitive.unwrap(lhs);

            // General case - perform the instanceof based on assignable
            return Types.isJavaBaseAssignable( rhs, lhs.getClass() )
                    ? Primitive.TRUE : Primitive.FALSE;
        }

        /*
            Look ahead and short circuit evaluation of the rhs if:
            we're a boolean AND and the lhs is false.
            or we're a boolean OR and the lhs is true.
        */
        if ( kind == BOOL_AND || kind == BOOL_ANDX )
            if ( lhs == Primitive.FALSE )
                return Primitive.FALSE;
        if ( kind == BOOL_OR || kind == BOOL_ORX )
            if ( lhs == Primitive.TRUE )
                return Primitive.TRUE;


        Object rhs = ((SimpleNode)jjtGetChild(1)).eval(callstack, interpreter);

        /*
            Are both the lhs and rhs either wrappers or primitive values?
            do binary op
            preserve identity semantics for Wrapper ==/!= Wrapper
            gets treated as arbitrary objects in comparison
        */
        boolean isLhsWrapper = isWrapper(lhs), isRhsWrapper = isWrapper(rhs);
        if ( ( isLhsWrapper || isPrimitiveValue(lhs) )
            && ( isRhsWrapper || isPrimitiveValue(rhs) ) ) try {
            if ( !((kind == EQ || kind == NE) && isLhsWrapper && isRhsWrapper) )
                return Operators.binaryOperation(lhs, rhs, kind);
        } catch ( UtilEvalError e ) {
            throw e.toEvalError(
                "Failed operation: "+lhs+" "+tokenImage[kind]+" "+rhs,
                this, callstack  );
        }

        if ( interpreter.getStrictJava() && ( kind == PLUS || kind == STAR )
                && !( lhs instanceof String || rhs instanceof String ) )
            throw new EvalError( "Bad operand types for binary operator "
                + tokenImage[kind] + " first type: "  + StringUtil.typeString(lhs)
                + " second type: " + StringUtil.typeString(rhs),
                    this, callstack );
        /*
            Treat lhs and rhs as arbitrary objects and do the operation.
            (including NULL and VOID represented by their Primitive types)
        */
        try {
            return Operators.arbitraryObjectsBinaryOperation(lhs, rhs, kind);
        } catch (UtilEvalError e) {
            throw e.toEvalError(this, callstack);
        }
    }

    /*
        object is a non-null and non-void Primitive type
    */
    private boolean isPrimitiveValue( Object obj ) {
        return ( (obj instanceof Primitive)
            && (obj != Primitive.VOID) && (obj != Primitive.NULL) );
    }

    /*
        object is a java.lang wrapper for boolean, char, or number type
    */
    private boolean isWrapper( Object obj ) {
        return ( obj instanceof Boolean ||
            obj instanceof Character || obj instanceof Number );
    }
}
