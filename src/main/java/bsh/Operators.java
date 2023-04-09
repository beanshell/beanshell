/** Copyright 2018 Nick nickl- Lombard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. */
package bsh;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import bsh.congo.tree.Operator;
import bsh.congo.parser.Token.TokenType;
import static bsh.congo.parser.Token.TokenType.*;

class Operators {

    private static final List<TokenType> OVERFLOW_OPS
        = Arrays.asList(PLUS, MINUS, STAR, POWER);
    private static final List<TokenType> COMPARABLE_OPS
        = Arrays.asList(LT, GT, EQ, LE, GE, NE);

    /** Constructor private no instance required. */
    private Operators() {}

    /** Binary operations on arbitrary objects.
     * @param lhs left hand side value
     * @param rhs right hand side value
     * @param kind operator type
     * @return operator applied value
     * @throws UtilEvalError evaluation error */
    @SuppressWarnings("unchecked")
    public static Object arbitraryObjectsBinaryOperation(Object lhs, Object rhs, TokenType kind)
            throws UtilEvalError {
        if ( kind == EQ )
            return (lhs == rhs) ? Primitive.TRUE : Primitive.FALSE;
        if ( kind == NE )
            return (lhs != rhs) ? Primitive.TRUE : Primitive.FALSE;

        if ( lhs == Primitive.VOID || rhs == Primitive.VOID )
            throw new UtilEvalError(
                "illegal use of undefined variable, class, or"
                    + " 'void' literal");

        if (kind == SPACESHIP) {
            int comp = 0; // used to ensure only -1, 0, and 1 is returned.
            if (lhs instanceof Comparable || rhs instanceof Comparable)
                comp = Comparator.nullsFirst( // nullsFirst Comparable Comparator
                    Comparator.<Comparable<Object>>naturalOrder())
                        .compare((Comparable<Object>)Primitive.unwrap(lhs),
                            (Comparable<Object>)Primitive.unwrap(rhs));
            else
                comp = Comparator.nullsFirst( // nullsFirst toString Comparator
                    Comparator.comparing(Object::toString))
                    .compare(Primitive.unwrap(lhs), Primitive.unwrap(rhs));
            return Primitive.wrap(comp < 0 ? -1 : comp > 0 ? 1 : 0, Integer.TYPE);
        }

        if ( kind == PLUS ) {
            // String concatenation operation
            if ( lhs instanceof String || rhs instanceof String )
                return BSHLiteral.internStrings
                    ? (String.valueOf(lhs) + String.valueOf(rhs)).intern()
                    : String.valueOf(lhs) + String.valueOf(rhs);
            // array concatenation operation
            if ( lhs.getClass().isArray() && rhs instanceof List )
                rhs = ((List<?>) rhs).toArray();
            if ( lhs.getClass().isArray()
                    && rhs.getClass().isArray() )
                return BshArray.concat(lhs, rhs);
            // list concatenation operation
            if ( lhs instanceof List && rhs.getClass().isArray() )
                rhs = Types.castObject(rhs, List.class, Types.CAST);
            if ( lhs instanceof List && rhs instanceof List )
                return BshArray.concat(
                        (List<?>) lhs, (List<?>) rhs);
        }
        if ( kind == STAR ) {
            // array repeat operation
            if ( lhs.getClass().isArray() )
                return BshArray.repeat(lhs,
                    (int) Primitive.castWrapper(Integer.TYPE, rhs));
            if ( rhs.getClass().isArray() )
                return BshArray.repeat(rhs,
                    (int) Primitive.castWrapper(Integer.TYPE, lhs));
            // List repeat operation
            if ( lhs instanceof List )
                return BshArray.repeat((List<Object>) lhs,
                    (int) Primitive.castWrapper(Integer.TYPE, rhs));
            if ( rhs instanceof List )
                return BshArray.repeat((List<Object>) rhs,
                    (int) Primitive.castWrapper(Integer.TYPE, lhs));
            try {
                // String repeat operation
                if ( lhs instanceof String )
                    return BSHLiteral.internStrings
                        ? new String(new char[(int) Primitive.castWrapper(Integer.TYPE, rhs)])
                            .replace("\0", String.valueOf(lhs)).intern()
                        : new String(new char[(int) Primitive.castWrapper(Integer.TYPE, rhs)])
                            .replace("\0", String.valueOf(lhs));
                if ( rhs instanceof String )
                    return BSHLiteral.internStrings
                        ? new String(new char[(int) Primitive.castWrapper(Integer.TYPE, lhs)])
                            .replace("\0", String.valueOf(rhs)).intern()
                        : new String(new char[(int) Primitive.castWrapper(Integer.TYPE, lhs)])
                            .replace("\0", String.valueOf(rhs));
            } catch (NegativeArraySizeException e) {
                throw new UtilEvalError("Negative repeat operand: "+e.getMessage(), e);
            }
        }

        if ( lhs instanceof String || rhs instanceof String )
            throw new UtilEvalError(
                "Use of non + operator with String" );
        if ( lhs.getClass().isArray() || rhs.getClass().isArray()
               || lhs instanceof List || rhs instanceof List)
            throw new UtilEvalError(
                "Use of invalid operator " + kind
                    + " with array or List type" );
        if ( lhs == Primitive.NULL || rhs == Primitive.NULL )
            throw new UtilEvalError(
                "illegal use of null value or 'null' literal");

        throw new UtilEvalError("Operator: " + kind
                    + " inappropriate for objects");
    }
    /**
    Perform a binary operation on two Primitives or wrapper types.
    If both original args were Primitives return a Primitive result
    else it was mixed (wrapper/primitive) return the wrapper type.
    The exception is for boolean operations where we will return the
    primitive type either way.
    */
    public static Object binaryOperation(Object obj1, Object obj2, TokenType kind)
            throws UtilEvalError {

        // Unwrap primitives
        Object lhs = Primitive.unwrap(obj1);
        Object rhs = Primitive.unwrap(obj2);

        if ( Types.isNumeric(lhs) && Types.isNumeric(rhs) ) {
            Object[] operands = promotePrimitives(lhs, rhs);
            lhs = operands[0];
            rhs = operands[1];
        }

        if ( lhs.getClass() != rhs.getClass() )
            throw new UtilEvalError("Type mismatch in operator.  "
                    + lhs.getClass() + " cannot be used with " + rhs.getClass());

        Object result;
        try {
            result = binaryOperationImpl( lhs, rhs, kind );
        } catch (ArithmeticException e) {
            throw new UtilTargetError("Arithemetic Exception in binary op", e);
        }

        if ( result instanceof Boolean )
            return ((Boolean) result).booleanValue() ? Primitive.TRUE : Primitive.FALSE;

        // If both original args were Primitives return a Primitive result
        // else it was mixed (wrapper/primitive) return the wrapper type
        // Exception is for boolean result, return the primitive
        if ( obj1 instanceof Primitive && obj2 instanceof Primitive )
            if ( Types.isFloatingpoint(result) && lhs.getClass() == BigDecimal.class )
                return Primitive.wrap(result, result.getClass());
            else
                return Primitive.shrinkWrap(result);

        return Primitive.shrinkWrap(result).getValue();
    }

