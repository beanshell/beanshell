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

class BSHForStatement extends SimpleNode implements InterpreterConstants
{
    public boolean hasForInit;
    public boolean hasExpression;
    public boolean hasForUpdate;

    private SimpleNode forInit;
    private SimpleNode expression;
    private SimpleNode forUpdate;
    private SimpleNode statement;

    private boolean parsed;

    BSHForStatement(int id) { super(id); }

    public Object eval(NameSpace namespace, Interpreter interpreter)  throws EvalError
    {
        int i = 0;
        if(hasForInit)
            forInit = ((SimpleNode)jjtGetChild(i++));
        if(hasExpression)
            expression = ((SimpleNode)jjtGetChild(i++));
        if(hasForUpdate)
            forUpdate = ((SimpleNode)jjtGetChild(i++));
        if(i < jjtGetNumChildren()) // should normally be
            statement = ((SimpleNode)jjtGetChild(i));

		NameSpace forInitNameSpace = null;

        // Do the for init
        if ( hasForInit ) {
			forInitNameSpace = new NameSpace( "ForInitNameSpace" );
            forInit.eval( forInitNameSpace, interpreter );
		} 

		// the forInitNameSpace may be null if there is no forInit
		NameSpace forBodyNameSpace = new ForBodyNameSpace(
			namespace, forInitNameSpace );

        while(true)
        {
            if(hasExpression && !BSHIfStatement.evaluateCondition(
								expression, forBodyNameSpace, interpreter))
                break;

            boolean breakout = false; // switch eats a multi-level break here?
            if(statement != null) // not empty statement
            {
                Object ret = statement.eval(forBodyNameSpace, interpreter);

                if(ret instanceof ReturnControl)
                {
                    switch(((ReturnControl)ret).kind)
                    {
                        case RETURN:
                            return ret;

                        case CONTINUE:
                            break;

                        case BREAK:
                            breakout = true;
                            break;
                    }
                }
            }
            if(breakout)
                break;

            if(hasForUpdate)
                forUpdate.eval(forBodyNameSpace, interpreter);
        }

        return Primitive.VOID;
    }

}
