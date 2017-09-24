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

import java.util.Hashtable;

/**
 * <p>
 * Wrapper for primitive types in Bsh. This is package public because it
 * is used in the implementation of some bsh commands.
 * </p>
 *
 * <p>
 * See the note in LHS.java about wrapping objects.
 * </p>
 *
 * @author Pat Niemeyer
 * @author Daniel Leuck
 */
public final class Primitive implements ParserConstants, java.io.Serializable {
    /*
     * Note: this class is final because we may test == Primitive.class in
     * places.
     * If we need to change that search for those tests.
     */

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;
    /** The wrapper map. *
     * static Hashtable primitiveToWrapper = new Hashtable();
     * static Hashtable wrapperToPrimitive = new Hashtable();
     * static {
     * primitiveToWrapper.put(Boolean.TYPE, Boolean.class);
     * primitiveToWrapper.put(Byte.TYPE, Byte.class);
     * primitiveToWrapper.put(Short.TYPE, Short.class);
     * primitiveToWrapper.put(Character.TYPE, Character.class);
     * primitiveToWrapper.put(Integer.TYPE, Integer.class);
     * primitiveToWrapper.put(Long.TYPE, Long.class);
     * primitiveToWrapper.put(Float.TYPE, Float.class);
     * primitiveToWrapper.put(Double.TYPE, Double.class);
     * wrapperToPrimitive.put(Boolean.class, Boolean.TYPE);
     * wrapperToPrimitive.put(Byte.class, Byte.TYPE);
     * wrapperToPrimitive.put(Short.class, Short.TYPE);
     * wrapperToPrimitive.put(Character.class, Character.TYPE);
     * wrapperToPrimitive.put(Integer.class, Integer.TYPE);
     * wrapperToPrimitive.put(Long.class, Long.TYPE);
     * wrapperToPrimitive.put(Float.class, Float.TYPE);
     * wrapperToPrimitive.put(Double.class, Double.TYPE);
     * }
     */
    static Hashtable wrapperMap = new Hashtable();
    static {
        wrapperMap.put(Boolean.TYPE, Boolean.class);
        wrapperMap.put(Byte.TYPE, Byte.class);
        wrapperMap.put(Short.TYPE, Short.class);
        wrapperMap.put(Character.TYPE, Character.class);
        wrapperMap.put(Integer.TYPE, Integer.class);
        wrapperMap.put(Long.TYPE, Long.class);
        wrapperMap.put(Float.TYPE, Float.class);
        wrapperMap.put(Double.TYPE, Double.class);
        wrapperMap.put(Boolean.class, Boolean.TYPE);
        wrapperMap.put(Byte.class, Byte.TYPE);
        wrapperMap.put(Short.class, Short.TYPE);
        wrapperMap.put(Character.class, Character.TYPE);
        wrapperMap.put(Integer.class, Integer.TYPE);
        wrapperMap.put(Long.class, Long.TYPE);
        wrapperMap.put(Float.class, Float.TYPE);
        wrapperMap.put(Double.class, Double.TYPE);
    }
    /** The primitive value stored in its java.lang wrapper class. */
    private final Object value;

    /**
     * The Class Special.
     */
    private static class Special implements java.io.Serializable {

        /** The Constant serialVersionUID. */
        private static final long serialVersionUID = 1L;

        /**
         * Instantiates a new special.
         */
        private Special() {}

        /** The Constant NULL_VALUE. */
        public static final Special NULL_VALUE = new Special();
        /** The Constant VOID_TYPE. */
        public static final Special VOID_TYPE = new Special();
    }

    /** The Constant NULL. *
     * NULL means "no value".
     * This ia a placeholder for primitive null value.
     */
    public static final Primitive NULL = new Primitive(Special.NULL_VALUE);
    /** The true. */
    public static Primitive TRUE = new Primitive(true);
    /** The false. */
    public static Primitive FALSE = new Primitive(false);
    /**
     * VOID means "no type".
     * Strictly speaking, this makes no sense here. But for practical
     * reasons we'll consider the lack of a type to be a special value.
     */
    public static final Primitive VOID = new Primitive(Special.VOID_TYPE);

    /**
     * Instantiates a new primitive.
     *
     * @param value
     *            the value
     */
    // private to prevent invocation with param that isn't a primitive-wrapper
    public Primitive(final Object value) {
        if (value == null)
            throw new InterpreterError(
                    "Use Primitve.NULL instead of Primitive(null)");
        if (value != Special.NULL_VALUE && value != Special.VOID_TYPE
                && !isWrapperType(value.getClass()))
            throw new InterpreterError(
                    "Not a wrapper type: " + value.getClass());
        this.value = value;
    }

