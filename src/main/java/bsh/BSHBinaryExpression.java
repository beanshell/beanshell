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
 * Implement binary expressions...
 * Note: this is too complicated... need some cleanup and simplification.
 *
 * @see Primitive.binaryOperation
 */
class BSHBinaryExpression extends SimpleNode implements ParserConstants {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;
    /** The kind. */
    public int kind;

    /**
     * Instantiates a new BSH binary expression.
     *
     * @param id
     *            the id
     */
    BSHBinaryExpression(final int id) {
        super(id);
    }

    /** {@inheritDoc} */
    @Override
    public Object eval(final CallStack callstack, final Interpreter interpreter)
            throws EvalError {
        final Object lhs = ((SimpleNode) this.jjtGetChild(0)).eval(callstack,
                interpreter);
        /*
         * Doing instanceof? Next node is a type.
         */
        if (this.kind == INSTANCEOF) {
            // null object ref is not instance of any type
            if (lhs == Primitive.NULL)
                return Primitive.FALSE;
            final Class rhs = ((BSHType) this.jjtGetChild(1)).getType(callstack,
                    interpreter);
            /*
             * // primitive (number or void) cannot be tested for instanceof
             * if (lhs instanceof Primitive)
             * throw new EvalError("Cannot be instance of primitive type.");
             *
             * Primitive (number or void) is not normally an instanceof
             * anything. But for internal use we'll test true for the
             * bsh.Primitive class.
             * i.e. (5 instanceof bsh.Primitive) will be true
             */
            if (lhs instanceof Primitive)
                if (rhs == bsh.Primitive.class)
                    return Primitive.TRUE;
                else
                    return Primitive.FALSE;
            // General case - performe the instanceof based on assignability
            final boolean ret = Types.isJavaBaseAssignable(rhs, lhs.getClass());
            return new Primitive(ret);
        }
        // The following two boolean checks were tacked on.
        // This could probably be smoothed out.
        /*
         * Look ahead and short circuit evaluation of the rhs if:
         * we're a boolean AND and the lhs is false.
         */
        if (this.kind == BOOL_AND || this.kind == BOOL_ANDX) {
            Object obj = lhs;
            if (this.isPrimitiveValue(lhs))
                obj = ((Primitive) lhs).getValue();
            if (obj instanceof Boolean
                    && ((Boolean) obj).booleanValue() == false)
                return Primitive.FALSE;
        }
        /*
         * Look ahead and short circuit evaluation of the rhs if:
         * we're a boolean AND and the lhs is false.
         */
        if (this.kind == BOOL_OR || this.kind == BOOL_ORX) {
            Object obj = lhs;
            if (this.isPrimitiveValue(lhs))
                obj = ((Primitive) lhs).getValue();
            if (obj instanceof Boolean
                    && ((Boolean) obj).booleanValue() == true)
                return Primitive.TRUE;
        }
        // end stuff that was tacked on for boolean short-circuiting.
        /*
         * Are both the lhs and rhs either wrappers or primitive values?
         * do binary op
         */
        final boolean isLhsWrapper = this.isWrapper(lhs);
        final Object rhs = ((SimpleNode) this.jjtGetChild(1)).eval(callstack,
                interpreter);
        final boolean isRhsWrapper = this.isWrapper(rhs);
        if ((isLhsWrapper || this.isPrimitiveValue(lhs))
                && (isRhsWrapper || this.isPrimitiveValue(rhs)))
            // Special case for EQ on two wrapper objects
            if (isLhsWrapper && isRhsWrapper && this.kind == EQ) {
                /*
                 * Don't auto-unwrap wrappers (preserve identity semantics)
                 * FALL THROUGH TO OBJECT OPERATIONS BELOW.
                 */
            } else
                try {
                    return Primitive.binaryOperation(lhs, rhs, this.kind);
                } catch (final UtilEvalError e) {
                    throw e.toEvalError(this, callstack);
                }
        /*
         * Treat lhs and rhs as arbitrary objects and do the operation.
         * (including NULL and VOID represented by their Primitive types)
         */
        // System.out.println("binary op arbitrary obj: {"+lhs+"}, {"+rhs+"}");
        switch (this.kind) {
            case EQ:
                return lhs == rhs ? Primitive.TRUE : Primitive.FALSE;
            case NE:
                return lhs != rhs ? Primitive.TRUE : Primitive.FALSE;
            case PLUS:
                if (lhs instanceof String || rhs instanceof String)
                    return lhs.toString() + rhs.toString();
                // FALL THROUGH TO DEFAULT CASE!!!
            default:
                if (lhs instanceof Primitive || rhs instanceof Primitive)
                    if (lhs == Primitive.VOID || rhs == Primitive.VOID)
                        throw new EvalError(
                                "illegal use of undefined variable, class, or 'void' literal",
                                this, callstack);
                    else if (lhs == Primitive.NULL || rhs == Primitive.NULL)
                        throw new EvalError(
                                "illegal use of null value or 'null' literal",
                                this, callstack);
                throw new EvalError(
                        "Operator: '" + tokenImage[this.kind]
                                + "' inappropriate for objects",
                        this, callstack);
        }
    }

    /**
     * Checks if is primitive value.
     *
     * @param obj
     *            the obj
     * @return true, if is primitive value
     *
     * object is a non-null and non-void Primitive type
     */
    private boolean isPrimitiveValue(final Object obj) {
        return obj instanceof Primitive && obj != Primitive.VOID
                && obj != Primitive.NULL;
    }

    /**
     * Checks if is wrapper.
     *
     * @param obj
     *            the obj
     * @return true, if is wrapper
     *
     * object is a java.lang wrapper for boolean, char, or number type
     */
    private boolean isWrapper(final Object obj) {
        return obj instanceof Boolean || obj instanceof Character
                || obj instanceof Number;
    }
}
