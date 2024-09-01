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

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
    Static routines supporing type comparison and conversion in BeanShell.

 The following are notes on type comparison and conversion in BeanShell.


*/
class Types {

    /** a uniquely typed Map.Entry which used solely for the purpose
     * of building map expressions and identifiable as Types.MapEntry. */
    static class MapEntry extends SimpleEntry<Object, Object> {
        private static final long serialVersionUID = 1L;
        public MapEntry(Object key, Object value) {
            super(key, value);
        }
    }

    /*
        Type conversion identifiers.  An ASSIGNMENT allows conversions that would
        normally happen on assignment.  A CAST performs numeric conversions to smaller
        types (as in an explicit Java cast) and things allowed only in variable and array
        declarations (e.g. byte b = 42;)
    */
    static final int CAST=0, ASSIGNMENT=1;
    /** The order of number types. */
    private static final Map<Class<?>, Integer> NUMBER_ORDER
        = Collections.unmodifiableMap(new HashMap<Class<?>, Integer>() {
            private static final long serialVersionUID = 1L;
        {
            put(Byte.TYPE, 0);
            put(Byte.class, 1);
            put(Short.TYPE, 2);
            put(Short.class, 3);
            put(Character.TYPE, 4);
            put(Character.class, 5);
            put(Integer.TYPE, 6);
            put(Integer.class, 7);
            put(Long.TYPE, 8);
            put(Long.class, 9);
            put(Float.TYPE, 10);
            put(Float.class, 11);
            put(Double.TYPE, 12);
            put(Double.class, 13);
            put(BigInteger.class, 14);
            put(BigDecimal.class, 15);
        }
    });

    /** Helper class for type suffixes. */
    public static class Suffix {
        private static final Map<String, Class<?>> m
            = Collections.unmodifiableMap(new HashMap<String, Class<?>>() {
                private static final long serialVersionUID = 1L;
            {
                put("O", Byte.TYPE);
                put("S", Short.TYPE);
                put("I", Integer.TYPE);
                put("L", Long.TYPE);
                put("W", BigInteger.class);
                put("w", BigDecimal.class);
                put("d", Double.TYPE);
                put("f", Float.TYPE);
            }
        });

        private static String toUpperKey(Character key) {
            return key.toString().toUpperCase();
        }

        private static String toLowerKey(Character key) {
            return key.toString().toLowerCase();
        }

        public static boolean isIntegral(Character key) {
            return m.containsKey(toUpperKey(key));
        }
        public static Class<?> getIntegralType(Character key) {
            return m.get(toUpperKey(key));
        }
        public static boolean isFloatingPoint(Character key) {
            return m.containsKey(toLowerKey(key));
        }
        public static Class<?> getFloatingPointType(Character key) {
            return m.get(toLowerKey(key));
        }
    };

    static final int
        JAVA_BASE_ASSIGNABLE = 1,
        JAVA_BOX_TYPES_ASSIGABLE = 2,
        JAVA_VARARGS_ASSIGNABLE = 3,
        BSH_ASSIGNABLE = 4;

    static final int
        FIRST_ROUND_ASSIGNABLE = JAVA_BASE_ASSIGNABLE,
        LAST_ROUND_ASSIGNABLE = BSH_ASSIGNABLE;

    /**
        Special value that indicates by identity that the result of a cast
        operation was a valid cast.  This is used by castObject() and
        castPrimitive() in the checkOnly mode of operation.  This value is a
        Primitive type so that it can be returned by castPrimitive.
    */
    static Primitive VALID_CAST = new Primitive(1);
    static Primitive INVALID_CAST = new Primitive(-1);

    /** Get the Java types of the arguments.
     * @param args object array of argument values.
     * @return class array of argument types. */
    public static Class<?>[] getTypes( Object[] args )
    {
        if ( args == null )
            return Reflect.ZERO_TYPES;

        Class<?>[] types = new Class[ args.length ];

        for( int i=0; i < args.length; i++ )
            types[i] = getType(args[i]);

        return types;
    }

    /** Find the type of an object.
     * @param arg the object to query.
     * @return null if arg null, getType if Primitive or getClass. */
    public static Class<?> getType( Object arg ) {
        return getType(arg, false);
    }

    /** Determine type of primitives via JAVAs dynamic method lookup.
     * JAVA will choose the most appropriate overloaded method based
     * on paramater types and we return the corresponding primitive
     * type.
     * @param arg the primitive variable
     * @return the primitive class of type */
    public static Class<?> getType( boolean arg ) { return Boolean.TYPE; }
    public static Class<?> getType( byte arg ) { return Byte.TYPE; }
    public static Class<?> getType( char arg ) { return Character.TYPE; }
    public static Class<?> getType( int arg ) { return Integer.TYPE; }
    public static Class<?> getType( long arg ) { return Long.TYPE; }
    public static Class<?> getType( short arg ) { return Short.TYPE; }
    public static Class<?> getType( double arg ) { return Double.TYPE; }
    public static Class<?> getType( float arg ) { return Float.TYPE; }

