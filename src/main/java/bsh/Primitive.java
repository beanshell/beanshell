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

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>Wrapper for primitive types in Bsh.  This is package public because it
 * is used in the implementation of some bsh commands.</p>
 *
 * <p>See the note in LHS.java about wrapping objects.</p>
 *
 * @author Pat Niemeyer
 * @author Daniel Leuck
 */
public final class Primitive implements Serializable {
    /*
    Note: this class is final because we may test == Primitive.class in places.
    If we need to change that search for those tests.
    */
    /** default serial version id */
    private static final long serialVersionUID = 1L;

    static final Map<Class<?>, Class<?>> wrapperMap = new HashMap<>();
    static {
        wrapperMap.put( Boolean.TYPE, Boolean.class );
        wrapperMap.put( Byte.TYPE, Byte.class );
        wrapperMap.put( Short.TYPE, Short.class );
        wrapperMap.put( Character.TYPE, Character.class );
        wrapperMap.put( Integer.TYPE, Integer.class );
        wrapperMap.put( Long.TYPE, Long.class );
        wrapperMap.put( Float.TYPE, Float.class );
        wrapperMap.put( Double.TYPE, Double.class );
        wrapperMap.put( Boolean.class, Boolean.TYPE );
        wrapperMap.put( Byte.class, Byte.TYPE );
        wrapperMap.put( Short.class, Short.TYPE );
        wrapperMap.put( Character.class, Character.TYPE );
        wrapperMap.put( Integer.class, Integer.TYPE );
        wrapperMap.put( Long.class, Long.TYPE );
        wrapperMap.put( Float.class, Float.TYPE );
        wrapperMap.put( Double.class, Double.TYPE );
        wrapperMap.put( BigInteger.class, BigInteger.class );
        wrapperMap.put( BigDecimal.class, BigDecimal.class );
    }

    /** The primitive value stored in its java.lang wrapper class */
    private Object value;

    public static final Primitive TRUE = new Primitive(true);
    public static final Primitive FALSE = new Primitive(false);

    /** Default zero value constants */
    public static final Primitive ZERO_CHAR = new Primitive('\000');
    public static final Primitive ZERO_BYTE = new Primitive((byte) 0);
    public static final Primitive ZERO_SHORT = new Primitive((short) 0);
    public static final Primitive ZERO_INT = new Primitive(0);
    public static final Primitive ZERO_LONG = new Primitive(0L);
    public static final Primitive ZERO_FLOAT = new Primitive(0f);
    public static final Primitive ZERO_DOUBLE = new Primitive(0d);
    public static final Primitive ZERO_BIG_INTEGER = new Primitive(BigInteger.ZERO);
    public static final Primitive ZERO_BIG_DECIMAL = new Primitive(BigDecimal.ZERO);

    private enum Special { NULL_VALUE, VOID_TYPE }

    /*
        NULL means "no value".
        This ia a placeholder for primitive null value.
    */
    public static final Primitive NULL = new Primitive(Special.NULL_VALUE);

    /**
        VOID means "no type".
        Strictly speaking, this makes no sense here.  But for practical
        reasons we'll consider the lack of a type to be a special value.
    */
    public static final Primitive VOID = new Primitive(Special.VOID_TYPE);

    private Object readResolve() throws ObjectStreamException
    {
        if (value == Special.NULL_VALUE)
            return Primitive.NULL;
        return this;
    }

    // private to prevent invocation with param that isn't a primitive-wrapper
    private Primitive( Object value )
    {
        if ( value == null )
            throw new InterpreterError(
                "Use Primitve.NULL instead of Primitive(null)");

        this.value = value;
    }

