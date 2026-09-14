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

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

import bsh.org.objectweb.asm.ClassWriter;
import bsh.org.objectweb.asm.MethodVisitor;
import bsh.org.objectweb.asm.Opcodes;
import bsh.org.objectweb.asm.Type;

/**
    The runtime value of a lambda expression. Opaque until coerced to a
    functional interface, either by a cast or assignment ({@link #convertTo})
    or by overload resolution matching its arity marker ({@link #castLambda}).
*/
public class BshLambda {

    /** Implemented by every generated wrapper class. A wrapper for a scripted
        interface is GeneratedClass-assignable without being a script-generated
        class, so Reflect must be able to tell the two apart. */
    public interface Wrapper {}

    // Overload resolution sees only argument Class[], never values, so a
    // lambda argument's type is a marker encoding its arity and body shape
    // (Types.getTypes). Shape only ranks functional interfaces, never rules one
    // out (JLS 15.12.2.5): see isAtLeastAsSpecific.
    interface ArityMarker {}
    interface ValueShape extends ArityMarker {}
    interface VoidShape extends ArityMarker {}
    interface EitherShape extends ArityMarker {}
    interface Value0 extends ValueShape {}
    interface Value1 extends ValueShape {}
    interface Value2 extends ValueShape {}
    interface Value3 extends ValueShape {}
    interface Value4 extends ValueShape {}
    interface Value5 extends ValueShape {}
    interface Value6 extends ValueShape {}
    interface Value7 extends ValueShape {}
    interface Value8 extends ValueShape {}
    interface Value9 extends ValueShape {}
    interface Value10 extends ValueShape {}
    interface Void0 extends VoidShape {}
    interface Void1 extends VoidShape {}
    interface Void2 extends VoidShape {}
    interface Void3 extends VoidShape {}
    interface Void4 extends VoidShape {}
    interface Void5 extends VoidShape {}
    interface Void6 extends VoidShape {}
    interface Void7 extends VoidShape {}
    interface Void8 extends VoidShape {}
    interface Void9 extends VoidShape {}
    interface Void10 extends VoidShape {}
    interface Either0 extends EitherShape {}
    interface Either1 extends EitherShape {}
    interface Either2 extends EitherShape {}
    interface Either3 extends EitherShape {}
    interface Either4 extends EitherShape {}
    interface Either5 extends EitherShape {}
    interface Either6 extends EitherShape {}
    interface Either7 extends EitherShape {}
    interface Either8 extends EitherShape {}
    interface Either9 extends EitherShape {}
    interface Either10 extends EitherShape {}

    /** A body fits a value-returning method, a void one, or either (a
        statement expression, or a block that cannot complete normally). */
    static final int VALUE = 0, VOID = 1, EITHER = 2;

    private static final Class<?>[][] MARKERS = {
        { Value0.class, Value1.class, Value2.class, Value3.class, Value4.class, Value5.class,
          Value6.class, Value7.class, Value8.class, Value9.class, Value10.class },
        { Void0.class, Void1.class, Void2.class, Void3.class, Void4.class, Void5.class,
          Void6.class, Void7.class, Void8.class, Void9.class, Void10.class },
        { Either0.class, Either1.class, Either2.class, Either3.class, Either4.class, Either5.class,
          Either6.class, Either7.class, Either8.class, Either9.class, Either10.class } };

    private static final Map<Class<?>, Integer> MARKER_ARITY = new HashMap<>();
    static {
        for (Class<?>[] shape : MARKERS)
            for (int arity = 0; arity < shape.length; arity++)
                MARKER_ARITY.put(shape[arity], arity);
    }

    /** The type overload resolution matches this lambda by. Past the last
        marker the arity is unknown, so only Object/loose parameters match. */
    Class<?> arityMarker() {
        return paramNames.length < MARKERS[shape].length
            ? MARKERS[shape][paramNames.length] : BshLambda.class;
    }

    static boolean isArityMarker(Class<?> type) {
        return type != null && MARKER_ARITY.containsKey(type);
    }

    /** Whether a lambda typed by this marker can become a toType: a public
        functional interface whose single abstract method has the marker's arity.
        Body shape does not affect applicability, only ranking. */
    static boolean isFunctionalTarget(Class<?> toType, Class<?> marker) {
        if (toType == null || !IMPLEMENTABLE.get(toType))
            return false;
        Method sam = singleAbstractMethod(toType);
        Integer arity = MARKER_ARITY.get(marker);
        return sam != null && arity != null && sam.getParameterCount() == arity;
    }

