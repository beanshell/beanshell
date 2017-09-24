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
 * What's in a name? I'll tell you...
 * Name() is a somewhat ambiguous thing in the grammar and so is this.
 * <p>
 *
 * This class is a name resolver. It holds a possibly ambiguous dot
 * separated name and reference to a namespace in which it allegedly lives.
 * It provides methods that attempt to resolve the name to various types of
 * entities: e.g. an Object, a Class, a declared scripted BeanShell method.
 * <p>
 *
 * Name objects are created by the factory method NameSpace getNameResolver(),
 * which caches them subject to a class namespace change. This means that
 * we can cache information about various types of resolution here.
 * Currently very little if any information is cached. However with a future
 * "optimize" setting that defeats certain dynamic behavior we might be able
 * to cache quite a bit.
 *
 * <strong>Implementation notes</strong>
 * <pre>
 * Thread safety: all of the work methods in this class must be synchronized
 * because they share the internal intermediate evaluation state.
 * Note about invokeMethod(): We could simply use resolveMethod and return
 * the MethodInvoker (BshMethod or JavaMethod) however there is no easy way
 * for the AST (BSHMehodInvocation) to use this as it doesn't have type
 * information about the target to resolve overloaded methods.
 * (In Java, overloaded methods are resolved at compile time... here they
 * are, of necessity, dynamic). So it would have to do what we do here
 * and cache by signature. We now do that for the client in Reflect.java.
 * Note on this.caller resolution:
 * Although references like these do work:
 * this.caller.caller.caller... // works
 * the equivalent using successive calls:
 * // does *not* work
 * for(caller=this.caller; caller != null; caller = caller.caller);
 * is prohibited by the restriction that you can only call .caller on a
 * literal this or caller reference. The effect is that magic caller
 * reference only works through the current 'this' reference.
 * The real explanation is that This referernces do not really know anything
 * about their depth on the call stack. It might even be hard to define
 * such a thing...
 * For those purposes we provide :
 * this.callstack
 * </pre>
 */
class Name implements java.io.Serializable {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;
    /** The namespace.
     * These do not change during evaluation
     */
    public NameSpace namespace;
    /** The value. */
    String value = null;
    // ---------------------------------------------------------
    // The following instance variables mutate during evaluation and should
    // be reset by the reset() method where necessary
    // For evaluation
    /** Remaining text to evaluate. */
    private String evalName;
    /**
     * The last part of the name evaluated. This is really only used for
     * this, caller, and super resolution.
     */
    private String lastEvalName;
    /** The finished. */
    private static String FINISHED = null; // null evalname and we're finished
    /** The eval base object. */
    private Object evalBaseObject; // base object for current eval
    /** The callstack depth. */
    private int callstackDepth; // number of times eval hit 'this.caller'
    //
    // End mutable instance variables.
    // ---------------------------------------------------------
    // Begin Cached result structures
    // These are optimizations
    // Note: it's ok to cache class resolution here because when the class
    // space changes the namespace will discard cached names.
    /** The result is a class. */
    Class asClass;
    /** The result is a static method call on the following class. */
    Class classOfStaticMethod;

    /**
     * Reset.
     */
    // End Cached result structures
    private void reset() {
        this.evalName = this.value;
        this.evalBaseObject = null;
        this.callstackDepth = 0;
    }

    /**
     * This constructor should *not* be used in general.
     * Use NameSpace getNameResolver() which supports caching.
     *
     * @param namespace
     *            the namespace
     * @param s
     *            the s
     * @see NameSpace getNameResolver().
     */
    // I wish I could make this "friendly" to only NameSpace
    Name(final NameSpace namespace, final String s) {
        this.namespace = namespace;
        this.value = s;
    }

