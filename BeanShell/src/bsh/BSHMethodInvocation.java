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

class BSHMethodInvocation extends SimpleNode
{
	BSHMethodInvocation (int id) { super(id); }

	public Object eval(
		NameSpace namespace, Interpreter interpreter)  throws EvalError
	{
		Name name = ((BSHAmbiguousName)jjtGetChild(0)).getName(namespace);
		Object[] args = 
			((BSHArguments)jjtGetChild(1)).getArguments(namespace, interpreter);
		try {
			return name.invokeMethod(interpreter, args);
		} catch (ReflectError e) {
			throw new EvalError(
				"Error in method invocation: " + e.getMessage(), this);
		} catch (java.lang.reflect.InvocationTargetException e) {
			throw new TargetError(e.getTargetException(), this);
		} catch ( TargetError te ) {
			// catch and re-throw to get line number right
			throw new TargetError( te, this );
		} catch ( EvalError ee ) {
			// catch and re-throw to get line number right
			throw new EvalError( ee.toString(), this );
		}
	}
}