    /**
     * Instantiates a new primitive.
     *
     * @param value
     *            the value
     */
    public Primitive(final boolean value) {
        this(value ? Boolean.TRUE : Boolean.FALSE);
    }

    /**
     * Instantiates a new primitive.
     *
     * @param value
     *            the value
     */
    public Primitive(final byte value) {
        this(new Byte(value));
    }

    /**
     * Instantiates a new primitive.
     *
     * @param value
     *            the value
     */
    public Primitive(final short value) {
        this(new Short(value));
    }

    /**
     * Instantiates a new primitive.
     *
     * @param value
     *            the value
     */
    public Primitive(final char value) {
        this(new Character(value));
    }

    /**
     * Instantiates a new primitive.
     *
     * @param value
     *            the value
     */
    public Primitive(final int value) {
        this(new Integer(value));
    }

    /**
     * Instantiates a new primitive.
     *
     * @param value
     *            the value
     */
    public Primitive(final long value) {
        this(new Long(value));
    }

    /**
     * Instantiates a new primitive.
     *
     * @param value
     *            the value
     */
    public Primitive(final float value) {
        this(new Float(value));
    }

    /**
     * Instantiates a new primitive.
     *
     * @param value
     *            the value
     */
    public Primitive(final double value) {
        this(new Double(value));
    }

    /**
     * Return the primitive value stored in its java.lang wrapper class.
     *
     * @return the value
     */
    public Object getValue() {
        if (this.value == Special.NULL_VALUE)
            return null;
        else if (this.value == Special.VOID_TYPE)
            throw new InterpreterError("attempt to unwrap void type");
        else
            return this.value;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        if (this.value == Special.NULL_VALUE)
            return "null";
        else if (this.value == Special.VOID_TYPE)
            return "void";
        else
            return this.value.toString();
    }

    /**
     * Get the corresponding Java primitive TYPE class for this Primitive.
     *
     * @return the primitive TYPE class type of the value or Void.TYPE for
     *         Primitive.VOID or null value for type of Primitive.NULL
     */
    public Class getType() {
        if (this == Primitive.VOID)
            return Void.TYPE;
        // NULL return null as type... we currently use null type to indicate
        // loose typing throughout bsh.
        if (this == Primitive.NULL)
            return null;
        return unboxType(this.value.getClass());
    }

    /**
     * Perform a binary operation on two Primitives or wrapper types.
     * If both original args were Primitives return a Primitive result
     * else it was mixed (wrapper/primitive) return the wrapper type.
     * The exception is for boolean operations where we will return the
     * primitive type either way.
     *
     * @param obj1
     *            the obj 1
     * @param obj2
     *            the obj 2
     * @param kind
     *            the kind
     * @return the object
     * @throws UtilEvalError
     *             the util eval error
     */
    public static Object binaryOperation(Object obj1, Object obj2,
            final int kind) throws UtilEvalError {
        // special primitive types
        if (obj1 == NULL || obj2 == NULL)
            throw new UtilEvalError(
                    "Null value or 'null' literal in binary operation");
        if (obj1 == VOID || obj2 == VOID)
            throw new UtilEvalError(
                    "Undefined variable, class, or 'void' literal in binary operation");
        // keep track of the original types
        final Class lhsOrgType = obj1.getClass();
        final Class rhsOrgType = obj2.getClass();
        // Unwrap primitives
        if (obj1 instanceof Primitive)
            obj1 = ((Primitive) obj1).getValue();
        if (obj2 instanceof Primitive)
            obj2 = ((Primitive) obj2).getValue();
        final Object[] operands = promotePrimitives(obj1, obj2);
        final Object lhs = operands[0];
        final Object rhs = operands[1];
        if (lhs.getClass() != rhs.getClass())
            throw new UtilEvalError(
                    "Type mismatch in operator.  " + lhs.getClass()
                            + " cannot be used with " + rhs.getClass());
        Object result;
        try {
            result = binaryOperationImpl(lhs, rhs, kind);
        } catch (final ArithmeticException e) {
            throw new UtilTargetError("Arithemetic Exception in binary op", e);
        }
        if (result instanceof Boolean)
            return ((Boolean) result).booleanValue() ? Primitive.TRUE
                    : Primitive.FALSE;
        // If both original args were Primitives return a Primitive result
        // else it was mixed (wrapper/primitive) return the wrapper type
        // Exception is for boolean result, return the primitive
        else if (lhsOrgType == Primitive.class && rhsOrgType == Primitive.class)
            return new Primitive(result);
        else
            return result;
    }