    @SuppressWarnings("unchecked")
    static <T> Object binaryOperationImpl( T lhs, T rhs, TokenType kind )
        throws UtilEvalError
    {
        if (kind == SPACESHIP) // compares two non null numbers
            return ((Comparable<T>)lhs).compareTo(rhs);
        if (lhs instanceof Boolean)
            return booleanBinaryOperation( (Boolean) lhs, (Boolean) rhs, kind );
        if (COMPARABLE_OPS.contains(kind))
            return comparableBinaryBooleanOperations((Comparable<T>) lhs, rhs, kind);
        if (lhs instanceof BigInteger)
            return bigIntegerBinaryOperation( (BigInteger) lhs, (BigInteger) rhs, kind );
        if (lhs instanceof BigDecimal)
            return bigDecimalBinaryOperation( (BigDecimal) lhs, (BigDecimal) rhs, kind );
        if (Types.isFloatingpoint(lhs))
            return doubleBinaryOperation( (Double) lhs, (Double) rhs, kind );
        if (lhs instanceof Number)
            return longBinaryOperation( (Long) lhs, (Long) rhs, kind );
        throw new UtilEvalError("Invalid types in binary operator" );
    }

    static Boolean booleanBinaryOperation(Boolean B1, Boolean B2, TokenType kind)
    {
        boolean lhs = B1.booleanValue();
        boolean rhs = B2.booleanValue();

        switch(kind)
        {
            case EQ:
                return lhs == rhs;

            case NE:
                return lhs != rhs;

            case SC_OR:
                // already evaluated lhs TRUE
                // see BSHBinaryExpression
                return false || rhs;

            case SC_AND:
                // already evaluated lhs FALSE
                // see BSHBinaryExpression
                return true && rhs;

            case BIT_AND:
                return lhs & rhs;

            case BIT_OR:
                return lhs | rhs;

            case XOR:
                return lhs ^ rhs;

        }
        throw new InterpreterError("unimplemented binary operator");
    }

