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

import java.util.Vector;

/**
 * A stack of NameSpaces representing the call path.
 * Each method invocation, for example, pushes a new NameSpace onto the stack.
 * The top of the stack is always the current namespace of evaluation.
 * <p>
 *
 * This is used to support the this.caller magic reference and to print
 * script "stack traces" when evaluation errors occur.
 * <p>
 *
 * Note: it would be awefully nice to use the java.util.Stack here.
 * Sigh... have to stay 1.1 compatible.
 * <p>
 *
 * Note: How can this be thread safe, you might ask? Wouldn't a thread
 * executing various beanshell methods be mutating the callstack? Don't we
 * need one CallStack per Thread in the interpreter? The answer is that we do.
 * Any java.lang.Thread enters our script via an external (hard) Java
 * reference via a This type interface, e.g. the Runnable interface
 * implemented by This or an arbitrary interface implemented by XThis.
 * In that case the This invokeMethod() method (called by any interface that
 * it exposes) creates a new CallStack for each external call.
 * <p>
 */
public class CallStack {

    /** The stack. */
    private Vector stack = new Vector(2);

    /**
     * Instantiates a new call stack.
     */
    public CallStack() {}

    /**
     * Instantiates a new call stack.
     *
     * @param namespace
     *            the namespace
     */
    public CallStack(final NameSpace namespace) {
        this.push(namespace);
    }

    /**
     * Clear.
     */
    public void clear() {
        this.stack.removeAllElements();
    }

    /**
     * Push.
     *
     * @param ns
     *            the ns
     */
    public void push(final NameSpace ns) {
        this.stack.insertElementAt(ns, 0);
    }

    /**
     * Top.
     *
     * @return the name space
     */
    public NameSpace top() {
        return this.get(0);
    }

    /**
     * zero based.
     *
     * @param depth
     *            the depth
     * @return the name space
     */
    public NameSpace get(final int depth) {
        if (depth >= this.depth())
            return NameSpace.JAVACODE;
        else
            return (NameSpace) this.stack.elementAt(depth);
    }

    /**
     * This is kind of crazy, but used by the setNameSpace command.
     * zero based.
     *
     * @param depth
     *            the depth
     * @param ns
     *            the ns
     */
    public void set(final int depth, final NameSpace ns) {
        this.stack.setElementAt(ns, depth);
    }

    /**
     * Pop.
     *
     * @return the name space
     */
    public NameSpace pop() {
        if (this.depth() < 1)
            throw new InterpreterError("pop on empty CallStack");
        final NameSpace top = this.top();
        this.stack.removeElementAt(0);
        return top;
    }

    /**
     * Swap in the value as the new top of the stack and return the old
     * value.
     *
     * @param newTop
     *            the new top
     * @return the name space
     */
    public NameSpace swap(final NameSpace newTop) {
        final NameSpace oldTop = (NameSpace) this.stack.elementAt(0);
        this.stack.setElementAt(newTop, 0);
        return oldTop;
    }

    /**
     * Depth.
     *
     * @return the int
     */
    public int depth() {
        return this.stack.size();
    }

    /**
     * To array.
     *
     * @return the name space[]
     */
    public NameSpace[] toArray() {
        final NameSpace[] nsa = new NameSpace[this.depth()];
        this.stack.copyInto(nsa);
        return nsa;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer();
        sb.append("CallStack:\n");
        final NameSpace[] nsa = this.toArray();
        for (final NameSpace element : nsa)
            sb.append("\t" + element + "\n");
        return sb.toString();
    }

    /**
     * Occasionally we need to freeze the callstack for error reporting
     * purposes, etc.
     *
     * @return the call stack
     */
    public CallStack copy() {
        final CallStack cs = new CallStack();
        cs.stack = (Vector) this.stack.clone();
        return cs;
    }
}
