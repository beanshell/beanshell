package bsh;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * It's the "main" SecurityGuard that must be used to real validate something.
 * This class store some implementations of {@link SecurityGuard} that each has some specific validation
 */
final class MainSecurityGuard {

    private final Set<SecurityGuard> securityGuards = new HashSet<SecurityGuard>();

    protected MainSecurityGuard() {
        this.securityGuards.add(new BasicSecurityGuard());
    }

    /** Add a SecurityGuard to be used */
    public void add(SecurityGuard guard) {
        this.securityGuards.add(guard);
    }

    /** Remove a SecurityGuard that is being used. Return if it really contained this SecurityGuard */
    public boolean remove(SecurityGuard guard) {
        return this.securityGuards.remove(guard);
    }

    /** Validate if you can create a instance */
    protected void canConstruct(Class<?> _class, Object[] args) throws SecurityError {
        final Object[] _args = Primitive.unwrap(args);
        for (SecurityGuard guard: this.securityGuards)
            if (!guard.canConstruct(_class, _args))
                throw SecurityError.cantConstruct(_class, _args);
    }

    /** Validate if a specific static method of a specific class can be invoked */
    protected void canInvokeStaticMethod(Class<?> _class, String methodName, Object[] args) throws SecurityError {
        final Object[] _args = Primitive.unwrap(args);
        this.canInvokeStaticMethodImpl(_class, methodName, _args);
        this.canInvokeStaticMethodImplToReflectionCanGetArrayLength(_class, methodName, _args);
    }

    /** Real validate if a specific static method of a specific class can be invoked */
    private void canInvokeStaticMethodImpl(Class<?> _class, String methodName, Object[] args) throws SecurityError {
        for (SecurityGuard guard: this.securityGuards)
            if (!guard.canInvokeStaticMethod(_class, methodName, args))
                throw SecurityError.cantInvokeStaticMethod(_class, methodName, args);
    }

    /** Validate if the length of an array can be get when using Reflection API */
    private void canInvokeStaticMethodImplToReflectionCanGetArrayLength(Class<?> _class, String methodName, Object[] args) throws SecurityError {
        if (!methodName.equals("getLength") || args.length != 1 || !_class.isAssignableFrom(Array.class)) return;

        final Object _thisArg = args[0];
        final String fieldName = "length";
        try {
            this.canGetField(_thisArg, fieldName);
        } catch (SecurityError error) {
            throw SecurityError.reflectCantGetField(_thisArg, fieldName);
        }
    }

    /** Validate if a specific method of a specific object can be invoked. */
    protected void canInvokeMethod(Object thisArg, String methodName, Object[] args) throws SecurityError {
        final Object[] _args = Primitive.unwrap(args);
        this.canInvokeMethodImpl(thisArg, methodName, _args);
        this.canInvokeMethodImplToReflectionCanGetField(thisArg, methodName, _args);
        this.canInvokeMethodImplToReflectionCanConstruct(thisArg, methodName, _args);
        this.canInvokeMethodImplToReflectionCanInvokeMethod(thisArg, methodName, _args);
    }

    /** Real validate if a specific method of a specific object can be invoked. */
    private final void canInvokeMethodImpl(Object thisArg, String methodName, Object[] args) throws SecurityError {
        for (SecurityGuard guard: this.securityGuards)
            if (!guard.canInvokeMethod(thisArg, methodName, args))
                throw SecurityError.cantInvokeMethod(thisArg, methodName, args);
    }

    /** Validate if can get a field when using Reflection API */
    private final void canInvokeMethodImplToReflectionCanGetField(Object thisArg, String methodName, Object[] args) throws SecurityError {
        if (!methodName.equals("get") || args.length != 1 || !(thisArg instanceof Field)) return;

        Field field = (Field) thisArg;
        String fieldName = field.getName();

        if (Reflect.isStatic(field)) {
            Class<?> _class = field.getDeclaringClass();
            try {
                this.canGetStaticField(_class, fieldName);
            } catch (SecurityError error) {
                throw SecurityError.reflectCantGetStaticField(_class, fieldName);
            }
        } else {
            Object _thisArg = args[0];
            try {
                this.canGetField(_thisArg, fieldName);
            } catch (SecurityError error) {
                throw SecurityError.reflectCantGetField(_thisArg, fieldName);
            }
        }
    }