    static <T> Boolean comparableBinaryBooleanOperations(Comparable<T> lhs, T rhs, TokenType kind) {
        switch(kind)
        {
            // boolean
            case LT:
                return lhs.compareTo(rhs) < 0;

            case GT:
                return lhs.compareTo(rhs) > 0;

            case LE:
                return lhs.compareTo(rhs) <= 0;

            case GE:
                return lhs.compareTo(rhs) >= 0;

            case NE:
                return lhs.compareTo(rhs) != 0;

            case EQ:
            default:
                return lhs.compareTo(rhs) == 0;
        }
    }

    // returns Object covering both Long and Boolean return types
    static Object longBinaryOperation(long lhs, long rhs, TokenType kind)
    {
        switch(kind)
        {
            // arithmetic
            case PLUS:
                if ( lhs > 0 && (Long.MAX_VALUE - lhs) < rhs )
                    break;
                return lhs + rhs;

            case MINUS:
                if ( lhs < 0 && (Long.MIN_VALUE - lhs) > -rhs )
                    break;
                return lhs - rhs;

            case STAR:
                if ( lhs != 0 && Long.MAX_VALUE / lhs < rhs )
                    break;
                return lhs * rhs;

            case SLASH:
                return lhs / rhs;

            case MOD:
                return lhs % rhs;

            case POWER:
                double check = Math.pow(lhs, rhs);
                BigInteger bi = BigDecimal.valueOf(check).toBigInteger();
                if ( bi.compareTo(Primitive.LONG_MIN) >= 0 && bi.compareTo(Primitive.LONG_MAX) <= 0 )
                    return (long) check;
                break;

            // bitwise
            case LSHIFT:
                return lhs << rhs;

            case RSIGNEDSHIFT:
                return lhs >> rhs;

            case RUNSIGNEDSHIFT:
                return lhs >>> rhs;

            case BIT_AND:
                return lhs & rhs;

            case BIT_OR:
                return lhs | rhs;

            case XOR:
                return lhs ^ rhs;

        }
        if ( OVERFLOW_OPS.contains(kind) )
            return bigIntegerBinaryOperation(BigInteger.valueOf(lhs), BigInteger.valueOf(rhs), kind);

        throw new InterpreterError(
                "Unimplemented binary long operator");
    }

    // returns Object covering both Integer and Boolean return types
    static Object bigIntegerBinaryOperation(BigInteger lhs, BigInteger rhs, TokenType kind)
    {

        switch(kind)
        {
            // arithmetic
            case PLUS:
                return lhs.add(rhs);

            case MINUS:
                return lhs.subtract(rhs);

            case STAR:
                return lhs.multiply(rhs);

            case SLASH:
                return lhs.divide(rhs);

            case MOD:
                return lhs.mod(rhs);

            case POWER:
                return lhs.pow(rhs.intValue());

            // bitwise
            case LSHIFT:
                return lhs.shiftLeft(rhs.intValue());

            case RSIGNEDSHIFT:
                return lhs.shiftRight(rhs.intValue());

            case RUNSIGNEDSHIFT:
                if ( lhs.signum() >= 0 )
                    return lhs.shiftRight(rhs.intValue());
                BigInteger opener = BigInteger.ONE.shiftLeft(lhs.toString(2).length() + 1);
                BigInteger opened = lhs.subtract(opener);
                BigInteger mask = opener.subtract(BigInteger.ONE).shiftRight(rhs.intValue() + 1);
                return opened.shiftRight(rhs.intValue()).and(mask);


            case BIT_AND:
                return lhs.and(rhs);

            case BIT_OR:
                return lhs.or(rhs);

            case XOR:
                return lhs.xor(rhs);

        }
        throw new InterpreterError(
                "Unimplemented binary integer operator");
    }

