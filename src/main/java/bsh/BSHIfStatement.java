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

class BSHIfStatement extends SimpleNode {
    boolean isClosed;

    BSHIfStatement(int id) { super(id); }

    public Object eval(CallStack callstack, Interpreter interpreter)
            throws EvalError {
        Object ret = null;
        if (evaluateCondition(jjtGetChild(0), callstack, interpreter)) {
            if (!isClosed)
                ret = evalBranch(jjtGetChild(1), callstack, interpreter);
        } else {
            if (jjtGetNumChildren() > 2)
                ret = evalBranch(jjtGetChild(2), callstack, interpreter);
            else if (isClosed)
                ret = evalBranch(jjtGetChild(1), callstack, interpreter);
        }
        if (ret instanceof ReturnControl)
            return ret;
        else
            return Primitive.VOID;
    }

    /** Evaluate an if/else branch. A {} branch uses the same cached
     * block-namespace path (BlockNameSpace.getInstance()) that
     * BSHForStatement/BSHEnhancedForStatement/BSHWhileStatement already
     * use for loop bodies, instead of BSHBlock's own default of always
     * allocating a fresh BlockNameSpace. An if nested in a loop is
     * reached once per matching iteration, so it benefits from cache
     * reuse the same way the loop body itself does; an if reached only
     * once pays no more than the cache's first (always-a-miss) lookup
     * would anyway. */
    private static Object evalBranch(Node branch, CallStack callstack,
            Interpreter interpreter) throws EvalError {
        return branch instanceof BSHBlock
            ? ((BSHBlock) branch).eval(callstack, interpreter, null)
            : branch.eval(callstack, interpreter);
    }

    public static boolean evaluateCondition( Node condExp, CallStack callstack,
            Interpreter interpreter) throws EvalError {
        Object obj = condExp.eval(callstack, interpreter);

        if ( obj == Primitive.VOID )
            throw new EvalException("Condition evaluates to void type",
                condExp, callstack );

        obj = Primitive.castWrapper(Boolean.TYPE, obj);
        return ((Boolean) obj).booleanValue();
    }
}
