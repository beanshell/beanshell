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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Vector;

/**
 * All of the reflection API code lies here. It is in the form of static
 * utilities. Maybe this belongs in LHS.java or a generic object
 * wrapper class.
 *
 * @author Pat Niemeyer
 * @author Daniel Leuck
 *
 *         Note: This class is messy. The method and field resolution need to be
 *         rewritten. Various methods in here catch NoSuchMethod or NoSuchField
 *         exceptions during their searches. These should be rewritten to avoid
 *         having to catch the exceptions. Method lookups are now cached at a
 *         high
 *         level so they are less important, however the logic is messy.
 */
class Reflect {

    /**
     * Invoke method on arbitrary object instance.
     * invocation may be static (through the object instance) or dynamic.
     * Object may be a bsh scripted object (bsh.This type).
     *
     * @param object
     *            the object
     * @param methodName
     *            the method name
     * @param args
     *            the args
     * @param interpreter
     *            the interpreter
     * @param callstack
     *            the callstack
     * @param callerInfo
     *            the caller info
     * @return the result of the method call
     * @throws ReflectError
     *             the reflect error
     * @throws EvalError
     *             the eval error
     * @throws InvocationTargetException
     *             the invocation target exception
     */
    public static Object invokeObjectMethod(final Object object,
            final String methodName, final Object[] args,
            final Interpreter interpreter, final CallStack callstack,
            final SimpleNode callerInfo)
            throws ReflectError, EvalError, InvocationTargetException {
        // Bsh scripted object
        if (object instanceof This && !This.isExposedThisMethod(methodName))
            return ((This) object).invokeMethod(methodName, args, interpreter,
                    callstack, callerInfo, false/* delcaredOnly */
            );
        // Plain Java object, find the java method
        try {
            final BshClassManager bcm = interpreter == null ? null
                    : interpreter.getClassManager();
            final Class clas = object.getClass();
            final Method method = resolveExpectedJavaMethod(bcm, clas, object,
                    methodName, args, false);
            return invokeMethod(method, object, args);
        } catch (final UtilEvalError e) {
            throw e.toEvalError(callerInfo, callstack);
        }
    }

    /**
     * Invoke a method known to be static.
     * No object instance is needed and there is no possibility of the
     * method being a bsh scripted method.
     *
     * @param bcm
     *            the bcm
     * @param clas
     *            the clas
     * @param methodName
     *            the method name
     * @param args
     *            the args
     * @return the object
     * @throws ReflectError
     *             the reflect error
     * @throws UtilEvalError
     *             the util eval error
     * @throws InvocationTargetException
     *             the invocation target exception
     */
    public static Object invokeStaticMethod(final BshClassManager bcm,
            final Class clas, final String methodName, final Object[] args)
            throws ReflectError, UtilEvalError, InvocationTargetException {
        Interpreter.debug("invoke static Method");
        final Method method = resolveExpectedJavaMethod(bcm, clas, null,
                methodName, args, true);
        return invokeMethod(method, null, args);
    }

    /**
     * Invoke the Java method on the specified object, performing needed
     * type mappings on arguments and return values.
     *
     * @param method
     *            the method
     * @param object
     *            the object
     * @param args
     *            may be null
     * @return the object
     * @throws ReflectError
     *             the reflect error
     * @throws InvocationTargetException
     *             the invocation target exception
     */
    static Object invokeMethod(final Method method, final Object object,
            Object[] args) throws ReflectError, InvocationTargetException {
        if (args == null)
            args = new Object[0];
        logInvokeMethod("Invoking method (entry): ", method, args);
        // Map types to assignable forms, need to keep this fast...
        Object[] tmpArgs = new Object[args.length];
        final Class[] types = method.getParameterTypes();
        try {
            for (int i = 0; i < args.length; i++)
                tmpArgs[i] = Types.castObject(args[i]/* rhs */,
                        types[i]/* lhsType */, Types.ASSIGNMENT);
        } catch (final UtilEvalError e) {
            throw new InterpreterError(
                    "illegal argument type in method invocation: " + e);
        }
        // unwrap any primitives
        tmpArgs = Primitive.unwrap(tmpArgs);
        logInvokeMethod("Invoking method (after massaging values): ", method,
                tmpArgs);
        try {
            Object returnValue = method.invoke(object, tmpArgs);
            if (returnValue == null)
                returnValue = Primitive.NULL;
            final Class returnType = method.getReturnType();
            return Primitive.wrap(returnValue, returnType);
        } catch (final IllegalAccessException e) {
            throw new ReflectError("Cannot access method "
                    + StringUtil.methodString(method.getName(),
                            method.getParameterTypes())
                    + " in '" + method.getDeclaringClass() + "' :" + e);
        }
    }

