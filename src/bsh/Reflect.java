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
 * This file is part of the BeanShell Java Scripting distribution.           *
 * Documentation and updates may be found at http://www.beanshell.org/       *
 * Patrick Niemeyer (pat@pat.net)                                            *
 * Author of Learning Java, O'Reilly & Associates                            *
 *                                                                           *
 *****************************************************************************/

package bsh;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

/**
 * All of the reflection API code lies here.  It is in the form of static
 * utilities.  Maybe this belongs in LHS.java or a generic object
 * wrapper class.
 */
/*
	Note: This class is messy.  The method and field resolution need to be
	rewritten.  Various methods in here catch NoSuchMethod or NoSuchField
	exceptions during their searches.  These should be rewritten to avoid
	having to catch the exceptions.  Method lookups are now cached at a high 
	level so they are less important, however the logic is messy.
*/
final class Reflect {

    /**
	 * A comperator wich sorts methods according to {@@link #getVisibility}.
	 */
	public static final Comparator<Method> METHOD_COMPARATOR = new Comparator<Method>() {
		public int compare(final Method a, final Method b) {
			final int scoreA = getVisibility(a);
			final int scoreB = getVisibility(b);
			return (scoreA < scoreB) ? -1 : ((scoreA == scoreB) ? 0 : 1);

		}
	};


	/**
	 * Invoke method on arbitrary object instance.
	 * invocation may be static (through the object instance) or dynamic.
	 * Object may be a bsh scripted object (bsh.This type).
	 *
	 * @return the result of the method call
	 */
	public static Object invokeObjectMethod(Object object, String methodName, Object[] args, Interpreter interpreter, CallStack callstack, SimpleNode callerInfo) throws ReflectError, EvalError, InvocationTargetException {
		// Bsh scripted object
		if (object instanceof This && !This.isExposedThisMethod(methodName)) {
			return ((This) object).invokeMethod(methodName, args, interpreter, callstack, callerInfo, false/*delcaredOnly*/);
		}

		// Plain Java object, find the java method
		try {
			BshClassManager bcm = interpreter == null ? null : interpreter.getClassManager();
			Class clas = object.getClass();

			Method method = resolveExpectedJavaMethod(bcm, clas, object, methodName, args, false);

			return invokeMethod(method, object, args);
		} catch (UtilEvalError e) {
			throw e.toEvalError(callerInfo, callstack);
		}
	}


	/**
	 * Invoke a method known to be static.
	 * No object instance is needed and there is no possibility of the
	 * method being a bsh scripted method.
	 */
	public static Object invokeStaticMethod(BshClassManager bcm, Class clas, String methodName, Object[] args) throws ReflectError, UtilEvalError, InvocationTargetException {
		Interpreter.debug("invoke static Method");
		Method method = resolveExpectedJavaMethod(bcm, clas, null, methodName, args, true);
		return invokeMethod(method, null, args);
	}


	/**
	 * Invoke the Java method on the specified object, performing needed
	 * type mappings on arguments and return values.
	 *
	 * @param args may be null
	 */
	static Object invokeMethod(Method method, Object object, Object[] args) throws ReflectError, InvocationTargetException {
		if (args == null) {
			args = new Object[0];
		}

		logInvokeMethod("Invoking method (entry): ", method, args);

		boolean isVarArgs = method.isVarArgs();

		// Map types to assignable forms, need to keep this fast...
		Class[] types = method.getParameterTypes();
		Object[] tmpArgs = new Object[types.length];
		int fixedArgLen = types.length;
		if (isVarArgs) {
			if (fixedArgLen == args.length && types[fixedArgLen - 1].isAssignableFrom(args[fixedArgLen - 1].getClass())) {
				isVarArgs = false;
			} else {
				fixedArgLen--;
			}
		}
		try {
			for (int i = 0; i < fixedArgLen; i++) {
				tmpArgs[i] = Types.castObject(args[i]/*rhs*/, types[i]/*lhsType*/, Types.ASSIGNMENT);
			}
			if (isVarArgs) {
				Class varType = types[fixedArgLen].getComponentType();
				Object varArgs = Array.newInstance(varType, args.length - fixedArgLen);
				for (int i = fixedArgLen, j = 0; i < args.length; i++, j++) {
					Array.set(varArgs, j, Primitive.unwrap(Types.castObject(args[i]/*rhs*/, varType/*lhsType*/, Types.ASSIGNMENT)));
				}
				tmpArgs[fixedArgLen] = varArgs;
			}
		} catch (UtilEvalError e) {
			throw new InterpreterError("illegal argument type in method invocation: " + e);
		}

		// unwrap any primitives
		tmpArgs = Primitive.unwrap(tmpArgs);

		logInvokeMethod("Invoking method (after massaging values): ", method, tmpArgs);

		try {
			Object returnValue = method.invoke(object, tmpArgs);
			if (returnValue == null) {
				returnValue = Primitive.NULL;
			}
			Class returnType = method.getReturnType();

			return Primitive.wrap(returnValue, returnType);
		} catch (IllegalAccessException e) {
			throw new ReflectError("Cannot access method " + StringUtil.methodString(method.getName(), method.getParameterTypes()) + " in '" + method.getDeclaringClass() + "' :" + e, e);
		}
	}