    /**
     * Binary operation impl.
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
    static Object binaryOperationImpl(final Object lhs, final Object rhs,
            final int kind) throws UtilEvalError {
        if (lhs instanceof Boolean)
            return booleanBinaryOperation((Boolean) lhs, (Boolean) rhs, kind);
        else if (lhs instanceof Integer)
            return intBinaryOperation((Integer) lhs, (Integer) rhs, kind);
        else if (lhs instanceof Long)
            return longBinaryOperation((Long) lhs, (Long) rhs, kind);
        else if (lhs instanceof Float)
            return floatBinaryOperation((Float) lhs, (Float) rhs, kind);
        else if (lhs instanceof Double)
            return doubleBinaryOperation((Double) lhs, (Double) rhs, kind);
        else
            throw new UtilEvalError("Invalid types in binary operator");
    }

    /**
     * Boolean binary operation.
     *
     * @param B1
     *            the b1
     * @param B2
     *            the b2
     * @param kind
     *            the kind
     * @return the boolean
     */
    static Boolean booleanBinaryOperation(final Boolean B1, final Boolean B2,
            final int kind) {
        final boolean lhs = B1.booleanValue();
        final boolean rhs = B2.booleanValue();
        switch (kind) {
            case EQ:
                return lhs == rhs ? Boolean.TRUE : Boolean.FALSE;
            case NE:
                return lhs != rhs ? Boolean.TRUE : Boolean.FALSE;
            case BOOL_OR:
            case BOOL_ORX:
                return lhs || rhs ? Boolean.TRUE : Boolean.FALSE;
            case BOOL_AND:
            case BOOL_ANDX:
                return lhs && rhs ? Boolean.TRUE : Boolean.FALSE;
            case BIT_AND:
            case BIT_ANDX:
                return lhs & rhs ? Boolean.TRUE : Boolean.FALSE;
            case BIT_OR:
            case BIT_ORX:
                return lhs | rhs ? Boolean.TRUE : Boolean.FALSE;
            case XOR:
                return lhs ^ rhs ? Boolean.TRUE : Boolean.FALSE;
            default:
                throw new InterpreterError("unimplemented binary operator");
        }
    }

    /**
     * Long binary operation.
     *
     * @param L1
     *            the l1
     * @param L2
     *            the l2
     * @param kind
     *            the kind
     * @return the object
     */
    // returns Object covering both Long and Boolean return types
    static Object longBinaryOperation(final Long L1, final Long L2,
            final int kind) {
        final long lhs = L1.longValue();
        final long rhs = L2.longValue();
        switch (kind) {
            // boolean
            case LT:
            case LTX:
                return lhs < rhs ? Boolean.TRUE : Boolean.FALSE;
            case GT:
            case GTX:
                return lhs > rhs ? Boolean.TRUE : Boolean.FALSE;
            case EQ:
                return lhs == rhs ? Boolean.TRUE : Boolean.FALSE;
            case LE:
            case LEX:
                return lhs <= rhs ? Boolean.TRUE : Boolean.FALSE;
            case GE:
            case GEX:
                return lhs >= rhs ? Boolean.TRUE : Boolean.FALSE;
            case NE:
                return lhs != rhs ? Boolean.TRUE : Boolean.FALSE;
            // arithmetic
            case PLUS:
                return new Long(lhs + rhs);
            case MINUS:
                return new Long(lhs - rhs);
            case STAR:
                return new Long(lhs * rhs);
            case SLASH:
                return new Long(lhs / rhs);
            case MOD:
                return new Long(lhs % rhs);
            // bitwise
            case LSHIFT:
            case LSHIFTX:
                return new Long(lhs << rhs);
            case RSIGNEDSHIFT:
            case RSIGNEDSHIFTX:
                return new Long(lhs >> rhs);
            case RUNSIGNEDSHIFT:
            case RUNSIGNEDSHIFTX:
                return new Long(lhs >>> rhs);
            case BIT_AND:
            case BIT_ANDX:
                return new Long(lhs & rhs);
            case BIT_OR:
            case BIT_ORX:
                return new Long(lhs | rhs);
            case XOR:
                return new Long(lhs ^ rhs);
            default:
                throw new InterpreterError(
                        "Unimplemented binary long operator");
        }
    }

