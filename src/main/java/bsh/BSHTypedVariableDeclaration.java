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
 * The Class BSHTypedVariableDeclaration.
 */
class BSHTypedVariableDeclaration extends SimpleNode {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;
    /** The modifiers. */
    public Modifiers modifiers;

    /**
     * Instantiates a new BSH typed variable declaration.
     *
     * @param id
     *            the id
     */
    BSHTypedVariableDeclaration(final int id) {
        super(id);
    }

    /**
     * Gets the type node.
     *
     * @return the type node
     */
    private BSHType getTypeNode() {
        return (BSHType) this.jjtGetChild(0);
    }

    /**
     * Eval type.
     *
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     * @return the class
     * @throws EvalError
     *             the eval error
     */
    Class evalType(final CallStack callstack, final Interpreter interpreter)
            throws EvalError {
        final BSHType typeNode = this.getTypeNode();
        return typeNode.getType(callstack, interpreter);
    }

    /**
     * Gets the declarators.
     *
     * @return the declarators
     */
    BSHVariableDeclarator[] getDeclarators() {
        final int n = this.jjtGetNumChildren();
        final int start = 1;
        final BSHVariableDeclarator[] bvda = new BSHVariableDeclarator[n
                - start];
        for (int i = start; i < n; i++)
            bvda[i - start] = (BSHVariableDeclarator) this.jjtGetChild(i);
        return bvda;
    }

    /**
     * Evaluate the type and one or more variable declarators, e.g.
     * int a, b=5, c;
     *
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     * @return the object
     * @throws EvalError
     *             the eval error
     */
    @Override
    public Object eval(final CallStack callstack, final Interpreter interpreter)
            throws EvalError {
        try {
            final NameSpace namespace = callstack.top();
            final BSHType typeNode = this.getTypeNode();
            final Class type = typeNode.getType(callstack, interpreter);
            final BSHVariableDeclarator[] bvda = this.getDeclarators();
            for (final BSHVariableDeclarator dec : bvda) {
                // Type node is passed down the chain for array initializers
                // which need it under some circumstances
                final Object value = dec.eval(typeNode, callstack, interpreter);
                try {
                    namespace.setTypedVariable(dec.name, type, value,
                            this.modifiers);
                } catch (final UtilEvalError e) {
                    throw e.toEvalError(this, callstack);
                }
            }
        } catch (final EvalError e) {
            e.reThrow("Typed variable declaration");
        }
        return Primitive.VOID;
    }

    /**
     * Gets the type descriptor.
     *
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     * @param defaultPackage
     *            the default package
     * @return the type descriptor
     */
    public String getTypeDescriptor(final CallStack callstack,
            final Interpreter interpreter, final String defaultPackage) {
        return this.getTypeNode().getTypeDescriptor(callstack, interpreter,
                defaultPackage);
    }
}
