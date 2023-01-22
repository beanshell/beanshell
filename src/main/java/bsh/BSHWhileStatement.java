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
import bsh.congo.parser.BaseNode;
import bsh.congo.parser.Node;
import bsh.congo.parser.BeanshellConstants.TokenType;
import bsh.congo.tree.DoStatement;

/**
 * This class handles both {@code while} statements and {@code do..while} statements.
 */
public class BSHWhileStatement extends BaseNode implements ParserConstants {

    /**
     * Set by Parser, default {@code false}
     */
    private boolean doStatement;
    public String label;

    public boolean isDoStatement() {
        return this instanceof DoStatement || doStatement;
    }

    public void setDoStatement(boolean doStatement) {
        this.doStatement = doStatement;
    }

    public Node getCondition() {
        if (isLegacyNode()) {
            return doStatement ? getChild(1) : getChild(0);
        }
        return firstChildOfType(TokenType.LPAREN).nextSibling();
    }

    public Node getBody() {
        if (isLegacyNode()) {
            return doStatement ? getChild(0) : getChildCount() > 1 ? getChild(1) : null;
        }
        return isDoStatement() ? getChild(1) : firstChildOfType(TokenType.RPAREN).nextSibling();
    }


    public Object eval(CallStack callstack, Interpreter interpreter) throws EvalError {
        final Node condExp = getCondition();
        final Node body = getBody();
        boolean doOnceFlag = isDoStatement();
        while ( !Thread.interrupted()
                && ( doOnceFlag || BSHIfStatement.evaluateCondition(condExp, callstack, interpreter)) ) {
            doOnceFlag = false;
            if (body == null) continue; // no body
            Object ret = body instanceof BSHBlock
                ? ((BSHBlock)body).eval(callstack, interpreter, null)
                : body.eval(callstack, interpreter);
            if (ret instanceof ReturnControl) {
                ReturnControl control = (ReturnControl)ret;

                if (null != control.label)
                    if (null == label || !label.equals(control.label))
                        return ret;

                if (control.kind == RETURN)
                    return ret;
                else if (control.kind == BREAK)
                    break;
                // if CONTINUE we just carry on
            }
        }
        return Primitive.VOID;
    }

    @Override
    public String toString() {
        return super.toString() + ": " + label + ": do=" + doStatement;
    }
}
