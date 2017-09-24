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
 * A formal parameter declaration.
 * For loose variable declaration type is null.
 */
class BSHFormalParameter extends SimpleNode {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;
    /** The Constant UNTYPED. */
    public static final Class UNTYPED = null;
    /** The name. */
    public String name;
    /** The type. */
    // unsafe caching of type here
    public Class type;

    /**
     * Instantiates a new BSH formal parameter.
     *
     * @param id
     *            the id
     */
    BSHFormalParameter(final int id) {
        super(id);
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
        if (this.jjtGetNumChildren() > 0)
            return ((BSHType) this.jjtGetChild(0)).getTypeDescriptor(callstack,
                    interpreter, defaultPackage);
        else
            // this will probably not get used
            return "Ljava/lang/Object;"; // Object type
    }

    /**
     * Evaluate the type.
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
        if (this.jjtGetNumChildren() > 0)
            this.type = ((BSHType) this.jjtGetChild(0)).getType(callstack,
                    interpreter);
        else
            this.type = UNTYPED;
        return this.type;
    }
}
