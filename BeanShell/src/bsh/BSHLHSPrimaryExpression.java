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

class BSHLHSPrimaryExpression extends SimpleNode
{
	BSHLHSPrimaryExpression(int id) { super(id); }

	public LHS toLHS(NameSpace namespace, Interpreter interpreter) throws EvalError
	{
		LHS lhs = ((BSHAmbiguousName)jjtGetChild(0)).toLHS(namespace, interpreter);

		int n = jjtGetNumChildren(); 
		for(int i=1; i<n; i++)
			lhs = ((BSHLHSPrimarySuffix)jjtGetChild(i)).doLHSSuffix(
				lhs.getValue(), namespace, interpreter);

		return lhs;
	}
}

