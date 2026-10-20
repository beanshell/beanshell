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

class BSHAssignment extends SimpleNode implements ParserConstants {
    private static final long serialVersionUID = 1L;
    public Integer operator;

    BSHAssignment(int id) { super(id); }

    public Object eval(CallStack callstack, Interpreter interpreter)
            throws EvalError {
        return eval(callstack, interpreter, null);
    }

    /** Evaluate, optionally reporting the assignment expression's type (the
     * assignee's declared type, per JLS 15.26) when the result is null. */
    Object eval(CallStack callstack, Interpreter interpreter, CallArguments.Result result)
            throws EvalError {
        if ( null == operator ) try {
            return jjtGetChild(0).eval(callstack, interpreter);
        } catch (SafeNavigate aborted) {
            return Primitive.NULL;
        }

        BSHPrimaryExpression lhsNode =
            (BSHPrimaryExpression) jjtGetChild(0);

        boolean strictJava = interpreter.getStrictJava();
        LHS lhs = lhsNode.toLHS( callstack, interpreter);
        if (result != null) result.type = lhs.getType();

        // For operator-assign operations save the lhs value before evaluating
        // the rhs.  This is correct Java behavior for postfix operations
        // e.g. i=1; i+=i++; // should be 2 not 3
        Object lhsValue = null;
        if ( operator != ASSIGN ) try { // assign doesn't need the lhs value
            lhsValue = lhs.getValue();
        } catch ( UtilEvalError e ) {
            throw e.toEvalError( this, callstack );
        }

        if ( operator == NULLCOALESCEASSIGN &&  Primitive.NULL != lhsValue )
            return lhsValue; // return non null lhs before evaluating rhs

        // evaluate the right hand side
        Object rhs = jjtGetChild(1).eval(callstack, interpreter);

        if ( rhs == Primitive.VOID ) {
            // #775: a void method result may be `=`-assigned to an untyped
            // or Object variable. A never-assigned variable's type reads as
            // Void.TYPE here, not null, so that counts as untyped too.
            Class<?> lhsType = lhs.getType();
            boolean allowed = operator == ASSIGN
                && ( lhsType == null || lhsType == Object.class || lhsType == Void.TYPE )
                && isDefiniteVoidResult(jjtGetChild(1));
            if ( !allowed )
                throw new EvalException("illegal void assignment", this, callstack);
        }

        try {
            switch( operator ) {
                case ASSIGN:
                    if (lhs.isFinal()) {
                        lhs.getVariable().setValue(rhs, Variable.ASSIGNMENT);
                        return rhs;
                    }
                    return lhs.assign(rhs, strictJava);

                case NULLCOALESCEASSIGN:
                    // we already know lhs is null
                    return lhs.assign(rhs, strictJava);

                case PLUSASSIGN:
                    if ( Primitive.NULL == lhsValue && lhs.getType() == String.class )
                        lhsValue = "null";
                    return lhs.assign(
                        compound(lhs, lhsValue, rhs, PLUS), strictJava);

                case MINUSASSIGN:
                    return lhs.assign(
                        compound(lhs, lhsValue, rhs, MINUS), strictJava);

                case STARASSIGN:
                    return lhs.assign(
                        compound(lhs, lhsValue, rhs, STAR), strictJava);

                case SLASHASSIGN:
                    return lhs.assign(
                        compound(lhs, lhsValue, rhs, SLASH), strictJava);

                case ANDASSIGN:
                case ANDASSIGNX:
                    return lhs.assign(
                        compound(lhs, lhsValue, rhs, BIT_AND), strictJava);

                case ORASSIGN:
                case ORASSIGNX:
                    return lhs.assign(
                        compound(lhs, lhsValue, rhs, BIT_OR), strictJava);

                case XORASSIGN:
                case XORASSIGNX:
                    return lhs.assign(
                        compound(lhs, lhsValue, rhs, XOR), strictJava);

                case MODASSIGN:
                case MODASSIGNX:
                    return lhs.assign(
                        compound(lhs, lhsValue, rhs, MOD), strictJava );

                case POWERASSIGN:
                case POWERASSIGNX:
                    return lhs.assign(
                            compound(lhs, lhsValue, rhs, POWER), strictJava);

                case LSHIFTASSIGN:
                case LSHIFTASSIGNX:
                    return lhs.assign(
                        compound(lhs, lhsValue, rhs, LSHIFT), strictJava);

                case RSIGNEDSHIFTASSIGN:
                case RSIGNEDSHIFTASSIGNX:
                    return lhs.assign(
                    compound(lhs, lhsValue, rhs, RSIGNEDSHIFT), strictJava);

                case RUNSIGNEDSHIFTASSIGN:
                case RUNSIGNEDSHIFTASSIGNX:
                    return lhs.assign(
                        compound(lhs, lhsValue, rhs, RUNSIGNEDSHIFT),
                        strictJava);

                default:
                    throw new InterpreterError(
                        "unimplemented operator in assignment BSH");
            }
        } catch ( UtilEvalError e ) {
            throw e.toEvalError( this, callstack );
        }
    }

