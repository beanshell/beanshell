/*****************************************************************************
 *                                                                           *
 *  This file is part of the BeanShell Java Scripting distribution.          *
 *  Documentation and updates may be found at http://www.beanshell.org/      *
 *                                                                           *
 *  BeanShell is distributed under the terms of the LGPL:                    *
 *  GNU Library Public License http://www.gnu.org/copyleft/lgpl.html         *
 *                                                                           *
 *  Patrick Niemeyer (pat@pat.net)                                           *
 *  Author of Exploring Java, O'Reilly & Associates                          *
 *  http://www.pat.net/~pat/                                                 *
 *                                                                           *
 *****************************************************************************/


/*
	Warning: this is a hack... should be unified with BSHPrimarySuffix
*/
package bsh;

import java.util.Hashtable;
import java.lang.reflect.InvocationTargetException;

class BSHLHSPrimarySuffix extends SimpleNode
{
	public static final int
		INDEX = 1,
		NAME = 2,
		PROPERTY = 3;

	public int operation;	// field access or index
	Object index;			// the index value if any
	/*
		If this is a simple field access field is the field
		If we're hopscotching a method to a field, method is the
		method name and field is the field of the resulting object
	*/
	public String field;
	public String method;

	BSHLHSPrimarySuffix(int id) { super(id); }

	public LHS doLHSSuffix(Object obj, NameSpace namespace, Interpreter interpreter) throws EvalError
	{
		try
		{
			switch(operation)
			{
				case INDEX:
					return doIndex(obj, namespace, interpreter);

				case NAME:
					return doName(obj, namespace, interpreter);

				case PROPERTY:
					return doProperty(obj, namespace, interpreter);

				default:
					throw new InterpreterError("LHS suffix");
			}
		}
		catch(ReflectError e)
		{
			throw new EvalError("reflection error: " + e, this);
		}
		catch(InvocationTargetException e)
		{
			throw new TargetError(e.getTargetException(), this);
		}
	}

	private LHS doName(
		Object obj, NameSpace namespace, Interpreter interpreter) 
		throws EvalError, ReflectError, InvocationTargetException 
	{
		if (jjtGetNumChildren() == 0)
			// simple field access
			return Reflect.getLHSObjectField(obj, field);
		else {
			// intermediate method invocation, and field access
			Object[] oa = ((BSHArguments)jjtGetChild(0)).getArguments(namespace, interpreter);
			try {
				obj = Reflect.invokeObjectMethod(interpreter, obj, method, oa);
			} catch ( EvalError ee ) {
				// catch and re-throw to get line number right
				throw new EvalError( ee.getMessage(), this );
			}
			return Reflect.getLHSObjectField(obj, field);
		}
	}

	private LHS doIndex(Object obj, NameSpace namespace, Interpreter interpreter) throws EvalError, ReflectError
	{
		if(!obj.getClass().isArray())
			throw new EvalError("Not an array", this);

		int index;
		try
		{
			Primitive val = (Primitive)(((SimpleNode)jjtGetChild(0)).eval(namespace, interpreter));
			index = val.intValue();
		}
		catch(Exception e)
		{
			throw new EvalError("You can only index arrays by integer types", this);
		}

		return new LHS(obj, index);
	}

	private LHS doProperty(Object obj, NameSpace namespace, Interpreter interpreter) throws EvalError, ReflectError
	{
		if(obj == Primitive.VOID)
			throw new EvalError("Attempt to access property on a void type", this);

		else if(obj instanceof Primitive)
			throw new EvalError("Attempt to access property on a primitive", this);

		Object value = ((SimpleNode)jjtGetChild(0)).eval(namespace, interpreter);
		if(!(value instanceof String))
			throw new EvalError("Property expression must be a String or identifier.", this);

		Interpreter.debug("LHS property access: ");
		return new LHS(obj, (String)value);
	}
}

