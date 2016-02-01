/*****************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one                *
 * or more contributor license agreements.  See the NOTICE file              *
 * distributed with this work for additional information                     *
 * regarding copyright ownership.  The ASF licenses this file                *
 * to you under the Apache License, Version 2.0 (the                         *
 * "License"); you may not use this file except in compliance                *
 * with the License.  You may obtain a copy of the License at                *
 *                                                                           *
 *     http://www.apache.org/licenses/LICENSE-2.0                            *
 *                                                                           *
 * Unless required by applicable law or agreed to in writing,                *
 * software distributed under the License is distributed on an               *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY                    *
 * KIND, either express or implied.  See the License for the                 *
 * specific language governing permissions and limitations                   *
 * under the License.                                                        *
 *                                                                           *
 *                                                                           *
 * This file is part of the BeanShell Java Scripting distribution.           *
 * Documentation and updates may be found at http://www.beanshell.org/       *
 * Patrick Niemeyer (pat@pat.net)                                            *
 * Author of Learning Java, O'Reilly & Associates                            *
 *                                                                           *
 *****************************************************************************/



package bsh;

/**
	Implementation of the for(;;) statement.
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

		NameSpace enclosingNameSpace= callstack.top();
		BlockNameSpace forNameSpace = new BlockNameSpace( enclosingNameSpace );

		/*
			Note: some interesting things are going on here.

			1) We swap instead of push...  The primary mode of operation 
			acts like we are in the enclosing namespace...  (super must be 
			preserved, etc.)

			2) We do *not* call the body block eval with the namespace 
			override.  Instead we allow it to create a second subordinate 
			BlockNameSpace child of the forNameSpace.  Variable propagation 
			still works through the chain, but the block's child cleans the 
			state between iteration.  
			(which is correct Java behavior... see forscope4.bsh)
		*/

		// put forNameSpace it on the top of the stack
		// Note: it's important that there is only one exit point from this
		// method so that we can swap back the namespace.
		callstack.swap( forNameSpace );

        // Do the for init
        if ( hasForInit ) 
            forInit.eval( callstack, interpreter );

		Object returnControl = Primitive.VOID;
        while(true)
        {
            if ( hasExpression ) 
			{
				boolean cond = BSHIfStatement.evaluateCondition(
					expression, callstack, interpreter );

				if ( !cond ) 
					break;
			}

            boolean breakout = false; // switch eats a multi-level break here?
            if ( statement != null ) // not empty statement
            {
				// do *not* invoke special override for block... (see above)
                Object ret = statement.eval( callstack, interpreter );

                if (ret instanceof ReturnControl)
                {
                    switch(((ReturnControl)ret).kind)
                    {
                        case RETURN:
							returnControl = ret;
							breakout = true;
                            break;

                        case CONTINUE:
                            break;

                        case BREAK:
                            breakout = true;
                            break;
                    }
                }
            }

            if ( breakout )
                break;

            if ( hasForUpdate )
                forUpdate.eval( callstack, interpreter );
        }

		callstack.swap( enclosingNameSpace );  // put it back
        return returnControl;
    }

}