    /**
     * Int binary operation.
     *
     * @param I1
     *            the i1
     * @param I2
     *            the i2
     * @param kind
     *            the kind
     * @return the object
     */
    // returns Object covering both Integer and Boolean return types
    static Object intBinaryOperation(final Integer I1, final Integer I2,
            final int kind) {
        final int lhs = I1.intValue();
        final int rhs = I2.intValue();
        switch (kind) {
            // boolean
            case LT:
            case LTX:
                return lhs < rhs ? Boolean.TRUE : Boolean.FALSE;
            case GT:
            case GTX:
                return lhs > rhs ? Boolean.TRUE : Boolean.FALSE;
            case EQ:
                return lhs == rhs ? Boolean.TRUE : Boolean.FALSE;
            case LE:
            case LEX:
                return lhs <= rhs ? Boolean.TRUE : Boolean.FALSE;
            case GE:
            case GEX:
                return lhs >= rhs ? Boolean.TRUE : Boolean.FALSE;
            case NE:
                return lhs != rhs ? Boolean.TRUE : Boolean.FALSE;
            // arithmetic
            case PLUS:
                return new Integer(lhs + rhs);
            case MINUS:
                return new Integer(lhs - rhs);
            case STAR:
                return new Integer(lhs * rhs);
            case SLASH:
                return new Integer(lhs / rhs);
            case MOD:
                return new Integer(lhs % rhs);
            // bitwise
            case LSHIFT:
            case LSHIFTX:
                return new Integer(lhs << rhs);
            case RSIGNEDSHIFT:
            case RSIGNEDSHIFTX:
                return new Integer(lhs >> rhs);
            case RUNSIGNEDSHIFT:
            case RUNSIGNEDSHIFTX:
                return new Integer(lhs >>> rhs);
            case BIT_AND:
            case BIT_ANDX:
                return new Integer(lhs & rhs);
            case BIT_OR:
            case BIT_ORX:
                return new Integer(lhs | rhs);
            case XOR:
                return new Integer(lhs ^ rhs);
            default:
                throw new InterpreterError(
                        "Unimplemented binary integer operator");
        }
    }

    /**
     * Double binary operation.
     *
     * @param D1
     *            the d1
     * @param D2
     *            the d2
     * @param kind
     *            the kind
     * @return the object
     * @throws UtilEvalError
     *             the util eval error
     */
    // returns Object covering both Double and Boolean return types
    static Object doubleBinaryOperation(final Double D1, final Double D2,
            final int kind) throws UtilEvalError {
        final double lhs = D1.doubleValue();
        final double rhs = D2.doubleValue();
        switch (kind) {
            // boolean
            case LT:
            case LTX:
                return lhs < rhs ? Boolean.TRUE : Boolean.FALSE;
            case GT:
            case GTX:
                return lhs > rhs ? Boolean.TRUE : Boolean.FALSE;
            case EQ:
                return lhs == rhs ? Boolean.TRUE : Boolean.FALSE;
            case LE:
            case LEX:
                return lhs <= rhs ? Boolean.TRUE : Boolean.FALSE;
            case GE:
            case GEX:
                return lhs >= rhs ? Boolean.TRUE : Boolean.FALSE;
            case NE:
                return lhs != rhs ? Boolean.TRUE : Boolean.FALSE;
            // arithmetic
            case PLUS:
                return new Double(lhs + rhs);
            case MINUS:
                return new Double(lhs - rhs);
            case STAR:
                return new Double(lhs * rhs);
            case SLASH:
                return new Double(lhs / rhs);
            case MOD:
                return new Double(lhs % rhs);
            // can't shift floating-point values
            case LSHIFT:
            case LSHIFTX:
            case RSIGNEDSHIFT:
            case RSIGNEDSHIFTX:
            case RUNSIGNEDSHIFT:
            case RUNSIGNEDSHIFTX:
                throw new UtilEvalError("Can't shift doubles");
            default:
                throw new InterpreterError(
                        "Unimplemented binary double operator");
        }
    }

