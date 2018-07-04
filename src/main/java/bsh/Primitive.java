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

        if ( value != Special.NULL_VALUE
            && value != Special.VOID_TYPE &&
            !isWrapperType( value.getClass() ) )
            throw new InterpreterError( "Not a wrapper type: "+value.getClass());

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

    public static boolean isFloatingpoint(Object number) {
        return number instanceof Float || number instanceof Double
                || number instanceof BigDecimal;
    }
    private static final BigInteger INTEGER_MAX = BigInteger.valueOf(Integer.MAX_VALUE);
    private static final BigInteger INTEGER_MIN = BigInteger.valueOf(Integer.MIN_VALUE);
    static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
    public static Primitive shrinkWrap(Object number) {
        if (!(number instanceof Number))
            throw new InterpreterError("Can only shrink wrap Number types");
        Number value = (Number) number;
        if (!isFloatingpoint(number)) {
            BigInteger bi = number instanceof BigInteger
                    ? (BigInteger) number : BigInteger.valueOf(value.longValue());
            if (bi.compareTo(INTEGER_MIN) >= 0 && bi.compareTo(INTEGER_MAX) <= 0)
                return new Primitive(bi.intValue());
            if (bi.compareTo(LONG_MIN) >= 0 && bi.compareTo(LONG_MAX) <= 0)
                return new Primitive(bi.longValue());
            return new Primitive(bi);
        }
        if (!Float.isInfinite(value.floatValue()))
            return new Primitive(value.floatValue());
        if (!Double.isInfinite(value.doubleValue()))
            return new Primitive(value.doubleValue());
        return new Primitive((BigDecimal) number);
    }


    public int intValue() throws UtilEvalError
    {
        if(value instanceof Number)
            return((Number)value).intValue();
        throw new UtilEvalError("Primitive not a number");
    }

    public boolean booleanValue() throws UtilEvalError
    {
        if(value instanceof Boolean)
            return((Boolean)value).booleanValue();
        throw new UtilEvalError("Primitive not a boolean");
    }

    /**
        Determine if this primitive is a numeric type.
        i.e. not boolean, null, or void (but including char)
    */
    public boolean isNumber() {
        return ( !(value instanceof Boolean)
            && !(this == NULL) && !(this == VOID) )
            && (value instanceof Number || value instanceof Character);
    }

    public Number numberValue() throws UtilEvalError
    {
        Object value = this.value;

        // Promote character to Number type for these purposes
        if (value instanceof Character)
            value = Integer.valueOf(((Character)value).charValue());

        if (value instanceof Number)
            return (Number)value;

        throw new UtilEvalError("Primitive not a number");
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
        if (pobj.isNumber()) try {
            if(Primitive.isFloatingpoint(pobj.getValue()))
                return this.numberValue().doubleValue() == pobj.numberValue().doubleValue();
            else
                return this.numberValue().longValue() == pobj.numberValue().longValue();
        } catch (UtilEvalError e) { /* ignore */ }
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
        <p/>
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

        if(value instanceof Boolean)
            return ((Boolean)value).booleanValue() ? Primitive.TRUE :
                Primitive.FALSE;

        if ( type.isPrimitive() && isWrapperType( value.getClass() ) )
            return new Primitive( value );

        return value;
    }


    /**
        Get the appropriate default value per JLS 4.5.4
    */
    public static Primitive getDefaultValue( Class<?> type )
    {
        if ( type == Boolean.TYPE )
            return Primitive.FALSE;
        if ( type == null || null == wrapperMap.get( type ) )
            return Primitive.NULL;

        // non boolean primitive, get appropriate flavor of zero
        try {
            return new Primitive(0).castToType( type, Types.CAST );
        } catch ( UtilEvalError e ) {
            throw new InterpreterError( "bad cast" );
        }
    }

    /**
        Get the corresponding java.lang wrapper class for the primitive TYPE
        class.
        e.g.  Integer.TYPE -> Integer.class
    */
    public static Class<?> boxType( Class<?> primitiveType )
    {
        Class<?> c = wrapperMap.get( primitiveType );
        if ( c != null )
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
        if ( c != null )
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
        <p/>

        @param toType is the target type of the cast.  It is normally a
        java primitive TYPE, but in the case of a null cast can be any object
        type.

        @param fromType is the java primitive TYPE type of the primitive to be
        cast or null, to indicate that the fromValue was null or void.

        @param fromValue is, optionally, the value to be converted.  If
        checkOnly is true fromValue must be null.  If checkOnly is false,
        fromValue must be non-null (Primitive.NULL is of course valid).
    */
    static Primitive castPrimitive(
        Class<?> toType, Class<?> fromType, Primitive fromValue,
        boolean checkOnly, int operation )
        throws UtilEvalError
    {
        /*
            Lots of preconditions checked here...
            Once things are running smoothly we might comment these out
            (That's what assertions are for).
        */
        if ( checkOnly && fromValue != null )
            throw new InterpreterError("bad cast param 1");
        if ( !checkOnly && fromValue == null )
            throw new InterpreterError("bad cast param 2");
        if ( fromType != null && !(isWrapperType(fromType)||fromType.isPrimitive()) )
            throw new InterpreterError("bad fromType:" +fromType);
        if ( fromValue == Primitive.NULL && fromType != null )
            throw new InterpreterError("inconsistent args 1");
        if ( fromValue == Primitive.VOID && fromType != Void.TYPE )
            throw new InterpreterError("inconsistent args 2");

        // can't cast void to anything
        if ( fromType == Void.TYPE )
            if ( checkOnly )
                return Types.INVALID_CAST;
            else
                throw Types.castError( Reflect.normalizeClassName(toType),
                    "void value", operation );

        // unwrap Primitive fromValue to its wrapper value, etc.
        Object value = null;
        if ( fromValue != null )
            value = fromValue.getValue();

        // Do numeric cast
        if (null != fromValue && fromValue.isNumber())
            return castNumber(toType, fromValue.numberValue(), checkOnly);

        if ( toType.isPrimitive() )
        {
            // Trying to cast null to primitive type?
            if ( fromType == null )
                if ( checkOnly )
                    return Types.INVALID_CAST;
                else
                    throw Types.castError(
                        "primitive type " + toType.getSimpleName(), "null value", operation );

            // fall through
        } else
        {
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

        // can only cast boolean to boolean
        if ( fromType == Boolean.TYPE )
        {
            if ( toType != Boolean.TYPE )
                if ( checkOnly )
                    return Types.INVALID_CAST;
                else
                    throw Types.castError( toType, fromType, operation );

            return checkOnly ? Types.VALID_CAST : fromValue;
        }

        // Only allow legal Java assignment unless we're a CAST operation
        if ( operation == Types.ASSIGNMENT
            && !Types.isJavaAssignable( toType, fromType )
        ) {
            if ( checkOnly )
                return Types.INVALID_CAST;

            throw Types.castError( toType, fromType, operation );
        }

        return checkOnly ? Types.VALID_CAST :
            new Primitive( castWrapper(toType, value) );
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
    */
    static Object castWrapper(
        Class<?> toType, Object value )
    {
        value = Primitive.unwrap(value);
        if ( !(Primitive.isWrapperType(toType) || toType.isPrimitive()) )
            throw new InterpreterError("invalid type in castWrapper: "+toType);
        if ( value == null )
            throw new InterpreterError("null value in castWrapper, guard");
        if ( value instanceof Boolean )
        {
            if ( toType != Boolean.TYPE )
                throw new InterpreterError("bad wrapper cast of boolean");
            return value;
        }

        // first promote char to Number type to avoid duplicating code
        if ( value instanceof Character )
            value = Integer.valueOf(((Character)value).charValue());

        if ( !(value instanceof Number) )
            throw new InterpreterError("bad type in cast "+StringUtil.typeValueString(value));

        return Primitive.unwrap(castNumber(toType, (Number) value, false));
    }

    static Primitive castNumber(Class<?> toType, Number number, boolean checkOnly) {
        if ((toType == Byte.class || toType == Byte.TYPE)
                && number.shortValue() <= 0xff && number.shortValue() >= Byte.MIN_VALUE)
            return checkOnly ? Types.VALID_CAST : new Primitive( number.byteValue() );
        if ((toType == Short.class || toType == Short.TYPE)
                && number.intValue() <= Short.MAX_VALUE && number.intValue() >= Short.MIN_VALUE)
            return checkOnly ? Types.VALID_CAST : new Primitive( number.shortValue() );
        if ((toType == Character.TYPE || toType == Character.class)
                && number.intValue() <= (int) Character.MAX_VALUE && number.intValue() >= (int) Character.MIN_VALUE)
            return checkOnly ? Types.VALID_CAST : new Primitive( (char) number.intValue() );
        if ((toType == Integer.class || toType == Integer.TYPE)
                && number.longValue() <= Integer.MAX_VALUE && number.longValue() >= Integer.MIN_VALUE)
            if (number instanceof Byte)
                return checkOnly ? Types.VALID_CAST : new Primitive( Byte.toUnsignedInt(number.byteValue()) );
            else
                return checkOnly ? Types.VALID_CAST : new Primitive( number.intValue() );
        if ((toType == Float.class || toType == Float.TYPE)
                && !Float.isInfinite(number.floatValue()))
            return checkOnly ? Types.VALID_CAST : new Primitive( number.floatValue() );
        if ((toType == Double.class || toType == Double.TYPE)
                && !Double.isInfinite(number.doubleValue()))
            return checkOnly ? Types.VALID_CAST : new Primitive( number.doubleValue() );

        String num = number.toString();
        if (number.equals(0) || number.equals(0f) || "0".equals(num)) {
            if ((toType == Long.class || toType == Long.TYPE))
                return checkOnly ? Types.VALID_CAST : new Primitive( 0L );
            if (toType == BigInteger.class)
                return checkOnly ? Types.VALID_CAST : new Primitive( BigInteger.ZERO );
            if (toType == BigDecimal.class)
                return checkOnly ? Types.VALID_CAST : new Primitive( BigDecimal.ZERO.setScale(1) );
        } else if (number.equals(1) || number.equals(1f) || "1".equals(num)) {
            if ((toType == Long.class || toType == Long.TYPE))
                return checkOnly ? Types.VALID_CAST : new Primitive( 1L );
            if (toType == BigInteger.class)
                return checkOnly ? Types.VALID_CAST : new Primitive( BigInteger.ONE );
            if (toType == BigDecimal.class)
                return checkOnly ? Types.VALID_CAST : new Primitive( BigDecimal.ONE.setScale(1) );
        } else {
            BigDecimal bd = !isFloatingpoint(number) ? new BigDecimal(num).setScale(1)
                : number instanceof BigDecimal ? (BigDecimal) number : new BigDecimal(num);
            if (toType == BigDecimal.class)
                return checkOnly ? Types.VALID_CAST : new Primitive( bd );

            BigInteger bi = number instanceof BigInteger ? (BigInteger) number : bd.toBigInteger();
            if ((toType == Long.class || toType == Long.TYPE)
                    && bi.compareTo(LONG_MIN) >= 0 && bi.compareTo(LONG_MAX) <= 0)
                return checkOnly ? Types.VALID_CAST : new Primitive( number.longValue() );
            if (toType == BigInteger.class)
                return checkOnly ? Types.VALID_CAST : new Primitive(bi);
        }
        throw new InterpreterError("cannot assign number "+number+" to type "+toType.getSimpleName());
    }

}