	public static Object getIndex(Object array, int index) throws ReflectError, UtilTargetError {
		if (Interpreter.DEBUG) {
			Interpreter.debug("getIndex: " + array + ", index=" + index);
		}
		try {
			Object val = Array.get(array, index);
			return Primitive.wrap(val, array.getClass().getComponentType());
		} catch (ArrayIndexOutOfBoundsException e1) {
			throw new UtilTargetError(e1);
		} catch (Exception e) {
			throw new ReflectError("Array access:" + e);
		}
	}


	public static void setIndex(Object array, int index, Object val) throws ReflectError, UtilTargetError {
		try {
			val = Primitive.unwrap(val);
			Array.set(array, index, val);
		} catch (ArrayStoreException e2) {
			throw new UtilTargetError(e2);
		} catch (IllegalArgumentException e1) {
			//noinspection ThrowableInstanceNeverThrown
			throw new UtilTargetError(new ArrayStoreException(e1.toString()));
		} catch (Exception e) {
			throw new ReflectError("Array access:" + e);
		}
	}


	public static Object getStaticFieldValue(Class clas, String fieldName) throws UtilEvalError, ReflectError {
		return getFieldValue(clas, null, fieldName, true/*onlystatic*/);
	}


	/**
	 */
	public static Object getObjectFieldValue(Object object, String fieldName) throws UtilEvalError, ReflectError {
		if (object instanceof This) {
			return ((This) object).namespace.getVariable(fieldName);
		} else if (object == Primitive.NULL) {
			//noinspection ThrowableInstanceNeverThrown
			throw new UtilTargetError(new NullPointerException("Attempt to access field '" + fieldName + "' on null value"));
		} else {
			try {
				return getFieldValue(object.getClass(), object, fieldName, false/*onlystatic*/);
			} catch (ReflectError e) {
				// no field, try property acces

				if (hasObjectPropertyGetter(object.getClass(), fieldName)) {
					return getObjectProperty(object, fieldName);
				} else {
					throw e;
				}
			}
		}
	}


	static LHS getLHSStaticField(Class clas, String fieldName) throws UtilEvalError, ReflectError {
		Field f = resolveExpectedJavaField(clas, fieldName, true/*onlystatic*/);
		return new LHS(f);
	}


	/**
	 * Get an LHS reference to an object field.
	 * <p/>
	 * This method also deals with the field style property access.
	 * In the field does not exist we check for a property setter.
	 */
	static LHS getLHSObjectField(Object object, String fieldName) throws UtilEvalError, ReflectError {
		if (object instanceof This) {
			// I guess this is when we pass it as an argument?
			// Setting locally
			boolean recurse = false;
			return new LHS(((This) object).namespace, fieldName, recurse);
		}

		try {
			Field f = resolveExpectedJavaField(object.getClass(), fieldName, false/*staticOnly*/);
			return new LHS(object, f);
		} catch (ReflectError e) {
			// not a field, try property access
			if (hasObjectPropertySetter(object.getClass(), fieldName)) {
				return new LHS(object, fieldName);
			} else {
				throw e;
			}
		}
	}


	private static Object getFieldValue(Class clas, Object object, String fieldName, boolean staticOnly) throws UtilEvalError, ReflectError {
		try {
			Field f = resolveExpectedJavaField(clas, fieldName, staticOnly);

			Object value = f.get(object);
			Class returnType = f.getType();
			return Primitive.wrap(value, returnType);

		} catch (NullPointerException e) { // shouldn't happen
			throw new ReflectError("???" + fieldName + " is not a static field.");
		} catch (IllegalAccessException e) {
			throw new ReflectError("Can't access field: " + fieldName);
		}
	}

