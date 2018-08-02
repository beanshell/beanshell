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

import static bsh.ClassGenerator.ClassNodeFilter.CLASSINSTANCE;
import static bsh.ClassGenerator.ClassNodeFilter.CLASSSTATIC;
import static bsh.ClassGeneratorUtil.DEFAULTCONSTRUCTOR;
import static bsh.This.Keys.BSHCONSTRUCTORS;
import static bsh.This.Keys.BSHINIT;
import static bsh.This.Keys.BSHTHIS;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
    'This' is the type of bsh scripted objects.
    A 'This' object is a bsh scripted object context.  It holds a namespace
    reference and implements event listeners and various other interfaces.

    This holds a reference to the declaring interpreter for callbacks from
    outside of bsh.
*/
public final class This implements java.io.Serializable, Runnable
{
    enum Keys {
        /** The name of the static field holding the reference to the bsh
         * static This (the callback namespace for static methods) */
        BSHSTATIC { public String toString() { return "_bshStatic"; } },
        /** The name of the instance field holding the reference to the bsh
         * instance This (the callback namespace for instance methods) */
        BSHTHIS { public String toString() { return "_bshThis"; } },
        /** The prefix for the name of the super delegate methods. e.g.
         * _bshSuperfoo() is equivalent to super.foo() */
        BSHSUPER { public String toString() { return "_bshSuper"; } },
        /** The bsh static namespace variable name of the instance initializer */
        BSHINIT { public String toString() { return "_bshInstanceInitializer"; } },
        /** The bsh static namespace variable that holds the constructor methods */
        BSHCONSTRUCTORS { public String toString() { return "_bshConstructors"; } },
        /** The bsh static namespace variable that holds the class modifiers */
        BSHCLASSMODIFIERS { public String toString() { return "_bshClassModifiers"; } }
    }


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
    public static final Map<String,NameSpace> contextStore = new ConcurrentHashMap<>();
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
            interf = Proxy.newProxyInstance(
                ca[0].getClassLoader(), ca, invocationHandler );
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
                Interpreter.debug( "EvalError in scripted interface: ",
                    This.this.toString(), ": ", ee );
                throw ee;
            }
        }

        public Object invokeImpl( Object proxy, Method method, Object[] args )
            throws EvalError
        {
            String methodName = method.getName();

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
                        + ( ints.length > 1 ? "," :"" ) );
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
        Simplified method for class generated method stubs.
        Uses This.namespace and declaringInterpreter and retains
        callerInfo from namespace.getNode().
        Also unwraps Primitives for convenience.
     */
    public Object invokeMethod(
            String methodName, Object [] args,
            boolean declaredOnly  )
            throws EvalError
    {
        CallStack callstack = new CallStack(namespace);
        SimpleNode node = namespace.getNode();
        namespace.setNode(null);
        try {
            return Primitive.unwrap(invokeMethod(
                    methodName, args, declaringInterpreter,
                    callstack, node, declaredOnly));
        } catch (Exception e) {
            throw new EvalError(e.getMessage(), node, callstack, e);
        }
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
        if (args == null)
            args = new Object[0];

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

    /** Delegate method to return enum values array.
     * @return array of enum values */
    public Object[] enumValues() {
        return Reflect.getEnumConstants(getNameSpace().classStatic);
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


    /**
     * This method is called by the **generated class** during construction.
     *
     * Evaluate the arguments (if any) for the constructor specified by
     * the constructor index. Return the ConstructorArgs object which
     * contains the actual arguments to the alternate constructor and also the
     * index of that constructor for the constructor switch.
     *
     * @param consArgs the arguments to the constructor. These are necessary in
     * the evaluation of the alt constructor args. e.g. Foo(a) { super(a); }
     * @return the ConstructorArgs object containing a constructor selector
     * and evaluated arguments for the alternate constructor
     */
    public static ConstructorArgs getConstructorArgs(String superClassName, This classStaticThis, Object[] consArgs, int index) {
        if (classStaticThis == null)
            throw new InterpreterError("Unititialized class: no static");

        DelayedEvalBshMethod[] constructors;
        try {
            Object cons = classStaticThis.getNameSpace().getVariable(BSHCONSTRUCTORS.toString());
            if (cons == Primitive.VOID)
                throw new InterpreterError("Unable to find constructors array in class");
            constructors = (DelayedEvalBshMethod[]) cons;
        } catch (Exception e) {
            throw new InterpreterError("Unable to get instance initializers: " + e, e);
        }

        if (index == DEFAULTCONSTRUCTOR) // auto-gen default constructor
            return ConstructorArgs.DEFAULT;
        // use default super constructor

        DelayedEvalBshMethod constructor = constructors[index];

        if (constructor.methodBody.jjtGetNumChildren() == 0)
            return ConstructorArgs.DEFAULT;
        // use default super constructor

        // Determine if the constructor calls this() or super()
        String altConstructor = null;
        BSHArguments argsNode = null;
        SimpleNode firstStatement = (SimpleNode) constructor.methodBody.jjtGetChild(0);
        if (firstStatement instanceof BSHAssignment)
            firstStatement = (SimpleNode) firstStatement.jjtGetChild(0);
        if (firstStatement instanceof BSHPrimaryExpression)
            firstStatement = (SimpleNode) firstStatement.jjtGetChild(0);

        if (firstStatement instanceof BSHMethodInvocation) {
            BSHMethodInvocation methodNode = (BSHMethodInvocation) firstStatement;
            BSHAmbiguousName methodName = methodNode.getNameNode();
            if (methodName.text.equals("super") || methodName.text.equals("this")) {
                altConstructor = methodName.text;
                argsNode = methodNode.getArgsNode();
            }
        }

        if (altConstructor == null)
            return ConstructorArgs.DEFAULT;
        // use default super constructor

        // Make a tmp namespace to hold the original constructor args for
        // use in eval of the parameters node
        NameSpace consArgsNameSpace = new NameSpace(classStaticThis.getNameSpace(), "consArgs");
        String[] consArgNames = constructor.getParameterNames();
        Class[] consArgTypes = constructor.getParameterTypes();
        for (int i = 0; i < consArgs.length; i++) try {
            consArgsNameSpace.setTypedVariable(consArgNames[i], consArgTypes[i], consArgs[i], null/*modifiers*/);
        } catch (UtilEvalError e) {
            throw new InterpreterError("err setting local cons arg:" + e, e);
        }

        // evaluate the args

        CallStack callstack = new CallStack();
        callstack.push(consArgsNameSpace);
        Object[] args = null;
        Interpreter interpreter = classStaticThis.declaringInterpreter;

        if ( null != argsNode ) try {
            args = argsNode.getArguments(callstack, interpreter);
        } catch (EvalError e) {
            throw new InterpreterError("Error evaluating constructor args: " + e, e);
        }

        Class[] argTypes = Types.getTypes(args);
        args = Primitive.unwrap(args);
        Class superClass = interpreter.getClassManager().classForName(superClassName);
        if (superClass == null)
            throw new InterpreterError("can't find superclass: " + superClassName);

        Constructor[] superCons = superClass.getDeclaredConstructors();

        // find the matching super() constructor for the args
        if (altConstructor.equals("super")) {
            int i = Reflect.findMostSpecificConstructorIndex(argTypes, superCons);
            if (i == -1)
                throw new InterpreterError("can't find constructor for args!");
            return new ConstructorArgs(i, args);
        }

        // find the matching this() constructor for the args
        Class[][] candidates = new Class[constructors.length][];
        for (int i = 0; i < candidates.length; i++)
            candidates[i] = constructors[i].getParameterTypes();
        int i = Reflect.findMostSpecificSignature(argTypes, candidates);
        if (i == -1)
            throw new InterpreterError("can't find constructor for args 2!");
        // this() constructors come after super constructors in the table

        int selector = i + superCons.length;
        int ourSelector = index + superCons.length;

        // Are we choosing ourselves recursively through a this() reference?
        if (selector == ourSelector)
            throw new InterpreterError("Recusive constructor call.");

        return new ConstructorArgs(selector, args);
    }

    /**
     * Initialize an instance of the class.
     * This method is called from the generated class constructor to evaluate
     * the instance initializer and scripted constructor in the instance
     * namespace.
     */
    public static void initInstance(GeneratedClass instance, String className, Object[] args) {
        try {
            This instanceThis = initClassInstanceThis(instance, className);
            NameSpace instanceNameSpace = instanceThis.getNameSpace();

            // if this is a super constructor we need to initialize the parent's instance This
            List<String> parentNames = new ArrayList<>();
            Class<?> clas = instance.getClass();
            while ( null != clas && !clas.getSimpleName().equals(className) ) {
                parentNames.add(0, clas.getSimpleName());
                clas = clas.getSuperclass();
            }
            parentNames.forEach(name -> initClassInstanceThis(instance, name));

            if ( instanceNameSpace.isEnum
                    && This.CONTEXT_ARGS.get().containsKey((Enum<?>) instance) )
                args = This.CONTEXT_ARGS.get().remove((Enum<?>) instance);

            // Find the constructor (now in the instance namespace)
            BshMethod constructor = instanceNameSpace.getMethod(Types.getBaseName(className), Types.getTypes(args), true/*declaredOnly*/);

            // if args, we must have constructor
            if (args.length > 0 && constructor == null)
                throw new InterpreterError("Can't find constructor: " + StringUtil.methodString(className, args) );

            // Evaluate the constructor
            if (constructor != null)
                constructor.invoke(args, instanceThis.declaringInterpreter);

            // Validate that final variables were set
            for (Variable var : Reflect.getVariables(instance))
                var.validateFinalIsSet(false);
        } catch (Exception e) {
            if (e instanceof TargetError)
                e = (Exception) ((TargetError) e).getTarget();
            if (e instanceof InvocationTargetException)
                e = (Exception) ((InvocationTargetException) e).getTargetException();
            throw new InterpreterError("Error in class instance initialization: " + e, e);
        }
    }


    private static final ThreadLocal<NameSpace> CONTEXT_NAMESPACE = new ThreadLocal<>();
    private static final ThreadLocal<Interpreter> CONTEXT_INTERPRETER = new ThreadLocal<>();
    static final ThreadLocal<Map<Enum<?>, Object[]>> CONTEXT_ARGS = ThreadLocal.withInitial(()->new HashMap<>());


    /**
     * Register actual context, used by generated class constructor, which calls
     * {@link #initInstance(GeneratedClass, String, Object[])}.
     */
    static void registerConstructorContext(CallStack callstack, Interpreter interpreter) {
        if (callstack != null)
            CONTEXT_NAMESPACE.set(callstack.top());
        else
            CONTEXT_NAMESPACE.remove();
        if (interpreter != null)
            CONTEXT_INTERPRETER.set(interpreter);
        else
            CONTEXT_INTERPRETER.remove();
    }

    /** Initialize the class instance This field and evaluate instance init block.
     * @param instance the instance this from class <init>
     * @param className the name of instance relative
     * @return instance This */
    private static This initClassInstanceThis(Object instance, String className) {
        This instanceThis = Reflect.getClassInstanceThis(instance, className);
        if (null == instanceThis) {
            // Create the instance 'This' namespace, set it on the object
            // instance and invoke the instance initializer

            // Get the static This reference from the proto-instance
            This classStaticThis = Reflect.getClassStaticThis(instance.getClass(), className);

            // Create the instance namespace
            NameSpace instanceNameSpace = classStaticThis.getNameSpace().copy();
            if (CONTEXT_NAMESPACE.get() != null)
                instanceNameSpace.setParent(CONTEXT_NAMESPACE.get());

            // Set the instance This reference on the instance
            if (null != CONTEXT_INTERPRETER.get())
                instanceThis = instanceNameSpace.getThis(CONTEXT_INTERPRETER.get());
            else
                instanceThis = instanceNameSpace.getThis(classStaticThis.declaringInterpreter);
            try {
                LHS lhs = Reflect.getLHSObjectField(instance, BSHTHIS + className);
                lhs.assign(instanceThis, false/*strict*/);
            } catch (Exception e) {
                throw new InterpreterError("Error in class gen setup: " + e, e);
            }

            // Give the instance space its object import
            instanceNameSpace.setClassInstance(instance);

            // Get the instance initializer block from the static This
            BSHBlock instanceInitBlock;
            try {
                instanceInitBlock = (BSHBlock) classStaticThis.getNameSpace().getVariable(BSHINIT.toString());
            } catch (Exception e) {
                throw new InterpreterError("unable to get instance initializer: " + e, e);
            }

            // evaluate the instance portion of the block in it
            try { // Evaluate the initializer block
                instanceInitBlock.evalBlock(new CallStack(instanceNameSpace), instanceThis.declaringInterpreter, true/*override*/, CLASSINSTANCE);
            } catch (Exception e) {
                throw new InterpreterError("Error in class instance This initialization: " + e, e);
            }
        }
        return instanceThis;
    }

    /** Lazy initialize static context implementation.
     * Called from <clinit> after static This was populated we will now
     * proceed to evaluate the static block node.
     * @param genClass the generated class.
     * @param className name of the class.
     * @throws UtilEvalError combined exceptions. */
    public static void initStatic(Class<?> genClass) throws UtilEvalError {
        String className = genClass.getSimpleName();
        try {
            This staticThis = Reflect.getClassStaticThis(genClass, className);
            NameSpace classStaticNameSpace = staticThis.getNameSpace();
            Interpreter interpreter = staticThis.declaringInterpreter;

            if (null == interpreter)
                throw new UtilEvalError("No namespace or interpreter for statitc This."
                        +" Start interpreter for class not implemented yet.");
                //startInterpreterForClass(genClass); ???

            BSHBlock block = (BSHBlock) classStaticNameSpace.getVariable(BSHINIT.toString());
            CallStack callstack = new CallStack(classStaticNameSpace);

            // evaluate the static portion of the block in the static space
            block.evalBlock(callstack, interpreter, true/*override*/, CLASSSTATIC);

            // Validate that static final variables were set
            for (Variable var : Reflect.getVariables(classStaticNameSpace))
                var.validateFinalIsSet(true);
        } catch (Exception e) {
            throw new UtilEvalError("Exception in static init block <clinit> for class "
                    + className + ". With message: " + e.getMessage(), e);
        }
    }

    /** Pull provider for class static This.
     * Called from <clinit> to initialize class BSHSTATIC.
     * @param uuid the class unique id.
     * @return This from static namespace. */
    public static This pullBshStatic(String uuid) {
        if (contextStore.containsKey(uuid))
            return contextStore.remove(uuid).getThis(null);
        else
            // we lost the context, provide empty
            // container for static final field
            return This.getThis(null, null);
    }

    /**
     * A ConstructorArgs object holds evaluated arguments for a constructor
     * call as well as the index of a possible alternate selector to invoke.
     * This object is used by the constructor switch.
     * @see #generateConstructor( int , String [] , int , ClassWriter)
     */
    public static class ConstructorArgs {

        /**
         * A ConstructorArgs which calls the default constructor
         */
        public static final ConstructorArgs DEFAULT = new ConstructorArgs();

        public int selector = DEFAULTCONSTRUCTOR;
        Object[] args;
        int arg;

        /**
         * The index of the constructor to call.
         */

        ConstructorArgs() { }

        ConstructorArgs(int selector, Object[] args) {
            this.selector = selector;
            this.args = args;
        }

        Object next() {
            return args[arg++];
        }

        public boolean getBoolean() {
            return (Boolean) next();
        }

        public byte getByte() {
            return ((Number) next()).byteValue();
        }

        public char getChar() {
            return (Character) next();
        }

        public short getShort() {
            return ((Number) next()).shortValue();
        }

        public int getInt() {
            return ((Number) next()).intValue();
        }

        public long getLong() {
            return ((Number) next()).longValue();
        }

        public double getDouble() {
            return ((Number) next()).doubleValue();
        }

        public float getFloat() {
            return ((Number) next()).floatValue();
        }

        public Object getObject() {
            return next();
        }
    }
}

