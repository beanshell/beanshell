/** Copyright 2018 Nick nickl- Lombard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. */
package bsh;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** MethodHandle wrappers to represent invocable members. */
public abstract class Invocable implements Member {

    /** Public invocable class method for making method invocables.
     * @param m reflect method member
     * @return executing method invocable */
    public static Invocable get(Method m) {
        return new MethodInvocable(m);
    }

    /** Public invocable class method for making constructor invocables.
     * @param m reflect constructor member
     * @return executing constructor invocable */
    public static Invocable get(Constructor<?> c) {
        return new ConstructorInvocable(c);
    }

    /** Public invocable class method for making field access invocables.
     * @param m reflect field member
     * @return field access invocable */
    public static FieldAccess get(Field f) {
        return new FieldAccess(f);
    }

    private MethodHandle handle = null;
    private final boolean isStatic, isSynthetic;
;
    private final String toString;
    private final String name;
    private final int flags;
    private final Class<?> declaringClass;
    protected final List<Object> parameters = new ArrayList<>();
    protected int lastParameterIndex;

    /** Package private abstract invocable constructor.
     * Collects an accessible member entity as the common invocable apparatus.
     * If running privileged we enforce accessibility for all types here.
     * @param member an accessible member entity */
    <M extends AccessibleObject & Member> Invocable(M member) {
        flags = member.getModifiers();
        declaringClass = member.getDeclaringClass();
        name = member.getName();
        toString = member.toString();
        lastParameterIndex = 0;
        isStatic = Reflect.isStatic(member);
        isSynthetic = member.isSynthetic();
        if (Capabilities.haveAccessibility()
                && member.getDeclaringClass() != Class.class)
            member.setAccessible(true);
    }

    /** abstract method declaration contract. */
    abstract Class<?>[] getParameterTypes();

    /** abstract method declaration contract. */
    abstract Class<?> getReturnType();

    /** abstract method declaration contract. */
    abstract int getParameterCount();

    /** abstract method declaration contract. */
    abstract MethodHandle lookup(MethodHandle m);

    /** provides default constructs for an invocable member prototype. */
    public boolean isInnerClass() { return false; }

    /** provides default constructs for an invocable member prototype. */
    public boolean isVarArgs() { return false; }

    /** provides default constructs for an invocable member prototype. */
    public Class<?> getVarArgsType() { return Void.TYPE; }

    /** provides default constructs for an invocable member prototype. */
    public Class<?> getVarArgsComponentType() { return Void.TYPE; }

    /** provides default constructs for an invocable member prototype. */
    public boolean isGetter() { return false; }

    /** provides default constructs for an invocable member prototype. */
    public boolean isSetter() { return false; }

    /** provides default constructs for an invocable member prototype. */
    public int getModifiers() { return flags; }

    /** provides default constructs for an invocable member prototype. */
    public boolean isStatic() { return isStatic; }

    /** provides default constructs for an invocable member prototype. */
    public boolean isSynthetic() { return isSynthetic; }

    /** provides default constructs for an invocable member prototype. */
    protected int getLastParameterIndex() { return lastParameterIndex; }

    /** provides default constructs for an invocable member prototype. */
    public Class<?> getDeclaringClass() { return declaringClass; }

    /** provides default constructs for an invocable member prototype. */
    public String getName() { return name; }

    /** Enables lazy initialize of MethodHandle lookup only once with reuse. */
    public MethodHandle getMethodHandle() {
        if (null == handle)
            handle = lookup(null);
        return handle;
    }

    /** Retrieve a method type from return type a parameter type signatures.
     * @return method type  */
    public MethodType methodType() {
        return MethodType.methodType(getReturnType(), getParameterTypes());
    }

    /** Retrieve a bytecode descriptor representation of the method.
     * @return the bytecode type descriptor  */
    public String getMethodDescriptor() {
        return methodType().toMethodDescriptorString();
    }

    /** Retrieve a bytecode descriptor representation of the parameter types.
     * @return the bytecode type descriptor  */
    public String [] getParamTypeDescriptors() {
        return methodType().parameterList().stream()
                .map(BSHType::getTypeDescriptor).toArray(String[]::new);
    }

    /** Retrieve a bytecode descriptor representation of the return type.
     * @return the bytecode type descriptor  */
    public String getReturnTypeDescriptor() {
        return BSHType.getTypeDescriptor(getReturnType());
    }

