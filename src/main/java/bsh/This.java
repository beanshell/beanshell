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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
    'This' is the type of bsh scripted objects.
    A 'This' object is a bsh scripted object context.  It holds a namespace
    reference and implements event listeners and various other interfaces.

    This holds a reference to the declaring interpreter for callbacks from
    outside of bsh.
*/
public final class This implements java.io.Serializable, Runnable
{
    /**
        The namespace that this This reference wraps.
    */
    final NameSpace namespace;

    /**
        This is the interpreter running when the This ref was created.
        It's used as a default interpreter for callback through the This
        where there is no current interpreter instance
        e.g. interface proxy or event call backs from outside of bsh.
    */
    transient Interpreter declaringInterpreter;

    /**
        A cache of proxy interface handlers.
        Currently just one per interface.
    */
    private Map<Integer,Object> interfaces;

    private final InvocationHandler invocationHandler = new Handler();

    /**
        getThis() is a factory for bsh.This type references.  The capabilities
        of ".this" references in bsh are version dependent up until jdk1.3.
        The version dependence was to support different default interface
        implementations.  i.e. different sets of listener interfaces which
        scripted objects were capable of implementing.  In jdk1.3 the
        reflection proxy mechanism was introduced which allowed us to
        implement arbitrary interfaces.  This is fantastic.

        A This object is a thin layer over a namespace, comprising a bsh object
        context.  We create it here only if needed for the namespace.

        Note: this method could be considered slow because of the way it
        dynamically factories objects.  However I've also done tests where
        I hard-code the factory to return JThis and see no change in the
        rough test suite time.  This references are also cached in NameSpace.
    */
    static This getThis(
        NameSpace namespace, Interpreter declaringInterpreter )
    {
        return new This( namespace, declaringInterpreter );
    }

    /**
        Get a version of this scripted object implementing the specified
        interface.
    */
    /**
        Get dynamic proxy for interface, caching those it creates.
    */
    public Object getInterface( Class clas )
    {
        return getInterface( new Class[] { clas } );
    }

    /**
        Get dynamic proxy for interface, caching those it creates.
    */
    public Object getInterface( Class [] ca )
    {
        if ( interfaces == null )
            interfaces = new HashMap<Integer,Object>();

        // Make a hash of the interface hashcodes in order to cache them
        int hash = 21;
        for(int i=0; i<ca.length; i++)
            hash *= ca[i].hashCode() + 3;
        Integer hashKey = Integer.valueOf(hash);

        Object interf = interfaces.get( hashKey );

        if ( interf == null )
        {
            ClassLoader classLoader = ca[0].getClassLoader(); // ?
            interf = Proxy.newProxyInstance(
                classLoader, ca, invocationHandler );
            interfaces.put( hashKey, interf );
        }

        return interf;
    }

    /**
        This is the invocation handler for the dynamic proxy.
        <p>

        Notes:
        Inner class for the invocation handler seems to shield this unavailable
        interface from JDK1.2 VM...

        I don't understand this.  JThis works just fine even if those
        classes aren't there (doesn't it?)  This class shouldn't be loaded
        if an XThis isn't instantiated in NameSpace.java, should it?
    */
    class Handler implements InvocationHandler, java.io.Serializable
    {
        public Object invoke( Object proxy, Method method, Object[] args )
            throws Throwable
        {
            try {
                return invokeImpl( proxy, method, args );
            } catch ( TargetError te ) {
                // Unwrap target exception.  If the interface declares that
                // it throws the ex it will be delivered.  If not it will be
                // wrapped in an UndeclaredThrowable

                // This isn't simple because unwrapping this loses all context info.
                // So rewrap is better than unwrap.  - fschmidt
                Throwable t = te.getTarget();
                Class<? extends Throwable> c = t.getClass();
                String msg = t.getMessage();
                try {
                    Throwable t2 = msg==null
                        ? c.getConstructor().newInstance()
                        : c.getConstructor(String.class).newInstance(msg)
                    ;
                    t2.initCause(te);
                    throw t2;
                } catch(NoSuchMethodException e) {
                    throw t;
                }
            } catch ( EvalError ee ) {
                // Ease debugging...
                // XThis.this refers to the enclosing class instance
                if ( Interpreter.DEBUG )
                    Interpreter.debug( "EvalError in scripted interface: "
                    + This.this.toString() + ": "+ ee );
                throw ee;
            }
        }

