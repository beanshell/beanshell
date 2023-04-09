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

import bsh.congo.tree.BaseNode;
import bsh.congo.tree.Operator;
import bsh.congo.parser.Node;
import bsh.congo.parser.Token.TokenType;
import static bsh.congo.parser.Token.TokenType.*;
/**
    Implement binary expressions...
    @see Primitive.binaryOperation
*/
public class BSHBinaryExpression extends BaseNode {
    public TokenType getKind() {
        return getOperator() != null ? getOperator().getType() : null;
    }

    public Operator getOperator() {
        return firstChildOfType(Operator.class);
    }

    public Object eval( CallStack callstack, Interpreter interpreter)
        throws EvalError
    {
        Object lhs = getChild(0).eval(callstack, interpreter);

        /*
            Doing instanceof?  Next node is a type.
        */
        if (getKind() == INSTANCEOF)
        {
            // null object ref is not instance of any type
            if ( lhs == Primitive.NULL )
                return Primitive.FALSE;

            Class<?> rhs = ((BSHType)getChild(1)).getType(
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
        if ( (getKind() == SC_AND) )
            if ( interpreter.getStrictJava() ) {
                if (Primitive.FALSE.equals(lhs)) return Primitive.FALSE;
            } else {
                if (Primitive.FALSE.equals(Primitive.castWrapper(Boolean.TYPE, lhs)))
                    return lhs;
            }
        if ( (getKind() == SC_OR || getKind() == ELVIS) )
            if ( interpreter.getStrictJava() ) {
                if (Primitive.TRUE.equals(lhs)) return Primitive.TRUE;
            } else {
                if (Primitive.TRUE.equals(Primitive.castWrapper(Boolean.TYPE, lhs)))
                    return lhs;
            }
        if ( getKind() == NULLCOALESCE && Primitive.NULL != lhs)
            return lhs;

        Object rhs = getChild(1).eval(callstack, interpreter);

        if ( getKind() == NULLCOALESCE || getKind() == ELVIS )
            return rhs;

        if ( !interpreter.getStrictJava() ) switch(getKind()) {
            case SC_OR: case SC_AND:
                // needs to validate to a boolean is all we return rhs
                if (Primitive.castWrapper(Boolean.TYPE, rhs) instanceof Boolean)
                    return rhs;
        }

        // Handle null values and apply null rules.
        lhs = checkNullValues(lhs, rhs, 0, callstack);
        rhs = checkNullValues(rhs, lhs, 1, callstack);

        /*
            Are both the lhs and rhs either wrappers or primitive values?
            do binary op
            preserve identity semantics for Wrapper ==/!= Wrapper
            gets treated as arbitrary objects in comparison
        */
        if ( !((getKind() == EQ || getKind() == NE) && isWrapper(lhs) && isWrapper(rhs)) ) {
            if ( (isWrapper(lhs) || isPrimitiveValue(lhs))
                && (isWrapper(rhs) || isPrimitiveValue(rhs)) ) try {
                    return Operators.binaryOperation(lhs, rhs, getKind());
            } catch ( UtilEvalError e ) {
                throw e.toEvalError(
                    "Failed operation: "+lhs+" "+getOperator()+" "+rhs,
                    this, callstack  );
            }
        }

        if ( interpreter.getStrictJava() && ( getKind() == PLUS || getKind() == STAR )
                && !( lhs instanceof String || rhs instanceof String ) )
            throw new EvalError( "Bad operand types for binary operator "
                + getOperator() + " first type: "  + StringUtil.typeString(lhs)
                + " second type: " + StringUtil.typeString(rhs),
                    this, callstack );
        /*
            Treat lhs and rhs as arbitrary objects and do the operation.
            (including NULL and VOID represented by their Primitive types)
        */
        try {
            return Operators.arbitraryObjectsBinaryOperation(lhs, rhs, getKind());
        } catch (UtilEvalError e) {
            throw e.toEvalError(this, callstack);
        }
    }

    /** Get Variable from namespace for value at specified index.
     * Used to identify the type of non-dynamic variables with null value.
     * @param index 0 for lhs val1 else 1
     * @param callstack the evaluation call stack
     * @return the variable in call stack name space for the ambiguous node text
     * @throws UtilEvalError thrown by getVariableImpl. */
    private Variable getVariableAtNode(int index, CallStack callstack) throws UtilEvalError {
        Node nameNode = null;
        if ( getChild(index).getChildCount() > 0
                && (nameNode = getChild(index).getChild(0))
                    instanceof BSHAmbiguousName )
            return callstack.top().getVariableImpl(
                    ((BSHAmbiguousName) nameNode).getName(), true);
        return null;
    }

    /** Apply null rules to operator values.
     * @param val1 value to inspect for null
     * @param val2 value to compare to
     * @param index 0 for lhs val1 else 1 for rhs val1
     * @param callstack the evaluation call stack
     * @return the value modified or not
     * @throws EvalError if operation cause an error */
    private Object checkNullValues(Object val1, Object val2, int index,
            CallStack callstack) throws EvalError {
        if ( Primitive.NULL != val1 )
            return val1;
        if ( Primitive.VOID == val2 )
            return val1;
        try {
            Variable var = null;
            boolean val2IsString = val2 instanceof String;
            Class<?> val2Class = null;
            if ( Primitive.NULL == val2 ) {
                if ( null != (var = getVariableAtNode(index ^ 1, callstack)) ) {
                    val2IsString = var.getType() == String.class;
                    val2Class = var.getType();
                }
            } else
                val2Class = Primitive.unwrap(val2).getClass();
            if ( null == (var = getVariableAtNode(index, callstack)) )
                return val1;
            if ( (getKind() == EQ || getKind() == NE)
                    && isComparableTypes(var.getType(), val2Class, callstack) )
                return val1;
            if ( getKind() == PLUS && (val2IsString || var.getType() == String.class) )
                return "null";
            if ( isWrapper(var.getType()) )
                throw new NullPointerException(
                        "null value with binary operator " + getOperator());
            throw new EvalError(
                    "bad operand types for binary operator "
                        + getOperator(), this, callstack);
        } catch (NullPointerException e) {
            throw new TargetError(e, this, callstack);
        } catch (UtilEvalError e) {
            e.toEvalError(this, callstack);
        }
        return val1;
    }

    /** Whether two types are comparable.
     * @param val1Class first type
     * @param val2Class second type
     * @param callstack for error location in source
     * @return if types are comparable
     * @throws EvalError if types are incomparable */
    private boolean isComparableTypes(Class<?> val1Class,
            Class<?> val2Class, CallStack callstack) throws EvalError {
        if ( val2Class == val1Class
                || isSimilarTypes(val1Class, val2Class) )
            return true;
        throw new EvalError("incomparable types: "
            + StringUtil.typeString(val1Class) + " and "
            + StringUtil.typeString(val2Class),
            this, callstack);
    }

    /** Whether there exists a similarity between two types.
     * @param type1 first type to compare
     * @param type2 second type to compare
     * @return types are similar */
    private boolean isSimilarTypes(Class<?> type1, Class<?> type2) {
        return null == type2 || type1.isAssignableFrom(type2)
                    || type2.isAssignableFrom(type1);
    }

    /** Object is a non-null and non-void Primitive type.
     * @param obj the value to inspect
     * @return is a primitive value */
    private boolean isPrimitiveValue( Object obj ) {
        return obj instanceof Primitive
            && obj != Primitive.NULL && obj != Primitive.VOID;
    }

    /** Object is a java.lang wrapper for boolean, char, or number type.
     * @param obj the value to inspect
     * @return is a wrapper type */
    private boolean isWrapper( Object obj ) {
        return obj instanceof Number
            || obj instanceof Boolean || obj instanceof Character;
    }

    /** Values of type class is relative to the operator kind.
     * @param cls the type to inspect
     * @return is a wrapper type relative to operator */
    private boolean isWrapper( Class<?> cls ) {
        if ( null == cls )
            return false;
        if ( Number.class.isAssignableFrom(cls)
            || Character.class.isAssignableFrom(cls)) switch ( getKind() ) {
            case SC_AND: case SC_OR:
                return false;
            default:
                return true;
        }
        if ( Boolean.class.isAssignableFrom(cls) ) switch ( getKind() ) {
            case EQ: case NE: case SC_OR: case SC_AND:
            case BIT_AND: case BIT_OR: case XOR:
                return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return super.toString() + ": " + getOperator();
    }
}
