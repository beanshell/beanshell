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
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.stream.Stream;

import static bsh.This.Keys.BSHTHIS;
import static bsh.This.Keys.BSHSTATIC;
import static bsh.This.Keys.BSHCLASSMODIFIERS;

/**
 * All of the reflection API code lies here.  It is in the form of static
 * utilities.  Maybe this belongs in LHS.java or a generic object
 * wrapper class.
 *
 * @author Pat Niemeyer
 * @author Daniel Leuck
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
        Invoke method on arbitrary object instance.
        invocation may be static (through the object instance) or dynamic.
        Object may be a bsh scripted object (bsh.This type).
        @return the result of the method call
    */
    public static Object invokeObjectMethod(
        Object object, String methodName, Object[] args,
        Interpreter interpreter, CallStack callstack, SimpleNode callerInfo )
        throws ReflectError, EvalError, InvocationTargetException
    {
        // Bsh scripted object
        if ( object instanceof This && !This.isExposedThisMethod(methodName) )
            return ((This)object).invokeMethod(
                methodName, args, interpreter, callstack, callerInfo,
                false/*delcaredOnly*/
            );

        // Plain Java object, find the java method
        try {
            BshClassManager bcm =
                interpreter == null ? null : interpreter.getClassManager();
            Class clas = object.getClass();

            Method method = resolveExpectedJavaMethod(
                bcm, clas, object, methodName, args, false );
            NameSpace ns = getThisNS(object);
            if (null != ns)
                ns.setNode(callerInfo);

            return invokeMethod( method, object, args );
        } catch ( UtilEvalError e ) {
            throw e.toEvalError( callerInfo, callstack );
        }
    }

    /**
        Invoke a method known to be static.
        No object instance is needed and there is no possibility of the
        method being a bsh scripted method.
    */
    public static Object invokeStaticMethod(
        BshClassManager bcm, Class clas, String methodName, Object [] args, SimpleNode callerInfo )
        throws ReflectError, UtilEvalError, InvocationTargetException
    {
        Interpreter.debug("invoke static Method");
        NameSpace ns = getThisNS(clas);
        if (null != ns)
            ns.setNode(callerInfo);
        Method method = resolveExpectedJavaMethod(
            bcm, clas, null, methodName, args, true );
        return invokeMethod( method, null, args );
    }

    /**
        Invoke the Java method on the specified object, performing needed
        type mappings on arguments and return values.
        @param args may be null
    */
    static Object invokeMethod(
        Method method, Object object, Object[] args )
        throws ReflectError, InvocationTargetException
    {
        if ( args == null )
            args = new Object[0];

        logInvokeMethod( "Invoking method (entry): ", method, args );

        boolean isVarArgs = method.isVarArgs();

        // Map types to assignable forms, need to keep this fast...
        Class [] types = method.getParameterTypes();
        Object [] tmpArgs = new Object [ types.length ];
        int fixedArgLen = types.length;
        if( isVarArgs )
            if( fixedArgLen==args.length && Types.isJavaAssignable(
                    types[fixedArgLen-1],
                    Types.getType(args[fixedArgLen-1])) )
                isVarArgs = false;
            else
                fixedArgLen--;
        try {
            for (int i=0; i<fixedArgLen; i++)
                tmpArgs[i] = Types.castObject(args[i], types[i], Types.ASSIGNMENT);
            if( isVarArgs )
                tmpArgs[fixedArgLen] = Types.castObject(
                        Primitive.unwrap((Object[])
                            BshArray.slice(args, fixedArgLen, args.length, 0)),
                        types[fixedArgLen].getComponentType(),
                        Types.CAST);
        } catch ( UtilEvalError e ) {
            throw new InterpreterError(
                "illegal argument type in method invocation: "+e, e);
        }

        // unwrap any primitives
        tmpArgs = Primitive.unwrap( tmpArgs );

        logInvokeMethod( "Invoking method (after massaging values): ",
            method, tmpArgs );

        try {
            Object returnValue = method.invoke( object, tmpArgs );
            if ( returnValue == null )
                returnValue = Primitive.NULL;
            Class returnType = method.getReturnType();

            return Primitive.wrap( returnValue, returnType );
        } catch( IllegalAccessException e ) {
            throw new ReflectError( "Cannot access method "
                + StringUtil.methodString(
                    method.getName(), method.getParameterTypes() )
                + " in '" + method.getDeclaringClass() + "' :" + e, e );
        }
    }

    public static Object getStaticFieldValue(Class clas, String fieldName)
        throws UtilEvalError, ReflectError
    {
        return getFieldValue( clas, null, fieldName, true/*onlystatic*/);
    }

    /**
     * Check for a field with the given name in a java object or scripted object
     * if the field exists fetch the value, if not check for a property value.
     * If neither is found return Primitive.VOID.
     */
    public static Object getObjectFieldValue( Object object, String fieldName )
        throws UtilEvalError, ReflectError
    {
        if ( object instanceof This ) {
            return ((This)object).namespace.getVariable( fieldName );
        } else if( object == Primitive.NULL ) {
            throw new UtilTargetError( new NullPointerException(
                "Attempt to access field '" +fieldName+"' on null value" ) );
        } else {
            try {
                return getFieldValue(
                    object.getClass(), object, fieldName, false/*onlystatic*/);
            } catch ( ReflectError e ) {
                // no field, try property acces

                if ( hasObjectPropertyGetter( object.getClass(), fieldName ) )
                    return getObjectProperty( object, fieldName );
                else
                    throw e;
            }
        }
    }

    static LHS getLHSStaticField(Class clas, String fieldName)
        throws UtilEvalError, ReflectError
    {
        try {
            Field f = resolveExpectedJavaField(
                clas, fieldName, true/*onlystatic*/);
            return new LHS(f);
        } catch ( ReflectError e )
        {
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
        throws UtilEvalError, ReflectError
    {
        if ( object instanceof This )
        {
            // I guess this is when we pass it as an argument?
            // Setting locally
            boolean recurse = false;
            return new LHS( ((This)object).namespace, fieldName, recurse );
        }

        try {
            Field f = resolveExpectedJavaField(
                object.getClass(), fieldName, false/*staticOnly*/ );
            return new LHS(object, f);
        } catch ( ReflectError e )
        {
            // not a field, try property access
            if ( hasObjectPropertySetter( object.getClass(), fieldName ) )
                return new LHS( object, fieldName );
            else
                throw e;
        }
    }

    private static Object getFieldValue(
        Class clas, Object object, String fieldName, boolean staticOnly )
        throws UtilEvalError, ReflectError
    {
        try {
            Field f = resolveExpectedJavaField( clas, fieldName, staticOnly );

            Object value = f.get(object);
            Class returnType = f.getType();
            return Primitive.wrap( value, returnType );
        } catch ( ReflectError e ) {
            NameSpace ns = getThisNS(clas);
            if (isGeneratedClass(clas) && null != ns && ns.isClass)
                if (staticOnly) {
                    Variable var = ns.getVariableImpl(fieldName, true);
                    Object val = Primitive.VOID;
                    if ( null != var && !var.hasModifier("private") )
                        val = ns.unwrapVariable(var);
                    if (Primitive.VOID != val)
                        return val;
                }
                else if (null != (ns = getThisNS(object))) {
                    Variable var = ns.getVariableImpl(fieldName, true);
                    Object val = Primitive.VOID;
                    if ( null != var && !var.hasModifier("private") )
                        val = ns.unwrapVariable(var);
                    if (Primitive.VOID != val)
                        return val;
                }
            throw e;
        } catch( NullPointerException e ) { // shouldn't happen
            throw new ReflectError(
                "???" + fieldName + " is not a static field.", e);
        } catch(IllegalAccessException e) {
            throw new ReflectError("Can't access field: " + fieldName, e);
        }
    }

    /*
        Note: this method and resolveExpectedJavaField should be rewritten
        to invert this logic so that no exceptions need to be caught
        unecessarily.  This is just a temporary impl.
        @return the field or null if not found
    */
    protected static Field resolveJavaField(
        Class clas, String fieldName, boolean staticOnly )
        throws UtilEvalError
    {
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
    protected static Field resolveExpectedJavaField(
        Class clas, String fieldName, boolean staticOnly
    )
        throws UtilEvalError, ReflectError
    {
        Field field;
        try {
            if ( Capabilities.haveAccessibility() )
                field = findAccessibleField( clas, fieldName );
            else
                // Class getField() finds only public fields
                field = clas.getField(fieldName);
        }
        catch( NoSuchFieldException e) {
            throw new ReflectError("No such field: " + fieldName, e );
        } catch ( SecurityException e ) {
            throw new UtilTargetError(
            "Security Exception while searching fields of: "+clas,
            e );
        }

        if ( staticOnly && !Modifier.isStatic( field.getModifiers() ) )
            throw new UtilEvalError(
                "Can't reach instance field: "+fieldName
                +" from static context: "+clas.getName() );

        return field;
    }

    /**
        Used when accessibility capability is available to locate an occurrence
        of the field in the most derived class or superclass and set its
        accessibility flag.
        Note that this method is not needed in the simple non accessible
        case because we don't have to hunt for fields.
        Note that classes may declare overlapping private fields, so the
        distinction about the most derived is important.  Java doesn't normally
        allow this kind of access (super won't show private variables) so
        there is no real syntax for specifying which class scope to use...

        @return the Field or throws NoSuchFieldException
        @throws NoSuchFieldException if the field is not found
    */
    /*
        This method should be rewritten to use getFields() and avoid catching
        exceptions during the search.
    */
    private static Field findAccessibleField( Class clas, String fieldName )
        throws UtilEvalError, NoSuchFieldException
    {
        Field field = null;
        // Quick check catches public fields include those in interfaces
        try {
            field = clas.getField(fieldName);
            // Not all public fields may be accessible
            if (field.isAccessible())
                return field;
        } catch (NoSuchFieldException e) {
            // ignore
        }
        // try hidden fields (protected, private, package protected)
        if (Capabilities.haveAccessibility()) {
            try {
                loop:
                while (clas != null) {
                    if ((field = getDeclaredFields(clas, fieldName)) != null)
                        break loop;
                    for (Class<?> inter : clas.getInterfaces())
                        if ((field = getDeclaredFields(inter, fieldName)) != null)
                            break loop;
                    clas = clas.getSuperclass();
                }
                if (field != null) {
                    field.setAccessible(true);
                    return field;
                }
            } catch (SecurityException e) {
               // ignore -> NoSuchFieldException
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    private static Field getDeclaredFields(Class<?> clas, String fieldName) {
        for (Field field :clas.getDeclaredFields())
            if (field.getName().equals(fieldName))
                return field;
        return null;
    }

    /**
        This method wraps resolveJavaMethod() and expects a non-null method
        result. If the method is not found it throws a descriptive ReflectError.
    */
    protected static Method resolveExpectedJavaMethod(
        BshClassManager bcm, Class clas, Object object,
        String name, Object[] args, boolean staticOnly )
        throws ReflectError, UtilEvalError
    {
        if ( object == Primitive.NULL )
            throw new UtilTargetError( new NullPointerException(
                "Attempt to invoke method " +name+" on null value" ) );

        Class [] types = Types.getTypes(args);
        Method method = resolveJavaMethod( bcm, clas, name, types, staticOnly );
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
    protected static Method resolveJavaMethod(
        BshClassManager bcm, Class clas, String name,
        Class [] types, boolean staticOnly )
        throws UtilEvalError
    {
        if ( clas == null )
            throw new InterpreterError("null class");

        // Lookup previously cached method
        Method method = null;
        if ( bcm == null )
            Interpreter.debug("resolveJavaMethod UNOPTIMIZED lookup");
        else
            method = bcm.getResolvedMethod( clas, name, types, staticOnly );

        if ( method == null )
        {
            boolean publicOnly = !Capabilities.haveAccessibility();
            // Searching for the method may, itself be a priviledged action
            try {
                method = findOverloadedMethod( clas, name, types, publicOnly );
            } catch ( SecurityException e ) {
                throw new UtilTargetError(
                "Security Exception while searching methods of: "+clas,
                e );
            }

            checkFoundStaticMethod( method, staticOnly, clas );

            // This is the first time we've seen this method, set accessibility
            if (method != null && !publicOnly && !method.isAccessible()) {
                try {
                    method.setAccessible(true);
                } catch (SecurityException e) {
                    method = null;
                }
            }

            // If succeeded cache the resolved method.
            if ( method != null && bcm != null )
                bcm.cacheResolvedMethod( clas, types, method );
        }

        return method;
    }

    /**
        Get the candidate methods by searching the class and interface graph
        of baseClass and resolve the most specific.
        @return the method or null for not found
     */
    private static Method findOverloadedMethod(
        Class baseClass, String methodName, Class[] types, boolean publicOnly )
    {
        Interpreter.debug( "Searching for method: ",
                StringUtil.methodString(methodName, types),
                " in '", baseClass.getName(), "'" );

        List<Method> publicMethods = new ArrayList<Method>();
        List<Method> nonPublicMethods = publicOnly ? null : new ArrayList<Method>();
        gatherMethodsRecursive(
            baseClass, methodName, types.length, publicMethods, nonPublicMethods );

        Interpreter.debug("Looking for most specific method: ", methodName);
        if (null != nonPublicMethods && !nonPublicMethods.isEmpty())
            publicMethods.addAll(nonPublicMethods);

        // sort methods by class hierarchy
        publicMethods.sort((a,b) ->
            a.getDeclaringClass().isAssignableFrom(b.getDeclaringClass())
            ? 1
            : a.getDeclaringClass() == b.getDeclaringClass() ? 0 : -1);

        Method method = findMostSpecificMethod( types, publicMethods );

        return method;
    }

    /**
        Climb the class and interface inheritance graph of the type and collect
        all methods matching the specified name and criterion.  If publicOnly
        is true then only public methods in *public* classes or interfaces will
        be returned.  In the normal (non-accessible) case this addresses the
        problem that arises when a package private class or private inner class
        implements a public interface or derives from a public type.
        <p/>

        preseving old comments for deleted getCandidateMethods() - fschmidt
    */

    /**
        Accumulate all methods, optionally including non-public methods,
        class and interface, in the inheritance tree of baseClass.

        This method is analogous to Class getMethods() which returns all public
        methods in the inheritance tree.

        In the normal (non-accessible) case this also addresses the problem
        that arises when a package private class or private inner class
        implements a public interface or derives from a public type.  In other
        words, sometimes we'll find public methods that we can't use directly
        and we have to find the same public method in a parent class or
        interface.

        @return the candidate methods vector
    */
    static void gatherMethodsRecursive(
        Class baseClass, String methodName, int numArgs,
        List<Method> publicMethods, List<Method> nonPublicMethods )
    {
        // Do we have a superclass? (interfaces don't, etc.)
        Class superclass = baseClass.getSuperclass();
        if ( superclass != null )
            gatherMethodsRecursive( superclass,
                methodName, numArgs, publicMethods, nonPublicMethods );

        // Add methods of the current class to the list.
        // In public case be careful to only add methods from a public class
        // and to use getMethods() instead of getDeclaredMethods()
        // (This addresses secure environments)
        boolean isPublicClass = isPublic(baseClass);
        if( isPublicClass || nonPublicMethods!=null ) {
            Method[] methods = nonPublicMethods==null ?
                baseClass.getMethods() : baseClass.getDeclaredMethods();
            for( Method m : methods ) {
                if  (methodName == null || (  m.getName().equals( methodName )
                    && ( m.isVarArgs() ? m.getParameterTypes().length-1 <= numArgs
                        : m.getParameterTypes().length == numArgs ))
                ) {
                    if( isPublicClass && isPublic(m) ) {
                        publicMethods.add( m );
                    } else if( nonPublicMethods != null ) {
                        nonPublicMethods.add( m );
                    }
                }
            }
        }

        // Does the class or interface implement interfaces?
        for( Class intf : baseClass.getInterfaces() )
            gatherMethodsRecursive(  intf,
                methodName, numArgs, publicMethods, nonPublicMethods );
    }

    /** Find a static method member of baseClass, for the given name.
     * @param baseClass class to query
     * @param methodName method name to find
     * @return a BshMethod wrapped Method. */
    static BshMethod staticMethodForName(Class<?> baseClass, String methodName) {
        for (Method method : baseClass.getDeclaredMethods())
            if (method.getName().equals(methodName)
                    && Modifier.isStatic(method.getModifiers())) {
                if (Capabilities.haveAccessibility())
                    method.setAccessible(true);
                else if (!Modifier.isPublic(method.getModifiers()))
                    continue;
                return new BshMethod(method, null);
            }
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
    static Object constructObject( Class clas, Object[] args )
        throws ReflectError, InvocationTargetException
    {
        if ( null == clas )
            return Primitive.NULL;
        if ( clas.isInterface() )
            throw new ReflectError(
                "Can't create instance of an interface: "+clas);

        Object obj = null;
        Class[] types = Types.getTypes(args);

        // Find the constructor.
        // (there are no inherited constructors to worry about)
        Constructor[] constructors =
            Capabilities.haveAccessibility() ?
                clas.getDeclaredConstructors() : clas.getConstructors() ;

        Interpreter.debug("Looking for most specific constructor: ", clas);
        Constructor con = findMostSpecificConstructor(types, constructors);
        if ( con == null )
            throw cantFindConstructor( clas, types );

        if ( !isPublic( con )  && Capabilities.haveAccessibility() )
            con.setAccessible(true);

        args=Primitive.unwrap( args );
        try {
            return con.newInstance( args );
        } catch(InstantiationException e) {
            throw new ReflectError("The class "+clas+" is abstract ", e);
        } catch(IllegalAccessException e) {
            throw new ReflectError(
                "We don't have permission to create an instance."
                +"Use setAccessibility(true) to enable access.", e);
        } catch(IllegalArgumentException e) {
            throw new ReflectError("The number of arguments was wrong", e);
        }
    }

    /*
        This method should parallel findMostSpecificMethod()
        The only reason it can't be combined is that Method and Constructor
        don't have a common interface for their signatures
    */
    static Constructor findMostSpecificConstructor(
        Class[] idealMatch, Constructor[] constructors)
    {
        int match = findMostSpecificConstructorIndex(idealMatch, constructors );
        return ( match == -1 ) ? null : constructors[ match ];
    }

    static int findMostSpecificConstructorIndex(
        Class[] idealMatch, Constructor[] constructors)
    {
        Class [][] candidates = new Class [ constructors.length ] [];
        for(int i=0; i< candidates.length; i++ )
            candidates[i] = constructors[i].getParameterTypes();

        return findMostSpecificSignature( idealMatch, candidates );
    }

    /**
        Find the best match for signature idealMatch.
        It is assumed that the methods array holds only valid candidates
        (e.g. method name and number of args already matched).
        This method currently does not take into account Java 5 covariant
        return types... which I think will require that we find the most
        derived return type of otherwise identical best matches.

        @see #findMostSpecificSignature(Class[], Class[][])
        @param methods the set of candidate method which differ only in the
            types of their arguments.
    */
    private static Method findMostSpecificMethod(
        Class[] idealMatch, List<Method> methods )
    {
        // copy signatures into array for findMostSpecificMethod()
        List<Class[]> candidateSigs = new ArrayList<Class[]>();
        List<Method> methodList = new ArrayList<Method>();
        for( Method method : methods ) {
            Class[] parameterTypes = method.getParameterTypes();
            methodList.add( method );
            candidateSigs.add( parameterTypes );
            if( method.isVarArgs() ) {
                Class[] candidateSig = new Class[idealMatch.length];
                int j = 0;
                for( ; j<parameterTypes.length-1; j++ ) {
                    candidateSig[j] = parameterTypes[j];
                }
                Class varType = parameterTypes[j].getComponentType();
                for( ; j<idealMatch.length; j++ ) {
                    candidateSig[j] = varType;
                }
                methodList.add( method );
                candidateSigs.add( candidateSig );
            }
        }

        int match = findMostSpecificSignature( idealMatch,
                candidateSigs.toArray(new Class[candidateSigs.size()][]) );
        return match == -1 ? null : methodList.get(match);
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
        Class [] idealMatch, Class [][] candidates )
    {
        for ( int round = Types.FIRST_ROUND_ASSIGNABLE;
              round <= Types.LAST_ROUND_ASSIGNABLE; round++ )
        {
            Class [] bestMatch = null;
            int bestMatchIndex = -1;

            for (int i=0; i < candidates.length; i++)
            {
                Class[] targetMatch = candidates[i];
                if (null != bestMatch && Types.areSignaturesEqual(targetMatch,bestMatch))
                    // overridden keep first
                    continue;

                // If idealMatch fits targetMatch and this is the first match
                // or targetMatch is more specific than the best match, make it
                // the new best match.
                if ( Types.isSignatureAssignable(
                        idealMatch, targetMatch, round )
                    && ( bestMatch == null || Types.areSignaturesEqual(idealMatch, targetMatch)
                        || ( Types.isSignatureAssignable( targetMatch, bestMatch,
                                Types.JAVA_BASE_ASSIGNABLE ) &&
                        !Types.areSignaturesEqual(idealMatch, bestMatch) )
                        )
                )
                {
                    bestMatch = targetMatch;
                    bestMatchIndex = i;
                }
            }

            if ( bestMatch != null )
                return bestMatchIndex;
        }

        return -1;
    }

    static String accessorName( String getorset, String propName ) {
        return getorset
            + String.valueOf(Character.toUpperCase(propName.charAt(0)))
            + propName.substring(1);
    }

    public static boolean hasObjectPropertyGetter(
        Class clas, String propName )
    {
        if( clas == Primitive.class )
            return false;
        String getterName = accessorName("get", propName );
        try {
            clas.getMethod( getterName, new Class [0] );
            return true;
        } catch ( NoSuchMethodException e ) { /* fall through */ }
        getterName = accessorName("is", propName );
        try {
            Method m = clas.getMethod( getterName, new Class [0] );
            return m.getReturnType() == Boolean.TYPE;
        } catch ( NoSuchMethodException e ) {
            return false;
        }
    }

    public static boolean hasObjectPropertySetter(
        Class clas, String propName )
    {
        String setterName = accessorName("set", propName );
        Method [] methods = clas.getMethods();

        // we don't know the right hand side of the assignment yet.
        // has at least one setter of the right name?
        for(Method method: methods)
            if ( method.getName().equals( setterName ) )
                return true;
        return false;
    }

    public static Object getObjectProperty(
        Object obj, String propName )
        throws UtilEvalError, ReflectError
    {
        Object[] args = new Object[] { };

        Interpreter.debug("property access: ");
        Method method = null;
        Class<?> cls = obj.getClass();
        if ( obj instanceof Class )
            cls = (Class<?>) obj;

        Exception e1=null, e2=null;
        try {
            String accessorName = accessorName( "get", propName );
            method = resolveExpectedJavaMethod(
                null/*bcm*/, cls, obj, accessorName, args, false );
        } catch ( Exception e ) {
            e1 = e;
        }
        if ( method == null )
            try {
                String accessorName = accessorName( "is", propName );
                method = resolveExpectedJavaMethod(
                    null/*bcm*/, cls, obj,
                    accessorName, args, false );
                if ( method.getReturnType() != Boolean.TYPE )
                    method = null;
            } catch ( Exception e ) {
                e2 = e;
            }
        if ( method == null )
            throw new ReflectError("Error in property getter: "
                +e1 + (e2!=null?" : "+e2:"") );

        try {
            return invokeMethod( method, obj, args );
        }
        catch(InvocationTargetException e)
        {
            throw new UtilEvalError("Property accessor threw exception: "
                +e.getTargetException(),  e.getTargetException());
        }
    }

    public static void setObjectProperty(
        Object obj, String propName, Object value)
        throws ReflectError, UtilEvalError
    {
        String accessorName = accessorName( "set", propName );
        Object[] args = new Object[] { value };

        Interpreter.debug("property access: ");
        try {
            Class<?> cls = obj.getClass();
            if (obj instanceof Class)
                cls = (Class<?>) obj;
            Method method = resolveExpectedJavaMethod(
                null/*bcm*/, cls, obj, accessorName, args, false );
            invokeMethod( method, obj, args );
        }
        catch ( InvocationTargetException e )
        {
            throw new UtilEvalError("Property accessor threw exception: "
                +e.getTargetException(), e.getTargetException());
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
        Class commandClass, Object [] args, Interpreter interpreter,
        CallStack callstack, SimpleNode callerInfo )
        throws UtilEvalError
    {
        // add interpereter and namespace to args list
        Object[] invokeArgs = new Object[args.length + 2];
        invokeArgs[0] = interpreter;
        invokeArgs[1] = callstack;
        System.arraycopy( args, 0, invokeArgs, 2, args.length );
        BshClassManager bcm = interpreter.getClassManager();
        try {
            return Reflect.invokeStaticMethod(
                bcm, commandClass, "invoke", invokeArgs, callerInfo );
        } catch ( InvocationTargetException e ) {
            throw new UtilEvalError(
                "Error in compiled command: "+e.getTargetException(), e );
        } catch ( ReflectError e ) {
            throw new UtilEvalError("Error invoking compiled command: "+e, e );
        }
    }

    private static void logInvokeMethod(String msg, Method method, Object[] args) {
        if (Interpreter.DEBUG.get()) {
            Interpreter.debug(msg, method, " with args:");
            for (int i = 0; i < args.length; i++) {
                final Object arg = args[i];
                Interpreter.debug("args[", i, "] = ", arg, " type = ", (arg == null ? "<unknown>" : arg.getClass()));
            }
        }
    }

    private static void checkFoundStaticMethod(
        Method method, boolean staticOnly, Class clas )
        throws UtilEvalError
    {
        // We're looking for a static method but found an instance method
        if ( method != null && staticOnly && !isStatic( method ) )
            throw new UtilEvalError(
                "Cannot reach instance method: "
                + StringUtil.methodString(
                    method.getName(), method.getParameterTypes() )
                + " from static context: "+ clas.getName() );
    }

    private static ReflectError cantFindConstructor(
        Class clas, Class [] types )
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
        return null != type && type != GeneratedClass.class && GeneratedClass.class.isAssignableFrom(type);
    }

    /**
     * Get the static bsh namespace field from the class.
     * @param className may be the name of clas itself or a superclass of clas.
     */
    public static This getClassStaticThis(Class clas, String className) {
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
            throw new InterpreterError("Generated class: Error getting This" + e, e);
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
            return new Modifiers(Modifiers.CLASS);
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

    private static boolean isPublic(Member member) {
        return Modifier.isPublic(member.getModifiers());
    }


    private static boolean isPublic(Class clazz) {
        return Modifier.isPublic(clazz.getModifiers());
    }


    private static boolean isStatic(Method m) {
        return Modifier.isStatic(m.getModifiers());
    }

    public static boolean isStatic(Field f) {
        return f != null && Modifier.isStatic(f.getModifiers());
    }

    public static boolean hasModifier(String name, int modifiers) {
        return Modifier.toString(modifiers).contains(name);
    }

    static void setAccessible(final Field field) {
        if ( ! field.isAccessible() && Capabilities.haveAccessibility()) {
            field.setAccessible(true);
        }
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