    /** Find the type of an object boxed or not.
     * @param arg the object to query.
     * @param boxed whether to get a primitive or boxed type.
     * @return null if arg null, type of Primitive or getClass. */
    public static Class<?> getType( Object arg, boolean boxed ) {
        if ( null == arg || Primitive.NULL == arg )
            return null;
        if ( arg instanceof Primitive && !boxed )
            return ((Primitive) arg).getType();
        if ( arg instanceof BshLambda )
            return ((BshLambda) arg).dummyType;
        return Primitive.unwrap(arg).getClass();
    }

    static boolean isAssignable(Class<?> from, Class<?> to, int round) {
        if (Types.isFunctionalInterface(to) && BshLambda.isAssignable(from, to, round)) return true;
        switch (round) {
            case JAVA_BASE_ASSIGNABLE: return isJavaBaseAssignable(to, from);
            case JAVA_BOX_TYPES_ASSIGABLE: return isJavaBoxTypesAssignable(to, from);
            case BSH_ASSIGNABLE: return isBshAssignable(to, from);
            case JAVA_VARARGS_ASSIGNABLE: return false; // return isSignatureVarargsAssignable( from, to );
            default: throw new InterpreterError("bad case");
        }
    }

    static boolean isAssignable(Class<?> from, Type to, int round) {
        if (to instanceof Class<?>) // Handle a simple type ( like void or Integer )
            return isAssignable(from, (Class<?>) to, round);

        if (to instanceof ParameterizedType) // Handle parameterized types (like List<T>)
            return isAssignable(from, ((ParameterizedType) to).getRawType(), round);

        if (to instanceof TypeVariable<?>) { // Handle type variables (like T or R)
            TypeVariable<?> typeVar = (TypeVariable<?>) to;
            for (Type bound: typeVar.getBounds())
                if (!isAssignable(from, bound, round))
                    return false;
            return true;
        }

        if (to instanceof WildcardType) { // Handle wildcards (like ? extends Number)
            WildcardType wildcardType = (WildcardType) to;
            for (Type bound: wildcardType.getUpperBounds())
                if (!isAssignable(from, bound, round))
                    return false;
            for (Type bound: wildcardType.getLowerBounds())
                if (!isAssignable(bound, from, round))
                    return false;
            return true;
        }

        return to == null;
    }

    static boolean isAssignable(Type from, Class<?> to, int round) {
        if (from instanceof Class<?>) // Handle a simple type ( like void or Integer )
            return isAssignable(from, (Class<?>) to, round);

        if (from instanceof ParameterizedType) // Handle parameterized types (like List<T>)
            return isAssignable(((ParameterizedType) from).getRawType(), to, round);

        if (from instanceof TypeVariable<?>) { // Handle type variables (like T or R)
            TypeVariable<?> typeVar = (TypeVariable<?>) from;
            for (Type bound: typeVar.getBounds())
                if (!isAssignable(bound, to, round))
                    return false;
            return true;
        }

        if (from instanceof WildcardType) { // Handle wildcards (like ? extends Number)
            WildcardType wildcardType = (WildcardType) from;
            for (Type bound: wildcardType.getUpperBounds())
                if (!isAssignable(bound, to, round))
                    return false;
            for (Type bound: wildcardType.getLowerBounds())
                if (!isAssignable(to, bound, round))
                    return false;
            return true;
        }

        return to == null;
    }

    /**
     Is the 'from' signature (argument types) assignable to the 'to'
     signature (candidate method types)
     This method handles the special case of null values in 'to' types
     indicating a loose type and matching anything.
     */
    /* Should check for strict java here and limit to isJavaAssignable() */
    static boolean isSignatureAssignable( Class<?>[] from, Class<?>[] to, int round )
    {
        if ( round != JAVA_VARARGS_ASSIGNABLE && from.length != to.length )
            return false;

        for (int i = 0; i < from.length; i++)
            if (!isAssignable(from[i], to[i], round))
                return false;
        return true;
    }

    static boolean isSignatureAssignable( Class<?>[] from, Type[] to, int round )
    {
        if ( round != JAVA_VARARGS_ASSIGNABLE && from.length != to.length )
            return false;

        for (int i = 0; i < from.length; i++)
            if (!isAssignable(from[i], to[i], round))
                return false;
        return true;
    }

    /**
     * Are the two signatures exactly equal? This is checked for a special
     * case in overload resolution.
     */
    static boolean areSignaturesEqual(Class<?>[] from, Class<?>[] to)
    {
        if (from.length != to.length)
            return false;

        for (int i = 0; i < from.length; i++)
            if (from[i] != to[i])
                return false;

        return true;
    }


