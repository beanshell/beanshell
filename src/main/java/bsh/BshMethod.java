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
import java.lang.reflect.Method;

/**
 * This represents an instance of a bsh method declaration in a particular
 * namespace. This is a thin wrapper around the BSHMethodDeclaration
 * with a pointer to the declaring namespace.
 * <p>
 *
 * When a method is located in a subordinate namespace or invoked from an
 * arbitrary namespace it must nonetheless execute with its 'super' as the
 * context in which it was declared.
 * <p/>
 *
 * Note: this method incorrectly caches the method structure. It needs to
 * be cleared when the classloader changes.
 */
public class BshMethod implements java.io.Serializable {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;
    /** The declaring name space. *
     * This is the namespace in which the method is set.
     * It is a back-reference for the node, which needs to execute under this
     * namespace. It is not necessary to declare this transient, because
     * we can only be saved as part of our namespace anyway... (currently).
     */
    NameSpace declaringNameSpace;
    /** The modifiers. */
    // Begin Method components
    Modifiers modifiers;
    /** The name. */
    private final String name;
    /** The creturn type. */
    private final Class creturnType;
    /** The param names. */
    // Arguments
    private final String[] paramNames;
    /** The num args. */
    private int numArgs;
    /** The cparam types. */
    private final Class[] cparamTypes;
    /** The method body. */
    // Scripted method body
    BSHBlock methodBody;
    /** The java method. */
    // Java Method, for a BshObject that delegates to a real Java method
    private Method javaMethod;
    /** The java object. */
    private Object javaObject;

    /**
     * Instantiates a new bsh method.
     *
     * @param method
     *            the method
     * @param declaringNameSpace
     *            the declaring name space
     * @param modifiers
     *            the modifiers
     */
    // End method components
    BshMethod(final BSHMethodDeclaration method,
            final NameSpace declaringNameSpace, final Modifiers modifiers) {
        this(method.name, method.returnType, method.paramsNode.getParamNames(),
                method.paramsNode.paramTypes, method.blockNode,
                declaringNameSpace, modifiers);
    }

    /**
     * Instantiates a new bsh method.
     *
     * @param name
     *            the name
     * @param returnType
     *            the return type
     * @param paramNames
     *            the param names
     * @param paramTypes
     *            the param types
     * @param methodBody
     *            the method body
     * @param declaringNameSpace
     *            the declaring name space
     * @param modifiers
     *            the modifiers
     */
    BshMethod(final String name, final Class returnType,
            final String[] paramNames, final Class[] paramTypes,
            final BSHBlock methodBody, final NameSpace declaringNameSpace,
            final Modifiers modifiers) {
        this.name = name;
        this.creturnType = returnType;
        this.paramNames = paramNames;
        if (paramNames != null)
            this.numArgs = paramNames.length;
        this.cparamTypes = paramTypes;
        this.methodBody = methodBody;
        this.declaringNameSpace = declaringNameSpace;
        this.modifiers = modifiers;
    }

    /**
     * Instantiates a new bsh method.
     *
     * @param method
     *            the method
     * @param object
     *            the object
     *
     * Create a BshMethod that delegates to a real Java method upon invocation.
     * This is used to represent imported object methods.
     */
    BshMethod(final Method method, final Object object) {
        this(method.getName(), method.getReturnType(), null/* paramNames */,
                method.getParameterTypes(), null/* method.block */,
                null/* declaringNameSpace */, null/* modifiers */);
        this.javaMethod = method;
        this.javaObject = object;
    }

    /**
     * Get the argument types of this method.
     * loosely typed (untyped) arguments will be represented by null argument
     * types.
     *
     * @return the parameter types
     *
     * Note: bshmethod needs to re-evaluate arg types here
     * This is broken.
     */
    public Class[] getParameterTypes() {
        return this.cparamTypes;
    }

    /**
     * Gets the parameter names.
     *
     * @return the parameter names
     */
    public String[] getParameterNames() {
        return this.paramNames;
    }

    /**
     * Get the return type of the method.
     *
     * @return Returns null for a loosely typed return value,
     *         Void.TYPE for a void return type, or the Class of the type.
     *
     * Note: bshmethod needs to re-evaluate the method return type here.
     * This is broken.
     */
    public Class getReturnType() {
        return this.creturnType;
    }

    /**
     * Gets the modifiers.
     *
     * @return the modifiers
     */
    public Modifiers getModifiers() {
        return this.modifiers;
    }

    /**
     * Gets the name.
     *
     * @return the name
     */
    public String getName() {
        return this.name;
    }

