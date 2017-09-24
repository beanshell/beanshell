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
 * This class handles both {@code while} statements and {@code do..while}
 * statements.
 */
class BSHWhileStatement extends SimpleNode implements ParserConstants {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;
    /** Set by Parser, default {@code false}. */
    boolean isDoStatement;

    /**
     * Instantiates a new BSH while statement.
     *
     * @param id
     *            the id
     */
    BSHWhileStatement(final int id) {
        super(id);
    }

    /** {@inheritDoc} */
    @Override
    public Object eval(final CallStack callstack, final Interpreter interpreter)
            throws EvalError {
        final int numChild = this.jjtGetNumChildren();
        // Order of body and condition is swapped for do / while
        final SimpleNode condExp;
        final SimpleNode body;
        if (this.isDoStatement) {
            condExp = (SimpleNode) this.jjtGetChild(1);
            body = (SimpleNode) this.jjtGetChild(0);
        } else {
            condExp = (SimpleNode) this.jjtGetChild(0);
            if (numChild > 1)
                body = (SimpleNode) this.jjtGetChild(1);
            else
                body = null;
        }
        boolean doOnceFlag = this.isDoStatement;
        while (doOnceFlag || BSHIfStatement.evaluateCondition(condExp,
                callstack, interpreter)) {
            doOnceFlag = false;
            // no body?
            if (body == null)
                continue;
            final Object ret = body.eval(callstack, interpreter);
            if (ret instanceof ReturnControl)
                switch (((ReturnControl) ret).kind) {
                    case RETURN:
                        return ret;
                    case CONTINUE:
                        break;
                    case BREAK:
                        return Primitive.VOID;
                }
        }
        return Primitive.VOID;
    }
}
