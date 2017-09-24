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
 * The Class BSHReturnType.
 */
class BSHReturnType extends SimpleNode {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;
    /** The is void. */
    public boolean isVoid;

    /**
     * Instantiates a new BSH return type.
     *
     * @param id
     *            the id
     */
    BSHReturnType(final int id) {
        super(id);
    }

    /**
     * Gets the type node.
     *
     * @return the type node
     */
    BSHType getTypeNode() {
        return (BSHType) this.jjtGetChild(0);
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
        if (this.isVoid)
            return "V";
        else
            return this.getTypeNode().getTypeDescriptor(callstack, interpreter,
                    defaultPackage);
    }

    /**
     * Eval return type.
     *
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     * @return the class
     * @throws EvalError
     *             the eval error
     */
    public Class evalReturnType(final CallStack callstack,
            final Interpreter interpreter) throws EvalError {
        if (this.isVoid)
            return Void.TYPE;
        else
            return this.getTypeNode().getType(callstack, interpreter);
    }
}
