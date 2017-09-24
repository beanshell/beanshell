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

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is an implementation of the ClassGenerator interface which
 * contains generally bsh related code. The actual bytecode generation is
 * done by ClassGeneratorUtil.
 *
 * @author Pat Niemeyer (pat@pat.net)
 */
public class ClassGeneratorImpl extends ClassGenerator {

    /** {@inheritDoc} */
    @Override
    public Class generateClass(final String name, final Modifiers modifiers,
            final Class[] interfaces, final Class superClass,
            final BSHBlock block, final boolean isInterface,
            final CallStack callstack, final Interpreter interpreter)
            throws EvalError {
        // Delegate to the static method
        return generateClassImpl(name, modifiers, interfaces, superClass, block,
                isInterface, callstack, interpreter);
    }

    /** {@inheritDoc} */
    @Override
    public Object invokeSuperclassMethod(final BshClassManager bcm,
            final Object instance, final String methodName, final Object[] args)
            throws UtilEvalError, ReflectError, InvocationTargetException {
        // Delegate to the static method
        return invokeSuperclassMethodImpl(bcm, instance, methodName, args);
    }

    // This could be static
    /**
     * Change the parent of the class instance namespace.
     * This is currently used for inner class support.
     * Note: This method will likely be removed in the future.
     *
     * @param instance
     *            the instance
     * @param className
     *            the class name
     * @param parent
     *            the parent
     */
    @Override
    public void setInstanceNameSpaceParent(final Object instance,
            final String className, final NameSpace parent) {
        final This ithis = ClassGeneratorUtil.getClassInstanceThis(instance,
                className);
        ithis.getNameSpace().setParent(parent);
    }

    /**
     * If necessary, parse the BSHBlock for for the class definition and
     * generate the class using ClassGeneratorUtil.
     * This method also initializes the static block namespace and sets it
     * in the class.
     *
     * @param name
     *            the name
     * @param modifiers
     *            the modifiers
     * @param interfaces
     *            the interfaces
     * @param superClass
     *            the super class
     * @param block
     *            the block
     * @param isInterface
     *            the is interface
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     * @return the class
     * @throws EvalError
     *             the eval error
     */
    public static Class generateClassImpl(final String name,
            final Modifiers modifiers, final Class[] interfaces,
            final Class superClass, final BSHBlock block,
            final boolean isInterface, final CallStack callstack,
            final Interpreter interpreter) throws EvalError {
        // Scripting classes currently requires accessibility
        // This can be eliminated with a bit more work.
        try {
            Capabilities.setAccessibility(true);
        } catch (final Capabilities.Unavailable e) {
            throw new EvalError(
                    "Defining classes currently requires reflective Accessibility.",
                    block, callstack);
        }
        final NameSpace enclosingNameSpace = callstack.top();
        final String packageName = enclosingNameSpace.getPackage();
        final String className = enclosingNameSpace.isClass
                ? enclosingNameSpace.getName() + "$" + name
                : name;
        final String fqClassName = packageName == null ? className
                : packageName + "." + className;
        final String bshStaticFieldName = ClassGeneratorUtil.BSHSTATIC
                + className;
        final BshClassManager bcm = interpreter.getClassManager();
        // Race condition here...
        bcm.definingClass(fqClassName);
        // Create the class static namespace
        final NameSpace classStaticNameSpace = new NameSpace(enclosingNameSpace,
                className);
        classStaticNameSpace.isClass = true;
        callstack.push(classStaticNameSpace);
        // Evaluate any inner class class definitions in the block
        // effectively recursively call this method for contained classes first
        block.evalBlock(callstack, interpreter, true/* override */,
                ClassNodeFilter.CLASSCLASSES);
        // Generate the type for our class
        final Variable[] variables = getDeclaredVariables(block, callstack,
                interpreter, packageName);
        final DelayedEvalBshMethod[] methods = getDeclaredMethods(block,
                callstack, interpreter, packageName);
        // Create the class generator, which encapsulates all knowledge of the
        // structure of the class
        final ClassGeneratorUtil classGenerator = new ClassGeneratorUtil(
                modifiers, className, packageName, superClass, interfaces,
                variables, methods, isInterface);
        // Check for existing class (saved class file)
        Class clas = bcm.getAssociatedClass(fqClassName);
        // If the class isn't there then generate it.
        // Else just let it be initialized below.
        if (clas == null) {
            // generate bytecode, optionally with static init hooks to
            // bootstrap the interpreter
            final byte[] code = classGenerator
                    .generateClass(Interpreter.getSaveClasses()/* init code */);
            if (Interpreter.getSaveClasses())
                saveClasses(className, code);
            else
                clas = bcm.defineClass(fqClassName, code);
        }
        // If we're just saving clases then don't actually execute the static
        // code for the class here.
        if (!Interpreter.getSaveClasses()) {
            // Let the class generator install hooks relating to the structure
            // of
            // the class into the class static namespace. e.g. the constructor
            // array. This is necessary whether we are generating code or just
            // reinitializing a previously generated class.
            classGenerator.initStaticNameSpace(classStaticNameSpace,
                    block/* instance initializer */);
            // import the unqualified class name into parent namespace
            enclosingNameSpace.importClass(fqClassName.replace('$', '.'));
            // Give the static space its class static import
            // important to do this after all classes are defined
            classStaticNameSpace.setClassStatic(clas);
            // evaluate the static portion of the block in the static space
            block.evalBlock(callstack, interpreter, true/* override */,
                    ClassNodeFilter.CLASSSTATIC);
            if (!clas.isInterface())
                installStaticBlock(clas, bshStaticFieldName,
                        classStaticNameSpace, interpreter);
        }
        callstack.pop();
        bcm.doneDefiningClass(fqClassName);
        return clas;
    }