    /**
     * Gets the index.
     *
     * @param array
     *            the array
     * @param index
     *            the index
     * @return the index
     * @throws ReflectError
     *             the reflect error
     * @throws UtilTargetError
     *             the util target error
     */
    public static Object getIndex(final Object array, final int index)
            throws ReflectError, UtilTargetError {
        if (Interpreter.DEBUG)
            Interpreter.debug("getIndex: " + array + ", index=" + index);
        try {
            final Object val = Array.get(array, index);
            return Primitive.wrap(val, array.getClass().getComponentType());
        } catch (final ArrayIndexOutOfBoundsException e1) {
            throw new UtilTargetError(e1);
        } catch (final Exception e) {
            throw new ReflectError("Array access:" + e);
        }
    }

    /**
     * Sets the index.
     *
     * @param array
     *            the array
     * @param index
     *            the index
     * @param val
     *            the val
     * @throws ReflectError
     *             the reflect error
     * @throws UtilTargetError
     *             the util target error
     */
    public static void setIndex(final Object array, final int index, Object val)
            throws ReflectError, UtilTargetError {
        try {
            val = Primitive.unwrap(val);
            Array.set(array, index, val);
        } catch (final ArrayStoreException e2) {
            throw new UtilTargetError(e2);
        } catch (final IllegalArgumentException e1) {
            throw new UtilTargetError(new ArrayStoreException(e1.toString()));
        } catch (final Exception e) {
            throw new ReflectError("Array access:" + e);
        }
    }

    /**
     * Gets the static field value.
     *
     * @param clas
     *            the clas
     * @param fieldName
     *            the field name
     * @return the static field value
     * @throws UtilEvalError
     *             the util eval error
     * @throws ReflectError
     *             the reflect error
     */
    public static Object getStaticFieldValue(final Class clas,
            final String fieldName) throws UtilEvalError, ReflectError {
        return getFieldValue(clas, null, fieldName, true/* onlystatic */);
    }

    /**
     * Check for a field with the given name in a java object or scripted object
     * if the field exists fetch the value, if not check for a property value.
     * If neither is found return Primitive.VOID.
     *
     * @param object
     *            the object
     * @param fieldName
     *            the field name
     * @return the object field value
     * @throws UtilEvalError
     *             the util eval error
     * @throws ReflectError
     *             the reflect error
     */
    public static Object getObjectFieldValue(final Object object,
            final String fieldName) throws UtilEvalError, ReflectError {
        if (object instanceof This) {
            final This t = (This) object;
            return t.namespace.getVariableOrProperty(fieldName, null);
        } else
            try {
                return getFieldValue(object.getClass(), object, fieldName,
                        false/* onlystatic */);
            } catch (final ReflectError e) {
                // no field, try property acces
                if (hasObjectPropertyGetter(object.getClass(), fieldName))
                    return getObjectProperty(object, fieldName);
                else
                    throw e;
            }
    }

    /**
     * Gets the LHS static field.
     *
     * @param clas
     *            the clas
     * @param fieldName
     *            the field name
     * @return the LHS static field
     * @throws UtilEvalError
     *             the util eval error
     * @throws ReflectError
     *             the reflect error
     */
    static LHS getLHSStaticField(final Class clas, final String fieldName)
            throws UtilEvalError, ReflectError {
        final Field f = resolveExpectedJavaField(clas, fieldName,
                true/* onlystatic */);
        return new LHS(f);
    }

    /**
     * Get an LHS reference to an object field.
     *
     * This method also deals with the field style property access.
     * In the field does not exist we check for a property setter.
     *
     * @param object
     *            the object
     * @param fieldName
     *            the field name
     * @return the LHS object field
     * @throws UtilEvalError
     *             the util eval error
     * @throws ReflectError
     *             the reflect error
     */
    static LHS getLHSObjectField(final Object object, final String fieldName)
            throws UtilEvalError, ReflectError {
        if (object instanceof This) {
            // I guess this is when we pass it as an argument?
            // Setting locally
            final boolean recurse = false;
            return new LHS(((This) object).namespace, fieldName, recurse);
        }
        try {
            final Field f = resolveExpectedJavaField(object.getClass(),
                    fieldName, false/* staticOnly */);
            return new LHS(object, f);
        } catch (final ReflectError e) {
            // not a field, try property access
            if (hasObjectPropertySetter(object.getClass(), fieldName))
                return new LHS(object, fieldName);
            else
                throw e;
        }
    }

