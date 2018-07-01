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
import static bsh.ClassGenerator.Type.CLASS;
import static bsh.ClassGenerator.Type.INTERFACE;
import static bsh.ClassGenerator.Type.ENUM;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import bsh.org.objectweb.asm.ClassWriter;
import bsh.org.objectweb.asm.Label;
import bsh.org.objectweb.asm.MethodVisitor;
import bsh.org.objectweb.asm.Opcodes;
import bsh.org.objectweb.asm.Type;

/**
 * ClassGeneratorUtil utilizes the ASM (www.objectweb.org) bytecode generator
 * by Eric Bruneton in order to generate class "stubs" for BeanShell at
 * runtime.
 * <p/>
 * <p/>
 * Stub classes contain all of the fields of a BeanShell scripted class
 * as well as two "callback" references to BeanShell namespaces: one for
 * static methods and one for instance methods. Methods of the class are
 * delegators which invoke corresponding methods on either the static or
 * instance bsh object and then unpack and return the results. The static
 * namespace utilizes a static import to delegate variable access to the
 * class' static fields. The instance namespace utilizes a dynamic import
 * (i.e. mixin) to delegate variable access to the class' instance variables.
 * <p/>
 * <p/>
 * Constructors for the class delegate to the static initInstance() method of
 * ClassGeneratorUtil to initialize new instances of the object. initInstance()
 * invokes the instance intializer code (init vars and instance blocks) and
 * then delegates to the corresponding scripted constructor method in the
 * instance namespace. Constructors contain special switch logic which allows
 * the BeanShell to control the calling of alternate constructors (this() or
 * super() references) at runtime.
 * <p/>
 * <p/>
 * Specially named superclass delegator methods are also generated in order to
 * allow BeanShell to access overridden methods of the superclass (which
 * reflection does not normally allow).
 * <p/>
 *
 * TODO: We have hooks for generating static initializer code, now used
 * to save persistent class stubs. This must be extended to accommodate
 * general static initializer blocks.
 *
 * @author Pat Niemeyer
 */
/*
    Notes:
    It would not be hard to eliminate the use of org.objectweb.asm.Type from
    this class, making the distribution a tiny bit smaller.
*/
public class ClassGeneratorUtil implements Opcodes {

    /**
     * The name of the static field holding the reference to the bsh
     * static This (the callback namespace for static methods)
     */
    static final String BSHSTATIC = "_bshStatic";

    /**
     * The name of the instance field holding the reference to the bsh
     * instance This (the callback namespace for instance methods)
     */
    static final String BSHTHIS = "_bshThis";

    /**
     * The prefix for the name of the super delegate methods. e.g.
     * _bshSuperfoo() is equivalent to super.foo()
     */
    static final String BSHSUPER = "_bshSuper";

    /**
     * The bsh static namespace variable name of the instance initializer
     */
    static final String BSHINIT = "_bshInstanceInitializer";

    /**
     * The bsh static namespace variable that holds the constructor methods
     */
    private static final String BSHCONSTRUCTORS = "_bshConstructors";

    /**
     * The bsh static namespace variable that holds the class modifiers
     */
    static final String BSHCLASSMODIFIERS = "_bshClassModifiers";

    /**
     * The switch branch number for the default constructor.
     * The value -1 will cause the default branch to be taken.
     */
    private static final int DEFAULTCONSTRUCTOR = -1;

    private static final String OBJECT = "Ljava/lang/Object;";

    private final String className;
    private final String canonClassName;
    private final String classDescript;
    /**
     * fully qualified class name (with package) e.g. foo/bar/Blah
     */
    private final String fqClassName;
    private final String uuid;
    private final Class superClass;
    private final String superClassName;
    private final Class[] interfaces;
    private final Variable[] vars;
    private final Constructor[] superConstructors;
    private final DelayedEvalBshMethod[] constructors;
    private final DelayedEvalBshMethod[] methods;
    private static final Map<String,NameSpace> contextStore = new ConcurrentHashMap<>();
    private final Modifiers classModifiers;
    private final ClassGenerator.Type type;

    /**
     * @param packageName e.g. "com.foo.bar"
     */
    public ClassGeneratorUtil(Modifiers classModifiers, String className, String packageName, Class superClass, Class[] interfaces, Variable[] vars, DelayedEvalBshMethod[] bshmethods, NameSpace classStaticNameSpace, ClassGenerator.Type type) {
        this.classModifiers = classModifiers;
        this.className = className;
        this.type = type;
        if (packageName != null) {
            this.fqClassName = packageName.replace('.', '/') + "/" + className;
            this.canonClassName = packageName+"."+className;
        } else {
            this.fqClassName = className;
            this.canonClassName = className;
        }
        this.classDescript = "L"+fqClassName.replace('.', '/')+";";

        if (superClass == null)
            if (type == ENUM)
                superClass = Enum.class;
            else
                superClass = Object.class;
        this.superClass = superClass;
        this.superClassName = Type.getInternalName(superClass);
        if (interfaces == null)
            interfaces = new Class[0];
        this.interfaces = interfaces;
        this.vars = vars;
        classStaticNameSpace.isInterface = type == INTERFACE;
        classStaticNameSpace.isEnum = type == ENUM;
        contextStore.put(this.uuid = UUID.randomUUID().toString(), classStaticNameSpace);
        this.superConstructors = superClass.getDeclaredConstructors();

        // Split the methods into constructors and regular method lists
        List<DelayedEvalBshMethod> consl = new ArrayList<>();
        List<DelayedEvalBshMethod> methodsl = new ArrayList<>();
        String classBaseName = getBaseName(className); // for inner classes
        for (DelayedEvalBshMethod bshmethod : bshmethods)
            if (bshmethod.getName().equals(classBaseName))
                consl.add(bshmethod);
            else
                methodsl.add(bshmethod);

        constructors = consl.toArray(new DelayedEvalBshMethod[consl.size()]);
        methods = methodsl.toArray(new DelayedEvalBshMethod[methodsl.size()]);

        if (type == INTERFACE && !classModifiers.hasModifier("abstract"))
            classModifiers.addModifier("abstract");
        if (type == ENUM && !classModifiers.hasModifier("static"))
            classModifiers.addModifier("static");
    }