    /**
     * Install static block.
     *
     * @param genClass
     *            the gen class
     * @param bshStaticFieldName
     *            the bsh static field name
     * @param classStaticNameSpace
     *            the class static name space
     * @param interpreter
     *            the interpreter
     */
    private static void installStaticBlock(final Class genClass,
            final String bshStaticFieldName,
            final NameSpace classStaticNameSpace,
            final Interpreter interpreter) {
        // Set the static bsh This callback
        try {
            final LHS lhs = Reflect.getLHSStaticField(genClass,
                    bshStaticFieldName);
            lhs.assign(classStaticNameSpace.getThis(interpreter),
                    false/* strict */);
        } catch (final Exception e) {
            throw new InterpreterError("Error in class gen setup: " + e);
        }
    }

    /**
     * Save classes.
     *
     * @param className
     *            the class name
     * @param code
     *            the code
     */
    private static void saveClasses(final String className, final byte[] code) {
        final String dir = Interpreter.getSaveClassesDir();
        if (dir != null)
            try {
                final FileOutputStream out = new FileOutputStream(
                        dir + "/" + className + ".class");
                out.write(code);
                out.close();
            } catch (final IOException e) {
                e.printStackTrace();
            }
    }

    /**
     * Gets the declared variables.
     *
     * @param body
     *            the body
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     * @param defaultPackage
     *            the default package
     * @return the declared variables
     */
    static Variable[] getDeclaredVariables(final BSHBlock body,
            final CallStack callstack, final Interpreter interpreter,
            final String defaultPackage) {
        final List vars = new ArrayList();
        for (int child = 0; child < body.jjtGetNumChildren(); child++) {
            final SimpleNode node = (SimpleNode) body.jjtGetChild(child);
            if (node instanceof BSHTypedVariableDeclaration) {
                final BSHTypedVariableDeclaration tvd = (BSHTypedVariableDeclaration) node;
                final Modifiers modifiers = tvd.modifiers;
                final String type = tvd.getTypeDescriptor(callstack,
                        interpreter, defaultPackage);
                final BSHVariableDeclarator[] vardec = tvd.getDeclarators();
                for (final BSHVariableDeclarator element : vardec) {
                    final String name = element.name;
                    try {
                        final Variable var = new Variable(name, type,
                                null/* value */, modifiers);
                        vars.add(var);
                    } catch (final UtilEvalError e) {
                        // value error shouldn't happen
                    }
                }
            }
        }
        return (Variable[]) vars.toArray(new Variable[0]);
    }

