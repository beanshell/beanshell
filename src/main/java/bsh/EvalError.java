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
 * EvalError indicates that we cannot continue evaluating the script
 * or the script has thrown an exception.
 *
 * EvalError may be thrown for a script syntax error, an evaluation
 * error such as referring to an undefined variable, an internal error.
 * <p>
 *
 * @see TargetError
 */
public class EvalError extends Exception {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;
    /** The node. */
    SimpleNode node;
    /** The message. */
    // Note: no way to mutate the Throwable message, must maintain our own
    String message;
    /** The callstack. */
    CallStack callstack;

    /**
     * Instantiates a new eval error.
     *
     * @param s
     *            the s
     * @param node
     *            the node
     * @param callstack
     *            the callstack
     */
    public EvalError(final String s, final SimpleNode node,
            final CallStack callstack) {
        this.setMessage(s);
        this.node = node;
        // freeze the callstack for the stack trace.
        if (callstack != null)
            this.callstack = callstack.copy();
    }

    /**
     * Print the error with line number and stack trace.
     *
     * @return the string
     */
    @Override
    public String toString() {
        String trace;
        if (this.node != null)
            trace = " : at Line: " + this.node.getLineNumber() + " : in file: "
                    + this.node.getSourceFile() + " : " + this.node.getText();
        else
            // Users should not normally see this.
            trace = ": <at unknown location>";
        if (this.callstack != null)
            trace = trace + "\n" + this.getScriptStackTrace();
        return this.getMessage() + trace;
    }

    /**
     * Re-throw the error, prepending the specified message.
     *
     * @param msg
     *            the msg
     * @throws EvalError
     *             the eval error
     */
    public void reThrow(final String msg) throws EvalError {
        this.prependMessage(msg);
        throw this;
    }

    /**
     * The error has trace info associated with it.
     * i.e. It has an AST node that can print its location and source text.
     *
     * @return the node
     */
    SimpleNode getNode() {
        return this.node;
    }

    /**
     * Sets the node.
     *
     * @param node
     *            the new node
     */
    void setNode(final SimpleNode node) {
        this.node = node;
    }

    /**
     * Gets the error text.
     *
     * @return the error text
     */
    public String getErrorText() {
        if (this.node != null)
            return this.node.getText();
        else
            return "<unknown error>";
    }

    /**
     * Gets the error line number.
     *
     * @return the error line number
     */
    public int getErrorLineNumber() {
        if (this.node != null)
            return this.node.getLineNumber();
        else
            return -1;
    }

    /**
     * Gets the error source file.
     *
     * @return the error source file
     */
    public String getErrorSourceFile() {
        if (this.node != null)
            return this.node.getSourceFile();
        else
            return "<unknown file>";
    }

    /**
     * Gets the script stack trace.
     *
     * @return the script stack trace
     */
    public String getScriptStackTrace() {
        if (this.callstack == null)
            return "<Unknown>";
        String trace = "";
        final CallStack stack = this.callstack.copy();
        while (stack.depth() > 0) {
            final NameSpace ns = stack.pop();
            final SimpleNode node = ns.getNode();
            if (ns.isMethod) {
                trace = trace + "\nCalled from method: " + ns.getName();
                if (node != null)
                    trace += " : at Line: " + node.getLineNumber()
                            + " : in file: " + node.getSourceFile() + " : "
                            + node.getText();
            }
        }
        return trace;
    }

    /**
     * Gets the message.
     *
     * @return the message
     * @see #toString() for a full display of the information
     */
    @Override
    public String getMessage() {
        return this.message;
    }

    /**
     * Sets the message.
     *
     * @param s
     *            the new message
     */
    public void setMessage(final String s) {
        this.message = s;
    }

    /**
     * Prepend the message if it is non-null.
     *
     * @param s
     *            the s
     */
    protected void prependMessage(final String s) {
        if (s == null)
            return;
        if (this.message == null)
            this.message = s;
        else
            this.message = s + " : " + this.message;
    }
}
