/*****************************************************************************
 *                                                                           *
 *  This file is part of the BeanShell Java Scripting distribution.          *
 *  Documentation and updates may be found at http://www.beanshell.org/      *
 *                                                                           *
 *  Sun Public License Notice:                                               *
 *                                                                           *
 *  The contents of this file are subject to the Sun Public License Version  *
 *  1.0 (the "License"); you may not use this file except in compliance with *
 *  the License. A copy of the License is available at http://www.sun.com    * 
 *                                                                           *
 *  The Original Code is BeanShell. The Initial Developer of the Original    *
 *  Code is Pat Niemeyer. Portions created by Pat Niemeyer are Copyright     *
 *  (C) 2000.  All Rights Reserved.                                          *
 *                                                                           *
 *  GNU Public License Notice:                                               *
 *                                                                           *
 *  Alternatively, the contents of this file may be used under the terms of  *
 *  the GNU Lesser General Public License (the "LGPL"), in which case the    *
 *  provisions of LGPL are applicable instead of those above. If you wish to *
 *  allow use of your version of this file only under the  terms of the LGPL *
 *  and not to allow others to use your version of this file under the SPL,  *
 *  indicate your decision by deleting the provisions above and replace      *
 *  them with the notice and other provisions required by the LGPL.  If you  *
 *  do not delete the provisions above, a recipient may use your version of  *
 *  this file under either the SPL or the LGPL.                              *
 *                                                                           *
 *  Patrick Niemeyer (pat@pat.net)                                           *
 *  Author of Learning Java, O'Reilly & Associates                           *
 *  http://www.pat.net/~pat/                                                 *
 *                                                                           *
 *****************************************************************************/


package bsh;

/**
	Implementation of the for(;;) statement.
*/
/*
	Note: there is some manipulation of the call stack in here to preserve
	the validity of this.caller even when new subordinate namespaces are 
	made for the for-init and for-body.
*/
class BSHForStatement extends SimpleNode implements ParserConstants
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

    public Object eval(CallStack callstack , Interpreter interpreter)  
		throws EvalError
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
		// save the parent namespace, we're going to do some swapping
		NameSpace enclosingNameSpace= callstack.top();

        // Do the for init
        if ( hasForInit ) {
			forInitNameSpace = 
				new NameSpace( enclosingNameSpace, "ForInitNameSpace" );

			// swap in the forInitSpace so that this.caller stays meaningful
			NameSpace tmp = callstack.swap( forInitNameSpace );
            forInit.eval( callstack, interpreter );
			callstack.swap( tmp );  // put it back
		} 

		// the forInitNameSpace may be null if there is no forInit
		NameSpace forBodyNameSpace = new ForBodyNameSpace(
			enclosingNameSpace, forInitNameSpace );

		Object returnControl = Primitive.VOID;
        while(true)
        {
			/*
            if ( hasExpression && !BSHIfStatement.evaluateCondition(
				expression, forBodyNameSpace, interpreter) )
                break;
			*/
            if ( hasExpression ) {
				NameSpace tmp = callstack.swap( forBodyNameSpace );
				boolean cond = BSHIfStatement.evaluateCondition(
					expression, callstack, interpreter );
				callstack.swap( tmp );  // put it back

				if ( !cond ) 
					break;
			}

            boolean breakout = false; // switch eats a multi-level break here?
            if ( statement != null ) // not empty statement
            {
				// swap in the forBodyNameSpace so this.caller stays meaningful
				NameSpace tmp = callstack.swap( forBodyNameSpace );
                Object ret = statement.eval( callstack, interpreter );
				callstack.swap( tmp );  // put it back

                if (ret instanceof ReturnControl)
                {
                    switch(((ReturnControl)ret).kind)
                    {
                        case RETURN:
							returnControl = ret;
							breakout = true;
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

            if ( hasForUpdate ) {
				// swap in the forBodyNameSpace so this.caller stays meaningful
				NameSpace tmp = callstack.swap( forBodyNameSpace );
                forUpdate.eval( callstack, interpreter );
				callstack.swap( tmp );  // put it back
			}
        }

        return returnControl;
    }

}
