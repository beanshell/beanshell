/*****************************************************************************
 *                                                                           *
 *  This file is part of the BeanShell Java Scripting distribution.          *
 *  Documentation and updates may be found at http://www.beanshell.org/      *
 *                                                                           *
 *  Sun Public License Notice:                                               *
 *                                                                           *
 *  The contents of this file are subject to the Sun Public License Version  *
 *  1.0 (the "License"); you may not use this file except in compliance with *
 *  the License. A copy of the License is available at http://www.sun.com    * 
 *                                                                           *
 *  The Original Code is BeanShell. The Initial Developer of the Original    *
 *  Code is Pat Niemeyer. Portions created by Pat Niemeyer are Copyright     *
 *  (C) 2000.  All Rights Reserved.                                          *
 *                                                                           *
 *  GNU Public License Notice:                                               *
 *                                                                           *
 *  Alternatively, the contents of this file may be used under the terms of  *
 *  the GNU Lesser General Public License (the "LGPL"), in which case the    *
 *  provisions of LGPL are applicable instead of those above. If you wish to *
 *  allow use of your version of this file only under the  terms of the LGPL *
 *  and not to allow others to use your version of this file under the SPL,  *
 *  indicate your decision by deleting the provisions above and replace      *
 *  them with the notice and other provisions required by the LGPL.  If you  *
 *  do not delete the provisions above, a recipient may use your version of  *
 *  this file under either the SPL or the LGPL.                              *
 *                                                                           *
 *  Patrick Niemeyer (pat@pat.net)                                           *
 *  Author of Learning Java, O'Reilly & Associates                           *
 *  http://www.pat.net/~pat/                                                 *
 *                                                                           *
 *****************************************************************************/


package bsh;

/**
    Wrapper for primitive types in Bsh.  This is package public because it 
	is used in the implementation of some bsh commands.

    See the note in LHS.java about wrapping objects.
*/
public class Primitive implements InterpreterConstants, java.io.Serializable
{
    // stored internally in java.lang. wrappers
    private Object value;

    private static class Special implements java.io.Serializable
    {
        private Special() { }

        public static final Special NULL_VALUE = new Special();
        public static final Special VOID_TYPE = new Special();
    }

    /*
        NULL means "no value".
        This ia a placeholder for primitive null value.
    */
    public static final Primitive NULL = new Primitive(Special.NULL_VALUE);

    /*
        VOID means "no type".
        Strictly speaking, this makes no sense here.  But for practical
        reasons we'll consider the lack of a type to be a special value.
    */
    public static final Primitive VOID = new Primitive(Special.VOID_TYPE);

    // private to prevent invocation with param that isn't a primitive-wrapper
    private Primitive(Object value)
    {
        if(value == null)
            throw new InterpreterError("Use Primitve.NULL instead of Primitive(null)");

        this.value = value;
    }

    public Primitive(Number number) { this((Object)number); }

    public Primitive(Boolean value) { this((Object)value); }
    public Primitive(Byte value) { this((Object)value); }
    public Primitive(Short value) { this((Object)value); }
    public Primitive(Character value) { this((Object)value); }
    public Primitive(Integer value) { this((Object)value); }
    public Primitive(Long value) { this((Object)value); }
    public Primitive(Float value) { this((Object)value); }
    public Primitive(Double value) { this((Object)value); }

    public Primitive(boolean value) { this(new Boolean(value)); }
    public Primitive(byte value) { this(new Byte(value)); }
    public Primitive(short value) { this(new Short(value)); }
    public Primitive(char value) { this(new Character(value)); }
    public Primitive(int value) { this(new Integer(value)); }
    public Primitive(long value) { this(new Long(value)); }
    public Primitive(float value) { this(new Float(value)); }
    public Primitive(double value) { this(new Double(value)); }

    public Object getValue()
    {
        if(value == Special.NULL_VALUE)
            return null;
        else if(value == Special.VOID_TYPE)
                throw new InterpreterError("attempt to unwrap void type");
        else
            return value;
    }

