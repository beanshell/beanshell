package bsh.security;

/** It's the interface to implement a single SecurityGuard to be used by MainSecurityGuard */
public interface SecurityGuard {

    /**
     * Validate and return if you can create a instance
     * @param _class - It can be a Class or Interface ( To anonymous inner classes )
     * @param args - Args used to call the constructor
     */
    public boolean canConstruct(Class<?> _class, Object[] args);

    /** Validate and return if a specific static method of a specific class can be invoked */
    public boolean canInvokeStaticMethod(Class<?> _class, String methodName, Object[] args);

    /** Validate and return if a specific method of a specific object can be invoked. */
    public boolean canInvokeMethod(Object thisArg, String methodName, Object[] args);

    /** Validate and return if can call a method of super class */
    public boolean canInvokeSuperMethod(Class<?> superClass, Object thisArg, String methodName, Object[] args);

    /** Validate and return if can get a field of a specific object */
    public boolean canGetField(Object thisArg, String fieldName);

    /** Validate and return if can get a static field of a specific class */
    public boolean canGetStaticField(Class<?> _class, String fieldName);

    /** Validate and return if some class can extends {@link superClass} */
    public boolean canExtends(Class<?> superClass);

    /** Validate and return if some class can implements {@link _interface} */
    public boolean canImplements(Class<?> _interface);

}
