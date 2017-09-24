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
 * The Class BSHAssignment.
 */
class BSHAssignment extends SimpleNode implements ParserConstants {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;
    /** The operator. */
    public int operator;

    /**
     * Instantiates a new BSH assignment.
     *
     * @param id
     *            the id
     */
    BSHAssignment(final int id) {
        super(id);
    }

    /** {@inheritDoc} */
    @Override
    public Object eval(final CallStack callstack, final Interpreter interpreter)
            throws EvalError {
        final BSHPrimaryExpression lhsNode = (BSHPrimaryExpression) this
                .jjtGetChild(0);
        if (lhsNode == null)
            throw new InterpreterError("Error, null LHSnode");
        final boolean strictJava = interpreter.getStrictJava();
        final LHS lhs = lhsNode.toLHS(callstack, interpreter);
        if (lhs == null)
            throw new InterpreterError("Error, null LHS");
        // For operator-assign operations save the lhs value before evaluating
        // the rhs. This is correct Java behavior for postfix operations
        // e.g. i=1; i+=i++; // should be 2 not 3
        Object lhsValue = null;
        if (this.operator != ASSIGN) // assign doesn't need the pre-value
            try {
                lhsValue = lhs.getValue();
            } catch (final UtilEvalError e) {
                throw e.toEvalError(this, callstack);
            }
        final SimpleNode rhsNode = (SimpleNode) this.jjtGetChild(1);
        Object rhs;
        // implement "blocks" foo = { };
        // if (rhsNode instanceof BSHBlock)
        // rsh =
        // else
        rhs = rhsNode.eval(callstack, interpreter);
        if (rhs == Primitive.VOID)
            throw new EvalError("Void assignment.", this, callstack);
        try {
            switch (this.operator) {
                case ASSIGN:
                    return lhs.assign(rhs, strictJava);
                case PLUSASSIGN:
                    return lhs.assign(this.operation(lhsValue, rhs, PLUS),
                            strictJava);
                case MINUSASSIGN:
                    return lhs.assign(this.operation(lhsValue, rhs, MINUS),
                            strictJava);
                case STARASSIGN:
                    return lhs.assign(this.operation(lhsValue, rhs, STAR),
                            strictJava);
                case SLASHASSIGN:
                    return lhs.assign(this.operation(lhsValue, rhs, SLASH),
                            strictJava);
                case ANDASSIGN:
                case ANDASSIGNX:
                    return lhs.assign(this.operation(lhsValue, rhs, BIT_AND),
                            strictJava);
                case ORASSIGN:
                case ORASSIGNX:
                    return lhs.assign(this.operation(lhsValue, rhs, BIT_OR),
                            strictJava);
                case XORASSIGN:
                    return lhs.assign(this.operation(lhsValue, rhs, XOR),
                            strictJava);
                case MODASSIGN:
                    return lhs.assign(this.operation(lhsValue, rhs, MOD),
                            strictJava);
                case LSHIFTASSIGN:
                case LSHIFTASSIGNX:
                    return lhs.assign(this.operation(lhsValue, rhs, LSHIFT),
                            strictJava);
                case RSIGNEDSHIFTASSIGN:
                case RSIGNEDSHIFTASSIGNX:
                    return lhs.assign(
                            this.operation(lhsValue, rhs, RSIGNEDSHIFT),
                            strictJava);
                case RUNSIGNEDSHIFTASSIGN:
                case RUNSIGNEDSHIFTASSIGNX:
                    return lhs.assign(
                            this.operation(lhsValue, rhs, RUNSIGNEDSHIFT),
                            strictJava);
                default:
                    throw new InterpreterError(
                            "unimplemented operator in assignment BSH");
            }
        } catch (final UtilEvalError e) {
            throw e.toEvalError(this, callstack);
        }
    }

    /**
     * Operation.
     *
     * @param lhs
     *            the lhs
     * @param rhs
     *            the rhs
     * @param kind
     *            the kind
     * @return the object
     * @throws UtilEvalError
     *             the util eval error
     */
    private Object operation(final Object lhs, final Object rhs, final int kind)
            throws UtilEvalError {
        /*
         * Implement String += value;
         * According to the JLS, value may be anything.
         * In BeanShell, we'll disallow VOID (undefined) values.
         * (or should we map them to the empty string?)
         */
        if (lhs instanceof String && rhs != Primitive.VOID) {
            if (kind != PLUS)
                throw new UtilEvalError(
                        "Use of non + operator with String LHS");
            return (String) lhs + rhs;
        }
        if (lhs instanceof Primitive || rhs instanceof Primitive)
            if (lhs == Primitive.VOID || rhs == Primitive.VOID)
                throw new UtilEvalError(
                        "Illegal use of undefined object or 'void' literal");
            else if (lhs == Primitive.NULL || rhs == Primitive.NULL)
                throw new UtilEvalError(
                        "Illegal use of null object or 'null' literal");
        if ((lhs instanceof Boolean || lhs instanceof Character
                || lhs instanceof Number || lhs instanceof Primitive)
                && (rhs instanceof Boolean || rhs instanceof Character
                        || rhs instanceof Number || rhs instanceof Primitive))
            return Primitive.binaryOperation(lhs, rhs, kind);
        throw new UtilEvalError(
                "Non primitive value in operator: " + lhs.getClass() + " "
                        + tokenImage[kind] + " " + rhs.getClass());
    }
}