        public Object invokeImpl( Object proxy, Method method, Object[] args )
            throws EvalError
        {
            String methodName = method.getName();
            CallStack callstack = new CallStack( namespace );

            /*
                If equals() is not explicitly defined we must override the
                default implemented by the This object protocol for scripted
                object.  To support XThis equals() must test for equality with
                the generated proxy object, not the scripted bsh This object;
                otherwise callers from outside in Java will not see a the
                proxy object as equal to itself.
            */
            BshMethod equalsMethod = null;
            try {
                equalsMethod = namespace.getMethod(
                    "equals", new Class [] { Object.class } );
            } catch ( UtilEvalError e ) {/*leave null*/ }
            if ( methodName.equals("equals" ) && equalsMethod == null ) {
                Object obj = args[0];
                return proxy == obj;
            }

            /*
                If toString() is not explicitly defined override the default
                to show the proxy interfaces.
            */
            BshMethod toStringMethod = null;
            try {
                toStringMethod =
                    namespace.getMethod( "toString", new Class [] { } );
            } catch ( UtilEvalError e ) {/*leave null*/ }

            if ( methodName.equals("toString" ) && toStringMethod == null)
            {
                Class [] ints = proxy.getClass().getInterfaces();
                // XThis.this refers to the enclosing class instance
                StringBuilder sb = new StringBuilder(
                    This.this.toString() + "\nimplements:" );
                for(int i=0; i<ints.length; i++)
                    sb.append( " "+ ints[i].getName()
                        + ((ints.length > 1)?",":"") );
                return sb.toString();
            }

            Class [] paramTypes = method.getParameterTypes();
            return Primitive.unwrap(
                invokeMethod( methodName, Primitive.wrap(args, paramTypes) ) );
        }
    }

    This( NameSpace namespace, Interpreter declaringInterpreter ) {
        this.namespace = namespace;
        this.declaringInterpreter = declaringInterpreter;
        //initCallStack( namespace );
    }

    public NameSpace getNameSpace() {
        return namespace;
    }

    public String toString() {
        return "'this' reference to Bsh object: " + namespace;
    }

    public void run() {
        try {
            invokeMethod( "run", new Object[0] );
        } catch( EvalError e ) {
            declaringInterpreter.error(
                "Exception in runnable:" + e );
        }
    }

    /**
        Invoke specified method as from outside java code, using the
        declaring interpreter and current namespace.
        The call stack will indicate that the method is being invoked from
        outside of bsh in native java code.
        Note: you must still wrap/unwrap args/return values using
        Primitive/Primitive.unwrap() for use outside of BeanShell.
        @see bsh.Primitive
    */
    public Object invokeMethod( String name, Object [] args )
        throws EvalError
    {
        // null callstack, one will be created for us
        return invokeMethod(
            name, args, null/*declaringInterpreter*/, null, null,
            false/*declaredOnly*/ );
    }

    /**
        Invoke a method in this namespace with the specified args,
        interpreter reference, callstack, and caller info.
        <p>

        Note: If you use this method outside of the bsh package and wish to
        use variables with primitive values you will have to wrap them using
        bsh.Primitive.  Consider using This getInterface() to make a true Java
        interface for invoking your scripted methods.
        <p>

        This method also implements the default object protocol of toString(),
        hashCode() and equals() and the invoke() meta-method handling as a
        last resort.
        <p>

        Note: The invoke() meta-method will not catch the Object protocol
        methods (toString(), hashCode()...).  If you want to override them you
        have to script them directly.
        <p>

        @see bsh.This#invokeMethod(String, Object[], Interpreter, CallStack, SimpleNode, boolean)
        @param callstack if callStack is null a new CallStack will be created and
            initialized with this namespace.
        @param declaredOnly if true then only methods declared directly in the
            namespace will be visible - no inherited or imported methods will
            be visible.
        @see bsh.Primitive
    */
    /*
        invokeMethod() here is generally used by outside code to callback
        into the bsh interpreter. e.g. when we are acting as an interface
        for a scripted listener, etc.  In this case there is no real call stack
        so we make a default one starting with the special JAVACODE namespace
        and our namespace as the next.
    */
    public Object invokeMethod(
        String methodName, Object [] args,
        Interpreter interpreter, CallStack callstack, SimpleNode callerInfo,
        boolean declaredOnly  )
        throws EvalError
    {
        /*
            Wrap nulls.
            This is a bit of a cludge to address a deficiency in the class
            generator whereby it does not wrap nulls on method delegate.  See
            Class Generator.java.  If we fix that then we can remove this.
            (just have to generate the code there.)
        */
        if (args == null) {
            args = new Object[0];
        } else {
            Object[] oa = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                oa[i] = (args[i] == null ? Primitive.NULL : args[i]);
            }
            args = oa;
        }