    /**
     * Invoke the declared method with the specified arguments and interpreter
     * reference. This is the simplest form of invoke() for BshMethod
     * intended to be used in reflective style access to bsh scripts.
     *
     * @param argValues
     *            the arg values
     * @param interpreter
     *            the interpreter
     * @return the object
     * @throws EvalError
     *             the eval error
     */
    public Object invoke(final Object[] argValues,
            final Interpreter interpreter) throws EvalError {
        return this.invoke(argValues, interpreter, null, null, false);
    }

    /**
     * Invoke the bsh method with the specified args, interpreter ref,
     * and callstack.
     * callerInfo is the node representing the method invocation
     * It is used primarily for debugging in order to provide access to the
     * text of the construct that invoked the method through the namespace.
     *
     * @param argValues
     *            the arg values
     * @param interpreter
     *            the interpreter
     * @param callstack
     *            is the callstack. If callstack is null a new one
     *            will be created with the declaring namespace of the method on
     *            top
     *            of the stack (i.e. it will look for purposes of the method
     *            invocation like the method call occurred in the declaring
     *            (enclosing) namespace in which the method is defined).
     * @param callerInfo
     *            is the BeanShell AST node representing the method
     *            invocation. It is used to print the line number and text of
     *            errors in EvalError exceptions. If the node is null here error
     *            messages may not be able to point to the precise location and
     *            text
     *            of the error.
     * @return the object
     * @throws EvalError
     *             the eval error
     */
    public Object invoke(final Object[] argValues,
            final Interpreter interpreter, final CallStack callstack,
            final SimpleNode callerInfo) throws EvalError {
        return this.invoke(argValues, interpreter, callstack, callerInfo,
                false);
    }

    /**
     * Invoke the bsh method with the specified args, interpreter ref,
     * and callstack.
     * callerInfo is the node representing the method invocation
     * It is used primarily for debugging in order to provide access to the
     * text of the construct that invoked the method through the namespace.
     *
     * @param argValues
     *            the arg values
     * @param interpreter
     *            the interpreter
     * @param callstack
     *            is the callstack. If callstack is null a new one
     *            will be created with the declaring namespace of the method on
     *            top
     *            of the stack (i.e. it will look for purposes of the method
     *            invocation like the method call occurred in the declaring
     *            (enclosing) namespace in which the method is defined).
     * @param callerInfo
     *            is the BeanShell AST node representing the method
     *            invocation. It is used to print the line number and text of
     *            errors in EvalError exceptions. If the node is null here error
     *            messages may not be able to point to the precise location and
     *            text
     *            of the error.
     * @param overrideNameSpace
     *            When true the method is executed in the namespace on the top
     *            of the
     *            stack instead of creating its own local namespace. This allows
     *            it
     *            to be used in constructors.
     * @return the object
     * @throws EvalError
     *             the eval error
     */
    Object invoke(final Object[] argValues, final Interpreter interpreter,
            final CallStack callstack, final SimpleNode callerInfo,
            final boolean overrideNameSpace) throws EvalError {
        if (argValues != null)
            for (final Object argValue : argValues)
                if (argValue == null)
                    throw new Error("HERE!");
        if (this.javaMethod != null)
            try {
                return Reflect.invokeMethod(this.javaMethod, this.javaObject,
                        argValues);
            } catch (final ReflectError e) {
                throw new EvalError("Error invoking Java method: " + e,
                        callerInfo, callstack);
            } catch (final InvocationTargetException e2) {
                throw new TargetError(
                        "Exception invoking imported object method.", e2,
                        callerInfo, callstack, true/* isNative */);
            }
        // is this a syncrhonized method?
        if (this.modifiers != null
                && this.modifiers.hasModifier("synchronized")) {
            // The lock is our declaring namespace's This reference
            // (the method's 'super'). Or in the case of a class it's the
            // class instance.
            Object lock;
            if (this.declaringNameSpace.isClass)
                try {
                    lock = this.declaringNameSpace.getClassInstance();
                } catch (final UtilEvalError e) {
                    throw new InterpreterError(
                            "Can't get class instance for synchronized method.");
                }
            else
                lock = this.declaringNameSpace.getThis(interpreter); // ???
            synchronized (lock) {
                return this.invokeImpl(argValues, interpreter, callstack,
                        callerInfo, overrideNameSpace);
            }
        } else
            return this.invokeImpl(argValues, interpreter, callstack,
                    callerInfo, overrideNameSpace);
    }

