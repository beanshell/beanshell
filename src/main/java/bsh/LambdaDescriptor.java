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

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.GenericSignatureFormatError;
import java.lang.reflect.MalformedParameterizedTypeException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
    What overload resolution can know about a lambda without running it, and
    how that ranks functional interfaces for it.
*/
final class LambdaDescriptor {

    final int arity;
    final int shape;
    /** Declared parameter types, null where untyped. */
    final Class<?>[] paramTypes;
    /** The statically known result, or null. */
    final Result result;

    LambdaDescriptor(int shape, Class<?>[] paramTypes, Result result) {
        this.arity = paramTypes.length;
        this.shape = shape;
        this.paramTypes = paramTypes;
        this.result = result;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof LambdaDescriptor))
            return false;
        LambdaDescriptor other = (LambdaDescriptor) o;
        return shape == other.shape && Arrays.equals(paramTypes, other.paramTypes)
            && Objects.equals(result, other.result);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shape, Arrays.hashCode(paramTypes), result);
    }

    /** Whether this lambda can become the type: a public functional interface
        whose method takes its parameters and whose result its body fits. */
    boolean fits(Class<?> type) {
        Method sam = type == null ? null : BshLambda.singleAbstractMethod(type);
        if (sam == null || !BshLambda.isImplementable(type) || sam.getParameterCount() != arity)
            return false;
        Class<?>[] parameters = sam.getParameterTypes();
        Type[] generic;
        try {
            // JLS 15.27.3: a lambda cannot implement a generic method.
            if (sam.getTypeParameters().length > 0)
                return false;
            generic = sam.getGenericParameterTypes();
        } catch (GenericSignatureFormatError | TypeNotPresentException | MalformedParameterizedTypeException e) {
            // Scripted interfaces carry a signature reflection cannot parse; treat them as generic.
            generic = null;
        }
        for (int i = 0; i < arity; i++) {
            Class<?> declared = paramTypes[i];
            // bsh has no type arguments, so a generic parameter takes any compatible type.
            if (declared != null && !(generic == null || mentionsTypeVariable(generic[i])
                    ? parameters[i] == declared || parameters[i].isAssignableFrom(box(declared))
                    : parameters[i] == declared))
                return false;
        }
        Class<?> returned = BshLambda.functionReturnType(type);
        if (shape == BshLambda.INVALID
                || shape == BshLambda.VALUE && returned == void.class
                || shape == BshLambda.VOID && returned != void.class)
            return false;
        return result == null || returned == void.class || isAssignable(result, returned);
    }

    private static boolean mentionsTypeVariable(Type type) {
        if (type instanceof TypeVariable || type instanceof ParameterizedType)
            return true;
        return type instanceof GenericArrayType
            && mentionsTypeVariable(((GenericArrayType) type).getGenericComponentType());
    }

    /** JLS 5.2 assignment contexts, including constant narrowing. */
    static boolean isAssignable(Result result, Class<?> to) {
        Class<?> from = result.type;
        if (from == NullType.class)
            return !to.isPrimitive();
        if (from == to)
            return true;
        if (from.isPrimitive()) {
            if (to.isPrimitive())
                return isWidening(from, to) || isNarrowingConstant(result, to);
            Class<?> unboxed = unbox(to);
            return to.isAssignableFrom(box(from))
                || unboxed != null && unboxed != to && isNarrowingConstant(result, unboxed);
        }
        if (to.isAssignableFrom(from))
            return true;
        Class<?> unboxed = to.isPrimitive() ? unbox(from) : null;
        return unboxed != null && (unboxed == to || isWidening(unboxed, to));
    }

    private static final List<Class<?>> WIDENS = Arrays.asList(
        byte.class, short.class, int.class, long.class, float.class, double.class);

    // JLS 5.1.2; char widens to int and beyond only.
    private static boolean isWidening(Class<?> from, Class<?> to) {
        if (from == char.class)
            return WIDENS.indexOf(to) >= WIDENS.indexOf(int.class);
        int f = WIDENS.indexOf(from), t = WIDENS.indexOf(to);
        return f >= 0 && t > f;
    }

    private static boolean isNarrowingConstant(Result result, Class<?> to) {
        Class<?> from = result.type;
        if (result.constants == null || to != byte.class && to != short.class && to != char.class
                || from != byte.class && from != short.class && from != char.class && from != int.class)
            return false;
        for (Object constant : result.constants) {
            long value = constant instanceof Character ? (Character) constant : ((Number) constant).longValue();
            long min = to == char.class ? Character.MIN_VALUE : to == short.class ? Short.MIN_VALUE : Byte.MIN_VALUE;
            long max = to == char.class ? Character.MAX_VALUE : to == short.class ? Short.MAX_VALUE : Byte.MAX_VALUE;
            if (value < min || value > max)
                return false;
        }
        return true;
    }

    private static Class<?> unbox(Class<?> type) {
        return Primitive.isWrapperType(type) ? Primitive.unboxType(type) : null;
    }

    private static Class<?> box(Class<?> type) {
        return type.isPrimitive() ? Primitive.boxType(type) : type;
    }

    /** L4: among the applicable candidates, the one no other beats, else the
        greatest by a total order; never dependent on candidate order. */
    static int select(LambdaDescriptor[] lambdas, Class<?>[][] candidates, List<Integer> applicable) {
        List<Integer> maximal = new ArrayList<>();
        for (int i : applicable) {
            boolean beaten = false;
            for (int j : applicable)
                beaten |= j != i && atLeastAsSpecific(lambdas, candidates[j], candidates[i])
                    && !atLeastAsSpecific(lambdas, candidates[i], candidates[j]);
            if (!beaten)
                maximal.add(i);
        }
        List<Integer> pool = maximal.isEmpty() ? applicable : maximal;
        int best = -1;
        for (int i : pool)
            if (best < 0 || compareSignatures(lambdas, candidates[i], candidates[best]) > 0)
                best = i;
        return best;
    }

    private static boolean atLeastAsSpecific(LambdaDescriptor[] lambdas, Class<?>[] target, Class<?>[] other) {
        for (int i = 0; i < target.length; i++)
            if (lambdas[i] != null ? !lambdas[i].atLeastAsSpecific(target[i], other[i])
                    : target[i] != other[i] && !Types.isJavaBaseAssignable(other[i], target[i]))
                return false;
        return true;
    }

    /** Per argument: JLS 15.12.2.5 where the result is known or the shape is
        certainly void, the safety-first key where bsh cannot know the result. */
    boolean atLeastAsSpecific(Class<?> target, Class<?> other) {
        if (target == other || other == null)
            return true;
        if (target == null)
            return false;
        Method t = BshLambda.singleAbstractMethod(target), o = BshLambda.singleAbstractMethod(other);
        if (t == null || o == null)
            return Types.isJavaBaseAssignable(other, target);
        if (result == null && shape != BshLambda.VOID)
            return compareKeys(target, other) >= 0;
        if (other.isAssignableFrom(target))
            return true;
        if (target.isAssignableFrom(other))
            return false;
        Class<?> rt = BshLambda.functionReturnType(target), ro = BshLambda.functionReturnType(other);
        if (ro == void.class)
            return true;
        if (rt == void.class || result == null)
            return false;
        if (rt.isPrimitive() && ro.isPrimitive())
            return rt == ro || isWidening(rt, ro);
        if (!rt.isPrimitive() && !ro.isPrimitive())
            return ro.isAssignableFrom(rt);
        boolean primitiveResult = result.type.isPrimitive();
        return rt.isPrimitive() == primitiveResult;
    }

    private int compareKeys(Class<?> a, Class<?> b) {
        int c = Integer.compare(exactParams(a), exactParams(b));
        if (c == 0)
            c = Integer.compare(resultScore(a), resultScore(b));
        if (c == 0)
            c = Integer.compare(depth(a), depth(b));
        return c;
    }

    private int exactParams(Class<?> type) {
        Class<?>[] parameters = BshLambda.singleAbstractMethod(type).getParameterTypes();
        int exact = 0;
        for (int i = 0; i < arity; i++)
            if (paramTypes[i] != null && parameters[i] == paramTypes[i])
                exact++;
        return exact;
    }

    private static final List<Class<?>> NARROWEST_FIRST = Arrays.asList(
        double.class, float.class, long.class, int.class, short.class, byte.class);

    // Higher is more specific; see the design's key.
    private int resultScore(Class<?> type) {
        Class<?> returned = BshLambda.functionReturnType(type);
        if (result == null) {
            if (returned == void.class)
                return shape == BshLambda.VOID_UNSURE ? 4000 : 2000;
            if (returned == Object.class)
                return 3000;
            return returned.isPrimitive() ? width(returned) : 1000 + depth(returned);
        }
        if (returned == void.class)
            return 0;
        boolean primitiveResult = result.type.isPrimitive();
        if (returned.isPrimitive())
            return (primitiveResult ? 2000 : 1000) + narrowness(returned);
        return (primitiveResult ? 1000 : 2000) + depth(returned);
    }

    // double 7 ... byte 2, boolean 1.
    private static int width(Class<?> primitive) {
        if (primitive == char.class)
            return width(short.class);
        int i = NARROWEST_FIRST.indexOf(primitive);
        return i < 0 ? 1 : 7 - i;
    }

    // byte 7 ... double 2, boolean 1.
    private static int narrowness(Class<?> primitive) {
        return primitive == boolean.class ? 1 : 9 - width(primitive);
    }

    private static final ClassValue<Integer> DEPTH = new ClassValue<Integer>() {
        @Override
        protected Integer computeValue(Class<?> type) {
            if (type.isArray())
                return 2 + (type.getComponentType().isPrimitive() ? 0 : depth(type.getComponentType()));
            if (type == Object.class || type.isPrimitive())
                return 0;
            int max = type.getSuperclass() == null ? 0 : depth(type.getSuperclass());
            for (Class<?> superinterface : type.getInterfaces())
                max = Math.max(max, depth(superinterface));
            return max + 1;
        }
    };

    /** Longest path to Object over superclasses and superinterfaces, so a
        proper subtype is always deeper. */
    static int depth(Class<?> type) {
        return DEPTH.get(type);
    }

    // The final tie-break: left to right, lambda arguments by the full key
    // (then name), other arguments by depth then name; untyped ranks lowest.
    private static int compareSignatures(LambdaDescriptor[] lambdas, Class<?>[] a, Class<?>[] b) {
        for (int i = 0; i < a.length; i++) {
            int c = lambdas[i] != null ? lambdas[i].compareFully(a[i], b[i]) : compareByDepthAndName(a[i], b[i]);
            if (c != 0)
                return c;
        }
        return 0;
    }

    private int compareFully(Class<?> a, Class<?> b) {
        boolean fa = a != null && BshLambda.singleAbstractMethod(a) != null;
        boolean fb = b != null && BshLambda.singleAbstractMethod(b) != null;
        if (fa != fb)
            return fa ? 1 : -1;
        if (!fa)
            return compareByDepthAndName(a, b);
        int c = compareKeys(a, b);
        return c != 0 ? c : b.getName().compareTo(a.getName());
    }

    private static int compareByDepthAndName(Class<?> a, Class<?> b) {
        if (a == null || b == null)
            return a == b ? 0 : a == null ? -1 : 1;
        int c = Integer.compare(depth(a), depth(b));
        return c != 0 ? c : b.getName().compareTo(a.getName());
    }

    /** The type of the null literal. */
    static final class NullType {
        private NullType() {}
    }

    /** A statically known result: its type and, when every result is a
        constant, the constants (boxed, so 1 and 1L differ). */
    static final class Result {
        final Class<?> type;
        final Set<Object> constants;

        Result(Class<?> type, Set<Object> constants) {
            this.type = type;
            this.constants = constants;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Result && type == ((Result) o).type
                && Objects.equals(constants, ((Result) o).constants);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, constants);
        }
    }
}
