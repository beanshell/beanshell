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

class BSHFormalParameters extends SimpleNode
{
	int numArgs;
	String[] argNames;
	Class[] argTypes;

	BSHFormalParameters(int id) { super(id); }

	/**
	*/
	public Object eval(NameSpace namespace, Interpreter interpreter)  
		throws EvalError
	{
		numArgs = jjtGetNumChildren();

		if(numArgs > 0)
		{
			argNames = new String[numArgs];
			argTypes = new Class[numArgs];
		}
		for(int i=0; i<numArgs; i++)
		{
			BSHFormalParameter param = (BSHFormalParameter)jjtGetChild(i);
			param.eval(namespace, interpreter);
			argNames[i] = param.name;
			argTypes[i] = param.type;
		}

		return Primitive.VOID;
	}
}