    public Primitive(boolean value) { this(value ? Boolean.TRUE : Boolean.FALSE); }
    public Primitive(byte value) { this(Byte.valueOf(value)); }
    public Primitive(short value) { this(Short.valueOf(value)); }
    public Primitive(char value) { this(Character.valueOf(value)); }
    public Primitive(int value) { this(Integer.valueOf(value)); }
    public Primitive(long value) { this(Long.valueOf(value)); }
    public Primitive(float value) { this(Float.valueOf(value)); }
    public Primitive(double value) { this(Double.valueOf(value)); }
    public Primitive(BigInteger value) { this((Object) value); }
    public Primitive(BigDecimal value) {
        this((Object) (null != value && value.scale() == 0 ? value.setScale(1) : value));
    }

    /**
        Return the primitive value stored in its java.lang wrapper class
    */
    public Object getValue()
    {
        if ( value == Special.NULL_VALUE )
            return null;
        if ( value == Special.VOID_TYPE )
                throw new InterpreterError("attempt to unwrap void type");
        return value;
    }

    public String toString()
    {
        if(value == Special.NULL_VALUE)
            return "null";
        if(value == Special.VOID_TYPE)
            return "void";
        return value.toString();
    }

    /**
        Get the corresponding Java primitive TYPE class for this Primitive.
        @return the primitive TYPE class type of the value or Void.TYPE for
        Primitive.VOID or null value for type of Primitive.NULL
    */
    public Class<?> getType()
    {
        if ( this == Primitive.VOID )
            return Void.TYPE;

        // NULL return null as type... we currently use null type to indicate
        // loose typing throughout bsh.
        if ( this == Primitive.NULL )
            return null;

        return unboxType( value.getClass() );
    }

    private static final BigInteger INTEGER_MAX = BigInteger.valueOf(Integer.MAX_VALUE);
    private static final BigInteger INTEGER_MIN = BigInteger.valueOf(Integer.MIN_VALUE);
    static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
    public static Primitive shrinkWrap(Object number) {
        if (!(number instanceof Number))
            throw new InterpreterError("Can only shrink wrap Number types");
        Number value = (Number) number;
        if (value instanceof Float)
            return new Primitive(value.floatValue());
        if ( Types.isFloatingpoint(number) ) {
            if ( !Double.isInfinite(value.doubleValue()) )
                return new Primitive(value.doubleValue());
            return new Primitive((BigDecimal) number);
        }
        BigInteger bi = number instanceof BigInteger
                ? (BigInteger) number : BigInteger.valueOf(value.longValue());
        if ( bi.compareTo(INTEGER_MIN) >= 0 && bi.compareTo(INTEGER_MAX) <= 0 )
            return new Primitive(bi.intValue());
        if ( bi.compareTo(LONG_MIN) >= 0 && bi.compareTo(LONG_MAX) <= 0 )
            return new Primitive(bi.longValue());
        return new Primitive(bi);
    }

    /**
        Determine if this primitive is a numeric type.
        i.e. not boolean, null, or void (but including char)
    */
    public boolean isNumber() {
        return !(value instanceof Boolean)
            && this != NULL && this != VOID
            && Types.isNumeric(value);
    }

    public Number numberValue() {
        Object value = this.value;

        // Promote character to Number type for these purposes
        if (value instanceof Character)
            value = Integer.valueOf(((Character)value).charValue());

        if (value instanceof Number)
            return (Number) value;

        throw new InterpreterError("Primitive not a number");
    }

    /**
        Primitives compare equal with other Primitives containing an equal
        wrapped value.
    */
    public boolean equals( Object obj )
    {
        if ( !( obj instanceof Primitive ) )
            if ( wrapperMap.containsKey(obj.getClass()) )
                obj = new Primitive(obj);
            else
                return false;
        Primitive pobj = (Primitive) obj;
        if ( pobj.isNumber() && this.isNumber() ) try {
            if ( this.getType() == BigDecimal.class )
                return this.value.equals(castNumber(BigDecimal.class, pobj.numberValue()));
            if ( pobj.getType() == BigDecimal.class )
                return pobj.value.equals(castNumber(BigDecimal.class, this.numberValue()));
            if ( Types.isFloatingpoint(this.value) || Types.isFloatingpoint(pobj.value) )
                return this.numberValue().doubleValue() == pobj.numberValue().doubleValue();
            if ( this.getType() == BigInteger.class )
                return this.value.equals(castNumber(BigInteger.class, pobj.numberValue()));
            if ( pobj.getType() == BigInteger.class )
                return pobj.value.equals(castNumber(BigInteger.class, this.numberValue()));
            return this.numberValue().longValue() == pobj.numberValue().longValue();
        } catch ( UtilTargetError e ) { // NaN or infinity compared with a BigDecimal
            return false;
        }
        return this.value.equals( pobj.value );
    }

