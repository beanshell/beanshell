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

class BSHAmbiguousName extends SimpleNode
{
    public String text;

    BSHAmbiguousName(int id) { super(id); }

    public Name getName(NameSpace namespace)
    {
        return new Name(namespace, text);
    }

    public Object toObject(NameSpace namespace, Interpreter interpreter) throws EvalError
    {
        return getName(namespace).toObject( interpreter );
    }

    public Class toClass(NameSpace namespace) throws EvalError
    {
        return getName(namespace).toClass();
    }

    public LHS toLHS(NameSpace namespace, Interpreter interpreter)
    {
        return getName(namespace).toLHS( interpreter );
    }

	/*
		The interpretation of an ambiguous name is context sensitive.
		We disallow a generic eval( ).
	*/
    public Object eval(NameSpace namespace, Interpreter interpreter) throws EvalError
    {
		throw new InterpreterError( "Don't know how to eval an ambiguous name!  Use toObject() if you want an object." );
    }
}