    /**
     * Resolve possibly complex name to an object value.
     *
     * Throws EvalError on various failures.
     * A null object value is indicated by a Primitive.NULL.
     * A return type of Primitive.VOID comes from attempting to access
     * an undefined variable.
     *
     * Some cases:
     * myVariable
     * myVariable.foo
     * myVariable.foo.bar
     * java.awt.GridBagConstraints.BOTH
     * my.package.stuff.MyClass.someField.someField...
     *
     * Interpreter reference is necessary to allow resolution of
     * "this.interpreter" magic field.
     * CallStack reference is necessary to allow resolution of
     * "this.caller" magic field.
     * "this.callstack" magic field.
     *
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     * @return the object
     * @throws UtilEvalError
     *             the util eval error
     */
    public Object toObject(final CallStack callstack,
            final Interpreter interpreter) throws UtilEvalError {
        return this.toObject(callstack, interpreter, false);
    }

    /**
     * To object.
     *
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     * @param forceClass
     *            if true then resolution will only produce a class.
     *            This is necessary to disambiguate in cases where the grammar
     *            knows
     *            that we want a class; where in general the var path may be
     *            taken.
     * @return the object
     * @throws UtilEvalError
     *             the util eval error
     * @see toObject()
     */
    public synchronized Object toObject(final CallStack callstack,
            final Interpreter interpreter, final boolean forceClass)
            throws UtilEvalError {
        this.reset();
        Object obj = null;
        while (this.evalName != null)
            obj = this.consumeNextObjectField(callstack, interpreter,
                    forceClass, false/* autoalloc */);
        if (obj == null)
            throw new InterpreterError("null value in toObject()");
        return obj;
    }

    /**
     * Complete round.
     *
     * @param lastEvalName
     *            the last eval name
     * @param nextEvalName
     *            the next eval name
     * @param returnObject
     *            the return object
     * @return the object
     */
    private Object completeRound(final String lastEvalName,
            final String nextEvalName, final Object returnObject) {
        if (returnObject == null)
            throw new InterpreterError("lastEvalName = " + lastEvalName);
        this.lastEvalName = lastEvalName;
        this.evalName = nextEvalName;
        this.evalBaseObject = returnObject;
        return returnObject;
    }

