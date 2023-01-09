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
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.stream.Stream;

import static bsh.This.Keys.BSHTHIS;
import static bsh.This.Keys.BSHSTATIC;
import static bsh.This.Keys.BSHCLASSMODIFIERS;
import static bsh.Capabilities.haveAccessibility;
/**
 * All of the reflection API code lies here.  It is in the form of static
 * utilities.  Maybe this belongs in LHS.java or a generic object
 * wrapper class.
 *
 * @author Pat Niemeyer
 * @author Daniel Leuck
 * @author Nick nickl- Lombard
 */
/*
    Note: This class is messy.  The method and field resolution need to be
    rewritten.  Various methods in here catch NoSuchMethod or NoSuchField
    exceptions during their searches.  These should be rewritten to avoid
    having to catch the exceptions.  Method lookups are now cached at a high
    level so they are less important, however the logic is messy.
*/
public final class Reflect {
    public static final Object[] ZERO_ARGS = {};
    public static final Class<?>[] ZERO_TYPES = {};
    static final String GET_PREFIX = "get";
    static final String SET_PREFIX = "set";
    static final String IS_PREFIX = "is";
    private static final Map<String,String> ACCESSOR_NAMES = new WeakHashMap<>();
    private static final Pattern DEFAULT_PACKAGE
        = Pattern.compile("[^\\.]+|bsh\\..*");
    private static final Pattern PACKAGE_ACCESS;
    static {
        String packageAccess = Security.getProperty("package.access");
        if (null == packageAccess) packageAccess = "null";
        PACKAGE_ACCESS = Pattern.compile("(?:"+packageAccess.replace(',', '|')+").*");
    }


    /** Invoke method on arbitrary object instance. May be static (through the object
     * instance) or dynamic. Object may be a bsh scripted object (bsh.This type).
     * @return the result of the method call */
    public static Object invokeObjectMethod(
            Object object, String methodName, Object[] args,
            Interpreter interpreter, CallStack callstack,
            Node callerInfo) throws EvalError {
        // Bsh scripted object
        if (object instanceof This && !This.isExposedThisMethod(methodName))
            return ((This) object).invokeMethod(
                    methodName, args, interpreter, callstack, callerInfo,
                    false/* declaredOnly */);
        // Plain Java object, script engine exposed instance and find java method to invoke
        BshClassManager bcm = interpreter.getClassManager();
        // Flag primitive for overwrites, value/type exposure and recursion loop metigation.
        boolean isPrimitive = object instanceof Primitive;
        try { // The type exposed to script engine and used for method lookup
            Class<?> type = object.getClass();
            if (isPrimitive) { // Overwrite methods cosmetically deferred to bsh.Primitive
                if (methodName.equals("equals")) return ((Primitive)object).equals(args[0]);
                // NULL and VOID remain Primitive the rest are value/primitive type exposed
                if (object != Primitive.NULL && object != Primitive.VOID) {
                    type = ((Primitive)object).getType();
                    object = Primitive.unwrap(object);
                } // Cosmetic Void.TYPE returned while internal type remains bsh.Primitive
                if (methodName.equals("getType") || methodName.equals("getClass"))
                    return (object == Primitive.VOID) ? ((Primitive)object).getType() : type;
            } try { // Script engine exposed instance for method lookup and invocation here
                Invocable method = resolveExpectedJavaMethod(
                        bcm, type, object, methodName, args, false);
                NameSpace ns = getThisNS(object);
                if (null != ns) ns.setNode(callerInfo);
                return method.invoke(object, args); // script engine exposed instance call
            } catch (ReflectError re) { // Void has overstayed its welcome round about here
                if (object == Primitive.VOID) throw new EvalError("Attempt to invoke method: "
                    + methodName + "() on undefined", callerInfo, callstack, re);
                // Handle primitive method not found. Autoboxing or magic math method lookup.
                // Errors gets rolled up, and not found is deferred back to exposed type.
                if (isPrimitive && !interpreter.getStrictJava()) try { // mitigate recursion
                    if (!Types.isNumeric(object)) return invokeObjectMethod( // autobox type
                        object, methodName, args, interpreter, callstack, callerInfo);
                    return numericMathMethod( // find magic math method on all numeric types
                        object, type, methodName, args, interpreter, callstack, callerInfo);
                } catch (TargetError te) { throw te; // method found but errored throw it up
                } catch (EvalError ee) { /* not found deffered fall through to exposed type */ }
                throw new EvalError( // Deferred/unhandled method not found on exposed type
                    "Error in method invocation: " + re.getMessage(), callerInfo, callstack, re);
            } catch (InvocationTargetException e) {
                throw targetErrorFromTargetException(e, methodName, callstack, callerInfo);
            }
        } catch (UtilEvalError e) {
            throw e.toEvalError(callerInfo, callstack);
        }
    }

    /** Package scoped target error, extracted as a method, to prevent duplication.
     * Due to the two paths leading to method invocation, via BSHMethodInvocation and
     * BSHPrimarySuffix, gets consolidated here.
     * @return error instance to throw */
    static TargetError targetErrorFromTargetException(InvocationTargetException e,
            String methodName, CallStack callstack, Node callerInfo) {
        String msg = "Method Invocation " + methodName;
        Throwable te = e.getCause();
        // Try to squeltch the native code stack trace if the exception was caused by
        // a reflective call back into the bsh interpreter (e.g. eval() or source()
        boolean isNative = true;
        if (te instanceof EvalError)
            isNative = (te instanceof TargetError) && ((TargetError) te).inNativeCode();

        return new TargetError(msg, te, callerInfo, callstack, isNative);
    }