    /** Basic parameter collection with pulling inherited cascade chaining. */
    public ParameterType collectParamaters(Object base, Object[] params)
            throws Throwable {
        parameters.clear();
        for (int i = 0; i < getLastParameterIndex(); i++)
            parameters.add(coerceToType(params[i], getParameterTypes()[i]));
        return new ParameterType(parameters, false);
    }

    /** Coerce parameter values to parameter type and unwrap primitives.
     * @param param the parameter value
     * @param type the parameter type
     * @return unwrapped coerced value
     * @throws Throwable on cast errors */
    protected Object coerceToType(Object param, Class<?> type)
            throws Throwable {
        if (type != Object.class)
            param = Types.castObject(param, type, Types.CAST);
        return Primitive.unwrap(param);
    }

    /** All purpose MethodHandle invoke implementation, with or without args.
     * @param base represents the base object instance.
     * @param pars parameter arguments
     * @return invocation result
     * @throws Throwable combined exceptions */
    private synchronized Object invokeTarget(Object base, Object[] pars)
            throws Throwable {
        Reflect.logInvokeMethod("Invoking method (entry): ", this, pars);
        ParameterType pt = collectParamaters(base, pars);
        List<Object> params = pt.params;
        Reflect.logInvokeMethod("Invoking method (after): ", this, params);
        if (getParameterCount() > 0) {
            MethodHandle mh = getMethodHandle();
            if (pt.isFixedArity)
                mh = mh.asFixedArity();
            return mh.invokeWithArguments(params);
        }
        if (isStatic() || this instanceof ConstructorInvocable)
            return getMethodHandle().invoke();
        return getMethodHandle().invoke(params.get(0));
    }

    /** Abstraction to cleanly apply the primitive result wrapping.
     * @param base represents the base object instance.
     * @param pars parameter arguments
     * @return invocation result
     * @throws InvocationTargetException wrapped target exceptions */
    public synchronized Object invoke(Object base, Object... pars)
            throws InvocationTargetException {
        if (null == pars)
            pars = Reflect.ZERO_ARGS;
        try {
            return Primitive.wrap(
                    invokeTarget(base, pars), getReturnType());
        }
        catch (Throwable ite) {
            throw new InvocationTargetException(ite);
        }
    }

    /** {@inheritDoc} */
    @Override
    public String toString() { return toString; }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        if (this == o) return true;
        if (this.getClass() != o.getClass()) return false;
        Invocable member = (Invocable) o;
        if (!getName().equals(member.getName())
                || getDeclaringClass() != member.getDeclaringClass()
                || getParameterCount() != member.getParameterCount()
                || getReturnType() != member.getReturnType()
                || getModifiers() != member.getModifiers())
            return false;
        for (int i = 0; i < getParameterCount(); i++)
            if (getParameterTypes()[i] != member.getParameterTypes()[i])
                return false;
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return getClass().hashCode() ^ getName().hashCode()
                ^ getDeclaringClass().hashCode() ^ getParameterCount()
                ^ getReturnType().hashCode() ^ getModifiers()
                ^ Stream.of(getParameterTypes())
                  .map(t -> null == t ? 39 : t.hashCode())
                  .reduce(75, (a, b) -> a ^ b).intValue();
    }

    static class ParameterType {
        List<Object> params;
        boolean isFixedArity;
        ParameterType(List<Object> params, boolean isFixedArity) {
            this.params = params;
            this.isFixedArity = isFixedArity;
        }
    }
}

/** Executable extension for invocable members includes varargs support. */
abstract class ExecutingInvocable extends Invocable {
    private final Class<?> varArgsType;
    private final Class<?>[] parameterTypes;
    private final int parameterCount;
    private final boolean isVarargs;

    /** Package private abstract executing invocable constructor.
     * Collects an executable member entity as common executing invocable
     * apparatus. Applies implementation for varargs specifics.
     * @param member an executable member entity */
    <M extends Executable & Member> ExecutingInvocable(M member) {
        super(member);
        parameterTypes = member.getParameterTypes();
        parameterCount = member.getParameterCount();
        isVarargs = member.isVarArgs();
        lastParameterIndex = parameterCount > 1 ? parameterCount -1 : 0;
        varArgsType = isVarArgs()
                ? getParameterTypes()[lastParameterIndex] : Void.TYPE;
    }