    /**
     * This method provides a hook for the class generator implementation to
     * store additional information in the class's bsh static namespace.
     * Currently this is used to store an array of consructors corresponding
     * to the constructor switch in the generated class.
     *
     * This method must be called to initialize the static space even if we
     * are using a previously generated class.
     */
    public void initStaticNameSpace(NameSpace classStaticNameSpace, BSHBlock instanceInitBlock) {
        try {
            classStaticNameSpace.setLocalVariable(BSHCLASSMODIFIERS, classModifiers, false/*strict*/);
            classStaticNameSpace.setLocalVariable(BSHCONSTRUCTORS, constructors, false/*strict*/);
            classStaticNameSpace.setLocalVariable(BSHINIT, instanceInitBlock, false/*strict*/);
        } catch (UtilEvalError e) {
            throw new InterpreterError("Unable to init class static block: " + e, e);
        }
    }

    /**
     * Generate the class bytecode for this class.
     */
    public byte[] generateClass() {
        NameSpace classStaticNameSpace = contextStore.get(this.uuid);
        // Force the class public for now...
        int classMods = getASMModifiers(classModifiers) | ACC_PUBLIC;
        if (type == INTERFACE)
            classMods |= ACC_INTERFACE | ACC_ABSTRACT;
        else if (type == ENUM)
            classMods |= ACC_FINAL | ACC_SUPER | ACC_ENUM;
        else if ( (classMods & ACC_ABSTRACT) > 0 )
            // bsh classes are not abstract
            classMods -= ACC_ABSTRACT;

        String[] interfaceNames = new String[interfaces.length + 1]; // +1 for GeneratedClass
        for (int i = 0; i < interfaces.length; i++) {
            interfaceNames[i] = Type.getInternalName(interfaces[i]);
            if (Reflect.isGeneratedClass(interfaces[i]))
                for (Variable v : Reflect.getVariables(interfaces[i]))
                    classStaticNameSpace.setVariableImpl(v);
        }
        // Everyone implements GeneratedClass
        interfaceNames[interfaces.length] = Type.getInternalName(GeneratedClass.class);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        String signature = type == ENUM ? "Ljava/lang/Enum<"+classDescript+">;" : null;
        cw.visit(V1_8, classMods, fqClassName, signature, superClassName, interfaceNames);

        if ( type != INTERFACE )
            // Generate the bsh instance 'This' reference holder field
            generateField(BSHTHIS+className, "Lbsh/This;", ACC_PUBLIC, cw);
        // Generate the static bsh static This reference holder field
        generateField(BSHSTATIC+className, "Lbsh/This;", ACC_PUBLIC + ACC_STATIC + ACC_FINAL, cw);
        // Generate class UUID
        generateField("UUID", "Ljava/lang/String;", ACC_PUBLIC + ACC_STATIC + ACC_FINAL, this.uuid, cw);

        // Generate the fields
        for (Variable var : vars) {
            // Don't generate private fields
            if (var.hasModifier("private"))
                continue;

            String fType = var.getTypeDescriptor();
            int modifiers = getASMModifiers(var.getModifiers());

            if ( type == INTERFACE ) {
                var.setConstant();
                classStaticNameSpace.setVariableImpl(var);
                // keep constant fields virtual
                continue;
            } else if ( type == ENUM && var.hasModifier("enum") ) {
                modifiers |= ACC_ENUM | ACC_FINAL;
                fType = classDescript;
            }

            generateField(var.getName(), fType, modifiers, cw);
        }

        if (type == ENUM)
            generateEnumSupport(fqClassName, className, classDescript, cw);

        // Generate the static initializer.
        generateStaticInitializer(cw);

        // Generate the constructors
        boolean hasConstructor = false;
        for (int i = 0; i < constructors.length; i++) {
            // Don't generate private constructors
            if (constructors[i].hasModifier("private"))
                continue;

            int modifiers = getASMModifiers(constructors[i].getModifiers());
            generateConstructor(i, constructors[i].getParamTypeDescriptors(), modifiers, cw);
            hasConstructor = true;
        }

        // If no other constructors, generate a default constructor
        if ( type == CLASS && !hasConstructor )
            generateConstructor(DEFAULTCONSTRUCTOR/*index*/, new String[0], ACC_PUBLIC, cw);

        // Generate methods
        for (DelayedEvalBshMethod method : methods) {

            // Don't generate private methods
            if (method.hasModifier("private"))
                continue;

            if ( type == INTERFACE )
                if ( !method.hasModifier("static") && !method.hasModifier("default") )
                    if ( !method.hasModifier("abstract") )
                        method.getModifiers().addModifier("abstract");
            int modifiers = getASMModifiers(method.getModifiers());
            boolean isStatic = (modifiers & ACC_STATIC) > 0;

            generateMethod(className, fqClassName, method.getName(), method.getReturnTypeDescriptor(),
                    method.getParamTypeDescriptors(), modifiers, cw);

            // check if method overrides existing method and generate super delegate.
            if ( null != classContainsMethod(superClass, method.getName(), method.getParamTypeDescriptors()) && !isStatic )
                generateSuperDelegateMethod(superClassName, method.getName(), method.getReturnTypeDescriptor(),
                        method.getParamTypeDescriptors(), ACC_PUBLIC, cw);
        }

        return cw.toByteArray();
    }