    /** Determine which math class to call first, based on the floating point flag.
     * If the method is not found on the primary class, attempt using alternative.
     * Errors and exceptions will abort and get rolled up.
     * @return the result of the method call */
    private static Object numericMathMethod(Object object, Class<?> type, String methodName,
            Object[] args, Interpreter interpreter, CallStack callstack, Node callerInfo)
            throws EvalError {
        Class<?> mathType = Types.isFloatingpoint(object) ? BigDecimal.class : BigInteger.class;
        try {
            return invokeMathMethod(mathType,
                    object, type, methodName, args, interpreter, callstack, callerInfo);
        } catch (TargetError te) { // method found but errored lets throw it back up
            throw te.reThrow("Method found on " + mathType.getSimpleName() + " but with error");
        } catch (EvalError ee) { // method not found try alternative math provider
            return invokeMathMethod(
                Types.isFloatingpoint(object) ? BigInteger.class : BigDecimal.class,
                    object, type, methodName, args, interpreter, callstack, callerInfo);
        }
    }

    /** Cast object up to math type, and invoke the math method with the supplied
     * arguments. Assess the method return, if mathType, cast return back down to
     * return type and complete the magic. Otherwise return non chaining result.
     * @return the result of the method call */
    private static Object invokeMathMethod(Class<?> mathType, Object object, Class<?> returnType,
        String methodName, Object[] args, Interpreter interpreter, CallStack callstack,
            Node callerInfo) throws EvalError {
        Object retrn = invokeObjectMethod(Primitive.castWrapper(mathType, object),
            methodName, args, interpreter, callstack, callerInfo);
        if (retrn instanceof Primitive && ((Primitive)retrn).getType() == mathType)
            return Primitive.wrap(Primitive.castWrapper(returnType, retrn), returnType);
        return retrn;
    }

    /**
        Invoke a method known to be static.
        No object instance is needed and there is no possibility of the
        method being a bsh scripted method.
    */
    public static Object invokeStaticMethod(
            BshClassManager bcm, Class<?> clas, String methodName,
            Object [] args, Node callerInfo )
                    throws ReflectError, UtilEvalError,
                           InvocationTargetException {
        Interpreter.debug("invoke static Method");
        NameSpace ns = getThisNS(clas);
        if (null != ns)
            ns.setNode(callerInfo);
        Invocable method = resolveExpectedJavaMethod(
            bcm, clas, null, methodName, args, true );
        return method.invoke(null, args);
    }

    public static Object getStaticFieldValue(Class<?> clas, String fieldName)
            throws UtilEvalError, ReflectError {
        return getFieldValue( clas, null, fieldName, true/*onlystatic*/);
    }

    /**
     * Check for a field with the given name in a java object or scripted object
     * if the field exists fetch the value, if not check for a property value.
     * If neither is found return Primitive.VOID.
     */
    public static Object getObjectFieldValue( Object object, String fieldName )
            throws UtilEvalError, ReflectError {
        if ( object instanceof This ) {
            return ((This) object).namespace.getVariable( fieldName );
        } else if( object == Primitive.NULL ) {
            throw new UtilTargetError( new NullPointerException(
                "Attempt to access field '" +fieldName+"' on null value" ) );
        } else {
            try {
                return getFieldValue(
                    object.getClass(), object, fieldName, false/*onlystatic*/);
            } catch ( ReflectError e ) {
                // no field, try property access
                if ( hasObjectPropertyGetter( object.getClass(), fieldName ) )
                    return getObjectProperty( object, fieldName );
                else
                    throw e;
            }
        }
    }

    static LHS getLHSStaticField(Class<?> clas, String fieldName)
            throws UtilEvalError, ReflectError {
        try {
            Invocable f = resolveExpectedJavaField(
                clas, fieldName, true/*onlystatic*/);
            return new LHS(f);
        } catch ( ReflectError e ) {
            NameSpace ns = getThisNS(clas);
            if (isGeneratedClass(clas) && null != ns && ns.isClass) {
                Variable var = ns.getVariableImpl(fieldName, true);
                if ( null != var && (!var.hasModifier("private")
                        || haveAccessibility()) )
                    return new LHS(ns, fieldName);
            }

            // not a field, try property access
            if ( hasObjectPropertySetter( clas, fieldName ) )
                return new LHS( clas, fieldName );
            else
                throw e;
        }
    }

    /**
        Get an LHS reference to an object field.

        This method also deals with the field style property access.
        In the field does not exist we check for a property setter.
    */
    static LHS getLHSObjectField( Object object, String fieldName )
            throws UtilEvalError, ReflectError {
        if ( object instanceof This )
            return new LHS( ((This)object).namespace, fieldName, false );
        try {
            Invocable f = resolveExpectedJavaField(
                object.getClass(), fieldName, false/*staticOnly*/ );
            return new LHS(object, f);
        } catch ( ReflectError e ) {
            NameSpace ns = getThisNS(object);
            if (isGeneratedClass(object.getClass()) && null != ns && ns.isClass) {
                Variable var = ns.getVariableImpl(fieldName, true);
                if ( null != var && (!var.hasModifier("private")
                        || haveAccessibility()) )
                    return new LHS(ns, fieldName);
            }
            // not a field, try property access
            if ( hasObjectPropertySetter( object.getClass(), fieldName ) )
                return new LHS( object, fieldName );
            else
                throw e;
        }
    }