    /**
        The hash of the Primitive is tied to the hash of the wrapped value but
        shifted so that they are not the same.
    */
    public int hashCode()
    {
        return this.value.hashCode() * 21; // arbitrary
    }

    /**
        Unwrap primitive values and map voids to nulls.
        Non Primitive types remain unchanged.

        @param obj object type which may be bsh.Primitive
        @return corresponding "normal" Java type, "unwrapping"
            any bsh.Primitive types to their wrapper types.
    */
    public static Object unwrap( Object obj )
    {
        // map voids to nulls for the outside world
        if (obj == Primitive.VOID)
            return null;

        // unwrap primitives
        if (obj instanceof Primitive)
            return((Primitive)obj).getValue();

        return obj;
    }

    /*
        Unwrap Primitive wrappers to their java.lang wrapper values.
        e.g. Primitive(42) becomes Integer(42)
        @see #unwrap( Object )
    */
    public static Object [] unwrap( Object[] args )
    {
        if ( args == null )
            return null;
        Object [] oa = new Object[ args.length ];
        for(int i=0; i<args.length; i++)
            oa[i] = unwrap( args[i] );
        return oa;
    }

    /*
    */
    public static Object [] wrap( Object[] args, Class<?> [] paramTypes )
    {
        if ( args == null )
            return null;
        Object [] oa = new Object[ args.length ];
        for(int i=0; i<args.length; i++)
            oa[i] = wrap( args[i], paramTypes[i] );
        return oa;
    }

    /**
        Wrap primitive values (as indicated by type param) and nulls in the
        Primitive class.  Values not primitive or null are left unchanged.
        Primitive values are represented by their wrapped values in param value.
        <p>
        The value null is mapped to Primitive.NULL.
        Any value specified with type Void.TYPE is mapped to Primitive.VOID.
    */
    public static Object wrap(
        Object value, Class<?> type )
    {
        if ( type == Void.TYPE )
            return Primitive.VOID;

        if ( value == null )
            return Primitive.NULL;

        if (Types.isPrimitive(type))
            if ( value instanceof Boolean )
                return ((Boolean) value).booleanValue()
                    ? Primitive.TRUE : Primitive.FALSE;

            else if ( isWrapperType( value.getClass() ) )
                return new Primitive( value );

        return value;
    }


    /**
        Get the appropriate default value per JLS 4.5.4
    */
    public static Primitive getDefaultValue( Class<?> type )
    {
        if ( Boolean.TYPE == type )
            return Primitive.FALSE;
        if ( Character.TYPE == type )
            return Primitive.ZERO_CHAR;
        if ( Byte.TYPE == type )
            return Primitive.ZERO_BYTE;
        if ( Short.TYPE == type )
            return Primitive.ZERO_SHORT;
        if ( Integer.TYPE == type )
            return Primitive.ZERO_INT;
        if ( Long.TYPE == type )
            return Primitive.ZERO_LONG;
        if ( Float.TYPE == type )
            return Primitive.ZERO_FLOAT;
        if ( Double.TYPE == type )
            return Primitive.ZERO_DOUBLE;
        return Primitive.NULL;
    }

    /**
        Get the corresponding java.lang wrapper class for the primitive TYPE
        class.
        e.g.  Integer.TYPE -> Integer.class
    */
    public static Class<?> boxType( Class<?> primitiveType )
    {
        Class<?> c = wrapperMap.get( primitiveType );
        if ( c != null && !c.isPrimitive() )
            return c;
        throw new InterpreterError(
            "Not a primitive type: "+ primitiveType );
    }

