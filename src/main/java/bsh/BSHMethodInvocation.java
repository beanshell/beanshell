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

import java.lang.reflect.InvocationTargetException;

/**
 * The Class BSHMethodInvocation.
 */
class BSHMethodInvocation extends SimpleNode {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;

    /**
     * Instantiates a new BSH method invocation.
     *
     * @param id
     *            the id
     */
    BSHMethodInvocation(final int id) {
        super(id);
    }

    /**
     * Gets the name node.
     *
     * @return the name node
     */
    BSHAmbiguousName getNameNode() {
        return (BSHAmbiguousName) this.jjtGetChild(0);
    }

    /**
     * Gets the args node.
     *
     * @return the args node
     */
    BSHArguments getArgsNode() {
        return (BSHArguments) this.jjtGetChild(1);
    }

    /**
     * Evaluate the method invocation with the specified callstack and
     * interpreter.
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
        final NameSpace namespace = callstack.top();
        final BSHAmbiguousName nameNode = this.getNameNode();
        // Do not evaluate methods this() or super() in class instance space
        // (i.e. inside a constructor)
        if (namespace.getParent() != null && namespace.getParent().isClass
                && (nameNode.text.equals("super")
                        || nameNode.text.equals("this")))
            return Primitive.VOID;
        final Name name = nameNode.getName(namespace);
        final Object[] args = this.getArgsNode().getArguments(callstack,
                interpreter);
        // This try/catch block is replicated is BSHPrimarySuffix... need to
        // factor out common functionality...
        // Move to Reflect?
        try {
            return name.invokeMethod(interpreter, args, callstack, this);
        } catch (final ReflectError e) {
            throw new EvalError("Error in method invocation: " + e.getMessage(),
                    this, callstack);
        } catch (final InvocationTargetException e) {
            final String msg = "Method Invocation " + name;
            final Throwable te = e.getTargetException();
            /*
             * Try to squeltch the native code stack trace if the exception
             * was caused by a reflective call back into the bsh interpreter
             * (e.g. eval() or source()
             */
            boolean isNative = true;
            if (te instanceof EvalError)
                if (te instanceof TargetError)
                    isNative = ((TargetError) te).inNativeCode();
                else
                    isNative = false;
            throw new TargetError(msg, te, this, callstack, isNative);
        } catch (final UtilEvalError e) {
            throw e.toEvalError(this, callstack);
        }
    }
}
