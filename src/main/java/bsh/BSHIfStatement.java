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

import bsh.congo.tree.BaseNode;
import bsh.congo.parser.Node;
import bsh.congo.parser.Token.TokenType;
import bsh.congo.tree.EmptyStatement;
import bsh.congo.tree.Expression;

public class BSHIfStatement extends BaseNode
{
    private boolean closed;

    public boolean isClosed() {
        return closed || firstChildOfType(EmptyStatement.class) != null;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    private Node getCondition() {
        return isLegacyNode() ? getChild(0) : firstChildOfType(Expression.class);
    }

    private Node getBody() {
        if (isLegacyNode()) {
            return isClosed() ? null : getChild(1);
        }
        return firstChildOfType(TokenType.RPAREN).nextSibling();
    }

    private Node getElseBlock() {
        if (isLegacyNode()) {
            if (isClosed()) {
                return getChildCount() > 1 ? getChild(1) : null;
            }
            return getChildCount() > 2 ? getChild(2)  : null;
        }
        Node elseNode = firstChildOfType(TokenType.ELSE);
        return elseNode != null ? elseNode.nextSibling() : null;
    }

    public Object eval(CallStack callstack, Interpreter interpreter)
            throws EvalError {
        Object ret = null;
        if(evaluateCondition(getCondition(), callstack, interpreter)) {
            if (!isClosed())
                ret = getBody().eval(callstack, interpreter);
        } else {
            Node elseBlock = getElseBlock();
            if (elseBlock !=null)
                ret = elseBlock.eval(callstack, interpreter);
        }
        if (ret instanceof ReturnControl)
            return ret;
        else
            return Primitive.VOID;
    }

    public static boolean evaluateCondition( Node condExp, CallStack callstack,
            Interpreter interpreter) throws EvalError {
        Object obj = condExp.eval(callstack, interpreter);

        if ( obj == Primitive.VOID )
            throw new EvalError("Condition evaluates to void type",
                condExp, callstack );

        obj = Primitive.castWrapper(Boolean.TYPE, obj);
        return ((Boolean) obj).booleanValue();
    }
}