    /**
     * Translate bsh.Modifiers into ASM modifier bitflags.
     */
    private static int getASMModifiers(Modifiers modifiers) {
        int mods = 0;
        if (modifiers == null)
            return mods;

        if (modifiers.hasModifier("public"))
            mods += ACC_PUBLIC;
        if (modifiers.hasModifier("private"))
            mods += ACC_PRIVATE;
        if (modifiers.hasModifier("protected"))
            mods += ACC_PROTECTED;
        if (modifiers.hasModifier("static"))
            mods += ACC_STATIC;
        if (modifiers.hasModifier("synchronized"))
            mods += ACC_SYNCHRONIZED;
        if (modifiers.hasModifier("abstract"))
            mods += ACC_ABSTRACT;

        if ( ( mods & (ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED) ) == 0 ) {
            mods |= ACC_PUBLIC;
            modifiers.addModifier("public");
        }

        return mods;
    }

    /** Generate a field - static or instance. */
    private static void generateField(String fieldName, String type, int modifiers, ClassWriter cw) {
        generateField(fieldName, type, modifiers, null/*value*/, cw);
    }
    /** Generate field and assign initial value. */
    private static void generateField(String fieldName, String type, int modifiers, Object value, ClassWriter cw) {
        cw.visitField(modifiers, fieldName, type, null/*signature*/, value);
    }

    /**
     * Build the signature for the supplied parameter types.
     * @param paramTypes list of parameter types
     * @return parameter type signature
     */
    private static String getTypeParameterSignature(String[] paramTypes) {
        StringBuilder sb = new StringBuilder("<");
        for (final String pt : paramTypes)
            sb.append(pt).append(":");
        return sb.toString();
    }