    /**
     * Get the next object by consuming one or more components of evalName.
     * Often this consumes just one component, but if the name is a classname
     * it will consume all of the components necessary to make the class
     * identifier.
     *
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     * @param forceClass
     *            the force class
     * @param autoAllocateThis
     *            the auto allocate this
     * @return the object
     * @throws UtilEvalError
     *             the util eval error
     */
    private Object consumeNextObjectField(final CallStack callstack,
            final Interpreter interpreter, final boolean forceClass,
            final boolean autoAllocateThis) throws UtilEvalError {
        /*
         * Is it a simple variable name?
         * Doing this first gives the correct Java precedence for vars
         * vs. imported class names (at least in the simple case - see
         * tests/precedence1.bsh). It should also speed things up a bit.
         */
        if (this.evalBaseObject == null && !isCompound(this.evalName)
                && !forceClass) {
            final Object obj = this.resolveThisFieldReference(callstack,
                    this.namespace, interpreter, this.evalName, false);
            if (obj != Primitive.VOID)
                return this.completeRound(this.evalName, FINISHED, obj);
        }
        /*
         * Is it a bsh script variable reference?
         * If we're just starting the eval of name (no base object)
         * or we're evaluating relative to a This type reference check.
         */
        final String varName = prefix(this.evalName, 1);
        if ((this.evalBaseObject == null || this.evalBaseObject instanceof This)
                && !forceClass) {
            if (Interpreter.DEBUG)
                Interpreter.debug("trying to resolve variable: " + varName);
            Object obj;
            // switch namespace and special var visibility
            if (this.evalBaseObject == null)
                obj = this.resolveThisFieldReference(callstack, this.namespace,
                        interpreter, varName, false);
            else
                obj = this.resolveThisFieldReference(callstack,
                        ((This) this.evalBaseObject).namespace, interpreter,
                        varName, true);
            if (obj != Primitive.VOID) {
                // Resolved the variable
                if (Interpreter.DEBUG)
                    Interpreter.debug("resolved variable: " + varName
                            + " in namespace: " + this.namespace);
                return this.completeRound(varName, suffix(this.evalName), obj);
            }
        }
        /*
         * Is it a class name?
         * If we're just starting eval of name try to make it, else fail.
         */
        if (this.evalBaseObject == null) {
            if (Interpreter.DEBUG)
                Interpreter.debug("trying class: " + this.evalName);
            /*
             * Keep adding parts until we have a class
             */
            Class clas = null;
            int i = 1;
            String className = null;
            for (; i <= countParts(this.evalName); i++) {
                className = prefix(this.evalName, i);
                if ((clas = this.namespace.getClass(className)) != null)
                    break;
            }
            if (clas != null)
                return this.completeRound(className,
                        suffix(this.evalName, countParts(this.evalName) - i),
                        new ClassIdentifier(clas));
            // not a class (or variable per above)
            if (Interpreter.DEBUG)
                Interpreter.debug(
                        "not a class, trying var prefix " + this.evalName);
        }
        // No variable or class found in 'this' type ref.
        // if autoAllocateThis then create one; a child 'this'.
        if ((this.evalBaseObject == null || this.evalBaseObject instanceof This)
                && !forceClass && autoAllocateThis) {
            final NameSpace targetNameSpace = this.evalBaseObject == null
                    ? this.namespace
                    : ((This) this.evalBaseObject).namespace;
            final Object obj = new NameSpace(targetNameSpace,
                    "auto: " + varName).getThis(interpreter);
            targetNameSpace.setVariable(varName, obj, false,
                    this.evalBaseObject == null);
            return this.completeRound(varName, suffix(this.evalName), obj);
        }
        /*
         * If we didn't find a class or variable name (or prefix) above
         * there are two possibilities:
         * - If we are a simple name then we can pass as a void variable
         * reference.
         * - If we are compound then we must fail at this point.
         */
        if (this.evalBaseObject == null)
            if (!isCompound(this.evalName))
                return this.completeRound(this.evalName, FINISHED,
                        Primitive.VOID);
            else
                throw new UtilEvalError(
                        "Class or variable not found: " + this.evalName);
        /*
         * --------------------------------------------------------
         * After this point we're definitely evaluating relative to
         * a base object.
         * --------------------------------------------------------
         *
         * Do some basic validity checks.
         */
        if (this.evalBaseObject == Primitive.NULL) // previous round produced
                                                   // null
            throw new UtilTargetError(new NullPointerException(
                    "Null Pointer while evaluating: " + this.value));
        if (this.evalBaseObject == Primitive.VOID) // previous round produced
                                                   // void
            throw new UtilEvalError(
                    "Undefined variable or class name while evaluating: "
                            + this.value);
        if (this.evalBaseObject instanceof Primitive)
            throw new UtilEvalError("Can't treat primitive like an object. "
                    + "Error while evaluating: " + this.value);
        /*
         * Resolve relative to a class type
         * static field, inner class, ?
         */
        if (this.evalBaseObject instanceof ClassIdentifier) {
            final Class clas = ((ClassIdentifier) this.evalBaseObject)
                    .getTargetClass();
            final String field = prefix(this.evalName, 1);
            // Class qualified 'this' reference from inner class.
            // e.g. 'MyOuterClass.this'
            if (field.equals("this")) {
                // find the enclosing class instance space of the class name
                NameSpace ns = this.namespace;
                while (ns != null) {
                    // getClassInstance() throws exception if not there
                    if (ns.classInstance != null
                            && ns.classInstance.getClass() == clas)
                        return this.completeRound(field, suffix(this.evalName),
                                ns.classInstance);
                    ns = ns.getParent();
                }
                throw new UtilEvalError(
                        "Can't find enclosing 'this' instance of class: "
                                + clas);
            }
            Object obj = null;
            // static field?
            try {
                if (Interpreter.DEBUG)
                    Interpreter
                            .debug("Name call to getStaticFieldValue, class: "
                                    + clas + ", field:" + field);
                obj = Reflect.getStaticFieldValue(clas, field);
            } catch (final ReflectError e) {
                if (Interpreter.DEBUG)
                    Interpreter.debug("field reflect error: " + e);
            }
            // inner class?
            if (obj == null) {
                final String iclass = clas.getName() + "$" + field;
                final Class c = this.namespace.getClass(iclass);
                if (c != null)
                    obj = new ClassIdentifier(c);
            }
            if (obj == null)
                throw new UtilEvalError("No static field or inner class: "
                        + field + " of " + clas);
            return this.completeRound(field, suffix(this.evalName), obj);
        }
        /*
         * If we've fallen through here we are no longer resolving to
         * a class type.
         */
        if (forceClass)
            throw new UtilEvalError(
                    this.value + " does not resolve to a class name.");
        /*
         * Some kind of field access?
         */
        final String field = prefix(this.evalName, 1);
        // length access on array?
        if (field.equals("length")
                && this.evalBaseObject.getClass().isArray()) {
            final Object obj = new Primitive(
                    Array.getLength(this.evalBaseObject));
            return this.completeRound(field, suffix(this.evalName), obj);
        }
        // Check for field on object
        // Note: could eliminate throwing the exception somehow
        try {
            final Object obj = Reflect.getObjectFieldValue(this.evalBaseObject,
                    field);
            return this.completeRound(field, suffix(this.evalName), obj);
        } catch (final ReflectError e) { /* not a field */ }
        // if we get here we have failed
        throw new UtilEvalError("Cannot access field: " + field
                + ", on object: " + this.evalBaseObject);
    }