    /**
     * Gets the field value.
     *
     * @param clas
     *            the clas
     * @param object
     *            the object
     * @param fieldName
     *            the field name
     * @param staticOnly
     *            the static only
     * @return the field value
     * @throws UtilEvalError
     *             the util eval error
     * @throws ReflectError
     *             the reflect error
     */
    private static Object getFieldValue(final Class clas, final Object object,
            final String fieldName, final boolean staticOnly)
            throws UtilEvalError, ReflectError {
        try {
            final Field f = resolveExpectedJavaField(clas, fieldName,
                    staticOnly);
            final Object value = f.get(object);
            final Class returnType = f.getType();
            return Primitive.wrap(value, returnType);
        } catch (final NullPointerException e) { // shouldn't happen
            throw new ReflectError(
                    "???" + fieldName + " is not a static field.");
        } catch (final IllegalAccessException e) {
            throw new ReflectError("Can't access field: " + fieldName);
        }
    }

    /**
     * Resolve java field.
     *
     * @param clas
     *            the clas
     * @param fieldName
     *            the field name
     * @param staticOnly
     *            the static only
     * @return the field
     *         the field or null if not found
     * @throws UtilEvalError
     *             the util eval error
     *
     *             Note: this method and resolveExpectedJavaField should be
     *             rewritten
     *             to invert this logic so that no exceptions need to be caught
     *             unecessarily. This is just a temporary impl.
     */
    protected static Field resolveJavaField(final Class clas,
            final String fieldName, final boolean staticOnly)
            throws UtilEvalError {
        try {
            return resolveExpectedJavaField(clas, fieldName, staticOnly);
        } catch (final ReflectError e) {
            return null;
        }
    }

    /**
     * Resolve expected java field.
     *
     * @param clas
     *            the clas
     * @param fieldName
     *            the field name
     * @param staticOnly
     *            the static only
     * @return the field
     * @throws UtilEvalError
     *             the util eval error
     * @throws ReflectError
     *             if the field is not found.
     *
     * Note: this should really just throw NoSuchFieldException... need
     * to change related signatures and code.
     */
    protected static Field resolveExpectedJavaField(final Class clas,
            final String fieldName, final boolean staticOnly)
            throws UtilEvalError, ReflectError {
        Field field;
        try {
            if (Capabilities.haveAccessibility())
                field = findAccessibleField(clas, fieldName);
            else
                // Class getField() finds only public (and in interfaces, etc.)
                field = clas.getField(fieldName);
        } catch (final NoSuchFieldException e) {
            throw new ReflectError("No such field: " + fieldName);
        } catch (final SecurityException e) {
            throw new UtilTargetError(
                    "Security Exception while searching fields of: " + clas, e);
        }
        if (staticOnly && !Modifier.isStatic(field.getModifiers()))
            throw new UtilEvalError("Can't reach instance field: " + fieldName
                    + " from static context: " + clas.getName());
        return field;
    }

    /**
     * Used when accessibility capability is available to locate an occurrence
     * of the field in the most derived class or superclass and set its
     * accessibility flag.
     * Note that this method is not needed in the simple non accessible
     * case because we don't have to hunt for fields.
     * Note that classes may declare overlapping private fields, so the
     * distinction about the most derived is important. Java doesn't normally
     * allow this kind of access (super won't show private variables) so
     * there is no real syntax for specifying which class scope to use...
     *
     * @param clas
     *            the clas
     * @param fieldName
     *            the field name
     * @return the Field or throws NoSuchFieldException
     * @throws UtilEvalError
     *             the util eval error
     * @throws NoSuchFieldException
     *             if the field is not found
     *
     * This method should be rewritten to use getFields() and avoid catching
     * exceptions during the search.
     */
    private static Field findAccessibleField(Class clas, final String fieldName)
            throws UtilEvalError, NoSuchFieldException {
        Field field;
        // Quick check catches public fields include those in interfaces
        try {
            field = clas.getField(fieldName);
            ReflectManager.RMSetAccessible(field);
            return field;
        } catch (final NoSuchFieldException e) {}
        // Now, on with the hunt...
        while (clas != null) {
            try {
                field = clas.getDeclaredField(fieldName);
                ReflectManager.RMSetAccessible(field);
                return field;
                // Not found, fall through to next class
            } catch (final NoSuchFieldException e) {}
            clas = clas.getSuperclass();
        }
        throw new NoSuchFieldException(fieldName);
    }