    /** Generate support code needed for Enum types.
     * Generates enum values and valueOf methods, default private constructor with initInstance call.
     * Instead of maintaining a synthetic array of enum values we greatly reduce the required bytecode
     * needed by delegating to This.enumValues and building the array dynamically.
     * @param fqClassName fully qualified class name
     * @param className class name string
     * @param classDescript class descriptor string
     * @param cw current class writer */
    private void generateEnumSupport(String fqClassName, String className, String classDescript, ClassWriter cw) {
        // generate enum values() method delegated to static This.enumValues.
        MethodVisitor cv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "values", "()["+classDescript, null, null);
        pushBshStatic(fqClassName, className, cv);
        cv.visitMethodInsn(INVOKEVIRTUAL, "bsh/This", "enumValues", "()[Ljava/lang/Object;", false);
        generatePlainReturnCode("["+classDescript, cv);
        cv.visitMaxs(0, 0);
        // generate Enum.valueOf delegate method
        cv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "valueOf", "(Ljava/lang/String;)"+classDescript, null, null);
        cv.visitLdcInsn(Type.getType(classDescript));
        cv.visitVarInsn(ALOAD, 0);
        cv.visitMethodInsn(INVOKESTATIC, "java/lang/Enum", "valueOf", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;", false);
        generatePlainReturnCode(fqClassName, cv);
        cv.visitMaxs(0, 0);
        // generate default private constructor and initInstance call
        cv = cw.visitMethod(ACC_PRIVATE, "<init>", "(Ljava/lang/String;I)V", null, null);
        cv.visitVarInsn(ALOAD, 0);
        cv.visitVarInsn(ALOAD, 1);
        cv.visitVarInsn(ILOAD, 2);
        cv.visitMethodInsn(INVOKESPECIAL, "java/lang/Enum", "<init>", "(Ljava/lang/String;I)V", false);
        cv.visitVarInsn(ALOAD, 0);
        cv.visitLdcInsn(className);
        generateParameterReifierCode(new String[0], false/*isStatic*/, cv);
        cv.visitMethodInsn(INVOKESTATIC, "bsh/ClassGeneratorUtil", "initInstance", "(Lbsh/GeneratedClass;Ljava/lang/String;[Ljava/lang/Object;)V", false);
        cv.visitInsn(RETURN);
        cv.visitMaxs(0, 0);
    }

    /** Generate the static initialization of the enum constants. Called from clinit.
     * @param fqClassName fully qualified class name
     * @param classDescript class descriptor string
     * @param cv clinit method visitor */
    private void generateEnumStaticInit(String fqClassName, String classDescript, MethodVisitor cv) {
        int ordinal = ICONST_0;
        for ( Variable var : vars ) if ( var.hasModifier("enum") ) {
            cv.visitTypeInsn(NEW, fqClassName);
            cv.visitInsn(DUP);
            cv.visitLdcInsn(var.getName());
            if ( ICONST_5 >= ordinal )
                cv.visitInsn(ordinal++);
            else
                cv.visitIntInsn(BIPUSH, (ordinal++) - ICONST_0);
            cv.visitMethodInsn(INVOKESPECIAL, fqClassName, "<init>", "(Ljava/lang/String;I)V", false);
            cv.visitFieldInsn(PUTSTATIC, fqClassName, var.getName(), classDescript);
        }
    }

    /**
     * Generate a delegate method - static or instance.
     * The generated code packs the method arguments into an object array
     * (wrapping primitive types in bsh.Primitive), invokes the static or
     * instance This invokeMethod() method, and then returns
     * the result.
     */
    private void generateMethod(String className, String fqClassName, String methodName, String returnType, String[] paramTypes, int modifiers, ClassWriter cw) {
        String[] exceptions = null;
        boolean isStatic = (modifiers & ACC_STATIC) != 0;

        if (returnType == null) // map loose return type to Object
            returnType = OBJECT;

        String methodDescriptor = getMethodDescriptor(returnType, paramTypes);

        String paramTypesSig = getTypeParameterSignature(paramTypes);

        // Generate method body
        MethodVisitor cv = cw.visitMethod(modifiers, methodName, methodDescriptor, paramTypesSig, exceptions);

        if ((modifiers & ACC_ABSTRACT) != 0)
            return;

        // Generate code to push the BSHTHIS or BSHSTATIC field
        if ( isStatic||type == INTERFACE )
            pushBshStatic(fqClassName, className, cv);
        else
            pushBshThis(fqClassName, className, cv);

        // Push the name of the method as a constant
        cv.visitLdcInsn(methodName);

        // Generate code to push arguments as an object array
        generateParameterReifierCode(paramTypes, isStatic, cv);

        // Push the boolean constant 'true' (for declaredOnly)
        cv.visitInsn(ICONST_1);

        // Invoke the method This.invokeMethod( name, Class [] sig, boolean )
        cv.visitMethodInsn(INVOKEVIRTUAL, "bsh/This", "invokeMethod", "(Ljava/lang/String;[Ljava/lang/Object;Z)Ljava/lang/Object;", false);

        // Generate code to return the value
        generateReturnCode(returnType, cv);

        // values here are ignored, computed automatically by ClassWriter
        cv.visitMaxs(0, 0);
    }

    /**
     * Generate a constructor.
     */
    void generateConstructor(int index, String[] paramTypes, int modifiers, ClassWriter cw) {
        /** offset after params of the args object [] var */
        final int argsVar = paramTypes.length + 1;
        /** offset after params of the ConstructorArgs var */
        final int consArgsVar = paramTypes.length + 2;

        String[] exceptions = null;
        String methodDescriptor = getMethodDescriptor("V", paramTypes);

        String paramTypesSig = getTypeParameterSignature(paramTypes);

        // Create this constructor method
        MethodVisitor cv = cw.visitMethod(modifiers, "<init>", methodDescriptor, paramTypesSig, exceptions);

        // Generate code to push arguments as an object array
        generateParameterReifierCode(paramTypes, false/*isStatic*/, cv);
        cv.visitVarInsn(ASTORE, argsVar);

        // Generate the code implementing the alternate constructor switch
        generateConstructorSwitch(index, argsVar, consArgsVar, cv);

        // Generate code to invoke the ClassGeneratorUtil initInstance() method

        // push 'this'
        cv.visitVarInsn(ALOAD, 0);

        // Push the class/constructor name as a constant
        cv.visitLdcInsn(className);

        // Push arguments as an object array
        cv.visitVarInsn(ALOAD, argsVar);

        // invoke the initInstance() method
        cv.visitMethodInsn(INVOKESTATIC, "bsh/ClassGeneratorUtil", "initInstance", "(Lbsh/GeneratedClass;Ljava/lang/String;[Ljava/lang/Object;)V", false);

        cv.visitInsn(RETURN);

        // values here are ignored, computed automatically by ClassWriter
        cv.visitMaxs(0, 0);
    }

    /**
     * Generate the static initializer for the class
     */
    void generateStaticInitializer(ClassWriter cw) {

        // Generate code to invoke the ClassGeneratorUtil initStatic() method
        MethodVisitor cv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null/*sig*/, null/*exceptions*/);

        // initialize _bshStaticThis
        cv.visitFieldInsn(GETSTATIC, fqClassName, "UUID", "Ljava/lang/String;");
        cv.visitMethodInsn(INVOKESTATIC, "bsh/ClassGeneratorUtil", "pullBshStatic", "(Ljava/lang/String;)Lbsh/This;", false);
        cv.visitFieldInsn(PUTSTATIC, fqClassName, BSHSTATIC+className, "Lbsh/This;");

        if ( type == ENUM )
            generateEnumStaticInit(fqClassName, classDescript, cv);

        // Invoke Class.forName() to get our class.
        // We do this here, as opposed to in the bsh static init helper method
        // in order to be sure to capture the correct classloader.
        cv.visitLdcInsn(canonClassName);
        cv.visitMethodInsn(INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", false);

        // invoke the initStatic() method
        cv.visitMethodInsn(INVOKESTATIC, "bsh/ClassGeneratorUtil", "initStatic", "(Ljava/lang/Class;)V", false);

        cv.visitInsn(RETURN);

        // values here are ignored, computed automatically by ClassWriter
        cv.visitMaxs(0, 0);
    }

    /**
     * Generate a switch with a branch for each possible alternate
     * constructor. This includes all superclass constructors and all
     * constructors of this class. The default branch of this switch is the
     * default superclass constructor.
     * <p/>
     * This method also generates the code to call the static
     * ClassGeneratorUtil
     * getConstructorArgs() method which inspects the scripted constructor to
     * find the alternate constructor signature (if any) and evaluate the
     * arguments at runtime. The getConstructorArgs() method returns the
     * actual arguments as well as the index of the constructor to call.
     */
    void generateConstructorSwitch(int consIndex, int argsVar, int consArgsVar, MethodVisitor cv) {
        Label defaultLabel = new Label();
        Label endLabel = new Label();
        int cases = superConstructors.length + constructors.length;

        Label[] labels = new Label[cases];
        for (int i = 0; i < cases; i++)
            labels[i] = new Label();

        // Generate code to call ClassGeneratorUtil to get our switch index
        // and give us args...

        // push super class name
        cv.visitLdcInsn(superClass.getName()); // use superClassName var?

        // Push the bsh static namespace field
        pushBshStatic(fqClassName, className, cv);

        // push args
        cv.visitVarInsn(ALOAD, argsVar);

        // push this constructor index number onto stack
        cv.visitIntInsn(BIPUSH, consIndex);

        // invoke the ClassGeneratorUtil getConstructorsArgs() method
        cv.visitMethodInsn(INVOKESTATIC, "bsh/ClassGeneratorUtil", "getConstructorArgs", "(Ljava/lang/String;Lbsh/This;[Ljava/lang/Object;I)" + "Lbsh/ClassGeneratorUtil$ConstructorArgs;", false);

        // store ConstructorArgs in consArgsVar
        cv.visitVarInsn(ASTORE, consArgsVar);

        // Get the ConstructorArgs selector field from ConstructorArgs

        // push ConstructorArgs
        cv.visitVarInsn(ALOAD, consArgsVar);
        cv.visitFieldInsn(GETFIELD, "bsh/ClassGeneratorUtil$ConstructorArgs", "selector", "I");

        // start switch
        cv.visitTableSwitchInsn(0/*min*/, cases - 1/*max*/, defaultLabel, labels);

        // generate switch body
        int index = 0;
        for (int i = 0; i < superConstructors.length; i++, index++)
            doSwitchBranch(index, superClassName, getTypeDescriptors(superConstructors[i].getParameterTypes()), endLabel, labels, consArgsVar, cv);
        for (int i = 0; i < constructors.length; i++, index++)
            doSwitchBranch(index, fqClassName, constructors[i].getParamTypeDescriptors(), endLabel, labels, consArgsVar, cv);

        // generate the default branch of switch
        cv.visitLabel(defaultLabel);
        // default branch always invokes no args super
        cv.visitVarInsn(ALOAD, 0); // push 'this'
        cv.visitMethodInsn(INVOKESPECIAL, superClassName, "<init>", "()V", false);

        // done with switch
        cv.visitLabel(endLabel);
    }

    // push the class static This object
    private static void pushBshStatic(String fqClassName, String className, MethodVisitor cv) {
        cv.visitFieldInsn(GETSTATIC, fqClassName, BSHSTATIC + className, "Lbsh/This;");
    }

    // push the class static This object
    private static void pushBshThis(String fqClassName, String className, MethodVisitor cv) {
        // Push 'this'
        cv.visitVarInsn(ALOAD, 0);
        // Get the instance field
        cv.visitFieldInsn(GETFIELD, fqClassName, BSHTHIS + className, "Lbsh/This;");
    }

    /*
        Generate a branch of the constructor switch.  This method is called by
        generateConstructorSwitch.
        The code generated by this method assumes that the argument array is
        on the stack.
    */
    private void doSwitchBranch(int index, String targetClassName, String[] paramTypes, Label endLabel, Label[] labels, int consArgsVar, MethodVisitor cv) {
        cv.visitLabel(labels[index]);
        //cv.visitLineNumber( index, labels[index] );
        cv.visitVarInsn(ALOAD, 0); // push this before args

        // Unload the arguments from the ConstructorArgs object
        for (String type : paramTypes) {
            final String method;
            if (type.equals("Z"))
                method = "getBoolean";
            else if (type.equals("B"))
                method = "getByte";
            else if (type.equals("C"))
                method = "getChar";
            else if (type.equals("S"))
                method = "getShort";
            else if (type.equals("I"))
                method = "getInt";
            else if (type.equals("J"))
                method = "getLong";
            else if (type.equals("D"))
                method = "getDouble";
            else if (type.equals("F"))
                method = "getFloat";
            else
                method = "getObject";

            // invoke the iterator method on the ConstructorArgs
            cv.visitVarInsn(ALOAD, consArgsVar); // push the ConstructorArgs
            String className = "bsh/ClassGeneratorUtil$ConstructorArgs";
            String retType;
            if (method.equals("getObject"))
                retType = OBJECT;
            else
                retType = type;

            cv.visitMethodInsn(INVOKEVIRTUAL, className, method, "()" + retType, false);
            // if it's an object type we must do a check cast
            if (method.equals("getObject"))
                cv.visitTypeInsn(CHECKCAST, descriptorToClassName(type));
        }

        // invoke the constructor for this branch
        String descriptor = getMethodDescriptor("V", paramTypes);
        cv.visitMethodInsn(INVOKESPECIAL, targetClassName, "<init>", descriptor, false);
        cv.visitJumpInsn(GOTO, endLabel);
    }

    private static String getMethodDescriptor(String returnType, String[] paramTypes) {
        StringBuilder sb = new StringBuilder("(");
        for (String paramType : paramTypes)
            sb.append(paramType);

        sb.append(')').append(returnType);
        return sb.toString();
    }

    /**
     * Generate a superclass method delegate accessor method.
     * These methods are specially named methods which allow access to
     * overridden methods of the superclass (which the Java reflection API
     * normally does not allow).
     */
    // Maybe combine this with generateMethod()
    private void generateSuperDelegateMethod(String superClassName, String methodName, String returnType, String[] paramTypes, int modifiers, ClassWriter cw) {
        String[] exceptions = null;

        if (returnType == null) // map loose return to Object
            returnType = OBJECT;

        String methodDescriptor = getMethodDescriptor(returnType, paramTypes);

        String paramTypesSig = getTypeParameterSignature(paramTypes);

        // Add method body
        MethodVisitor cv = cw.visitMethod(modifiers, "_bshSuper" + methodName, methodDescriptor, paramTypesSig, exceptions);

        cv.visitVarInsn(ALOAD, 0);
        // Push vars
        int localVarIndex = 1;
        for (String paramType : paramTypes) {
            if (isPrimitive(paramType))
                cv.visitVarInsn(ILOAD, localVarIndex);
            else
                cv.visitVarInsn(ALOAD, localVarIndex);
            localVarIndex += ((paramType.equals("D") || paramType.equals("J")) ? 2 : 1);
        }

        cv.visitMethodInsn(INVOKESPECIAL, superClassName, methodName, methodDescriptor, false);

        generatePlainReturnCode(returnType, cv);

        // values here are ignored, computed automatically by ClassWriter
        cv.visitMaxs(0, 0);
    }

    /** Validate abstract method implementation.
     * Check that class is abstract or implements all abstract methods. BSH classes are not abstract
     * which allows us to instantiate abstract classes.
     * Also applies inheritance rules @see checkInheritanceRules().
     * @param type The class to check.
     * @throws RuntimException if validation fails. */
    static void checkAbstractMethodImplementation(Class<?> type) {
        List<Method> methsp = new ArrayList<>();
        List<Method> methso = new ArrayList<>();
        Reflect.gatherMethodsRecursive(type, null, 0, methsp, methso);

        Stream.concat(methsp.stream(), methso.stream())
        .filter( m -> ( m.getModifiers() & ACC_ABSTRACT ) > 0 )
        .forEach( method -> {
            Method[] meth = Stream.concat(methsp.stream(), methso.stream())
                .filter( m -> ( m.getModifiers() & ( ACC_ABSTRACT | ACC_PRIVATE ) ) == 0
                    && method.getName().equals(m.getName())
                    && Types.areSignaturesEqual(method.getParameterTypes(), m.getParameterTypes()))
                .sorted( (a, b) -> ( a.getModifiers() & ACC_PUBLIC ) > 0 ? 1
                        : ( a.getModifiers() & ACC_PROTECTED ) > 0 ? 0 : -1 )
                .toArray(Method[]::new);

            if ( meth.length == 0 && !Reflect.getClassModifiers(type).hasModifier("abstract") )
                throw new RuntimeException(type.getSimpleName()
                    +" is not abstract and does not override abstract method "
                    + method.getName() +"() in "+ method.getDeclaringClass().getSimpleName());
            if ( meth.length > 0)
                checkInheritanceRules(method.getModifiers(), meth[0].getModifiers(), method.getDeclaringClass());
        });
    }

    /** Apply inheritance rules. Overridden methods may not reduce visibility.
     * @param parentModifiers parent modifiers of method being overridden
     * @param overriddenModifiers overridden modifiers of new method
     * @param parentClass parent class name
     * @return true if visibility is not reduced
     * @throws RuntimeException if validation fails */
    static boolean checkInheritanceRules(int parentModifiers, int overriddenModifiers, Class<?> parentClass) {
        int prnt = parentModifiers & ( ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED );
        int chld = overriddenModifiers & ( ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED );

        if ( chld == prnt || prnt == ACC_PRIVATE || chld == ACC_PUBLIC || (prnt == 0 && chld != ACC_PRIVATE) )
            return true;

        throw new RuntimeException("Cannot reduce the visibility of the inherited method from "
                + parentClass.getName());
    }

    /** Check if method name and type descriptor signature is overridden.
     * @param clas super class
     * @param methodName name of method
     * @param paramTypes type descriptor of parameter types
     * @return matching method or null if not found */
    static Method classContainsMethod(Class<?> clas, String methodName, String[] paramTypes) {
        while ( clas != null ) {
            for ( Method method : clas.getDeclaredMethods() )
                if ( method.getName().equals(methodName)
                        && paramTypes.length == method.getParameterCount() ) {
                    String[] methodParamTypes = getTypeDescriptors(method.getParameterTypes());
                    boolean found = true;
                    for ( int j = 0; j < paramTypes.length; j++ )
                        if (false == (found = paramTypes[j].equals(methodParamTypes[j])))
                            break;
                    if (found) return method;
                }
            clas = clas.getSuperclass();
        }
        return null;
    }

    /**
     * Generate return code for a normal bytecode
     */
    private static void generatePlainReturnCode(String returnType, MethodVisitor cv) {
        if (returnType.equals("V"))
            cv.visitInsn(RETURN);
        else if (isPrimitive(returnType)) {
            int opcode = IRETURN;
            if (returnType.equals("D"))
                opcode = DRETURN;
            else if (returnType.equals("F"))
                opcode = FRETURN;
            else if (returnType.equals("J")) //long
                opcode = LRETURN;

            cv.visitInsn(opcode);
        } else {
            cv.visitTypeInsn(CHECKCAST, descriptorToClassName(returnType));
            cv.visitInsn(ARETURN);
        }
    }

    /**
     * Generates the code to reify the arguments of the given method.
     * For a method "int m (int i, String s)", this code is the bytecode
     * corresponding to the "new Object[] { new bsh.Primitive(i), s }"
     * expression.
     *
     * @author Eric Bruneton
     * @author Pat Niemeyer
     * @param cv the code visitor to be used to generate the bytecode.
     * @param isStatic the enclosing methods is static
     */
    private void generateParameterReifierCode(String[] paramTypes, boolean isStatic, final MethodVisitor cv) {
        cv.visitIntInsn(SIPUSH, paramTypes.length);
        cv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
        int localVarIndex = isStatic ? 0 : 1;
        for (int i = 0; i < paramTypes.length; ++i) {
            String param = paramTypes[i];
            cv.visitInsn(DUP);
            cv.visitIntInsn(SIPUSH, i);
            if (isPrimitive(param)) {
                int opcode;
                if (param.equals("F"))
                    opcode = FLOAD;
                else if (param.equals("D"))
                    opcode = DLOAD;
                else if (param.equals("J"))
                    opcode = LLOAD;
                else
                    opcode = ILOAD;

                String type = "bsh/Primitive";
                cv.visitTypeInsn(NEW, type);
                cv.visitInsn(DUP);
                cv.visitVarInsn(opcode, localVarIndex);
                String desc = param; // ok?
                cv.visitMethodInsn(INVOKESPECIAL, type, "<init>", "(" + desc + ")V", false);
            } else {
                // Technically incorrect here - we need to wrap null values
                // as bsh.Primitive.NULL.  However the This.invokeMethod()
                // will do that much for us.
                // We need to generate a conditional here to test for null
                // and return Primitive.NULL
                cv.visitVarInsn(ALOAD, localVarIndex);
            }
            cv.visitInsn(AASTORE);
            localVarIndex += ((param.equals("D") || param.equals("J")) ? 2 : 1);
        }
    }

    /**
     * Generates the code to unreify the result of the given method. For a
     * method "int m (int i, String s)", this code is the bytecode
     * corresponding to the "((Integer)...).intValue()" expression.
     *
     * @param cv the code visitor to be used to generate the bytecode.
     * @author Eric Bruneton
     * @author Pat Niemeyer
     */
    private void generateReturnCode(String returnType, MethodVisitor cv) {
        if (returnType.equals("V")) {
            cv.visitInsn(POP);
            cv.visitInsn(RETURN);
        } else if (isPrimitive(returnType)) {
            int opcode = IRETURN;
            String type;
            String meth;
            if (returnType.equals("B")) {
                type = "java/lang/Byte";
                meth = "byteValue";
            } else if (returnType.equals("I")) {
                type = "java/lang/Integer";
                meth = "intValue";
            } else if (returnType.equals("Z")) {
                type = "java/lang/Boolean";
                meth = "booleanValue";
            } else if (returnType.equals("D")) {
                opcode = DRETURN;
                type = "java/lang/Double";
                meth = "doubleValue";
            } else if (returnType.equals("F")) {
                opcode = FRETURN;
                type = "java/lang/Float";
                meth = "floatValue";
            } else if (returnType.equals("J")) {
                opcode = LRETURN;
                type = "java/lang/Long";
                meth = "longValue";
            } else if (returnType.equals("C")) {
                type = "java/lang/Character";
                meth = "charValue";
            } else /*if (returnType.equals("S") )*/ {
                type = "java/lang/Short";
                meth = "shortValue";
            }

            String desc = returnType;
            cv.visitTypeInsn(CHECKCAST, type); // type is correct here
            cv.visitMethodInsn(INVOKEVIRTUAL, type, meth, "()" + desc, false);
            cv.visitInsn(opcode);
        } else {
            cv.visitTypeInsn(CHECKCAST, descriptorToClassName(returnType));
            cv.visitInsn(ARETURN);
        }
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
            Object cons = classStaticThis.getNameSpace().getVariable(BSHCONSTRUCTORS);
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
        Object[] args;
        Interpreter interpreter = classStaticThis.declaringInterpreter;

        try {
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

    private static final ThreadLocal<NameSpace> CONTEXT_NAMESPACE = new ThreadLocal<>();
    private static final ThreadLocal<Interpreter> CONTEXT_INTERPRETER = new ThreadLocal<>();


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

            if ( instanceNameSpace.isEnum ) {
                Object a = instanceThis.declaringInterpreter.get(instance+"-args");
                if ( null != a ) {
                    args = (Object[]) a;
                    instanceThis.declaringInterpreter.unset(instance+"-args");
                }
            }

            // Find the constructor (now in the instance namespace)
            BshMethod constructor = instanceNameSpace.getMethod(getBaseName(className), Types.getTypes(args), true/*declaredOnly*/);

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
            throw new InterpreterError("Error in class initialization: " + e, e);
        }
    }

    /** Initialize the class instance This field and evaluate instance init block.
     * @param instance the instance this from class <init>
     * @param className the name of instance relative
     * @return instance This */
    private static This initClassInstanceThis(Object instance, String className) {
        This instanceThis = getClassInstanceThis(instance, className);
        if (null == instanceThis) {
            // Create the instance 'This' namespace, set it on the object
            // instance and invoke the instance initializer

            // Get the static This reference from the proto-instance
            This classStaticThis = getClassStaticThis(instance.getClass(), className);

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
                instanceInitBlock = (BSHBlock) classStaticThis.getNameSpace().getVariable(BSHINIT);
            } catch (Exception e) {
                throw new InterpreterError("unable to get instance initializer: " + e, e);
            }

            // evaluate the instance portion of the block in it
            try { // Evaluate the initializer block
                instanceInitBlock.evalBlock(new CallStack(instanceNameSpace), instanceThis.declaringInterpreter, true/*override*/, CLASSINSTANCE);
            } catch (Exception e) {
                throw new InterpreterError("Error in class initialization: " + e, e);
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
            This staticThis = getClassStaticThis(genClass, className);
            NameSpace classStaticNameSpace = staticThis.getNameSpace();
            Interpreter interpreter = staticThis.declaringInterpreter;

            if (null == interpreter)
                throw new UtilEvalError("No namespace or interpreter for statitc This."
                        +" Start interpreter for class not implemented yet.");
                //startInterpreterForClass(genClass); ???

            BSHBlock block = (BSHBlock) classStaticNameSpace.getVariable(BSHINIT);
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
     * Get the static bsh namespace field from the class.
     * @param className may be the name of clas itself or a superclass of clas.
     */
    private static This getClassStaticThis(Class clas, String className) {
        try {
            return (This) Reflect.getStaticFieldValue(clas, BSHSTATIC + className);
        } catch (Exception e) {
            throw new InterpreterError("Unable to get class static space: " + e, e);
        }
    }

    /**
     * Get the instance bsh namespace field from the object instance.
     * @return the class instance This object or null if the object has not
     * been initialized.
     */
    static This getClassInstanceThis(Object instance, String className) {
        try {
            Object o = Reflect.getObjectFieldValue(instance, BSHTHIS + className);
            return (This) Primitive.unwrap(o); // unwrap Primitive.Null to null
        } catch (Exception e) {
            throw new InterpreterError("Generated class: Error getting This" + e, e);
        }
    }

    /**
     * Does the type descriptor string describe a primitive type?
     */
    private static boolean isPrimitive(String typeDescriptor) {
        return typeDescriptor.length() == 1; // right?
    }

    private static String[] getTypeDescriptors(Class[] cparams) {
        String[] sa = new String[cparams.length];
        for (int i = 0; i < sa.length; i++)
            sa[i] = BSHType.getTypeDescriptor(cparams[i]);
        return sa;
    }

    /**
     * If a non-array object type, remove the prefix "L" and suffix ";".
     */
    // Can this be factored out...?
    // Should be be adding the L...; here instead?
    private static String descriptorToClassName(String s) {
        if (s.startsWith("[") || !s.startsWith("L"))
            return s;
        return s.substring(1, s.length() - 1);
    }

    /**
     * This should live in utilities somewhere.
     */
    private static String getBaseName(String className) {
        int i = className.indexOf("$");
        if (i == -1)
            return className;

        return className.substring(i + 1);
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

    /**
     * Attempt to load a script named for the class: e.g. Foo.class Foo.bsh.
     * The script is expected to (at minimum) initialize the class body.
     * That is, it should contain the scripted class definition.
     *
     * This method relies on the fact that the ClassGenerator generateClass()
     * method will detect that the generated class already exists and
     * initialize it rather than recreating it.
     *
     * The only interact that this method has with the process is to initially
     * cache the correct class in the class manager for the interpreter to
     * insure that it is found and associated with the scripted body.
     */
    public static void startInterpreterForClass(Class genClass) {
        String fqClassName = genClass.getName();
        String baseName = Name.suffix(fqClassName, 1);
        String resName = baseName + ".bsh";

        URL url = genClass.getResource(resName);
        if (null == url)
            throw new InterpreterError("Script (" + resName + ") for BeanShell generated class: " + genClass + " not found.");

        Reader reader = new InputStreamReader(genClass.getResourceAsStream(resName));

        // Set up the interpreter
        try (Interpreter bsh = new Interpreter()) {
            NameSpace globalNS = bsh.getNameSpace();
            globalNS.setName("class_" + baseName + "_global");
            globalNS.getClassManager().associateClass(genClass);

            // Source the script
            bsh.eval(reader, globalNS, resName);
        } catch (TargetError e) {
            System.out.println("Script threw exception: " + e);
            if (e.inNativeCode())
                e.printStackTrace(System.err);
        } catch (IOException | EvalError e) {
            System.out.println("Evaluation Error: " + e);
        }
    }
}