    /**
        Get the corresponding primitive TYPE class for the java.lang wrapper
        class type.
        e.g.  Integer.class -> Integer.TYPE
    */
    public static Class<?> unboxType( Class<?> wrapperType )
    {
        Class<?> c = wrapperMap.get( wrapperType );
        if ( c != null && (c.isPrimitive() || c == wrapperType) )
            return c;
        throw new InterpreterError(
            "Not a primitive wrapper type: "+wrapperType );
    }

    /**
        Cast this bsh.Primitive value to a new bsh.Primitive value
        This is usually a numeric type cast.  Other cases include:
            A boolean can be cast to boolen
            null can be cast to any object type and remains null
            Attempting to cast a void causes an exception
        @param toType is the java object or primitive TYPE class
    */
    public Primitive castToType( Class<?> toType, int operation )
        throws UtilEvalError
    {
        return castPrimitive(
            toType, getType()/*fromType*/, this/*fromValue*/,
            false/*checkOnly*/, operation );
    }

    /*
        Cast or check a cast of a primitive type to another type.
        Normally both types are primitive (e.g. numeric), but a null value
        (no type) may be cast to any type.
        <p>

        @param toType is the target type of the cast.  It is normally a
        java primitive TYPE, but in the case of a null cast can be any object
        type.

        @param fromType is the java primitive TYPE type of the primitive to be
        cast or null, to indicate that the fromValue was null or void.

        @param fromValue is, optionally, the value to be converted.  If
        checkOnly is true fromValue must be null.  If checkOnly is false,
        fromValue must be non-null (Primitive.NULL is of course valid).
    */
    static Primitive castPrimitive( Class<?> toType, Class<?> fromType,
            Primitive fromValue, boolean checkOnly, int operation )
            throws UtilEvalError {
        // can't cast void to anything
        if ( fromType == Void.TYPE )
            if ( checkOnly )
                return Types.INVALID_CAST;
            else
                throw Types.castError( StringUtil.typeString(toType),
                    "void value", operation );

        // Do numeric cast
        if ( !checkOnly && fromValue.isNumber() && Types.isNumeric(toType) )
            return new Primitive( castNumber(toType, fromValue.numberValue()) );

        if ( toType.isPrimitive() ) {
            // null never becomes a primitive default value
            if ( fromType == null && !Primitive.VOID.equals(fromValue) ) {
                if ( checkOnly )
                    return Types.INVALID_CAST;
                throw Types.castError( "primitive type " + toType.getSimpleName(),
                    "null value", operation );
            }
            // can only cast boolean to boolean
            if ( (toType == Boolean.TYPE) != (fromType == Boolean.TYPE) ) {
                if ( checkOnly )
                    return Types.INVALID_CAST;
                throw Types.castError( toType, fromType, fromValue, operation );
            }
        } else {
            // Trying to cast primitive to an object type
            // Primitive.NULL can be cast to any object type
            if ( fromType == null )
                return checkOnly ? Types.VALID_CAST :
                    Primitive.NULL;

            if ( checkOnly )
                return Types.INVALID_CAST;

            throw Types.castError(
                    "object type " + toType.getName(), "primitive value", operation);
        }

        // Only allow legal Java assignment unless we're a CAST operation
        if ( operation == Types.ASSIGNMENT
            && !Types.isJavaAssignable( toType, fromType ) ) {
            if ( checkOnly )
                return Types.INVALID_CAST;
            throw Types.castError( toType, fromType, fromValue, operation );
        }

        return checkOnly ? Types.VALID_CAST :
            new Primitive( castWrapper(toType, fromValue) );
    }

    public static boolean isWrapperType( Class<?> type )
    {
        return null != type && wrapperMap.containsKey( type ) && !type.isPrimitive();
    }