    private static Object getFieldValue(
            Class<?> clas, Object object, String fieldName, boolean staticOnly)
            throws UtilEvalError, ReflectError {
        try {
            Invocable f = resolveExpectedJavaField(clas, fieldName, staticOnly);
            return f.invoke(object);
        } catch ( ReflectError e ) {
            NameSpace ns = getThisNS(clas);
            if (isGeneratedClass(clas) && null != ns && ns.isClass)
                if (staticOnly) {
                    Variable var = ns.getVariableImpl(fieldName, true);
                    Object val = Primitive.VOID;
                    if ( null != var && (!var.hasModifier("private")
                            || haveAccessibility()) )
                        val = ns.unwrapVariable(var);
                    if (Primitive.VOID != val)
                        return val;
                }
                else if (null != (ns = getThisNS(object))) {
                    Variable var = ns.getVariableImpl(fieldName, true);
                    Object val = Primitive.VOID;
                    if ( null != var && (!var.hasModifier("private")
                            || haveAccessibility()) )
                        val = ns.unwrapVariable(var);
                    if (Primitive.VOID != val)
                        return val;
                }
            throw e;
        } catch(InvocationTargetException e) {
            if (e.getCause() instanceof InterpreterError)
                throw (InterpreterError)e.getCause();
            if (e.getCause() instanceof UtilEvalError)
                throw new UtilTargetError(e.getCause());
            throw new ReflectError("Can't access field: "
                + fieldName, e.getCause());
        }
    }

    /*
        Note: this method and resolveExpectedJavaField should be rewritten
        to invert this logic so that no exceptions need to be caught
        unecessarily.  This is just a temporary impl.
        @return the field or null if not found
    */
    protected static Invocable resolveJavaField(
            Class<?> clas, String fieldName, boolean staticOnly )
            throws UtilEvalError {
        try {
            return resolveExpectedJavaField( clas, fieldName, staticOnly );
        } catch ( ReflectError e ) {
            return null;
        }
    }

    /**
        @throws ReflectError if the field is not found.
    */
    /*
        Note: this should really just throw NoSuchFieldException... need
        to change related signatures and code.
    */
    protected static Invocable resolveExpectedJavaField(
            Class<?> clas, String fieldName, boolean staticOnly)
            throws UtilEvalError, ReflectError {
        Invocable field = BshClassManager.memberCache
                .get(clas).findField(fieldName);

        if (null == field)
            throw new ReflectError("No such field: "
                    + fieldName + " for class: " + clas.getName());

        if ( staticOnly && !field.isStatic() )
            throw new UtilEvalError(
                "Can't reach instance field: " + fieldName
              + " from static context: " + clas.getName() );

        return field;
    }

    /**
        This method wraps resolveJavaMethod() and expects a non-null method
        result. If the method is not found it throws a descriptive ReflectError.
    */
    protected static Invocable resolveExpectedJavaMethod(
            BshClassManager bcm, Class<?> clas, Object object,
            String name, Object[] args, boolean staticOnly )
            throws ReflectError, UtilEvalError {
        if ( object == Primitive.NULL )
            throw new UtilTargetError( new NullPointerException(
                "Attempt to invoke method " +name+" on null value" ) );

        Class<?>[] types = Types.getTypes(args);
        Invocable method = resolveJavaMethod( clas, name, types, staticOnly );
        if ( null != bcm && bcm.getStrictJava()
                && method != null && method.getDeclaringClass().isInterface()
                && method.getDeclaringClass() != clas
                && Modifier.isStatic(method.getModifiers()))
            // static interface methods are class only
            method = null;

        if ( method == null )
            throw new ReflectError(
                ( staticOnly ? "Static method " : "Method " )
                + StringUtil.methodString(name, types) +
                " not found in class'" + clas.getName() + "'");

        return method;
    }

    /**
        The full blown resolver method.  All other method invocation methods
        delegate to this.  The method may be static or dynamic unless
        staticOnly is set (in which case object may be null).
        If staticOnly is set then only static methods will be located.
        <p/>

        This method performs caching (caches discovered methods through the
        class manager and utilizes cached methods.)
        <p/>

        This method determines whether to attempt to use non-public methods
        based on Capabilities.haveAccessibility() and will set the accessibilty
        flag on the method as necessary.
        <p/>

        If, when directed to find a static method, this method locates a more
        specific matching instance method it will throw a descriptive exception
        analogous to the error that the Java compiler would produce.
        Note: as of 2.0.x this is a problem because there is no way to work
        around this with a cast.
        <p/>

        @param staticOnly
            The method located must be static, the object param may be null.
        @return the method or null if no matching method was found.
    */
    protected static Invocable resolveJavaMethod(
            Class<?> clas, String name, Class<?>[] types,
            boolean staticOnly ) throws UtilEvalError {
        if ( clas == null )
            throw new InterpreterError("null class");

        Invocable method = BshClassManager.memberCache
                .get(clas).findMethod(name, types);
        Interpreter.debug("resolved java method: ", method, " on class: ", clas);
        checkFoundStaticMethod( method, staticOnly, clas );
        return method;
    }