    /**
     * This method wraps resolveJavaMethod() and expects a non-null method
     * result. If the method is not found it throws a descriptive ReflectError.
     *
     * @param bcm
     *            the bcm
     * @param clas
     *            the clas
     * @param object
     *            the object
     * @param name
     *            the name
     * @param args
     *            the args
     * @param staticOnly
     *            the static only
     * @return the method
     * @throws ReflectError
     *             the reflect error
     * @throws UtilEvalError
     *             the util eval error
     */
    protected static Method resolveExpectedJavaMethod(final BshClassManager bcm,
            final Class clas, final Object object, final String name,
            final Object[] args, final boolean staticOnly)
            throws ReflectError, UtilEvalError {
        if (object == Primitive.NULL)
            throw new UtilTargetError(new NullPointerException(
                    "Attempt to invoke method " + name + " on null value"));
        final Class[] types = Types.getTypes(args);
        final Method method = resolveJavaMethod(bcm, clas, name, types,
                staticOnly);
        if (method == null)
            throw new ReflectError((staticOnly ? "Static method " : "Method ")
                    + StringUtil.methodString(name, types)
                    + " not found in class'" + clas.getName() + "'");
        return method;
    }

    /**
     * The full blown resolver method. All other method invocation methods
     * delegate to this. The method may be static or dynamic unless
     * staticOnly is set (in which case object may be null).
     * If staticOnly is set then only static methods will be located.
     * <p/>
     *
     * This method performs caching (caches discovered methods through the
     * class manager and utilizes cached methods.)
     * <p/>
     *
     * This method determines whether to attempt to use non-public methods
     * based on Capabilities.haveAccessibility() and will set the accessibilty
     * flag on the method as necessary.
     * <p/>
     *
     * If, when directed to find a static method, this method locates a more
     * specific matching instance method it will throw a descriptive exception
     * analogous to the error that the Java compiler would produce.
     * Note: as of 2.0.x this is a problem because there is no way to work
     * around this with a cast.
     * <p/>
     *
     * @param bcm
     *            the bcm
     * @param clas
     *            the clas
     * @param name
     *            the name
     * @param types
     *            the types
     * @param staticOnly
     *            The method located must be static, the object param may be
     *            null.
     * @return the method or null if no matching method was found.
     * @throws UtilEvalError
     *             the util eval error
     */
    protected static Method resolveJavaMethod(final BshClassManager bcm,
            final Class clas, final String name, final Class[] types,
            final boolean staticOnly) throws UtilEvalError {
        if (clas == null)
            throw new InterpreterError("null class");
        // Lookup previously cached method
        Method method = null;
        if (bcm == null)
            Interpreter.debug("resolveJavaMethod UNOPTIMIZED lookup");
        else
            method = bcm.getResolvedMethod(clas, name, types, staticOnly);
        if (method == null) {
            final boolean publicOnly = !Capabilities.haveAccessibility();
            // Searching for the method may, itself be a priviledged action
            try {
                method = findOverloadedMethod(clas, name, types, publicOnly);
            } catch (final SecurityException e) {
                throw new UtilTargetError(
                        "Security Exception while searching methods of: "
                                + clas,
                        e);
            }
            checkFoundStaticMethod(method, staticOnly, clas);
            // This is the first time we've seen this method, set accessibility
            // Note: even if it's a public method, we may have found it in a
            // non-public class
            if (method != null && !publicOnly)
                try {
                    ReflectManager.RMSetAccessible(method);
                } catch (final UtilEvalError e) { /* ignore */ }
            // If succeeded cache the resolved method.
            if (method != null && bcm != null)
                bcm.cacheResolvedMethod(clas, types, method);
        }
        return method;
    }

    /**
     * Get the candidate methods by searching the class and interface graph
     * of baseClass and resolve the most specific.
     *
     * @param baseClass
     *            the base class
     * @param methodName
     *            the method name
     * @param types
     *            the types
     * @param publicOnly
     *            the public only
     * @return the method or null for not found
     */
    private static Method findOverloadedMethod(final Class baseClass,
            final String methodName, final Class[] types,
            final boolean publicOnly) {
        if (Interpreter.DEBUG)
            Interpreter.debug("Searching for method: "
                    + StringUtil.methodString(methodName, types) + " in '"
                    + baseClass.getName() + "'");
        final Method[] methods = getCandidateMethods(baseClass, methodName,
                types.length, publicOnly);
        if (Interpreter.DEBUG)
            Interpreter
                    .debug("Looking for most specific method: " + methodName);
        final Method method = findMostSpecificMethod(types, methods);
        return method;
    }