    /**
        Test if a conversion of the rhsType type to the lhsType type is legal via
     standard Java assignment conversion rules (i.e. without a cast).
     The rules include Java 5 autoboxing/unboxing.
        <p/>

        For Java primitive TYPE classes this method takes primitive promotion
        into account.  The ordinary Class.isAssignableFrom() does not take
        primitive promotion conversions into account.  Note that Java allows
        additional assignments without a cast in combination with variable
        declarations and array allocations.  Those are handled elsewhere
        (maybe should be here with a flag?)
        <p/>
        This class accepts a null rhsType type indicating that the rhsType was the
        value Primitive.NULL and allows it to be assigned to any reference lhsType
        type (non primitive).
        <p/>

        Note that the getAssignableForm() method is the primary bsh method for
        checking assignability.  It adds additional bsh conversions, etc.

        @see #isBshAssignable( Class, Class )
        @param lhsType assigning from rhsType to lhsType
        @param rhsType assigning from rhsType to lhsType
    */
    static boolean isJavaAssignable( Class<?> lhsType, Class<?> rhsType ) {
        return isJavaBaseAssignable( lhsType, rhsType )
            || isJavaBoxTypesAssignable( lhsType, rhsType );
    }

    /**
        Is the assignment legal via original Java (up to version 1.4)
        assignment rules, not including auto-boxing/unboxing.
     @param rhsType may be null to indicate primitive null value
    */
    public static boolean isJavaBaseAssignable( Class<?> lhsType, Class<?> rhsType )
    {
        /*
            Assignment to loose type, defer to bsh extensions
            Note: we could shortcut this here:
            if ( lhsType == null ) return true;
            rather than forcing another round.  It's not strictly a Java issue,
            so does it belong here?
        */
        if ( lhsType == null )
            return false;

        // null rhs type corresponds to type of Primitive.NULL assignable to any
        // object type but we give preference here to string types
        if ( rhsType == null )
            return lhsType == String.class;

        if ( lhsType.isPrimitive() && rhsType.isPrimitive() ) {
            if ( lhsType == rhsType )
                return true;

            // handle primitive widening conversions - JLS 5.1.2
            if ( NUMBER_ORDER.containsKey(rhsType)
                    && NUMBER_ORDER.containsKey(lhsType) )
                return (NUMBER_ORDER.get(rhsType) < NUMBER_ORDER.get(lhsType));
        }
        // need to properly incorporate auto narrowing and widening this is just
        // a quick fix to auto wide for magic math methods
        else if ((lhsType == BigInteger.class||lhsType == BigDecimal.class) && Types.isNumeric(rhsType))
            return true;
        else if ( lhsType.isAssignableFrom(rhsType) )
            return true;

        return false;
    }

    /**
        Determine if the type is assignable via Java boxing/unboxing rules.
    */
    static boolean isJavaBoxTypesAssignable(
        Class<?> lhsType, Class<?> rhsType )
    {
        // Assignment to loose type... defer to bsh extensions
        if ( lhsType == null )
            return false;

        // prim can be boxed and assigned to Object
        if ( lhsType == Object.class )
            return true;

        // null rhs type corresponds to type of Primitive.NULL
        // assignable to any object type but not array
        if (rhsType == null)
            return !lhsType.isPrimitive() && !lhsType.isArray();

        // prim numeric type can be boxed and assigned to number
        if ( lhsType == Number.class
            && rhsType != Character.TYPE
            && rhsType != Boolean.TYPE
        )
            return true;

        // General case prim type to wrapper or vice versa.
        // I don't know if this is faster than a flat list of 'if's like above.
        // wrapperMap maps both prim to wrapper and wrapper to prim types,
        // so this test is symmetric
        if ( Primitive.wrapperMap.get( lhsType ) == rhsType )
            return true;

        return isJavaBaseAssignable(lhsType, rhsType);
    }

    /**
     Test if a type can be converted to another type via BeanShell
     extended syntax rules (a superset of Java conversion rules).
     */
    static boolean isBshAssignable( Class<?> toType, Class<?> fromType )
    {
        try {
            return castObject(
                toType, fromType, null/*fromValue*/,
                ASSIGNMENT, true/*checkOnly*/
            ) == VALID_CAST;
        } catch ( UtilEvalError e ) {
            // This should not happen with checkOnly true
            throw new InterpreterError("err in cast check: "+e, e);
        }
    }

    /** Find array element type for class.
     * Roll back component type until class is not an array anymore.
     * @param arrType the class to inspect
     * @return null if type is null or the class when class not array */
    public static Class<?> arrayElementType(Class<?> arrType) {
        if ( null == arrType )
            return null;
        while ( arrType.isArray() )
            arrType = arrType.getComponentType();
        return arrType;
    }

    /** Find the number of array dimensions for class.
     * By counting the number of [ prefixing the class name.
     * @param arrType the class to inspect
     * @return number of [ name prefixes */
    public static int arrayDimensions(Class<?> arrType) {
        if ( null == arrType || !arrType.isArray() )
            return 0;
        return arrType.getName().lastIndexOf('[') + 1;
    }