    /**
     * Resolve a variable relative to a This reference.
     *
     * This is the general variable resolution method, accommodating special
     * fields from the This context. Together the namespace and interpreter
     * comprise the This context. The callstack, if available allows for the
     * this.caller construct.
     * Optionally interpret special "magic" field names: e.g. interpreter.
     * <p/>
     *
     * @param callstack
     *            may be null, but this is only legitimate in special
     *            cases where we are sure resolution will not involve
     *            this.caller.
     * @param thisNameSpace
     *            the this name space
     * @param interpreter
     *            the interpreter
     * @param varName
     *            the var name
     * @param specialFieldsVisible
     *            the special fields visible
     * @return the object
     * @throws UtilEvalError
     *             the util eval error
     */
    Object resolveThisFieldReference(final CallStack callstack,
            NameSpace thisNameSpace, final Interpreter interpreter,
            final String varName, final boolean specialFieldsVisible)
            throws UtilEvalError {
        if (varName.equals("this")) {
            /*
             * Somewhat of a hack. If the special fields are visible (we're
             * operating relative to a 'this' type already) dissallow further
             * .this references to prevent user from skipping to things like
             * super.this.caller
             */
            if (specialFieldsVisible)
                throw new UtilEvalError("Redundant to call .this on This type");
            // Allow getThis() to work through BlockNameSpace to the method
            // namespace
            // XXX re-eval this... do we need it?
            final This ths = thisNameSpace.getThis(interpreter);
            thisNameSpace = ths.getNameSpace();
            Object result = ths;
            final NameSpace classNameSpace = getClassNameSpace(thisNameSpace);
            if (classNameSpace != null)
                if (isCompound(this.evalName))
                    result = classNameSpace.getThis(interpreter);
                else
                    result = classNameSpace.getClassInstance();
            return result;
        }
        /*
         * Some duplication for "super". See notes for "this" above
         * If we're in an enclsing class instance and have a superclass
         * instance our super is the superclass instance.
         */
        if (varName.equals("super")) {
            // if (specialFieldsVisible)
            // throw new UtilEvalError("Redundant to call .this on This type");
            // Allow getSuper() to through BlockNameSpace to the method's super
            This ths = thisNameSpace.getSuper(interpreter);
            thisNameSpace = ths.getNameSpace();
            // super is now the closure's super or class instance
            // XXXX re-evaluate this
            // can getSuper work by itself now?
            // If we're a class instance and the parent is also a class instance
            // then super means our parent.
            if (thisNameSpace.getParent() != null
                    && thisNameSpace.getParent().isClass)
                ths = thisNameSpace.getParent().getThis(interpreter);
            return ths;
        }
        Object obj = null;
        if (varName.equals("global"))
            obj = thisNameSpace.getGlobal(interpreter);
        if (obj == null && specialFieldsVisible)
            if (varName.equals("namespace"))
                obj = thisNameSpace;
            else if (varName.equals("variables"))
                obj = thisNameSpace.getVariableNames();
            else if (varName.equals("methods"))
                obj = thisNameSpace.getMethodNames();
            else if (varName.equals("interpreter"))
                if (this.lastEvalName.equals("this"))
                    obj = interpreter;
                else
                    throw new UtilEvalError(
                            "Can only call .interpreter on literal 'this'");
        if (obj == null && specialFieldsVisible && varName.equals("caller")) {
            if (this.lastEvalName.equals("this")
                    || this.lastEvalName.equals("caller")) {
                // get the previous context (see notes for this class)
                if (callstack == null)
                    throw new InterpreterError("no callstack");
                obj = callstack.get(++this.callstackDepth).getThis(interpreter);
            } else
                throw new UtilEvalError(
                        "Can only call .caller on literal 'this' or literal '.caller'");
            // early return
            return obj;
        }
        if (obj == null && specialFieldsVisible && varName.equals("callstack"))
            if (this.lastEvalName.equals("this")) {
                // get the previous context (see notes for this class)
                if (callstack == null)
                    throw new InterpreterError("no callstack");
                obj = callstack;
            } else
                throw new UtilEvalError(
                        "Can only call .callstack on literal 'this'");
        if (obj == null)
            obj = thisNameSpace.getVariable(varName,
                    this.evalBaseObject == null);
        if (obj == null)
            throw new InterpreterError("null this field ref:" + varName);
        return obj;
    }

