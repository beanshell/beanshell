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
	Warning: this is a hack... should be unified with BSHLHSPrimarySuffix
*/
package bsh;

import java.util.Hashtable;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;

class BSHPrimarySuffix extends SimpleNode
{
	public static final int
		CLASS = 0,
		INDEX = 1,
		NAME = 2,
		PROPERTY = 3;

	public int operation;
	Object index;
	public String field;

	BSHPrimarySuffix(int id) { super(id); }

	/*
		Perform a suffix operation on the given object and return the 
		new value.

		obj will be a Node when suffix evaluation begins, allowing us to
		interpret it contextually. (e.g. for .class) Thereafter it will be 
		a normal object.
	*/
	public Object doSuffix(Object obj, NameSpace namespace, Interpreter interpreter) throws EvalError
	{
		// Handle ".class" suffix operation
		if ( operation == CLASS )
			if ( obj instanceof BSHAmbiguousName )
				return ((BSHAmbiguousName)obj).toClass(namespace );
			else
				throw new EvalError("Trying to call .class on something inappropriate...", this);

		// Handle other suffix operations

		/*
			eval( ) the node to an object

			Note: This construct is now necessary where the node may be
			an ambiguous name.  If this becomes common we might want to 
			make a static method nodeToObject() or something.
		*/
		if ( obj instanceof SimpleNode )
			if ( obj instanceof BSHAmbiguousName )
				obj = ((BSHAmbiguousName)obj).toObject(namespace, interpreter);
			else
				obj = ((SimpleNode)obj).eval(namespace, interpreter);	

		try
		{
			switch(operation)
			{
				case INDEX:
					return doIndex(obj, namespace, interpreter );

				case NAME:
					return doName(obj, namespace, interpreter );

				case PROPERTY:
					return doProperty(obj, namespace, interpreter );

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
			throw new TargetError(e, this);
		}
	}

	/*
		Field access or a method invocation
		Field access might be .length on an array
	*/
	private Object doName(
		Object obj, NameSpace namespace, Interpreter interpreter) 
		throws EvalError, ReflectError, InvocationTargetException
	{
		if(field.equals("length") && obj.getClass().isArray())
			return new Primitive(Array.getLength(obj));
		
		if (jjtGetNumChildren() == 0)
			return Reflect.getObjectField(obj, field);
		else
		{
			Object[] oa = ((BSHArguments)jjtGetChild(0)).getArguments(namespace, interpreter);
			try {
				return Reflect.invokeObjectMethod(interpreter, obj, field, oa);
			} catch ( EvalError ee ) {
				// catch and re-throw to get line number right
				throw new EvalError( ee.getMessage(), this );
			}
		}
	}

	private Object doIndex(Object obj, NameSpace namespace, Interpreter interpreter) throws EvalError, ReflectError
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

		return Reflect.getIndex(obj, index);
	}

	private Object doProperty( Object obj, NameSpace namespace, Interpreter interpreter ) throws EvalError
	{
		if(obj == Primitive.VOID)
			throw new EvalError("Attempt to access property on void type", this);

		if(obj instanceof Primitive)
			throw new EvalError("Attempt to access property on a primitive", this);

		Object value = ((SimpleNode)jjtGetChild(0)).eval(namespace, interpreter);
		if(!(value instanceof String))
			throw new EvalError("Property expression must be a String or identifier.", this);

		// property style access to hashtable
		if(obj instanceof Hashtable)
		{
			Object val = ((Hashtable)obj).get((String)value);
			if(val == null)
				val = Primitive.NULL;
			return val;
		}

		try
		{
			return Reflect.getObjectProperty(obj, (String)value);
		}
		catch(ReflectError e)
		{
			Interpreter.debug(e.toString());
			throw new EvalError("No such property: " + value, this);
		}
	}
}

