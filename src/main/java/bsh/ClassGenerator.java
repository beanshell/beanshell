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

import static bsh.This.Keys.BSHSUPER;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class ClassGenerator {

    enum Type { CLASS, INTERFACE, ENUM }

    private static ClassGenerator cg;

    public static ClassGenerator getClassGenerator() {
        if (cg == null) {
            cg = new ClassGenerator();
        }

        return cg;
    }

    /**
     * Parse the BSHBlock for the class definition and generate the class.
     */
    public Class generateClass(String name, Modifiers modifiers, Class[] interfaces, Class superClass, BSHBlock block, Type type, CallStack callstack, Interpreter interpreter) throws EvalError {
        // Delegate to the static method
        return generateClassImpl(name, modifiers, interfaces, superClass, block, type, callstack, interpreter);
    }

    /**
     * Invoke a super.method() style superclass method on an object instance.
     * This is not a normal function of the Java reflection API and is
     * provided by generated class accessor methods.
     */
    public Object invokeSuperclassMethod(BshClassManager bcm, Object instance, String methodName, Object[] args) throws UtilEvalError, ReflectError, InvocationTargetException {
        // Delegate to the static method
        return invokeSuperclassMethodImpl(bcm, instance, methodName, args);
    }

    /**
     * Parse the BSHBlock for for the class definition and generate the class
     * using ClassGenerator.
     */
    public static Class generateClassImpl(String name, Modifiers modifiers, Class[] interfaces, Class superClass, BSHBlock block, Type type, CallStack callstack, Interpreter interpreter) throws EvalError {
        NameSpace enclosingNameSpace = callstack.top();
        String packageName = enclosingNameSpace.getPackage();
        String className = enclosingNameSpace.isClass ? (enclosingNameSpace.getName() + "$" + name) : name;
        String fqClassName = packageName == null ? className : packageName + "." + className;
        BshClassManager bcm = interpreter.getClassManager();
        // Race condition here...
        bcm.definingClass(fqClassName);

        // Create the class static namespace
        NameSpace classStaticNameSpace = new NameSpace(enclosingNameSpace, className);
        classStaticNameSpace.isClass = true;

        callstack.push(classStaticNameSpace);

        // Evaluate any inner class class definitions in the block
        // effectively recursively call this method for contained classes first
        block.evalBlock(callstack, interpreter, true/*override*/, ClassNodeFilter.CLASSCLASSES);

        // Generate the type for our class
        Variable[] variables = getDeclaredVariables(block, callstack, interpreter, packageName);
        DelayedEvalBshMethod[] methods = getDeclaredMethods(block, callstack, interpreter, packageName, superClass);

        callstack.pop();

        // initialize static this singleton in namespace
        classStaticNameSpace.getThis(interpreter);

        // Create the class generator, which encapsulates all knowledge of the
        // structure of the class
        ClassGeneratorUtil classGenerator = new ClassGeneratorUtil(modifiers, className, packageName, superClass, interfaces, variables, methods, classStaticNameSpace, type);

        // Let the class generator install hooks relating to the structure of
        // the class into the class static namespace.  e.g. the constructor
        // array.  This is necessary whether we are generating code or just
        // reinitializing a previously generated class.
        classGenerator.initStaticNameSpace(classStaticNameSpace, block/*instance initializer*/);

        // Check for existing class (saved class file)
        Class genClass = bcm.getAssociatedClass(fqClassName);

        // If the class isn't there then generate it.
        // Else just let it be initialized below.
        if (genClass == null) {
            // generate bytecode, optionally with static init hooks to
            // bootstrap the interpreter
            byte[] code = classGenerator.generateClass();

            if (Interpreter.getSaveClasses())
                saveClasses(className, code);

            // Define the new class in the classloader
            genClass = bcm.defineClass(fqClassName, code);
            Interpreter.debug("Define ", fqClassName, " as ", genClass);
        }
        // import the unqualified class name into parent namespace
        enclosingNameSpace.importClass(fqClassName.replace('$', '.'));

        // Give the static space its class static import
        // important to do this after all classes are defined
        classStaticNameSpace.setClassStatic(genClass);

        Interpreter.debug(classStaticNameSpace);

        bcm.doneDefiningClass(fqClassName);

        if (interpreter.getStrictJava())
            ClassGeneratorUtil.checkAbstractMethodImplementation(genClass);

        return genClass;
    }

    private static void saveClasses(String className, byte[] code) {
        String dir = Interpreter.getSaveClassesDir();
        if (dir != null) try
        ( FileOutputStream out = new FileOutputStream(dir + "/" + className + ".class") ) {
            out.write(code);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static Variable[] getDeclaredVariables(BSHBlock body, CallStack callstack, Interpreter interpreter, String defaultPackage) {
        List<Variable> vars = new ArrayList<Variable>();
        for (int child = 0; child < body.jjtGetNumChildren(); child++) {
            SimpleNode node = (SimpleNode) body.jjtGetChild(child);
            if (node instanceof BSHEnumConstant) {
                BSHEnumConstant enm = (BSHEnumConstant) node;
                try {
                    Variable var = new Variable(enm.getName(),
                            enm.getType(), null/*value*/, enm.mods);
                    vars.add(var);
                } catch (UtilEvalError e) {
                    // value error shouldn't happen
                }
            } else if (node instanceof BSHTypedVariableDeclaration) {
                BSHTypedVariableDeclaration tvd = (BSHTypedVariableDeclaration) node;
                Modifiers modifiers = tvd.modifiers;
                BSHVariableDeclarator[] vardec = tvd.getDeclarators();
                for (BSHVariableDeclarator aVardec : vardec) {
                    String name = aVardec.name;
                    try {
                        Class<?> type = tvd.evalType(callstack, interpreter);
                        Variable var = new Variable(name, type, null/*value*/, modifiers);
                        vars.add(var);
                    } catch (UtilEvalError | EvalError e) {
                        // value error shouldn't happen
                    }
                }
            }
        }

        return vars.toArray(new Variable[vars.size()]);
    }

    static DelayedEvalBshMethod[] getDeclaredMethods(BSHBlock body, CallStack callstack, Interpreter interpreter, String defaultPackage, Class superClass) throws EvalError {
        List<DelayedEvalBshMethod> methods = new ArrayList<DelayedEvalBshMethod>();
        if ( callstack.top().getName().contains("$anon") ) {
            // anonymous classes need super constructor
            String classBaseName = Types.getBaseName(callstack.top().getName());
            DelayedEvalBshMethod bm = new DelayedEvalBshMethod(classBaseName,
                Reflect.findMostSpecificConstructor(
                    Types.getTypes(This.CONTEXT_ARGS.get().get(classBaseName)),
                    superClass.getConstructors()),
                callstack.top());
            methods.add(bm);
        }
        for (int child = 0; child < body.jjtGetNumChildren(); child++) {
            SimpleNode node = (SimpleNode) body.jjtGetChild(child);
            if (node instanceof BSHMethodDeclaration) {
                BSHMethodDeclaration md = (BSHMethodDeclaration) node;
                md.insureNodesParsed();
                Modifiers modifiers = md.modifiers;
                String name = md.name;
                String returnType = md.getReturnTypeDescriptor(callstack, interpreter, defaultPackage);
                BSHReturnType returnTypeNode = md.getReturnTypeNode();
                BSHFormalParameters paramTypesNode = md.paramsNode;
                String[] paramTypes = paramTypesNode.getTypeDescriptors(callstack, interpreter, defaultPackage);

                DelayedEvalBshMethod bm = new DelayedEvalBshMethod(name, returnType, returnTypeNode, md.paramsNode.getParamNames(), paramTypes, paramTypesNode, md.blockNode, null/*declaringNameSpace*/, modifiers, callstack, interpreter);

                methods.add(bm);
            }
        }
        return methods.toArray(new DelayedEvalBshMethod[methods.size()]);
    }


    /**
     * A node filter that filters nodes for either a class body static
     * initializer or instance initializer. In the static case only static
     * members are passed, etc.
     */
    static class ClassNodeFilter implements BSHBlock.NodeFilter {
        enum Context { STATIC, INSTANCE, CLASSES }

        public static ClassNodeFilter CLASSSTATIC = new ClassNodeFilter(Context.STATIC);
        public static ClassNodeFilter CLASSINSTANCE = new ClassNodeFilter(Context.INSTANCE);
        public static ClassNodeFilter CLASSCLASSES = new ClassNodeFilter(Context.CLASSES);

        Context context;

        private ClassNodeFilter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isVisible(SimpleNode node) {
            if (context == Context.CLASSES) return node instanceof BSHClassDeclaration;

            // Only show class decs in CLASSES
            if (node instanceof BSHClassDeclaration) return false;

            if (context == Context.STATIC) return isStatic(node);

            if (context == Context.INSTANCE) return !isStatic(node);

            // ALL
            return true;
        }

        boolean isStatic(SimpleNode node) {
            if ( null != node.jjtGetParent()
                    && node.jjtGetParent().jjtGetParent() instanceof BSHClassDeclaration
                    && ((BSHClassDeclaration) node.jjtGetParent().jjtGetParent()).type == Type.INTERFACE )
                return true;

            if (node instanceof BSHTypedVariableDeclaration)
                return ((BSHTypedVariableDeclaration) node).modifiers != null
                    && ((BSHTypedVariableDeclaration) node).modifiers.hasModifier("static");

            if (node instanceof BSHMethodDeclaration)
                return ((BSHMethodDeclaration) node).modifiers != null
                    && ((BSHMethodDeclaration) node).modifiers.hasModifier("static");

            if (node instanceof BSHBlock)
                return ((BSHBlock) node).isStatic;

            return false;
        }
    }

    public static Object invokeSuperclassMethodImpl(BshClassManager bcm, Object instance, String methodName, Object[] args) throws UtilEvalError, ReflectError, InvocationTargetException {
        String superName = BSHSUPER + methodName;

        // look for the specially named super delegate method
        Class clas = instance.getClass();
        Method superMethod = Reflect.resolveJavaMethod(bcm, clas, superName, Types.getTypes(args), false/*onlyStatic*/);
        if (superMethod != null) return Reflect.invokeMethod(superMethod, instance, args);

        // No super method, try to invoke regular method
        // could be a superfluous "super." which is legal.
        Class superClass = clas.getSuperclass();
        superMethod = Reflect.resolveExpectedJavaMethod(bcm, superClass, instance, methodName, args, false/*onlyStatic*/);
        return Reflect.invokeMethod(superMethod, instance, args);
    }

}