    /**
     * Climb the class and interface inheritance graph of the type and collect
     * all methods matching the specified name and criterion. If publicOnly
     * is true then only public methods in *public* classes or interfaces will
     * be returned. In the normal (non-accessible) case this addresses the
     * problem that arises when a package private class or private inner class
     * implements a public interface or derives from a public type.
     * <p/>
     *
     * This method primarily just delegates to gatherMethodsRecursive()
     *
     * @param baseClass
     *            the base class
     * @param methodName
     *            the method name
     * @param numArgs
     *            the num args
     * @param publicOnly
     *            the public only
     * @return the candidate methods
     * @see #gatherMethodsRecursive(
     *      Class, String, int, boolean, java.util.Vector)
     */
    static Method[] getCandidateMethods(final Class baseClass,
            final String methodName, final int numArgs,
            final boolean publicOnly) {
        final Vector candidates = gatherMethodsRecursive(baseClass, methodName,
                numArgs, publicOnly, null/* candidates */);
        // return the methods in an array
        final Method[] ma = new Method[candidates.size()];
        candidates.copyInto(ma);
        return ma;
    }

    /**
     * Accumulate all methods, optionally including non-public methods,
     * class and interface, in the inheritance tree of baseClass.
     *
     * This method is analogous to Class getMethods() which returns all public
     * methods in the inheritance tree.
     *
     * In the normal (non-accessible) case this also addresses the problem
     * that arises when a package private class or private inner class
     * implements a public interface or derives from a public type. In other
     * words, sometimes we'll find public methods that we can't use directly
     * and we have to find the same public method in a parent class or
     * interface.
     *
     * @param baseClass
     *            the base class
     * @param methodName
     *            the method name
     * @param numArgs
     *            the num args
     * @param publicOnly
     *            the public only
     * @param candidates
     *            the candidates
     * @return the candidate methods vector
     */
    private static Vector gatherMethodsRecursive(final Class baseClass,
            final String methodName, final int numArgs,
            final boolean publicOnly, Vector candidates) {
        if (candidates == null)
            candidates = new Vector();
        // Add methods of the current class to the vector.
        // In public case be careful to only add methods from a public class
        // and to use getMethods() instead of getDeclaredMethods()
        // (This addresses secure environments)
        if (publicOnly) {
            if (isPublic(baseClass))
                addCandidates(baseClass.getMethods(), methodName, numArgs,
                        publicOnly, candidates);
        } else
            addCandidates(baseClass.getDeclaredMethods(), methodName, numArgs,
                    publicOnly, candidates);
        // Does the class or interface implement interfaces?
        final Class[] intfs = baseClass.getInterfaces();
        for (final Class intf : intfs)
            gatherMethodsRecursive(intf, methodName, numArgs, publicOnly,
                    candidates);
        // Do we have a superclass? (interfaces don't, etc.)
        final Class superclass = baseClass.getSuperclass();
        if (superclass != null)
            gatherMethodsRecursive(superclass, methodName, numArgs, publicOnly,
                    candidates);
        return candidates;
    }

    /**
     * Adds the candidates.
     *
     * @param methods
     *            the methods
     * @param methodName
     *            the method name
     * @param numArgs
     *            the num args
     * @param publicOnly
     *            the public only
     * @param candidates
     *            the candidates
     * @return the vector
     */
    private static Vector addCandidates(final Method[] methods,
            final String methodName, final int numArgs,
            final boolean publicOnly, final Vector candidates) {
        for (final Method m : methods)
            if (m.getName().equals(methodName)
                    && m.getParameterTypes().length == numArgs
                    && (!publicOnly || isPublic(m)))
                candidates.add(m);
        return candidates;
    }

    /**
     * Primary object constructor
     * This method is simpler than those that must resolve general method
     * invocation because constructors are not inherited.
     * <p/>
     * This method determines whether to attempt to use non-public constructors
     * based on Capabilities.haveAccessibility() and will set the accessibilty
     * flag on the method as necessary.
     * <p/>
     *
     * @param clas
     *            the clas
     * @param args
     *            the args
     * @return the object
     * @throws ReflectError
     *             the reflect error
     * @throws InvocationTargetException
     *             the invocation target exception
     */
    static Object constructObject(final Class clas, Object[] args)
            throws ReflectError, InvocationTargetException {
        if (clas.isInterface())
            throw new ReflectError(
                    "Can't create instance of an interface: " + clas);
        Object obj = null;
        final Class[] types = Types.getTypes(args);
        Constructor con = null;
        // Find the constructor.
        // (there are no inherited constructors to worry about)
        final Constructor[] constructors = Capabilities.haveAccessibility()
                ? clas.getDeclaredConstructors()
                : clas.getConstructors();
        if (Interpreter.DEBUG)
            Interpreter.debug("Looking for most specific constructor: " + clas);
        con = findMostSpecificConstructor(types, constructors);
        if (con == null)
            throw cantFindConstructor(clas, types);
        if (!isPublic(con))
            try {
                ReflectManager.RMSetAccessible(con);
            } catch (final UtilEvalError e) { /* ignore */ }
        args = Primitive.unwrap(args);
        try {
            obj = con.newInstance(args);
        } catch (final InstantiationException e) {
            throw new ReflectError("The class " + clas + " is abstract ");
        } catch (final IllegalAccessException e) {
            throw new ReflectError(
                    "We don't have permission to create an instance."
                            + "Use setAccessibility(true) to enable access.");
        } catch (final IllegalArgumentException e) {
            throw new ReflectError("The number of arguments was wrong");
        }
        if (obj == null)
            throw new ReflectError("Couldn't construct the object");
        return obj;
    }