    /**
     * Float binary operation.
     *
     * @param F1
     *            the f1
     * @param F2
     *            the f2
     * @param kind
     *            the kind
     * @return the object
     * @throws UtilEvalError
     *             the util eval error
     */
    // returns Object covering both Long and Boolean return types
    static Object floatBinaryOperation(final Float F1, final Float F2,
            final int kind) throws UtilEvalError {
        final float lhs = F1.floatValue();
        final float rhs = F2.floatValue();
        switch (kind) {
            // boolean
            case LT:
            case LTX:
                return lhs < rhs ? Boolean.TRUE : Boolean.FALSE;
            case GT:
            case GTX:
                return lhs > rhs ? Boolean.TRUE : Boolean.FALSE;
            case EQ:
                return lhs == rhs ? Boolean.TRUE : Boolean.FALSE;
            case LE:
            case LEX:
                return lhs <= rhs ? Boolean.TRUE : Boolean.FALSE;
            case GE:
            case GEX:
                return lhs >= rhs ? Boolean.TRUE : Boolean.FALSE;
            case NE:
                return lhs != rhs ? Boolean.TRUE : Boolean.FALSE;
            // arithmetic
            case PLUS:
                return new Float(lhs + rhs);
            case MINUS:
                return new Float(lhs - rhs);
            case STAR:
                return new Float(lhs * rhs);
            case SLASH:
                return new Float(lhs / rhs);
            case MOD:
                return new Float(lhs % rhs);
            // can't shift floats
            case LSHIFT:
            case LSHIFTX:
            case RSIGNEDSHIFT:
            case RSIGNEDSHIFTX:
            case RUNSIGNEDSHIFT:
            case RUNSIGNEDSHIFTX:
                throw new UtilEvalError("Can't shift floats ");
            default:
                throw new InterpreterError(
                        "Unimplemented binary float operator");
        }
    }

    /**
     * Promote primitive wrapper type to to Integer wrapper type.
     *
     * @param wrapper
     *            the wrapper
     * @return the object
     */
    static Object promoteToInteger(final Object wrapper) {
        if (wrapper instanceof Character)
            return new Integer(((Character) wrapper).charValue());
        else if (wrapper instanceof Byte || wrapper instanceof Short)
            return new Integer(((Number) wrapper).intValue());
        return wrapper;
    }

    /**
     * Promote the pair of primitives to the maximum type of the two.
     * e.g. [int,long]->[long,long]
     *
     * @param lhs
     *            the lhs
     * @param rhs
     *            the rhs
     * @return the object[]
     */
    static Object[] promotePrimitives(Object lhs, Object rhs) {
        lhs = promoteToInteger(lhs);
        rhs = promoteToInteger(rhs);
        if (lhs instanceof Number && rhs instanceof Number) {
            final Number lnum = (Number) lhs;
            final Number rnum = (Number) rhs;
            boolean b;
            if ((b = lnum instanceof Double) || rnum instanceof Double) {
                if (b)
                    rhs = new Double(rnum.doubleValue());
                else
                    lhs = new Double(lnum.doubleValue());
            } else if ((b = lnum instanceof Float) || rnum instanceof Float) {
                if (b)
                    rhs = new Float(rnum.floatValue());
                else
                    lhs = new Float(lnum.floatValue());
            } else if ((b = lnum instanceof Long) || rnum instanceof Long)
                if (b)
                    rhs = new Long(rnum.longValue());
                else
                    lhs = new Long(lnum.longValue());
        }
        return new Object[] {lhs, rhs};
    }

    /**
     * Unary operation.
     *
     * @param val
     *            the val
     * @param kind
     *            the kind
     * @return the primitive
     * @throws UtilEvalError
     *             the util eval error
     */
    public static Primitive unaryOperation(final Primitive val, final int kind)
            throws UtilEvalError {
        if (val == NULL)
            throw new UtilEvalError(
                    "illegal use of null object or 'null' literal");
        if (val == VOID)
            throw new UtilEvalError(
                    "illegal use of undefined object or 'void' literal");
        final Class operandType = val.getType();
        final Object operand = promoteToInteger(val.getValue());
        if (operand instanceof Boolean)
            return booleanUnaryOperation((Boolean) operand, kind)
                    ? Primitive.TRUE
                    : Primitive.FALSE;
        else if (operand instanceof Integer) {
            final int result = intUnaryOperation((Integer) operand, kind);
            // ++ and -- must be cast back the original type
            if (kind == INCR || kind == DECR) {
                if (operandType == Byte.TYPE)
                    return new Primitive((byte) result);
                if (operandType == Short.TYPE)
                    return new Primitive((short) result);
                if (operandType == Character.TYPE)
                    return new Primitive((char) result);
            }
            return new Primitive(result);
        } else if (operand instanceof Long)
            return new Primitive(longUnaryOperation((Long) operand, kind));
        else if (operand instanceof Float)
            return new Primitive(floatUnaryOperation((Float) operand, kind));
        else if (operand instanceof Double)
            return new Primitive(doubleUnaryOperation((Double) operand, kind));
        else
            throw new InterpreterError(
                    "An error occurred.  Please call technical support.");
    }

    /**
     * Boolean unary operation.
     *
     * @param B
     *            the b
     * @param kind
     *            the kind
     * @return true, if successful
     * @throws UtilEvalError
     *             the util eval error
     */
    static boolean booleanUnaryOperation(final Boolean B, final int kind)
            throws UtilEvalError {
        final boolean operand = B.booleanValue();
        switch (kind) {
            case BANG:
                return !operand;
            default:
                throw new UtilEvalError("Operator inappropriate for boolean");
        }
    }

