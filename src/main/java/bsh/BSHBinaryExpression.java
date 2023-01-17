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

import bsh.congo.parser.BeanshellConstants.TokenType;

/**
    Implement binary expressions...
    @see Primitive.binaryOperation
*/
class BSHBinaryExpression extends SimpleNode implements ParserConstants {
    public int kind;

    BSHBinaryExpression(int id) { super(id); }

    BSHBinaryExpression(bsh.congo.tree.AdditiveExpression addExp) {
        super(addExp);
        kind = addExp.firstChildOfType(TokenType.PLUS) != null ? PLUS : MINUS;
    }

    BSHBinaryExpression(bsh.congo.tree.MultiplicativeExpression multExp) {
        super(multExp);
        kind = multExp.firstChildOfType(TokenType.STAR) != null ? STAR : SLASH;
    }

    BSHBinaryExpression(bsh.congo.tree.PowerExpression powExp) {
        super(powExp);
        kind = POWER;
    }

    BSHBinaryExpression(bsh.congo.tree.ConditionalOrExpression condOrExp) {
        super(condOrExp);
        kind = BOOL_OR;
    }

    BSHBinaryExpression(bsh.congo.tree.ConditionalAndExpression condAndExp) {
        super(condAndExp);
        kind = BOOL_AND;
    }

    BSHBinaryExpression(bsh.congo.tree.InclusiveOrExpression inclusiveOrExpression) {
        super(inclusiveOrExpression);
        kind = BIT_OR; 
    }

    BSHBinaryExpression(bsh.congo.tree.ExclusiveOrExpression exclusiveOrExpression) {
        super(exclusiveOrExpression);
        kind = XOR; 
    }

    BSHBinaryExpression(bsh.congo.tree.AndExpression andExpression) {
        super(andExpression);
        kind = BIT_AND;
    }

    BSHBinaryExpression(bsh.congo.tree.EqualityExpression equalityExpression) {
        super(equalityExpression);
        kind = equalityExpression.firstChildOfType(TokenType.EQ) != null ? EQ : NE;
    }
    
    BSHBinaryExpression(bsh.congo.tree.RelationalExpression relationalExpression) {
        super(relationalExpression);
        switch(relationalExpression.firstChildOfType(bsh.congo.parser.Token.class).getType()) {
            case LT : kind = LT; break;
            case GT : kind = GT; break;
            case GE : kind = GE; break;
            case LE : kind = LE; break;
            default:break;
        }
    }

    BSHBinaryExpression(bsh.congo.tree.ShiftExpression shiftExpression) {
        super(shiftExpression);
        switch(shiftExpression.firstChildOfType(bsh.congo.parser.Token.class).getType()) {
            case LSHIFT : kind = LSHIFT; break;
            case RSIGNEDSHIFT : kind = RSIGNEDSHIFT; break;
            case RUNSIGNEDSHIFT : kind = RUNSIGNEDSHIFT; break;
            case LE : kind = LE; break;
            default:break;
        }
    }

    BSHBinaryExpression(bsh.congo.tree.InstanceOfExpression instanceOfExpression) {
        super(instanceOfExpression);
        kind = INSTANCEOF;
    }

    BSHBinaryExpression(bsh.congo.tree.NullCoalesceElvisSpaceShipExpression nullCoalesceElvisSpaceShipExpression) {
        super(nullCoalesceElvisSpaceShipExpression);
        switch(nullCoalesceElvisSpaceShipExpression.firstChildOfType(bsh.congo.parser.Token.class).getType()) {
            case NULLCOALESCE : kind = NULLCOALESCE; break;
            case ELVIS : kind = ELVIS; break;
            case SPACESHIP : kind = SPACESHIP; break;
            default : break;
        }
    }


    public Object eval( CallStack callstack, Interpreter interpreter)
        throws EvalError
    {
        Object lhs = getChild(0).eval(callstack, interpreter);

        /*
            Doing instanceof?  Next node is a type.
        */
        if (kind == INSTANCEOF)
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
        if ( Primitive.FALSE.equals(lhs) && (kind == BOOL_AND || kind == BOOL_ANDX) )
            return Primitive.FALSE;
        if ( Primitive.TRUE.equals(lhs) && (kind == BOOL_OR || kind == BOOL_ORX || kind == ELVIS) )
            return Primitive.TRUE;
        if ( Primitive.NULL != lhs && kind == NULLCOALESCE )
            return lhs;

        Object rhs = getChild(1).eval(callstack, interpreter);

        if ( kind == NULLCOALESCE || kind == ELVIS )
            return rhs;

        // Handle null values and apply null rules.
        lhs = checkNullValues(lhs, rhs, 0, callstack);
        rhs = checkNullValues(rhs, lhs, 1, callstack);

        /*
            Are both the lhs and rhs either wrappers or primitive values?
            do binary op
            preserve identity semantics for Wrapper ==/!= Wrapper
            gets treated as arbitrary objects in comparison
        */
        if ( !((kind == EQ || kind == NE) && isWrapper(lhs) && isWrapper(rhs)) ) {
            if ( (isWrapper(lhs) || isPrimitiveValue(lhs))
                && (isWrapper(rhs) || isPrimitiveValue(rhs)) ) try {
                    return Operators.binaryOperation(lhs, rhs, kind);
            } catch ( UtilEvalError e ) {
                throw e.toEvalError(
                    "Failed operation: "+lhs+" "+tokenImage[kind]+" "+rhs,
                    this, callstack  );
            }
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

    /** Get Variable from namespace for value at specified index.
     * Used to identify the type of non-dynamic variables with null value.
     * @param index 0 for lhs val1 else 1
     * @param callstack the evaluation call stack
     * @return the variable in call stack name space for the ambiguous node text
     * @throws UtilEvalError thrown by getVariableImpl. */
    private Variable getVariableAtNode(int index, CallStack callstack) throws UtilEvalError {
        bsh.congo.parser.Node nameNode = null;
        if ( getChild(index).getChildCount() > 0
                && (nameNode = getChild(index).getChild(0))
                    instanceof BSHAmbiguousName )
            return callstack.top().getVariableImpl(
                    ((BSHAmbiguousName) nameNode).text, true);
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
            if ( (kind == EQ || kind == NE)
                    && isComparableTypes(var.getType(), val2Class, callstack) )
                return val1;
            if ( kind == PLUS && (val2IsString || var.getType() == String.class) )
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
            || Character.class.isAssignableFrom(cls)) switch ( kind ) {
            case BOOL_AND: case BOOL_ANDX: case BOOL_OR: case BOOL_ORX:
                return false;
            default:
                return true;
        }
        if ( Boolean.class.isAssignableFrom(cls) ) switch ( kind ) {
            case EQ: case NE: case BOOL_OR: case BOOL_ORX: case BOOL_AND:
            case BOOL_ANDX: case BIT_AND: case BIT_ANDX: case BIT_OR:
            case BIT_ORX: case XOR: case XORX:
                return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return super.toString() + ": " + tokenImage[kind];
    }
}
