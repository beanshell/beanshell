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
 * The Class DelayedEvalBshMethod.
 */
public class DelayedEvalBshMethod extends BshMethod {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;
    /** The return type descriptor. */
    String returnTypeDescriptor;
    /** The return type node. */
    BSHReturnType returnTypeNode;
    /** The param type descriptors. */
    String[] paramTypeDescriptors;
    /** The param types node. */
    BSHFormalParameters paramTypesNode;
    /** The callstack. */
    // used for the delayed evaluation...
    transient CallStack callstack;
    /** The interpreter. */
    transient Interpreter interpreter;

    /**
     * This constructor is used in class generation. It supplies String type
     * descriptors for return and parameter class types and allows delay of
     * the evaluation of those types until they are requested. It does this
     * by holding BSHType nodes, as well as an evaluation callstack, and
     * interpreter which are called when the class types are requested.
     *
     * @param name
     *            the name
     * @param returnTypeDescriptor
     *            the return type descriptor
     * @param returnTypeNode
     *            the return type node
     * @param paramNames
     *            the param names
     * @param paramTypeDescriptors
     *            the param type descriptors
     * @param paramTypesNode
     *            the param types node
     * @param methodBody
     *            the method body
     * @param declaringNameSpace
     *            the declaring name space
     * @param modifiers
     *            the modifiers
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     *
     * Note: technically I think we could get by passing in only the
     * current namespace or perhaps BshClassManager here instead of
     * CallStack and Interpreter. However let's just play it safe in case
     * of future changes - anywhere you eval a node you need these.
     */
    DelayedEvalBshMethod(final String name, final String returnTypeDescriptor,
            final BSHReturnType returnTypeNode, final String[] paramNames,
            final String[] paramTypeDescriptors,
            final BSHFormalParameters paramTypesNode, final BSHBlock methodBody,
            final NameSpace declaringNameSpace, final Modifiers modifiers,
            final CallStack callstack, final Interpreter interpreter) {
        super(name, null/* returnType */, paramNames, null/* paramTypes */,
                methodBody, declaringNameSpace, modifiers);
        this.returnTypeDescriptor = returnTypeDescriptor;
        this.returnTypeNode = returnTypeNode;
        this.paramTypeDescriptors = paramTypeDescriptors;
        this.paramTypesNode = paramTypesNode;
        this.callstack = callstack;
        this.interpreter = interpreter;
    }

    /**
     * Gets the return type descriptor.
     *
     * @return the return type descriptor
     */
    public String getReturnTypeDescriptor() {
        return this.returnTypeDescriptor;
    }

    /** {@inheritDoc} */
    @Override
    public Class getReturnType() {
        if (this.returnTypeNode == null)
            return null;
        // BSHType will cache the type for us
        try {
            return this.returnTypeNode.evalReturnType(this.callstack,
                    this.interpreter);
        } catch (final EvalError e) {
            throw new InterpreterError("can't eval return type: " + e);
        }
    }

    /**
     * Gets the param type descriptors.
     *
     * @return the param type descriptors
     */
    public String[] getParamTypeDescriptors() {
        return this.paramTypeDescriptors;
    }

    /** {@inheritDoc} */
    @Override
    public Class[] getParameterTypes() {
        // BSHFormalParameters will cache the type for us
        try {
            return (Class[]) this.paramTypesNode.eval(this.callstack,
                    this.interpreter);
        } catch (final EvalError e) {
            throw new InterpreterError("can't eval param types: " + e);
        }
    }
}
