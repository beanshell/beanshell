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
 * The Class BSHMethodDeclaration.
 */
class BSHMethodDeclaration extends SimpleNode {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;
    /** The name. */
    public String name;
    /** The return type node. */
    // Begin Child node structure evaluated by insureNodesParsed
    BSHReturnType returnTypeNode;
    /** The params node. */
    BSHFormalParameters paramsNode;
    /** The block node. */
    BSHBlock blockNode;
    /** The first throws clause. */
    // index of the first throws clause child node
    int firstThrowsClause;
    /** The modifiers. */
    // End Child node structure evaluated by insureNodesParsed
    public Modifiers modifiers;
    /** The return type. */
    // Unsafe caching of type here.
    Class returnType; // null (none), Void.TYPE, or a Class
    /** The num throws. */
    int numThrows = 0;

    /**
     * Instantiates a new BSH method declaration.
     *
     * @param id
     *            the id
     */
    BSHMethodDeclaration(final int id) {
        super(id);
    }

    /**
     * Set the returnTypeNode, paramsNode, and blockNode based on child
     * node structure. No evaluation is done here.
     */
    synchronized void insureNodesParsed() {
        if (this.paramsNode != null) // there is always a paramsNode
            return;
        final Object firstNode = this.jjtGetChild(0);
        this.firstThrowsClause = 1;
        if (firstNode instanceof BSHReturnType) {
            this.returnTypeNode = (BSHReturnType) firstNode;
            this.paramsNode = (BSHFormalParameters) this.jjtGetChild(1);
            if (this.jjtGetNumChildren() > 2 + this.numThrows)
                this.blockNode = (BSHBlock) this
                        .jjtGetChild(2 + this.numThrows); // skip throws
            ++this.firstThrowsClause;
        } else {
            this.paramsNode = (BSHFormalParameters) this.jjtGetChild(0);
            this.blockNode = (BSHBlock) this.jjtGetChild(1 + this.numThrows); // skip
                                                                              // throws
        }
    }

    /**
     * Evaluate the return type node.
     *
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     * @return the type or null indicating loosely typed return
     * @throws EvalError
     *             the eval error
     */
    Class evalReturnType(final CallStack callstack,
            final Interpreter interpreter) throws EvalError {
        this.insureNodesParsed();
        if (this.returnTypeNode != null)
            return this.returnTypeNode.evalReturnType(callstack, interpreter);
        else
            return null;
    }

    /**
     * Gets the return type descriptor.
     *
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     * @param defaultPackage
     *            the default package
     * @return the return type descriptor
     */
    String getReturnTypeDescriptor(final CallStack callstack,
            final Interpreter interpreter, final String defaultPackage) {
        this.insureNodesParsed();
        if (this.returnTypeNode == null)
            return null;
        else
            return this.returnTypeNode.getTypeDescriptor(callstack, interpreter,
                    defaultPackage);
    }

    /**
     * Gets the return type node.
     *
     * @return the return type node
     */
    BSHReturnType getReturnTypeNode() {
        this.insureNodesParsed();
        return this.returnTypeNode;
    }

    /**
     * Evaluate the declaration of the method. That is, determine the
     * structure of the method and install it into the caller's namespace.
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
        this.returnType = this.evalReturnType(callstack, interpreter);
        this.evalNodes(callstack, interpreter);
        // Install an *instance* of this method in the namespace.
        // See notes in BshMethod
        // This is not good...
        // need a way to update eval without re-installing...
        // so that we can re-eval params, etc. when classloader changes
        // look into this
        final NameSpace namespace = callstack.top();
        final BshMethod bshMethod = new BshMethod(this, namespace,
                this.modifiers);
        try {
            namespace.setMethod(this.name, bshMethod);
        } catch (final UtilEvalError e) {
            throw e.toEvalError(this, callstack);
        }
        return Primitive.VOID;
    }

    /**
     * Eval nodes.
     *
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     * @throws EvalError
     *             the eval error
     */
    private void evalNodes(final CallStack callstack,
            final Interpreter interpreter) throws EvalError {
        this.insureNodesParsed();
        // validate that the throws names are class names
        for (int i = this.firstThrowsClause;
                i < this.numThrows + this.firstThrowsClause;
                i++)
            ((BSHAmbiguousName) this.jjtGetChild(i)).toClass(callstack,
                    interpreter);
        this.paramsNode.eval(callstack, interpreter);
        // if strictJava mode, check for loose parameters and return type
        if (interpreter.getStrictJava()) {
            for (int i = 0; i < this.paramsNode.paramTypes.length; i++)
                if (this.paramsNode.paramTypes[i] == null)
                    // Warning: Null callstack here. Don't think we need
                    // a stack trace to indicate how we sourced the method.
                    throw new EvalError(
                            "(Strict Java Mode) Undeclared argument type, parameter: "
                                    + this.paramsNode.getParamNames()[i]
                                    + " in method: " + this.name,
                            this, null);
            if (this.returnType == null)
                // Warning: Null callstack here. Don't think we need
                // a stack trace to indicate how we sourced the method.
                throw new EvalError(
                        "(Strict Java Mode) Undeclared return type for method: "
                                + this.name,
                        this, null);
        }
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "MethodDeclaration: " + this.name;
    }
}