    /** Whether, at an argument typed by marker, parameter type target is at
        least as specific as best. Java drops an interface whose method the body
        cannot fit; bsh keeps it applicable but ranks it below one the body fits.
        Among those the body fits, a subinterface wins, then the result kind
        (JLS 15.12.2.5, approximated without static types: see rank). */
    static boolean isAtLeastAsSpecific(Class<?> marker, Class<?> target, Class<?> best) {
        boolean subtype = Types.isJavaBaseAssignable(best, target);
        int t = result(target), b = result(best);
        // Object and untyped (null) parameters have no method to rank by.
        if (t < 0 || b < 0)
            return subtype;
        int shape = shape(marker);
        if (fits(shape, t) != fits(shape, b))
            return fits(shape, t);
        return subtype || rank(t) > rank(b) && !target.isAssignableFrom(best);
    }

    private static final int VOID_RESULT = 0, PRIMITIVE_RESULT = 1, REFERENCE_RESULT = 2;

    // -1 if type has no single abstract method.
    private static int result(Class<?> type) {
        Method sam = type == null ? null : singleAbstractMethod(type);
        if (sam == null)
            return -1;
        Class<?> returned = sam.getReturnType();
        return returned == void.class ? VOID_RESULT
            : returned.isPrimitive() ? PRIMITIVE_RESULT : REFERENCE_RESULT;
    }

    private static int shape(Class<?> marker) {
        return EitherShape.class.isAssignableFrom(marker) ? EITHER
            : VoidShape.class.isAssignableFrom(marker) ? VOID : VALUE;
    }

    private static boolean fits(int shape, int result) {
        return shape == EITHER || (shape == VOID) == (result == VOID_RESULT);
    }

    // Without static types, prefer what can hold anything the body yields: a
    // reference (even a void call's null), then void, and a primitive last.
    private static int rank(int result) {
        return result == REFERENCE_RESULT ? 2 : result == VOID_RESULT ? 1 : 0;
    }

    /** Error-message name for an arity marker, which must never surface as-is. */
    static String markerTypeName(Class<?> marker) {
        return "<" + MARKER_ARITY.get(marker) + "-arg lambda>";
    }

    /**
        Types.castObject for a lambda value. fromType is BshLambda.class for a
        real cast or assignment, or an arity marker for overload resolution's
        checkOnly probe, which has no value to inspect.
    */
    static Object castLambda(Class<?> toType, Class<?> fromType, Object fromValue,
            boolean checkOnly) throws UtilEvalError {
        if (checkOnly)
            return toType.isAssignableFrom(BshLambda.class) || isFunctionalTarget(toType, fromType)
                ? Types.VALID_CAST : Types.INVALID_CAST;
        if (toType.isInstance(fromValue))
            return fromValue;
        return ((BshLambda) fromValue).convertTo(toType);
    }

    private final Node expressionNode;
    private final NameSpace declaringNameSpace;
    private final Interpreter declaringInterpreter;
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
        wrapper = new WrapperLoader(functionalInterface.getClassLoader()).define(functionalInterface);
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

    private Object invokeImpl(Object[] args) throws UtilEvalError, EvalError {
        // Never reuse the declaring call stack: by the time Java calls back it
        // has been popped. The declaring namespace is shared, not copied.
        NameSpace nameSpace = new NameSpace(declaringNameSpace, "LambdaExpression");
        for (int i = 0; i < paramNames.length; i++) {
            if (paramTypes[i] != null)
                nameSpace.setTypedVariable(paramNames[i], paramTypes[i], args[i], paramModifiers[i]);
            else
                nameSpace.setVariable(paramNames[i], args[i], false);
        }
        CallStack callstack = new CallStack(nameSpace);

        Object result = bodyNode.eval(callstack, declaringInterpreter);
        if (bodyNode instanceof BSHBlock) {
            if (result instanceof ReturnControl) {
                ReturnControl rc = (ReturnControl) result;
                if (rc.kind == ReturnControl.RETURN)
                    return rc.value;
            }
            return Primitive.VOID;
        }
        return result;
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
                    Type.getInternalName(functionalInterface), Type.getInternalName(Wrapper.class) });

            cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "bshLambda",
                Type.getDescriptor(BshLambda.class), null, null).visitEnd();

            writeConstructor(cw, internalClassName);
            // A covariant diamond inherits one method under several descriptors.
            Set<String> descriptors = new HashSet<>();
            for (Method m : abstractMethods(functionalInterface))
                if (descriptors.add(Type.getMethodDescriptor(m)))
                    writeMethod(cw, internalClassName, m);

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