    /**
     * Gets the class name space.
     *
     * @param thisNameSpace
     *            the this name space
     * @return the enclosing class body namespace or null if not in a class.
     */
    static NameSpace getClassNameSpace(final NameSpace thisNameSpace) {
        // is a class instance
        // if (thisNameSpace.classInstance != null)
        if (thisNameSpace.isClass)
            return thisNameSpace;
        if (thisNameSpace.isMethod && thisNameSpace.getParent() != null
        // && thisNameSpace.getParent().classInstance != null
                && thisNameSpace.getParent().isClass)
            return thisNameSpace.getParent();
        return null;
    }

    /**
     * Check the cache, else use toObject() to try to resolve to a class
     * identifier.
     *
     * @return the class
     * @throws ClassNotFoundException
     *             on class not found.
     * @throws UtilEvalError
     *             the util eval error
     */
    public synchronized Class toClass()
            throws ClassNotFoundException, UtilEvalError {
        if (this.asClass != null)
            return this.asClass;
        this.reset();
        // "var" means untyped, return null class
        if (this.evalName.equals("var"))
            return this.asClass = null;
        /* Try straightforward class name first */
        Class clas = this.namespace.getClass(this.evalName);
        if (clas == null) {
            /*
             * Try toObject() which knows how to work through inner classes
             * and see what we end up with
             */
            Object obj = null;
            try {
                // Null interpreter and callstack references.
                // class only resolution should not require them.
                obj = this.toObject(null, null, true);
            } catch (final UtilEvalError e) {} // couldn't resolve it
            if (obj instanceof ClassIdentifier)
                clas = ((ClassIdentifier) obj).getTargetClass();
        }
        if (clas == null)
            throw new ClassNotFoundException(
                    "Class: " + this.value + " not found in namespace");
        this.asClass = clas;
        return this.asClass;
    }