    /** Find a static method member of baseClass, for the given name.
     * @param baseClass class to query
     * @param methodName method name to find
     * @return a BshMethod wrapped Method. */
    static BshMethod staticMethodImport(Class<?> baseClass, String methodName) {
        Invocable method = BshClassManager.memberCache.get(baseClass)
                .findStaticMethod(methodName);
        if (null != method)
            return new BshMethod(method, null);
        return null;
    }

    /**
        Primary object constructor
        This method is simpler than those that must resolve general method
        invocation because constructors are not inherited.
     <p/>
     This method determines whether to attempt to use non-public constructors
     based on Capabilities.haveAccessibility() and will set the accessibilty
     flag on the method as necessary.
     <p/>
    */
    static Object constructObject( Class<?> clas, Object[] args )
            throws ReflectError, InvocationTargetException {
        return constructObject(clas, null, args);
    }
    static Object constructObject( Class<?> clas, Object object, Object[] args )
            throws ReflectError, InvocationTargetException {
        if ( null == clas )
            return Primitive.NULL;
        if ( clas.isInterface() )
            throw new ReflectError(
                "Can't create instance of an interface: "+clas);

        Class<?>[] types = Types.getTypes(args);
        if (clas.isMemberClass() && !isStatic(clas) && null != object)
            types = Stream.concat(Stream.of(object.getClass()),
                    Stream.of(types)).toArray(Class[]::new);
        Interpreter.debug("Looking for most specific constructor: ", clas);
        Invocable con = BshClassManager.memberCache.get(clas)
                .findMethod(clas.getName(), types);
        if ( con == null || (args.length != con.getParameterCount()
                    && !con.isVarArgs() && !con.isInnerClass()))
            throw cantFindConstructor( clas, types );

        try {
            return con.invoke( object, args );
        } catch(InvocationTargetException  e) {
            if (e.getCause().getCause() instanceof IllegalAccessException)
                throw new ReflectError(
                    "We don't have permission to create an instance. "
                    + e.getCause().getCause().getMessage()
                    + " Use setAccessibility(true) to enable access.",
                    e.getCause().getCause());
            throw e;
        }
    }


    /**
     * Find the best match for signature idealMatch and return the method.
     * This method anticipates receiving the full list of BshMethods of
     * the same name, regardless of the potential arity/validity of
     * each method.
     *
     * @param idealMatch the signature to look for
     * @param methods the set of candidate {@link BshMethod}s which
     * differ only in the types of their arguments.
     */
    public static BshMethod findMostSpecificBshMethod(
            Class<?>[] idealMatch, List<BshMethod> methods ) {
        Interpreter.debug("find most specific BshMethod for: "+
                          Arrays.toString(idealMatch));
        int match = findMostSpecificBshMethodIndex( idealMatch, methods );
        return match == -1 ? null : methods.get(match);
    }


    /**
     * Find the best match for signature idealMatch and return the position
     * of the matching signature within the list.
     * This method anticipates receiving the full list of BshMethods of the
     * same name,regardless of the potential arity/validity of each method.
     * Filter the list of methods for only valid candidates
     * before performing the comparison (e.g. method name and
     * number of args match).  Also expand the parameter type list
     * for VarArgs methods.
     *
     * @param idealMatch the signature to look for
     * @param methods the set of candidate BshMethods which differ only in the
     * types of their arguments.
     */
    public static int findMostSpecificBshMethodIndex(Class<?>[] idealMatch,
                                                      List<BshMethod> methods) {
        for (int i=0; i<methods.size(); i++)
            Interpreter.debug("  "+i+":"+methods.get(i).toString()+" "+methods.get(i).getClass().getName());

        /*
         * Filter for non-varArgs method signatures of the same arity
         */
        List<Class<?>[]> candidateSigs = new ArrayList<>();

        // array to remap the index in the new list
        ArrayList<Integer> remap = new ArrayList<>();

        int i=0;
        for( BshMethod m : methods ) {
            Class<?>[] parameterTypes = m.getParameterTypes();
            if (idealMatch.length == parameterTypes.length) {
                remap.add(i);
                candidateSigs.add( parameterTypes );
            }
            i++;
        }
        Class<?>[][] sigs = candidateSigs.toArray(new Class[candidateSigs.size()][]);

        int match = findMostSpecificSignature( idealMatch, sigs );
        if (match >= 0) {
            match = remap.get(match);
            Interpreter.debug(" remap: "+remap);
            Interpreter.debug(" match:"+match);
            return match;
        }

        /*
         * If did not get a match then try VarArgs methods
         * Filter for varArgs method signatures of sufficient arity
         * Expand out the vararg parameters.
         */
        candidateSigs.clear();
        remap.clear();
        i=0;
        for( BshMethod m : methods ) {
            Class<?>[] parameterTypes = m.getParameterTypes();
            if (m.isVarArgs()
                && idealMatch.length >= parameterTypes.length-1 ) {
                Class<?>[] candidateSig = new Class[idealMatch.length];
                System.arraycopy(parameterTypes, 0, candidateSig, 0,
                                 parameterTypes.length-1);
                Class<?> arrayCompType = parameterTypes[parameterTypes.length-1].getComponentType();
                Arrays.fill(candidateSig, parameterTypes.length-1,
                            idealMatch.length, arrayCompType);
                remap.add(i);
                candidateSigs.add( candidateSig );
            }
            i++;
        }

        sigs = candidateSigs.toArray(new Class[candidateSigs.size()][]);
        match = findMostSpecificSignature( idealMatch, sigs);
        if (match >= 0) {
            match = remap.get(match);
            Interpreter.debug(" remap (varargs): "+Arrays.toString(remap.toArray(new Integer[0])));
            Interpreter.debug(" match (varargs):"+match);
        }

        return match;
    }

