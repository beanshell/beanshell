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

class BSHReturnType extends SimpleNode
{
	public boolean isVoid;

	BSHReturnType(int id) { super(id); }

	public Object getReturnType( NameSpace namespace, Interpreter interpreter ) throws EvalError
	{
		if(isVoid)
			return Primitive.VOID;
		else
			return ((BSHType)jjtGetChild(0)).getType( namespace );
	}
}