    /**
     * Invoke impl.
     *
     * @param argValues
     *            the arg values
     * @param interpreter
     *            the interpreter
     * @param callstack
     *            the callstack
     * @param callerInfo
     *            the caller info
     * @param overrideNameSpace
     *            the override name space
     * @return the object
     * @throws EvalError
     *             the eval error
     */
    private Object invokeImpl(Object[] argValues, final Interpreter interpreter,
            CallStack callstack, final SimpleNode callerInfo,
            final boolean overrideNameSpace) throws EvalError {
        final Class returnType = this.getReturnType();
        final Class[] paramTypes = this.getParameterTypes();
        // If null callstack
        if (callstack == null)
            callstack = new CallStack(this.declaringNameSpace);
        if (argValues == null)
            argValues = new Object[] {};
        // Cardinality (number of args) mismatch
        if (argValues.length != this.numArgs)
            /*
             * // look for help string
             * try {
             * // should check for null namespace here
             * String help =
             * (String)declaringNameSpace.get(
             * "bsh.help."+name, interpreter);
             * interpreter.println(help);
             * return Primitive.VOID;
             * } catch (Exception e) {
             * throw eval error
             * }
             */
            throw new EvalError(
                    "Wrong number of arguments for local method: " + this.name,
                    callerInfo, callstack);
        // Make the local namespace for the method invocation
        NameSpace localNameSpace;
        if (overrideNameSpace)
            localNameSpace = callstack.top();
        else {
            localNameSpace = new NameSpace(this.declaringNameSpace, this.name);
            localNameSpace.isMethod = true;
        }
        // should we do this for both cases above?
        localNameSpace.setNode(callerInfo);
        // set the method parameters in the local namespace
        for (int i = 0; i < this.numArgs; i++)
            // Set typed variable
            if (paramTypes[i] != null) {
                try {
                    argValues[i] =
                            // Types.getAssignableForm(argValues[i],
                            // paramTypes[i]);
                            Types.castObject(argValues[i], paramTypes[i],
                                    Types.ASSIGNMENT);
                } catch (final UtilEvalError e) {
                    throw new EvalError("Invalid argument: " + "`"
                            + this.paramNames[i] + "'" + " for method: "
                            + this.name + " : " + e.getMessage(), callerInfo,
                            callstack);
                }
                try {
                    localNameSpace.setTypedVariable(this.paramNames[i],
                            paramTypes[i], argValues[i], null/* modifiers */);
                } catch (final UtilEvalError e2) {
                    throw e2.toEvalError("Typed method parameter assignment",
                            callerInfo, callstack);
                }
            } else if (argValues[i] == Primitive.VOID) // Set untyped variable, getAssignable would catch this for typed param
                throw new EvalError(
                        "Undefined variable or class name, parameter: "
                                + this.paramNames[i] + " to method: "
                                + this.name,
                        callerInfo, callstack);
            else
                try {
                    localNameSpace.setLocalVariable(this.paramNames[i],
                            argValues[i], interpreter.getStrictJava());
                } catch (final UtilEvalError e3) {
                    throw e3.toEvalError(callerInfo, callstack);
                }
        // Push the new namespace on the call stack
        if (!overrideNameSpace)
            callstack.push(localNameSpace);
        // Invoke the block, overriding namespace with localNameSpace
        Object ret = this.methodBody.eval(callstack, interpreter,
                true/* override */);
        // save the callstack including the called method, just for error mess
        final CallStack returnStack = callstack.copy();
        // Get back to caller namespace
        if (!overrideNameSpace)
            callstack.pop();
        ReturnControl retControl = null;
        if (ret instanceof ReturnControl) {
            retControl = (ReturnControl) ret;
            // Method body can only use 'return' statement type return control.
            if (retControl.kind == ParserConstants.RETURN)
                ret = ((ReturnControl) ret).value;
            else
                // retControl.returnPoint is the Node of the return statement
                throw new EvalError("'continue' or 'break' in method body",
                        retControl.returnPoint, returnStack);
            // Check for explicit return of value from void method type.
            // retControl.returnPoint is the Node of the return statement
            if (returnType == Void.TYPE && ret != Primitive.VOID)
                throw new EvalError("Cannot return value from void method",
                        retControl.returnPoint, returnStack);
        }
        if (returnType != null) {
            // If return type void, return void as the value.
            if (returnType == Void.TYPE)
                return Primitive.VOID;
            // return type is a class
            try {
                ret =
                        // Types.getAssignableForm(ret, (Class)returnType);
                        Types.castObject(ret, returnType, Types.ASSIGNMENT);
            } catch (final UtilEvalError e) {
                // Point to return statement point if we had one.
                // (else it was implicit return? What's the case here?)
                SimpleNode node = callerInfo;
                if (retControl != null)
                    node = retControl.returnPoint;
                throw e.toEvalError("Incorrect type returned from method: "
                        + this.name + e.getMessage(), node, callstack);
            }
        }
        return ret;
    }

    /**
     * Checks for modifier.
     *
     * @param name
     *            the name
     * @return true, if successful
     */
    public boolean hasModifier(final String name) {
        return this.modifiers != null && this.modifiers.hasModifier(name);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "Scripted Method: "
                + StringUtil.methodString(this.name, this.getParameterTypes());
    }
}
