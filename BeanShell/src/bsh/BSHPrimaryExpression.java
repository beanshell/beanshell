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

class BSHPrimaryExpression extends SimpleNode
{
	BSHPrimaryExpression(int id) { super(id); }

	/*
		Should contain a prefix expression and any number of suffixes.

		We don't eval( ) any nodes until the suffixes have had an
		opportunity to work through them.  This let's the suffixes decide
		how to interpret an ambiguous name (e.g. for the .class operation).
	*/
	public Object eval(NameSpace namespace, Interpreter interpreter)  throws EvalError
	{
		Object obj = jjtGetChild(0);
		int n = jjtGetNumChildren(); 

		for(int i=1; i<n; i++)
			obj = ((BSHPrimarySuffix)jjtGetChild(i)).doSuffix(obj, namespace, interpreter);

		/*
			eval(namespace, interpreter) the node to an object

			Note: This construct is now necessary where the node may be
			an ambiguous name.  If this becomes common we might want to 
			make a static method nodeToObject() or something.
		*/
		if ( obj instanceof SimpleNode )
			if ( obj instanceof BSHAmbiguousName )
				obj = ((BSHAmbiguousName)obj).toObject(namespace, interpreter);
			else
				obj = ((SimpleNode)obj).eval(namespace, interpreter);	

		return obj;
	}
}