    /** Validate if can invoke a method when using Reflection API */
    private final void canInvokeMethodImplToReflectionCanInvokeMethod(Object thisArg, String methodName, Object[] args) throws SecurityError {
        if (!methodName.equals("invoke") || args.length == 0 || !(thisArg instanceof Method)) return;

        Method method = (Method) thisArg;
        String _methodName = method.getName();
        Object[] _args = args.length == 2 && args[1] instanceof Object[]
                            ? (Object[]) args[1]
                            : Arrays.copyOfRange(args, 1, args.length);

        if (Reflect.isStatic(method)) {
            Class<?> _class = method.getDeclaringClass();
            try {
                this.canInvokeStaticMethod(_class, _methodName, _args);
            } catch (SecurityError error) {
                throw SecurityError.reflectCantInvokeStaticMethod(_class, _methodName, _args);
            }
        } else {
            Object _thisArg = args[0];
            try {
                this.canInvokeMethod(_thisArg, _methodName, _args);
            } catch (SecurityError error) {
                throw SecurityError.reflectCantInvokeMethod(_thisArg, _methodName, _args);
            }
        }
    }

    /** Validate if can construct a instance when using Reflection API */
    private final void canInvokeMethodImplToReflectionCanConstruct(Object thisArg, String methodName, Object[] args) throws SecurityError {
        // Deprecated way using reflection
        if (thisArg instanceof Class<?> && methodName.equals("newInstance")) {
            Class<?> _class = (Class<?>) thisArg;
            Object[] _args = new Object[0];
            try {
                this.canConstruct(_class, _args);
            } catch (SecurityError error) {
                throw SecurityError.reflectCantConstruct(_class, _args);
            }
        }
        // Modern way using reflection
        else if (thisArg instanceof Constructor<?> && methodName.equals("newInstance")) {
            Class<?> _class = ((Constructor<?>) thisArg).getDeclaringClass();
            Object[] _args = args.length == 1 && args[0] instanceof Object[]
                                ? (Object[]) args[0]
                                : args;
            try {
                this.canConstruct(_class, _args);
            } catch (SecurityError error) {
                throw SecurityError.reflectCantConstruct(_class, _args);
            }
        }
    }

    /** Validate if can call a local method ( aka commands ) */
    protected void canInvokeLocalMethod(String methodName, Object[] args) throws SecurityError {
        final Object[] _args = Primitive.unwrap(args);
        for (SecurityGuard guard: this.securityGuards)
            if (!guard.canInvokeLocalMethod(methodName, _args))
                throw SecurityError.cantInvokeLocalMethod(methodName, _args);
    }

    /** Validate if can call a method of super class */
    protected void canInvokeSuperMethod(Class<?> superClass, Object thisArg, String methodName, Object[] args) throws SecurityError {
        final Object[] _args = Primitive.unwrap(args);
        for (SecurityGuard guard: this.securityGuards)
            if (!guard.canInvokeSuperMethod(superClass, thisArg, methodName, _args))
                throw SecurityError.cantInvokeSuperMethod(superClass, methodName, _args);
    }

    /** Validate if can get a field of a specific object */
    protected void canGetField(Object thisArg, String fieldName) throws SecurityError {
        for (SecurityGuard guard: this.securityGuards)
            if (!guard.canGetField(thisArg, fieldName))
                throw SecurityError.cantGetField(thisArg, fieldName);
    }

    /** Validate if can get a static field of a specific class */
    protected void canGetStaticField(Class<?> _class, String fieldName) throws SecurityError {
        for (SecurityGuard guard: this.securityGuards)
            if (!guard.canGetStaticField(_class, fieldName))
                throw SecurityError.cantGetStaticField(_class, fieldName);
    }

    /** Validate if {@link _class} can extends {@link superClass} */
    protected void canExtends(Class<?> superClass) throws SecurityError {
        for (SecurityGuard guard: this.securityGuards)
            if (!guard.canExtends(superClass))
                throw SecurityError.cantExtends(superClass);
    }

    /** Validate if {@link _class} can implements {@link _interface} */
    protected void canImplements(Class<?> _interface) throws SecurityError {
        for (SecurityGuard guard: this.securityGuards)
            if (!guard.canImplements(_interface))
                throw SecurityError.cantImplements(_interface);
    }

    /** It prevents the execution of codes that manipulate the SecurityGuard or MainSecurityGuard */
    private class BasicSecurityGuard implements SecurityGuard {

        public boolean canInvokeMethod(Object thisArg, String methodName, Object[] args) {
            return !(thisArg instanceof MainSecurityGuard);
        }

        public boolean canGetStaticField(Class<?> _class, String fieldName) {
            if (_class == Interpreter.class && fieldName.equals("mainSecurityGuard")) return false;
            return true;
        }

        public boolean canImplements(Class<?> _interface) {
            return _interface != SecurityGuard.class;
        }

    }
}
