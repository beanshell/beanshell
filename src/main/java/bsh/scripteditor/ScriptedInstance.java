package bsh.scripteditor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

import bsh.EvalError;
import bsh.NameSpace;
import bsh.Primitive;
import bsh.This;
import bsh.UtilEvalError;
import bsh.security.SecurityError;

/**
 * A scripted object created by a ScriptedDefinition, together with the input values
 * it must keep. Calls made through as() or invoke() are followed by the contract check.
 */
public final class ScriptedInstance {

    private final Object object;     // proxy, generated-class instance, or This
    private final This self;
    private final String[] names;    // parameter names (empty for anonymous classes)
    private final Object[] values;   // parameter values as stored (bsh.Primitive for primitives)

    ScriptedInstance(Object object, This self, String[] names, Object[] values) {
        this.object = object;
        this.self = self;
        this.names = names;
        this.values = values;
    }

    /** The object exactly as created. Calls made directly on it are NOT checked. */
    public Object object() {
        return object;
    }

    /** The This whose namespace holds the object's methods and variables. */
    public This self() {
        return self;
    }

    /**
     * A typed view. For an interface, every call is followed by the contract check.
     * For a class type (anonymous subclasses) this is a plain cast, since a proxy
     * cannot extend a class.
     * @throws IllegalStateException if the security guard refuses the interface
     */
    @SuppressWarnings("unchecked")
    public <T> T as(Class<T> type) {
        if (!type.isInterface())
            return type.cast(object);
        final Object target;
        try {
            target = type.isInstance(object) ? object : self.getInterface(type);
        } catch (SecurityError e) {
            throw new IllegalStateException("Interface not permitted: " + type.getName(), e);
        }
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
            (proxy, method, args) -> {
                Object result;
                try {
                    result = method.invoke(target, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
                check();
                return result;
            });
    }

    /** Invoke a scripted method by name, then check. */
    public Object invoke(String methodName, Object... args) throws EvalError, UtilEvalError {
        Object result = self.invokeMethod(methodName, args);
        check();
        return result;
    }

    /** Detects rebinding of a parameter (param = ...), not mutation of the object it refers to. */
    public void check() throws UtilEvalError {
        NameSpace ns = self.getNameSpace();
        for (int i = 0; i < names.length; i++) {
            Object now = ns.getVariable(names[i], false);
            boolean same = (values[i] instanceof Primitive) ? values[i].equals(now) : now == values[i];
            if (!same)
                throw new IllegalStateException(
                    "Contract violation: parameter '" + names[i] + "' was reassigned");
        }
    }
}
