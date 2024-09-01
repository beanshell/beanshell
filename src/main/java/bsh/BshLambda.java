package bsh;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import java.lang.reflect.WildcardType;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.WeakHashMap;

import bsh.org.objectweb.asm.ClassWriter;
import bsh.org.objectweb.asm.FieldVisitor;
import bsh.org.objectweb.asm.MethodVisitor;
import bsh.org.objectweb.asm.Opcodes;
import bsh.util.Util;

/**
 * It's the instance of lambdas written in code.
 * This class main purpose is convert to a instance of any functional interface and validate if you can convert to some specific functional interface.
 */
@SuppressWarnings("unchecked")
public abstract class BshLambda {

    private static final ByteClassLoader byteClassLoader = new ByteClassLoader();
    private static final WeakHashMap<BshLambda, Class<?>> dummyTypesLambdas = new WeakHashMap<>();

    protected final Node expressionNode;
    protected final Class<?> dummyType;

    private BshLambda(Node expressionNode) {
        this.expressionNode = expressionNode;
        this.dummyType = BshLambda.generateDummyType();
        BshLambda.dummyTypesLambdas.put(this, dummyType); // We use 'this' as key to avoid memory leaks!
    }

    private static volatile int dummyTypeCount = 1;
    private static Class<?> generateDummyType() {
        final String interfaceName = BshLambda.class.getName() + "Type" + (BshLambda.dummyTypeCount++);

        // Create an interface with version 1.8 and specified access modifiers
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC + Opcodes.ACC_ABSTRACT + Opcodes.ACC_INTERFACE, interfaceName.replace(".", "/"), null, "java/lang/Object", null);
        cw.visitEnd();