	/*
			 Note: this method and resolveExpectedJavaField should be rewritten
			 to invert this logic so that no exceptions need to be caught
			 unecessarily.  This is just a temporary impl.
			 @return the field or null if not found
		 */


	protected static Field resolveJavaField(Class clas, String fieldName, boolean staticOnly) throws UtilEvalError {
		try {
			return resolveExpectedJavaField(clas, fieldName, staticOnly);
		} catch (ReflectError e) {
			return null;
		}
	}


	/**
	 * @throws ReflectError if the field is not found.
	 */
	/*
			 Note: this should really just throw NoSuchFieldException... need
			 to change related signatures and code.
		 */
	protected static Field resolveExpectedJavaField(Class clas, String fieldName, boolean staticOnly) throws UtilEvalError, ReflectError {
		Field field;
		try {
			if (Capabilities.haveAccessibility()) {
				field = findAccessibleField(clas, fieldName);
			} else {
			    // Class getField() finds only public fields
				field = clas.getField(fieldName);
			}
		} catch (NoSuchFieldException e) {
			throw new ReflectError("No such field: " + fieldName, e);
		} catch (SecurityException e) {
			throw new UtilTargetError("Security Exception while searching fields of: " + clas, e);
		}

		if (staticOnly && !Modifier.isStatic(field.getModifiers())) {
			throw new UtilEvalError("Can't reach instance field: " + fieldName + " from static context: " + clas.getName());
		}

		return field;
	}


	/**
	 * Used when accessibility capability is available to locate an occurrance
	 * of the field in the most derived class or superclass and set its
	 * accessibility flag.
	 * Note that this method is not needed in the simple non accessible
	 * case because we don't have to hunt for fields.
	 * Note that classes may declare overlapping private fields, so the
	 * distinction about the most derived is important.  Java doesn't normally
	 * allow this kind of access (super won't show private variables) so
	 * there is no real syntax for specifying which class scope to use...
	 *
	 * @return the Field or throws NoSuchFieldException
	 * @throws NoSuchFieldException if the field is not found
	 */
	/*
			 This method should be rewritten to use getFields() and avoid catching
			 exceptions during the search.
		 */
	private static Field findAccessibleField(Class clas, String fieldName) throws UtilEvalError, NoSuchFieldException {
		// Quick check catches public fields include those in interfaces
        try {
            return clas.getField(fieldName);
		} catch (NoSuchFieldException e) {
			// ignore
		}
        // try hidden fields (protected, private, package protected)
        if (Capabilities.haveAccessibility()) {
            try {
                while (clas != null) {
                    final Field[] declaredFields = clas.getDeclaredFields();
                    for (int i = 0; i < declaredFields.length; i++) {
                        Field field = declaredFields[i];
                        if (field.getName().equals(fieldName)) {
                            field.setAccessible(true);
                            return field;
                        }
                    }
                    clas = clas.getSuperclass();
                }
            } catch (SecurityException e) {
               // ignore -> NoSuchFieldException
            }
        }
		throw new NoSuchFieldException(fieldName);
	}


	/**
	 * This method wraps resolveJavaMethod() and expects a non-null method
	 * result. If the method is not found it throws a descriptive ReflectError.
	 */
	protected static Method resolveExpectedJavaMethod(BshClassManager bcm, Class clas, Object object, String name, Object[] args, boolean staticOnly) throws ReflectError, UtilEvalError {
		if (object == Primitive.NULL) {
			//noinspection ThrowableInstanceNeverThrown
			throw new UtilTargetError(new NullPointerException("Attempt to invoke method " + name + " on null value"));
		}

		Class[] types = Types.getTypes(args);
		Method method = resolveJavaMethod(bcm, clas, name, types, staticOnly);

		if (method == null) {
			throw new ReflectError((staticOnly ? "Static method " : "Method ") + StringUtil.methodString(name, types) + " not found in class'" + clas.getName() + "'");
		}

		return method;
	}


