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
 * The Class BSHFormalParameters.
 */
class BSHFormalParameters extends SimpleNode {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;
    /** The param names. */
    private String[] paramNames;
    /**
     * For loose type parameters the paramTypes are null.
     */
    // unsafe caching of types
    Class[] paramTypes;
    /** The num args. */
    int numArgs;
    /** The type descriptors. */
    String[] typeDescriptors;

    /**
     * Instantiates a new BSH formal parameters.
     *
     * @param id
     *            the id
     */
    BSHFormalParameters(final int id) {
        super(id);
    }

    /**
     * Insure parsed.
     */
    void insureParsed() {
        if (this.paramNames != null)
            return;
        this.numArgs = this.jjtGetNumChildren();
        final String[] paramNames = new String[this.numArgs];
        for (int i = 0; i < this.numArgs; i++) {
            final BSHFormalParameter param = (BSHFormalParameter) this
                    .jjtGetChild(i);
            paramNames[i] = param.name;
        }
        this.paramNames = paramNames;
    }

    /**
     * Gets the param names.
     *
     * @return the param names
     */
    public String[] getParamNames() {
        this.insureParsed();
        return this.paramNames;
    }

    /**
     * Gets the type descriptors.
     *
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     * @param defaultPackage
     *            the default package
     * @return the type descriptors
     */
    public String[] getTypeDescriptors(final CallStack callstack,
            final Interpreter interpreter, final String defaultPackage) {
        if (this.typeDescriptors != null)
            return this.typeDescriptors;
        this.insureParsed();
        final String[] typeDesc = new String[this.numArgs];
        for (int i = 0; i < this.numArgs; i++) {
            final BSHFormalParameter param = (BSHFormalParameter) this
                    .jjtGetChild(i);
            typeDesc[i] = param.getTypeDescriptor(callstack, interpreter,
                    defaultPackage);
        }
        this.typeDescriptors = typeDesc;
        return typeDesc;
    }

    /**
     * Evaluate the types.
     * Note that type resolution does not require the interpreter instance.
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
        if (this.paramTypes != null)
            return this.paramTypes;
        this.insureParsed();
        final Class[] paramTypes = new Class[this.numArgs];
        for (int i = 0; i < this.numArgs; i++) {
            final BSHFormalParameter param = (BSHFormalParameter) this
                    .jjtGetChild(i);
            paramTypes[i] = (Class) param.eval(callstack, interpreter);
        }
        this.paramTypes = paramTypes;
        return paramTypes;
    }
}