    /**
        Cast a primitive value represented by its java.lang wrapper type to the
        specified java.lang wrapper type.  e.g.  Byte(5) to Integer(5) or
        Integer(5) to Byte(5)
        @param toType is the java TYPE type
        @param value is the value in java.lang wrapper.
        value may not be null.
        @throws UtilTargetError when the value does not fit, see castNumber
    */
    static Object castWrapper( Class<?> toType, Object value ) throws UtilTargetError {
        if ( Primitive.VOID.equals(value) )
            return value;

        value = Primitive.unwrap(value);

        if ( !(Primitive.isWrapperType(toType) || toType.isPrimitive()) )
            throw new InterpreterError("invalid type in castWrapper: "+toType);

        // first promote char to Number type to avoid duplicating code
        if ( value instanceof Character )
            value = Integer.valueOf(((Character)value).charValue());

        if ( value == null )
            throw new InterpreterError("null value in castWrapper, guard");
        if ( value instanceof Boolean ) {
            if ( toType != Boolean.TYPE )
                throw new InterpreterError("bad wrapper cast of boolean");
            return value;
        }
        if ( toType == Boolean.TYPE )
            throw new InterpreterError("bad wrapper cast to boolean");

        if ( !(value instanceof Number) )
            throw new InterpreterError("bad type in cast "+StringUtil.typeValueString(value));

        return castNumber(toType, (Number) value);
    }

    /**
        Convert a number to another numeric type for an assignment. The value
        must fit the target type: an out of range, fractional, NaN or infinite
        value is rejected. Float and double targets are the exception, they
        keep NaN and infinity and may round, as Java's widening does. A byte
        also accepts 128..255, stored as the byte with the same bits.
        @param toType the numeric target type
        @param number the value to convert
        @return the converted value
        @throws UtilTargetError wrapping ClassCastException when the value does
        not fit the target type
    */
    static Object castNumber(Class<?> toType, Number number) throws UtilTargetError {
        if (toType.isInstance(number) || toType == unboxType(number.getClass()))
            return number;
        if (toType == Float.class || toType == Float.TYPE) {
            float value = number.floatValue();
            if (!Float.isInfinite(value) || isNaNOrInfinite(number))
                return value;
        } else if (toType == Double.class || toType == Double.TYPE) {
            double value = number.doubleValue();
            if (!Double.isInfinite(value) || isNaNOrInfinite(number))
                return value;
        } else if (!isNaNOrInfinite(number)) {
            if (toType == BigDecimal.class)
                return toBigDecimal(number);
            if (toType == BigInteger.class) {
                BigInteger value = toBigInteger(number);
                if (null != value)
                    return value;
            } else {
                Long whole = toLong(number);
                if (null != whole) {
                    long value = whole;
                    if (toType == Long.class || toType == Long.TYPE)
                        return value;
                    if ((toType == Integer.class || toType == Integer.TYPE)
                            && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE)
                        return (int) value;
                    if ((toType == Short.class || toType == Short.TYPE)
                            && value >= Short.MIN_VALUE && value <= Short.MAX_VALUE)
                        return (short) value;
                    if ((toType == Character.class || toType == Character.TYPE)
                            && value >= Character.MIN_VALUE && value <= Character.MAX_VALUE)
                        return (char) value;
                    if ((toType == Byte.class || toType == Byte.TYPE)
                            && value >= Byte.MIN_VALUE && value <= 0xff)
                        return (byte) value;
                }
            }
        }
        throw new UtilTargetError(new ClassCastException(
            "cannot assign number "+number+" to type "+toType.getSimpleName()));
    }

    /** Whether the number is a floating point NaN or infinity. */
    private static boolean isNaNOrInfinite(Number number) {
        return (number instanceof Double || number instanceof Float)
            && (Double.isNaN(number.doubleValue()) || Double.isInfinite(number.doubleValue()));
    }

    /** The value of a finite number as a long, or null when it has a
        fraction or is outside the long range. */
    private static Long toLong(Number number) {
        if (number instanceof BigInteger)
            return ((BigInteger) number).bitLength() < 64 ? number.longValue() : null;
        if (number instanceof BigDecimal) try {
            return ((BigDecimal) number).longValueExact();
        } catch (ArithmeticException e) {
            return null;
        }
        if (number instanceof Double || number instanceof Float) {
            double value = number.doubleValue();
            // -2^63 and 2^63 are exact doubles
            return value == Math.rint(value)
                    && value >= -0x1p63 && value < 0x1p63 ? (long) value : null;
        }
        return number.longValue();
    }