    /** Find the common type between two classes.
     * @param common most likely common class
     * @param compare class compared with
     * @return the class representing the most common type. */
    public static Class<?> getCommonType(Class<?> common, Class<?> compare) {
        if ( null == common )
            return compare;
        if ( null == compare || common.isAssignableFrom(compare) )
            return common;

        // pick the largest number type based on NUMBER_ORDER definitions
        if ( NUMBER_ORDER.containsKey(common)
                && NUMBER_ORDER.containsKey(compare) )
            if ( NUMBER_ORDER.get(common) >= NUMBER_ORDER.get(compare) )
                return common;
            else
                return compare;

        // find a common super class
        Class<?> supr = common;
        while ( null != (supr = supr.getSuperclass()) && Object.class != supr )
            if ( supr.isAssignableFrom(compare) )
                return supr;

        // common type can only be Object
        return Object.class;
    }

    /**
        Attempt to cast an object instance to a new type if possible via
     BeanShell extended syntax rules.  These rules are always a superset of
     Java conversion rules.  If you wish to impose context sensitive
     conversion rules then you must test before calling this method.
     <p/>

        This method can handle fromValue Primitive types (representing
        primitive casts) as well as fromValue object casts requiring interface
        generation, etc.

        @param toType the class type of the cast result, which may include
        primitive types, e.g. Byte.TYPE

        @param fromValue an Object or bsh.Primitive primitive value (including
            Primitive.NULL or Primitive.VOID )

        @see #isBshAssignable( Class, Class )
    */
    public static Object castObject(
        Object fromValue, Class<?> toType, int operation )
        throws UtilEvalError
    {
        if ( fromValue == null ) {
            if ( operation == Types.CAST )
                if ( !isPrimitive(toType) && !Primitive.isWrapperType(toType) )
                    return Primitive.NULL;
                else
                    return Primitive.getDefaultValue(toType);

            throw new InterpreterError(
                    "Cast error: null fromValue for toType: "
                    + toType.getSimpleName());
        }

        Class<?> fromType = getType(fromValue);

        return castObject(
            toType, fromType, fromValue, operation, false/*checkonly*/ );
    }