    /**
     * Find the best match for signature idealMatch and return the method.
     * This method anticipates receiving the full list of methods of
     * the same name, regardless of the potential arity/validity of
     * each method.
     *
     * @param idealMatch the signature to look for
     * @param methods the set of candidate {@link Invocable}s which
     * differ only in the types of their arguments.
     */
    public static Invocable findMostSpecificInvocable(
            Class<?>[] idealMatch, List<Invocable> methods ) {
        Interpreter.debug("find most specific Invocable for: "+
                          Arrays.toString(idealMatch));

        int match = findMostSpecificInvocableIndex( idealMatch, methods );
        return match == -1 ? null : methods.get(match);
    }

    /**
     * Find the best match for signature idealMatch and return the position
     * of the matching signature within the list.
     * This method anticipates receiving the full list of methods of the
     * same name,regardless of the potential arity/validity of each method.
     * Filter the list of methods for only valid candidates
     * before performing the comparison (e.g. method name and
     * number of args match).  Also expand the parameter type list
     * for VarArgs methods.
     *
     * This method currently does not take into account Java 5 covariant
     * return types... which I think will require that we find the most
     * derived return type of otherwise identical best matches.
     *
     * @param idealMatch the signature to look for
     * @param methods the set of candidate method which differ only in the
     * types of their arguments.
     */
    public static int findMostSpecificInvocableIndex(Class<?>[] idealMatch,
                                                      List<Invocable> methods) {
        for (int i=0; i<methods.size(); i++)
            Interpreter.debug("  "+i+"="+methods.get(i).toString());

        /*
         * Filter for non-varArgs method signatures of the same arity
         */
        List<Class<?>[]> candidateSigs = new ArrayList<>();
        // array to remap the index in the new list
        ArrayList<Integer> remap = new ArrayList<>();
        int i=0;
        for( Invocable method : methods ) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (idealMatch.length == parameterTypes.length) {
                remap.add(i);
                candidateSigs.add( parameterTypes );
            }
            i++;
        }
        Class<?>[][] sigs = candidateSigs.toArray(new Class[candidateSigs.size()][]);

        int match = findMostSpecificSignature( idealMatch, sigs );
        if (match >= 0) {
            match = remap.get(match);
            Interpreter.debug(" remap="+Arrays.toString(remap.toArray(new Integer[0])));
            Interpreter.debug(" match="+match);
            return match;
        }


