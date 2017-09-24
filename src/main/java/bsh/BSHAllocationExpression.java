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

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;

/**
 * New object, new array, or inner class style allocation with body.
 */
class BSHAllocationExpression extends SimpleNode {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;

    /**
     * Instantiates a new BSH allocation expression.
     *
     * @param id
     *            the id
     */
    BSHAllocationExpression(final int id) {
        super(id);
    }

    /** The inner class count. */
    private static int innerClassCount = 0;

    /** {@inheritDoc} */
    @Override
    public Object eval(final CallStack callstack, final Interpreter interpreter)
            throws EvalError {
        // type is either a class name or a primitive type
        final SimpleNode type = (SimpleNode) this.jjtGetChild(0);
        // args is either constructor arguments or array dimensions
        final SimpleNode args = (SimpleNode) this.jjtGetChild(1);
        if (type instanceof BSHAmbiguousName) {
            final BSHAmbiguousName name = (BSHAmbiguousName) type;
            if (args instanceof BSHArguments)
                return this.objectAllocation(name, (BSHArguments) args,
                        callstack, interpreter);
            else
                return this.objectArrayAllocation(name,
                        (BSHArrayDimensions) args, callstack, interpreter);
        } else
            return this.primitiveArrayAllocation((BSHPrimitiveType) type,
                    (BSHArrayDimensions) args, callstack, interpreter);
    }

    /**
     * Object allocation.
     *
     * @param nameNode
     *            the name node
     * @param argumentsNode
     *            the arguments node
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     * @return the object
     * @throws EvalError
     *             the eval error
     */
    private Object objectAllocation(final BSHAmbiguousName nameNode,
            final BSHArguments argumentsNode, final CallStack callstack,
            final Interpreter interpreter) throws EvalError {
        callstack.top();
        final Object[] args = argumentsNode.getArguments(callstack,
                interpreter);
        if (args == null)
            throw new EvalError("Null args in new.", this, callstack);
        // Look for scripted class object
        Object obj = nameNode.toObject(callstack, interpreter,
                false/* force class */);
        // Try regular class
        obj = nameNode.toObject(callstack, interpreter, true/* force class */);
        Class type = null;
        if (obj instanceof ClassIdentifier)
            type = ((ClassIdentifier) obj).getTargetClass();
        else
            throw new EvalError("Unknown class: " + nameNode.text, this,
                    callstack);
        // Is an inner class style object allocation
        final boolean hasBody = this.jjtGetNumChildren() > 2;
        if (hasBody) {
            final BSHBlock body = (BSHBlock) this.jjtGetChild(2);
            if (type.isInterface())
                return this.constructWithInterfaceBody(type, args, body,
                        callstack, interpreter);
            else
                return this.constructWithClassBody(type, args, body, callstack,
                        interpreter);
        } else
            return this.constructObject(type, args, callstack);
    }

    /**
     * Construct object.
     *
     * @param type
     *            the type
     * @param args
     *            the args
     * @param callstack
     *            the callstack
     * @return the object
     * @throws EvalError
     *             the eval error
     */
    private Object constructObject(final Class type, final Object[] args,
            final CallStack callstack) throws EvalError {
        Object obj;
        try {
            obj = Reflect.constructObject(type, args);
        } catch (final ReflectError e) {
            throw new EvalError("Constructor error: " + e.getMessage(), this,
                    callstack);
        } catch (final InvocationTargetException e) {
            // No need to wrap this debug
            Interpreter.debug("The constructor threw an exception:\n\t"
                    + e.getTargetException());
            throw new TargetError("Object constructor", e.getTargetException(),
                    this, callstack, true);
        }
        final String className = type.getName();
        // Is it an inner class?
        if (className.indexOf("$") == -1)
            return obj;
        // Temporary hack to support inner classes
        // If the obj is a non-static inner class then import the context...
        // This is not a sufficient emulation of inner classes.
        // Replace this later...
        // work through to class 'this'
        final This ths = callstack.top().getThis(null);
        final NameSpace instanceNameSpace = Name
                .getClassNameSpace(ths.getNameSpace());
        // Change the parent (which was the class static) to the class instance
        // We really need to check if we're a static inner class here first...
        // but for some reason Java won't show the static modifier on our
        // fake inner classes... could generate a flag field.
        if (instanceNameSpace != null
                && className.startsWith(instanceNameSpace.getName() + "$"))
            try {
                ClassGenerator.getClassGenerator().setInstanceNameSpaceParent(
                        obj, className, instanceNameSpace);
            } catch (final UtilEvalError e) {
                throw e.toEvalError(this, callstack);
            }
        return obj;
    }

    // TODO
    /**
     * Construct with class body.
     *
     * @param type
     *            the type
     * @param args
     *            the args
     * @param block
     *            the block
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     * @return the object
     * @throws EvalError
     *             the eval error
     *
     * This is totally broken...
     * need to construct a real inner class block here...
     */
    private Object constructWithClassBody(final Class type, final Object[] args,
            final BSHBlock block, final CallStack callstack,
            final Interpreter interpreter) throws EvalError {
        // throw new InterpreterError("constructWithClassBody unimplemented");
        final String name = callstack.top().getName() + "$"
                + (++innerClassCount);
        final Modifiers modifiers = new Modifiers();
        modifiers.addModifier(Modifiers.CLASS, "public");
        Class clas;
        try {
            clas = ClassGenerator.getClassGenerator().generateClass(name,
                    modifiers, null/* interfaces */, type/* superClass */,
                    // block is not innerClassBlock here!!!
                    block, false/* isInterface */, callstack, interpreter);
        } catch (final UtilEvalError e) {
            throw e.toEvalError(this, callstack);
        }
        try {
            return Reflect.constructObject(clas, args);
        } catch (Exception e) {
            if (e instanceof InvocationTargetException)
                e = (Exception) ((InvocationTargetException) e)
                        .getTargetException();
            if (Interpreter.DEBUG)
                e.printStackTrace();
            throw new EvalError("Error constructing inner class instance: " + e,
                    this, callstack);
        }
    }

