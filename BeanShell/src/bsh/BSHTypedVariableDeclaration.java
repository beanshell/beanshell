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

class BSHTypedVariableDeclaration extends SimpleNode
{
    public boolean isFinal;

    BSHTypedVariableDeclaration(int id) { super(id); }

    public Object eval(NameSpace namespace, Interpreter interpreter)  throws EvalError
    {
        Class type = ((BSHType)jjtGetChild(0)).getType(namespace );

        int n = jjtGetNumChildren();
        for(int i = 1; i < n; i++)
        {
            BSHVariableDeclarator dec = (BSHVariableDeclarator)jjtGetChild(i);
            dec.eval(namespace, interpreter);
            if(dec.initValue != null)
                dec.initValue = NameSpace.checkAssignableFrom(dec.initValue, type);
            namespace.setTypedVariable(dec.name, type, dec.initValue, isFinal);
        }

        return Primitive.VOID;
    }
}