    /**
     Perform a type conversion or test if a type conversion is possible with
     respect to BeanShell extended rules.  These rules are always a superset of
     the Java language rules, so this method can also perform (but not test)
     any Java language assignment or cast conversion.
     <p/>

     This method can perform the functionality of testing if an assignment
     or cast is ultimately possible (with respect to BeanShell) as well as the
     functionality of performing the necessary conversion of a value based
     on the specified target type.  This combined functionality is done for
     expediency and could be separated later.
     <p/>

     Other methods such as isJavaAssignable() should be used to determine the
     suitability of an assignment in a fine grained or restrictive way based
     on context before calling this method
     <p/>

     A CAST is stronger than an ASSIGNMENT operation in that it will attempt to
     perform primtive operations that cast to a smaller type. e.g. (byte)myLong;
     These are used in explicit primitive casts, primitive delclarations and
     array declarations. I don't believe there are any object conversions which are
     different between  ASSIGNMENT and CAST (e.g. scripted object to interface proxy
     in bsh is done on assignment as well as cast).
     <p/>

     This method does not obey strictJava(), you must test first before
     using this method if you care. (See #isJavaAssignable()).
     <p/>

        @param toType the class type of the cast result, which may include
            primitive types, e.g. Byte.TYPE.  toType may be null to indicate a
            loose type assignment (which matches any fromType).

        @param fromType is the class type of the value to be cast including
            java primitive TYPE classes for primitives.
            If fromValue is (or would be) Primitive.NULL then fromType should be null.

        @param fromValue an Object or bsh.Primitive primitive value (including
            Primitive.NULL or Primitive.VOID )

        @param checkOnly If checkOnly is true then fromValue must be null.
            FromType is checked for the cast to toType...
            If checkOnly is false then fromValue must be non-null
            (Primitive.NULL is ok) and the actual cast is performed.

        @throws UtilEvalError on invalid assignment (when operation is
            assignment ).

        @throws UtilTargetError wrapping ClassCastException on cast error
            (when operation is cast)

        @param operation is Types.CAST or Types.ASSIGNMENT

        @see bsh.Primitive.getType()
    */
    /*
        Notes: This method is currently responsible for auto-boxing/unboxing
        conversions...  Where does that need to go?
    */
    public static Object castObject( Class<?> toType, Class<?> fromType, Object fromValue,
            int operation, boolean checkOnly ) throws UtilEvalError {
        // assignment to loose type, void type, or exactly same type
        if ( toType == null || arrayElementType(toType) == arrayElementType(fromType) )
            return checkOnly ? VALID_CAST :
                fromValue;

        if ( null != fromType && fromType.isArray() )
            if ( operation == Types.CAST
                    || Collection.class.isAssignableFrom(toType) )
                return checkOnly ? VALID_CAST : BshArray.castArray(
                        toType, fromType, fromValue );

        // Casting to primitive type
        if ( toType.isPrimitive() ) {
            if ( fromType == Void.TYPE || fromType == null || fromType.isPrimitive() ) {
                if (!Primitive.class.isInstance(fromValue))
                    fromValue = Primitive.wrap(fromValue, fromType);
                // Both primitives, do primitive cast
                return Primitive.castPrimitive( toType, fromType, (Primitive) fromValue,
                    checkOnly, operation );
            } else {
                if (((Types.isNumeric(fromType) || String.valueOf(fromValue).matches("[-+0-9.]*"))
                        && Types.isNumeric(toType)) || toType == Boolean.TYPE) {
                    // Auto widening and narrowing of primitive numeric types
                    if (checkOnly)
                        return VALID_CAST;
                    else
                        return Primitive.wrap(
                            Primitive.castWrapper(toType, fromValue), toType);
                } else {
                    // Cannot cast from arbitrary object to primitive
                    if ( checkOnly )
                        return INVALID_CAST;
                    else
                        throw castError(toType, fromType, fromValue, operation);
                }
            }
        }

        // Else, casting to reference type

        // Casting from primitive or void (to reference type)
        if ( fromType == Void.TYPE || fromType == null || fromType.isPrimitive() || toType == Boolean.class
                || (String.valueOf(fromValue).matches("[-+0-9.]*") && Types.isNumeric(toType))) {
            // cast from primitive to wrapper type
            if ( Primitive.isWrapperType( toType ) && fromType != Void.TYPE && fromType != null ) {
                // primitive to wrapper type
                return checkOnly ? VALID_CAST :
                    Primitive.castWrapper(Primitive.unboxType(toType), fromValue);
            }

            // Primitive (not null or void) to Object.class type
            if ( toType == Object.class && fromType != Void.TYPE && fromType != null ) {
                // box it
                return checkOnly ? VALID_CAST : Primitive.unwrap(fromValue);
            }

            // Primitive to arbitrary object type.
            // Allow Primitive.castToType() to handle it as well as cases of
            // Primitive.NULL and Primitive.VOID
            return Primitive.castPrimitive(
                toType, fromType, (Primitive)fromValue, checkOnly, operation );
        }

        // Casting a BshLambda to a normal usable lambda
        if (Types.isFunctionalInterface(toType) && fromValue instanceof BshLambda)
            return checkOnly ? VALID_CAST : ((BshLambda) fromValue).convertTo(toType);

        // If type already assignable no cast necessary
        // We do this last to allow various errors above to be caught.
        // e.g cast Primitive.Void to Object would pass this
        // returns class instance This for generated super types
        if ( toType.isAssignableFrom( fromType ) )
            return checkOnly ? VALID_CAST
                : Reflect.isGeneratedClass(toType) && !Proxy.isProxyClass(fromType)
                ? Reflect.getClassInstanceThis(fromValue, toType.getSimpleName())
                : fromValue;

        // Allow This to pass as typed variable if classStatic is toType
        if (This.class.isInstance(fromValue)
                && ((This)fromValue).getNameSpace().classStatic == toType)
            return checkOnly ? VALID_CAST : fromValue;

        // Can we use the proxy mechanism to cast a bsh.This to
        // the correct interface?
        if ( toType.isInterface() && bsh.This.class.isAssignableFrom( fromType ) )
            return checkOnly ? VALID_CAST :
                ((bsh.This)fromValue).getInterface( toType );

        // Both numeric wrapper types?
        // Try numeric style promotion wrapper cast
        if ( Primitive.isWrapperType( toType )
            && Primitive.isWrapperType( fromType ) )
            return checkOnly ? VALID_CAST :
                Primitive.castWrapper( toType, fromValue );

        if ( checkOnly )
            return INVALID_CAST;
        else
            throw castError(toType, fromType, fromValue, operation);
    }

    /**
        Return a UtilEvalError or UtilTargetError wrapping a ClassCastException
        describing an illegal assignment or illegal cast, respectively.
    */
    static UtilEvalError castError(Class<?> lhsType, Class<?> rhsType, Object value, int operation) {
        return castError(
            StringUtil.typeString(lhsType),
            StringUtil.typeString(rhsType), value, operation);
    }

    static UtilEvalError castError(String lhs, String rhs, int operation) {
        return castError(lhs, rhs, null, operation);
    }

    static UtilEvalError castError(String lhs, String rhs, Object value, int operation) {
        if ( operation == ASSIGNMENT )
            return new UtilEvalError (
                "Cannot assign " + rhs + (null == value ? "" : " with value \""+value+"\"") + " to "+ lhs );

        Exception cce = new ClassCastException(
            "Cannot cast " + rhs + (null == value ? "" : " with value \""+value+"\"") + " to " + lhs );
        return new UtilTargetError( cce );
    }

