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

/**
	This class needs logic to prevent the right hand side of boolean logical
	expressions from being naively evaluated...  e.g. for "foo && bar" bar 
	should not be evaluated in the case where foo is true.
*/
class BSHTernaryExpression extends SimpleNode {

    BSHTernaryExpression(int id) { super(id); }

    public Object eval( NameSpace namespace, Interpreter interpreter) 
		throws EvalError
    {
        SimpleNode
			cond = (SimpleNode)jjtGetChild(0),
			evalTrue = (SimpleNode)jjtGetChild(1),
			evalFalse = (SimpleNode)jjtGetChild(2);

		if ( BSHIfStatement.evaluateCondition( cond, namespace, interpreter ) )
			return evalTrue.eval( namespace, interpreter );
		else
			return evalFalse.eval( namespace, interpreter );
    }

}