    /** The value of a finite number as a BigInteger, or null when it has a
        fraction. A double converts through its decimal string, as for
        BigDecimal. */
    private static BigInteger toBigInteger(Number number) {
        if (number instanceof BigInteger)
            return (BigInteger) number;
        if (number instanceof BigDecimal) {
            BigDecimal value = (BigDecimal) number;
            return value.signum() == 0 || value.scale() <= 0
                    || value.stripTrailingZeros().scale() <= 0
                ? value.toBigInteger() : null;
        }
        if (number instanceof Double || number instanceof Float) {
            double value = number.doubleValue();
            return value == Math.rint(value)
                ? BigDecimal.valueOf(value).toBigInteger() : null;
        }
        return BigInteger.valueOf(number.longValue());
    }

    /** Convert a finite number to BigDecimal. Whole numbers get a scale of one
        and a double converts through its decimal string. */
    private static BigDecimal toBigDecimal(Number number) {
        if ( number.doubleValue() == 0 )
            return BigDecimal.ZERO.setScale(1);
        if ( number.doubleValue() == 1 )
            return BigDecimal.ONE.setScale(1);
        if ( number instanceof BigInteger )
            return new BigDecimal((BigInteger) number).setScale(1);
        if ( Types.isFloatingpoint(number) )
            return BigDecimal.valueOf(number.doubleValue());
        return new BigDecimal(number.longValue()).setScale(1);
    }

    /** Narrow the numeric result of a compound assignment or an increment
        to the assignee's declared numeric type, as Java's implicit cast does
        (JLS 15.26.2, 15.14.2): x += y is x = (T)(x + y).
        @param result the operation result
        @param type the assignee's declared type, may be null
        @return the narrowed result, or result when there is nothing to narrow
        @throws UtilTargetError see castNumberStrictJava */
    static Object narrowForCompoundAssignment(Object result, Class<?> type)
            throws UtilTargetError {
        if ( null == type || !Types.isNumeric(type)
                || !(type.isPrimitive() || isWrapperType(type)
                    || type == BigInteger.class || type == BigDecimal.class) )
            return result;
        Object value = unwrap(result);
        if ( value instanceof Character )
            value = Integer.valueOf((Character) value);
        if ( !(value instanceof Number) )
            return result;
        Object narrowed = castNumberStrictJava(type, (Number) value);
        return result instanceof Primitive
            ? new Primitive(narrowed) : narrowed;
    }

    /** Convert a number with an explicit cast's narrowing, which truncates
        and wraps as Java does. A BigInteger target drops the fraction.
        @throws UtilTargetError when a BigInteger or BigDecimal target can't
        hold the value, see castNumber */
    static Object castNumberStrictJava(Class<?> toType, Number number)
            throws UtilTargetError {
        if (toType == Byte.class || toType == Byte.TYPE)
            return number.byteValue();
        if (toType == Short.class || toType == Short.TYPE)
            return number.shortValue();
        if (toType == Character.class || toType == Character.TYPE)
            return (char) number.intValue();
        if (toType == Integer.class || toType == Integer.TYPE)
            return number.intValue();
        if (toType == Long.class || toType == Long.TYPE)
            return number.longValue();
        if (toType == Float.class || toType == Float.TYPE)
            return number.floatValue();
        if (toType == Double.class || toType == Double.TYPE)
            return number.doubleValue();
        if (toType == BigInteger.class && !isNaNOrInfinite(number)) {
            if (number instanceof BigDecimal)
                return ((BigDecimal) number).toBigInteger();
            if (number instanceof Double || number instanceof Float)
                return BigDecimal.valueOf(number.doubleValue()).toBigInteger();
        }
        return castNumber(toType, number);
    }

}