    /**
     * To LHS.
     *
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     * @return the lhs
     * @throws UtilEvalError
     *             the util eval error
     *
    */
    public synchronized LHS toLHS(final CallStack callstack,
            final Interpreter interpreter) throws UtilEvalError {
        // Should clean this up to a single return statement
        this.reset();
        LHS lhs;
        // Simple (non-compound) variable assignment e.g. x=5;
        if (!isCompound(this.evalName)) {
            if (this.evalName.equals("this"))
                throw new UtilEvalError("Can't assign to 'this'.");
            // Interpreter.debug("Simple var LHS...");
            lhs = new LHS(this.namespace, this.evalName,
                    false/* bubble up if allowed */);
            return lhs;
        }
        // Field e.g. foo.bar=5;
        Object obj = null;
        try {
            while (this.evalName != null && isCompound(this.evalName))
                obj = this.consumeNextObjectField(callstack, interpreter,
                        false/* forcclass */, true/* autoallocthis */);
        } catch (final UtilEvalError e) {
            throw new UtilEvalError("LHS evaluation: " + e.getMessage());
        }
        // Finished eval and its a class.
        if (this.evalName == null && obj instanceof ClassIdentifier)
            throw new UtilEvalError("Can't assign to class: " + this.value);
        if (obj == null)
            throw new UtilEvalError("Error in LHS: " + this.value);
        // e.g. this.x=5; or someThisType.x=5;
        if (obj instanceof This) {
            // dissallow assignment to magic fields
            if (this.evalName.equals("namespace")
                    || this.evalName.equals("variables")
                    || this.evalName.equals("methods")
                    || this.evalName.equals("caller"))
                throw new UtilEvalError(
                        "Can't assign to special variable: " + this.evalName);
            Interpreter.debug("found This reference evaluating LHS");
            /*
             * If this was a literal "super" reference then we allow recursion
             * in setting the variable to get the normal effect of finding the
             * nearest definition starting at the super scope. On any other
             * resolution qualified by a 'this' type reference we want to set
             * the variable directly in that scope. e.g. this.x=5; or
             * someThisType.x=5;
             * In the old scoping rules super didn't do this.
             */
            final boolean localVar = !this.lastEvalName.equals("super");
            return new LHS(((This) obj).namespace, this.evalName, localVar);
        }
        if (this.evalName != null)
            try {
                if (obj instanceof ClassIdentifier) {
                    final Class clas = ((ClassIdentifier) obj).getTargetClass();
                    lhs = Reflect.getLHSStaticField(clas, this.evalName);
                    return lhs;
                } else {
                    lhs = Reflect.getLHSObjectField(obj, this.evalName);
                    return lhs;
                }
            } catch (final ReflectError e) {
                throw new UtilEvalError("Field access: " + e);
            }
        throw new InterpreterError("Internal error in lhs...");
    }

