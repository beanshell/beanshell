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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

/**
 * A namespace in which methods, variables, and imports (class names) live.
 * This is package public because it is used in the implementation of some
 * bsh commands. However for normal use you should be using methods on
 * bsh.Interpreter to interact with your scripts.
 * <p>
 *
 * A bsh.This object is a thin layer over a NameSpace that associates it with
 * an Interpreter instance. Together they comprise a Bsh scripted object
 * context.
 * <p>
 *
 * Note: I'd really like to use collections here, but we have to keep this
 * compatible with JDK1.1
 *
 * Thanks to Slava Pestov (of jEdit fame) for import caching enhancements.
 * Note: This class has gotten too big. It should be broken down a bit.
 */
public class NameSpace
        implements java.io.Serializable, BshClassManager.Listener, NameSource {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;
    /** The Constant JAVACODE. */
    public static final NameSpace JAVACODE =
            // (BshClassManager)
            new NameSpace(null, null, "Called from compiled Java code.");
    static {
        JAVACODE.isMethod = true;
    }
    // Begin instance data
    // Note: if we add something here we should reset it in the clear() method.
    /**
     * The name of this namespace. If the namespace is a method body
     * namespace then this is the name of the method. If it's a class or
     * class instance then it's the name of the class.
     */
    private String nsName;
    /** The parent. */
    private NameSpace parent;
    /** The variables. */
    private Hashtable<String, Variable> variables;
    /** The methods. */
    private Hashtable methods;
    /** The imported classes. */
    protected Hashtable importedClasses;
    /** The imported packages. */
    private Vector importedPackages;
    /** The imported commands. */
    private Vector importedCommands;
    /** The imported objects. */
    private Vector importedObjects;
    /** The imported static. */
    private Vector importedStatic;
    /** The package name. */
    private String packageName;
    /** The class manager. */
    private transient BshClassManager classManager;
    /** The this reference. */
    // See notes in getThis()
    private This thisReference;
    /** Name resolver objects. */
    private Hashtable names;
    /**
     * The node associated with the creation of this namespace.
     * This is used support getInvocationLine() and getInvocationText().
     */
    SimpleNode callerInfoNode;
    /**
     * Note that the namespace is a method body namespace. This is used for
     * printing stack traces in exceptions.
     */
    boolean isMethod;
    /**
     * Note that the namespace is a class body or class instance namespace.
     * This is used for controlling static/object import precedence, etc.
     *
     * Note: We can move this class related behavior out to a subclass of
     * NameSpace, but we'll start here.
     */
    boolean isClass;
    /** The class static. */
    Class classStatic;
    /** The class instance. */
    Object classInstance;

    /**
     * Sets the class static.
     *
     * @param clas
     *            the new class static
     */
    void setClassStatic(final Class clas) {
        this.classStatic = clas;
        this.importStatic(clas);
    }

    /**
     * Sets the class instance.
     *
     * @param instance
     *            the new class instance
     */
    void setClassInstance(final Object instance) {
        this.classInstance = instance;
        this.importObject(instance);
    }

    /**
     * Gets the class instance.
     *
     * @return the class instance
     * @throws UtilEvalError
     *             the util eval error
     */
    Object getClassInstance() throws UtilEvalError {
        if (this.classInstance != null)
            return this.classInstance;
        if (this.classStatic != null
        // || (getParent()!=null && getParent().classStatic != null)
        )
            throw new UtilEvalError(
                    "Can't refer to class instance from static context.");
        else
            throw new InterpreterError(
                    "Can't resolve class instance 'this' in: " + this);
    }

    /**
     * Local class cache for classes resolved through this namespace using
     * getClass() (taking into account imports). Only unqualified class names
     * are cached here (those which might be imported). Qualified names are
     * always absolute and are cached by BshClassManager.
     */
    private transient Hashtable classCache;
    // End instance data

    // Begin constructors
    /**
     * Instantiates a new name space.
     *
     * @param parent
     *            the parent
     * @param name
     *            the name
     * @parent the parent namespace of this namespace. Child namespaces
     *         inherit all variables and methods of their parent and can (of
     *         course)
     *         override / shadow them.
     */
    public NameSpace(final NameSpace parent, final String name) {
        // Note: in this case parent must have a class manager.
        this(parent, null, name);
    }

    // public NameSpace(BshClassManager classManager, String name)
    // {
    // this(null, classManager, name);
    // }
    /**
     * Instantiates a new name space.
     *
     * @param parent
     *            the parent
     * @param classManager
     *            the class manager
     * @param name
     *            the name
     */
    public NameSpace(final NameSpace parent, final BshClassManager classManager,
            final String name) {
        // We might want to do this here rather than explicitly in Interpreter
        // for global (see also prune())
        // if (classManager == null && (parent == null))
        // create our own class manager?
        this.setName(name);
        this.setParent(parent);
        this.setClassManager(classManager);
        // Register for notification of classloader change
        if (classManager != null)
            classManager.addListener(this);
    }

    /**
     * Sets the name.
     *
     * @param name
     *            the new name
     */
    // End constructors
    public void setName(final String name) {
        this.nsName = name;
    }

    /**
     * The name of this namespace. If the namespace is a method body
     * namespace then this is the name of the method. If it's a class or
     * class instance then it's the name of the class.
     *
     * @return the name
     */
    public String getName() {
        return this.nsName;
    }

    /**
     * Set the node associated with the creation of this namespace.
     * This is used in debugging and to support the getInvocationLine()
     * and getInvocationText() methods.
     *
     * @param node
     *            the new node
     */
    void setNode(final SimpleNode node) {
        this.callerInfoNode = node;
    }

    /**
     * Gets the node.
     *
     * @return the node
     */
    SimpleNode getNode() {
        if (this.callerInfoNode != null)
            return this.callerInfoNode;
        if (this.parent != null)
            return this.parent.getNode();
        else
            return null;
    }

    /**
     * Resolve name to an object through this namespace.
     *
     * @param name
     *            the name
     * @param interpreter
     *            the interpreter
     * @return the object
     * @throws UtilEvalError
     *             the util eval error
     */
    public Object get(final String name, final Interpreter interpreter)
            throws UtilEvalError {
        final CallStack callstack = new CallStack(this);
        return this.getNameResolver(name).toObject(callstack, interpreter);
    }

    /**
     * Set the variable through this namespace.
     * This method obeys the LOCALSCOPING property to determine how variables
     * are set.
     * <p>
     * Note: this method is primarily intended for use internally. If you use
     * this method outside of the bsh package and wish to set variables with
     * primitive values you will have to wrap them using bsh.Primitive.
     *
     * @param name
     *            the name
     * @param value
     *            the value
     * @param strictJava
     *            specifies whether strict java rules are applied.
     * @throws UtilEvalError
     *             the util eval error
     * @see bsh.Primitive
     *      <p>
     *      Setting a new variable (which didn't exist before) or removing
     *      a variable causes a namespace change.
     */
    public void setVariable(final String name, final Object value,
            final boolean strictJava) throws UtilEvalError {
        // if localscoping switch follow strictJava, else recurse
        final boolean recurse = Interpreter.LOCALSCOPING ? strictJava : true;
        this.setVariable(name, value, strictJava, recurse);
    }

    /**
     * Set a variable explicitly in the local scope.
     *
     * @param name
     *            the name
     * @param value
     *            the value
     * @param strictJava
     *            the strict java
     * @return the variable
     * @throws UtilEvalError
     *             the util eval error
     */
    public Variable setLocalVariable(final String name, final Object value,
            final boolean strictJava) throws UtilEvalError {
        return this.setVariable(name, value, strictJava, false/* recurse */);
    }

    /**
     * Set the value of a the variable 'name' through this namespace.
     * The variable may be an existing or non-existing variable.
     * It may live in this namespace or in a parent namespace if recurse is
     * true.
     * <p>
     * Note: This method is not public and does *not* know about LOCALSCOPING.
     * Its caller methods must set recurse intelligently in all situations
     * (perhaps based on LOCALSCOPING).
     *
     * <p>
     * Note: this method is primarily intended for use internally. If you use
     * this method outside of the bsh package and wish to set variables with
     * primitive values you will have to wrap them using bsh.Primitive.
     *
     * @param name
     *            the name
     * @param value
     *            the value
     * @param strictJava
     *            specifies whether strict java rules are applied.
     * @param recurse
     *            determines whether we will search for the variable in
     *            our parent's scope before assigning locally.
     * @return the variable
     * @throws UtilEvalError
     *             the util eval error
     * @see bsh.Primitive
     *      <p>
     *      Setting a new variable (which didn't exist before) or removing
     *      a variable causes a namespace change.
     */
    Variable setVariable(final String name, final Object value,
            final boolean strictJava, final boolean recurse)
            throws UtilEvalError {
        if (this.variables == null)
            this.variables = new Hashtable();
        // primitives should have been wrapped
        if (value == null)
            throw new InterpreterError("null variable value");
        // Locate the variable definition if it exists.
        final Variable existing = this.getVariableImpl(name, recurse);
        // Found an existing variable here (or above if recurse allowed)
        if (existing != null) {
            try {
                existing.setValue(value, Variable.ASSIGNMENT);
            } catch (final UtilEvalError e) {
                throw new UtilEvalError(
                        "Variable assignment: " + name + ": " + e.getMessage());
            }
            return existing;
        } else {
            // No previous variable definition found here (or above if recurse)
            if (strictJava)
                throw new UtilEvalError(
                        "(Strict Java mode) Assignment to undeclared variable: "
                                + name);
            // If recurse, set global untyped var, else set it here.
            // NameSpace varScope = recurse ? getGlobal() : this;
            // This modification makes default allocation local
            // NameSpace varScope = this;
            final Variable var = this.createVariable(name, value,
                    null/* modifiers */);
            this.variables.put(name, var);
            // nameSpaceChanged() on new variable addition
            this.nameSpaceChanged();
            return var;
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    /**
     * <p>
     * Sets a variable or property. See "setVariable" for rules regarding
     * scoping.
     * </p>
     *
     * <p>
     * We first check for the existence of the variable. If it exists, we set
     * it.
     * If the variable does not exist we look for a property. If the property
     * exists and is writable we set it.
     * Finally, if neither the variable or the property exist, we create a new
     * variable.
     * </p>
     *
     * @param name
     *            the name
     * @param value
     *            the value
     * @param strictJava
     *            specifies whether strict java rules are applied.
     * @throws UtilEvalError
     *             the util eval error
     */
    public void setVariableOrProperty(final String name, final Object value,
            final boolean strictJava) throws UtilEvalError {
        // if localscoping switch follow strictJava, else recurse
        final boolean recurse = Interpreter.LOCALSCOPING ? strictJava : true;
        this.setVariableOrProperty(name, value, strictJava, recurse);
    }

    /**
     * Set a variable or property explicitly in the local scope.
     *
     * <p>
     * Sets a variable or property. See "setLocalVariable" for rules regarding
     * scoping.
     * </p>
     *
     * <p>
     * We first check for the existence of the variable. If it exists, we set
     * it.
     * If the variable does not exist we look for a property. If the property
     * exists and is writable we set it.
     * Finally, if neither the variable or the property exist, we create a new
     * variable.
     * </p>
     *
     * @param name
     *            the name
     * @param value
     *            the value
     * @param strictJava
     *            the strict java
     * @throws UtilEvalError
     *             the util eval error
     */
    void setLocalVariableOrProperty(final String name, final Object value,
            final boolean strictJava) throws UtilEvalError {
        this.setVariableOrProperty(name, value, strictJava, false/* recurse */);
    }

    /**
     * Set the value of a the variable or property 'name' through this
     * namespace.
     *
     * <p>
     * Sets a variable or property. See "setVariableOrProperty" for rules
     * regarding scope.
     * </p>
     *
     * <p>
     * We first check for the existence of the variable. If it exists, we set
     * it.
     * If the variable does not exist we look for a property. If the property
     * exists and is writable we set it.
     * Finally, if neither the variable or the property exist, we create a new
     * variable.
     * </p>
     *
     * @param name
     *            the name
     * @param value
     *            the value
     * @param strictJava
     *            specifies whether strict java rules are applied.
     * @param recurse
     *            determines whether we will search for the variable in
     *            our parent's scope before assigning locally.
     * @throws UtilEvalError
     *             the util eval error
     */
    void setVariableOrProperty(final String name, final Object value,
            final boolean strictJava, final boolean recurse)
            throws UtilEvalError {
        if (this.variables == null)
            this.variables = new Hashtable();
        // primitives should have been wrapped
        if (value == null)
            throw new InterpreterError("null variable value");
        // Locate the variable definition if it exists.
        final Variable existing = this.getVariableImpl(name, recurse);
        // Found an existing variable here (or above if recurse allowed)
        if (existing != null)
            try {
                existing.setValue(value, Variable.ASSIGNMENT);
            } catch (final UtilEvalError e) {
                throw new UtilEvalError(
                        "Variable assignment: " + name + ": " + e.getMessage());
            }
        else {
            // No previous variable definition found here (or above if recurse)
            if (strictJava)
                throw new UtilEvalError(
                        "(Strict Java mode) Assignment to undeclared variable: "
                                + name);
            final boolean setProp = this.attemptSetPropertyValue(name, value,
                    null);
            if (setProp)
                return;
            // If recurse, set global untyped var, else set it here.
            // NameSpace varScope = recurse ? getGlobal() : this;
            // This modification makes default allocation local
            final NameSpace varScope = this;
            varScope.variables.put(name,
                    this.createVariable(name, value, null/* modifiers */));
            // nameSpaceChanged() on new variable addition
            this.nameSpaceChanged();
        }
    }

    /**
     * Creates the variable.
     *
     * @param name
     *            the name
     * @param value
     *            the value
     * @param mods
     *            the mods
     * @return the variable
     * @throws UtilEvalError
     *             the util eval error
     */
    protected Variable createVariable(final String name, final Object value,
            final Modifiers mods) throws UtilEvalError {
        return this.createVariable(name, null/* type */, value, mods);
    }

    /**
     * Creates the variable.
     *
     * @param name
     *            the name
     * @param type
     *            the type
     * @param value
     *            the value
     * @param mods
     *            the mods
     * @return the variable
     * @throws UtilEvalError
     *             the util eval error
     */
    protected Variable createVariable(final String name, final Class type,
            final Object value, final Modifiers mods) throws UtilEvalError {
        return new Variable(name, type, value, mods);
    }

    /**
     * Creates the variable.
     *
     * @param name
     *            the name
     * @param type
     *            the type
     * @param lhs
     *            the lhs
     * @return the variable
     * @throws UtilEvalError
     *             the util eval error
     */
    protected Variable createVariable(final String name, final Class type,
            final LHS lhs) throws UtilEvalError {
        return new Variable(name, type, lhs);
    }

    /**
     * Remove the variable from the namespace.
     *
     * @param name
     *            the name
     */
    public void unsetVariable(final String name) {
        if (this.variables != null) {
            this.variables.remove(name);
            this.nameSpaceChanged();
        }
    }

    /**
     * Get the names of variables defined in this namespace.
     * (This does not show variables in parent namespaces).
     *
     * @return the variable names
     */
    public String[] getVariableNames() {
        if (this.variables == null)
            return new String[0];
        else
            return this.enumerationToStringArray(this.variables.keys());
    }

    /**
     * Get the names of methods declared in this namespace.
     * (This does not include methods in parent namespaces).
     *
     * @return the method names
     */
    public String[] getMethodNames() {
        if (this.methods == null)
            return new String[0];
        else
            return this.enumerationToStringArray(this.methods.keys());
    }

    /**
     * Get the methods defined in this namespace.
     * (This does not show methods in parent namespaces).
     * Note: This will probably be renamed getDeclaredMethods()
     *
     * @return the methods
     */
    public BshMethod[] getMethods() {
        if (this.methods == null)
            return new BshMethod[0];
        else
            return this.flattenMethodCollection(this.methods.elements());
    }

    /**
     * Enumeration to string array.
     *
     * @param e
     *            the e
     * @return the string[]
     */
    private String[] enumerationToStringArray(final Enumeration e) {
        final Vector v = new Vector();
        while (e.hasMoreElements())
            v.addElement(e.nextElement());
        final String[] sa = new String[v.size()];
        v.copyInto(sa);
        return sa;
    }

    /**
     * Flatten the vectors of overloaded methods to a single array.
     *
     * @param e
     *            the e
     * @return the bsh method[]
     * @see #getMethods()
     */
    private BshMethod[] flattenMethodCollection(final Enumeration e) {
        final Vector v = new Vector();
        while (e.hasMoreElements()) {
            final Object o = e.nextElement();
            if (o instanceof BshMethod)
                v.addElement(o);
            else {
                final Vector ov = (Vector) o;
                for (int i = 0; i < ov.size(); i++)
                    v.addElement(ov.elementAt(i));
            }
        }
        final BshMethod[] bma = new BshMethod[v.size()];
        v.copyInto(bma);
        return bma;
    }

    /**
     * Get the parent namespace.
     * Note: this isn't quite the same as getSuper().
     * getSuper() returns 'this' if we are at the root namespace.
     *
     * @return the parent
     */
    public NameSpace getParent() {
        return this.parent;
    }

    /**
     * Get the parent namespace' This reference or this namespace' This
     * reference if we are the top.
     *
     * @param declaringInterpreter
     *            the declaring interpreter
     * @return the super
     */
    public This getSuper(final Interpreter declaringInterpreter) {
        if (this.parent != null)
            return this.parent.getThis(declaringInterpreter);
        else
            return this.getThis(declaringInterpreter);
    }

    /**
     * Get the top level namespace or this namespace if we are the top.
     * Note: this method should probably return type bsh.This to be consistent
     * with getThis();
     *
     * @param declaringInterpreter
     *            the declaring interpreter
     * @return the global
     */
    public This getGlobal(final Interpreter declaringInterpreter) {
        if (this.parent != null)
            return this.parent.getGlobal(declaringInterpreter);
        else
            return this.getThis(declaringInterpreter);
    }

    /**
     * A This object is a thin layer over a namespace, comprising a bsh object
     * context. It handles things like the interface types the bsh object
     * supports and aspects of method invocation on it.
     * <p>
     *
     * The declaringInterpreter is here to support callbacks from Java through
     * generated proxies. The scripted object "remembers" who created it for
     * things like printing messages and other per-interpreter phenomenon
     * when called externally from Java.
     *
     * @param declaringInterpreter
     *            the declaring interpreter
     * @return the this
     *
     * Note: we need a singleton here so that things like 'this == this' work
     * (and probably a good idea for speed).
     * Caching a single instance here seems technically incorrect,
     * considering the declaringInterpreter could be different under some
     * circumstances. (Case: a child interpreter running a source() / eval()
     * command). However the effect is just that the main interpreter that
     * executes your script should be the one involved in call-backs from Java.
     * I do not know if there are corner cases where a child interpreter would
     * be the first to use a This reference in a namespace or if that would
     * even cause any problems if it did... We could do some experiments
     * to find out... and if necessary we could cache on a per interpreter
     * basis if we had weak references... We might also look at skipping
     * over child interpreters and going to the parent for the declaring
     * interpreter, so we'd be sure to get the top interpreter.
     */
    public This getThis(final Interpreter declaringInterpreter) {
        if (this.thisReference == null)
            this.thisReference = This.getThis(this, declaringInterpreter);
        return this.thisReference;
    }

    /**
     * Gets the class manager.
     *
     * @return the class manager
     */
    public BshClassManager getClassManager() {
        if (this.classManager != null)
            return this.classManager;
        if (this.parent != null && this.parent != JAVACODE)
            return this.parent.getClassManager();
        // System.out.println("experiment: creating class manager");
        this.classManager = BshClassManager
                .createClassManager(null/* interp */);
        // Interpreter.debug("No class manager namespace:" +this);
        return this.classManager;
    }

    /**
     * Sets the class manager.
     *
     * @param classManager
     *            the new class manager
     */
    void setClassManager(final BshClassManager classManager) {
        this.classManager = classManager;
    }

    /**
     * Used for serialization.
     */
    public void prune() {
        // Cut off from parent, we must have our own class manager.
        // Can't do this in the run() command (needs to resolve stuff)
        // Should we do it by default when we create a namespace will no
        // parent of class manager?
        if (this.classManager == null)
            // XXX if we keep the createClassManager in getClassManager then we
            // can axe
            // this?
            this.setClassManager(
                    BshClassManager.createClassManager(null/* interp */));
        this.setParent(null);
    }

    /**
     * Sets the parent.
     *
     * @param parent
     *            the new parent
     */
    public void setParent(final NameSpace parent) {
        this.parent = parent;
        // If we are disconnected from root we need to handle the def imports
        if (parent == null)
            this.loadDefaultImports();
    }

    /**
     * <p>
     * Get the specified variable or property in this namespace or a parent
     * namespace.
     * </p>
     *
     * <p>
     * We first search for a variable name, and then a property.
     * </p>
     *
     * @param name
     *            the name
     * @param interp
     *            the interp
     * @return The variable or property value or Primitive.VOID if neither is
     *         defined.
     * @throws UtilEvalError
     *             the util eval error
     */
    public Object getVariableOrProperty(final String name,
            final Interpreter interp) throws UtilEvalError {
        final Object val = this.getVariable(name, true);
        return val == Primitive.VOID ? this.getPropertyValue(name, interp)
                : val;
    }

    /**
     * Get the specified variable in this namespace or a parent namespace.
     * <p>
     * Note: this method is primarily intended for use internally. If you use
     * this method outside of the bsh package you will have to use
     * Primitive.unwrap() to get primitive values.
     *
     * @param name
     *            the name
     * @return The variable value or Primitive.VOID if it is not defined.
     * @throws UtilEvalError
     *             the util eval error
     * @see Primitive#unwrap(Object)
     */
    public Object getVariable(final String name) throws UtilEvalError {
        return this.getVariable(name, true);
    }

    /**
     * Get the specified variable in this namespace.
     *
     * @param name
     *            the name
     * @param recurse
     *            If recurse is true then we recursively search through
     *            parent namespaces for the variable.
     *            <p>
     *            Note: this method is primarily intended for use internally. If
     *            you use
     *            this method outside of the bsh package you will have to use
     *            Primitive.unwrap() to get primitive values.
     * @return The variable value or Primitive.VOID if it is not defined.
     * @throws UtilEvalError
     *             the util eval error
     * @see Primitive#unwrap(Object)
     */
    public Object getVariable(final String name, final boolean recurse)
            throws UtilEvalError {
        final Variable var = this.getVariableImpl(name, recurse);
        return this.unwrapVariable(var);
    }

    /**
     * Locate a variable and return the Variable object with optional
     * recursion through parent name spaces.
     * <p/>
     * If this namespace is static, return only static variables.
     *
     * @param name
     *            the name
     * @param recurse
     *            the recurse
     * @return the Variable value or null if it is not defined
     * @throws UtilEvalError
     *             the util eval error
     */
    protected Variable getVariableImpl(final String name, final boolean recurse)
            throws UtilEvalError {
        Variable var = null;
        // Change import precedence if we are a class body/instance
        // Get imported first.
        if (var == null && this.isClass)
            var = this.getImportedVar(name);
        if (var == null && this.variables != null)
            var = this.variables.get(name);
        // Change import precedence if we are a class body/instance
        if (var == null && !this.isClass)
            var = this.getImportedVar(name);
        // try parent
        if (recurse && var == null && this.parent != null)
            var = this.parent.getVariableImpl(name, recurse);
        return var;
    }

    /**
     * Gets the declared variables.
     *
     * @return the declared variables
     *
     * Get variables declared in this namespace.
     */
    public Variable[] getDeclaredVariables() {
        if (this.variables == null)
            return new Variable[0];
        final Variable[] vars = new Variable[this.variables.size()];
        int i = 0;
        for (final Enumeration e = this.variables.elements();
                e.hasMoreElements();)
            vars[i++] = (Variable) e.nextElement();
        return vars;
    }

    /**
     * Unwrap a variable to its value.
     *
     * @param var
     *            the var
     * @return return the variable value. A null var is mapped to
     *         Primitive.VOID
     * @throws UtilEvalError
     *             the util eval error
     */
    protected Object unwrapVariable(final Variable var) throws UtilEvalError {
        return var == null ? Primitive.VOID : var.getValue();
    }

    /**
     * Sets the typed variable.
     *
     * @param name
     *            the name
     * @param type
     *            the type
     * @param value
     *            the value
     * @param isFinal
     *            the is final
     * @throws UtilEvalError
     *             the util eval error
     * @deprecated See #setTypedVariable(String, Class, Object, Modifiers)
     */
    @Deprecated
    public void setTypedVariable(final String name, final Class type,
            final Object value, final boolean isFinal) throws UtilEvalError {
        final Modifiers modifiers = new Modifiers();
        if (isFinal)
            modifiers.addModifier(Modifiers.FIELD, "final");
        this.setTypedVariable(name, type, value, modifiers);
    }

    /**
     * Declare a variable in the local scope and set its initial value.
     * Value may be null to indicate that we would like the default value
     * for the variable type. (e.g. 0 for integer types, null for object
     * types). An existing typed variable may only be set to the same type.
     * If an untyped variable of the same name exists it will be overridden
     * with the new typed var.
     * The set will perform a Types.getAssignableForm() on the value if
     * necessary.
     *
     * <p>
     * Note: this method is primarily intended for use internally. If you use
     * this method outside of the bsh package and wish to set variables with
     * primitive values you will have to wrap them using bsh.Primitive.
     *
     * @param name
     *            the name
     * @param type
     *            the type
     * @param value
     *            If value is null, you'll get the default value for the type
     * @param modifiers
     *            may be null
     * @throws UtilEvalError
     *             the util eval error
     * @see bsh.Primitive
     */
    public void setTypedVariable(final String name, final Class type,
            final Object value, final Modifiers modifiers)
            throws UtilEvalError {
        // checkVariableModifiers(name, modifiers);
        if (this.variables == null)
            this.variables = new Hashtable();
        // Setting a typed variable is always a local operation.
        final Variable existing = this.getVariableImpl(name,
                false/* recurse */);
        // Null value is just a declaration
        // Note: we might want to keep any existing value here instead of reset
        // does the variable already exist?
        if (existing != null)
            // Is it typed?
            if (existing.getType() != null)
                // If it had a different type throw error.
                // This allows declaring the same var again, but not with
                // a different (even if assignable) type.
                if (existing.getType() != type)
                    throw new UtilEvalError("Typed variable: " + name
                            + " was previously declared with type: "
                            + existing.getType());
                else {
                    // else set it and return
                    existing.setValue(value, Variable.DECLARATION);
                    return;
                }
        // Add the new typed var
        this.variables.put(name,
                this.createVariable(name, type, value, modifiers));
    }

    /**
     * Dissallow static vars outside of a class.
     *
     * @param name
     *            is here just to allow the error message to use it
     *            protected void checkVariableModifiers(String name, Modifiers
     *            modifiers)
     *            throws UtilEvalError
     *            {
     *            if (modifiers!=null && modifiers.hasModifier("static"))
     *            throw new UtilEvalError(
     *            "Can't declare static variable outside of class: "+name);
     *            }
     * @param method
     *            the method
     * @throws UtilEvalError
     *             the util eval error
     **
     * Note: this is primarily for internal use.
     *
     * @see Interpreter#source(String)
     * @see Interpreter#eval(String)
     */
    public void setMethod(final String name, final BshMethod method)
            throws UtilEvalError {
        // checkMethodModifiers(method);
        if (this.methods == null)
            this.methods = new Hashtable();
        final Object m = this.methods.get(name);
        if (m == null)
            this.methods.put(name, method);
        else if (m instanceof BshMethod) {
            final Vector v = new Vector();
            v.addElement(m);
            v.addElement(method);
            this.methods.put(name, v);
        } else // Vector
            ((Vector) m).addElement(method);
    }

    /**
     * Gets the method.
     *
     * @param name
     *            the name
     * @param sig
     *            the sig
     * @return the method
     * @throws UtilEvalError
     *             the util eval error
     * @see #getMethod(String, Class [], boolean)
     * @see #getMethod(String, Class [])
     */
    public BshMethod getMethod(final String name, final Class[] sig)
            throws UtilEvalError {
        return this.getMethod(name, sig, false/* declaredOnly */);
    }

    /**
     * Get the bsh method matching the specified signature declared in
     * this name space or a parent.
     * <p>
     * Note: this method is primarily intended for use internally. If you use
     * this method outside of the bsh package you will have to be familiar
     * with BeanShell's use of the Primitive wrapper class.
     *
     * @param name
     *            the name
     * @param sig
     *            the sig
     * @param declaredOnly
     *            if true then only methods declared directly in this
     *            namespace will be found and no inherited or imported methods
     *            will
     *            be visible.
     * @return the BshMethod or null if not found
     * @throws UtilEvalError
     *             the util eval error
     * @see bsh.Primitive
     */
    public BshMethod getMethod(final String name, final Class[] sig,
            final boolean declaredOnly) throws UtilEvalError {
        BshMethod method = null;
        // Change import precedence if we are a class body/instance
        // Get import first.
        if (method == null && this.isClass && !declaredOnly)
            method = this.getImportedMethod(name, sig);
        Object m = null;
        if (method == null && this.methods != null) {
            m = this.methods.get(name);
            // m contains either BshMethod or Vector of BshMethod
            if (m != null) {
                // unwrap
                BshMethod[] ma;
                if (m instanceof Vector) {
                    final Vector vm = (Vector) m;
                    ma = new BshMethod[vm.size()];
                    vm.copyInto(ma);
                } else
                    ma = new BshMethod[] {(BshMethod) m};
                // Apply most specific signature matching
                final Class[][] candidates = new Class[ma.length][];
                for (int i = 0; i < ma.length; i++)
                    candidates[i] = ma[i].getParameterTypes();
                final int match = Reflect.findMostSpecificSignature(sig,
                        candidates);
                if (match != -1)
                    method = ma[match];
            }
        }
        if (method == null && !this.isClass && !declaredOnly)
            method = this.getImportedMethod(name, sig);
        // try parent
        if (!declaredOnly && method == null && this.parent != null)
            return this.parent.getMethod(name, sig);
        return method;
    }

    /**
     * Import a class name.
     * Subsequent imports override earlier ones
     *
     * @param name
     *            the name
     */
    public void importClass(final String name) {
        if (this.importedClasses == null)
            this.importedClasses = new Hashtable();
        this.importedClasses.put(Name.suffix(name, 1), name);
        this.nameSpaceChanged();
    }

    /**
     * subsequent imports override earlier ones.
     *
     * @param name
     *            the name
     */
    public void importPackage(final String name) {
        if (this.importedPackages == null)
            this.importedPackages = new Vector();
        // If it exists, remove it and add it at the end (avoid memory leak)
        if (this.importedPackages.contains(name))
            this.importedPackages.remove(name);
        this.importedPackages.addElement(name);
        this.nameSpaceChanged();
    }

    /**
     * Import scripted or compiled BeanShell commands in the following package
     * in the classpath. You may use either "/" path or "." package notation.
     * e.g. importCommands("/bsh/commands") or importCommands("bsh.commands")
     * are equivalent. If a relative path style specifier is used then it is
     * made into an absolute path by prepending "/".
     *
     * @param name
     *            the name
     */
    public void importCommands(String name) {
        if (this.importedCommands == null)
            this.importedCommands = new Vector();
        // dots to slashes
        name = name.replace('.', '/');
        // absolute
        if (!name.startsWith("/"))
            name = "/" + name;
        // remove trailing (but preserve case of simple "/")
        if (name.length() > 1 && name.endsWith("/"))
            name = name.substring(0, name.length() - 1);
        // If it exists, remove it and add it at the end (avoid memory leak)
        if (this.importedCommands.contains(name))
            this.importedCommands.remove(name);
        this.importedCommands.addElement(name);
        this.nameSpaceChanged();
    }

    /**
     * A command is a scripted method or compiled command class implementing a
     * specified method signature. Commands are loaded from the classpath
     * and may be imported using the importCommands() method.
     * <p/>
     *
     * This method searches the imported commands packages for a script or
     * command object corresponding to the name of the method. If it is a
     * script the script is sourced into this namespace and the BshMethod for
     * the requested signature is returned. If it is a compiled class the
     * class is returned. (Compiled command classes implement static invoke()
     * methods).
     * <p/>
     *
     * The imported packages are searched in reverse order, so that later
     * imports take priority.
     * Currently only the first object (script or class) with the appropriate
     * name is checked. If another, overloaded form, is located in another
     * package it will not currently be found. This could be fixed.
     * <p/>
     *
     * @param name
     *            is the name of the desired command method
     * @param argTypes
     *            is the signature of the desired command method.
     * @param interpreter
     *            the interpreter
     * @return a BshMethod, Class, or null if no such command is found.
     * @throws UtilEvalError
     *             if loadScriptedCommand throws UtilEvalError
     *             i.e. on errors loading a script that was found
     */
    public Object getCommand(final String name, final Class[] argTypes,
            final Interpreter interpreter) throws UtilEvalError {
        if (Interpreter.DEBUG)
            Interpreter.debug("getCommand: " + name);
        final BshClassManager bcm = interpreter.getClassManager();
        if (this.importedCommands != null)
            // loop backwards for precedence
            for (int i = this.importedCommands.size() - 1; i >= 0; i--) {
                final String path = (String) this.importedCommands.elementAt(i);
                String scriptPath;
                if (path.equals("/"))
                    scriptPath = path + name + ".bsh";
                else
                    scriptPath = path + "/" + name + ".bsh";
                Interpreter.debug("searching for script: " + scriptPath);
                final InputStream in = bcm.getResourceAsStream(scriptPath);
                if (in != null)
                    return this.loadScriptedCommand(in, name, argTypes,
                            scriptPath, interpreter);
                // Chop leading "/" and change "/" to "."
                String className;
                if (path.equals("/"))
                    className = name;
                else
                    className = path.substring(1).replace('/', '.') + "."
                            + name;
                Interpreter.debug("searching for class: " + className);
                final Class clas = bcm.classForName(className);
                if (clas != null)
                    return clas;
            }
        if (this.parent != null)
            return this.parent.getCommand(name, argTypes, interpreter);
        else
            return null;
    }

    /**
     * Gets the imported method.
     *
     * @param name
     *            the name
     * @param sig
     *            the sig
     * @return the imported method
     * @throws UtilEvalError
     *             the util eval error
     */
    protected BshMethod getImportedMethod(final String name, final Class[] sig)
            throws UtilEvalError {
        // Try object imports
        if (this.importedObjects != null)
            for (int i = 0; i < this.importedObjects.size(); i++) {
                final Object object = this.importedObjects.elementAt(i);
                final Class clas = object.getClass();
                final Method method = Reflect.resolveJavaMethod(
                        this.getClassManager(), clas, name, sig,
                        false/* onlyStatic */);
                if (method != null)
                    return new BshMethod(method, object);
            }
        // Try static imports
        if (this.importedStatic != null)
            for (int i = 0; i < this.importedStatic.size(); i++) {
                final Class clas = (Class) this.importedStatic.elementAt(i);
                final Method method = Reflect.resolveJavaMethod(
                        this.getClassManager(), clas, name, sig,
                        true/* onlyStatic */);
                if (method != null)
                    return new BshMethod(method, null/* object */);
            }
        return null;
    }

    /**
     * Gets the imported var.
     *
     * @param name
     *            the name
     * @return the imported var
     * @throws UtilEvalError
     *             the util eval error
     */
    protected Variable getImportedVar(final String name) throws UtilEvalError {
        // Try object imports
        if (this.importedObjects != null)
            for (int i = 0; i < this.importedObjects.size(); i++) {
                final Object object = this.importedObjects.elementAt(i);
                final Class clas = object.getClass();
                final Field field = Reflect.resolveJavaField(clas, name,
                        false/* onlyStatic */);
                if (field != null)
                    return this.createVariable(name, field.getType(),
                            new LHS(object, field));
            }
        // Try static imports
        if (this.importedStatic != null)
            for (int i = 0; i < this.importedStatic.size(); i++) {
                final Class clas = (Class) this.importedStatic.elementAt(i);
                final Field field = Reflect.resolveJavaField(clas, name,
                        true/* onlyStatic */);
                if (field != null)
                    return this.createVariable(name, field.getType(),
                            new LHS(field));
            }
        return null;
    }

    /**
     * Load a command script from the input stream and find the BshMethod in
     * the target namespace.
     *
     * @param in
     *            the in
     * @param name
     *            the name
     * @param argTypes
     *            the arg types
     * @param resourcePath
     *            the resource path
     * @param interpreter
     *            the interpreter
     * @return the bsh method
     * @throws UtilEvalError
     *             on error in parsing the script or if the the
     *             method is not found after parsing the script.
     *
     * If we want to support multiple commands in the command path we need to
     * change this to not throw the exception.
     */
    private BshMethod loadScriptedCommand(final InputStream in,
            final String name, final Class[] argTypes,
            final String resourcePath, final Interpreter interpreter)
            throws UtilEvalError {
        try {
            interpreter.eval(new InputStreamReader(in), this, resourcePath);
        } catch (final EvalError e) {
            /*
             * Here we catch any EvalError from the interpreter because we are
             * using it as a tool to load the command, not as part of the
             * execution path.
             */
            Interpreter.debug(e.toString());
            throw new UtilEvalError("Error loading script: " + e.getMessage());
        }
        // Look for the loaded command
        final BshMethod meth = this.getMethod(name, argTypes);
        /*
         * if (meth == null)
         * throw new UtilEvalError("Loaded resource: " + resourcePath +
         * "had an error or did not contain the correct method");
         */
        return meth;
    }

    /**
     * Helper that caches class.
     *
     * @param name
     *            the name
     * @param c
     *            the c
     */
    void cacheClass(final String name, final Class c) {
        if (this.classCache == null)
            this.classCache = new Hashtable();
        // cacheCount++; // debug
        this.classCache.put(name, c);
    }

    /**
     * Load a class through this namespace taking into account imports.
     * The class search will proceed through the parent namespaces if
     * necessary.
     *
     * @param name
     *            the name
     * @return null if not found.
     * @throws UtilEvalError
     *             the util eval error
     */
    public Class getClass(final String name) throws UtilEvalError {
        final Class c = this.getClassImpl(name);
        if (c != null)
            return c;
        else
        // implement the recursion for getClassImpl()
        if (this.parent != null)
            return this.parent.getClass(name);
        else
            return null;
    }

    /**
     * Implementation of getClass()
     *
     * Load a class through this namespace taking into account imports.
     * <p>
     *
     * Check the cache first. If an unqualified name look for imported
     * class or package. Else try to load absolute name.
     * <p>
     *
     * This method implements caching of unqualified names (normally imports).
     * Qualified names are cached by the BshClassManager.
     * Unqualified absolute class names (e.g. unpackaged Foo) are cached too
     * so that we don't go searching through the imports for them each time.
     *
     * @param name
     *            the name
     * @return null if not found.
     * @throws UtilEvalError
     *             the util eval error
     */
    private Class getClassImpl(final String name) throws UtilEvalError {
        Class c = null;
        // Check the cache
        if (this.classCache != null) {
            c = (Class) this.classCache.get(name);
            if (c != null)
                return c;
        }
        // Unqualified (simple, non-compound) name
        final boolean unqualifiedName = !Name.isCompound(name);
        // Unqualified name check imported
        if (unqualifiedName) {
            // Try imported class
            if (c == null)
                c = this.getImportedClassImpl(name);
            // if found as imported also cache it
            if (c != null) {
                this.cacheClass(name, c);
                return c;
            }
        }
        // Try absolute
        c = this.classForName(name);
        if (c != null) {
            // Cache unqualified names to prevent import check again
            if (unqualifiedName)
                this.cacheClass(name, c);
            return c;
        }
        // Not found
        if (Interpreter.DEBUG)
            Interpreter.debug("getClass(): " + name + " not found in " + this);
        return null;
    }

    /**
     * Try to make the name into an imported class.
     * This method takes into account only imports (class or package)
     * found directly in this NameSpace (no parent chain).
     *
     * @param name
     *            the name
     * @return the imported class impl
     * @throws UtilEvalError
     *             the util eval error
     */
    private Class getImportedClassImpl(final String name) throws UtilEvalError {
        // Try explicitly imported class, e.g. import foo.Bar;
        String fullname = null;
        if (this.importedClasses != null)
            fullname = (String) this.importedClasses.get(name);
        // not sure if we should really recurse here for explicitly imported
        // class in parent...
        if (fullname != null) {
            /*
             * Found the full name in imported classes.
             */
            // Try to make the full imported name
            Class clas = this.classForName(fullname);
            // Handle imported inner class case
            if (clas == null) {
                // Imported full name wasn't found as an absolute class
                // If it is compound, try to resolve to an inner class.
                // (maybe this should happen in the BshClassManager?)
                if (Name.isCompound(fullname))
                    try {
                        clas = this.getNameResolver(fullname).toClass();
                    } catch (final ClassNotFoundException e) { /* not a class */ }
                else if (Interpreter.DEBUG)
                    Interpreter.debug(
                            "imported unpackaged name not found:" + fullname);
                // If found cache the full name in the BshClassManager
                if (clas != null) {
                    // (should we cache info in not a class case too?)
                    this.getClassManager().cacheClassInfo(fullname, clas);
                    return clas;
                }
            } else
                return clas;
            // It was explicitly imported, but we don't know what it is.
            // should we throw an error here??
            return null;
        }
        /*
         * Try imported packages, e.g. "import foo.bar.*;"
         * in reverse order of import...
         * (give later imports precedence...)
         */
        if (this.importedPackages != null)
            for (int i = this.importedPackages.size() - 1; i >= 0; i--) {
                final String s = (String) this.importedPackages.elementAt(i)
                        + "." + name;
                final Class c = this.classForName(s);
                if (c != null)
                    return c;
            }
        final BshClassManager bcm = this.getClassManager();
        /*
         * Try super import if available
         * Note: we do this last to allow explicitly imported classes
         * and packages to take priority. This method will also throw an
         * error indicating ambiguity if it exists...
         */
        if (bcm.hasSuperImport()) {
            final String s = bcm.getClassNameByUnqName(name);
            if (s != null)
                return this.classForName(s);
        }
        return null;
    }

    /**
     * Class for name.
     *
     * @param name
     *            the name
     * @return the class
     */
    private Class classForName(final String name) {
        return this.getClassManager().classForName(name);
    }

    /**
     * Implements NameSource.
     *
     * @return all variable and method names in this and all parent
     *         namespaces
     */
    public String[] getAllNames() {
        final Vector vec = new Vector();
        this.getAllNamesAux(vec);
        final String[] names = new String[vec.size()];
        vec.copyInto(names);
        return names;
    }

    /**
     * Helper for implementing NameSource.
     *
     * @param vec
     *            the vec
     */
    protected void getAllNamesAux(final Vector vec) {
        final Enumeration varNames = this.variables.keys();
        while (varNames.hasMoreElements())
            vec.addElement(varNames.nextElement());
        final Enumeration methodNames = this.methods.keys();
        while (methodNames.hasMoreElements())
            vec.addElement(methodNames.nextElement());
        if (this.parent != null)
            this.parent.getAllNamesAux(vec);
    }

    /** The name source listeners. */
    Vector nameSourceListeners;

    /**
     * Implements NameSource
     * Add a listener who is notified upon changes to names in this space.
     *
     * @param listener
     *            the listener
     */
    public void addNameSourceListener(final NameSource.Listener listener) {
        if (this.nameSourceListeners == null)
            this.nameSourceListeners = new Vector();
        this.nameSourceListeners.addElement(listener);
    }

    /**
     * Perform "import *;" causing the entire classpath to be mapped.
     * This can take a while.
     *
     * @throws UtilEvalError
     *             the util eval error
     */
    public void doSuperImport() throws UtilEvalError {
        this.getClassManager().doSuperImport();
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "NameSpace: "
                + (this.nsName == null ? super.toString()
                        : this.nsName + " (" + super.toString() + ")")
                + (this.isClass ? " (isClass) " : "")
                + (this.isMethod ? " (method) " : "")
                + (this.classStatic != null ? " (class static) " : "")
                + (this.classInstance != null ? " (class instance) " : "");
    }

    /**
     * Write object.
     *
     * @param s
     *            the s
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     *
     * For serialization.
     * Don't serialize non-serializable objects.
     */
    private synchronized void writeObject(final java.io.ObjectOutputStream s)
            throws IOException {
        // clear name resolvers... don't know if this is necessary.
        this.names = null;
        s.defaultWriteObject();
    }

    /**
     * Invoke a method in this namespace with the specified args and
     * interpreter reference. No caller information or call stack is
     * required. The method will appear as if called externally from Java.
     * <p>
     *
     * @param methodName
     *            the method name
     * @param args
     *            the args
     * @param interpreter
     *            the interpreter
     * @return the object
     * @throws EvalError
     *             the eval error
     * @see bsh.This.invokeMethod(
     *      String methodName, Object [] args, Interpreter interpreter,
     *      CallStack callstack, SimpleNode callerInfo, boolean)
     */
    public Object invokeMethod(final String methodName, final Object[] args,
            final Interpreter interpreter) throws EvalError {
        return this.invokeMethod(methodName, args, interpreter, null, null);
    }

    /**
     * This method simply delegates to This.invokeMethod();.
     * <p>
     *
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
     * @return the object
     * @throws EvalError
     *             the eval error
     * @see bsh.This.invokeMethod(
     *      String methodName, Object [] args, Interpreter interpreter,
     *      CallStack callstack, SimpleNode callerInfo)
     */
    public Object invokeMethod(final String methodName, final Object[] args,
            final Interpreter interpreter, final CallStack callstack,
            final SimpleNode callerInfo) throws EvalError {
        return this.getThis(interpreter).invokeMethod(methodName, args,
                interpreter, callstack, callerInfo, false/* declaredOnly */);
    }

    /**
     * Clear all cached classes and names.
     */
    public void classLoaderChanged() {
        this.nameSpaceChanged();
    }

    /**
     * Clear all cached classes and names.
     */
    public void nameSpaceChanged() {
        this.classCache = null;
        this.names = null;
    }

    /**
     * Import standard packages. Currently:
     *
     * <pre>
     * importClass("bsh.EvalError");
     * importClass("bsh.Interpreter");
     * importPackage("javax.swing.event");
     * importPackage("javax.swing");
     * importPackage("java.awt.event");
     * importPackage("java.awt");
     * importPackage("java.net");
     * importPackage("java.util");
     * importPackage("java.io");
     * importPackage("java.lang");
     * importCommands("/bsh/commands");
     * </pre>
     */
    public void loadDefaultImports() {
        /**
         * Note: the resolver looks through these in reverse order, per
         * precedence rules... so for max efficiency put the most common
         * ones later.
         */
        this.importClass("bsh.EvalError");
        this.importClass("bsh.Interpreter");
        this.importPackage("javax.swing.event");
        this.importPackage("javax.swing");
        this.importPackage("java.awt.event");
        this.importPackage("java.awt");
        this.importPackage("java.net");
        this.importPackage("java.util");
        this.importPackage("java.io");
        this.importPackage("java.lang");
        this.importCommands("/bsh/commands");
    }

    /**
     * This is the factory for Name objects which resolve names within
     * this namespace (e.g. toObject(), toClass(), toLHS()).
     * <p>
     *
     * This was intended to support name resolver caching, allowing
     * Name objects to cache info about the resolution of names for
     * performance reasons. However this not proven useful yet.
     * <p>
     *
     * We'll leave the caching as it will at least minimize Name object
     * creation.
     * <p>
     *
     * (This method would be called getName() if it weren't already used for
     * the simple name of the NameSpace)
     * <p>
     *
     * This method was public for a time, which was a mistake.
     * Use get() instead.
     *
     * @param ambigname
     *            the ambigname
     * @return the name resolver
     */
    Name getNameResolver(final String ambigname) {
        if (this.names == null)
            this.names = new Hashtable();
        Name name = (Name) this.names.get(ambigname);
        if (name == null) {
            name = new Name(this, ambigname);
            this.names.put(ambigname, name);
        }
        return name;
    }

    /**
     * Gets the invocation line.
     *
     * @return the invocation line
     */
    public int getInvocationLine() {
        final SimpleNode node = this.getNode();
        if (node != null)
            return node.getLineNumber();
        else
            return -1;
    }

    /**
     * Gets the invocation text.
     *
     * @return the invocation text
     */
    public String getInvocationText() {
        final SimpleNode node = this.getNode();
        if (node != null)
            return node.getText();
        else
            return "<invoked from Java code>";
    }

    /**
     * This is a helper method for working inside of bsh scripts and commands.
     * In that context it is impossible to see a ClassIdentifier object
     * for what it is. Attempting to access a method on a ClassIdentifier
     * will look like a static method invocation.
     *
     * This method is in NameSpace for convenience (you don't have to import
     * bsh.ClassIdentifier to use it);
     *
     * @param ci
     *            the ci
     * @return the class
     */
    public static Class identifierToClass(final ClassIdentifier ci) {
        return ci.getTargetClass();
    }

    /**
     * Clear all variables, methods, and imports from this namespace.
     * If this namespace is the root, it will be reset to the default
     * imports.
     *
     * @see #loadDefaultImports()
     */
    public void clear() {
        this.variables = null;
        this.methods = null;
        this.importedClasses = null;
        this.importedPackages = null;
        this.importedCommands = null;
        this.importedObjects = null;
        if (this.parent == null)
            this.loadDefaultImports();
        this.classCache = null;
        this.names = null;
    }

    /**
     * Import a compiled Java object's methods and variables into this
     * namespace. When no scripted method / command or variable is found
     * locally in this namespace method / fields of the object will be
     * checked. Objects are checked in the order of import with later imports
     * taking precedence.
     * <p/>
     *
     * @param obj
     *            the obj
     *
     * Note: this impor pattern is becoming common... could factor it out into
     * an importedObject Vector class.
     */
    public void importObject(final Object obj) {
        if (this.importedObjects == null)
            this.importedObjects = new Vector();
        // If it exists, remove it and add it at the end (avoid memory leak)
        if (this.importedObjects.contains(obj))
            this.importedObjects.remove(obj);
        this.importedObjects.addElement(obj);
        this.nameSpaceChanged();
    }

    /**
     * Import static.
     *
     * @param clas
     *            the clas
     */
    public void importStatic(final Class clas) {
        if (this.importedStatic == null)
            this.importedStatic = new Vector();
        // If it exists, remove it and add it at the end (avoid memory leak)
        if (this.importedStatic.contains(clas))
            this.importedStatic.remove(clas);
        this.importedStatic.addElement(clas);
        this.nameSpaceChanged();
    }

    /**
     * Set the package name for classes defined in this namespace.
     * Subsequent sets override the package.
     *
     * @param packageName
     *            the new package
     */
    void setPackage(final String packageName) {
        this.packageName = packageName;
    }

    /**
     * Gets the package.
     *
     * @return the package
     */
    String getPackage() {
        if (this.packageName != null)
            return this.packageName;
        if (this.parent != null)
            return this.parent.getPackage();
        return null;
    }

    /**
     * If a writable property exists for the given name, set it and return true,
     * otherwise do nothing and return false.
     *
     * @param propName
     *            the prop name
     * @param value
     *            the value
     * @param interp
     *            the interp
     * @return true, if successful
     * @throws UtilEvalError
     *             the util eval error
     */
    boolean attemptSetPropertyValue(final String propName, final Object value,
            final Interpreter interp) throws UtilEvalError {
        final String accessorName = Reflect.accessorName("set", propName);
        final Class[] classArray = new Class[] {
                value == null ? null : value.getClass()};
        final BshMethod m = this.getMethod(accessorName, classArray);
        if (m != null)
            try {
                this.invokeMethod(accessorName, new Object[] {value}, interp);
                // m.invoke(new Object[] {value}, interp);
                return true;
            } catch (final EvalError ee) {
                throw new UtilEvalError(
                        "'This' property accessor threw exception: "
                                + ee.getMessage());
            }
        return false;
    }

    /**
     * Get a property from a scripted object or Primitive.VOID if no such
     * property exists.
     *
     * @param propName
     *            the prop name
     * @param interp
     *            the interp
     * @return the property value
     * @throws UtilEvalError
     *             the util eval error
     */
    Object getPropertyValue(final String propName, final Interpreter interp)
            throws UtilEvalError {
        String accessorName = Reflect.accessorName("get", propName);
        final Class[] classArray = new Class[0];
        BshMethod m = this.getMethod(accessorName, classArray);
        try {
            if (m != null)
                return m.invoke((Object[]) null, interp);
            accessorName = Reflect.accessorName("is", propName);
            m = this.getMethod(accessorName, classArray);
            if (m != null)
                return m.invoke((Object[]) null, interp);
            return Primitive.VOID;
        } catch (final EvalError ee) {
            throw new UtilEvalError("'This' property accessor threw exception: "
                    + ee.getMessage());
        }
    }
}
