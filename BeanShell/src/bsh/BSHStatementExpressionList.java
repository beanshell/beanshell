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

class BSHStatementExpressionList extends SimpleNode
{
	BSHStatementExpressionList(int id) { super(id); }

	public Object eval(NameSpace namespace, Interpreter interpreter)  throws EvalError
	{
		int n = jjtGetNumChildren();
		for(int i=0; i<n; i++)
		{
			SimpleNode node = ((SimpleNode)jjtGetChild(i));
			node.eval(namespace, interpreter);
		}
		return Primitive.VOID;
	}
}

