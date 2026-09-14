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
 *                                                                           *
 *****************************************************************************/

package bsh;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

import bsh.org.objectweb.asm.ClassWriter;
import bsh.org.objectweb.asm.MethodVisitor;
import bsh.org.objectweb.asm.Opcodes;
import bsh.org.objectweb.asm.Type;

/**
    The runtime value of a lambda expression. Opaque until coerced to a
    functional interface, either by a cast or assignment ({@link #convertTo})
    or by overload resolution matching its arity marker ({@link #castLambda}).
    <p>
    Public only because generated wrapper classes, defined in other class
    loaders, must call {@link #invoke}; it is not a supported API. Script errors
    reach Java callers of a wrapper as {@link RuntimeEvalError}.
*/
public class BshLambda implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Implemented by every generated wrapper class. A wrapper for a scripted
        interface is GeneratedClass-assignable without being a script-generated
        class, so Reflect must be able to tell the two apart. */
    public interface Wrapper {}

    /** A body fits a value-returning method, a void one, or either (a
        statement expression, or a block that cannot complete normally);
        VOID_UNSURE is a void block bsh cannot be sure completes. */
    static final int VALUE = 0, VOID = 1, EITHER = 2, VOID_UNSURE = 3;

    // Overload resolution sees only argument Class[], never values, so a lambda
    // argument's type is an empty marker interface generated per descriptor
    // (Types.getTypes); its loader carries the descriptor.
    private static final String MARKER_PREFIX = BshLambda.class.getName() + "$Marker$";
    private static final AtomicLong MARKER_COUNT = new AtomicLong();

    // Weak both ways; the key is the descriptor held by the marker's own loader,
    // so an entry lives exactly as long as its marker.
    private static final Map<LambdaDescriptor, WeakReference<Class<?>>> MARKERS = new WeakHashMap<>();

    /** A node's last marker, reused only for an equal descriptor. */
    static final class Marker {
        final LambdaDescriptor descriptor;
        final Class<?> type;

        Marker(LambdaDescriptor descriptor, Class<?> type) {
            this.descriptor = descriptor;
            this.type = type;
        }
    }

    private transient volatile LambdaDescriptor descriptor;

    LambdaDescriptor descriptor() {
        LambdaDescriptor known = descriptor;
        if (known == null)
            descriptor = known = new LambdaDescriptor(shape, paramTypes, BSHLambdaExpression.result(bodyNode));
        return known;
    }

    /** The type overload resolution matches this lambda by. */
    Class<?> marker() {
        LambdaDescriptor wanted = descriptor();
        BSHLambdaExpression node = (BSHLambdaExpression) expressionNode;
        Marker cached = node.marker;
        if (cached != null && cached.descriptor.equals(wanted))
            return cached.type;
        Class<?> type = sharedMarker(wanted);
        node.marker = new Marker(wanted, type);
        return type;
    }

    private static Class<?> sharedMarker(LambdaDescriptor wanted) {
        synchronized (MARKERS) {
            WeakReference<Class<?>> ref = MARKERS.get(wanted);
            Class<?> type = ref == null ? null : ref.get();
            if (type != null)
                return type;
        }
        MarkerLoader loader = AccessController.doPrivileged(
            (PrivilegedAction<MarkerLoader>) () -> new MarkerLoader(wanted));
        Class<?> defined = loader.define();
        synchronized (MARKERS) {
            WeakReference<Class<?>> ref = MARKERS.get(wanted);
            Class<?> raced = ref == null ? null : ref.get();
            if (raced != null)
                return raced;
            MARKERS.put(loader.descriptor, new WeakReference<>(defined));
            return defined;
        }
    }

    // Parented on bsh's own loader, so bsh code may call getClassLoader() on a
    // marker without a permission check.
    private static final class MarkerLoader extends ClassLoader {
        final LambdaDescriptor descriptor;

        MarkerLoader(LambdaDescriptor descriptor) {
            super(BshLambda.class.getClassLoader());
            this.descriptor = descriptor;
        }

        Class<?> define() {
            String name = MARKER_PREFIX + MARKER_COUNT.incrementAndGet();
            ClassWriter cw = new ClassWriter(0);
            cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE,
                name.replace('.', '/'), null, "java/lang/Object", null);
            cw.visitEnd();
            byte[] bytes = cw.toByteArray();
            return defineClass(name, bytes, 0, bytes.length, BSH_DOMAIN);
        }
    }

    static long markersDefined() {
        return MARKER_COUNT.get();
    }

    static boolean isLambdaMarker(Class<?> type) {
        return type != null && type.getName().startsWith(MARKER_PREFIX)
            && type.getClassLoader() instanceof MarkerLoader;
    }

    static LambdaDescriptor descriptor(Class<?> marker) {
        return ((MarkerLoader) marker.getClassLoader()).descriptor;
    }

    /** Descriptors of the lambda arguments by position, or null without one. */
    static LambdaDescriptor[] lambdaDescriptors(Class<?>[] argumentTypes) {
        LambdaDescriptor[] lambdas = null;
        for (int i = 0; i < argumentTypes.length; i++)
            if (isLambdaMarker(argumentTypes[i])) {
                if (lambdas == null)
                    lambdas = new LambdaDescriptor[argumentTypes.length];
                lambdas[i] = descriptor(argumentTypes[i]);
            }
        return lambdas;
    }

    /** Whether a lambda typed by this marker can become toType. */
    static boolean isFunctionalTarget(Class<?> toType, Class<?> marker) {
        return descriptor(marker).fits(toType);
    }

    /** Error-message name for a marker, which must never surface as-is. */
    static String markerTypeName(Class<?> marker) {
        LambdaDescriptor lambda = descriptor(marker);
        StringBuilder name = new StringBuilder("<");
        boolean typed = false;
        for (Class<?> type : lambda.paramTypes)
            typed |= type != null;
        if (typed) {
            StringBuilder params = new StringBuilder();
            for (Class<?> type : lambda.paramTypes)
                params.append(params.length() == 0 ? "" : ", ").append(type == null ? "?" : type.getSimpleName());
            name.append("lambda (").append(params).append(')');
        } else
            name.append(lambda.arity).append("-arg lambda");
        if (lambda.result != null)
            name.append(" returning ").append(lambda.result.type == LambdaDescriptor.NullType.class
                ? "null" : lambda.result.type.getSimpleName());
        else if (lambda.shape == VOID)
            name.append(" returning void");
        return name.append('>').toString();
    }

    /**
        Types.castObject for a lambda value. fromType is BshLambda.class for a
        real cast or assignment, or an arity marker for overload resolution's
        checkOnly probe, which has no value to inspect.
    */
    static Object castLambda(Class<?> toType, Class<?> fromType, Object fromValue,
            boolean checkOnly) throws UtilEvalError {
        // Only Object holds a raw lambda: Serializable is an implementation detail, not a target.
        boolean raw = toType == Object.class || toType == BshLambda.class;
        if (checkOnly)
            return raw || isFunctionalTarget(toType, fromType) ? Types.VALID_CAST : Types.INVALID_CAST;
        if (raw)
            return fromValue;
        return ((BshLambda) fromValue).convertTo(toType);
    }

    private final Node expressionNode;
    private final NameSpace declaringNameSpace;
    // Transient, as in This: a deserialized lambda must not run (see invokeImpl).
    private final transient Interpreter declaringInterpreter;
    private final String[] paramNames;
    private final Class<?>[] paramTypes;
    private final Modifiers[] paramModifiers;
    private final Node bodyNode;
    private final int shape;

    BshLambda(Node expressionNode, NameSpace declaringNameSpace,
            Interpreter declaringInterpreter, String[] paramNames,
            Class<?>[] paramTypes, Modifiers[] paramModifiers, Node bodyNode, int shape) {
        this.expressionNode = expressionNode;
        this.declaringNameSpace = declaringNameSpace;
        this.declaringInterpreter = declaringInterpreter;
        this.paramNames = paramNames;
        this.paramTypes = paramTypes;
        this.paramModifiers = paramModifiers;
        this.bodyNode = bodyNode;
        this.shape = shape;
    }

    @Override
    public String toString() {
        return "<lambda: (" + String.join(",", paramNames) + ")>";
    }

    // ClassValue, not a static map keyed by Class: a scripted interface's
    // class loader references its class manager, which a map would pin.
    private static final ClassValue<Optional<Method>> SAM = new ClassValue<Optional<Method>>() {
        @Override
        protected Optional<Method> computeValue(Class<?> type) {
            return Optional.ofNullable(discoverSingleAbstractMethod(type));
        }
    };

    /** The single abstract method of a functional interface, or null if the
        type is not one. */
    static Method singleAbstractMethod(Class<?> type) {
        return SAM.get(type).orElse(null);
    }

    private static Method discoverSingleAbstractMethod(Class<?> type) {
        if (!type.isInterface())
            return null;
        List<Method> methods = abstractMethods(type);
        for (Method m : methods)
            if (!m.getName().equals(methods.get(0).getName())
                    || !Arrays.equals(m.getParameterTypes(), methods.get(0).getParameterTypes()))
                return null;
        // Inherited from several superinterfaces: one return type must
        // substitute for all of them, though not every pair need be related.
        for (Method candidate : methods)
            if (methods.stream().allMatch(m -> m.getReturnType().isAssignableFrom(candidate.getReturnType())))
                return candidate;
        return null;
    }

    // getMethods(), not getDeclaredMethods(): a SAM may be inherited.
    private static List<Method> abstractMethods(Class<?> type) {
        List<Method> methods = new ArrayList<>();
        for (Method m : type.getMethods())
            if (Modifier.isAbstract(m.getModifiers()) && !isObjectMethod(m))
                methods.add(m);
        return methods;
    }

    // A wrapper lives in its own runtime package, so it can implement only a
    // public interface and can name (checkcast) only public return types.
    static boolean isImplementable(Class<?> type) {
        return IMPLEMENTABLE.get(type);
    }

    private static final ClassValue<Boolean> IMPLEMENTABLE = new ClassValue<Boolean>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return Modifier.isPublic(type.getModifiers()) && nonPublicReturnType(type) == null;
        }
    };

    private static Class<?> nonPublicReturnType(Class<?> type) {
        for (Method m : abstractMethods(type)) {
            Class<?> returned = m.getReturnType();
            while (returned.isArray())
                returned = returned.getComponentType();
            if (!returned.isPrimitive() && !Modifier.isPublic(returned.getModifiers()))
                return returned;
        }
        return null;
    }

    /** A functional interface may redeclare an Object method (equals, hashCode,
        toString) without it counting as the single abstract method. */
    private static boolean isObjectMethod(Method m) {
        try {
            Object.class.getMethod(m.getName(), m.getParameterTypes());
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /** This lambda as an instance of the given functional interface. */
    @SuppressWarnings("unchecked")
    <T> T convertTo(Class<T> functionalInterface) throws UtilEvalError {
        Method sam = singleAbstractMethod(functionalInterface);
        if (sam == null)
            throw new UtilEvalError(functionalInterface.isInterface()
                ? "Not a functional interface (it must declare exactly one abstract method): "
                    + functionalInterface.getName()
                : "A lambda can only be assigned to a functional interface, not "
                    + functionalInterface.getName());
        if (sam.getParameterCount() != paramNames.length)
            throw new UtilEvalError("Cannot convert a " + paramNames.length
                + "-parameter lambda to " + functionalInterface.getName()
                + ", whose single abstract method declares " + sam.getParameterCount()
                + " parameter(s)");
        if (!Modifier.isPublic(functionalInterface.getModifiers()))
            throw new UtilEvalError("A lambda cannot implement "
                + functionalInterface.getName() + ": the interface is not public");
        Class<?> hidden = nonPublicReturnType(functionalInterface);
        if (hidden != null)
            throw new UtilEvalError("A lambda cannot implement "
                + functionalInterface.getName() + ": its return type "
                + hidden.getName() + " is not public");
        if (!descriptor().fits(functionalInterface))
            throw new UtilEvalError("A lambda cannot implement "
                + functionalInterface.getName() + ": its body does not fit the "
                + "method's void/value shape or its statically known result type");

        Class<?> wrapperClass;
        try {
            wrapperClass = wrapperClass(functionalInterface);
        } catch (RuntimeException | LinkageError e) {
            throw new UtilEvalError("Cannot generate a wrapper class for "
                + functionalInterface.getName() + ": " + e, e);
        }
        try {
            return (T) wrapperClass.getConstructor(BshLambda.class).newInstance(this);
        } catch (ReflectiveOperationException e) {
            throw new UtilEvalError("Cannot instantiate lambda wrapper for "
                + functionalInterface.getName() + ": " + e, e);
        }
    }

    // Weak both ways: a strong key would pin a scripted interface's class
    // manager, and a strong value would pin the key through the wrapper's loader.
    private static final Map<Class<?>, WeakReference<Class<?>>> WRAPPERS = new WeakHashMap<>();

    private static Class<?> wrapperClass(Class<?> functionalInterface) {
        Class<?> wrapper = cachedWrapper(functionalInterface);
        if (wrapper != null)
            return wrapper;
        // Define outside the lock: defineClass takes the interface loader's lock,
        // and holding ours meanwhile deadlocks against a thread holding theirs.
        // A racing duplicate just goes unused.
        // Privileged, like marker definition: bsh's own domain needs createClassLoader, not the script's caller.
        wrapper = AccessController.doPrivileged((PrivilegedAction<Class<?>>) () ->
            new WrapperLoader(functionalInterface.getClassLoader()).define(functionalInterface));
        synchronized (WRAPPERS) {
            Class<?> raced = cachedWrapper(functionalInterface);
            if (raced != null)
                return raced;
            WRAPPERS.put(functionalInterface, new WeakReference<>(wrapper));
            return wrapper;
        }
    }

    private static Class<?> cachedWrapper(Class<?> functionalInterface) {
        synchronized (WRAPPERS) {
            WeakReference<Class<?>> ref = WRAPPERS.get(functionalInterface);
            return ref == null ? null : ref.get();
        }
    }

    // Wrappers run with bsh's own permissions under a SecurityManager.
    private static final ProtectionDomain BSH_DOMAIN = AccessController.doPrivileged(
        (PrivilegedAction<ProtectionDomain>) BshLambda.class::getProtectionDomain);

    // Parented on the interface's own loader, like a JDK Proxy, so the wrapper
    // links against that exact interface and its signature types; never through
    // a class manager, whose defineClass reloads classes and flushes caches.
    private static final class WrapperLoader extends ClassLoader {
        WrapperLoader(ClassLoader interfaceLoader) {
            super(interfaceLoader);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals(BshLambda.class.getName()))
                return BshLambda.class;
            if (name.equals(Wrapper.class.getName()))
                return Wrapper.class;
            return super.loadClass(name, resolve);
        }

        Class<?> define(Class<?> functionalInterface) {
            String name = WrapperGenerator.className(functionalInterface);
            byte[] bytes = WrapperGenerator.generateBytes(name.replace('.', '/'), functionalInterface);
            return defineClass(name, bytes, 0, bytes.length, BSH_DOMAIN);
        }
    }

    /** Called by generated wrappers; public because they live in another class
        loader. A generated SAM declares no EvalError, so errors go unchecked. */
    @SuppressWarnings("unchecked")
    public final <T> T invoke(Object[] args, Class<T> returnType) {
        try {
            Object result = invokeImpl(args);
            if (returnType == void.class)
                return null;
            if (result == Primitive.VOID) {
                // Resolution cannot tell a void call body from a valued one.
                if (!returnType.isPrimitive() && BSHLambdaExpression.isMethodInvocation(bodyNode))
                    return null;
                throw new RuntimeEvalError("Cannot return void from a lambda declared to return "
                    + returnType.getName(), expressionNode, null);
            }
            if (returnType.isPrimitive())
                result = Primitive.unwrap(result);
            try {
                return (T) Primitive.unwrap(Types.castObject(result, returnType, Types.ASSIGNMENT));
            } catch (UtilEvalError e) {
                throw new RuntimeEvalError("Cannot return " + Types.getType(result)
                    + " from a lambda declared to return " + returnType.getName(),
                    expressionNode, null);
            }
        } catch (TargetError e) {
            throw new RuntimeEvalError(
                "Uncaught exception from lambda body: " + e.getMessage(),
                expressionNode, null, e.getTarget());
        } catch (EvalError e) {
            throw new RuntimeEvalError("Error invoking lambda: " + e.getMessage(), expressionNode, null, e);
        } catch (UtilEvalError e) {
            throw new RuntimeEvalError(e.toEvalError(expressionNode, null));
        }
    }

    /** Called by a generated wrapper's writeReplace; public for the same reason as invoke. */
    public final Object serializedForm(Class<?> functionalInterface) {
        return new SerializedWrapper(this, functionalInterface);
    }

    // Wrapper classes exist only in the JVM that generated them, so a wrapper
    // travels as its lambda and interface and is regenerated on read.
    private static final class SerializedWrapper implements Serializable {
        private static final long serialVersionUID = 1L;
        private final BshLambda lambda;
        private final Class<?> functionalInterface;

        SerializedWrapper(BshLambda lambda, Class<?> functionalInterface) {
            this.lambda = lambda;
            this.functionalInterface = functionalInterface;
        }

        private Object readResolve() throws ObjectStreamException {
            try {
                return lambda.convertTo(functionalInterface);
            } catch (UtilEvalError e) {
                InvalidObjectException invalid = new InvalidObjectException(e.getMessage());
                invalid.initCause(e);
                throw invalid;
            }
        }
    }

    private Object invokeImpl(Object[] args) throws UtilEvalError, EvalError {
        // Running script code read from a stream would make this a deserialization gadget.
        if (declaringInterpreter == null)
            throw new UtilEvalError("A deserialized lambda cannot run: it has no interpreter");
        // Never reuse the declaring call stack: by the time Java calls back it
        // has been popped. The declaring namespace is shared, not copied.
        NameSpace nameSpace = new LambdaNameSpace(declaringNameSpace);
        for (int i = 0; i < paramNames.length; i++) {
            if (paramTypes[i] != null)
                nameSpace.setTypedVariable(paramNames[i], paramTypes[i], args[i], paramModifiers[i]);
            else
                nameSpace.setLocalVariable(paramNames[i], args[i], false);
        }
        CallStack callstack = new CallStack(nameSpace);

        Object result = bodyNode.eval(callstack, declaringInterpreter);
        if (bodyNode instanceof BSHBlock) {
            if (result instanceof ReturnControl) {
                ReturnControl rc = (ReturnControl) result;
                if (rc.kind != ReturnControl.RETURN)
                    throw new EvalException("'continue' or 'break' in lambda body",
                        rc.returnPoint, callstack);
                return rc.value;
            }
            return Primitive.VOID;
        }
        return result;
    }

    /** Parameters and body locals stay local, but like a Java lambda (and a
        BlockNameSpace) it has no this or super of its own. */
    private static final class LambdaNameSpace extends NameSpace {
        LambdaNameSpace(NameSpace declaringNameSpace) {
            super(declaringNameSpace, "LambdaExpression");
        }

        @Override
        public This getThis(Interpreter declaringInterpreter) {
            return getParent().getThis(declaringInterpreter);
        }

        @Override
        public This getSuper(Interpreter declaringInterpreter) {
            return getParent().getSuper(declaringInterpreter);
        }
    }

    /** Generates a real implementation class (not a Proxy) so the interface's
        default methods work unmodified. */
    private static final class WrapperGenerator {

        static String className(Class<?> functionalInterface) {
            return BshLambda.class.getName() + "$Wrapper$" + functionalInterface.getName().replace('.', '_');
        }

        private static byte[] generateBytes(String internalClassName, Class<?> functionalInterface) {
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, internalClassName, null,
                "java/lang/Object", new String[] {
                    Type.getInternalName(functionalInterface), Type.getInternalName(Wrapper.class),
                    Type.getInternalName(Serializable.class) });

            cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "bshLambda",
                Type.getDescriptor(BshLambda.class), null, null).visitEnd();

            writeConstructor(cw, internalClassName);
            // A covariant diamond inherits one method under several descriptors.
            Set<String> names = new HashSet<>();
            for (Method m : abstractMethods(functionalInterface))
                if (names.add(m.getName() + Type.getMethodDescriptor(m)))
                    writeMethod(cw, internalClassName, m);
            if (!names.contains(WRITE_REPLACE))
                writeWriteReplace(cw, internalClassName, functionalInterface);

            cw.visitEnd();
            return cw.toByteArray();
        }

        private static void writeConstructor(ClassWriter cw, String internalClassName) {
            String descriptor = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(BshLambda.class));
            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", descriptor, null, null);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitFieldInsn(Opcodes.PUTFIELD, internalClassName, "bshLambda",
                Type.getDescriptor(BshLambda.class));
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        private static final String WRITE_REPLACE = "writeReplace()Ljava/lang/Object;";

        private static void writeWriteReplace(ClassWriter cw, String internalClassName,
                Class<?> functionalInterface) {
            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "writeReplace",
                "()Ljava/lang/Object;", null, null);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitFieldInsn(Opcodes.GETFIELD, internalClassName, "bshLambda",
                Type.getDescriptor(BshLambda.class));
            mv.visitLdcInsn(Type.getType(functionalInterface));
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(BshLambda.class),
                "serializedForm", "(Ljava/lang/Class;)Ljava/lang/Object;", false);
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        private static void writeMethod(ClassWriter cw, String internalClassName, Method sam) {
            String bshLambdaInternalName = Type.getInternalName(BshLambda.class);
            Parameter[] params = sam.getParameters();

            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, sam.getName(),
                Type.getMethodDescriptor(sam), null, null);
            mv.visitCode();

            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitFieldInsn(Opcodes.GETFIELD, internalClassName, "bshLambda",
                Type.getDescriptor(BshLambda.class));

            mv.visitLdcInsn(params.length);
            mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");

            int localVarIndex = 1;
            for (int i = 0; i < params.length; i++) {
                Class<?> paramType = params[i].getType();
                mv.visitInsn(Opcodes.DUP);
                mv.visitLdcInsn(i);
                localVarIndex = boxArgument(mv, paramType, localVarIndex);
                mv.visitInsn(Opcodes.AASTORE);
            }

            Class<?> returnType = sam.getReturnType();
            if (returnType.isPrimitive())
                mv.visitFieldInsn(Opcodes.GETSTATIC, primitiveWrapperInternalName(returnType),
                    "TYPE", "Ljava/lang/Class;");
            else
                mv.visitLdcInsn(Type.getType(returnType));

            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, bshLambdaInternalName, "invoke",
                "([Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Object;", false);

            finishReturn(mv, returnType);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        /** Returns the next local slot: longs and doubles take two. */
        private static int boxArgument(MethodVisitor mv, Class<?> paramType, int localVarIndex) {
            if (paramType == boolean.class) {
                mv.visitVarInsn(Opcodes.ILOAD, localVarIndex);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf",
                    "(Z)Ljava/lang/Boolean;", false);
            } else if (paramType == char.class) {
                mv.visitVarInsn(Opcodes.ILOAD, localVarIndex);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Character", "valueOf",
                    "(C)Ljava/lang/Character;", false);
            } else if (paramType == byte.class) {
                mv.visitVarInsn(Opcodes.ILOAD, localVarIndex);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf",
                    "(B)Ljava/lang/Byte;", false);
            } else if (paramType == short.class) {
                mv.visitVarInsn(Opcodes.ILOAD, localVarIndex);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Short", "valueOf",
                    "(S)Ljava/lang/Short;", false);
            } else if (paramType == int.class) {
                mv.visitVarInsn(Opcodes.ILOAD, localVarIndex);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf",
                    "(I)Ljava/lang/Integer;", false);
            } else if (paramType == long.class) {
                mv.visitVarInsn(Opcodes.LLOAD, localVarIndex);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf",
                    "(J)Ljava/lang/Long;", false);
                return localVarIndex + 2;
            } else if (paramType == float.class) {
                mv.visitVarInsn(Opcodes.FLOAD, localVarIndex);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf",
                    "(F)Ljava/lang/Float;", false);
            } else if (paramType == double.class) {
                mv.visitVarInsn(Opcodes.DLOAD, localVarIndex);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf",
                    "(D)Ljava/lang/Double;", false);
                return localVarIndex + 2;
            } else {
                mv.visitVarInsn(Opcodes.ALOAD, localVarIndex);
            }
            return localVarIndex + 1;
        }

        private static void finishReturn(MethodVisitor mv, Class<?> returnType) {
            if (returnType == void.class) {
                mv.visitInsn(Opcodes.POP);
                mv.visitInsn(Opcodes.RETURN);
                return;
            }
            if (!returnType.isPrimitive()) {
                mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(returnType));
                mv.visitInsn(Opcodes.ARETURN);
                return;
            }
            mv.visitTypeInsn(Opcodes.CHECKCAST, primitiveWrapperInternalName(returnType));
            if (returnType == boolean.class) {
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
            } else {
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
                mv.visitInsn(Opcodes.DRETURN);
            }
        }

        // Primitive.boxType() has no entry for void.
        private static String primitiveWrapperInternalName(Class<?> primitiveType) {
            return Type.getInternalName(
                primitiveType == void.class ? Void.class : Primitive.boxType(primitiveType));
        }
    }
}