        return BshLambda.byteClassLoader.classFromBytes(interfaceName, cw.toByteArray());
    }

    /** Method with the real implementation to eval the code written */
    protected abstract Object invokeImpl(Object[] args) throws UtilEvalError, EvalError, TargetError;

    /**
     * Method to invoke this lambda where the return must be an Object of a specific Class<?>
     * @param args The args to give to the lambda
     * @param exceptionTypes An array of accepted exceptions that can be throw when invoking this lambda
     * @param returnType This specify of what type must be the return
     * @return An value that is assignable to <code>returnType</code>. Note: whether <code>returnType</code> is a primitive ( e.g., int ) the return will be a wrapper instance ( e.g., Integer )
     */
    public final <T> T invoke(Object[] args, Class<?>[] exceptionTypes, Class<T> returnType) throws Throwable {
        try {
            final Object result = returnType.isPrimitive() ? Primitive.unwrap(this.invokeImpl(args)) : this.invokeImpl(args);
            if (returnType == void.class) return null;

            try {
                return (T) Primitive.unwrap(Types.castObject(result, returnType, Types.ASSIGNMENT));
            } catch (Throwable t) {
                final String msg = String.format("Can't assign %s to %s", Types.prettyName(Types.getType(result)), Types.prettyName(returnType));
                throw new RuntimeEvalError(msg, expressionNode, null);
            }

        } catch (TargetError e) {
            for (Class<?> exceptionType: exceptionTypes)
                if (exceptionType.isInstance(e.getTarget()))
                    throw e.getTarget();
            throw new RuntimeEvalError("Can't invoke lambda: Unexpected Exception: " + e.getTarget().getMessage(), expressionNode, null, e.getTarget());
        } catch (EvalError e) {
            throw new RuntimeEvalError("Can't invoke lambda: " + e.getMessage(), expressionNode, null, e);
        } catch (UtilEvalError e) {
            throw new RuntimeEvalError(e.toEvalError(expressionNode, null));
        }
    }

    /** Creates a lambda from a lambda expression, used by {@link BSHLambdaExpression} */
    protected static BshLambda fromLambdaExpression(Node expressionNode, NameSpace declaringNameSpace, Modifiers[] paramsModifiers, Class<?>[] paramsTypes, String[] paramsNames, Node bodyNode) {
        return new BshLambdaFromLambdaExpression(expressionNode, declaringNameSpace, paramsModifiers, paramsTypes, paramsNames, bodyNode);
    }

    /** Creates a lambda from a method reference, used by BSHPrimaryExpression when the last {@link BSHPrimarySuffix} is a method reference */
    protected static BshLambda fromMethodReference(Node expressionNode, Object thisArg, String methodName) {
        return new BshLambdaFromMethodReference(expressionNode, thisArg, methodName);
    }

    /**
     * Returns if this lambda could be assignable to a specific functional interface.
     * @param from the BshLambda dummy type
     * @param to the function interface
     */
    protected abstract boolean isAssignable(Method to, int round);

    /**
     * Returns if a specific BshLambda dummy type could be assignable to a specific functional interface.
     * @param from the BshLambda dummy type
     * @param to the function interface
     */
    protected static boolean isAssignable(Class<?> from, Class<?> to, int round) {
        Method method = BshLambda.methodFromFI(to);
        for (Entry<BshLambda, Class<?>> entry: BshLambda.dummyTypesLambdas.entrySet())
            if (entry.getValue() == from)
                return entry.getKey().isAssignable(method, round);
        return false;
    }

    /**
     * Convert this lambda to a specific functional interface.
     * <p>
     * In other words, returns a wrapper that is a instance of a specific functional interface that invoke this lambda
     */
    protected <T> T convertTo(Class<T> functionalInterface) throws UtilEvalError {
        if (!BshLambda.isAssignable(this.dummyType, functionalInterface, Types.BSH_ASSIGNABLE))
            throw new UtilEvalError("This BshLambda can't be converted to " + functionalInterface.getName());
        Class<T> _class = getClassForFI(functionalInterface);

        try {
            return (T) _class.getConstructors()[0].newInstance(this);
        } catch (Throwable e) {
            throw new UtilEvalError("Can't create a instance for the generate class for the BshLambda: " + e.getMessage(), e);
        }
    }

    /** Util method to return the functional interface's method to be implemented */
    protected static Method methodFromFI(Class<?> functionalInterface) {
        for (Method method: functionalInterface.getDeclaredMethods())
            if (Modifier.isAbstract(method.getModifiers()) && !method.isDefault())
                try {
                    Object.class.getDeclaredMethod(method.getName(), method.getParameterTypes());
                } catch (NoSuchMethodException e) {
                    return method;
                }
        throw new IllegalArgumentException("This class isn't a valid Functional Interface: " + functionalInterface.getName());
    }

    /** Cache for the BshLambda wrappers classes */
    private static Map<Class<?>, Class<?>> fiClasses = new HashMap<>();
    protected static <T> Class<T> getClassForFI(Class<T> fi) {
        if (fiClasses.containsKey(fi)) return (Class<T>) fiClasses.get(fi);
        Class<T> _class = WrapperGenerator.generateClass(fi);
        fiClasses.put(fi, _class);
        return _class;
    }

    /** Implementation of BshLambda for lambda expressions */
    private static class BshLambdaFromLambdaExpression extends BshLambda {
        private final NameSpace declaringNameSpace;
        private final Modifiers[] paramsModifiers;
        private final Class<?>[] paramsTypes;
        private final String[] paramsNames;
        private final Node bodyNode;

        public BshLambdaFromLambdaExpression(Node expressionNode, NameSpace declaringNameSpace, Modifiers[] paramsModifiers, Class<?>[] paramsTypes, String[] paramsNames, Node bodyNode) {
            super(expressionNode);
            this.declaringNameSpace = declaringNameSpace != null ? declaringNameSpace.toLambdaNameSpace() : null;
            this.paramsModifiers = paramsModifiers;
            this.paramsTypes = paramsTypes;
            this.paramsNames = paramsNames;
            this.bodyNode = bodyNode;

            if (paramsModifiers.length != paramsTypes.length || paramsTypes.length != paramsNames.length)
                throw new IllegalArgumentException("The length of 'paramsModifiers', 'paramsTypes' and 'paramsNames' can't be different!");
        }

        protected final Object invokeImpl(Object[] args) throws UtilEvalError, EvalError, TargetError {
            if (args.length != this.paramsTypes.length) throw new UtilEvalError("Wrong number of arguments!");
            NameSpace nameSpace = this.initNameSpace(args);
            CallStack callStack = new CallStack(nameSpace);
            Interpreter interpreter = new Interpreter(nameSpace);

            if (this.bodyNode instanceof BSHBlock) {
                Object result = this.bodyNode.eval(callStack, interpreter);
                if (result instanceof ReturnControl) {
                    ReturnControl returnControl = (ReturnControl) result;
                    if (returnControl.kind == ReturnControl.RETURN)
                        return returnControl.value;
                }
                return null;
            }
            return this.bodyNode.eval(callStack, interpreter);
        }

        /** Initialize a name space for eval the lambda expression's body */
        private NameSpace initNameSpace(Object[] args) throws UtilEvalError {
            NameSpace nameSpace = new NameSpace(this.declaringNameSpace, "LambdaExpression");
            for (int i = 0; i < paramsNames.length; i++) {
                Class<?> paramType = this.paramsTypes[i];
                if (paramType != null)
                    nameSpace.setTypedVariable(this.paramsNames[i], paramType, args[i], this.paramsModifiers[i]);
                else
                    nameSpace.setVariable(this.paramsNames[i], args[i], false);
            }
            return nameSpace;
        }

        protected boolean isAssignable(Method to, int round) {
            Type[] toParamsTypes = to.getGenericParameterTypes();
            if (this.paramsTypes.length != toParamsTypes.length) return false;

            // TODO: validate the return type of 'this.bodyNode' ???
            return Types.isSignatureAssignable(this.paramsTypes, toParamsTypes, round);
        }

    }

    /** Implementation of BshLambda for method references */
    private static class BshLambdaFromMethodReference extends BshLambda {
        private final Object thisArg;
        private final String methodName;

        // Util variables
        private final boolean staticRef;
        private final Class<?> _class;
        private final Method[] methods;

        public BshLambdaFromMethodReference(Node expressionNode, Object thisArg, String methodName) {
            super(expressionNode);
            this.thisArg = thisArg;
            this.methodName = methodName;
            this.staticRef = this.thisArg instanceof ClassIdentifier;
            this._class = this.staticRef ? ((ClassIdentifier) this.thisArg).clas : this.thisArg.getClass();
            this.methods = this._class.isInterface()
                                ? Util.concatArrays(this._class.getMethods(), Object.class.getMethods())
                                : this._class.getMethods();
        }

        protected final Object invokeImpl(Object[] args) throws UtilEvalError, EvalError, TargetError {
            try {
                final NameSpace nameSpace = new NameSpace("MethodReferenceLambda");
                final CallStack callStack = new CallStack(nameSpace);
                final Interpreter interpreter = new Interpreter(nameSpace);

                if (!this.staticRef) return Reflect.invokeObjectMethod(this.thisArg, this.methodName, args, interpreter, callStack, this.expressionNode);
                if (this.methodName.equals("new")) return Reflect.constructObject(this._class, args);
                if (args.length == 0 || !this._class.isInstance(args[0])) return Reflect.invokeStaticMethod(nameSpace.getClassManager(), this._class, this.methodName, args, this.expressionNode);

                final Class<?>[] argsTypes = Types.getTypes(args);
                final Class<?>[] nonStaticArgsTypes = Arrays.copyOfRange(argsTypes, 1, argsTypes.length);

                for (Method method: this.methods) {
                    if (!this.methodName.equals(method.getName())) continue;

                    try {
                        if (Reflect.isStatic(method)) { // Static reference to static method
                            if (Types.isSignatureAssignable(argsTypes, method.getGenericParameterTypes(), Types.JAVA_BASE_ASSIGNABLE))
                                return method.invoke(null, args);
                        } else { // Static reference to non static method
                            if (Types.isSignatureAssignable(nonStaticArgsTypes, method.getGenericParameterTypes(), Types.JAVA_BASE_ASSIGNABLE))
                                return method.invoke(args[0], Arrays.copyOfRange(args, 1, args.length));
                        }
                    } catch (IllegalAccessException e) {}
                }

                throw new UtilEvalError("Can't invoke lambda made from method reference!");
            } catch (InvocationTargetException e) {
                throw new TargetError(e.getTargetException(), expressionNode, null);
            }
        }

        protected boolean isAssignable(Method to, int round) {
            if (!this.staticRef) { // Non-Static references
                for (Method method: _class.getMethods()) {
                    if (!this.methodName.equals(method.getName())) continue;
                    if (Reflect.isStatic(method)) continue;
                    if (!Types.isSignatureAssignable(method.getParameterTypes(), to.getGenericParameterTypes(), round)) continue;
                    if (!Types.isAssignable(method.getReturnType(), to.getGenericReturnType(), round)) continue;
                    return true;
                }
                return false;
            }

            if (this.methodName.equals("new")) { // Constructor reference
                for (Constructor<?> constructor: _class.getConstructors()) {
                    if (!Types.isSignatureAssignable(constructor.getParameterTypes(), to.getGenericParameterTypes(), round)) continue;
                    if (!Types.isAssignable(_class, to.getGenericReturnType(), round)) continue;
                    return true;
                }
                return false;
            }

            // Static reference
            for (Method method: this.methods) {
                if (!this.methodName.equals(method.getName())) continue;
                if (Reflect.isStatic(method)) { // Static reference to static method
                    if (!Types.isSignatureAssignable(method.getParameterTypes(), to.getGenericParameterTypes(), round)) continue;
                    if (!Types.isAssignable(method.getReturnType(), to.getGenericReturnType(), round)) continue;
                } else { // Static reference to non static method
                    final Class<?>[] paramTypes = Util.concatArrays(new Class<?>[] { this._class }, method.getParameterTypes());
                    if (!Types.isSignatureAssignable(paramTypes, to.getGenericParameterTypes(), round)) continue;
                    if (!Types.isAssignable(method.getReturnType(), to.getGenericReturnType(), round)) continue;
                }
                return true;
            }
            return false;
        }
    }

    /** It's a custom implementation of ClassLoader to just load a Class<?> from a byte[] */
    private static class ByteClassLoader extends ClassLoader {
        public Class<?> classFromBytes(String className, byte[] classBytes) {
            return defineClass(className, classBytes, 0, classBytes.length);
        }
    }

    /**
     * It's an util class that generate classes that extend functional interfaces
     * where the implementation is basically a wrapper of {@link BshLambda}
     */
    private static class WrapperGenerator {

        // TODO: get the FunctionalInterface args names too!
        /**
         * Return a new generated class that wraps a bshLambda. Example of a class that is generated:
         *
         * <p>
         *
         * <pre>{@code
         * import java.util.function.Function;
         *
         * public class MyClass<T, R> implements Function<T, R> {
         *  private BshLambda bshLambda;
         *
         *  public MyClass(BshLambda bshLambda) {
         *      this.bshLambda = bshLambda;
         *  }
         *
         *  public R apply(T arg1) {
         *      return this.bshLambda.invokeObject(new Object[] { arg1 }, new Class[0], Object.class);
         *  }
         * }
         * </pre>
         */
        protected static <T> Class<T> generateClass(Class<T> functionalInterface) {
            final String encodedFIName = Base64.getEncoder().encodeToString(functionalInterface.getName().getBytes()).replace('=', '_');
            final String className = BshLambda.class.getName() + "Generated" + encodedFIName;
            byte[] bytes = WrapperGenerator.generateClassBytes(className.replace(".", "/"), functionalInterface);
            // try {
            //     Files.write(Paths.get("/home/net0/git/beanshell-securityguard/generatedClass.class"), bytes);
            // } catch (IOException e) {}
            return (Class<T>) BshLambda.byteClassLoader.classFromBytes(className, bytes);
        }

        /**
         * Return the bytes of a class that wraps a bshLambda. Example of a class that is generated:
         *
         * <p>
         *
         * <pre>
         * import java.util.function.Function;
         *
         * public class MyClass implements Function {
         *  private BshLambda bshLambda;
         *
         *  public MyClass(BshLambda bshLambda) {
         *      this.bshLambda = bshLambda;
         *  }
         *
         *  public Object apply(Object arg1) {
         *      return this.bshLambda.invokeObject(new Object[] { arg1 }, new Class[0], Object.class);
         *  }
         * }
         * </pre>
         */
        private static byte[] generateClassBytes(String className, Class<?> functionalInterface) {
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

            final String[] interfacesPrimitiveNames = { Types.getInternalName(functionalInterface) };
            final String superPrimitiveName = "java/lang/Object";
            final String signature =  generateClassSignature(functionalInterface);

            cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, className, signature, superPrimitiveName, interfacesPrimitiveNames);

            // Declare the 'bshLambda' field
            FieldVisitor fieldVisitor = cw.visitField(Opcodes.ACC_PRIVATE, "bshLambda", Types.getDescriptor(BshLambda.class), null, null);
            fieldVisitor.visitEnd();

            WrapperGenerator.writeConstructor(cw, className);

            Method method = methodFromFI(functionalInterface);
            WrapperGenerator.writeMethod(cw, className, method);

            cw.visitEnd();
            return cw.toByteArray();
        }

        private static String generateClassSignature(Class<?> functionalInterface) {
            Type _interface = functionalInterface.getTypeParameters().length != 0
                                                    ? Types.createParameterizedType(functionalInterface, functionalInterface.getTypeParameters())
                                                    : functionalInterface;
            return Types.getASMClassSignature(functionalInterface.getTypeParameters(), Object.class, _interface);
        }

        /**
         * Just write the constructor in the ClassWriter. Example of a class with the constructor that is written with this method:
         *
         * <p>
         *
         * <pre>
         * public class MyClass {
         *  private BshLambda bshLambda;
         *
         *  public MyClass(BshLambda bshLambda) {
         *      this.bshLambda = bshLambda;
         *  }
         * }
         * </pre>
         */
        private static void writeConstructor(ClassWriter cw, String className) {
            // Add a default constructor
            final String constructorDescriptor = Types.getMethodDescriptor(void.class, BshLambda.class);
            MethodVisitor constructor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", constructorDescriptor, null, null);
            constructor.visitCode();

            // Default begin: Call the superclass constructor 'super()''
            constructor.visitVarInsn(Opcodes.ALOAD, 0); // Load 'this' onto the stack
            constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);

            // Write the implementation: this.bshLambda = bshLambda;
            constructor.visitVarInsn(Opcodes.ALOAD, 0); // Load this
            constructor.visitVarInsn(Opcodes.ALOAD, 1); // Load the first arg
            constructor.visitFieldInsn(Opcodes.PUTFIELD, className, "bshLambda", Types.getDescriptor(BshLambda.class)); // Set the 'bshLambda' field

            // Default end
            constructor.visitInsn(Opcodes.RETURN); // Return void
            constructor.visitMaxs(0, 0); // Set the stack sizes (obs.: the ClassWritter shou compute it by itself)
            constructor.visitEnd();
        }

        /**
         * Write the method to implement the Functional-Interface. Some examples:
         *
         * <p>First Example:</p>
         * <pre>
         * import java.util.function.Function;
         *
         * public class MyFunction implements Function {
         *  private BshLambda bshLamba;
         *
         *  public Object apply(Object arg1) {
         *      return this.bshLambda.invokeObject(new Object[] { arg1 }, new Class[0], Object.class);
         *  }
         * }
         * </pre>
         *
         * <p>Second Example:</p>
         * <pre>
         * import java.util.function.BooleanSupplier;
         *
         * public class MyBooleanSupplier implements BooleanSupplier {
         *  private BshLambda bshLamba;
         *
         *  public boolean getAsBoolean() {
         *      return this.bshLambda.invokeBoolean(new Object[0], new Class[0]);
         *  }
         * }
         * </pre>
         *
         * <p>Third Example:</p>
         * <pre>
         * import java.util.concurrent.Callable;
         *
         * public class MyCallable implements Callable {
         *  private BshLambda bshLamba;
         *
         *  public Object call() throws Exception {
         *      return this.bshLambda.invokeObject(new Object[0], new Class[] { Exception.class }, Object.class);
         *  }
         * }
         * </pre>
         *
         * <p>Fourth Example:</p>
         * <pre>
         * import java.lang.Runnable;
         *
         * public class MyRunnable implements Runnable {
         *  private BshLambda bshLamba;
         *
         *  public void run() {
         *      return this.bshLambda.invoke(new Object[0], new Class[0]);
         *  }
         * }
         * </pre>
         */
        private static void writeMethod(ClassWriter cw, String className, Method method) {
            final String BSH_LAMBDA_NAME = Types.getInternalName(BshLambda.class);
            final Parameter[] params = method.getParameters();
            final Class<?>[] exceptionTypes = method.getExceptionTypes();

            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, method.getName(), Types.getMethodDescriptor(method), Types.getASMMethodSignature(method), Types.getInternalNames(exceptionTypes));
            mv.visitCode();

            mv.visitVarInsn(Opcodes.ALOAD, 0); // Load 'this' onto the stack
            mv.visitFieldInsn(Opcodes.GETFIELD, className, "bshLambda", Types.getDescriptor(BshLambda.class)); // Get the field value

            // Define and create the Object[] array to store the 'args'
            mv.visitLdcInsn(params.length); // Size of the array
            mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");

            int paramLocalVarIndex = 1;
            for (int paramIndex = 0; paramIndex < params.length; paramIndex++) { // Load arguments onto the stack (assuming arguments)
                Class<?> paramType = params[paramIndex].getType();
                mv.visitInsn(Opcodes.DUP);
                mv.visitLdcInsn(paramIndex); // Load the array index to set the value
                if (paramType == char.class) {
                    // Load a char argument and already convert it to a Character
                    mv.visitVarInsn(Opcodes.ILOAD, paramLocalVarIndex);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
                } else if (paramType == boolean.class) {
                    // Load a boolean argument and already convert it to a Boolean
                    mv.visitVarInsn(Opcodes.ILOAD, paramLocalVarIndex);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                } else if (paramType == byte.class) {
                    // Load a byte argument and already convert it to a Byte
                    mv.visitVarInsn(Opcodes.ILOAD, paramLocalVarIndex);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
                } else if (paramType == short.class) {
                    // Load a short argument and already convert it to a Short
                    mv.visitVarInsn(Opcodes.ILOAD, paramLocalVarIndex);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false);
                } else if (paramType == int.class) {
                    // Load an int argument and already convert it to an Integer
                    mv.visitVarInsn(Opcodes.ILOAD, paramLocalVarIndex);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                } else if (paramType == long.class) {
                    // Load a long argument and already convert it to a Long
                    mv.visitVarInsn(Opcodes.LLOAD, paramLocalVarIndex);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
                } else if (paramType == float.class) {
                    // Load a float argument and already convert it to a Float
                    mv.visitVarInsn(Opcodes.FLOAD, paramLocalVarIndex);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
                } else if (paramType == double.class) {
                    // Load a double argument and already convert it to a Double
                    mv.visitVarInsn(Opcodes.DLOAD, paramLocalVarIndex);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                } else {
                    // Load an object argument
                    mv.visitVarInsn(Opcodes.ALOAD, paramLocalVarIndex);
                }
                mv.visitInsn(Opcodes.AASTORE);

                paramLocalVarIndex += paramType == long.class || paramType == double.class ? 2 : 1;
            }

            // Define and create the Class<?>[] array to store the 'exceptionTypes'
            mv.visitLdcInsn(exceptionTypes.length);
            mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Class");
            for (int i = 0; i < exceptionTypes.length; i++) {
                Class<?> exceptionType = exceptionTypes[i];
                mv.visitInsn(Opcodes.DUP);
                mv.visitLdcInsn(i);
                mv.visitLdcInsn(Types.getASMType(exceptionType));
                mv.visitInsn(Opcodes.AASTORE);
            }

            final Class<?> returnType = method.getReturnType();
            final Class<?> invokeReturnType = returnType.isPrimitive() ? Primitive.boxType(returnType) : returnType;

            if (returnType.isPrimitive())
                mv.visitFieldInsn(Opcodes.GETSTATIC, Types.getInternalName(invokeReturnType), "TYPE", "Ljava/lang/Class;"); // Primitive type
            else
                mv.visitLdcInsn(Types.getASMType(returnType)); // Other types, just load it :P

            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BSH_LAMBDA_NAME, "invoke", "([Ljava/lang/Object;[Ljava/lang/Class;Ljava/lang/Class;)Ljava/lang/Object;", false);
            mv.visitTypeInsn(Opcodes.CHECKCAST, Types.getInternalName(invokeReturnType));

            if (returnType == void.class) {
                mv.visitInsn(Opcodes.POP);
                mv.visitInsn(Opcodes.RETURN);
            } else if (returnType == boolean.class) {
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
                mv.visitInsn(Opcodes.IRETURN);
            } else if (returnType == char.class) {
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C", false);
                mv.visitInsn(Opcodes.IRETURN);
            } else if (returnType == byte.class) {
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B", false);
                mv.visitInsn(Opcodes.IRETURN);
            } else if (returnType == short.class) {
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S", false);
                mv.visitInsn(Opcodes.IRETURN);
            } else if (returnType == int.class) {
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                mv.visitInsn(Opcodes.IRETURN);
            } else if (returnType == long.class) {
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
                mv.visitInsn(Opcodes.LRETURN);
            } else if (returnType == float.class) {
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false);
                mv.visitInsn(Opcodes.FRETURN);
            } else if (returnType == double.class) {
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
                mv.visitInsn(Opcodes.DRETURN);
            } else {
                mv.visitInsn(Opcodes.ARETURN);
            }

            mv.visitMaxs(0, 0); // The Writter must calculate the values by itself.
            mv.visitEnd();
        }

    }
}
