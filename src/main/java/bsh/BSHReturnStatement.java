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

import bsh.congo.tree.BreakStatement;
import bsh.congo.tree.ContinueStatement;
import bsh.congo.tree.Expression;
import bsh.congo.tree.ReturnStatement;
import bsh.congo.tree.BaseNode;
import bsh.congo.parser.Node;
import bsh.legacy.ParserConstants;

public class BSHReturnStatement extends BaseNode implements ParserConstants
{
    public int kind;
    public String label;

    public int getKind() {
        if (this instanceof ReturnStatement) return RETURN;
        if (this instanceof BreakStatement) return BREAK;
        if (this instanceof ContinueStatement) return CONTINUE;
        return kind;
    }

    Node getReturnExpression() {
        if (isLegacyNode()) {
            return getChildCount() > 0 ? getChild(0) : null;
        }
        return firstChildOfType(Expression.class);
    }

    public Object eval(CallStack callstack, Interpreter interpreter)
        throws EvalError
    {
        if (null != label)
            return new ReturnControl(kind, label, this);
        Object value;
        Node returnExp = getReturnExpression();
        if (returnExp != null)
            value = returnExp.eval(callstack, interpreter);
        else
            value = Primitive.VOID;
        return new ReturnControl( kind, value, this );
    }

    @Override
    public String toString() {
        return isLegacyNode() ? super.toString() + ": " + tokenImage[kind] + " " + label + ":" : super.toString();
    }
}
