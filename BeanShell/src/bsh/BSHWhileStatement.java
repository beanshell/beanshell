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
	This class handles both while(){} statements and do{}while() statements.
*/
class BSHWhileStatement extends SimpleNode implements InterpreterConstants
{
	public boolean isDoStatement;

    BSHWhileStatement(int id) { super(id); }

    public Object eval(NameSpace namespace, Interpreter interpreter)  
		throws EvalError
    {
		int numChild = jjtGetNumChildren();

		// Order of body and condition is swapped for do / while
        SimpleNode condExp, body = null;

		if ( isDoStatement ) {
			condExp = (SimpleNode)jjtGetChild(1);
			body =(SimpleNode)jjtGetChild(0);
		} else {
			condExp = (SimpleNode)jjtGetChild(0);
			if ( numChild > 1 )	// has body, else just for side effects
				body =(SimpleNode)jjtGetChild(1);
		}

		boolean doOnceFlag = isDoStatement;
        while( 
			doOnceFlag || 
			BSHIfStatement.evaluateCondition(condExp, namespace, interpreter )
		)
		{
			if ( body == null ) // no body?
				continue;

			Object ret = body.eval(namespace, interpreter);

			boolean breakout = false;
			if(ret instanceof ReturnControl)
			{
				switch(((ReturnControl)ret).kind )
				{
					case RETURN:
						return ret;

					case CONTINUE:
						continue;

					case BREAK:
						breakout = true;
						break;
				}
			}
			if(breakout)
				break;

			doOnceFlag = false;
		}

        return Primitive.VOID;
    }

}
