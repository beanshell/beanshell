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
 * UtilTargetError is an error corresponding to a TargetError but thrown by a
 * utility or other class that does not have the caller context (Node)
 * available to it. See UtilEvalError for an explanation of the difference
 * between UtilEvalError and EvalError.
 * <p>
 *
 * @see UtilEvalError
 */
public class UtilTargetError extends UtilEvalError {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;
    /** The t. */
    public Throwable t;

    /**
     * Instantiates a new util target error.
     *
     * @param message
     *            the message
     * @param t
     *            the t
     */
    public UtilTargetError(final String message, final Throwable t) {
        super(message);
        this.t = t;
    }

    /**
     * Instantiates a new util target error.
     *
     * @param t
     *            the t
     */
    public UtilTargetError(final Throwable t) {
        this(null, t);
    }

    /**
     * Override toEvalError to throw TargetError type.
     *
     * @param msg
     *            the msg
     * @param node
     *            the node
     * @param callstack
     *            the callstack
     * @return the eval error
     */
    @Override
    public EvalError toEvalError(String msg, final SimpleNode node,
            final CallStack callstack) {
        if (msg == null)
            msg = this.getMessage();
        else
            msg = msg + ": " + this.getMessage();
        return new TargetError(msg, this.t, node, callstack, false);
    }
}