	/**
	 * The full blown resolver method.  All other method invocation methods
	 * delegate to this.  The method may be static or dynamic unless
	 * staticOnly is set (in which case object may be null).
	 * If staticOnly is set then only static methods will be located.
	 * <p/>
	 * <p/>
	 * This method performs caching (caches discovered methods through the
	 * class manager and utilizes cached methods.)
	 * <p/>
	 * <p/>
	 * This method determines whether to attempt to use non-public methods
	 * based on Capabilities.haveAccessibility() and will set the accessibilty
	 * flag on the method as necessary.
	 * <p/>
	 * <p/>
	 * If, when directed to find a static method, this method locates a more
	 * specific matching instance method it will throw a descriptive exception
	 * analogous to the error that the Java compiler would produce.
	 * Note: as of 2.0.x this is a problem because there is no way to work
	 * around this with a cast.
	 * <p/>
	 *
	 * @param staticOnly The method located must be static, the object param may be null.
	 * @return the method or null if no matching method was found.
	 */
	protected static Method resolveJavaMethod(BshClassManager bcm, Class clas, String name, Class[] types, boolean staticOnly) throws UtilEvalError {
		if (clas == null) {
			throw new InterpreterError("null class");
		}

		// Lookup previously cached method
		Method method = null;
		if (bcm == null) {
			Interpreter.debug("resolveJavaMethod UNOPTIMIZED lookup");
		} else {
			method = bcm.getResolvedMethod(clas, name, types, staticOnly);
		}

		if (method == null) {
			boolean publicOnly = !Capabilities.haveAccessibility();
			// Searching for the method may, itself be a priviledged action
			try {
				method = findOverloadedMethod(clas, name, types, publicOnly);
			} catch (SecurityException e) {
				throw new UtilTargetError("Security Exception while searching methods of: " + clas, e);
			}

			checkFoundStaticMethod(method, staticOnly, clas);

			// This is the first time we've seen this method, set accessibility if needed
			if ((method != null) && !isPublic(method)) {
				if (publicOnly) {
					Interpreter.debug("resolveJavaMethod - no accessible method found");
					method = null;
				} else {
					Interpreter.debug("resolveJavaMethod - setting method accessible");
					try {
						method.setAccessible(true);
					} catch (final SecurityException e) {
						Interpreter.debug("resolveJavaMethod - setting accessible failed: " + e);
						method = null;
					}
				}
			}

			// If succeeded cache the resolved method.
			if (method != null && bcm != null) {
				bcm.cacheResolvedMethod(clas, types, method);
			}
		}

		return method;
	}


	/**
	 * Get the candidate methods by searching the class and interface graph
	 * of baseClass and resolve the most specific.
	 *
	 * @return the method or null for not found
	 */
	private static Method findOverloadedMethod(final Class baseClass, final String methodName, final Class[] types, final boolean publicOnly) {
		if (Interpreter.DEBUG) {
			Interpreter.debug("Searching for method: " + StringUtil.methodString(methodName, types) + " in '" + baseClass.getName() + "'");
		}
		final List<Method> publicMethods = new ArrayList<Method>();
		final Collection<Method> nonPublicMethods = publicOnly ? new DummyCollection<Method>() : new ArrayList<Method>();
		collectMethods(baseClass, methodName, types.length, publicMethods, nonPublicMethods);
		Collections.sort(publicMethods, METHOD_COMPARATOR);
		Method method = findMostSpecificMethod(types, publicMethods);
		if (method == null) {
			method = findMostSpecificMethod(types, nonPublicMethods);
		}
		return method;
	}


	/**
	 * Accumulate all matching methods, including non-public methods in the
	 * inheritence tree of provided baseClass.
	 * <p/>
	 * This method is analogous to Class getMethods() which returns all public
	 * methods in the inheritence tree.
	 * <p/>
	 * In the normal (non-accessible) case this also addresses the problem
	 * that arises when a package private class or private inner class
	 * implements a public interface or derives from a public type.  In other
	 * words, sometimes we'll find public methods that we can't use directly
	 * and we have to find the same public method in a parent class or
	 * interface.
	 */
	private static void collectMethods(final Class baseClass, final String methodName, final int numArgs, final Collection<Method> publicMethods, final Collection<Method> nonPublicMethods) {
		final Class superclass = baseClass.getSuperclass();
		if (superclass != null) {
			collectMethods(superclass, methodName, numArgs, publicMethods, nonPublicMethods);
		}
		final Method[] methods = baseClass.getDeclaredMethods();
		for (final Method m : methods) {
			if (matchesNameAndSignature(m, methodName, numArgs)) {
				if (isPublic(m.getDeclaringClass()) && isPublic(m)) {
					publicMethods.add(m);
				} else {
					nonPublicMethods.add(m);
				}
			}
		}
		for (final Class interfaceClass :baseClass.getInterfaces()){
			collectMethods(interfaceClass, methodName, numArgs, publicMethods, nonPublicMethods);
		}
	}


