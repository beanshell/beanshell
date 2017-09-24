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
 * The Class BSHUnaryExpression.
 */
class BSHUnaryExpression extends SimpleNode implements ParserConstants {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;
    /** The kind. */
    public int kind;
    /** The postfix. */
    public boolean postfix = false;

    /**
     * Instantiates a new BSH unary expression.
     *
     * @param id
     *            the id
     */
    BSHUnaryExpression(final int id) {
        super(id);
    }

    /** {@inheritDoc} */
    @Override
    public Object eval(final CallStack callstack, final Interpreter interpreter)
            throws EvalError {
        final SimpleNode node = (SimpleNode) this.jjtGetChild(0);
        // If this is a unary increment of decrement (either pre or postfix)
        // then we need an LHS to which to assign the result. Otherwise
        // just do the unary operation for the value.
        try {
            if (this.kind == INCR || this.kind == DECR) {
                final LHS lhs = ((BSHPrimaryExpression) node).toLHS(callstack,
                        interpreter);
                return this.lhsUnaryOperation(lhs, interpreter.getStrictJava());
            } else
                return this.unaryOperation(node.eval(callstack, interpreter),
                        this.kind);
        } catch (final UtilEvalError e) {
            throw e.toEvalError(this, callstack);
        }
    }

    /**
     * Lhs unary operation.
     *
     * @param lhs
     *            the lhs
     * @param strictJava
     *            the strict java
     * @return the object
     * @throws UtilEvalError
     *             the util eval error
     */
    private Object lhsUnaryOperation(final LHS lhs, final boolean strictJava)
            throws UtilEvalError {
        if (Interpreter.DEBUG)
            Interpreter.debug("lhsUnaryOperation");
        Object prevalue, postvalue;
        prevalue = lhs.getValue();
        postvalue = this.unaryOperation(prevalue, this.kind);
        Object retVal;
        if (this.postfix)
            retVal = prevalue;
        else
            retVal = postvalue;
        lhs.assign(postvalue, strictJava);
        return retVal;
    }

    /**
     * Unary operation.
     *
     * @param op
     *            the op
     * @param kind
     *            the kind
     * @return the object
     * @throws UtilEvalError
     *             the util eval error
     */
    private Object unaryOperation(final Object op, final int kind)
            throws UtilEvalError {
        if (op instanceof Boolean || op instanceof Character
                || op instanceof Number)
            return this.primitiveWrapperUnaryOperation(op, kind);
        if (!(op instanceof Primitive))
            throw new UtilEvalError("Unary operation " + tokenImage[kind]
                    + " inappropriate for object");
        return Primitive.unaryOperation((Primitive) op, kind);
    }

    /**
     * Primitive wrapper unary operation.
     *
     * @param val
     *            the val
     * @param kind
     *            the kind
     * @return the object
     * @throws UtilEvalError
     *             the util eval error
     */
    private Object primitiveWrapperUnaryOperation(final Object val,
            final int kind) throws UtilEvalError {
        final Class operandType = val.getClass();
        final Object operand = Primitive.promoteToInteger(val);
        if (operand instanceof Boolean)
            return Primitive.booleanUnaryOperation((Boolean) operand, kind)
                    ? Boolean.TRUE
                    : Boolean.FALSE;
        else if (operand instanceof Integer) {
            final int result = Primitive.intUnaryOperation((Integer) operand,
                    kind);
            // ++ and -- must be cast back the original type
            if (kind == INCR || kind == DECR) {
                if (operandType == Byte.TYPE)
                    return new Byte((byte) result);
                if (operandType == Short.TYPE)
                    return new Short((short) result);
                if (operandType == Character.TYPE)
                    return new Character((char) result);
            }
            return new Integer(result);
        } else if (operand instanceof Long)
            return new Long(Primitive.longUnaryOperation((Long) operand, kind));
        else if (operand instanceof Float)
            return new Float(
                    Primitive.floatUnaryOperation((Float) operand, kind));
        else if (operand instanceof Double)
            return new Double(
                    Primitive.doubleUnaryOperation((Double) operand, kind));
        else
            throw new InterpreterError(
                    "An error occurred.  Please call technical support.");
    }
}