    /** Return the baseName of an inner class.
     * This should live in utilities somewhere.
     * @param className the class name to modify
     * @return the name before $ of a class */
    public static String getBaseName(String className) {
        int i = className.lastIndexOf("$");
        if (i == -1)
            return className;

        return className.substring(i + 1);
    }

    /** Consider BigInteger and BigDecimal as primitives.
     * @param type the class type to inspect
     * @return true if class isPrimitive, or BigInteger, or BigDecimal */
    public static boolean isPrimitive(Class<?> type) {
        return type.isPrimitive() || type == BigInteger.class || type == BigDecimal.class;
    }

    /** Consider Character as a number type.
     * @param value the value to inspect.
     * @return true if value is a Number or a Character. */
    public static boolean isNumeric(Object value) {
        return value instanceof Number || value instanceof Character;
    }

    /** Overload of isNumeric to evaluate if class is numeric.
     * @param type the class to inspect.
     * @return true if type is a Number or a Character. */
    public static boolean isNumeric(Class<?> type) {
        return Number.class.isAssignableFrom(
            type.isPrimitive() ? Primitive.boxType(type) : type)
            || Character.class.isAssignableFrom(
                type.isPrimitive() ? Primitive.boxType(type) : type);
    }

    /** Consider Float, Double and BigDecimal as floating point types.
     * @param number the number to inspect
     * @return true if number is a Float, or a Double, or a BigDecimal */
    public static boolean isFloatingpoint(Object number) {
        return number instanceof Float || number instanceof Double
                || number instanceof BigDecimal;
    }

    /** Check if object is a Map type property type.
     * @param obj to identify as a property type.
     * @return true if object is a property type.*/
    public static boolean isPropertyTypeMap(Object obj) {
        return obj instanceof Map;
    }

    /** Check if class is a Map type property type.
     * @param clas to identify as a property type.
     * @return true if class is a property type.*/
    public static boolean isPropertyTypeMap(Class<?> clas) {
        return Map.class.isAssignableFrom(clas);
    }

    /** Check if object is an Entry type property type.
     * @param obj to identify as a property type.
     * @return true if object is a property type.*/
    public static boolean isPropertyTypeEntry(Object obj) {
        return obj instanceof Entry;
    }

    /** Check if class is an Entry type property type.
     * @param clas to identify as a property type.
     * @return true if class is a property type.*/
    public static boolean isPropertyTypeEntry(Class<?> clas) {
        return Entry.class.isAssignableFrom(clas);
    }

    /** Check if class is an Entry[] type property type.
     * @param clas to identify as a property type.
     * @return true if class is a property type.*/
    public static boolean isPropertyTypeEntryList(Class<?> clas) {
        return clas.isArray()
                && isPropertyTypeEntry(clas.getComponentType());
    }

    /** Extended property types includes Map, Entry and Entry[].
     * @param clas to identify as a property type.
     * @return true if class is a property type.*/
    public static boolean isPropertyType(Class<?> clas) {
        return isPropertyTypeMap(clas)
                || isPropertyTypeEntry(clas)
                || isPropertyTypeEntryList(clas);
    }

    /** Collection types include Collection, Map or Entry.
     * @param clas to identify as a property type.
     * @return true if class is a collection type.*/
    public static boolean isCollectionType(Class<?> clas) {
        return Collection.class.isAssignableFrom(clas)
            || Map.class.isAssignableFrom(clas)
            || Entry.class.isAssignableFrom(clas);
    }

    /**
     * Just a method to return the pretty name of any Class
     *
     * <pre>
     * prettyName(String.class)
     *  returns "java.lang.String"
     * prettyName(byte.class)
     *  returns "byte"
     * prettyName((new Object[3]).getClass())
     *  returns "java.lang.Object[];"
     * prettyName((new int[3][4][5][6][7][8][9]).getClass())
     *  returns "int[][][][][][][]"
     * </pre>
     */
    public static String prettyName(Class<?> clas) {
        if (clas == null) return "null";
        if (!clas.isArray()) return clas.getName();

        // Return a string like "int[]", "double[]", "double[][]", etc...
        Class<?> arrayType = clas.getComponentType();
        return prettyName(arrayType) + "[]";
    }
    
    public static String[] prettyNames(Type[] types) {
        String[] result = new String[types.length];
        for (int i = 0; i < types.length; i++) result[i] = prettyName(types[i]);
        return result;
    }

    public static String prettyName(Type type) {
        return prettyName(type, true);
    }

