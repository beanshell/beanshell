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

	public LHS doLHSSuffix(
		Object obj, CallStack callstack, Interpreter interpreter) 
		throws EvalError
	{
		try
		{
			switch(operation)
			{
				case INDEX:
					return doIndex(obj, callstack, interpreter);

				case NAME:
					return doName(obj, callstack, interpreter);

				case PROPERTY:
					return doProperty(obj, callstack, interpreter);

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
			throw new TargetError(
				"target exception", e.getTargetException(), this, true);
		}
	}

	private LHS doName(
		Object obj, CallStack callstack, Interpreter interpreter) 
		throws EvalError, ReflectError, InvocationTargetException 
	{
		if (jjtGetNumChildren() == 0)
			// simple field access
			return Reflect.getLHSObjectField(obj, field);
		else {
			// intermediate method invocation, and field access
			Object[] oa = ((BSHArguments)jjtGetChild(0)).getArguments(
				callstack, interpreter);
			try {
				obj = Reflect.invokeObjectMethod(interpreter, obj, method, oa, this);
			} catch ( EvalError ee ) {
				// catch and re-throw to get line number right
				throw new EvalError( ee.getMessage(), this );
			}
			return Reflect.getLHSObjectField(obj, field);
		}
	}

	private LHS doIndex(
		Object obj, CallStack callstack, Interpreter interpreter) 
		throws EvalError, ReflectError
	{
		if(!obj.getClass().isArray())
			throw new EvalError("Not an array", this);

		int index;
		try
		{
			Primitive val = (Primitive)(((SimpleNode)jjtGetChild(0)).eval(
				callstack, interpreter));
			index = val.intValue();
		}
		catch(Exception e)
		{
			throw new EvalError("You can only index arrays by integer types", this);
		}

		return new LHS(obj, index);
	}
	private LHS doProperty(
		Object obj, CallStack callstack, Interpreter interpreter) 
		throws EvalError, ReflectError
	{
		if(obj == Primitive.VOID)
			throw new EvalError("Attempt to access property on a void type", this);

		else if(obj instanceof Primitive)
			throw new EvalError("Attempt to access property on a primitive", this);

		Object value = ((SimpleNode)jjtGetChild(0)).eval(
			callstack, interpreter);

		if(!(value instanceof String))
			throw new EvalError("Property expression must be a String or identifier.", this);

		Interpreter.debug("LHS property access: ");
		return new LHS(obj, (String)value);
	}
}

