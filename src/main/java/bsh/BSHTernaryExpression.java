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

import bsh.legacy.*;
import bsh.congo.parser.Node;

/**
    This class needs logic to prevent the right hand side of boolean logical
    expressions from being naively evaluated...  e.g. for "foo && bar" bar
    should not be evaluated in the case where foo is true.
*/
public class BSHTernaryExpression extends SimpleNode {

    public BSHTernaryExpression(int id) { super(id); }

    public Object eval( CallStack callstack, Interpreter interpreter)
        throws EvalError
    {
        Node
            cond = getChild(0),
            evalTrue = getChild(1),
            evalFalse = getChild(2);

        if ( BSHIfStatement.evaluateCondition( cond, callstack, interpreter ) )
            return evalTrue.eval( callstack, interpreter );
        else
            return evalFalse.eval( callstack, interpreter );
    }

}
