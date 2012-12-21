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
    This class handles both while(){} statements and do{}while() statements.
*/
class BSHWhileStatement extends SimpleNode implements ParserConstants
{
    public boolean isDoStatement;

    BSHWhileStatement(int id) { super(id); }

    public Object eval( CallStack callstack, Interpreter interpreter)  
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
            if ( numChild > 1 )    // has body, else just for side effects
                body =(SimpleNode)jjtGetChild(1);
        }

        boolean doOnceFlag = isDoStatement;
        while( 
            doOnceFlag || 
            BSHIfStatement.evaluateCondition(condExp, callstack, interpreter )
        )
        {
            if ( body == null ) // no body?
                continue;

            Object ret = body.eval(callstack, interpreter);

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