    /**
     * Invoke the method identified by this name.
     * Performs caching of method resolution using SignatureKey.
     * <p>
     *
     * Name contains a wholely unqualfied messy name; resolve it to
     * (object | static prefix) + method name and invoke.
     * <p>
     *
     * The interpreter is necessary to support 'this.interpreter' references
     * in the called code. (e.g. debug());
     * <p>
     *
     * <pre>
     *      Some cases:
     *
     *          // dynamic
     *          local();
     *          myVariable.foo();
     *          myVariable.bar.blah.foo();
     *          // static
     *          java.lang.Integer.getInteger("foo");
     * </pre>
     *
     * @param interpreter
     *            the interpreter
     * @param args
     *            the args
     * @param callstack
     *            the callstack
     * @param callerInfo
     *            the caller info
     * @return the object
     * @throws UtilEvalError
     *             the util eval error
     * @throws EvalError
     *             the eval error
     * @throws ReflectError
     *             the reflect error
     * @throws InvocationTargetException
     *             the invocation target exception
     */
    public Object invokeMethod(final Interpreter interpreter,
            final Object[] args, final CallStack callstack,
            final SimpleNode callerInfo) throws UtilEvalError, EvalError,
            ReflectError, InvocationTargetException {
        final String methodName = Name.suffix(this.value, 1);
        final BshClassManager bcm = interpreter.getClassManager();
        final NameSpace namespace = callstack.top();
        // Optimization - If classOfStaticMethod is set then we have already
        // been here and determined that this is a static method invocation.
        // Note: maybe factor this out with path below... clean up.
        if (this.classOfStaticMethod != null)
            return Reflect.invokeStaticMethod(bcm, this.classOfStaticMethod,
                    methodName, args);
        if (!Name.isCompound(this.value))
            return this.invokeLocalMethod(interpreter, args, callstack,
                    callerInfo);
        // Note: if we want methods declared inside blocks to be accessible via
        // this.methodname() inside the block we could handle it here as a
        // special case. See also resolveThisFieldReference() special handling
        // for BlockNameSpace case. They currently work via the direct name
        // e.g. methodName().
        final String prefix = Name.prefix(this.value);
        // Superclass method invocation? (e.g. super.foo())
        if (prefix.equals("super") && Name.countParts(this.value) == 2) {
            // Allow getThis() to work through block namespaces first
            final This ths = namespace.getThis(interpreter);
            final NameSpace thisNameSpace = ths.getNameSpace();
            final NameSpace classNameSpace = getClassNameSpace(thisNameSpace);
            if (classNameSpace != null) {
                final Object instance = classNameSpace.getClassInstance();
                return ClassGenerator.getClassGenerator()
                        .invokeSuperclassMethod(bcm, instance, methodName,
                                args);
            }
        }
        // Find target object or class identifier
        final Name targetName = namespace.getNameResolver(prefix);
        final Object obj = targetName.toObject(callstack, interpreter);
        if (obj == Primitive.VOID)
            throw new UtilEvalError("Attempt to resolve method: " + methodName
                    + "() on undefined variable or class name: " + targetName);
        // if we've got an object, resolve the method
        if (!(obj instanceof ClassIdentifier)) {
            if (obj instanceof Primitive) {
                if (obj == Primitive.NULL)
                    throw new UtilTargetError(new NullPointerException(
                            "Null Pointer in Method Invocation"));
                // some other primitive
                // should avoid calling methods on primitive, as we do
                // in Name (can't treat primitive like an object message)
                // but the hole is useful right now.
                if (Interpreter.DEBUG)
                    Interpreter.debug("Attempt to access method on primitive..."
                            + " allowing bsh.Primitive to peek through for debugging");
            }
            // found an object and it's not an undefined variable
            return Reflect.invokeObjectMethod(obj, methodName, args,
                    interpreter, callstack, callerInfo);
        }
        // It's a class
        // try static method
        if (Interpreter.DEBUG)
            Interpreter.debug("invokeMethod: trying static - " + targetName);
        final Class clas = ((ClassIdentifier) obj).getTargetClass();
        // cache the fact that this is a static method invocation on this class
        this.classOfStaticMethod = clas;
        if (clas != null)
            return Reflect.invokeStaticMethod(bcm, clas, methodName, args);
        // return null; ???
        throw new UtilEvalError("invokeMethod: unknown target: " + targetName);
    }