    /**
     * Find most specific constructor.
     *
     * @param idealMatch
     *            the ideal match
     * @param constructors
     *            the constructors
     * @return the constructor
     *
     * This method should parallel findMostSpecificMethod()
     * The only reason it can't be combined is that Method and Constructor
     * don't have a common interface for their signatures
     */
    static Constructor findMostSpecificConstructor(final Class[] idealMatch,
            final Constructor[] constructors) {
        final int match = findMostSpecificConstructorIndex(idealMatch,
                constructors);
        return match == -1 ? null : constructors[match];
    }

    /**
     * Find most specific constructor index.
     *
     * @param idealMatch
     *            the ideal match
     * @param constructors
     *            the constructors
     * @return the int
     */
    static int findMostSpecificConstructorIndex(final Class[] idealMatch,
            final Constructor[] constructors) {
        final Class[][] candidates = new Class[constructors.length][];
        for (int i = 0; i < candidates.length; i++)
            candidates[i] = constructors[i].getParameterTypes();
        return findMostSpecificSignature(idealMatch, candidates);
    }

    /**
     * Find the best match for signature idealMatch.
     * It is assumed that the methods array holds only valid candidates
     * (e.g. method name and number of args already matched).
     * This method currently does not take into account Java 5 covariant
     * return types... which I think will require that we find the most
     * derived return type of otherwise identical best matches.
     *
     * @param idealMatch
     *            the ideal match
     * @param methods
     *            the set of candidate method which differ only in the
     *            types of their arguments.
     * @return the method
     * @see #findMostSpecificSignature(Class[], Class[][])
     */
    static Method findMostSpecificMethod(final Class[] idealMatch,
            final Method[] methods) {
        // copy signatures into array for findMostSpecificMethod()
        final Class[][] candidateSigs = new Class[methods.length][];
        for (int i = 0; i < methods.length; i++)
            candidateSigs[i] = methods[i].getParameterTypes();
        final int match = findMostSpecificSignature(idealMatch, candidateSigs);
        return match == -1 ? null : methods[match];
    }

    /**
     * Implement JLS 15.11.2
     * Return the index of the most specific arguments match or -1 if no
     * match is found.
     * This method is used by both methods and constructors (which
     * unfortunately don't share a common interface for signature info).
     *
     * @param idealMatch
     *            the ideal match
     * @param candidates
     *            the candidates
     * @return the index of the most specific candidate
     *
     * Note: Two methods which are equally specific should not be allowed by
     * the Java compiler. In this case BeanShell currently chooses the first
     * one it finds. We could add a test for this case here (I believe) by
     * adding another isSignatureAssignable() in the other direction between
     * the target and "best" match. If the assignment works both ways then
     * neither is more specific and they are ambiguous. I'll leave this test
     * out for now because I'm not sure how much another test would impact
     * performance. Method selection is now cached at a high level, so a few
     * friendly extraneous tests shouldn't be a problem.
     */
    static int findMostSpecificSignature(final Class[] idealMatch,
            final Class[][] candidates) {
        for (int round = Types.FIRST_ROUND_ASSIGNABLE;
                round <= Types.LAST_ROUND_ASSIGNABLE;
                round++) {
            Class[] bestMatch = null;
            int bestMatchIndex = -1;
            for (int i = 0; i < candidates.length; i++) {
                final Class[] targetMatch = candidates[i];
                // If idealMatch fits targetMatch and this is the first match
                // or targetMatch is more specific than the best match, make it
                // the new best match.
                if (Types.isSignatureAssignable(idealMatch, targetMatch, round)
                        && (bestMatch == null
                                || Types.isSignatureAssignable(targetMatch,
                                        bestMatch, Types.JAVA_BASE_ASSIGNABLE)
                                        && !Types.areSignaturesEqual(
                                                targetMatch, bestMatch))) {
                    bestMatch = targetMatch;
                    bestMatchIndex = i;
                }
            }
            if (bestMatch != null)
                return bestMatchIndex;
        }
        return -1;
    }