    /**
     * Gets the declared methods.
     *
     * @param body
     *            the body
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     * @param defaultPackage
     *            the default package
     * @return the declared methods
     */
    static DelayedEvalBshMethod[] getDeclaredMethods(final BSHBlock body,
            final CallStack callstack, final Interpreter interpreter,
            final String defaultPackage) {
        final List methods = new ArrayList();
        for (int child = 0; child < body.jjtGetNumChildren(); child++) {
            final SimpleNode node = (SimpleNode) body.jjtGetChild(child);
            if (node instanceof BSHMethodDeclaration) {
                final BSHMethodDeclaration md = (BSHMethodDeclaration) node;
                md.insureNodesParsed();
                final Modifiers modifiers = md.modifiers;
                final String name = md.name;
                final String returnType = md.getReturnTypeDescriptor(callstack,
                        interpreter, defaultPackage);
                final BSHReturnType returnTypeNode = md.getReturnTypeNode();
                final BSHFormalParameters paramTypesNode = md.paramsNode;
                final String[] paramTypes = paramTypesNode.getTypeDescriptors(
                        callstack, interpreter, defaultPackage);
                final DelayedEvalBshMethod bm = new DelayedEvalBshMethod(name,
                        returnType, returnTypeNode,
                        md.paramsNode.getParamNames(), paramTypes,
                        paramTypesNode, md.blockNode,
                        null/* declaringNameSpace */, modifiers, callstack,
                        interpreter);
                methods.add(bm);
            }
        }
        return (DelayedEvalBshMethod[]) methods
                .toArray(new DelayedEvalBshMethod[0]);
    }

    /**
     * A node filter that filters nodes for either a class body static
     * initializer or instance initializer. In the static case only static
     * members are passed, etc.
     */
    static class ClassNodeFilter implements BSHBlock.NodeFilter {

        /** The Constant CLASSES. */
        public static final int STATIC = 0, INSTANCE = 1, CLASSES = 2;
        /** The classstatic. */
        public static ClassNodeFilter CLASSSTATIC = new ClassNodeFilter(STATIC);
        /** The classinstance. */
        public static ClassNodeFilter CLASSINSTANCE = new ClassNodeFilter(
                INSTANCE);
        /** The classclasses. */
        public static ClassNodeFilter CLASSCLASSES = new ClassNodeFilter(
                CLASSES);
        /** The context. */
        int context;

        /**
         * Instantiates a new class node filter.
         *
         * @param context
         *            the context
         */
        private ClassNodeFilter(final int context) {
            this.context = context;
        }

        /** {@inheritDoc} */
        public boolean isVisible(final SimpleNode node) {
            if (this.context == CLASSES)
                return node instanceof BSHClassDeclaration;
            // Only show class decs in CLASSES
            if (node instanceof BSHClassDeclaration)
                return false;
            if (this.context == STATIC)
                return this.isStatic(node);
            if (this.context == INSTANCE)
                return !this.isStatic(node);
            // ALL
            return true;
        }

        /**
         * Checks if is static.
         *
         * @param node
         *            the node
         * @return true, if is static
         */
        boolean isStatic(final SimpleNode node) {
            if (node instanceof BSHTypedVariableDeclaration)
                return ((BSHTypedVariableDeclaration) node).modifiers != null
                        && ((BSHTypedVariableDeclaration) node).modifiers
                                .hasModifier("static");
            if (node instanceof BSHMethodDeclaration)
                return ((BSHMethodDeclaration) node).modifiers != null
                        && ((BSHMethodDeclaration) node).modifiers
                                .hasModifier("static");
            // need to add static block here
            if (node instanceof BSHBlock)
                return ((BSHBlock) node).isStatic;
            return false;
        }
    }

    /**
     * Invoke superclass method impl.
     *
     * @param bcm
     *            the bcm
     * @param instance
     *            the instance
     * @param methodName
     *            the method name
     * @param args
     *            the args
     * @return the object
     * @throws UtilEvalError
     *             the util eval error
     * @throws ReflectError
     *             the reflect error
     * @throws InvocationTargetException
     *             the invocation target exception
     */
    public static Object invokeSuperclassMethodImpl(final BshClassManager bcm,
            final Object instance, final String methodName, final Object[] args)
            throws UtilEvalError, ReflectError, InvocationTargetException {
        final String superName = ClassGeneratorUtil.BSHSUPER + methodName;
        // look for the specially named super delegate method
        final Class clas = instance.getClass();
        Method superMethod = Reflect.resolveJavaMethod(bcm, clas, superName,
                Types.getTypes(args), false/* onlyStatic */);
        if (superMethod != null)
            return Reflect.invokeMethod(superMethod, instance, args);
        // No super method, try to invoke regular method
        // could be a superfluous "super." which is legal.
        final Class superClass = clas.getSuperclass();
        superMethod = Reflect.resolveExpectedJavaMethod(bcm, superClass,
                instance, methodName, args, false/* onlyStatic */);
        return Reflect.invokeMethod(superMethod, instance, args);
    }
}