	private static boolean matchesNameAndSignature(final Method m, final String methodName, final int numArgs) {
		return m.getName().equals(methodName) && (m.isVarArgs() ? m.getParameterTypes().length - 1 <= numArgs : m.getParameterTypes().length == numArgs);
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
	 */
	static Object constructObject(Class clas, Object[] args) throws ReflectError, InvocationTargetException {
		if (clas.isInterface()) {
			throw new ReflectError("Can't create instance of an interface: " + clas);
		}

		Class[] types = Types.getTypes(args);

		// Find the constructor.
		// (there are no inherited constructors to worry about)
		Constructor[] constructors = Capabilities.haveAccessibility() ? clas.getDeclaredConstructors() : clas.getConstructors();

		if (Interpreter.DEBUG) {
			Interpreter.debug("Looking for most specific constructor: " + clas);
		}
		Constructor con = findMostSpecificConstructor(types, constructors);
		if (con == null) {
			throw cantFindConstructor(clas, types);
		}

		if (!isPublic(con) && Capabilities.haveAccessibility()) {
			con.setAccessible(true);
		}

		args = Primitive.unwrap(args);
		try {
			return con.newInstance(args);
		} catch (InstantiationException e) {
			throw new ReflectError("The class " + clas + " is abstract ", e);
		} catch (IllegalAccessException e) {
			throw new ReflectError("We don't have permission to create an instance. Use setAccessibility(true) to enable access.", e);
		} catch (IllegalArgumentException e) {
			throw new ReflectError("The number of arguments was wrong", e);
		}
	}


	/*
			This method should parallel findMostSpecificMethod()
			The only reason it can't be combined is that Method and Constructor
			don't have a common interface for their signatures
		*/
	static Constructor findMostSpecificConstructor(Class[] idealMatch, Constructor[] constructors) {
		int match = findMostSpecificConstructorIndex(idealMatch, constructors);
		return (match == -1) ? null : constructors[match];
	}


	static int findMostSpecificConstructorIndex(Class[] idealMatch, Constructor[] constructors) {
		Class[][] candidates = new Class[constructors.length][];
		for (int i = 0; i < candidates.length; i++) {
			candidates[i] = constructors[i].getParameterTypes();
		}

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
	 * @param methods the set of candidate method which differ only in the
	 *                types of their arguments.
	 * @see #findMostSpecificSignature(Class[], Class[][])
	 */
	private static Method findMostSpecificMethod(final Class[] idealMatch, final Collection<Method> methods) {
		if (Interpreter.DEBUG) {
			Interpreter.debug("Looking for most specific method");
		}
		// copy signatures into array for findMostSpecificMethod()
		List<Class[]> candidateSigs = new ArrayList<Class[]>();
		List<Method> methodList = new ArrayList<Method>();
		for (Method method : methods) {
			Class[] parameterTypes = method.getParameterTypes();
			methodList.add(method);
			candidateSigs.add(parameterTypes);
			if (method.isVarArgs()) {
				Class[] candidateSig = new Class[idealMatch.length];
				int j = 0;
				for (; j < parameterTypes.length - 1; j++) {
					candidateSig[j] = parameterTypes[j];
				}
				Class varType = parameterTypes[j].getComponentType();
				for (; j < idealMatch.length; j++) {
					candidateSig[j] = varType;
				}
				methodList.add(method);
				candidateSigs.add(candidateSig);
			}
		}

		int match = findMostSpecificSignature(idealMatch, candidateSigs.toArray(new Class[candidateSigs.size()][]));
		return match == -1 ? null : methodList.get(match);
	}


	/**
	 * Implement JLS 15.11.2
	 * Return the index of the most specific arguments match or -1 if no
	 * match is found.
	 * This method is used by both methods and constructors (which
	 * unfortunately don't share a common interface for signature info).
	 *
	 * @return the index of the most specific candidate
	 */
	/*
		  Note: Two methods which are equally specific should not be allowed by
		  the Java compiler.  In this case BeanShell currently chooses the first
		  one it finds.  We could add a test for this case here (I believe) by
		  adding another isSignatureAssignable() in the other direction between
		  the target and "best" match.  If the assignment works both ways then
		  neither is more specific and they are ambiguous.  I'll leave this test
		  out for now because I'm not sure how much another test would impact
		  performance.  Method selection is now cached at a high level, so a few
		  friendly extraneous tests shouldn't be a problem.
		 */
	static int findMostSpecificSignature(Class[] idealMatch, Class[][] candidates) {
		for (int round = Types.FIRST_ROUND_ASSIGNABLE; round <= Types.LAST_ROUND_ASSIGNABLE; round++) {
			Class[] bestMatch = null;
			int bestMatchIndex = -1;

			for (int i = 0; i < candidates.length; i++) {
				Class[] targetMatch = candidates[i];

				// If idealMatch fits targetMatch and this is the first match
				// or targetMatch is more specific than the best match, make it
				// the new best match.
				if (Types.isSignatureAssignable(idealMatch, targetMatch, round) && ((bestMatch == null) || Types.isSignatureAssignable(targetMatch, bestMatch, Types.JAVA_BASE_ASSIGNABLE))) {
					bestMatch = targetMatch;
					bestMatchIndex = i;
				}
			}

			if (bestMatch != null) {
				return bestMatchIndex;
			}
		}

		return -1;
	}


	private static String accessorName(String getorset, String propName) {
		return getorset + String.valueOf(Character.toUpperCase(propName.charAt(0))) + propName.substring(1);
	}


	public static boolean hasObjectPropertyGetter(Class clas, String propName) {
		if (clas == Primitive.class) {
			return false;
		}
		String getterName = accessorName("get", propName);
		try {
			clas.getMethod(getterName, new Class[0]);
			return true;
		} catch (NoSuchMethodException e) { /* fall through */ }
		getterName = accessorName("is", propName);
		try {
			Method m = clas.getMethod(getterName, new Class[0]);
			return (m.getReturnType() == Boolean.TYPE);
		} catch (NoSuchMethodException e) {
			return false;
		}
	}


	public static boolean hasObjectPropertySetter(Class clas, String propName) {
		String setterName = accessorName("set", propName);
		Method[] methods = clas.getMethods();

		// we don't know the right hand side of the assignment yet.
		// has at least one setter of the right name?
		for (Method method : methods) {
			if (method.getName().equals(setterName)) {
				return true;
			}
		}
		return false;
	}


	public static Object getObjectProperty(Object obj, String propName) throws UtilEvalError, ReflectError {
		Object[] args = new Object[]{};

		Interpreter.debug("property access: ");
		Method method = null;

		Exception e1 = null, e2 = null;
		try {
			String accessorName = accessorName("get", propName);
			method = resolveExpectedJavaMethod(null/*bcm*/, obj.getClass(), obj, accessorName, args, false);
		} catch (Exception e) {
			e1 = e;
		}
		if (method == null) {
			try {
				String accessorName = accessorName("is", propName);
				method = resolveExpectedJavaMethod(null/*bcm*/, obj.getClass(), obj, accessorName, args, false);
				if (method.getReturnType() != Boolean.TYPE) {
					method = null;
				}
			} catch (Exception e) {
				e2 = e;
			}
		}
		if (method == null) {
			throw new ReflectError("Error in property getter: " + e1 + (e2 != null ? " : " + e2 : ""));
		}

		try {
			return invokeMethod(method, obj, args);
		} catch (InvocationTargetException e) {
			throw new UtilEvalError("Property accessor threw exception: " + e.getTargetException());
		}
	}


	public static void setObjectProperty(Object obj, String propName, Object value) throws ReflectError, UtilEvalError {
		String accessorName = accessorName("set", propName);
		Object[] args = new Object[]{value};

		Interpreter.debug("property access: ");
		try {
			Method method = resolveExpectedJavaMethod(null/*bcm*/, obj.getClass(), obj, accessorName, args, false);
			invokeMethod(method, obj, args);
		} catch (InvocationTargetException e) {
			throw new UtilEvalError("Property accessor threw exception: " + e.getTargetException());
		}
	}


	/**
	 * Return a more human readable version of the type name.
	 * Specifically, array types are returned with postfix "[]" dimensions.
	 * e.g. return "int []" for integer array instead of "class [I" as
	 * would be returned by Class getName() in that case.
	 */
	public static String normalizeClassName(Class type) {
		if (!type.isArray()) {
			return type.getName();
		}
		StringBuilder className = new StringBuilder();
		try {
			className.append(getArrayBaseType(type).getName()).append(' ');
			for (int i = 0; i < getArrayDimensions(type); i++) {
				className.append("[]");
			}
		} catch (ReflectError e) {
			/*shouldn't happen*/
		}

		return className.toString();
	}


	/**
	 * returns the dimensionality of the Class
	 * returns 0 if the Class is not an array class
	 */
	public static int getArrayDimensions(Class arrayClass) {
		if (!arrayClass.isArray()) {
			return 0;
		}

		return arrayClass.getName().lastIndexOf('[') + 1;  // why so cute?
	}


	/**
	 * Returns the base type of an array Class.
	 * throws ReflectError if the Class is not an array class.
	 */
	public static Class getArrayBaseType(Class arrayClass) throws ReflectError {
		if (!arrayClass.isArray()) {
			throw new ReflectError("The class is not an array.");
		}

		return arrayClass.getComponentType();

	}


	/**
	 * A command may be implemented as a compiled Java class containing one or
	 * more static invoke() methods of the correct signature.  The invoke()
	 * methods must accept two additional leading arguments of the interpreter
	 * and callstack, respectively. e.g. invoke(interpreter, callstack, ... )
	 * This method adds the arguments and invokes the static method, returning
	 * the result.
	 */
	public static Object invokeCompiledCommand(Class commandClass, Object[] args, Interpreter interpreter, CallStack callstack) throws UtilEvalError {
		// add interpereter and namespace to args list
		Object[] invokeArgs = new Object[args.length + 2];
		invokeArgs[0] = interpreter;
		invokeArgs[1] = callstack;
		System.arraycopy(args, 0, invokeArgs, 2, args.length);
		BshClassManager bcm = interpreter.getClassManager();
		try {
			return Reflect.invokeStaticMethod(bcm, commandClass, "invoke", invokeArgs);
		} catch (InvocationTargetException e) {
			throw new UtilEvalError("Error in compiled command: " + e.getTargetException(), e);
		} catch (ReflectError e) {
			throw new UtilEvalError("Error invoking compiled command: " + e, e);
		}
	}


	private static void logInvokeMethod(String msg, Method method, Object[] args) {
		if (Interpreter.DEBUG) {
			Interpreter.debug(msg + method + " with args:");
			for (int i = 0; i < args.length; i++) {
				final Object arg = args[i];
				Interpreter.debug("args[" + i + "] = " + arg + " type = " + (arg == null ? "<unkown>" : arg.getClass()));
			}
		}
	}


	private static void checkFoundStaticMethod(Method method, boolean staticOnly, Class clas) throws UtilEvalError {
		// We're looking for a static method but found an instance method
		if (method != null && staticOnly && !isStatic(method)) {
			throw new UtilEvalError("Cannot reach instance method: " + StringUtil.methodString(method.getName(), method.getParameterTypes()) + " from static context: " + clas.getName());
		}
	}


	private static ReflectError cantFindConstructor(Class clas, Class[] types) {
		if (types.length == 0) {
			return new ReflectError("Can't find default constructor for: " + clas);
		} else {
			return new ReflectError("Can't find constructor: " + StringUtil.methodString(clas.getName(), types) + " in class: " + clas.getName());
		}
	}


	private static boolean isPublic(Member member) {
		return Modifier.isPublic(member.getModifiers());
	}


	private static boolean isPublic(Class clazz) {
		return Modifier.isPublic(clazz.getModifiers());
	}


	private static boolean isStatic(Method m) {
		return Modifier.isStatic(m.getModifiers());
	}


	static void setAccessible(final Field field) {
		if ( ! isPublic(field) && Capabilities.haveAccessibility()) {
			field.setAccessible(true);
		}
	}


	/**
	 * A method from a non public class gets a visibility score of 0.
	 * A method from a public class gets a visibility score of 1.
	 * And an interface method will get a visibility score of 2.
	 */
	private static int getVisibility(final Method method) {
		final Class<?> declaringClass = method.getDeclaringClass();
		if (declaringClass.isInterface()) {
			return 2; // interface methods are always public
		}
		if (isPublic(declaringClass)) {
			return 1;
		}
		return 0;
	}


	private static class DummyCollection<T> extends AbstractCollection<T> {

		@Override
		public Iterator<T> iterator() {
			return Collections.<T>emptySet().iterator();
		}


		@Override
		public int size() {
			return 0;
		}


		@Override
		public boolean add(final T t) {
			return false;
		}
	}

}