    /** Override default prototype construct with value implementation. */
    @Override
    public boolean isVarArgs() { return isVarargs; }

    /** Override default prototype construct with value implementation. */
    @Override
    public int getParameterCount() { return parameterCount; }

    /** Override default prototype construct with value implementation. */
    @Override
    public Class<?>[] getParameterTypes() { return parameterTypes; }

    /** Override default prototype construct with value implementation. */
    @Override
    public Class<?> getVarArgsType() { return varArgsType; }

    /** Override default prototype construct with value implementation. */
    @Override
    public Class<?> getVarArgsComponentType() {
        return Types.arrayElementType(getVarArgsType());
    }

    /** Appends varargs collector as appropriate to the MethodHandles lookup.
     * The return pushes the cascade chaining result to the parent.
     * {@inheritDoc} */
    @Override
    protected MethodHandle lookup(MethodHandle m) {
        if (isVarArgs() && null != m)
            return m.asVarargsCollector(getVarArgsType());
        return m;
    }

    /** Pull the cascade inheritance chain for parameter collection.
     * Applies the varargs collection of parameters supplied as an array or
     * as separate args.
     *  {@inheritDoc} */
    @Override
    public ParameterType collectParamaters(Object base, Object[] params)
            throws Throwable {
        super.collectParamaters(base, params);
        boolean isFixedArity = false;
        if (isVarArgs()) {
            if (getLastParameterIndex() < params.length) {
                Object[] varargs;
                if (getParameterCount() == params.length
                    && params[getLastParameterIndex()].getClass().isArray()
                    && getVarArgsComponentType().isAssignableFrom(params[getLastParameterIndex()].getClass().getComponentType())) {
                    isFixedArity = true;
                    parameters.add(params[getLastParameterIndex()]);
                } else {
                    varargs = Arrays.copyOfRange(
                            params, getLastParameterIndex(), params.length);
                    for (int i = 0; i < varargs.length; i++)
                        parameters.add(super.coerceToType(
                            varargs[i], getVarArgsComponentType()));
                }
            }
        } else if (null != params && getLastParameterIndex() < params.length)
            parameters.add(super.coerceToType(params[getLastParameterIndex()],
                    getParameterTypes()[getLastParameterIndex()]));
        return new ParameterType(parameters, isFixedArity);
    }
}

/** Executable constructor members includes inner classes. */
class ConstructorInvocable extends ExecutingInvocable {
    private Constructor<?> constructor;
    private final boolean isStatic;

    /** Package private constructor executing invocable constructor.
     * Collects the reflect constructor member to unreflect into MethodHandles.
     * @param cons a reflect constructor */
    ConstructorInvocable(Constructor<?> cons) {
        super(cons);
        this.constructor = cons;
        isStatic = Reflect.isStatic(getDeclaringClass());
    }

    /** Override default prototype construct with value implementation. */
    @Override
    public boolean isStatic() { return isStatic; }

    /** Override default prototype construct with value implementation. */
    @Override
    public Class<?> getReturnType() { return getDeclaringClass(); }

    /** Override default prototype construct with value implementation. */
    @Override
    public boolean isInnerClass() {
        return getDeclaringClass().isMemberClass();
    }

    /** Lazy initialize MethodHandle lookup which is persisted for reuse.
     * {@inheritDoc} */
    @Override
    protected MethodHandle lookup(MethodHandle m) {
        try {
            return super.lookup(
                    MethodHandles.lookup().unreflectConstructor(constructor));
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            // release object reference
            constructor = null;
        }
    }

    /** Pull the inheritance cascade chain of parameter collection.
     * Applies inner class mappings as required.
     * {@inheritDoc} */
    @Override
    public ParameterType collectParamaters(Object base, Object[] params)
            throws Throwable {
        if (isInnerClass() && !isStatic())
            params = Stream.concat(
                    Stream.of(base), Stream.of(params)).toArray();
        return super.collectParamaters(base, params);
    }

}

/** Executable method members includes bean property identification. */
class MethodInvocable extends ExecutingInvocable {
    private static final Pattern PROPERTY_PATTERN
                = Pattern.compile("(?:[gs]et|is)\\p{javaUpperCase}.*");
    private final Class<?> type;
    private Method method;
    private boolean getter = false, setter = false;

