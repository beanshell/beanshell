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

import java.lang.reflect.Field;
import java.util.Hashtable;

/**
	An LHS is a wrapper for an variable, field, or property.  It ordinarily 
	holds the "left hand side" of an assignment and may be either resolved to 
	a value or assigned a value.
	<p>
	
	There is one special case here termed METHOD_EVAL where the LHS is used
	in an intermediate evaluation of a chain of suffixes and wraps a method
	invocation.  In this case it may only be resolved to a value and cannot be 
	assigned.  (You can't assign a value to the result of a method call e.g.
	"foo() = 5;").
	<p>
*/
class LHS implements ParserConstants, java.io.Serializable
{
	NameSpace nameSpace;

	/**
		Identifiers for the various types of LHS.
	*/
	static final int
		VARIABLE = 0,
		FIELD = 1,
		PROPERTY = 2,
		INDEX = 3,
		METHOD_EVAL = 4;

	int type;

	String varName;
	String propName;
	Field field;
	Object object;
	int index;
	//Method method;

	/**
		Variable LHS constructor.
	*/
	LHS( NameSpace nameSpace, String varName )
	{
		type = VARIABLE;
		this.varName = varName;
		this.nameSpace = nameSpace;
	}

	/**
		Static field LHS Constructor.
		This simply calls Object field constructor with null object.
	*/
	LHS( Field field )
	{
		type = FIELD;
		this.object = null;
		this.field = field;
	}

	/**
		Object field LHS Constructor.
	*/
	LHS( Object object, Field field )
	{
		if(object == null)
			throw new NullPointerException("constructed empty LHS");

		type = FIELD;
		this.object = object;
		this.field = field;
	}

	/**
		Object property LHS Constructor.
	*/
	LHS( Object object, String propName )
	{
		if(object == null)
			throw new NullPointerException("constructed empty LHS");

		type = PROPERTY;
		this.object = object;
		this.propName = propName;
	}

	/**
		Array index LHS Constructor.
	*/
	LHS( Object array, int index )
	{
		if(array == null)
			throw new NullPointerException("constructed empty LHS");

		type = INDEX;
		this.object = array;
		this.index = index;
	}

	/**
		Intermediate object Method evaluation LHS Constructor.
		This type of LHS is used in an intermediate evaluation of a chain of 
		suffixes and wraps a method invocation.  In this case it may only be 
		resolved to a value and cannot be assigned. 
	LHS( Method method, Object object ) {
		this.type = METHOD_EVAL;
		this.method = method;
		this.object = object;
	}
	*/

	/**
		Intermediate static Method evaluation LHS Constructor.
		@see #LHS( Method, Object )
	LHS( Method method ) {
		this( method, null );
	}
	*/

	public Object getValue() throws UtilEvalError
	{
		if ( type == VARIABLE )
			return nameSpace.getVariable(varName);
		else 
		if (type == FIELD)
			try {
				return field.get(object);
			}
			catch(IllegalAccessException e2) {
				throw new UtilEvalError("Can't read field: " + field);
			}
		else 
		if( type == PROPERTY )
			try {
				return Reflect.getObjectProperty(object, propName);
			}
			catch(ReflectError e) {
				Interpreter.debug(e.getMessage());
				throw new UtilEvalError("No such property: " + propName);
			}
		else 
		if( type == INDEX )
			try {
				return Reflect.getIndex(object, index);
			}
			catch(Exception e) {
				throw new UtilEvalError("Array access: " + e);
			}

		throw new InterpreterError("LHS type");
	}

	/**
		Assign a value to the LHS.
		This method throws InterpreterError if the LHS is an intermediate
		evaluation state Method call LHS.
	*/
	public Object assign( Object val, boolean strictJava ) 
		throws UtilEvalError
	{
	/*
		if ( type == METHOD_EVAL )
			throw new InterpreterError(
				"Attempt to assign METHOD_EVAL LHS");
	*/

		if ( type == VARIABLE )
			nameSpace.setVariable( varName, val, strictJava );
		else 
		if ( type == FIELD )
			try {
				if(val instanceof Primitive)
					val = ((Primitive)val).getValue();

				field.set(object, val);
				return val;
			}
			catch( NullPointerException e) {   
    			throw new UtilEvalError(
					"LHS ("+field.getName()+") not a static field.");
			}     
   			catch( IllegalAccessException e2) {   
				throw new UtilEvalError(
					"LHS ("+field.getName()+") can't access field.");
			}     
			catch( IllegalArgumentException e3) {
				throw new UtilEvalError(
					"Argument type mismatch. "
					+ (val == null ? "null" : val.getClass().getName())
					+ " not assignable to field "+field.getName());
			}

		else if ( type == PROPERTY )
			if ( object instanceof Hashtable )
				((Hashtable)object).put(propName, val);
			else
				try {
					Reflect.setObjectProperty(object, propName, val);
				}
				catch(ReflectError e) {
					Interpreter.debug("Assignment: " + e.getMessage());
					throw new UtilEvalError("No such property: " + propName);
				}
		else if ( type == INDEX )
			try {
				Reflect.setIndex(object, index, val);
			} catch ( UtilTargetError e1 ) { // pass along target error
				throw e1;
			} catch ( Exception e ) {
				throw new UtilEvalError("Assignment: " + e.getMessage());
			}

		return val;
	}

	public String toString() { return "LHS"; }
}

