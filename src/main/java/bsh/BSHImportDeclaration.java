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
 * The Class BSHImportDeclaration.
 */
class BSHImportDeclaration extends SimpleNode {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;
    /** The import package. */
    public boolean importPackage;
    /** The static import. */
    public boolean staticImport;
    /** The super import. */
    public boolean superImport;

    /**
     * Instantiates a new BSH import declaration.
     *
     * @param id
     *            the id
     */
    BSHImportDeclaration(final int id) {
        super(id);
    }

    /** {@inheritDoc} */
    @Override
    public Object eval(final CallStack callstack, final Interpreter interpreter)
            throws EvalError {
        final NameSpace namespace = callstack.top();
        if (this.superImport)
            try {
                namespace.doSuperImport();
            } catch (final UtilEvalError e) {
                throw e.toEvalError(this, callstack);
            }
        else if (this.staticImport) {
            if (this.importPackage) {
                final Class clas = ((BSHAmbiguousName) this.jjtGetChild(0))
                        .toClass(callstack, interpreter);
                namespace.importStatic(clas);
            } else
                throw new EvalError("static field imports not supported yet",
                        this, callstack);
        } else {
            final String name = ((BSHAmbiguousName) this.jjtGetChild(0)).text;
            if (this.importPackage)
                namespace.importPackage(name);
            else
                namespace.importClass(name);
        }
        return Primitive.VOID;
    }
}
