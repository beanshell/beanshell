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
	public Object doSuffix(
		Object obj, CallStack callstack, Interpreter interpreter) 
		throws EvalError
	{
		// Handle ".class" suffix operation
		if ( operation == CLASS )
			if ( obj instanceof BSHType ) {
				NameSpace namespace = callstack.top();
				return ((BSHType)obj).getType( callstack, interpreter );
			} else
				throw new EvalError(
					"Attempt to use .class suffix on non class.", 
					this, callstack );

		// Handle other suffix operations

		/*
			eval( ) the node to an object

			Note: This construct is now necessary where the node may be
			an ambiguous name.  If this becomes common we might want to 
			make a static method nodeToObject() or something.

			The point is that we can't just eval() - we need to direct the
			evaluation to the context sensitive type of result; namely
			object, class, etc.
		*/
		if ( obj instanceof SimpleNode )
			if ( obj instanceof BSHAmbiguousName )
				obj = ((BSHAmbiguousName)obj).toObject(callstack, interpreter);
			else
				obj = ((SimpleNode)obj).eval(callstack, interpreter);	

		try
		{
			switch(operation)
			{
				case INDEX:
					return doIndex(obj, callstack, interpreter );

				case NAME:
					return doName(obj, callstack, interpreter );

				case PROPERTY:
					return doProperty(obj, callstack, interpreter );

				default:
					throw new InterpreterError("LHS suffix");
			} 
		}
		catch(ReflectError e)
		{
			throw new EvalError("reflection error: " + e, this, callstack );
		}
		catch(InvocationTargetException e)
		{
			throw new TargetError( "target exception", e.getTargetException(), 
				this, callstack, true);
		}
	}

	/*
		Field access or a method invocation
		Field access might be .length on an array
	*/
	private Object doName(
		Object obj, CallStack callstack, Interpreter interpreter) 
		throws EvalError, ReflectError, InvocationTargetException
	{
		if(field.equals("length") && obj.getClass().isArray())
			return new Primitive(Array.getLength(obj));
		
		if (jjtGetNumChildren() == 0)
			// field access
			return Reflect.getObjectField(obj, field);
		else
		{
			//System.out.println( "doName method invocation: "+field );
			// method invocation
			Object[] oa = ((BSHArguments)jjtGetChild(0)).getArguments(
				callstack, interpreter);
			//try {
				return Reflect.invokeObjectMethod( 
					obj, field, oa, interpreter, callstack, this );
			//} catch ( UtilEvalError e ) {
				//throw e.toEvalError( this, callstack  );
			//}
		}
	}

	/**
		Just factoring out some common code for the two suffix classes...
		later need to complete factoring of these classes
		(BSHPrimarySuffix, BSHLHSPrimarySuffix)
	*/
	static int getIndexAux(
		Object obj, CallStack callstack, Interpreter interpreter, 
		SimpleNode callerInfo ) 
		throws EvalError
	{
		if ( !obj.getClass().isArray() )
			throw new EvalError("Not an array", callerInfo, callstack );

		int index;
		try {
			Object indexVal = 
				((SimpleNode)callerInfo.jjtGetChild(0)).eval( 
					callstack, interpreter );
			if ( !(indexVal instanceof Primitive) )
				indexVal = NameSpace.getAssignableForm( indexVal, Integer.TYPE);
			index = ((Primitive)indexVal).intValue();
		} catch( UtilEvalError e ) {
			Interpreter.debug("doIndex: "+e);
			throw e.toEvalError( 
				"Arrays may only be indexed by integer types.", 
				callerInfo, callstack );
		}

		return index;
	}

	private Object doIndex(
		Object obj, CallStack callstack, Interpreter interpreter) 
		throws EvalError, ReflectError
	{
		int index = getIndexAux( obj, callstack, interpreter, this );
		try {
			return Reflect.getIndex(obj, index);
		} catch ( UtilEvalError e ) {
			throw e.toEvalError( this, callstack );
		}
	}

	private Object doProperty( 
		Object obj, CallStack callstack, Interpreter interpreter ) 
		throws EvalError
	{
		if(obj == Primitive.VOID)
			throw new EvalError( 
			"Attempt to access property on undefined variable or class name", 
				this, callstack );

		if ( obj instanceof Primitive )
			throw new EvalError("Attempt to access property on a primitive", 
				this, callstack );

		Object value = ((SimpleNode)jjtGetChild(0)).eval(
			callstack, interpreter);
		if ( ! ( value instanceof String ) )
			throw new EvalError(
				"Property expression must be a String or identifier.", 
				this, callstack );

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
			throw new EvalError("No such property: " + value, this, callstack );
		}
	}
}