    /** Package private method executing invocable constructor.
     * Collects the reflect method member to unreflect into MethodHandles.
     * Identifies method as bean property getter or setter if applicable.
     * @param method a reflect method */
    MethodInvocable(Method method) {
        super(method);
        this.method = method;
        type = method.getReturnType();
        lastParameterIndex = getParameterCount() - (isVarArgs() ? 1 : 0);
        if (PROPERTY_PATTERN.matcher(getName()).matches()) {
            setter = getName().startsWith(Reflect.SET_PREFIX);
            getter = !setter && getParameterCount() == 0 && type != Void.TYPE;
            setter &= getParameterCount() == 1 && type == Void.TYPE;
            if (getter && getName().startsWith(Reflect.IS_PREFIX))
                getter = type == Boolean.class || type == Boolean.TYPE;
        }
    }

    /** Override default prototype construct with value implementation. */
    @Override
    public boolean isGetter() { return getter; }

    /** Override default prototype construct with value implementation. */
    @Override
    public boolean isSetter() { return setter; }

    /** Override default prototype construct with value implementation. */
    @Override
    public Class<?> getReturnType() { return type; }

    /** Lazy initialize MethodHandle lookup which is persisted for reuse.
     * {@inheritDoc} */
    @Override
    protected MethodHandle lookup(MethodHandle m) {
        try {
            return super.lookup(MethodHandles.lookup().unreflect(method));
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            // release object reference
            method = null;
        }
    }

    /** Pull the cascade inheritance chain for parameter collection.
     *  {@inheritDoc} */
    @Override
    public ParameterType collectParamaters(Object base, Object[] params)
            throws Throwable {
        ParameterType pt = super.collectParamaters(base, params);
        if (!isStatic())
            parameters.add(0, base);
        return new ParameterType(parameters, pt.isFixedArity);
    }

}

/** Field member invocable includes functionality for get and set. */
class FieldAccess extends Invocable {
    private Field field;
    private final Class<?> type;
    private MethodHandle setter;
    private boolean getter = false;

    /** Package private field access invocable constructor.
     * Collects the reflect field member to unreflect into MethodHandles.
     * @param field a reflect field */
   FieldAccess(Field field) {
        super(field);
        type = field.getType();
        this.field = field;
    }

   /** Lazy initialize MethodHandle lookup which is persisted for reuse.
    * Lookup for field getter
    * {@inheritDoc} */
    @Override
    protected MethodHandle lookup(MethodHandle m) {
        try {
            return MethodHandles.lookup().unreflectGetter(field);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            getter = true;
            if (null != setter)
                field = null;
        }
    }

    /** Lazy initialize MethodHandle lookup which is persisted for reuse.
     * Lookup for field setter */
    protected MethodHandle lookup() {
        try {
            return MethodHandles.lookup().unreflectSetter(field);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (getter)
                field = null;
        }
    }

    /** Enables lazy initialize of MethodHandle lookup only once with reuse. */
    public MethodHandle getSetterHandle() {
        if (null == setter)
            setter = lookup();
        return setter;
    }

    /** Specialty invoke for field access invocable types.
     * Based on arguments supplied infer get or set operation.
     * {@inheritDoc} */
    @Override
    public synchronized Object invoke(Object base, Object... pars)
            throws InvocationTargetException {
        try {
            if (0 == pars.length) { // getter
                if (isStatic())
                    return Primitive.wrap(
                            getMethodHandle().invoke(), getReturnType());
                return Primitive.wrap(
                        getMethodHandle().invoke(base), getReturnType());
            } else {                // setter
                if (isStatic())
                    return getSetterHandle().invoke(super.coerceToType(
                            pars[0], getParameterTypes()[0]));
                return getSetterHandle().invoke(base, super.coerceToType(
                            pars[0], getParameterTypes()[0]));
            }
        }
        catch (Throwable ite) {
            throw new InvocationTargetException(ite.getCause());
        }
    }

    /** Override default prototype construct with value implementation. */
    @Override
    public Class<?> getReturnType() { return type; }

    /** Override default prototype construct with value implementation. */
    @Override
    public int getParameterCount() { return 1; }

    /** Override default prototype construct with value implementation. */
    @Override
    public Class<?>[] getParameterTypes() { return new Class<?>[] {type}; }

}