    private static String prettyName(Type type, boolean showTypeVariableBounds) {
        if (type instanceof Class<?>) // Handle a simple type ( like void or Integer )
            return prettyName((Class<?>) type);
        
        if (type instanceof ParameterizedType) { // Handle parameterized types (like List<T>)
            ParameterizedType paramType = (ParameterizedType) type;
            Type[] params = paramType.getActualTypeArguments();
            String[] paramsNames = new String[params.length];
            for (int i = 0; i < params.length; i++) paramsNames[i] = prettyName(params[i], false);
            return String.format("%s<%s>", prettyName(paramType.getRawType()), String.join(", ", paramsNames));
        }

        if (type instanceof TypeVariable<?>) { // Handle type variables (like T or R)
            TypeVariable<?> typeVar = (TypeVariable<?>) type;

            Type[] bounds = typeVar.getBounds();
            if (!showTypeVariableBounds || (bounds.length == 1 && bounds[0] == Object.class)) return typeVar.getName();

            String[] boundsStr = new String[bounds.length];
            for (int i = 0; i < bounds.length; i++) boundsStr[i] = prettyName(bounds[i]);

            return String.format("%s extends %s", typeVar.getName(), String.join(" & ", boundsStr));
        }

        if (type instanceof WildcardType) { // Handle wildcards (like '? extends Number' and '? super Integer')
            WildcardType wildcardType = (WildcardType) type;

            Type[] upperBounds = wildcardType.getUpperBounds();
            if (upperBounds[0] != Object.class) {
                String[] upperBoundsStr = new String[upperBounds.length];
                for (int i = 0; i < upperBounds.length; i++) upperBoundsStr[i] = prettyName(upperBounds[i]);
                return String.format("? extends %s", String.join(" & ", upperBoundsStr));
            }

            Type[] lowerBounds = wildcardType.getLowerBounds();
            if (lowerBounds.length != 0) {
                String[] lowerBoundsStr = new String[lowerBounds.length];
                for (int i = 0; i < lowerBounds.length; i++) lowerBoundsStr[i] = prettyName(lowerBounds[i]);
                return String.format("? super %s", String.join(" & ", lowerBoundsStr));
            }

            return "?";
        }
        
        if (type == null) return "null";

        throw new RuntimeException("Can't return a pretty name because the type is unknown!");
    }

    /** Returns if a specific class is a functional interface */
    public static boolean isFunctionalInterface(Class<?> _class) {
        return _class != null && _class.isInterface() && _class.getAnnotation(FunctionalInterface.class) != null;
    }

    public static bsh.org.objectweb.asm.Type getASMType(Class<?> type) {
        return bsh.org.objectweb.asm.Type.getType(type);
    }

    public static String getInternalName(Class<?> type) {
        return bsh.org.objectweb.asm.Type.getInternalName(type);
    }

    public static String[] getInternalNames(Class<?>[] types) {
        final String[] internalNames = new String[types.length];
        for (int i = 0; i < types.length; i++) internalNames[i] = bsh.org.objectweb.asm.Type.getInternalName(types[i]);
        return internalNames;
    }

    public static String getDescriptor(Class<?> type) {
        return bsh.org.objectweb.asm.Type.getDescriptor(type);
    }

    public static String getMethodDescriptor(Method method) {
        return bsh.org.objectweb.asm.Type.getMethodDescriptor(method);
    }

    public static String getMethodDescriptor(Class<?> returnType, Class<?>  ...argumentTypes) {
        final bsh.org.objectweb.asm.Type[] _types = new bsh.org.objectweb.asm.Type[argumentTypes.length];
        for (int i = 0; i < argumentTypes.length; i++) _types[i] = bsh.org.objectweb.asm.Type.getType(argumentTypes[i]);
        return bsh.org.objectweb.asm.Type.getMethodDescriptor(bsh.org.objectweb.asm.Type.getType(returnType), _types);
    }

    // Helper method to convert Type to ASM style signature
    /** Return the signature of a specific type to be used in ASM bytecodes */
    public static String getASMSignature(Type type) {
        if (type instanceof Class<?>) return bsh.org.objectweb.asm.Type.getDescriptor((Class<?>) type); // Handle a simple type ( like void or Integer )

        if (type instanceof ParameterizedType) { // Handle parameterized types (like List<T>)
            ParameterizedType paramType = (ParameterizedType) type;
            StringBuilder paramSignature = new StringBuilder();
            paramSignature.append("L" + bsh.org.objectweb.asm.Type.getInternalName((Class<?>) paramType.getRawType())); // Base type
            paramSignature.append("<");
            for (Type arg : paramType.getActualTypeArguments()) {
                paramSignature.append(getASMSignature(arg));
            }
            paramSignature.append(">;");
            return paramSignature.toString();
        }

        if (type instanceof TypeVariable<?>) // Handle type variables (like T or R)
            return "T" + ((TypeVariable<?>) type).getName() + ";";

        if (type instanceof WildcardType) { // Handle wildcards (like ? extends Number)
            WildcardType wildcard = (WildcardType) type;
            Type[] lowerBounds = wildcard.getLowerBounds();
            return lowerBounds.length > 0
                    ? "-" + bsh.org.objectweb.asm.Type.getDescriptor((Class<?>) lowerBounds[0])
                    : "+" + bsh.org.objectweb.asm.Type.getDescriptor((Class<?>) wildcard.getUpperBounds()[0]);
        }

        throw new IllegalArgumentException("Can't get the signature of this type because its Class is unknown: " + (type != null ? type.getClass() : null));
    }