    /**
     * Int unary operation.
     *
     * @param I
     *            the i
     * @param kind
     *            the kind
     * @return the int
     */
    static int intUnaryOperation(final Integer I, final int kind) {
        final int operand = I.intValue();
        switch (kind) {
            case PLUS:
                return operand;
            case MINUS:
                return -operand;
            case TILDE:
                return ~operand;
            case INCR:
                return operand + 1;
            case DECR:
                return operand - 1;
            default:
                throw new InterpreterError("bad integer unaryOperation");
        }
    }

    /**
     * Long unary operation.
     *
     * @param L
     *            the l
     * @param kind
     *            the kind
     * @return the long
     */
    static long longUnaryOperation(final Long L, final int kind) {
        final long operand = L.longValue();
        switch (kind) {
            case PLUS:
                return operand;
            case MINUS:
                return -operand;
            case TILDE:
                return ~operand;
            case INCR:
                return operand + 1;
            case DECR:
                return operand - 1;
            default:
                throw new InterpreterError("bad long unaryOperation");
        }
    }

    /**
     * Float unary operation.
     *
     * @param F
     *            the f
     * @param kind
     *            the kind
     * @return the float
     */
    static float floatUnaryOperation(final Float F, final int kind) {
        final float operand = F.floatValue();
        switch (kind) {
            case PLUS:
                return operand;
            case MINUS:
                return -operand;
            default:
                throw new InterpreterError("bad float unaryOperation");
        }
    }

    /**
     * Double unary operation.
     *
     * @param D
     *            the d
     * @param kind
     *            the kind
     * @return the double
     */
    static double doubleUnaryOperation(final Double D, final int kind) {
        final double operand = D.doubleValue();
        switch (kind) {
            case PLUS:
                return operand;
            case MINUS:
                return -operand;
            default:
                throw new InterpreterError("bad double unaryOperation");
        }
    }

    /**
     * Int value.
     *
     * @return the int
     * @throws UtilEvalError
     *             the util eval error
     */
    public int intValue() throws UtilEvalError {
        if (this.value instanceof Number)
            return ((Number) this.value).intValue();
        else
            throw new UtilEvalError("Primitive not a number");
    }

    /**
     * Boolean value.
     *
     * @return true, if successful
     * @throws UtilEvalError
     *             the util eval error
     */
    public boolean booleanValue() throws UtilEvalError {
        if (this.value instanceof Boolean)
            return ((Boolean) this.value).booleanValue();
        else
            throw new UtilEvalError("Primitive not a boolean");
    }

    /**
     * Determine if this primitive is a numeric type.
     * i.e. not boolean, null, or void (but including char)
     *
     * @return true, if is number
     */
    public boolean isNumber() {
        return !(this.value instanceof Boolean) && !(this == NULL)
                && !(this == VOID);
    }

    /**
     * Number value.
     *
     * @return the number
     * @throws UtilEvalError
     *             the util eval error
     */
    public Number numberValue() throws UtilEvalError {
        Object value = this.value;
        // Promote character to Number type for these purposes
        if (value instanceof Character)
            value = new Integer(((Character) value).charValue());
        if (value instanceof Number)
            return (Number) value;
        else
            throw new UtilEvalError("Primitive not a number");
    }

    /**
     * Primitives compare equal with other Primitives containing an equal
     * wrapped value.
     *
     * @param obj
     *            the obj
     * @return true, if successful
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof Primitive)
            return ((Primitive) obj).value.equals(this.value);
        else
            return false;
    }

    /**
     * The hash of the Primitive is tied to the hash of the wrapped value but
     * shifted so that they are not the same.
     *
     * @return the int
     */
    @Override
    public int hashCode() {
        return this.value.hashCode() * 21; // arbitrary
    }

    /**
     * Unwrap primitive values and map voids to nulls.
     * Non Primitive types remain unchanged.
     *
     * @param obj
     *            object type which may be bsh.Primitive
     * @return corresponding "normal" Java type, "unwrapping"
     *         any bsh.Primitive types to their wrapper types.
     */
    public static Object unwrap(final Object obj) {
        // map voids to nulls for the outside world
        if (obj == Primitive.VOID)
            return null;
        // unwrap primitives
        if (obj instanceof Primitive)
            return ((Primitive) obj).getValue();
        else
            return obj;
    }

