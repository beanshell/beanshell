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
 * The Class BSHPrimarySuffix.
 */
class BSHPrimarySuffix extends SimpleNode {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;
    /** The Constant PROPERTY. */
    public static final int CLASS = 0, INDEX = 1, NAME = 2, PROPERTY = 3;
    /** The operation. */
    public int operation;
    /** The index. */
    Object index;
    /** The field. */
    public String field;

    /**
     * Instantiates a new BSH primary suffix.
     *
     * @param id
     *            the id
     */
    BSHPrimarySuffix(final int id) {
        super(id);
    }

    /**
     * Do suffix.
     *
     * @param obj
     *            the obj
     * @param toLHS
     *            the to LHS
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     * @return the object
     * @throws EvalError
     *             the eval error
     *
     * Perform a suffix operation on the given object and return the
     * new value.
     * <p>
     * obj will be a Node when suffix evaluation begins, allowing us to
     * interpret it contextually. (e.g. for .class) Thereafter it will be
     * an value object or LHS (as determined by toLHS).
     * <p>
     * We must handle the toLHS case at each point here.
     * <p>
     */
    public Object doSuffix(Object obj, final boolean toLHS,
            final CallStack callstack, final Interpreter interpreter)
            throws EvalError {
        // Handle ".class" suffix operation
        // Prefix must be a BSHType
        if (this.operation == CLASS)
            if (obj instanceof BSHType) {
                if (toLHS)
                    throw new EvalError("Can't assign .class", this, callstack);
                callstack.top();
                return ((BSHType) obj).getType(callstack, interpreter);
            } else
                throw new EvalError(
                        "Attempt to use .class suffix on non class.", this,
                        callstack);
        /*
         * Evaluate our prefix if it needs evaluating first.
         * If this is the first evaluation our prefix mayb be a Node
         * (directly from the PrimaryPrefix) - eval() it to an object.
         * If it's an LHS, resolve to a value.
         * Note: The ambiguous name construct is now necessary where the node
         * may be an ambiguous name. If this becomes common we might want to
         * make a static method nodeToObject() or something. The point is
         * that we can't just eval() - we need to direct the evaluation to
         * the context sensitive type of result; namely object, class, etc.
         */
        if (obj instanceof SimpleNode)
            if (obj instanceof BSHAmbiguousName)
                obj = ((BSHAmbiguousName) obj).toObject(callstack, interpreter);
            else
                obj = ((SimpleNode) obj).eval(callstack, interpreter);
        else if (obj instanceof LHS)
            try {
                obj = ((LHS) obj).getValue();
            } catch (final UtilEvalError e) {
                throw e.toEvalError(this, callstack);
            }
        try {
            switch (this.operation) {
                case INDEX:
                    return this.doIndex(obj, toLHS, callstack, interpreter);
                case NAME:
                    return this.doName(obj, toLHS, callstack, interpreter);
                case PROPERTY:
                    return this.doProperty(toLHS, obj, callstack, interpreter);
                default:
                    throw new InterpreterError("Unknown suffix type");
            }
        } catch (final ReflectError e) {
            throw new EvalError("reflection error: " + e, this, callstack);
        } catch (final InvocationTargetException e) {
            throw new TargetError("target exception", e.getTargetException(),
                    this, callstack, true);
        }
    }

