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

class BSHArguments extends SimpleNode
{
    BSHArguments(int id) { super(id); }

    public Object[] getArguments(NameSpace namespace, Interpreter interpreter) throws EvalError
    {
        // evaluate each child
        Object[] args = new Object[jjtGetNumChildren()];
        for(int i = 0; i < args.length; i++)
            args[i] = ((SimpleNode)jjtGetChild(i)).eval(namespace, interpreter);

        return args;
    }
}