    /**
     * Unwrap.
     *
     * @param args
     *            the args
     * @return the object[]
     *
     * Unwrap Primitive wrappers to their java.lang wrapper values.
     * e.g. Primitive(42) becomes Integer(42)
     * @see #unwrap(Object)
     */
    public static Object[] unwrap(final Object[] args) {
        final Object[] oa = new Object[args.length];
        for (int i = 0; i < args.length; i++)
            oa[i] = unwrap(args[i]);
        return oa;
    }

    /**
     * Wrap.
     *
     * @param args
     *            the args
     * @param paramTypes
     *            the param types
     * @return the object[]
     *
    */
    public static Object[] wrap(final Object[] args, final Class[] paramTypes) {
        if (args == null)
            return null;
        final Object[] oa = new Object[args.length];
        for (int i = 0; i < args.length; i++)
            oa[i] = wrap(args[i], paramTypes[i]);
        return oa;
    }

    /**
     * Wrap primitive values (as indicated by type param) and nulls in the
     * Primitive class. Values not primitive or null are left unchanged.
     * Primitive values are represented by their wrapped values in param value.
     * <p/>
     * The value null is mapped to Primitive.NULL.
     * Any value specified with type Void.TYPE is mapped to Primitive.VOID.
     *
     * @param value
     *            the value
     * @param type
     *            the type
     * @return the object
     */
    public static Object wrap(final Object value, final Class type) {
        if (type == Void.TYPE)
            return Primitive.VOID;
        if (value == null)
            return Primitive.NULL;
        if (value instanceof Boolean)
            return ((Boolean) value).booleanValue() ? Primitive.TRUE
                    : Primitive.FALSE;
        if (type.isPrimitive() && isWrapperType(value.getClass()))
            return new Primitive(value);
        return value;
    }

    /**
     * Get the appropriate default value per JLS 4.5.4.
     *
     * @param type
     *            the type
     * @return the default value
     */
    public static Primitive getDefaultValue(final Class type) {
        if (type == null || !type.isPrimitive())
            return Primitive.NULL;
        if (type == Boolean.TYPE)
            return Primitive.FALSE;
        // non boolean primitive, get appropriate flavor of zero
        try {
            return new Primitive(0).castToType(type, Types.CAST);
        } catch (final UtilEvalError e) {
            throw new InterpreterError("bad cast");
        }
    }

    /**
     * Get the corresponding java.lang wrapper class for the primitive TYPE
     * class.
     * e.g. Integer.TYPE -> Integer.class
     *
     * @param primitiveType
     *            the primitive type
     * @return the class
     */
    public static Class boxType(final Class primitiveType) {
        final Class c = (Class) wrapperMap.get(primitiveType);
        if (c != null)
            return c;
        throw new InterpreterError("Not a primitive type: " + primitiveType);
    }

    /**
     * Get the corresponding primitive TYPE class for the java.lang wrapper
     * class type.
     * e.g. Integer.class -> Integer.TYPE
     *
     * @param wrapperType
     *            the wrapper type
     * @return the class
     */
    public static Class unboxType(final Class wrapperType) {
        final Class c = (Class) wrapperMap.get(wrapperType);
        if (c != null)
            return c;
        throw new InterpreterError(
                "Not a primitive wrapper type: " + wrapperType);
    }

    /**
     * Cast this bsh.Primitive value to a new bsh.Primitive value
     * This is usually a numeric type cast. Other cases include:
     * A boolean can be cast to boolen
     * null can be cast to any object type and remains null
     * Attempting to cast a void causes an exception
     *
     * @param toType
     *            is the java object or primitive TYPE class
     * @param operation
     *            the operation
     * @return the primitive
     * @throws UtilEvalError
     *             the util eval error
     */
    public Primitive castToType(final Class toType, final int operation)
            throws UtilEvalError {
        return castPrimitive(toType, this.getType()/* fromType */,
                this/* fromValue */, false/* checkOnly */, operation);
    }

