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

import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;

/**
 * TargetError is an EvalError that wraps an exception thrown by the script
 * (or by code called from the script). TargetErrors indicate exceptions
 * which can be caught within the script itself, whereas a general EvalError
 * indicates that the script cannot be evaluated further for some reason.
 *
 * If the exception is caught within the script it is automatically unwrapped,
 * so the code looks like normal Java code. If the TargetError is thrown
 * from the eval() or interpreter.eval() method it may be caught and unwrapped
 * to determine what exception was thrown.
 */
public class TargetError extends EvalError {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;
    /** The in native code. */
    boolean inNativeCode;

    /**
     * Instantiates a new target error.
     *
     * @param msg
     *            the msg
     * @param t
     *            the t
     * @param node
     *            the node
     * @param callstack
     *            the callstack
     * @param inNativeCode
     *            the in native code
     */
    public TargetError(final String msg, final Throwable t,
            final SimpleNode node, final CallStack callstack,
            final boolean inNativeCode) {
        super(msg, node, callstack);
        this.initCause(t);
        this.inNativeCode = inNativeCode;
    }

    /**
     * Instantiates a new target error.
     *
     * @param t
     *            the t
     * @param node
     *            the node
     * @param callstack
     *            the callstack
     */
    public TargetError(final Throwable t, final SimpleNode node,
            final CallStack callstack) {
        this("TargetError", t, node, callstack, false);
    }

    /**
     * Gets the target.
     *
     * @return the target
     */
    public Throwable getTarget() {
        // check for easy mistake
        final Throwable target = this.getCause();
        if (target instanceof InvocationTargetException)
            return ((InvocationTargetException) target).getTargetException();
        else
            return target;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return super.toString() + "\nTarget exception: "
                + this.printTargetError(this.getCause());
    }

    /** {@inheritDoc} */
    @Override
    public void printStackTrace() {
        this.printStackTrace(false, System.err);
    }

    /** {@inheritDoc} */
    @Override
    public void printStackTrace(final PrintStream out) {
        this.printStackTrace(false, out);
    }

    /**
     * Prints the stack trace.
     *
     * @param debug
     *            the debug
     * @param out
     *            the out
     */
    public void printStackTrace(final boolean debug, final PrintStream out) {
        if (debug) {
            super.printStackTrace(out);
            out.println("--- Target Stack Trace ---");
        }
        this.getCause().printStackTrace(out);
    }

    /**
     * Generate a printable string showing the wrapped target exception.
     * If the proxy mechanism is available, allow the extended print to
     * check for UndeclaredThrowableException and print that embedded error.
     *
     * @param t
     *            the t
     * @return the string
     */
    public String printTargetError(final Throwable t) {
        String s = this.getCause().toString();
        if (Capabilities.canGenerateInterfaces())
            s += "\n" + this.xPrintTargetError(t);
        return s;
    }

    /**
     * Extended form of print target error.
     * This indirection is used to print UndeclaredThrowableExceptions
     * which are possible when the proxy mechanism is available.
     *
     * We are shielded from compile problems by using a bsh script.
     * This is acceptable here because we're not in a critical path...
     * Otherwise we'd need yet another dynamically loaded module just for this.
     *
     * @param t
     *            the t
     * @return the string
     */
    public String xPrintTargetError(final Throwable t) {
        final String getTarget = "import java.lang.reflect.UndeclaredThrowableException;"
                + "String result=\"\";"
                + "while (target instanceof UndeclaredThrowableException) {"
                + "   target=target.getUndeclaredThrowable(); "
                + "   result+=\"Nested: \"+target.toString();" + "}"
                + "return result;";
        final Interpreter i = new Interpreter();
        try {
            i.set("target", t);
            return (String) i.eval(getTarget);
        } catch (final EvalError e) {
            throw new InterpreterError("xprintarget: " + e.toString());
        }
    }

    /**
     * Return true if the TargetError was generated from native code.
     * e.g. if the script called into a compiled java class which threw
     * the excpetion. We distinguish so that we can print the stack trace
     * for the native code case... the stack trace would not be useful if
     * the exception was generated by the script. e.g. if the script
     * explicitly threw an exception... (the stack trace would simply point
     * to the bsh internals which generated the exception).
     *
     * @return true, if successful
     */
    public boolean inNativeCode() {
        return this.inNativeCode;
    }
}