    /**
     * Do name.
     *
     * @param obj
     *            the obj
     * @param toLHS
     *            the to LHS
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     * @return the object
     * @throws EvalError
     *             the eval error
     * @throws ReflectError
     *             the reflect error
     * @throws InvocationTargetException
     *             the invocation target exception
     *
     * Field access, .length on array, or a method invocation
     * Must handle toLHS case for each.
     */
    private Object doName(final Object obj, final boolean toLHS,
            final CallStack callstack, final Interpreter interpreter)
            throws EvalError, ReflectError, InvocationTargetException {
        try {
            // .length on array
            if (this.field.equals("length") && obj.getClass().isArray())
                if (toLHS)
                    throw new EvalError("Can't assign array length", this,
                            callstack);
                else
                    return new Primitive(Array.getLength(obj));
            // field access
            if (this.jjtGetNumChildren() == 0)
                if (toLHS)
                    return Reflect.getLHSObjectField(obj, this.field);
                else
                    return Reflect.getObjectFieldValue(obj, this.field);
            // Method invocation
            // (LHS or non LHS evaluation can both encounter method calls)
            final Object[] oa = ((BSHArguments) this.jjtGetChild(0))
                    .getArguments(callstack, interpreter);
            // TODO:
            // Note: this try/catch block is copied from BSHMethodInvocation
            // we need to factor out this common functionality and make sure
            // we handle all cases ... (e.g. property style access, etc.)
            // maybe move this to Reflect ?
            try {
                return Reflect.invokeObjectMethod(obj, this.field, oa,
                        interpreter, callstack, this);
            } catch (final ReflectError e) {
                throw new EvalError(
                        "Error in method invocation: " + e.getMessage(), this,
                        callstack);
            } catch (final InvocationTargetException e) {
                final String msg = "Method Invocation " + this.field;
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
            }
        } catch (final UtilEvalError e) {
            throw e.toEvalError(this, callstack);
        }
    }

    /**
     * Gets the index aux.
     *
     * @param obj
     *            the obj
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     * @param callerInfo
     *            the caller info
     * @return the index aux
     * @throws EvalError
     *             the eval error
     */
    static int getIndexAux(final Object obj, final CallStack callstack,
            final Interpreter interpreter, final SimpleNode callerInfo)
            throws EvalError {
        if (!obj.getClass().isArray())
            throw new EvalError("Not an array", callerInfo, callstack);
        int index;
        try {
            Object indexVal = ((SimpleNode) callerInfo.jjtGetChild(0))
                    .eval(callstack, interpreter);
            if (!(indexVal instanceof Primitive))
                indexVal = Types.castObject(indexVal, Integer.TYPE,
                        Types.ASSIGNMENT);
            index = ((Primitive) indexVal).intValue();
        } catch (final UtilEvalError e) {
            Interpreter.debug("doIndex: " + e);
            throw e.toEvalError("Arrays may only be indexed by integer types.",
                    callerInfo, callstack);
        }
        return index;
    }

    /**
     * array index.
     * Must handle toLHS case.
     *
     * @param obj
     *            the obj
     * @param toLHS
     *            the to LHS
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     * @return the object
     * @throws EvalError
     *             the eval error
     * @throws ReflectError
     *             the reflect error
     */
    private Object doIndex(final Object obj, final boolean toLHS,
            final CallStack callstack, final Interpreter interpreter)
            throws EvalError, ReflectError {
        final int index = getIndexAux(obj, callstack, interpreter, this);
        if (toLHS)
            return new LHS(obj, index);
        else
            try {
                return Reflect.getIndex(obj, index);
            } catch (final UtilEvalError e) {
                throw e.toEvalError(this, callstack);
            }
    }

    /**
     * Property access.
     * Must handle toLHS case.
     *
     * @param toLHS
     *            the to LHS
     * @param obj
     *            the obj
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     * @return the object
     * @throws EvalError
     *             the eval error
     */
    private Object doProperty(final boolean toLHS, final Object obj,
            final CallStack callstack, final Interpreter interpreter)
            throws EvalError {
        if (obj == Primitive.VOID)
            throw new EvalError(
                    "Attempt to access property on undefined variable or class name",
                    this, callstack);
        if (obj instanceof Primitive)
            throw new EvalError("Attempt to access property on a primitive",
                    this, callstack);
        final Object value = ((SimpleNode) this.jjtGetChild(0)).eval(callstack,
                interpreter);
        if (!(value instanceof String))
            throw new EvalError(
                    "Property expression must be a String or identifier.", this,
                    callstack);
        if (toLHS)
            return new LHS(obj, (String) value);
        // Property style access to Hashtable or Map
        final CollectionManager cm = CollectionManager.getCollectionManager();
        if (cm.isMap(obj)) {
            Object val = cm.getFromMap(obj, value/* key */);
            return val == null ? val = Primitive.NULL : val;
        }
        try {
            return Reflect.getObjectProperty(obj, (String) value);
        } catch (final UtilEvalError e) {
            throw e.toEvalError("Property: " + value, this, callstack);
        } catch (final ReflectError e) {
            throw new EvalError("No such property: " + value, this, callstack);
        }
    }
}
