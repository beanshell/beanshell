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

import bsh.congo.parser.BeanshellConstants.TokenType;

class BSHReturnStatement extends SimpleNode implements ParserConstants
{
    public int kind;
    public String label;

    BSHReturnStatement(int id) { super(id); }

    BSHReturnStatement(bsh.congo.tree.ReturnStatement rs) {
        super(rs);
        this.kind = ParserConstants.RETURN;
    }

    BSHReturnStatement(bsh.congo.tree.BreakStatement bs) {
        super(bs);
        this.kind = ParserConstants.BREAK;
        bsh.congo.parser.Token labelToken = bs.firstChildOfType(TokenType.IDENTIFIER);
        if (labelToken != null) {
            this.label = labelToken.getImage();
        }
    }

    BSHReturnStatement(bsh.congo.tree.ContinueStatement cs) {
        super(cs);
        this.kind = ParserConstants.CONTINUE;
        bsh.congo.parser.Token labelToken = cs.firstChildOfType(TokenType.IDENTIFIER);
        if (labelToken != null) {
            this.label = labelToken.getImage();
        }
    }

    public Object eval(CallStack callstack, Interpreter interpreter)
        throws EvalError
    {
        if (null != label)
            return new ReturnControl(kind, label, this);
        Object value;
        if(jjtGetNumChildren() > 0)
            value = jjtGetChild(0).eval(callstack, interpreter);
        else
            value = Primitive.VOID;

        return new ReturnControl( kind, value, this );
    }

    @Override
    public String toString() {
        return super.toString() + ": " + tokenImage[kind] + " " + label + ":";
    }
}