    public String toString()
    {
        if(value == Special.NULL_VALUE)
            return "null";
        else if(value == Special.VOID_TYPE)
            return "void";
        else
            return value.toString();
    }

    public Class getType()
    {
        return getType(value);
    }

    private Class getType(Object o)
    {
        if(o instanceof Boolean)
            return Boolean.TYPE;
        else if(o instanceof Byte)
            return Byte.TYPE;
        else if(o instanceof Short)
            return Short.TYPE;
        else if(o instanceof Character)
            return Character.TYPE;
        else if(o instanceof Integer)
            return Integer.TYPE;
        else if(o instanceof Long)
            return Long.TYPE;
        else if(o instanceof Float)
            return Float.TYPE;
        else if(o instanceof Double)
            return Double.TYPE;

        return null;
    }

/*
    public static Primitive binaryOperation(
		Primitive p1, Primitive p2, int kind )
        throws EvalError
    {
		return new Primitive( binaryOperation( p1, p2, kind ) );
    }
*/

	/**
		Allow primitive operations on wrapper types such as Integer and Boolean.
		This is static so that it can be reached from wherever...
	*/
    public static Object binaryOperation(
		Object obj1, Object obj2, int kind)
        throws EvalError
    {
		// special primitive types
        if(obj1 == NULL || obj2 == NULL)
            throw new EvalError("illegal use of null object or 'null' literal");
        if(obj1 == VOID || obj2 == VOID)
            throw new EvalError(
				"illegal use of undefined object or 'void' literal");

		// keep track of the original types
		Class lhsOrgType = obj1.getClass();
		Class rhsOrgType = obj2.getClass();

		// Unwrap primitives
        if(obj1 instanceof Primitive)
            obj1 = ((Primitive)obj1).getValue();
        if(obj2 instanceof Primitive)
            obj2 = ((Primitive)obj2).getValue();

        Object[] operands = promotePrimitives(obj1, obj2);
        Object lhs = operands[0];
        Object rhs = operands[1];

        if(lhs.getClass() != rhs.getClass())
            throw new EvalError("type mismatch in operator.  " 
			+ lhs.getClass() + " cannot be matched with " + rhs.getClass() );

		Object result;
		try {
			result = binaryOperationImpl( lhs, rhs, kind );
		} catch ( ArithmeticException e ) {
			throw new TargetError("Arithemetic Exception in binary op", e);
		}

		// If both original args were Primitives return a Primitive result
		// else it was mixed (wrapper/primitive) return the wrapper type
		if ( lhsOrgType == Primitive.class && rhsOrgType == Primitive.class )
			return new Primitive( result );
		else
			return result;
    }

    static Object binaryOperationImpl( Object lhs, Object rhs, int kind )
        throws EvalError
	{
        if(lhs instanceof Boolean)
            return booleanBinaryOperation((Boolean)lhs, (Boolean)rhs, kind);
        else if(lhs instanceof Integer)
            return intBinaryOperation( (Integer)lhs, (Integer)rhs, kind );
        else if(lhs instanceof Long)
            return longBinaryOperation((Long)lhs, (Long)rhs, kind);
        else if(lhs instanceof Float)
            return floatBinaryOperation((Float)lhs, (Float)rhs, kind);
        else if(lhs instanceof Double)
            return doubleBinaryOperation( (Double)lhs, (Double)rhs, kind);
        else
            throw new EvalError("Invalid types in binary operator" );
	}


    static Boolean booleanBinaryOperation(Boolean B1, Boolean B2, int kind)
        throws EvalError
    {
        boolean lhs = B1.booleanValue();
        boolean rhs = B2.booleanValue();

        switch(kind)
        {
            case EQ:
                return new Boolean(lhs == rhs);

            case NE:
                return new Boolean(lhs != rhs);

            case BOOL_OR:
            case BOOL_ORX:
                return new Boolean( lhs || rhs );

            case BOOL_AND:
            case BOOL_ANDX:
                return new Boolean( lhs && rhs );

            default:
                throw new InterpreterError("unimplemented binary operator");
        }
    }

