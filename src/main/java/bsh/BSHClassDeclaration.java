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
 * The Class BSHClassDeclaration.
 */
class BSHClassDeclaration extends SimpleNode {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;
    /**
     * The class instance initializer method name.
     * A BshMethod by this name is installed by the class delcaration into
     * the static class body namespace.
     * It is called once to initialize the static members of the class space
     * and each time an instances is created to initialize the instance
     * members.
     */
    static final String CLASSINITNAME = "_bshClassInit";
    /** The name. */
    String name;
    /** The modifiers. */
    Modifiers modifiers;
    /** The num interfaces. */
    int numInterfaces;
    /** The extend. */
    boolean extend;
    /** The is interface. */
    boolean isInterface;

    /**
     * Instantiates a new BSH class declaration.
     *
     * @param id
     *            the id
     */
    BSHClassDeclaration(final int id) {
        super(id);
    }

    /** {@inheritDoc} */
    @Override
    public Object eval(final CallStack callstack, final Interpreter interpreter)
            throws EvalError {
        int child = 0;
        // resolve superclass if any
        Class superClass = null;
        if (this.extend) {
            final BSHAmbiguousName superNode = (BSHAmbiguousName) this
                    .jjtGetChild(child++);
            superClass = superNode.toClass(callstack, interpreter);
        }
        // Get interfaces
        final Class[] interfaces = new Class[this.numInterfaces];
        for (int i = 0; i < this.numInterfaces; i++) {
            final BSHAmbiguousName node = (BSHAmbiguousName) this
                    .jjtGetChild(child++);
            interfaces[i] = node.toClass(callstack, interpreter);
            if (!interfaces[i].isInterface())
                throw new EvalError(
                        "Type: " + node.text + " is not an interface!", this,
                        callstack);
        }
        BSHBlock block;
        // Get the class body BSHBlock
        if (child < this.jjtGetNumChildren())
            block = (BSHBlock) this.jjtGetChild(child);
        else
            block = new BSHBlock(ParserTreeConstants.JJTBLOCK);
        try {
            return ClassGenerator.getClassGenerator().generateClass(this.name,
                    this.modifiers, interfaces, superClass, block,
                    this.isInterface, callstack, interpreter);
        } catch (final UtilEvalError e) {
            throw e.toEvalError(this, callstack);
        }
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "ClassDeclaration: " + this.name;
    }
}
