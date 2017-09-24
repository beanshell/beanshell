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
 * UtilEvalError is an error corresponding to an EvalError but thrown by a
 * utility or other class that does not have the caller context (Node)
 * available to it. A normal EvalError must supply the caller Node in order
 * for error messages to be pinned to the correct line and location in the
 * script. UtilEvalError is a checked exception that is *not* a subtype of
 * EvalError, but instead must be caught and rethrown as an EvalError by
 * the a nearest location with context. The method toEvalError(Node)
 * should be used to throw the EvalError, supplying the node.
 * <p>
 *
 * To summarize: Utilities throw UtilEvalError. ASTs throw EvalError.
 * ASTs catch UtilEvalError and rethrow it as EvalError using
 * toEvalError(Node).
 * <p>
 *
 * Philosophically, EvalError and UtilEvalError corrospond to
 * RuntimeException. However they are constrained in this way in order to
 * add the context for error reporting.
 *
 * @see UtilTargetError
 */
public class UtilEvalError extends Exception {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;

    /**
     * Instantiates a new util eval error.
     */
    protected UtilEvalError() {}

    /**
     * Instantiates a new util eval error.
     *
     * @param s
     *            the s
     */
    public UtilEvalError(final String s) {
        super(s);
    }

    /**
     * Re-throw as an eval error, prefixing msg to the message and specifying
     * the node. If a node already exists the addNode is ignored.
     *
     * @param msg
     *            may be null for no additional message.
     * @param node
     *            the node
     * @param callstack
     *            the callstack
     * @return the eval error
     * @see #setNode(bsh.SimpleNode)
     *      <p>
     */
    public EvalError toEvalError(String msg, final SimpleNode node,
            final CallStack callstack) {
        if (Interpreter.DEBUG)
            this.printStackTrace();
        if (msg == null)
            msg = "";
        else
            msg = msg + ": ";
        return new EvalError(msg + this.getMessage(), node, callstack);
    }

    /**
     * To eval error.
     *
     * @param node
     *            the node
     * @param callstack
     *            the callstack
     * @return the eval error
     */
    public EvalError toEvalError(final SimpleNode node,
            final CallStack callstack) {
        return this.toEvalError(null, node, callstack);
    }
}