    // returns Object covering both Long and Boolean return types
    static Object longBinaryOperation(Long L1, Long L2, int kind)
    {
        long lhs = L1.longValue();
        long rhs = L2.longValue();

        switch(kind)
        {
            // boolean
            case LT:
            case LTX:
                return new Boolean(lhs < rhs);

            case GT:
            case GTX:
                return new Boolean(lhs > rhs);

            case EQ:
                return new Boolean(lhs == rhs);

            case LE:
            case LEX:
                return new Boolean(lhs <= rhs);

            case GE:
            case GEX:
                return new Boolean(lhs >= rhs);

            case NE:
                return new Boolean(lhs != rhs);

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
                throw new InterpreterError("Unimplemented binary long operator");
        }
    }

    // returns Object covering both Integer and Boolean return types
    static Object intBinaryOperation(Integer I1, Integer I2, int kind)
    {
        int lhs = I1.intValue();
        int rhs = I2.intValue();

        switch(kind)
        {
            // boolean
            case LT:
            case LTX:
                return new Boolean(lhs < rhs);

            case GT:
            case GTX:
                return new Boolean(lhs > rhs);

            case EQ:
                return new Boolean(lhs == rhs);

            case LE:
            case LEX:
                return new Boolean(lhs <= rhs);

            case GE:
            case GEX:
                return new Boolean(lhs >= rhs);

            case NE:
                return new Boolean(lhs != rhs);

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
                throw new InterpreterError("Unimplemented binary integer operator");
        }
    }

    // returns Object covering both Double and Boolean return types
    static Object doubleBinaryOperation(Double D1, Double D2, int kind)
        throws EvalError
    {
        double lhs = D1.doubleValue();
        double rhs = D2.doubleValue();

        switch(kind)
        {
            // boolean
            case LT:
            case LTX:
                return new Boolean(lhs < rhs);

            case GT:
            case GTX:
                return new Boolean(lhs > rhs);

            case EQ:
                return new Boolean(lhs == rhs);

            case LE:
            case LEX:
                return new Boolean(lhs <= rhs);

            case GE:
            case GEX:
                return new Boolean(lhs >= rhs);

            case NE:
                return new Boolean(lhs != rhs);

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
                throw new EvalError("Can't shift doubles");

            default:
                throw new InterpreterError("Unimplemented binary double operator");
        }
    }
    // returns Object covering both Long and Boolean return types
    static Object floatBinaryOperation(Float F1, Float F2, int kind)
        throws EvalError
    {
        float lhs = F1.floatValue();
        float rhs = F2.floatValue();

        switch(kind)
        {
            // boolean
            case LT:
            case LTX:
                return new Boolean(lhs < rhs);

            case GT:
            case GTX:
                return new Boolean(lhs > rhs);

            case EQ:
                return new Boolean(lhs == rhs);

            case LE:
            case LEX:
                return new Boolean(lhs <= rhs);

            case GE:
            case GEX:
                return new Boolean(lhs >= rhs);

            case NE:
                return new Boolean(lhs != rhs);

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
                throw new EvalError("Can't shift floats ");

            default:
                throw new InterpreterError("Unimplemented binary float operator");
        }
    }

    static Object promotePrimitive(Object primitive)
    {
        if(primitive instanceof Character)
            return new Integer(((Character)primitive).charValue());
        else if((primitive instanceof Byte) || (primitive instanceof Short))
            return new Integer(((Number)primitive).intValue());

        return primitive;
    }