    /**
     * Cast primitive.
     *
     * @param toType
     *            is the target type of the cast. It is normally a
     *            java primitive TYPE, but in the case of a null cast can be any
     *            object
     *            type.
     * @param fromType
     *            is the java primitive TYPE type of the primitive to be
     *            cast or null, to indicate that the fromValue was null or void.
     * @param fromValue
     *            is, optionally, the value to be converted. If
     *            checkOnly is true fromValue must be null. If checkOnly is
     *            false,
     *            fromValue must be non-null (Primitive.NULL is of course
     *            valid).
     * @param checkOnly
     *            the check only
     * @param operation
     *            the operation
     * @return the primitive
     * @throws UtilEvalError
     *             the util eval error
     *
     *             Cast or check a cast of a primitive type to another type.
     *             Normally both types are primitive (e.g. numeric), but a null
     *             value
     *             (no type) may be cast to any type.
     *             <p/>
     */
    static Primitive castPrimitive(final Class toType, final Class fromType,
            final Primitive fromValue, final boolean checkOnly,
            final int operation) throws UtilEvalError {
        /*
         * Lots of preconditions checked here...
         * Once things are running smoothly we might comment these out
         * (That's what assertions are for).
         */
        if (checkOnly && fromValue != null)
            throw new InterpreterError("bad cast param 1");
        if (!checkOnly && fromValue == null)
            throw new InterpreterError("bad cast param 2");
        if (fromType != null && !fromType.isPrimitive())
            throw new InterpreterError("bad fromType:" + fromType);
        if (fromValue == Primitive.NULL && fromType != null)
            throw new InterpreterError("inconsistent args 1");
        if (fromValue == Primitive.VOID && fromType != Void.TYPE)
            throw new InterpreterError("inconsistent args 2");
        // can't cast void to anything
        if (fromType == Void.TYPE)
            if (checkOnly)
                return Types.INVALID_CAST;
            else
                throw Types.castError(Reflect.normalizeClassName(toType),
                        "void value", operation);
        // unwrap Primitive fromValue to its wrapper value, etc.
        Object value = null;
        if (fromValue != null)
            value = fromValue.getValue();
        if (toType.isPrimitive()) {
            // Trying to cast null to primitive type?
            if (fromType == null)
                if (checkOnly)
                    return Types.INVALID_CAST;
                else
                    throw Types.castError("primitive type:" + toType,
                            "Null value", operation);
            // fall through
        } else {
            // Trying to cast primitive to an object type
            // Primitive.NULL can be cast to any object type
            if (fromType == null)
                return checkOnly ? Types.VALID_CAST : Primitive.NULL;
            if (checkOnly)
                return Types.INVALID_CAST;
            else
                throw Types.castError("object type:" + toType,
                        "primitive value", operation);
        }
        // can only cast boolean to boolean
        if (fromType == Boolean.TYPE) {
            if (toType != Boolean.TYPE)
                if (checkOnly)
                    return Types.INVALID_CAST;
                else
                    throw Types.castError(toType, fromType, operation);
            return checkOnly ? Types.VALID_CAST : fromValue;
        }
        // Do numeric cast
        // Only allow legal Java assignment unless we're a CAST operation
        if (operation == Types.ASSIGNMENT
                && !Types.isJavaAssignable(toType, fromType))
            if (checkOnly)
                return Types.INVALID_CAST;
            else
                throw Types.castError(toType, fromType, operation);
        return checkOnly ? Types.VALID_CAST
                : new Primitive(castWrapper(toType, value));
    }

    /**
     * Checks if is wrapper type.
     *
     * @param type
     *            the type
     * @return true, if is wrapper type
     */
    public static boolean isWrapperType(final Class type) {
        return wrapperMap.get(type) != null && !type.isPrimitive();
    }

    /**
     * Cast a primitive value represented by its java.lang wrapper type to the
     * specified java.lang wrapper type. e.g. Byte(5) to Integer(5) or
     * Integer(5) to Byte(5)
     *
     * @param toType
     *            is the java TYPE type
     * @param value
     *            is the value in java.lang wrapper.
     *            value may not be null.
     * @return the object
     */
    static Object castWrapper(final Class toType, Object value) {
        if (!toType.isPrimitive())
            throw new InterpreterError(
                    "invalid type in castWrapper: " + toType);
        if (value == null)
            throw new InterpreterError("null value in castWrapper, guard");
        if (value instanceof Boolean)
            if (toType != Boolean.TYPE)
                throw new InterpreterError("bad wrapper cast of boolean");
            else
                return value;
        // first promote char to Number type to avoid duplicating code
        if (value instanceof Character)
            value = new Integer(((Character) value).charValue());
        if (!(value instanceof Number))
            throw new InterpreterError("bad type in cast");
        final Number number = (Number) value;
        if (toType == Byte.TYPE)
            return new Byte(number.byteValue());
        if (toType == Short.TYPE)
            return new Short(number.shortValue());
        if (toType == Character.TYPE)
            return new Character((char) number.intValue());
        if (toType == Integer.TYPE)
            return new Integer(number.intValue());
        if (toType == Long.TYPE)
            return new Long(number.longValue());
        if (toType == Float.TYPE)
            return new Float(number.floatValue());
        if (toType == Double.TYPE)
            return new Double(number.doubleValue());
        throw new InterpreterError("error in wrapper cast");
    }
}