    /**
     * Invoke a locally declared method or a bsh command.
     * If the method is not already declared in the namespace then try
     * to load it as a resource from the imported command path (e.g.
     * /bsh/commands)
     *
     * @param interpreter
     *            the interpreter
     * @param args
     *            the args
     * @param callstack
     *            the callstack
     * @param callerInfo
     *            the caller info
     * @return the object
     * @throws EvalError
     *             the eval error
     *
     * Note: the bsh command code should probably not be here... we need to
     * scope it by the namespace that imported the command... so it probably
     * needs to be integrated into NameSpace.
     */
    private Object invokeLocalMethod(final Interpreter interpreter,
            final Object[] args, final CallStack callstack,
            final SimpleNode callerInfo)
            throws EvalError/* , ReflectError, InvocationTargetException */ {
        if (Interpreter.DEBUG)
            Interpreter.debug("invokeLocalMethod: " + this.value);
        if (interpreter == null)
            throw new InterpreterError("invokeLocalMethod: interpreter = null");
        final String commandName = this.value;
        final Class[] argTypes = Types.getTypes(args);
        // Check for existing method
        BshMethod meth = null;
        try {
            meth = this.namespace.getMethod(commandName, argTypes);
        } catch (final UtilEvalError e) {
            throw e.toEvalError("Local method invocation", callerInfo,
                    callstack);
        }
        // If defined, invoke it
        if (meth != null)
            return meth.invoke(args, interpreter, callstack, callerInfo);
        interpreter.getClassManager();
        // Look for a BeanShell command
        Object commandObject;
        try {
            commandObject = this.namespace.getCommand(commandName, argTypes,
                    interpreter);
        } catch (final UtilEvalError e) {
            throw e.toEvalError("Error loading command: ", callerInfo,
                    callstack);
        }
        // should try to print usage here if nothing found
        if (commandObject == null) {
            // Look for a default invoke() handler method in the namespace
            // Note: this code duplicates that in This.java... should it?
            // Call on 'This' can never be a command
            BshMethod invokeMethod = null;
            try {
                invokeMethod = this.namespace.getMethod("invoke",
                        new Class[] {null, null});
            } catch (final UtilEvalError e) {
                throw e.toEvalError("Local method invocation", callerInfo,
                        callstack);
            }
            if (invokeMethod != null)
                return invokeMethod.invoke(new Object[] {commandName, args},
                        interpreter, callstack, callerInfo);
            throw new EvalError(
                    "Command not found: "
                            + StringUtil.methodString(commandName, argTypes),
                    callerInfo, callstack);
        }
        if (commandObject instanceof BshMethod)
            return ((BshMethod) commandObject).invoke(args, interpreter,
                    callstack, callerInfo);
        if (commandObject instanceof Class)
            try {
                return Reflect.invokeCompiledCommand((Class) commandObject,
                        args, interpreter, callstack);
            } catch (final UtilEvalError e) {
                throw e.toEvalError("Error invoking compiled command: ",
                        callerInfo, callstack);
            }
        throw new InterpreterError("invalid command type");
    }
    /*
     * private String getHelp(String name)
     * throws UtilEvalError
     * {
     * try {
     * // should check for null namespace here
     * return get("bsh.help."+name, null/interpreter/);
     * } catch (Exception e) {
     * return "usage: "+name;
     * }
     * }
     * private String getHelp(Class commandClass)
     * throws UtilEvalError
     * {
     * try {
     * return (String)Reflect.invokeStaticMethod(
     * null/bcm/, commandClass, "usage", null);
     * } catch(Exception e)
     * return "usage: "+name;
     * }
     * }
     */

    // Static methods that operate on compound ('.' separated) names
    /**
     * Checks if is compound.
     *
     * @param value
     *            the value
     * @return true, if is compound
     */
    // I guess we could move these to StringUtil someday
    public static boolean isCompound(final String value) {
        return value.indexOf('.') != -1;
        // return countParts(value) > 1;
    }

    /**
     * Count parts.
     *
     * @param value
     *            the value
     * @return the int
     */
    static int countParts(final String value) {
        if (value == null)
            return 0;
        int count = 0;
        int index = -1;
        while ((index = value.indexOf('.', index + 1)) != -1)
            count++;
        return count + 1;
    }

    /**
     * Prefix.
     *
     * @param value
     *            the value
     * @return the string
     */
    static String prefix(final String value) {
        if (!isCompound(value))
            return null;
        return prefix(value, countParts(value) - 1);
    }

    /**
     * Prefix.
     *
     * @param value
     *            the value
     * @param parts
     *            the parts
     * @return the string
     */
    static String prefix(final String value, final int parts) {
        if (parts < 1)
            return null;
        int count = 0;
        int index = -1;
        while ((index = value.indexOf('.', index + 1)) != -1
                && ++count < parts);
        return index == -1 ? value : value.substring(0, index);
    }

    /**
     * Suffix.
     *
     * @param name
     *            the name
     * @return the string
     */
    static String suffix(final String name) {
        if (!isCompound(name))
            return null;
        return suffix(name, countParts(name) - 1);
    }

    /**
     * Suffix.
     *
     * @param value
     *            the value
     * @param parts
     *            the parts
     * @return the string
     */
    public static String suffix(final String value, final int parts) {
        if (parts < 1)
            return null;
        int count = 0;
        int index = value.length() + 1;
        while ((index = value.lastIndexOf('.', index - 1)) != -1
                && ++count < parts);
        return index == -1 ? value : value.substring(index + 1);
    }
    // end compound name routines

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return this.value;
    }
}
