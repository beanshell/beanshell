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
 * The Class BSHAmbiguousName.
 */
class BSHAmbiguousName extends SimpleNode {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;
    /** The text. */
    public String text;

    /**
     * Instantiates a new BSH ambiguous name.
     *
     * @param id
     *            the id
     */
    BSHAmbiguousName(final int id) {
        super(id);
    }

    /**
     * Gets the name.
     *
     * @param namespace
     *            the namespace
     * @return the name
     */
    public Name getName(final NameSpace namespace) {
        return namespace.getNameResolver(this.text);
    }

    /**
     * To object.
     *
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     * @return the object
     * @throws EvalError
     *             the eval error
     */
    public Object toObject(final CallStack callstack,
            final Interpreter interpreter) throws EvalError {
        return this.toObject(callstack, interpreter, false);
    }

    /**
     * To object.
     *
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     * @param forceClass
     *            the force class
     * @return the object
     * @throws EvalError
     *             the eval error
     */
    Object toObject(final CallStack callstack, final Interpreter interpreter,
            final boolean forceClass) throws EvalError {
        try {
            return this.getName(callstack.top()).toObject(callstack,
                    interpreter, forceClass);
        } catch (final UtilEvalError e) {
            // e.printStackTrace();
            throw e.toEvalError(this, callstack);
        }
    }

    /**
     * To class.
     *
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     * @return the class
     * @throws EvalError
     *             the eval error
     */
    public Class toClass(final CallStack callstack,
            final Interpreter interpreter) throws EvalError {
        try {
            return this.getName(callstack.top()).toClass();
        } catch (final ClassNotFoundException e) {
            throw new EvalError(e.getMessage(), this, callstack);
        } catch (final UtilEvalError e2) {
            // ClassPathException is a type of UtilEvalError
            throw e2.toEvalError(this, callstack);
        }
    }

    /**
     * To LHS.
     *
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     * @return the lhs
     * @throws EvalError
     *             the eval error
     */
    public LHS toLHS(final CallStack callstack, final Interpreter interpreter)
            throws EvalError {
        try {
            return this.getName(callstack.top()).toLHS(callstack, interpreter);
        } catch (final UtilEvalError e) {
            throw e.toEvalError(this, callstack);
        }
    }

    /** {@inheritDoc} *
     * The interpretation of an ambiguous name is context sensitive.
     * We disallow a generic eval().
     */
    @Override
    public Object eval(final CallStack callstack, final Interpreter interpreter)
            throws EvalError {
        throw new InterpreterError("Don't know how to eval an ambiguous name!"
                + "  Use toObject() if you want an object.");
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "AmbigousName: " + this.text;
    }
}
