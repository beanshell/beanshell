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

class BSHThrowStatement extends SimpleNode
{
	BSHThrowStatement(int id) { super(id); }

	public Object eval(NameSpace namespace, Interpreter interpreter)  throws EvalError
	{
		Object obj = ((SimpleNode)jjtGetChild(0)).eval(namespace, interpreter);

		// actually should loosen this to any Throwable
		if(!(obj instanceof Exception))
			throw new EvalError("Expression in 'throw' must be Exception type", this);

		// wrap the exception in a TargetException to propogate it up
		throw new TargetError((Exception)obj, this);
	}
}