    // TODO: testes com enums, interfaces e classes
    public static String getASMClassSignature(TypeVariable<?>[] types, Type superClass, Type ...interfaces) {
        StringBuilder signature = new StringBuilder();

        // 1. Extract type parameters (generics)
        if (types.length != 0) {
            signature.append("<");
            for (TypeVariable<?> typeParam : types) {
                signature.append(typeParam.getName()); // Add the type variable (e.g.: "T")

                for (Type bound : typeParam.getBounds()) // Add the bound of the type variable (e.g.: 'extends Number' => ":Ljava/lang/Number;")
                    signature.append(":").append(getASMSignature(bound));
            }
            signature.append(">");
        }

        // 2. Add the superclass in the signature
        signature.append(getASMSignature(superClass)); // All wrapper classes doesn't have a superclass, thus extends Object

        // 3. Add interfaces in the signature
        for (Type interface_: interfaces) signature.append(getASMSignature(interface_));

        return signature.toString();
    }

    // TODO: testes com vários métodos
    public static String getASMMethodSignature(Method method) {
        return getASMMethodSignature(method.getTypeParameters(), method.getGenericParameterTypes(), method.getGenericReturnType(), method.getGenericExceptionTypes());
    }

    public static String getASMMethodSignature(TypeVariable<?>[] types, Type[] params, Type returnType, Type[] exceptions) {
        StringBuilder signature = new StringBuilder();

        // 1. Handle generic type parameters (if any)
        if (types.length > 0) {
            signature.append("<");
            for (TypeVariable<?> typeParam : types) {
                signature.append(typeParam.getName()); // Add type variable (e.g., "T")
                for (Type bound : typeParam.getBounds())
                    signature.append(":").append(getASMSignature(bound)); // Add the bound
            }
            signature.append(">");
        }

        // 2. Add the method parameter types
        signature.append("(");
        for (Type param : params) signature.append(getASMSignature(param));
        signature.append(")");

        // 3. Add the return type
        signature.append(getASMSignature(returnType));

        // 4. Add the exception types (if any)
        for (Type exceptionType : exceptions) signature.append("^").append(getASMSignature(exceptionType));

        return signature.toString();
    }

    // if (type instanceof ParameterizedType) { // Handle parameterized types (like List<T>)
    //     ParameterizedType paramType = (ParameterizedType) type;
    //     StringBuilder paramSignature = new StringBuilder();
    //     paramSignature.append("L" + bsh.org.objectweb.asm.Type.getInternalName((Class<?>) paramType.getRawType())); // Base type
    //     paramSignature.append("<");
    //     for (Type arg : paramType.getActualTypeArguments()) {
    //         paramSignature.append(getASMSignature(arg));
    //     }
    //     paramSignature.append(">;");
    //     return paramSignature.toString();
    // }

    // // if (type instanceof TypeVariable<?>) // Handle type variables (like T or R)
    // //     return "T" + ((TypeVariable<?>) type).getName() + ";";
    // public static TypeVariable<?> createTypeVariable(String name, Type[] bounds) {
    //     // TODO: see it!
    //     // bounds = bounds == null || bounds.length == 0 ? new Type[] { Object.class } : bounds;

    //     return new TypeVariable<GenericDeclaration>() {
    //         public String getName() { return name; }
    //         public <T extends Annotation> T getAnnotation(Class<T> annotationClass) { return null; }
    //         public Annotation[] getAnnotations() { return new Annotation[0]; }
    //         public Annotation[] getDeclaredAnnotations() { return new Annotation[0]; }
    //         public AnnotatedType[] getAnnotatedBounds() { throw new UnsupportedOperationException("Unimplemented method 'getAnnotatedBounds'"); }
    //         public Type[] getBounds() { return bounds; }
    //         public GenericDeclaration getGenericDeclaration() { throw new UnsupportedOperationException("Unimplemented method 'getGenericDeclaration'"); };
    //     };
    // }
    public static ParameterizedType createParameterizedType(Type rawType, Type[] typeArguments) {
        return new ParameterizedType() {
            public Type[] getActualTypeArguments() { return typeArguments; }
            public Type getRawType() { return rawType; }
            public Type getOwnerType() { return null; }
        };
    }

    // if (type instanceof WildcardType) { // Handle wildcards (like ? extends Number)
    //     WildcardType wildcard = (WildcardType) type;
    //     Type[] lowerBounds = wildcard.getLowerBounds();
    //     return lowerBounds.length > 0
    //             ? "-" + bsh.org.objectweb.asm.Type.getDescriptor((Class<?>) lowerBounds[0])
    //             : "+" + bsh.org.objectweb.asm.Type.getDescriptor((Class<?>) wildcard.getUpperBounds()[0]);
    // }

}
