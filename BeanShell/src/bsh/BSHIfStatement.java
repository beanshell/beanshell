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

class BSHIfStatement extends SimpleNode
{
    BSHIfStatement(int id) { super(id); }

    public Object eval(NameSpace namespace, Interpreter interpreter)  throws EvalError
    {
        Object ret = null;

        if( evaluateCondition( 
			(SimpleNode)jjtGetChild(0), namespace, interpreter ) )
            ret = ((SimpleNode)jjtGetChild(1)).eval(namespace, interpreter);
        else
            if(jjtGetNumChildren() > 2)
                ret = ((SimpleNode)jjtGetChild(2)).eval(namespace, interpreter);

        if(ret instanceof ReturnControl)
            return ret;
        else    
            return Primitive.VOID;
    }

    public static boolean evaluateCondition(
		SimpleNode condExp, NameSpace namespace, Interpreter interpreter) 
		throws EvalError
    {
        Object obj = condExp.eval(namespace, interpreter);
        if(obj instanceof Primitive)
            obj = ((Primitive)obj).getValue();

        if(obj instanceof Boolean)
            return ((Boolean)obj).booleanValue();
        else
            throw new EvalError(
				"Condition must evaluate to a Boolean or boolean.", condExp );
    }
}