    /**
     * Construct with interface body.
     *
     * @param type
     *            the type
     * @param args
     *            the args
     * @param body
     *            the body
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     * @return the object
     * @throws EvalError
     *             the eval error
     */
    private Object constructWithInterfaceBody(final Class type,
            final Object[] args, final BSHBlock body, final CallStack callstack,
            final Interpreter interpreter) throws EvalError {
        final NameSpace namespace = callstack.top();
        final NameSpace local = new NameSpace(namespace, "AnonymousBlock");
        callstack.push(local);
        body.eval(callstack, interpreter, true/* overrideNamespace */);
        callstack.pop();
        // statical import fields from the interface so that code inside
        // can refer to the fields directly (e.g. HEIGHT)
        local.importStatic(type);
        try {
            return local.getThis(interpreter).getInterface(type);
        } catch (final UtilEvalError e) {
            throw e.toEvalError(this, callstack);
        }
    }

    /**
     * Object array allocation.
     *
     * @param nameNode
     *            the name node
     * @param dimensionsNode
     *            the dimensions node
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     * @return the object
     * @throws EvalError
     *             the eval error
     */
    private Object objectArrayAllocation(final BSHAmbiguousName nameNode,
            final BSHArrayDimensions dimensionsNode, final CallStack callstack,
            final Interpreter interpreter) throws EvalError {
        final NameSpace namespace = callstack.top();
        final Class type = nameNode.toClass(callstack, interpreter);
        if (type == null)
            throw new EvalError(
                    "Class " + nameNode.getName(namespace) + " not found.",
                    this, callstack);
        return this.arrayAllocation(dimensionsNode, type, callstack,
                interpreter);
    }

    /**
     * Primitive array allocation.
     *
     * @param typeNode
     *            the type node
     * @param dimensionsNode
     *            the dimensions node
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     * @return the object
     * @throws EvalError
     *             the eval error
     */
    private Object primitiveArrayAllocation(final BSHPrimitiveType typeNode,
            final BSHArrayDimensions dimensionsNode, final CallStack callstack,
            final Interpreter interpreter) throws EvalError {
        final Class type = typeNode.getType();
        return this.arrayAllocation(dimensionsNode, type, callstack,
                interpreter);
    }

    /**
     * Array allocation.
     *
     * @param dimensionsNode
     *            the dimensions node
     * @param type
     *            the type
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     * @return the object
     * @throws EvalError
     *             the eval error
     */
    private Object arrayAllocation(final BSHArrayDimensions dimensionsNode,
            final Class type, final CallStack callstack,
            final Interpreter interpreter) throws EvalError {
        /*
         * dimensionsNode can return either a fully intialized array or VOID.
         * when VOID the prescribed array dimensions (defined and undefined)
         * are contained in the node.
         */
        final Object result = dimensionsNode.eval(type, callstack, interpreter);
        if (result != Primitive.VOID)
            return result;
        else
            return this.arrayNewInstance(type, dimensionsNode, callstack);
    }

    /**
     * Create an array of the dimensions specified in dimensionsNode.
     * dimensionsNode may contain a number of "undefined" as well as "defined"
     * dimensions.
     * <p>
     *
     * Background: in Java arrays are implemented in arrays-of-arrays style
     * where, for example, a two dimensional array is a an array of arrays of
     * some base type. Each dimension-type has a Java class type associated
     * with it... so if foo = new int[5][5] then the type of foo is
     * int [][] and the type of foo[0] is int[], etc. Arrays may also be
     * specified with undefined trailing dimensions - meaning that the lower
     * order arrays are not allocated as objects. e.g.
     * if foo = new int [5][]; then foo[0] == null //true; and can later be
     * assigned with the appropriate type, e.g. foo[0] = new int[5];
     * (See Learning Java, O'Reilly & Associates more background).
     * <p>
     *
     * To create an array with undefined trailing dimensions using the
     * reflection API we must use an array type to represent the lower order
     * (undefined) dimensions as the "base" type for the array creation...
     * Java will then create the correct type by adding the dimensions of the
     * base type to specified allocated dimensions yielding an array of
     * dimensionality base + specified with the base dimensons unallocated.
     * To create the "base" array type we simply create a prototype, zero
     * length in each dimension, array and use it to get its class
     * (Actually, I think there is a way we could do it with Class.forName()
     * but I don't trust this). The code is simpler than the explanation...
     * see below.
     *
     * @param type
     *            the type
     * @param dimensionsNode
     *            the dimensions node
     * @param callstack
     *            the callstack
     * @return the object
     * @throws EvalError
     *             the eval error
     */
    private Object arrayNewInstance(Class type,
            final BSHArrayDimensions dimensionsNode, final CallStack callstack)
            throws EvalError {
        if (dimensionsNode.numUndefinedDims > 0) {
            final Object proto = Array.newInstance(type,
                    new int[dimensionsNode.numUndefinedDims]); // zeros
            type = proto.getClass();
        }
        try {
            return Array.newInstance(type, dimensionsNode.definedDimensions);
        } catch (final NegativeArraySizeException e1) {
            throw new TargetError(e1, this, callstack);
        } catch (final Exception e) {
            throw new EvalError(
                    "Can't construct primitive array: " + e.getMessage(), this,
                    callstack);
        }
    }
}