    // returns Object covering both Double and Boolean return types
    static Object doubleBinaryOperation(double lhs, double rhs, TokenType kind)
        throws UtilEvalError
    {
        switch(kind)
        {
            // arithmetic
            case PLUS:
                if ( lhs > 0d && (Double.MAX_VALUE - lhs) < rhs )
                    break;
                return lhs + rhs;

            case MINUS:
                if ( lhs < 0d && (-Double.MAX_VALUE - lhs) > -rhs )
                    break;
                return lhs - rhs;

            case STAR:
                if ( lhs != 0 && Double.MAX_VALUE / lhs < rhs )
                    break;
                return lhs * rhs;

            case SLASH:
                return lhs / rhs;

            case MOD:
                return lhs % rhs;

            case POWER:
                double check = Math.pow(lhs, rhs);
                if ( Double.isInfinite(check) )
                    break;
                return check;

            // can't shift floating-point values
            case LSHIFT:
            case RSIGNEDSHIFT:
            case RUNSIGNEDSHIFT:
                throw new UtilEvalError("Can't shift floatingpoint values");

        }
        if ( OVERFLOW_OPS.contains(kind) )
            return bigDecimalBinaryOperation(BigDecimal.valueOf(lhs), BigDecimal.valueOf(rhs), kind);

        throw new InterpreterError(
                "Unimplemented binary double operator");
    }

    // returns Object covering both Long and Boolean return types
    static Object bigDecimalBinaryOperation(BigDecimal lhs, BigDecimal rhs, TokenType kind)
        throws UtilEvalError
    {
        switch(kind)
        {
            // arithmetic
            case PLUS:
                return lhs.add(rhs);

            case MINUS:
                return lhs.subtract(rhs);

            case STAR:
                return lhs.multiply(rhs);

            case SLASH:
                return lhs.divide(rhs);

            case MOD:
                return lhs.remainder(rhs);

            case POWER:
                return lhs.pow(rhs.intValue());

            // can't shift floats
            case LSHIFT:
            case RSIGNEDSHIFT:
            case RUNSIGNEDSHIFT:
                throw new UtilEvalError("Can't shift floatingpoint values");

        }
        throw new InterpreterError(
                "Unimplemented binary float operator");
    }

    /**
        Promote primitive wrapper type to Integer wrapper type
    */
    static Number promoteToInteger(Object wrapper )
    {
        if ( wrapper instanceof Character )
            return Integer.valueOf(((Character) wrapper).charValue());
        if ( wrapper instanceof Byte || wrapper instanceof Short )
            return Integer.valueOf(((Number) wrapper).intValue());

        return (Number) wrapper;
    }

    /**
        Promote the pair of primitives to the maximum type of the two.
        e.g. [int,long]->[long,long]
    */
    static Object[] promotePrimitives(Object lhs, Object rhs)
    {
        Number lnum = promoteToInteger(lhs);
        Number rnum = promoteToInteger(rhs);

        if ( lhs instanceof BigDecimal ) {
            if ( !(rhs instanceof BigDecimal) )
                rhs = Primitive.castNumber(BigDecimal.class, rnum);
        } else if ( rhs instanceof BigDecimal ) {
            lhs = Primitive.castNumber(BigDecimal.class, lnum);
        } else if ( Types.isFloatingpoint(lhs) || Types.isFloatingpoint(rhs)) {
            if ( !(lhs instanceof Double) )
                lhs = Double.valueOf(lnum.doubleValue());
            if ( !(rhs instanceof Double) )
                rhs = Double.valueOf(rnum.doubleValue());
        } else if ( lhs instanceof BigInteger ) {
            if ( !(rhs instanceof BigInteger) )
                rhs = Primitive.castNumber(BigInteger.class, rnum);
        } else if ( rhs instanceof BigInteger ) {
            lhs = Primitive.castNumber(BigInteger.class, lnum);
        } else {
            if ( !(lhs instanceof Long) )
                lhs = Long.valueOf(lnum.longValue());
            if ( !(rhs instanceof Long) )
                rhs = Long.valueOf(rnum.longValue());
        }

        return new Object[] { lhs, rhs };
    }

