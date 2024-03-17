package bsh.security;

import java.util.ArrayList;
import java.util.List;

import bsh.CallStack;
import bsh.EvalError;
import bsh.SimpleNode;
import bsh.Types;
import bsh.UtilEvalError;

/** It's a specific error that is throwed when try to execute something that mustn't be executed */
public class SecurityError extends UtilEvalError {

    public SecurityError(String msg) {
        super("SecurityError: " + msg);
    }

    public EvalError toEvalError(String msg, SimpleNode node, CallStack callstack)  {
        return new EvalError(this.getMessage(), node, callstack);
    }

    public EvalError toEvalError(SimpleNode node, CallStack callstack) {
        return new EvalError(this.getMessage(), node, callstack);
    }

    /** This method basically return the types of args at a concatened String by ", " */
    private static String argsTypesString(Object[] args) {
        List<String> typesString = new ArrayList<String>();
        for (Class<?> typeClass: Types.getTypes(args))
            typesString.add(typeClass.getName());
        return String.join(", ", typesString);
    }

    private static String classNameOf(Class<?> _class) {
        if (!_class.isArray()) return _class.getName();

        // Return a string like "int[]", "double[]", "double[][]", etc...
        Class<?> arrayType = _class.getComponentType();
        return classNameOf(arrayType) + "[]";
    }

    /** Create a error for when can't construct a instance */
    public static SecurityError cantConstruct(Class<?> _class, Object[] args) {
        String msg = String.format("Can't call this construct: new %s(%s)", _class.getName(), argsTypesString(args));
        return new SecurityError(msg);
    }

    /** Create a error for when can't construct a instance using reflection */
    public static SecurityError reflectCantConstruct(Class<?> _class, Object[] args) {
        String msg = String.format("Can't call this construct using reflection: new %s(%s)", _class.getName(), argsTypesString(args));
        return new SecurityError(msg);
    }

    /** Create a error for when can't invoke a static method */
    public static SecurityError cantInvokeStaticMethod(Class<?> _class, String methodName, Object[] args) {
        String className = classNameOf(_class);
        String msg = String.format("Can't invoke this static method: %s.%s(%s)", className, methodName, argsTypesString(args));
        return new SecurityError(msg);
    }

    /** Create a error for when can't invoke a static method using reflection */
    public static SecurityError reflectCantInvokeStaticMethod(Class<?> _class, String methodName, Object[] args) {
        String className = classNameOf(_class);
        String msg = String.format("Can't invoke this static method using reflection: %s.%s(%s)", className, methodName, argsTypesString(args));
        return new SecurityError(msg);
    }

    /** Create a error for when can't invoke a method */
    public static SecurityError cantInvokeMethod(Object thisArg, String methodName, Object[] args) {
        String className = classNameOf(thisArg.getClass());
        String msg = String.format("Can't invoke this method: %s.%s(%s)", className, methodName, argsTypesString(args));
        return new SecurityError(msg);
    }

    /** Create a error for when can't invoke a method using reflection */
    public static SecurityError reflectCantInvokeMethod(Object thisArg, String methodName, Object[] args) {
        String className = classNameOf(thisArg.getClass());
        String msg = String.format("Can't invoke this method using reflection: %s.%s(%s)", className, methodName, argsTypesString(args));
        return new SecurityError(msg);
    }

    /** Create a error for when can't invoke a local method ( aka commands ) */
    public static SecurityError cantInvokeLocalMethod(String methodName, Object[] args) {
        String msg = String.format("Can't invoke this local method: %s(%s)", methodName, argsTypesString(args));
        return new SecurityError(msg);
    }

    /** Create a error for when can't invoke a super method */
    public static SecurityError cantInvokeSuperMethod(Class<?> superClass, Object thisArg, String methodName, Object[] args) {
        String superClassName = classNameOf(superClass);
        String msg = String.format("Can't invoke this super method: %s.%s(%s)", superClassName, methodName, argsTypesString(args));
        return new SecurityError(msg);
    }

    /** Create a error for when can't invoke a super method */
    public static SecurityError reflectCantInvokeSuperMethod(Class<?> superClass, Object thisArg, String methodName, Object[] args) {
        String superClassName = classNameOf(superClass);
        String msg = String.format("Can't invoke this super method using reflection: %s.%s(%s)", superClassName, methodName, argsTypesString(args));
        return new SecurityError(msg);
    }

    /** Create a error for when can't get a field */
    public static SecurityError cantGetField(Object thisArg, String fieldName) {
        String className = classNameOf(thisArg.getClass());
        String msg = String.format("Can't get this field: %s.%s", className, fieldName);
        return new SecurityError(msg);
    }

    /** Create a error for when can't get a field */
    public static SecurityError reflectCantGetField(Object thisArg, String fieldName) {
        String className = classNameOf(thisArg.getClass());
        String msg = String.format("Can't get this field using reflection: %s.%s", className, fieldName);
        return new SecurityError(msg);
    }

    /** Create a error for when can't get a field */
    public static SecurityError cantGetStaticField(Class<?> _class, String fieldName) {
        String className = classNameOf(_class);
        String msg = String.format("Can't get this static field: %s.%s", className, fieldName);
        return new SecurityError(msg);
    }

    /** Create a error for when can't get a field */
    public static SecurityError reflectCantGetStaticField(Class<?> _class, String fieldName) {
        String className = classNameOf(_class);
        String msg = String.format("Can't get this static field using reflection: %s.%s", className, fieldName);
        return new SecurityError(msg);
    }

    /** Create a error for when a class can't extends another class */
    public static SecurityError cantExtends(Class<?> superClass) {
        String msg = String.format("This class can't be extended: %s", superClass.getName());
        return new SecurityError(msg);
    }

    /** Create a error for when a class can't implements an interface */
    public static SecurityError cantImplements(Class<?> _interface) {
        String msg = String.format("This interface can't be implemented: %s", _interface.getName());
        return new SecurityError(msg);
    }

}
