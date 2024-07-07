package bsh;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.WeakHashMap;

import bsh.org.objectweb.asm.ClassWriter;
import bsh.org.objectweb.asm.FieldVisitor;
import bsh.org.objectweb.asm.MethodVisitor;
import bsh.org.objectweb.asm.Opcodes;
import bsh.org.objectweb.asm.Type;

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

    /** Method to invoke the lambda and deal with the expected exceptions */
    public final Object invoke(Object[] args, Class<?>[] exceptionTypes) throws Throwable {
        try {
            return Primitive.unwrap(this.invokeImpl(args));
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

    /** Method to invoke the lambda where the return must be a char */
    public final char invokeChar(Object[] args, Class<?>[] exceptionTypes) throws Throwable {
        Object result = this.invoke(args, exceptionTypes);
        if (result instanceof Character) return ((Character) result).charValue();
        throw new RuntimeEvalError("Can't convert " + StringUtil.typeString(result) + " to char", expressionNode, null);
    }

    /** Method to invoke the lambda where the return must be a boolean */
    public final boolean invokeBoolean(Object[] args, Class<?>[] exceptionTypes) throws Throwable {
        Object result = this.invoke(args, exceptionTypes);
        if (result instanceof Boolean) return ((Boolean) result).booleanValue();
        throw new RuntimeEvalError("Can't convert " + StringUtil.typeString(result) + " to boolean", expressionNode, null);
    }

    /** Method to invoke the lambda where the return must be a byte */
    public final byte invokeByte(Object[] args, Class<?>[] exceptionTypes) throws Throwable {
        Object result = this.invoke(args, exceptionTypes);
        if (result instanceof Number) return ((Number) result).byteValue();
        throw new RuntimeEvalError("Can't convert " + StringUtil.typeString(result) + " to byte", expressionNode, null);
    }

    /** Method to invoke the lambda where the return must be a boolean */
    public final short invokeShort(Object[] args, Class<?>[] exceptionTypes) throws Throwable {
        Object result = this.invoke(args, exceptionTypes);
        if (result instanceof Number) return ((Number) result).shortValue();
        throw new RuntimeEvalError("Can't convert " + StringUtil.typeString(result) + " to short", expressionNode, null);
    }

    /** Method to invoke the lambda where the return must be an int */
    public final int invokeInt(Object[] args, Class<?>[] exceptionTypes) throws Throwable {
        Object result = this.invoke(args, exceptionTypes);
        if (result instanceof Number) return ((Number) result).intValue();
        throw new RuntimeEvalError("Can't convert " + StringUtil.typeString(result) + " to int", expressionNode, null);
    }

    /** Method to invoke the lambda where the return must be a long */
    public final long invokeLong(Object[] args, Class<?>[] exceptionTypes) throws Throwable {
        Object result = this.invoke(args, exceptionTypes);
        if (result instanceof Number) return ((Number) result).longValue();
        throw new RuntimeEvalError("Can't convert " + StringUtil.typeString(result) + " to long", expressionNode, null);
    }

    /** Method to invoke the lambda where the return must be a float */
    public final float invokeFloat(Object[] args, Class<?>[] exceptionTypes) throws Throwable {
        Object result = this.invoke(args, exceptionTypes);
        if (result instanceof Number) return ((Number) result).floatValue();
        throw new RuntimeEvalError("Can't convert " + StringUtil.typeString(result) + " to float", expressionNode, null);
    }

    /** Method to invoke the lambda where the return must be a double */
    public final double invokeDouble(Object[] args, Class<?>[] exceptionTypes) throws Throwable {
        Object result = this.invoke(args, exceptionTypes);
        if (result instanceof Number) return ((Number) result).doubleValue();
        throw new RuntimeEvalError("Can't convert " + StringUtil.typeString(result) + " to double", expressionNode, null);
    }

    /** Method to invoke the lambda where the return must be an Object of a specific Class<?> */
    public final <T> T invokeObject(Object[] args, Class<?>[] exceptionTypes, Class<T> returnType) throws Throwable {
        Object result = this.invoke(args, exceptionTypes);
        if (result == null) return null;
        try {
            return (T) Types.castObject(result, returnType, Types.ASSIGNMENT);
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
        for (Method method: functionalInterface.getMethods())
            if (Modifier.isAbstract(method.getModifiers()))
                return method;
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
            Class<?>[] toParamsTypes = to.getParameterTypes();
            if (this.paramsTypes.length != toParamsTypes.length) return false;

            // TODO: validate the return type of 'this.bodyNode' ???
            return Types.isSignatureAssignable(this.paramsTypes, toParamsTypes, round);
        }

    }

    /** Implementation of BshLambda for method references */
    private static class BshLambdaFromMethodReference extends BshLambda {
        private final Object thisArg;
        private final String methodName;

        public BshLambdaFromMethodReference(Node expressionNode, Object thisArg, String methodName) {
            super(expressionNode);
            this.thisArg = thisArg;
            this.methodName = methodName;
        }

        protected final Object invokeImpl(Object[] args) throws UtilEvalError, EvalError, TargetError {
            try {
                NameSpace nameSpace = new NameSpace("MethodReferenceLambda");
                CallStack callStack = new CallStack(nameSpace);
                Interpreter interpreter = new Interpreter(nameSpace);

                if (thisArg instanceof ClassIdentifier) {
                    Class<?> _class = ((ClassIdentifier) thisArg).clas;
                    return methodName.equals("new")
                            ? Reflect.constructObject(_class, args)
                            : Reflect.invokeStaticMethod(nameSpace.getClassManager(), _class, methodName, args, expressionNode);
                }
                return Reflect.invokeObjectMethod(this.thisArg, methodName, args, interpreter, callStack, expressionNode);
            } catch (InvocationTargetException e) {
                throw new TargetError(e.getTargetException(), expressionNode, null);
            }
        }

        protected boolean isAssignable(Method to, int round) {
            boolean staticMethod = this.thisArg instanceof ClassIdentifier;
            Class<?> _class = this.thisArg instanceof ClassIdentifier ? ((ClassIdentifier) this.thisArg).clas : this.thisArg.getClass();

            if (this.thisArg instanceof ClassIdentifier && this.methodName.equals("new")) {
                for (Constructor<?> constructor: _class.getConstructors()) {
                    if (!Types.isSignatureAssignable(constructor.getParameterTypes(), to.getParameterTypes(), round)) continue;
                    if (!Types.isAssignable(_class, to.getReturnType(), round)) continue;
                    return true;
                }
                return false;
            }

            for (Method method: _class.getMethods()) {
                if (!this.methodName.equals(method.getName())) continue;
                if (Reflect.isStatic(method) != staticMethod) continue;
                if (!Types.isSignatureAssignable(method.getParameterTypes(), to.getParameterTypes(), round)) continue;
                if (!Types.isAssignable(method.getReturnType(), to.getReturnType(), round)) continue;
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

        private static String[] getInternalNames(Class<?>[] types) {
            final String[] internalNames = new String[types.length];
            for (int i = 0; i < types.length; i++) internalNames[i] = Type.getInternalName(types[i]);
            return internalNames;
        }

        /**
         * Return a new generated class that wraps a bshLambda. Example of a class that is generated:
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
        protected static <T> Class<T> generateClass(Class<T> functionalInterface) {
            final String encodedFIName = Base64.getEncoder().encodeToString(functionalInterface.getName().getBytes());
            final String className = BshLambda.class.getName() + "Generated" + encodedFIName;
            byte[] bytes = WrapperGenerator.generateClassBytes(className.replace(".", "/"), functionalInterface);
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

            final String[] interfacesPrimitiveNames = { Type.getInternalName(functionalInterface) };
            final String superPrimitiveName = "java/lang/Object";
            cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, className, null, superPrimitiveName, interfacesPrimitiveNames);

            // Declare the 'bshLambda' field
            FieldVisitor fieldVisitor = cw.visitField(Opcodes.ACC_PRIVATE, "bshLambda", Type.getDescriptor(BshLambda.class), null, null);
            fieldVisitor.visitEnd();

            WrapperGenerator.writeConstructor(cw, className);

            Method method = methodFromFI(functionalInterface);
            WrapperGenerator.writeMethod(cw, className, method);

            cw.visitEnd();
            return cw.toByteArray();
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
            final String constructorDescriptor = Type.getMethodDescriptor(Type.getType(void.class), Type.getType(BshLambda.class));
            MethodVisitor constructor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", constructorDescriptor, null, null);
            constructor.visitCode();

            // Default begin: Call the superclass constructor 'super()''
            constructor.visitVarInsn(Opcodes.ALOAD, 0); // Load 'this' onto the stack
            constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);

            // Write the implementation: this.bshLambda = bshLambda;
            constructor.visitVarInsn(Opcodes.ALOAD, 0); // Load this
            constructor.visitVarInsn(Opcodes.ALOAD, 1); // Load the first arg
            constructor.visitFieldInsn(Opcodes.PUTFIELD, className, "bshLambda", Type.getDescriptor(BshLambda.class)); // Set the 'bshLambda' field

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
            final String BSH_LAMBDA_NAME = Type.getInternalName(BshLambda.class);
            final Parameter[] params = method.getParameters();
            final Class<?>[] exceptionTypes = method.getExceptionTypes();

            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, method.getName(), Type.getMethodDescriptor(method), null, getInternalNames(exceptionTypes));
            mv.visitCode();

            mv.visitVarInsn(Opcodes.ALOAD, 0); // Load 'this' onto the stack
            mv.visitFieldInsn(Opcodes.GETFIELD, className, "bshLambda", Type.getDescriptor(BshLambda.class)); // Get the field value

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
                mv.visitLdcInsn(Type.getType(exceptionType));
                mv.visitInsn(Opcodes.AASTORE);
            }

            final Class<?> returnType = method.getReturnType();
            if (returnType == void.class) {
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BSH_LAMBDA_NAME, "invoke", "([Ljava/lang/Object;[Ljava/lang/Class;)Ljava/lang/Object;", false);
                mv.visitInsn(Opcodes.POP);
                mv.visitInsn(Opcodes.RETURN);
            } else if (returnType == boolean.class) {
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BSH_LAMBDA_NAME, "invokeBoolean", "([Ljava/lang/Object;[Ljava/lang/Class;)Z", false);
                mv.visitInsn(Opcodes.IRETURN);
            } else if (returnType == char.class) {
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BSH_LAMBDA_NAME, "invokeChar", "([Ljava/lang/Object;[Ljava/lang/Class;)C", false);
                mv.visitInsn(Opcodes.IRETURN);
            } else if (returnType == byte.class) {
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BSH_LAMBDA_NAME, "invokeByte", "([Ljava/lang/Object;[Ljava/lang/Class;)B", false);
                mv.visitInsn(Opcodes.IRETURN);
            } else if (returnType == short.class) {
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BSH_LAMBDA_NAME, "invokeShort", "([Ljava/lang/Object;[Ljava/lang/Class;)S", false);
                mv.visitInsn(Opcodes.IRETURN);
            } else if (returnType == int.class) {
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BSH_LAMBDA_NAME, "invokeInt", "([Ljava/lang/Object;[Ljava/lang/Class;)I", false);
                mv.visitInsn(Opcodes.IRETURN);
            } else if (returnType == long.class) {
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BSH_LAMBDA_NAME, "invokeLong", "([Ljava/lang/Object;[Ljava/lang/Class;)J", false);
                mv.visitInsn(Opcodes.LRETURN);
            } else if (returnType == float.class) {
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BSH_LAMBDA_NAME, "invokeFloat", "([Ljava/lang/Object;[Ljava/lang/Class;)F", false);
                mv.visitInsn(Opcodes.FRETURN);
            } else if (returnType == double.class) {
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BSH_LAMBDA_NAME, "invokeDouble", "([Ljava/lang/Object;[Ljava/lang/Class;)D", false);
                mv.visitInsn(Opcodes.DRETURN);
            } else {
                mv.visitLdcInsn(Type.getType(returnType));
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BSH_LAMBDA_NAME, "invokeObject", "([Ljava/lang/Object;[Ljava/lang/Class;Ljava/lang/Class;)Ljava/lang/Object;", false);
                mv.visitInsn(Opcodes.ARETURN);
            }

            mv.visitMaxs(0, 0); // The Writter must calculate the values by itself.
            mv.visitEnd();
        }

    }
}
