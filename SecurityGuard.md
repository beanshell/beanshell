# SecurityGuard

SecurityGuard lets an application define rules for operations performed by
BeanShell scripts. For example, an application can reject selected constructors,
method calls, or field reads. Implement the callbacks you need in Java and
register your guard with `Interpreter.mainSecurityGuard`.

## API version

This page describes the `bsh.security` API in
[#772](https://github.com/beanshell/beanshell/pull/772). That PR makes guard
registration accessible to applications outside BeanShell's own package. Until
it is merged, use a build containing that change to run these examples.

The earlier API uses `bsh.SecurityGuard`, but its package-private
`MainSecurityGuard` prevents ordinary external Java code from calling `add()` and
`remove()`. Changing the import alone does not fix that visibility problem.

## Registering a guard

Each callback returns `true` to allow an operation or `false` to reject it.
Default implementations return `true`. An operation must be allowed by every
registered guard; one guard's approval cannot override another guard's rejection.

Here is a complete Java application. Save it as `Main.java`. It allows adding
elements to a list and reading an element, but rejects calling `List.size()`.

```java
package examples;

import bsh.EvalError;
import bsh.Interpreter;
import bsh.security.SecurityGuard;
import java.util.List;

public class Main {
    public static void main(String[] args) throws EvalError {
        SecurityGuard guard = new SecurityGuard() {
            @Override
            public boolean canInvokeMethod(Object receiver, String name,
                    Object[] arguments) {
                return !(receiver instanceof List && "size".equals(name));
            }
        };

        Interpreter.mainSecurityGuard.add(guard);
        try {
            Interpreter interpreter = new Interpreter();
            interpreter.eval("list = new java.util.ArrayList();"
                    + "list.add(\"Hello\"); list.add(\"World\");");
            System.out.println(interpreter.eval("list.get(0);"));
            try {
                interpreter.eval("list.size();");
            } catch (EvalError error) {
                System.out.println(error.getMessage());
            }
        } finally {
            Interpreter.mainSecurityGuard.remove(guard);
        }
    }
}
```

Compile and run it with a BeanShell JAR containing #772 on the classpath. For
example, on a Unix-like system, with that JAR named `bsh.jar`:

```shell
javac -cp bsh.jar -d . Main.java
java -cp .:bsh.jar examples.Main
```

The output starts with `Hello`, followed by an error containing
`SecurityError: Can't invoke this method: java.util.ArrayList.size()`.

`mainSecurityGuard` is a static registry shared by all interpreter instances
using the same loaded `Interpreter` class. Creating another interpreter does
not create an independent policy. Register application-wide guards before
starting script execution and remove them when that policy is no longer needed.
The `finally` block above cleans up this standalone example. The current registry
does not synchronize registration/removal; avoid changing it while scripts are
using it. A guard shared by concurrent evaluations must also handle concurrent
callback invocations.

## Available callbacks

Override any of the following methods in your `SecurityGuard` implementation.
Argument arrays supplied to the callbacks contain unwrapped values: for example,
an integer argument is a Java numeric wrapper and a null argument is Java `null`.

| Callback | Operation checked | Information supplied |
| --- | --- | --- |
| `canConstruct(Class<?> type, Object[] args)` | Object construction, including anonymous implementation creation | Requested class or interface and constructor arguments |
| `canInvokeMethod(Object receiver, String name, Object[] args)` | Instance method invocation, including `super.method()` | Receiver, method name, and arguments |
| `canInvokeStaticMethod(Class<?> type, String name, Object[] args)` | Static method invocation | Class, method name, and arguments |
| `canInvokeLocalMethod(String name, Object[] args)` | Local method or command invocation | Method name and arguments |
| `canGetField(Object receiver, String name)` | Instance field read, including array `length` | Receiver and field name |
| `canGetStaticField(Class<?> type, String name)` | Static field read | Class and field name |
| `canExtends(Class<?> superClass)` | Class extension | Superclass |
| `canImplements(Class<?> interfaceType)` | Interface implementation. Also consulted when a lambda expression is converted to a functional interface. | Interface |

The earlier `canInvokeSuperMethod()` callback was removed in #772. Move policies
using that callback to `canInvokeMethod()`. The replacement callback also checks
ordinary instance calls and does not receive the separate superclass argument.
`MainSecurityGuard.remove()` returns `void` in this API.

## Example policies

Each Java block below is a separate guard implementation. Register its instance
from the host application as shown above. Run the accompanying BeanShell code
with that guard installed. Each script first performs a permitted operation,
then attempts an operation the guard rejects.

### Reject construction of lists

```java
import bsh.security.SecurityGuard;
import java.util.List;

class NoListConstruction implements SecurityGuard {
    @Override
    public boolean canConstruct(Class<?> type, Object[] args) {
        return !List.class.isAssignableFrom(type);
    }
}
```

```bsh
new java.util.HashMap();
new java.util.ArrayList(); // Rejected: the requested type is ArrayList.
```

### Reject a static method

```java
import bsh.security.SecurityGuard;
import java.util.Collections;

class NoEmptyList implements SecurityGuard {
    @Override
    public boolean canInvokeStaticMethod(Class<?> type, String name,
            Object[] args) {
        return !(type == Collections.class && "emptyList".equals(name));
    }
}
```

```bsh
java.util.Collections.emptyMap();
java.util.Collections.emptyList(); // Rejected.
```

### Reject a command

```java
import bsh.security.SecurityGuard;

class NoEvalCommand implements SecurityGuard {
    @Override
    public boolean canInvokeLocalMethod(String name, Object[] args) {
        return !"eval".equals(name);
    }
}
```

```bsh
Math.abs(-1);
eval("30 * 3"); // Rejected as a local command call.
```

This rule checks the command named `eval`. Calls such as `interpreter.eval(...)`
are instance method calls and require an instance-method policy if they are to
be restricted as well.

### Reject reading array length

```java
import bsh.security.SecurityGuard;

class NoArrayLength implements SecurityGuard {
    @Override
    public boolean canGetField(Object receiver, String name) {
        return !(receiver.getClass().isArray() && "length".equals(name));
    }
}
```

```bsh
class Value { public int number = 82; }
new Value().number;
new Object[0].length; // Rejected.
```

### Reject reading a static field

```java
import bsh.security.SecurityGuard;
import java.util.Collections;

class NoEmptyMapField implements SecurityGuard {
    @Override
    public boolean canGetStaticField(Class<?> type, String name) {
        return !(type == Collections.class && "EMPTY_MAP".equals(name));
    }
}
```

```bsh
java.util.Collections.EMPTY_LIST;
java.util.Collections.EMPTY_MAP; // Rejected.
```

### Reject extending a class

```java
import bsh.security.SecurityGuard;
import java.util.HashMap;

class NoHashMapExtension implements SecurityGuard {
    @Override
    public boolean canExtends(Class<?> superClass) {
        return superClass != HashMap.class;
    }
}
```

```bsh
class MyList extends java.util.ArrayList {}
class MyMap extends java.util.HashMap {} // Rejected.
```

This example rejects the exact superclass `HashMap`. To include its subclasses,
use `!HashMap.class.isAssignableFrom(superClass)` instead.

### Reject implementing an interface

```java
import bsh.security.SecurityGuard;
import java.util.List;

class NoListImplementation implements SecurityGuard {
    @Override
    public boolean canImplements(Class<?> interfaceType) {
        return interfaceType != List.class;
    }
}
```

```bsh
class MyMap extends java.util.HashMap implements java.util.Map {}
class MyList extends java.util.ArrayList implements java.util.List {} // Rejected.
```

The callback checks explicitly declared interfaces. The construction and
extension callbacks allow additional rules for classes that already implement
an interface.

## Reflection and class loaders

BeanShell applies additional target checks to these reflective operations when
they are invoked through the interpreter:

| Reflective call | Additional target callback |
| --- | --- |
| `Method.invoke(...)` | `canInvokeMethod(...)` or `canInvokeStaticMethod(...)` |
| `Constructor.newInstance(...)` and `Class.newInstance()` | `canConstruct(...)` |
| `Field.get(...)` | `canGetField(...)` or `canGetStaticField(...)` |
| `Array.getLength(...)` | `canGetField(array, "length")` |

For example, with the `List.size()` guard from the complete Java example, both
of these BeanShell calls are rejected:

```bsh
list.size();
java.util.List.class.getMethod("size", new Class[0])
    .invoke(list, new Object[0]);
```

The two statements are alternative attempts: a rejection stops the current
evaluation unless the script handles the error.

An application can also use `canConstruct()` to reject a chosen class-loader
type, or `canInvokeMethod()` to reject its `loadClass` calls. Match the receiver
or requested class as well as the method name. For example, the predicate
`receiver instanceof ClassLoader && "loadClass".equals(name)` identifies an
instance call to a class loader's `loadClass` method. These rules apply at the
intercepted operation; calling an allowed Java method does not instrument the
compiled Java code that runs inside it.

## Errors and scope

The guard manager throws `bsh.security.SecurityError` when a callback rejects an
operation. During script evaluation BeanShell converts it into an `EvalError`,
whose message includes `SecurityError:`. Embedding applications should handle
`EvalError` from `Interpreter.eval()`; `EvalError` can also report unrelated
evaluation failures.

The built-in guard rejects direct script access to the registry and direct
attempts to construct or manipulate guard objects. Application-specific rules
are supplied by the host application. The callbacks cover the operations listed
above; this API does not provide field-write callbacks or execution-time and
memory limits. The reflection table describes specific supported paths, rather
than a guarantee covering every reflective API.

See the [SecurityGuard source and tests in #772](https://github.com/beanshell/beanshell/pull/772/files)
and the [documentation discussion in #770](https://github.com/beanshell/beanshell/issues/770)
for the implementation and the original examples on which this page is based.