    /** Apply a compound assignment's operation and narrow the result to the
     * assignee's declared type, as Java's implicit cast does (JLS 15.26.2).
     * @param lhs the assignee
     * @param lhsValue the assignee's value
     * @param rhs the assigner
     * @param kind the operation kind
     * @return the operated result
     * @throws UtilEvalError of invalidation */
    private Object compound( LHS lhs, Object lhsValue, Object rhs, int kind )
            throws UtilEvalError {
        Class<?> type = lhs.getDeclaredType();
        Object result = intWidthShift(lhsValue, rhs, kind, type);
        if ( null == result )
            result = operation(lhsValue, rhs, kind);
        return Primitive.narrowLikeCast(result, type);
    }

    /** Shift at int width for an int or narrower assignee, as Java does:
     * bsh's own shift operators work on long, which would give a different
     * value once the result is narrowed.
     * @return the shifted value, or null when this doesn't apply */
    private static Object intWidthShift( Object lhsValue, Object rhs, int kind,
            Class<?> type ) {
        if ( kind != LSHIFT && kind != RSIGNEDSHIFT && kind != RUNSIGNEDSHIFT )
            return null;
        if ( null == type )
            return null;
        if ( type != Integer.TYPE && type != Integer.class
                && type != Short.TYPE && type != Short.class
                && type != Byte.TYPE && type != Byte.class
                && type != Character.TYPE && type != Character.class )
            return null;
        Object l = Primitive.unwrap(lhsValue), r = Primitive.unwrap(rhs);
        if ( l instanceof Character )
            l = Integer.valueOf((Character) l);
        if ( r instanceof Character )
            r = Integer.valueOf((Character) r);
        if ( !isIntegral(l) || !isIntegral(r) )
            return null;
        int value = ((Number) l).intValue();
        int count = (int) (((Number) r).longValue() & 31);
        int shifted = kind == LSHIFT ? value << count
            : kind == RSIGNEDSHIFT ? value >> count : value >>> count;
        return lhsValue instanceof Primitive
            ? new Primitive(shifted) : (Object) Integer.valueOf(shifted);
    }

    private static boolean isIntegral( Object value ) {
        return value instanceof Byte || value instanceof Short
            || value instanceof Integer || value instanceof Long;
    }

    /** Convenience method to shuffle the different types off to
     * the correct helper classes for application.
     * @param lhs the assignee
     * @param rhs the assigner
     * @param kind the operation kind
     * @return the operated result
     * @throws UtilEvalError of invalidation */
    private Object operation( Object lhs, Object rhs, int kind )
            throws UtilEvalError {
        if ( lhs instanceof String || lhs.getClass().isArray() )
            return Operators.arbitraryObjectsBinaryOperation(lhs, rhs, kind);

        if ( rhs == Primitive.NULL )
            throw new UtilEvalError(
                "Illegal use of null object or 'null' literal" );

        if ( (lhs instanceof Boolean || lhs instanceof Character
               || lhs instanceof Number || lhs instanceof Primitive)
               && (rhs instanceof Boolean || rhs instanceof Character
               || rhs instanceof Number || rhs instanceof Primitive) ) {
            return Operators.binaryOperation(lhs, rhs, kind);
        }

        throw new UtilEvalError("Non primitive value in operator: " +
            lhs.getClass() + " " + tokenImage(kind) + " " + rhs.getClass());
    }

    /** Whether an unevaluated node that produced Primitive.VOID is a genuine
     * unqualified method call, e.g. `f()`, rather than an undefined name or
     * property reference (`booga`, `obj.noSuchProperty`), which produce the
     * same sentinel but aren't real void results (#775). */
    static boolean isDefiniteVoidResult(Node node) {
        while ( node instanceof BSHAssignment && ((BSHAssignment) node).operator == null )
            node = node.jjtGetChild(0);
        if ( node instanceof BSHPrimaryExpression && node.jjtGetNumChildren() == 1 )
            node = node.jjtGetChild(0);
        return node instanceof BSHMethodInvocation;
    }

    @Override
    public String toString() {
        return super.toString() + (null == operator ? "" : ": " + tokenImage(operator));
    }
}