        if ( interpreter == null )
            interpreter = declaringInterpreter;
        if ( callstack == null )
            callstack = new CallStack( namespace );
        if ( callerInfo == null )
            callerInfo = SimpleNode.JAVACODE;

        // Find the bsh method
        Class [] types = Types.getTypes( args );
        BshMethod bshMethod = null;
        try {
            bshMethod = namespace.getMethod( methodName, types, declaredOnly );
        } catch ( UtilEvalError e ) {
            // leave null
        }

        if ( bshMethod != null )
            return bshMethod.invoke( args, interpreter, callstack, callerInfo );

        /*
            No scripted method of that name.
            Implement the required part of the Object protocol:
                public int hashCode();
                public boolean equals(java.lang.Object);
                public java.lang.String toString();
            if these were not handled by scripted methods we must provide
            a default impl.
        */
        // a default toString() that shows the interfaces we implement
        if ( methodName.equals("toString") && args.length==0 )
            return toString();

        // a default hashCode()
        if ( methodName.equals("hashCode") && args.length==0 )
            return Integer.valueOf(this.hashCode());

        // a default equals() testing for equality with the This reference
        if ( methodName.equals("equals") && args.length==1 ) {
            Object obj = args[0];
            return this == obj ? Boolean.TRUE : Boolean.FALSE;
        }

        // a default clone()
        if ( methodName.equals("clone") && args.length==0 ) {
            NameSpace ns = new NameSpace(namespace,namespace.getName()+" clone");
            try {
                for( String varName : namespace.getVariableNames() ) {
                    ns.setLocalVariable(varName,namespace.getVariable(varName,false),false);
                }
                for( BshMethod method : namespace.getMethods() ) {
                    ns.setMethod(method);
                }
            } catch ( UtilEvalError e ) {
                throw e.toEvalError( SimpleNode.JAVACODE, callstack );
            }
            return ns.getThis(declaringInterpreter);
        }

        // Look for a default invoke() handler method in the namespace
        // Note: this code duplicates that in NameSpace getCommand()
        // is that ok?
        try {
            bshMethod = namespace.getMethod(
                "invoke", new Class [] { null, null } );
        } catch ( UtilEvalError e ) { /*leave null*/ }

        // Call script "invoke( String methodName, Object [] args );
        if ( bshMethod != null )
            return bshMethod.invoke( new Object [] { methodName, args },
                interpreter, callstack, callerInfo );

        throw new EvalError("Method " +
            StringUtil.methodString( methodName, types ) +
            " not found in bsh scripted object: "+ namespace.getName(),
            callerInfo, callstack );
    }

    /**
        Bind a This reference to a parent's namespace with the specified
        declaring interpreter.  Also re-init the callstack.  It's necessary
        to bind a This reference before it can be used after deserialization.
        This is used by the bsh load() command.
        <p>

        This is a static utility method because it's used by a bsh command
        bind() and the interpreter doesn't currently allow access to direct
        methods of This objects (small hack)
    */
    public static void bind(
        This ths, NameSpace namespace, Interpreter declaringInterpreter )
    {
        ths.namespace.setParent( namespace );
        ths.declaringInterpreter = declaringInterpreter;
    }

    /**
        Allow invocations of these method names on This type objects.
        Don't give bsh.This a chance to override their behavior.
        <p>

        If the method is passed here the invocation will actually happen on
        the bsh.This object via the regular reflective method invocation
        mechanism.  If not, then the method is evaluated by bsh.This itself
        as a scripted method call.
    */
    static boolean isExposedThisMethod( String name )
    {
        return
            name.equals("getClass")
            || name.equals("invokeMethod")
            || name.equals("getInterface")
            // These are necessary to let us test synchronization from scripts
            || name.equals("wait")
            || name.equals("notify")
            || name.equals("notifyAll");
    }

}

