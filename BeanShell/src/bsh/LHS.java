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


package bsh;

import java.lang.reflect.Field;
import java.util.Hashtable;

/**
	The left hand side in an assignment

	This is probably the most debatable design issue in bsh...
	Because of the way things started, the grammar splits on whether
	something is an LHS or not...  The alternative would be to wrap all
	objects in bsh, rather than just carrying types that might be used in
	an assignment.  Wrapping all objects, say, in a generalized BshObject 
	type, would also provide a nice place to put all the reflection stuff, 
	which is now static in bsh.Reflect

	Note: moving some stuff from Reflect to a BshObject, but not going
	as far as the above yet...
*/
class LHS implements InterpreterConstants, java.io.Serializable
{
	NameSpace nameSpace;

	static final int
		VARIABLE = 0,
		FIELD = 1,
		PROPERTY = 2,
		INDEX = 3;

	int type;

	String varName;
	String propName;
	Field field;
	Object object;
	int index;

	LHS(NameSpace nameSpace, String varName)
	{
		type = VARIABLE;
		this.varName = varName;
		this.nameSpace = nameSpace;
	}

	// Static field
	LHS(Field field)
	{
		type = FIELD;
		this.object = null;
		this.field = field;
	}

	// Object field
	LHS(Object object, Field field)
	{
		if(object == null)
			throw new NullPointerException("constructed empty LHS");

		type = FIELD;
		this.object = object;
		this.field = field;
	}

	// Object property
	LHS(Object object, String propName)
	{
		if(object == null)
			throw new NullPointerException("constructed empty LHS");

		type = PROPERTY;
		this.object = object;
		this.propName = propName;
	}

	// Array index
	LHS(Object array, int index)
	{
		if(array == null)
			throw new NullPointerException("constructed empty LHS");

		type = INDEX;
		this.object = array;
		this.index = index;
	}

	public Object getValue() throws EvalError
	{
		if(type == VARIABLE)
			return nameSpace.getVariable(varName);
		else if(type == FIELD)
			try
			{
				return field.get(object);
			}
			catch(IllegalAccessException e2)
			{
				throw new EvalError("Can't read field: " + field);
			}
		else if(type == PROPERTY)
			try
			{
				return Reflect.getObjectProperty(object, propName);
			}
			catch(ReflectError e)
			{
				Interpreter.debug(e.getMessage());
				throw new EvalError("No such property: " + propName);
			}
		else if(type == INDEX)
			try
			{
				return Reflect.getIndex(object, index);
			}
			catch(Exception e)
			{
				throw new EvalError("Array access: " + e);
			}

		throw new InterpreterError("LHS type");
	}

	public Object assign( Object val ) throws EvalError
	{
		if(type == VARIABLE)
			nameSpace.setVariable(varName, val);
		else if(type == FIELD)
			try
			{
				if(val instanceof Primitive)
					val = ((Primitive)val).getValue();

				field.set(object, val);

				return val;
			}
			catch(NullPointerException e)
			{
				throw new EvalError("LHS not a static field.");
			}
			catch(IllegalAccessException e2)
			{
				throw new EvalError("LHS can't access field.");
			}
			catch(IllegalArgumentException e3)
			{
				throw new EvalError("Argument type mismatch");
			}
		else if(type == PROPERTY)
			if(object instanceof Hashtable)
				((Hashtable)object).put(propName, val);
			else
				try
				{
					Reflect.setObjectProperty(object, propName, val);
				}
				catch(ReflectError e)
				{
					Interpreter.debug("Assignment: " + e.getMessage());
					throw new EvalError("No such property: " + propName);
				}
		else if(type == INDEX)
			try
			{
				Reflect.setIndex(object, index, val);
			}
			catch(Exception e)
			{
				throw new EvalError("Assignment: " + e.getMessage());
			}

		return val;
	}

	public String toString() { return "LHS"; }
}

