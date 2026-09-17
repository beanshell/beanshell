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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    and undeclared checked exceptions reach Java callers of a wrapper as
    {@link RuntimeEvalError}; unchecked exceptions and declared checked
    exceptions reach them as themselves.
*/
public class BshLambda implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Implemented by every generated wrapper class. A wrapper for a scripted
        interface is GeneratedClass-assignable without being a script-generated
        class, so Reflect must be able to tell the two apart. */
    public interface Wrapper {}

    /** A body fits a value-returning method, a void one, or either (a
        statement expression, or a block that cannot complete normally);
        VOID_UNSURE is a void block bsh cannot be sure completes; INVALID is a
        block with a valued return that also has a bare return or can complete
        normally (JLS 15.27.2: neither value- nor void-compatible). */
    static final int VALUE = 0, VOID = 1, EITHER = 2, VOID_UNSURE = 3, INVALID = 4;

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
            descriptor = known = new LambdaDescriptor(shape, paramTypes,
                BSHLambdaExpression.result(bodyNode, paramNames, paramTypes));
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
        if (checkOnly) {
            if (raw)
                return Types.VALID_CAST;
            // BshLambda.class itself (an API caller typing a lambda by getClass())
            // carries no descriptor: any implementable functional interface may fit.
            if (!isLambdaMarker(fromType))
                return singleAbstractMethod(toType) != null && isImplementable(toType)
                    ? Types.VALID_CAST : Types.INVALID_CAST;
            return isFunctionalTarget(toType, fromType) ? Types.VALID_CAST : Types.INVALID_CAST;
        }
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

    private static final ClassValue<Optional<Class<?>>> FUNCTION_RETURN = new ClassValue<Optional<Class<?>>>() {
        @Override
        protected Optional<Class<?>> computeValue(Class<?> type) {
            Method sam = singleAbstractMethod(type);
            return Optional.ofNullable(sam == null ? null
                : specializedReturnType(type, sameDescriptorGroup(type, sam)));
        }
    };

    /** The SAM's return type in the interface's own context (JLS 9.9): a type
        variable bound by a direct generic superinterface resolves to its
        argument; anything else is the erasure. Null if not functional. */
    static Class<?> functionReturnType(Class<?> functionalInterface) {
        return FUNCTION_RETURN.get(functionalInterface).orElse(null);
    }

    // Every abstract method sharing sam's name and erased descriptor: when two
    // unrelated generic superinterfaces contribute the same erasure (e.g. two
    // T get() methods, one from A<String> and one from B<CharSequence>),
    // getMethods()'s declaration-order-dependent iteration must not decide
    // which one's specialization wins -- every member of the group is a
    // candidate, in the same grouping WrapperGenerator uses for codegen, so
    // the two can never disagree.
    private static List<Method> sameDescriptorGroup(Class<?> type, Method sam) {
        String key = sam.getName() + Type.getMethodDescriptor(sam);
        List<Method> group = new ArrayList<>();
        for (Method m : abstractMethods(type))
            if ((m.getName() + Type.getMethodDescriptor(m)).equals(key))
                group.add(m);
        return group;
    }

    /** The narrowest type every member of the group's own specialization
        (see the single-method overload) is a supertype of; when the members'
        specializations don't relate by subtyping at all, bsh's resolution API
        has no ambiguity outcome, so the shared erasure -- already validated
        as a common return type by singleAbstractMethod -- is the safe answer,
        not an error. Order-independent: a candidate the whole group agrees on
        is unique when one exists. */
    static Class<?> specializedReturnType(Class<?> functionalInterface, List<Method> group) {
        List<Class<?>> specialized = new ArrayList<>();
        for (Method m : group)
            specialized.add(specializedReturnType(functionalInterface, m));
        outer:
        for (Class<?> candidate : specialized) {
            for (Class<?> other : specialized)
                if (!other.isAssignableFrom(candidate))
                    continue outer;
            return candidate;
        }
        return group.get(0).getReturnType();
    }

    static Class<?> specializedReturnType(Class<?> functionalInterface, Method m) {
        java.lang.reflect.Type generic;
        try {
            generic = m.getGenericReturnType();
        } catch (java.lang.reflect.GenericSignatureFormatError | TypeNotPresentException
                | java.lang.reflect.MalformedParameterizedTypeException malformed) {
            return m.getReturnType();
        }
        if (!(generic instanceof java.lang.reflect.TypeVariable))
            return m.getReturnType();
        Map<java.lang.reflect.TypeVariable<?>, Class<?>> substitution =
            directSubstitution(functionalInterface, m.getDeclaringClass());
        Class<?> actual = substitution == null ? null : substitution.get(generic);
        // The wrapper can checkcast only a public type it can name, and only
        // one the erased descriptor can carry.
        return actual != null && Modifier.isPublic(actual.getModifiers())
            && isExportedToUnnamedModules(actual)
            && m.getReturnType().isAssignableFrom(actual) ? actual : m.getReturnType();
    }

    // Modifier.isPublic screens simple visibility, not JPMS module exports: a
    // public type in a named module whose package the module does not export
    // is still inaccessible to the wrapper, which is always defined into an
    // unnamed module (WrapperLoader never assigns one). Reflects because this
    // module targets bytecode release 8, where java.lang.Module cannot be
    // named directly, and because this must also run unmodified on an actual
    // JDK 8, where the class does not exist at all.
    private static boolean isExportedToUnnamedModules(Class<?> type) {
        // An array type's own getPackage() is always null (it has none); the
        // export to check is its component type's, same as
        // inaccessibleSignatureType's unwrapArray use below. A primitive
        // component has no package or module to export from.
        Class<?> component = unwrapArray(type);
        if (component.isPrimitive())
            return true;
        try {
            Object module = Class.class.getMethod("getModule").invoke(component);
            Method isNamed = module.getClass().getMethod("isNamed");
            if (!(Boolean) isNamed.invoke(module))
                return true;
            Package pkg = component.getPackage();
            String packageName = pkg == null ? "" : pkg.getName();
            return (Boolean) module.getClass().getMethod("isExported", String.class)
                .invoke(module, packageName);
        } catch (ReflectiveOperationException noModuleSystem) {
            // JDK 8: nothing to export from.
            return true;
        }
    }

    private static Method discoverSingleAbstractMethod(Class<?> type) {
        if (!type.isInterface())
            return null;
        List<Method> methods = abstractMethods(type);
        if (methods.isEmpty())
            return null;
        // Pairwise-to-first, not all-pairs: each survivor is consistent with
        // first, though two survivors are never compared to each other
        // directly. Only reachable via hand-written bytecode (javac never
        // emits a shape exposing the gap), and every downstream consumer
        // keys off first or a method validated against it, so it's benign.
        Method first = methods.get(0);
        for (Method m : methods)
            if (!m.getName().equals(first.getName())
                    || !sameAfterSubstitution(type, m, first))
                return null;
        // Inherited from several superinterfaces: one return type must
        // substitute for all of them, though not every pair need be related.
        for (Method candidate : methods)
            if (methods.stream().allMatch(m -> m.getReturnType().isAssignableFrom(candidate.getReturnType())))
                return mostConcrete(methods, candidate);
        return null;
    }

    private static boolean sameAfterSubstitution(Class<?> type, Method a, Method b) {
        if (Arrays.equals(a.getParameterTypes(), b.getParameterTypes()))
            return true;
        Class<?>[] substitutedA = substitutedParameterTypes(type, a);
        Class<?>[] substitutedB = substitutedParameterTypes(type, b);
        return substitutedA != null && substitutedB != null && Arrays.equals(substitutedA, substitutedB);
    }

    // Single-level only: resolves a.getGenericParameterTypes() against type's
    // DIRECT generic superinterfaces' actual type arguments. Returns null (not
    // resolvable) for anything deeper -- multi-level chains, wildcards, and
    // generic-array type variables are deliberately out of scope.
    private static Class<?>[] substitutedParameterTypes(Class<?> type, Method a) {
        Class<?> declaringClass = a.getDeclaringClass();
        java.lang.reflect.Type[] generic = genericParameterTypesOrErasure(a);
        boolean anyTypeVariable = false;
        for (java.lang.reflect.Type t : generic)
            anyTypeVariable |= t instanceof java.lang.reflect.TypeVariable;
        if (!anyTypeVariable)
            return a.getParameterTypes();
        Map<java.lang.reflect.TypeVariable<?>, Class<?>> substitution = directSubstitution(type, declaringClass);
        if (substitution == null)
            return null;
        Class<?>[] resolved = new Class<?>[generic.length];
        for (int i = 0; i < generic.length; i++) {
            if (generic[i] instanceof java.lang.reflect.TypeVariable) {
                Class<?> actual = substitution.get(generic[i]);
                if (actual == null)
                    return null;
                resolved[i] = actual;
            } else if (generic[i] instanceof Class) {
                resolved[i] = (Class<?>) generic[i];
            } else {
                return null;
            }
        }
        return resolved;
    }

    // A generated class's method (e.g. a bsh-scripted interface's) may carry a
    // Signature attribute the JVM cannot parse, or one that names a type
    // absent from the classpath (an optional dependency, missing at runtime)
    // -- exactly the three exceptions LambdaDescriptor.fits already guards
    // the same call against. Treat that exactly like an ordinary erased
    // method rather than let discovery crash on it.
    private static java.lang.reflect.Type[] genericParameterTypesOrErasure(Method m) {
        try {
            return m.getGenericParameterTypes();
        } catch (java.lang.reflect.GenericSignatureFormatError | TypeNotPresentException
                | java.lang.reflect.MalformedParameterizedTypeException malformed) {
            return m.getParameterTypes();
        }
    }

    // A generated class's method may carry a Signature attribute reflection cannot parse.
    private static boolean isGenericMethod(Method sam) {
        try {
            return sam.getTypeParameters().length > 0;
        } catch (java.lang.reflect.GenericSignatureFormatError | TypeNotPresentException
                | java.lang.reflect.MalformedParameterizedTypeException malformed) {
            return false;
        }
    }

    // type's direct (one-level) generic superinterfaces only, e.g. GS extends
    // G<String>: maps G's type variable T to String, as seen from GS.
    private static Map<java.lang.reflect.TypeVariable<?>, Class<?>> directSubstitution(
            Class<?> type, Class<?> declaringClass) {
        java.lang.reflect.Type[] superInterfaces;
        try {
            superInterfaces = type.getGenericInterfaces();
        } catch (java.lang.reflect.GenericSignatureFormatError | TypeNotPresentException
                | java.lang.reflect.MalformedParameterizedTypeException malformed) {
            return null;
        }
        for (java.lang.reflect.Type superInterface : superInterfaces) {
            if (!(superInterface instanceof java.lang.reflect.ParameterizedType))
                continue;
            java.lang.reflect.ParameterizedType parameterized = (java.lang.reflect.ParameterizedType) superInterface;
            if (parameterized.getRawType() != declaringClass)
                continue;
            java.lang.reflect.TypeVariable<?>[] variables = declaringClass.getTypeParameters();
            java.lang.reflect.Type[] arguments;
            try {
                arguments = parameterized.getActualTypeArguments();
            } catch (TypeNotPresentException | java.lang.reflect.MalformedParameterizedTypeException malformed) {
                return null;
            }
            Map<java.lang.reflect.TypeVariable<?>, Class<?>> substitution = new java.util.HashMap<>();
            for (int i = 0; i < variables.length; i++) {
                if (!(arguments[i] instanceof Class))
                    return null; // a further type variable or wildcard: out of scope
                substitution.put(variables[i], (Class<?>) arguments[i]);
            }
            return substitution;
        }
        return null;
    }

    // candidate is already the return-type-optimal choice (see above); leave
    // it alone unless swapping to a concrete sibling is both safe and useful:
    //   - safe: the same return type, so this can never demote candidate's
    //     already-correct, order-independent return type (every other method
    //     in the group is only guaranteed a supertype-or-equal return type);
    //   - useful: a genuinely different erasure, so LambdaDescriptor.exactParams
    //     actually learns something substitution alone wouldn't tell it.
    // A candidate whose own erasure already matches every concrete sibling's
    // (e.g. G<Object>,O both erasing m's parameter to Object) is left as-is:
    // swapping there would change nothing real, only which method's generic
    // signature LambdaDescriptor.fits reads for its protected lenient-match
    // rule -- not this task's call to make.
    private static Method mostConcrete(List<Method> methods, Method candidate) {
        if (!hasUnresolvedTypeVariable(candidate))
            return candidate;
        for (Method m : methods)
            if (!hasUnresolvedTypeVariable(m)
                    && m.getReturnType() == candidate.getReturnType()
                    && !Arrays.equals(m.getParameterTypes(), candidate.getParameterTypes()))
                return m;
        return candidate;
    }

    private static boolean hasUnresolvedTypeVariable(Method m) {
        return Arrays.stream(genericParameterTypesOrErasure(m))
            .anyMatch(t -> t instanceof java.lang.reflect.TypeVariable);
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
    // public interface and can name (checkcast) only public return types and parameters.
    static boolean isImplementable(Class<?> type) {
        return IMPLEMENTABLE.get(type);
    }

    private static final ClassValue<Boolean> IMPLEMENTABLE = new ClassValue<Boolean>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return Modifier.isPublic(type.getModifiers()) && inaccessibleSignatureType(type) == null;
        }
    };

    // A wrapper lives in its own runtime package, in an unnamed module: it can
    // name (CHECKCAST/exception-table/LDC) only a public type also exported to
    // unnamed modules -- Modifier.isPublic alone screens visibility, not JPMS
    // module exports (see isExportedToUnnamedModules).
    private static boolean isAccessibleToWrapper(Class<?> type) {
        Class<?> component = unwrapArray(type);
        return component.isPrimitive()
            || Modifier.isPublic(component.getModifiers()) && isExportedToUnnamedModules(component);
    }

    private static Class<?> inaccessibleSignatureType(Class<?> type) {
        for (Method m : abstractMethods(type)) {
            if (!isAccessibleToWrapper(m.getReturnType()))
                return unwrapArray(m.getReturnType());
            for (Class<?> parameter : m.getParameterTypes())
                if (!isAccessibleToWrapper(parameter))
                    return unwrapArray(parameter);
        }
        return null;
    }

    private static Class<?> unwrapArray(Class<?> type) {
        while (type.isArray())
            type = type.getComponentType();
        return type;
    }

    // Any zero-argument writeReplace in the inherited abstract family, whatever
    // its return type: Class.getDeclaredMethod picks the most specific return,
    // so serialization would find it instead of the surrogate hook.
    private static boolean hasWriteReplaceSam(Class<?> functionalInterface) {
        for (Method m : abstractMethods(functionalInterface))
            if (m.getName().equals("writeReplace") && m.getParameterCount() == 0)
                return true;
        return false;
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
        Class<?> hidden = inaccessibleSignatureType(functionalInterface);
        if (hidden != null)
            throw new UtilEvalError("A lambda cannot implement "
                + functionalInterface.getName() + ": its return type or a parameter type "
                + hidden.getName() + " is not accessible to the generated wrapper "
                + "(not public, or public in a module that does not export it)");
        if (Serializable.class.isAssignableFrom(functionalInterface) && hasWriteReplaceSam(functionalInterface))
            throw new UtilEvalError("A lambda cannot implement "
                + functionalInterface.getName() + ": its single abstract method "
                + "collides with Java serialization's writeReplace() hook, which "
                + "would run the lambda body during writeObject");
        if (shape == INVALID)
            throw new UtilEvalError("A lambda cannot implement "
                + functionalInterface.getName() + ": its block body has a valued return "
                + "beside a bare return or a path that falls off the end, so it fits no "
                + "functional interface (JLS 15.27.2)");
        if (isGenericMethod(sam))
            throw new UtilEvalError("A lambda cannot implement "
                + functionalInterface.getName() + ": its single abstract method "
                + sam.getName() + " is generic, and a lambda cannot declare type parameters (JLS 15.27.3)");
        // The same veto an implements clause gets (BSHClassDeclaration).
        Interpreter.mainSecurityGuard.canImplements(functionalInterface);
        if (!descriptor().fits(functionalInterface))
            throw new UtilEvalError(descriptor().parametersFit(functionalInterface)
                ? "A lambda cannot implement " + functionalInterface.getName() + ": its body does "
                    + "not fit the method's void/value shape or its statically known result type"
                : "A lambda cannot implement " + functionalInterface.getName() + ": its parameter "
                    + "types don't match the method's");

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
    public final <T> T invoke(Object[] args, Class<T> returnType, Class<?>[] declaredExceptions) {
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
            } catch (RuntimeException e) {
                throw new RuntimeEvalError("Cannot return " + Types.getType(result)
                    + " from a lambda declared to return " + returnType.getName(),
                    expressionNode, null, e);
            }
        } catch (TargetError e) {
            Throwable target = e.getTarget();
            // JLS 11.2: unchecked throwables are exempt from the throws clause.
            if (target instanceof RuntimeException || target instanceof Error)
                BshLambda.<RuntimeException>sneakyThrow(target);
            for (Class<?> declared : declaredExceptions)
                if (declared.isInstance(target))
                    BshLambda.<RuntimeException>sneakyThrow(target);
            throw new RuntimeEvalError(
                "Uncaught exception from lambda body: " + e.getMessage(),
                expressionNode, null, target);
        } catch (EvalError e) {
            throw new RuntimeEvalError("Error invoking lambda: " + e.getMessage(), expressionNode, null, e);
        } catch (UtilEvalError e) {
            throw new RuntimeEvalError(e.toEvalError(expressionNode, null));
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void sneakyThrow(Throwable t) throws E {
        throw (E) t;
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
            boolean serializable = Serializable.class.isAssignableFrom(functionalInterface);
            List<String> interfaces = new ArrayList<>();
            interfaces.add(Type.getInternalName(functionalInterface));
            interfaces.add(Type.getInternalName(Wrapper.class));
            if (serializable)
                interfaces.add(Type.getInternalName(Serializable.class));

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, internalClassName, null,
                "java/lang/Object", interfaces.toArray(new String[0]));

            cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "bshLambda",
                Type.getDescriptor(BshLambda.class), null, null).visitEnd();

            writeConstructor(cw, internalClassName);
            // A covariant diamond inherits one method under several descriptors; a SAM
            // inherited from unrelated superinterfaces can also carry different throws
            // clauses, so each name+descriptor group passes through only the exceptions
            // every branch declares (javac's own effective throws clause for the call).
            Map<String, List<Method>> byDescriptor = new LinkedHashMap<>();
            for (Method m : abstractMethods(functionalInterface))
                byDescriptor.computeIfAbsent(m.getName() + Type.getMethodDescriptor(m), k -> new ArrayList<>()).add(m);
            for (List<Method> group : byDescriptor.values())
                writeMethod(cw, internalClassName, group.get(0),
                    specializedReturnType(functionalInterface, group),
                    intersectedPublicExceptionTypes(group));
            if (serializable && !byDescriptor.containsKey(WRITE_REPLACE))
                writeWriteReplace(cw, internalClassName, functionalInterface);

            cw.visitEnd();
            return cw.toByteArray();
        }

        // JLS 9.4.1.3: a type is in the effective throws clause when every
        // inherited branch declares it or a supertype of it; a kept type whose
        // supertype is also kept is redundant. Non-public types are dropped:
        // the wrapper lives in its own runtime package and cannot name one.
        // A single branch has no cross-branch conflict to resolve, so javac
        // copies its own clause verbatim: no minimality reduction applies.
        private static Class<?>[] intersectedPublicExceptionTypes(List<Method> group) {
            if (group.size() == 1) {
                List<Class<?>> own = new ArrayList<>(Arrays.asList(group.get(0).getExceptionTypes()));
                own.removeIf(type -> !isAccessibleToWrapper(type));
                return own.toArray(new Class<?>[0]);
            }
            List<Class<?>> kept = new ArrayList<>();
            for (Method m : group)
                for (Class<?> candidate : m.getExceptionTypes())
                    if (!kept.contains(candidate) && isAccessibleToWrapper(candidate)
                            && group.stream().allMatch(branch -> declaresSupertypeOf(branch, candidate)))
                        kept.add(candidate);
            List<Class<?>> minimal = new ArrayList<>(kept);
            minimal.removeIf(type -> kept.stream().anyMatch(other -> other != type && other.isAssignableFrom(type)));
            return minimal.toArray(new Class<?>[0]);
        }

        private static boolean declaresSupertypeOf(Method branch, Class<?> exception) {
            for (Class<?> declared : branch.getExceptionTypes())
                if (declared.isAssignableFrom(exception))
                    return true;
            return false;
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

        private static void writeMethod(ClassWriter cw, String internalClassName, Method sam,
                Class<?> resultType, Class<?>[] exceptionTypes) {
            String bshLambdaInternalName = Type.getInternalName(BshLambda.class);
            Parameter[] params = sam.getParameters();

            String[] exceptionInternalNames = new String[exceptionTypes.length];
            for (int i = 0; i < exceptionTypes.length; i++)
                exceptionInternalNames[i] = Type.getInternalName(exceptionTypes[i]);

            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, sam.getName(),
                Type.getMethodDescriptor(sam), null, exceptionInternalNames);
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
                mv.visitLdcInsn(Type.getType(resultType));

            mv.visitLdcInsn(exceptionTypes.length);
            mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Class");
            for (int i = 0; i < exceptionTypes.length; i++) {
                mv.visitInsn(Opcodes.DUP);
                mv.visitLdcInsn(i);
                mv.visitLdcInsn(Type.getType(exceptionTypes[i]));
                mv.visitInsn(Opcodes.AASTORE);
            }

            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, bshLambdaInternalName, "invoke",
                "([Ljava/lang/Object;Ljava/lang/Class;[Ljava/lang/Class;)Ljava/lang/Object;", false);

            finishReturn(mv, returnType, resultType);

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

        private static void finishReturn(MethodVisitor mv, Class<?> returnType, Class<?> resultType) {
            if (returnType == void.class) {
                mv.visitInsn(Opcodes.POP);
                mv.visitInsn(Opcodes.RETURN);
                return;
            }
            if (!returnType.isPrimitive()) {
                mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(resultType));
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