    public static Primitive unaryOperation(Primitive val, TokenType kind)
        throws UtilEvalError
    {
        if (val == Primitive.NULL)
            throw new UtilEvalError(
                "illegal use of null object or 'null' literal");
        if (val == Primitive.VOID)
            throw new UtilEvalError(
                "illegal use of undefined object or 'void' literal");

        Class<?> operandType = val.getType();

        if ( operandType == Boolean.TYPE )
            return booleanUnaryOperation((Boolean) val.getValue(), kind)
                ? Primitive.TRUE : Primitive.FALSE;

        Number operand = promoteToInteger(val.getValue());
        if(operand instanceof Integer)
        {
            int result = intUnaryOperation((Integer) operand, kind);

            // ++ and -- must be cast back the original type
            if(kind == INCR || kind == DECR)
            {
                if(operandType == Byte.TYPE)
                    return new Primitive((byte) result);
                if(operandType == Short.TYPE)
                    return new Primitive((short) result);
                if(operandType == Character.TYPE)
                    return new Primitive((char) result);
            }

            return new Primitive(result);
        }
        if(operand instanceof Long)
            return new Primitive(longUnaryOperation(operand.longValue(), kind));
        if(operand instanceof Float)
            return new Primitive(floatUnaryOperation(operand.floatValue(), kind));
        if(operand instanceof Double)
            return new Primitive(doubleUnaryOperation(operand.doubleValue(), kind));
        if(operand instanceof BigInteger)
            return new Primitive(bigIntegerUnaryOperation((BigInteger) operand, kind));
        if(operand instanceof BigDecimal)
            return new Primitive(bigDecimalUnaryOperation((BigDecimal) operand, kind));

        throw new InterpreterError(
            "An error occurred.  Please call technical support.");
    }

    static boolean booleanUnaryOperation(Boolean B, TokenType kind)
        throws UtilEvalError
    {
        boolean operand = B.booleanValue();
        switch(kind)
        {
            case BANG:
                return !operand;
        }
        throw new UtilEvalError("Operator inappropriate for boolean");
    }

    static int intUnaryOperation(Integer I, TokenType kind)
    {
        int operand = I.intValue();

        switch(kind)
        {
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
        }
        throw new InterpreterError("bad integer unaryOperation");
    }

    static BigInteger bigIntegerUnaryOperation(BigInteger operand, TokenType kind)
    {
        switch(kind)
        {
            case PLUS:
                return operand;
            case MINUS:
                return operand.negate();
            case TILDE:
                return operand.not();
            case INCR:
                return operand.add(BigInteger.ONE);
            case DECR:
                return operand.subtract(BigInteger.ONE);
        }
        throw new InterpreterError("bad big integer unaryOperation");
    }

    static BigDecimal bigDecimalUnaryOperation(BigDecimal operand, TokenType kind)
    {
        switch(kind)
        {
            case PLUS:
                return operand;
            case MINUS:
                return operand.negate();
            case TILDE:
                return operand.signum() == 1 ? operand.negate() : operand;
            case INCR:
                return operand.add(BigDecimal.ONE);
            case DECR:
                return operand.subtract(BigDecimal.ONE);
        }
        throw new InterpreterError("bad big decimal unaryOperation");
    }

    static long longUnaryOperation(Long L, TokenType kind)
    {
        long operand = L.longValue();

        switch(kind)
        {
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
        }
        throw new InterpreterError("bad long unaryOperation");
    }

    static float floatUnaryOperation(Float F, TokenType kind)
    {
        float operand = F.floatValue();

        switch(kind)
        {
            case PLUS:
                return operand;
            case MINUS:
                return -operand;
            case INCR:
                return operand + 1;
            case DECR:
                return operand - 1;
        }
        throw new InterpreterError("bad float unaryOperation");
    }

    static double doubleUnaryOperation(Double D, TokenType kind)
    {
        double operand = D.doubleValue();

        switch(kind)
        {
            case PLUS:
                return operand;
            case MINUS:
                return -operand;
            case INCR:
                return operand + 1;
            case DECR:
                return operand - 1;
        }
        throw new InterpreterError("bad double unaryOperation");
    }
}