    /**
     * Accessor name.
     *
     * @param getorset
     *            the getorset
     * @param propName
     *            the prop name
     * @return the string
     */
    static String accessorName(final String getorset, final String propName) {
        return getorset
                + String.valueOf(Character.toUpperCase(propName.charAt(0)))
                + propName.substring(1);
    }

    /**
     * Checks for object property getter.
     *
     * @param clas
     *            the clas
     * @param propName
     *            the prop name
     * @return true, if successful
     */
    public static boolean hasObjectPropertyGetter(final Class clas,
            final String propName) {
        String getterName = accessorName("get", propName);
        try {
            clas.getMethod(getterName, new Class[0]);
            return true;
        } catch (final NoSuchMethodException e) { /* fall through */ }
        getterName = accessorName("is", propName);
        try {
            final Method m = clas.getMethod(getterName, new Class[0]);
            return m.getReturnType() == Boolean.TYPE;
        } catch (final NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * Checks for object property setter.
     *
     * @param clas
     *            the clas
     * @param propName
     *            the prop name
     * @return true, if successful
     */
    public static boolean hasObjectPropertySetter(final Class clas,
            final String propName) {
        final String setterName = accessorName("set", propName);
        final Method[] methods = clas.getMethods();
        // we don't know the right hand side of the assignment yet.
        // has at least one setter of the right name?
        for (final Method method : methods)
            if (method.getName().equals(setterName))
                return true;
        return false;
    }

    /**
     * Gets the object property.
     *
     * @param obj
     *            the obj
     * @param propName
     *            the prop name
     * @return the object property
     * @throws UtilEvalError
     *             the util eval error
     * @throws ReflectError
     *             the reflect error
     */
    public static Object getObjectProperty(final Object obj,
            final String propName) throws UtilEvalError, ReflectError {
        final Object[] args = new Object[] {};
        Interpreter.debug("property access: ");
        Method method = null;
        Exception e1 = null, e2 = null;
        try {
            final String accessorName = accessorName("get", propName);
            method = resolveExpectedJavaMethod(null/* bcm */, obj.getClass(),
                    obj, accessorName, args, false);
        } catch (final Exception e) {
            e1 = e;
        }
        if (method == null)
            try {
                final String accessorName = accessorName("is", propName);
                method = resolveExpectedJavaMethod(null/* bcm */,
                        obj.getClass(), obj, accessorName, args, false);
                if (method.getReturnType() != Boolean.TYPE)
                    method = null;
            } catch (final Exception e) {
                e2 = e;
            }
        if (method == null)
            throw new ReflectError("Error in property getter: " + e1
                    + (e2 != null ? " : " + e2 : ""));
        try {
            return invokeMethod(method, obj, args);
        } catch (final InvocationTargetException e) {
            throw new UtilEvalError("Property accessor threw exception: "
                    + e.getTargetException());
        }
    }

    /**
     * Sets the object property.
     *
     * @param obj
     *            the obj
     * @param propName
     *            the prop name
     * @param value
     *            the value
     * @throws ReflectError
     *             the reflect error
     * @throws UtilEvalError
     *             the util eval error
     */
    public static void setObjectProperty(final Object obj,
            final String propName, final Object value)
            throws ReflectError, UtilEvalError {
        final String accessorName = accessorName("set", propName);
        final Object[] args = new Object[] {value};
        Interpreter.debug("property access: ");
        try {
            final Method method = resolveExpectedJavaMethod(null/* bcm */,
                    obj.getClass(), obj, accessorName, args, false);
            invokeMethod(method, obj, args);
        } catch (final InvocationTargetException e) {
            throw new UtilEvalError("Property accessor threw exception: "
                    + e.getTargetException());
        }
    }

    /**
     * Return a more human readable version of the type name.
     * Specifically, array types are returned with postfix "[]" dimensions.
     * e.g. return "int []" for integer array instead of "class [I" as
     * would be returned by Class getName() in that case.
     *
     * @param type
     *            the type
     * @return the string
     */
    public static String normalizeClassName(final Class type) {
        if (!type.isArray())
            return type.getName();
        final StringBuffer className = new StringBuffer();
        try {
            className.append(getArrayBaseType(type).getName() + " ");
            for (int i = 0; i < getArrayDimensions(type); i++)
                className.append("[]");
        } catch (final ReflectError e) { /* shouldn't happen */ }
        return className.toString();
    }

    /**
     * returns the dimensionality of the Class
     * returns 0 if the Class is not an array class.
     *
     * @param arrayClass
     *            the array class
     * @return the array dimensions
     */
    public static int getArrayDimensions(final Class arrayClass) {
        if (!arrayClass.isArray())
            return 0;
        return arrayClass.getName().lastIndexOf('[') + 1; // why so cute?
    }

    /**
     * Returns the base type of an array Class.
     * throws ReflectError if the Class is not an array class.
     *
     * @param arrayClass
     *            the array class
     * @return the array base type
     * @throws ReflectError
     *             the reflect error
     */
    public static Class getArrayBaseType(final Class arrayClass)
            throws ReflectError {
        if (!arrayClass.isArray())
            throw new ReflectError("The class is not an array.");
        return arrayClass.getComponentType();
    }

    /**
     * A command may be implemented as a compiled Java class containing one or
     * more static invoke() methods of the correct signature. The invoke()
     * methods must accept two additional leading arguments of the interpreter
     * and callstack, respectively. e.g. invoke(interpreter, callstack, ...)
     * This method adds the arguments and invokes the static method, returning
     * the result.
     *
     * @param commandClass
     *            the command class
     * @param args
     *            the args
     * @param interpreter
     *            the interpreter
     * @param callstack
     *            the callstack
     * @return the object
     * @throws UtilEvalError
     *             the util eval error
     */
    public static Object invokeCompiledCommand(final Class commandClass,
            final Object[] args, final Interpreter interpreter,
            final CallStack callstack) throws UtilEvalError {
        // add interpereter and namespace to args list
        final Object[] invokeArgs = new Object[args.length + 2];
        invokeArgs[0] = interpreter;
        invokeArgs[1] = callstack;
        System.arraycopy(args, 0, invokeArgs, 2, args.length);
        final BshClassManager bcm = interpreter.getClassManager();
        try {
            return Reflect.invokeStaticMethod(bcm, commandClass, "invoke",
                    invokeArgs);
        } catch (final InvocationTargetException e) {
            throw new UtilEvalError(
                    "Error in compiled command: " + e.getTargetException());
        } catch (final ReflectError e) {
            throw new UtilEvalError("Error invoking compiled command: " + e);
        }
    }

    /**
     * Log invoke method.
     *
     * @param msg
     *            the msg
     * @param method
     *            the method
     * @param args
     *            the args
     */
    private static void logInvokeMethod(final String msg, final Method method,
            final Object[] args) {
        if (Interpreter.DEBUG) {
            Interpreter.debug(msg + method + " with args:");
            for (int i = 0; i < args.length; i++) {
                final Object arg = args[i];
                Interpreter.debug("args[" + i + "] = " + arg + " type = "
                        + (arg == null ? "<unknown>" : arg.getClass()));
            }
        }
    }

    /**
     * Check found static method.
     *
     * @param method
     *            the method
     * @param staticOnly
     *            the static only
     * @param clas
     *            the clas
     * @throws UtilEvalError
     *             the util eval error
     */
    private static void checkFoundStaticMethod(final Method method,
            final boolean staticOnly, final Class clas) throws UtilEvalError {
        // We're looking for a static method but found an instance method
        if (method != null && staticOnly && !isStatic(method))
            throw new UtilEvalError("Cannot reach instance method: "
                    + StringUtil.methodString(method.getName(),
                            method.getParameterTypes())
                    + " from static context: " + clas.getName());
    }

    /**
     * Cant find constructor.
     *
     * @param clas
     *            the clas
     * @param types
     *            the types
     * @return the reflect error
     */
    private static ReflectError cantFindConstructor(final Class clas,
            final Class[] types) {
        if (types.length == 0)
            return new ReflectError(
                    "Can't find default constructor for: " + clas);
        else
            return new ReflectError("Can't find constructor: "
                    + StringUtil.methodString(clas.getName(), types)
                    + " in class: " + clas.getName());
    }

    /**
     * Checks if is public.
     *
     * @param c
     *            the c
     * @return true, if is public
     */
    private static boolean isPublic(final Class c) {
        return Modifier.isPublic(c.getModifiers());
    }

    /**
     * Checks if is public.
     *
     * @param m
     *            the m
     * @return true, if is public
     */
    private static boolean isPublic(final Method m) {
        return Modifier.isPublic(m.getModifiers());
    }

    /**
     * Checks if is public.
     *
     * @param c
     *            the c
     * @return true, if is public
     */
    private static boolean isPublic(final Constructor c) {
        return Modifier.isPublic(c.getModifiers());
    }

    /**
     * Checks if is static.
     *
     * @param m
     *            the m
     * @return true, if is static
     */
    private static boolean isStatic(final Method m) {
        return Modifier.isStatic(m.getModifiers());
    }
}