        /*
         * If did not get a match then try VarArgs methods
         * Filter for varArgs method signatures of sufficient arity
         */
        candidateSigs.clear();
        remap.clear();
        i=0;
        for( Invocable method : methods ) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (method.isVarArgs()
                && idealMatch.length >= parameterTypes.length-1 ) {
                Class<?>[] candidateSig = new Class[idealMatch.length];
                System.arraycopy(parameterTypes, 0, candidateSig, 0,
                                 parameterTypes.length-1);
                Arrays.fill(candidateSig, parameterTypes.length-1,
                            idealMatch.length, method.getVarArgsComponentType());
                remap.add(i);
                candidateSigs.add( candidateSig );
            }
            i++;
        }

        sigs = candidateSigs.toArray(new Class[candidateSigs.size()][]);
        match = findMostSpecificSignature( idealMatch, sigs);

        /*
         * return the remaped value so that the index is relative
         * to the original list
         */
        if (match >= 0)
            match = remap.get(match);
        Interpreter.debug(" remap (varargs) ="+Arrays.toString(remap.toArray(new Integer[0])));
        Interpreter.debug(" match (varargs) ="+match);
        return match;
    }

    /**
        Implement JLS 15.11.2
        Return the index of the most specific arguments match or -1 if no
        match is found.
        This method is used by both methods and constructors (which
        unfortunately don't share a common interface for signature info).

     @return the index of the most specific candidate

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
    static int findMostSpecificSignature(
        Class<?>[] idealMatch, Class<?>[][] candidates ) {

        for ( int round = Types.FIRST_ROUND_ASSIGNABLE;
                round <= Types.LAST_ROUND_ASSIGNABLE; round++ ) {
            Class<?>[] bestMatch = null;
            int bestMatchIndex = -1;

            for (int i=0; i < candidates.length; i++) {
                Class<?>[] targetMatch = candidates[i];
                if (null != bestMatch && Types
                        .areSignaturesEqual(targetMatch, bestMatch))
                    // overridden keep first
                    continue;

                // If idealMatch fits targetMatch and this is the first match
                // or targetMatch is more specific than the best match, make it
                // the new best match.
                if ( Types.isSignatureAssignable(
                        idealMatch, targetMatch, round )
                    && ( bestMatch == null
                        || Types.areSignaturesEqual(idealMatch, targetMatch)
                    || ( Types.isSignatureAssignable(targetMatch, bestMatch,
                                Types.JAVA_BASE_ASSIGNABLE)
                       && !Types.areSignaturesEqual(idealMatch, bestMatch)))) {
                    bestMatch = targetMatch;
                    bestMatchIndex = i;
                }
            }
            if ( bestMatch != null )
                return bestMatchIndex;
        }
        return -1;
    }

    static String accessorName( String prefix, String propName ) {
        if (!ACCESSOR_NAMES.containsKey(propName)) {
            char[] ch = propName.toCharArray();
            ch[0] = Character.toUpperCase(ch[0]);
            ACCESSOR_NAMES.put(propName, new String(ch));
        }
        return prefix + ACCESSOR_NAMES.get(propName);
    }

    public static boolean hasObjectPropertyGetter(
            Class<?> clas, String propName ) {
        if ( Types.isPropertyType(clas) )
            return true;
        return BshClassManager.memberCache
                .get(clas).hasMember(propName)
            && null != BshClassManager.memberCache
                .get(clas).findGetter(propName);
    }

    public static boolean hasObjectPropertySetter(
            Class<?> clas, String propName ) {
        if ( Types.isPropertyType(clas) )
            return true;
        return BshClassManager.memberCache
                .get(clas).hasMember(propName)
            && null != BshClassManager.memberCache
                .get(clas).findSetter(propName);
    }

    @SuppressWarnings("rawtypes")
    public static Object getObjectProperty(Object obj, String propName) {
        if (Types.isPropertyTypeEntry(obj)) switch (propName) {
            case "key":
                return ((Entry) obj).getKey();
            case "val": case "value":
                return ((Entry) obj).getValue();
        }
        return getObjectProperty(obj, (Object) propName);
    }
    @SuppressWarnings("rawtypes")
    public static Object getObjectProperty(Object obj, Object propName) {
        if ( Types.isPropertyTypeMap(obj) ) {
            Map map = (Map) obj;
            if (map.containsKey(propName))
                return map.get(propName);
            return Primitive.VOID;
        }

        if ( Types.isPropertyTypeEntry(obj) ) {
            Entry entre = (Entry) obj;
            if (propName.equals(entre.getKey()))
                return entre.getValue();
            return Primitive.VOID;
        }

        Class<?> cls = obj.getClass();
        if ( Types.isPropertyTypeEntryList(cls) ) {
            Entry entre = getEntryForKey(propName, (Entry[]) obj);
            if ( null != entre )
                return entre.getValue();
            return Primitive.VOID;
        }

        if ( obj instanceof Class )
            cls = (Class) obj;
        Invocable getter = BshClassManager.memberCache.get(cls)
                .findGetter(propName.toString());
        if ( null == getter ) {
            Interpreter.debug("property getter not found");
            return Primitive.VOID;
        }
        try {
            return getter.invoke(obj);
        } catch(InvocationTargetException e) {
            Interpreter.debug("Property accessor threw exception");
            return Primitive.VOID;
        }
    }

    @SuppressWarnings("rawtypes")
    public static Entry getEntryForKey(Object key, Entry[] entries) {
        for ( Entry ntre : entries )
            if ( key.equals(ntre.getKey()) )
                return ntre;
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Object setObjectProperty(
        Object obj, String propName, Object value) {
        if (Types.isPropertyTypeEntry(obj)) switch(propName) {
            case "val": case "value":
                return ((Entry) obj).setValue(value);
        }
        return setObjectProperty(obj, (Object) propName, value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Object setObjectProperty(
        Object obj, Object propName, Object value) {
        if ( Types.isPropertyTypeMap(obj) )
            return ((Map) obj).put(propName, Primitive.unwrap(value));

        if ( Types.isPropertyTypeEntry(obj) ) {
            Entry entre = (Entry) obj;
            if ( propName.equals(entre.getKey()) )
                return entre.setValue(Primitive.unwrap(value));
            throw new ReflectError("No such property setter: " + propName
                    + " for type: " + StringUtil.typeString(obj));
        }

        Class<?> cls = obj.getClass();
        if ( Types.isPropertyTypeEntryList(cls) )
            return getEntryForKey(propName, (Entry[]) obj)
                    .setValue(Primitive.unwrap(value));

        if ( obj instanceof Class )
            cls = (Class) obj;

        Invocable setter = BshClassManager.memberCache.get(cls)
                .findSetter(propName.toString());
        if ( null == setter )
            throw new ReflectError("No such property setter: " + propName
                    + " for type: " + StringUtil.typeString(cls));
        try {
            return setter.invoke(obj, new Object[] { Primitive.unwrap(value) });
        } catch(InvocationTargetException e) {
            throw new ReflectError("Property accessor threw exception: "
                + e.getCause(),  e.getCause());
        }
    }

    /**
        A command may be implemented as a compiled Java class containing one or
        more static invoke() methods of the correct signature.  The invoke()
        methods must accept two additional leading arguments of the interpreter
        and callstack, respectively. e.g. invoke(interpreter, callstack, ... )
        This method adds the arguments and invokes the static method, returning
        the result.
    */
    public static Object invokeCompiledCommand(
        Class<?> commandClass, Object [] args, Interpreter interpreter,
        CallStack callstack, Node callerInfo )
        throws UtilEvalError
    {
        // add interpereter and namespace to args list
        Object[] invokeArgs = new Object[args.length + 2];
        invokeArgs[0] = interpreter;
        invokeArgs[1] = callstack;
        System.arraycopy( args, 0, invokeArgs, 2, args.length );
        BshClassManager bcm = interpreter.getClassManager();
        try {
            return invokeStaticMethod(
                bcm, commandClass, "invoke", invokeArgs, callerInfo );
        } catch ( InvocationTargetException e ) {
            throw new UtilEvalError(
                "Error in compiled command: " + e.getCause(), e );
        } catch ( ReflectError e ) {
            throw new UtilEvalError("Error invoking compiled command: " + e, e );
        }
    }

    static void logInvokeMethod(String msg, Invocable method, List<Object> params) {
        if (Interpreter.DEBUG.get()) {
            logInvokeMethod(msg, method, params.toArray());
        }
    }
    static void logInvokeMethod(String msg, Invocable method, Object[] args) {
        if (Interpreter.DEBUG.get()) {
            Interpreter.debug(msg, method, " with args:");
            for (int i = 0; i < args.length; i++) {
                final Object arg = args[i];
                Interpreter.debug("args[", i, "] = ", arg, " type = ", (arg == null ? "<unknown>" : arg.getClass()));
            }
        }
    }

    private static void checkFoundStaticMethod(
        Invocable method, boolean staticOnly, Class<?> clas )
        throws UtilEvalError
    {
        // We're looking for a static method but found an instance method
        if ( method != null && staticOnly && !method.isStatic() )
            throw new UtilEvalError(
                "Cannot reach instance method: "
                + StringUtil.methodString(
                    method.getName(), method.getParameterTypes() )
                + " from static context: "+ clas.getName() );
    }

    private static ReflectError cantFindConstructor(
        Class<?> clas, Class<?>[] types )
    {
        if ( types.length == 0 )
            return new ReflectError(
                "Can't find default constructor for: "+clas);
        else
            return new ReflectError(
                "Can't find constructor: "
                    + StringUtil.methodString( clas.getName(), types )
                    +" in class: "+ clas.getName() );
    }

    /*
     * Whether class is a bsh script generated type
     */
    public static boolean isGeneratedClass(Class<?> type) {
        return null != type && type != GeneratedClass.class
                && GeneratedClass.class.isAssignableFrom(type);
    }

    /**
     * Get the static bsh namespace field from the class.
     * @param className may be the name of clas itself or a superclass of clas.
     */
    public static This getClassStaticThis(Class<?> clas, String className) {
        try {
            return (This) getStaticFieldValue(clas, BSHSTATIC + className);
        } catch (Exception e) {
            throw new InterpreterError("Unable to get class static space: " + e, e);
        }
    }

    /**
     * Get the instance bsh namespace field from the object instance.
     * @return the class instance This object or null if the object has not
     * been initialized.
     */
    public static This getClassInstanceThis(Object instance, String className) {
        try {
            Object o = getObjectFieldValue(instance, BSHTHIS + className);
            return (This) Primitive.unwrap(o); // unwrap Primitive.Null to null
        } catch (Exception e) {
            throw new InterpreterError("Generated class: Error getting This " + e, e);
        }
    }

    /*
     * Get This namespace from the class static field BSHSTATIC
     */
    public static NameSpace getThisNS(Class<?> type) {
        if (!isGeneratedClass(type))
            return null;
        try {
            return getClassStaticThis(type, type.getSimpleName()).namespace;
        } catch (Exception e) {
            if (e.getCause() instanceof UtilTargetError)
                throw new InterpreterError(e.getCause().getCause().getMessage(),
                        e.getCause().getCause());
            return null;
        }
    }

    /*
     * Get This namespace from the instance field BSHTHIS
     */
    public static NameSpace getThisNS(Object object) {
        if (null == object)
            return null;
        Class<?> type = object.getClass();
        if (!isGeneratedClass(type))
            return null;
        try {
            if (object instanceof Proxy)
                return getThisNS(type.getInterfaces()[0]);
            return getClassInstanceThis(object, type.getSimpleName()).namespace;
        } catch (Exception e) {
            return null;
        }
    }

    /*
     * Get only variable names from the name space.
     * Filter out any bsh internal names.
     */
    public static String[] getVariableNames(NameSpace ns) {
        if (ns == null)
            return new String[0];
        return Stream.of(ns.getVariableNames())
                .filter(name->!name.matches("_?bsh.*"))
                .toArray(String[]::new);
    }

    /*
     * Convenience method helper to get method names from namespace
     */
    public static String[] getMethodNames(NameSpace ns) {
        if (ns == null)
            return new String[0];
        return ns.getMethodNames();
    }

    /*
     * Get method from class static namespace
     */
    public static BshMethod getMethod(Class<?> type, String name, Class<?>[] sig) {
        return getMethod(getThisNS(type), name, sig);
    }

    /*
     * Get method from object instance namespace
     */
    public static BshMethod getMethod(Object object, String name, Class<?>[] sig) {
        return getMethod(getThisNS(object), name, sig);
    }

    /*
     * Get method from namespace
     */
    public static BshMethod getMethod(NameSpace ns, String name, Class<?>[] sig) {
        if (null == ns)
            return null;
        try {
            return ns.getMethod(name, sig, true);
        } catch (Exception e) {
            return null;
        }
    }

    /*
     * Get method from either class static or object instance namespaces
     */
    public static BshMethod getDeclaredMethod(Class<?> type, String name, Class<?>[] sig) {
        if (!isGeneratedClass(type))
            return null;
        BshMethod meth = getMethod(type, name, sig);
        if (null == meth && !type.isInterface())
            return getMethod(getNewInstance(type), name, sig);
        return meth;
    }

    /*
     * Get all methods from class static namespace
     */
    public static BshMethod[] getMethods(Class<?> type) {
        return getMethods(getThisNS(type));
    }

    /*
     * Get all methods from object instance namespace
     */
    public static BshMethod[] getMethods(Object object) {
        return getMethods(getThisNS(object));
    }

    /*
     * Get all methods from namespace
     */
    public static BshMethod[] getMethods(NameSpace ns) {
        if (ns == null)
            return new BshMethod[0];
        return ns.getMethods();
    }

    /*
     * Get all methods from both class static and object instance namespaces
     */
    public static BshMethod[] getDeclaredMethods(Class<?> type) {
        if (!isGeneratedClass(type))
            return new BshMethod[0];
        if (type.isInterface())
            return getMethods(type);
        return getMethods(getNewInstance(type));
    }

    /*
     * Get variable from class static namespace
     */
    public static Variable getVariable(Class<?> type, String name) {
        return getVariable(getThisNS(type), name);
    }

    /*
     * Get variable from object instance namespace
     */
    public static Variable getVariable(Object object, String name) {
        return getVariable(getThisNS(object), name);
    }

    /*
     * Get variable from namespace
     */
    public static Variable getVariable(NameSpace ns, String name) {
        if (null == ns)
            return null;
        try {
            return ns.getVariableImpl(name, false);
        } catch (Exception e) {
            return null;
        }
    }

    /*
     * Get variable from either class static or object instance namespaces
     */
    public static Variable getDeclaredVariable(Class<?> type, String name) {
        if (!isGeneratedClass(type))
            return null;
        Variable var = getVariable(type, name);
        if (null == var && !type.isInterface())
            return getVariable(getNewInstance(type), name);
        return var;
    }

    /*
     * Get all variables from class static namespace
     */
    public static Variable[] getVariables(Class<?> type) {
        return getVariables(getThisNS(type));
    }

    /*
     * Get all variables from object instance namespace
     */
    public static Variable[] getVariables(Object object) {
        return getVariables(getThisNS(object));
    }

    /*
     * Get all variables from namespace
     */
    public static Variable[] getVariables(NameSpace ns) {
        return getVariables(ns, getVariableNames(ns));
    }

    /*
     * Get named list of variables from namespace
     */
    public static Variable[] getVariables(NameSpace ns, String[] names) {
        if (null == ns || null == names)
            return new Variable[0];
        return Stream.of(names).map(name->getVariable(ns, name))
            .filter(Objects::nonNull).toArray(Variable[]::new);
    }

    /*
     * Get all variables from both class static and object instance namespaces
     */
    public static Variable[] getDeclaredVariables(Class<?> type) {
        if (!isGeneratedClass(type))
            return new Variable[0];
        if (type.isInterface())
            return getVariables(type);
        return getVariables(getNewInstance(type));
    }

    /*
     * Get class modifiers from static variable BSHCLASSMODIFIERS
     */
    public static Modifiers getClassModifiers(Class<?> type) {
        try {
            return (Modifiers)getVariable(type, BSHCLASSMODIFIERS.toString()).getValue();
        } catch (Exception e) {
            return new Modifiers(type.isInterface() ? Modifiers.INTERFACE : Modifiers.CLASS);
        }
    }

    private static final Map<Class<?>,Object> instanceCache = new WeakHashMap<>();

    /*
     * Class new instance or null, wrap exception handling and
     * instance cache.
     */
    public static Object getNewInstance(Class<?> type) {
        if (instanceCache.containsKey(type))
            return instanceCache.get(type);
        try {
            instanceCache.put(type, type.getConstructor().newInstance());
        } catch ( IllegalArgumentException | ReflectiveOperationException | SecurityException e) {
            instanceCache.put(type, null);
        }
        return instanceCache.get(type);
    }

    static boolean isPrivate(Member member) {
        return Modifier.isPrivate(member.getModifiers());
    }

    static boolean isPrivate(Class<?> clazz) {
        return Modifier.isPrivate(clazz.getModifiers());
    }

    static boolean isPublic(Member member) {
        return Modifier.isPublic(member.getModifiers());
    }

    static boolean isPublic(Class<?> clazz) {
        return Modifier.isPublic(clazz.getModifiers());
    }

    public static boolean isStatic(Member member) {
        return member != null && Modifier.isStatic(member.getModifiers());
    }

    public static boolean isStatic(Class<?> clazz) {
        return clazz != null && Modifier.isStatic(clazz.getModifiers());
    }

    public static boolean hasModifier(String name, int modifiers) {
        return Modifier.toString(modifiers).contains(name);
    }

    public static boolean isPackageScope(Class<?> clazz) {
        return DEFAULT_PACKAGE.matcher(clazz.getName()).matches();
    }

    public static boolean isPackageAccessible(Class<?> clazz) {
        return haveAccessibility()
                || !PACKAGE_ACCESS.matcher(clazz.getName()).matches();
    }

    /** Manually create enum values array without using enum.values().
     * @param enm enum class to query
     * @return array of enum values */
    @SuppressWarnings("unchecked")
    static <T> T[] getEnumConstants(Class<T> enm) {
        return Stream.of(enm.getFields())
                .filter(f -> f.getType() == enm)
                .map(f -> {
            try {
                return f.get(null);
            } catch (Exception e) {
                return null;
            }
        })
        .filter(Objects::nonNull)
        .toArray(len -> (T[]) Array.newInstance(enm, len));
    }
}