    static Object[] promotePrimitives(Object lhs, Object rhs)
    {
        lhs = promotePrimitive(lhs);
        rhs = promotePrimitive(rhs);

        if((lhs instanceof Number) && (rhs instanceof Number))
        {
            Number lnum = (Number)lhs;
            Number rnum = (Number)rhs;

            boolean b;

            if((b = (lnum instanceof Double)) || (rnum instanceof Double))
            {
                if(b)
                    rhs = new Double(rnum.doubleValue());
                else
                    lhs = new Double(lnum.doubleValue());
            }
            else if((b = (lnum instanceof Float)) || (rnum instanceof Float))
            {
                if(b)
                    rhs = new Float(rnum.floatValue());
                else
                    lhs = new Float(lnum.floatValue());
            }
            else if((b = (lnum instanceof Long)) || (rnum instanceof Long))
            {
                if(b)
                    rhs = new Long(rnum.longValue());
                else
                    lhs = new Long(lnum.longValue());
            }
        }

        return new Object[] { lhs, rhs };
    }

    public static Primitive unaryOperation(Primitive val, int kind)
        throws EvalError
    {
        if(val == NULL)
            throw new EvalError("illegal use of null object or 'null' literal");
        if(val == VOID)
            throw new EvalError("illegal use of undefined object or 'void' literal");

        Class operandType = val.getType();
        Object operand = promotePrimitive(val.getValue());

        if(operand instanceof Boolean)
            return new Primitive(booleanUnaryOperation((Boolean)operand, kind));
        else if(operand instanceof Integer)
        {
            int result = intUnaryOperation((Integer)operand, kind);

            // ++ and -- must be cast back the original type
            if(kind == INCR || kind == DECR)
            {
                if(operandType == Byte.TYPE)
                    return new Primitive((byte)result);
                if(operandType == Short.TYPE)
                    return new Primitive((short)result);
                if(operandType == Character.TYPE)
                    return new Primitive((char)result);
            }

            return new Primitive(result);
        }
        else if(operand instanceof Long)
            return new Primitive(longUnaryOperation((Long)operand, kind));
        else if(operand instanceof Float)
            return new Primitive(floatUnaryOperation((Float)operand, kind));
        else if(operand instanceof Double)
            return new Primitive(doubleUnaryOperation((Double)operand, kind));
        else
            throw new InterpreterError("An error occurred.  Please call technical support.");
    }

    static boolean booleanUnaryOperation(Boolean B, int kind) throws EvalError
    {
        boolean operand = B.booleanValue();
        switch(kind)
        {
            case BANG:
                return !operand;

            default:
                throw new EvalError("Operator inappropriate for boolean");
        }
    }

    static int intUnaryOperation(Integer I, int kind)
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

            default:
                throw new InterpreterError("bad integer unaryOperation");
        }
    }

    static long longUnaryOperation(Long L, int kind)
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

            default:
                throw new InterpreterError("bad long unaryOperation");
        }
    }

    static float floatUnaryOperation(Float F, int kind)
    {
        float operand = F.floatValue();

        switch(kind)
        {
            case PLUS:
                return operand;

            case MINUS:
                return -operand;

            default:
                throw new InterpreterError("bad float unaryOperation");
        }
    }

    static double doubleUnaryOperation(Double D, int kind)
    {
        double operand = D.doubleValue();

        switch(kind)
        {
            case PLUS:
                return operand;

            case MINUS:
                return -operand;

            default:
                throw new InterpreterError("bad double unaryOperation");
        }
    }

    public int intValue() throws EvalError
    {
        if(value instanceof Number)
            return((Number)value).intValue();
        else
            throw new EvalError("Primitive not a number");
    }

    public boolean booleanValue() throws EvalError
    {
        if(value instanceof Boolean)
            return((Boolean)value).booleanValue();
        else
            throw new EvalError("Primitive not a boolean");
    }

    public Number numberValue() throws EvalError
    {
        if(value instanceof Number)
            return (Number)value;
        else
            throw new EvalError("Primitive not a number");
    }

	public boolean equals( Object obj ) {
		if ( obj instanceof Primitive )
			return ((Primitive)obj).value.equals( this.value );
		else
			return obj.equals( this.value );
	}

}
