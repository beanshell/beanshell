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
        if ( (kind == BOOL_AND || kind == BOOL_ANDX)
                && lhs == Primitive.FALSE )
            return Primitive.FALSE;
        if ( (kind == BOOL_OR || kind == BOOL_ORX)
                && lhs == Primitive.TRUE )
            return Primitive.TRUE;


        Object rhs = ((SimpleNode)jjtGetChild(1)).eval(callstack, interpreter);

        // Handle null values and apply null rules.
        lhs = checkNullValues(lhs, rhs, 0, callstack);
        rhs = checkNullValues(rhs, lhs, 1, callstack);

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

    /** Get Variable for value at specified index.
     * @param index 0 for lhs val1 else 1
     * @param callstack the evaluation call stack
     * @return the variable in call stack name space for the ambiguous node text
     * @throws UtilEvalError thrown by getVariableImpl. */
    private Variable getVariableAtNode(int index, CallStack callstack) throws UtilEvalError {
        Node nameNode = null;
        if (jjtGetChild(index).jjtGetNumChildren() > 0
                && (nameNode = jjtGetChild(index).jjtGetChild(0))
                    instanceof BSHAmbiguousName)
            return callstack.top().getVariableImpl(
                    ((BSHAmbiguousName) nameNode).text, true);
        return null;
    }

    /** Apply null rules to operator values.
     * @param val1 value to inspect for null
     * @param val2 value to compare to
     * @param index 0 for lhs val1 else 1
     * @param callstack the evaluation call stack
     * @return the value modified or not
     * @throws EvalError if operation cause an error */
    private Object checkNullValues(Object val1, Object val2, int index,
            CallStack callstack) throws EvalError {
        if ( Primitive.NULL == val1 && Primitive.VOID != val2
                && jjtGetChild(index).jjtGetChild(0)
                    instanceof BSHAmbiguousName) try {
            Variable var = null;
            boolean val2IsString = val2 instanceof String;
            Class<?> val2Class = null;
            if ( Primitive.NULL == val2 )
                if ( null != (var
                        = getVariableAtNode(index == 0 ? 1 : 0, callstack)) ) {
                    val2IsString = var.getType() == String.class;
                    val2Class = var.getType();
                    var = null;
                }
            if ( null == (var = getVariableAtNode(index, callstack)) )
                return val1;
            switch ( kind ) {
                case EQ: case NE:
                if ( (Primitive.NULL == val2 && val2Class == null)
                        || val2Class == var.getType()
                        || (val2Class != null && (
                            var.getType().isAssignableFrom(val2Class)
                        || val2Class.isAssignableFrom(var.getType()))) )
                    return val1;
                else if ( null != val2Class )
                    throw new EvalError("incomparable types: "
                            + StringUtil.typeString(var.getType()) + " and "
                            + StringUtil.typeString(val2Class),
                            this, callstack);
            }
            if ( kind == PLUS
                    && (val2IsString || var.getType() == String.class) )
                return "null";
            if ( isWrapper(var.getType()) )
                throw new NullPointerException(
                        "null value with binary operator " + tokenImage[kind]);
            throw new EvalError(
                    "bad operand types for binary operator "
                        + tokenImage[kind], this, callstack);
        } catch (NullPointerException e) {
            throw new TargetError(e, this, callstack);
        } catch (UtilEvalError e) {
            e.toEvalError(this, callstack);
        }
        return val1;
    }

    /*
        object is a non-null and non-void Primitive type
    */
    private boolean isPrimitiveValue( Object obj ) {
        return obj instanceof Primitive
            && obj != Primitive.VOID && obj != Primitive.NULL;
    }

    /*
        object is a java.lang wrapper for boolean, char, or number type
    */
    private boolean isWrapper( Object obj ) {
        return obj instanceof Boolean
            || obj instanceof Character || obj instanceof Number;
    }

    /** Is the class a wrapper type.
     * Also apply valid operator type to response.
     * @param cls the type to inspect
     * @return is a wrapper type with valid operators */
    private boolean isWrapper( Class<?> cls ) {
        if ( null == cls )
            return false;
        if ( Boolean.class.isAssignableFrom(cls) ) switch ( kind ) {
            case EQ: case NE: case BOOL_OR: case BOOL_ORX: case BOOL_AND:
            case BOOL_ANDX: case BIT_AND: case BIT_ANDX: case BIT_OR:
            case BIT_ORX: case XOR: case XORX:
                return true;
            default:
                return false;
        }
        if (Character.class.isAssignableFrom(cls)
            || Number.class.isAssignableFrom(cls)) switch ( kind ) {
            case BOOL_AND: case BOOL_ANDX: case BOOL_OR: case BOOL_ORX:
                return false;
            default:
                return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return super.toString() + ": " + tokenImage[kind];
    }
}
