# Migrating scripts to BeanShell 3.x

This guide describes changes that can affect existing scripts and ways to make
their intent explicit. It compares behavior on the current development branch
against the historical 2.x releases below. Build and Java integration notices
are summarized under [Embedding applications](#embedding-applications); the
[changelog](CHANGES.md) has the rest.

## Versions and changes covered here

We checked the examples on 21 October 2026. Historical comparisons use the
published artifacts **`org.beanshell:bsh:2.0b4`** and
**`org.apache-extras.beanshell:bsh:2.0b6`**.

Some fixes on 3.0 corrected behavior that only earlier 3.0 snapshots and
3.0.0 beta 1 had; 2.x already behaved correctly. They are listed under
[Changes since earlier 3.0 builds](#changes-since-earlier-30-builds) instead of
in the 3.0 sections.

## Numeric values that don't fit their type

In 2.x, a typed declaration silently truncated a fraction and wrapped an
out-of-range number. 3.0 follows Java's assignment rules instead: the value
must fit the declared type, or the declaration fails
([#911](https://github.com/beanshell/beanshell/issues/911)).

| Script | 2.0b4 and 2.0b6 | 3.0 |
| --- | --- | --- |
| `byte b = 300;` | `44` | Error |
| `int x = 3.7;` | `3` | Error |
| `long l = 2.5;` | `2` | Error |
| `int i = 3000000000L;` | `-1294967296` | Error |
| `char c = 70000;` | `4464` | Error |
| `int i = 65601; byte b = i;` | `65` | Error |
| `float f = 1e300;` | `Infinity` | Error |
| `a[1.7]` | `a[1]` | Error |
| `new int[2.5]` | Length 2 | Error |

A whole value that fits is still accepted, whatever its numeric type:
`int x = 3.0;`, `Integer x = 3.0;` and `long l = 5L; int i = l;` all work. If a
script relies on the old truncation or wrapping, add the explicit cast. Casts
behave as in Java and 2.x:

```java
int x = (int) 3.7;      // 3
byte b = (byte) 300;    // 44
a[(int) 1.7];           // a[1]
float f = (float) 1e300; // Infinity
```

A fractional argument to a Java method that takes an `int`, such as
`Integer.toHexString(1.5)`, failed in 2.x with "method not found". 3.0 finds
the method and then fails with a `ClassCastException` when it converts the
argument. Cast the argument in both cases.

A plain reassignment such as `i = i / 2.0` already failed in 2.x when `i` is an
`int`, and still fails on 3.0.

### Compound assignments narrow as in Java

The other direction is more permissive. 2.x rejected every compound assignment
whose result needed narrowing. 3.0 applies Java's implicit cast:

| Script | 2.0b4 and 2.0b6 | 3.0 |
| --- | --- | --- |
| `int i = 5; i += 2.5;` | Error | `7` |
| `byte c = 100; c += 200;` | Error | `44` |
| `byte b = 1; b <<= 9;` | Error | `0` |

`++` and `--` also narrow, as they already did in 2.x: `byte b = 127; b++;`
gives `-128`.

### Conversion errors can be caught

In 2.x a failed reassignment, such as `int i = 1; i = "x";`, ended the script;
no `catch` could see it. On 3.0, any failed assignment conversion throws a
`ClassCastException` that a script can catch. That includes declarations,
reassignments, array elements, fields, method arguments and return values:

```java
try {
    int x = 3.7;
} catch (ClassCastException e) {
    print(e.getMessage()); // cannot assign number 3.7 to type int
}
```

Storing a number that doesn't fit into a numeric array, such as
`bytes[0] = 300`, now throws `ClassCastException`. 2.x threw
`ArrayStoreException`.

## Variables created inside blocks

In 2.0b4, a new untyped variable assigned inside an ordinary block is visible in
the enclosing scope. In 2.0b6, the new variable belongs to the block. 3.0 uses
this behavior.

```java
{ x = 5; }
x == void; // false in 2.0b4; true in 2.0b6 and 3.0
```

Initialize the variable in the scope where you need to use it. Assignment in a
child block then updates the existing variable:

```java
x = null;
{ x = 5; }
x; // 5 in all three checked versions
```

You can also assign the variable through `super` for this ordinary block:

```java
{ super.x = 5; }
x; // 5
```

For scripts with nested blocks or methods, initialize the variable in the
enclosing scope. This makes the variable's intended lifetime clear. A variable
can go out of scope by design. A cache defect can lose a variable that is still
in scope.

[#555](https://github.com/beanshell/beanshell/issues/555) records the scoping change
and workarounds. [#727](https://github.com/beanshell/beanshell/issues/727) asks for
an optional mode that restores the earlier behavior. Its implementation in
[#728](https://github.com/beanshell/beanshell/pull/728) is closed and unmerged.
The checked 3.0 has no `setBsh2ScopingCompatibility` API. The existing
`setCompatibility()` / `bsh.compatibility` setting controls Java source loading.
It does not restore the block scope from 2.0b4.

## Undefined values in expressions

Both 2.0b4 and 2.0b6 turn an undefined value into the text `void` in this string
expression. 3.0 reports an evaluation error:

```java
"hello " + missing;
```

Before you use a variable, initialize it or check whether it exists. If you need
the result after the block, initialize it in the enclosing scope:

```java
message = null;
if (missing == void) {
    message = "hello guest";
} else {
    message = "hello " + missing;
}
message; // "hello guest" when missing is undefined
```

`missing == void` remains a supported existence check on 3.0. A variable with
an assigned `null` value is different from an undefined variable. If you
initialize a variable to `null`, it is still defined. Do not replace every
`void` check with a null check.

[#746](https://github.com/beanshell/beanshell/issues/746) asks for documentation of
these differences. It does not ask for a change to string concatenation with an
undefined value.

## Objects created inside loops

On 3.0, each loop iteration has its own scope, as in Java. An anonymous class,
a scripted object (a method that returns `this`) or a lambda created in the loop
sees the variables of its own iteration. In 2.x, every such object saw the
values from the last iteration
([#848](https://github.com/beanshell/beanshell/issues/848)):

```java
l = new java.util.ArrayList();
for (String s : new String[] {"a", "b", "c"}) {
    l.add(new Runnable() { public void run() { print(s); } });
}
for (r : l) r.run(); // "c", "c", "c" in 2.x; "a", "b", "c" on 3.0
```

A script that relied on the old behavior to read a loop's final value through
such an object should store that value in a variable declared outside the loop
instead.

## Output of the print() command

`print()` adds the value's type to anything that isn't a string or character.
2.x printed only the value:

| Script | 2.0b4 and 2.0b6 | 3.0 |
| --- | --- | --- |
| `print(5);` | `5` | `5I :int` |
| `print(true);` | `true` | `true :boolean` |
| `print(new java.util.ArrayList());` | `[]` | `[] :List` |
| `print("abc");` | `abc` | `abc` |

If another program reads or compares a script's output, convert the value to a
string first with `print("" + x)`, or use `System.out.println(x)`, which
prints only the value. `System.out.println` bypasses an output stream set with
`Interpreter.setOut()` or `setOutputFile()`; `print()` doesn't.

## Catching out-of-bounds array and List store errors

In 2.x, an out-of-bounds array or `List` store threw a plain `bsh.EvalError`
that no script `catch` block could see, not even `catch (Throwable)`. Reads
(`x = arr[3]`) already surfaced correctly; only stores, and the read-before-write
step in `arr[i] += x`, `arr[i]++`, and `++arr[i]`, were affected.

```java
arr = new int[3];
try {
    arr[10] = 5;
} catch (Throwable t) {
    print("caught: " + t); // caught: java.lang.ArrayIndexOutOfBoundsException
}
```

If your script treated an out-of-bounds store as fatal, or worked around the
missing catch by checking bounds yourself first, either still works; you can
now also catch the exception directly where that's more convenient.
[#824](https://github.com/beanshell/beanshell/pull/824)

## Primitive values and wrapper utility methods

Older scripts could observe the internal `bsh.Primitive` wrapper. On 3.0,
script-visible primitive operations expose the value and its primitive type.
For a fresh `int i = 1`, the checked versions behave as follows:

| Expression | 2.0b4 and 2.0b6 | 3.0 |
| --- | --- | --- |
| `i.getClass()` | `bsh.Primitive.class` | `int.class` |
| `i.getType()` | `int.class` | `int.class` |
| `i.getValue()` | `1` | Method-not-found error |

Use `i` directly instead of `i.getValue()`. To ask whether its exposed type is
primitive, use `i.getClass().isPrimitive()`. `getType()` remains available on
primitive values. Java objects do not all have a `getType()` method.

On 3.0, `i instanceof Number` can replace `isNumber()` for numeric values.
Characters are not instances of `Number`. To get a `Number`, assign or cast the
value to that type instead of the `numberValue()` call:

```java
int i = 1;
Number value = i;
value.intValue(); // 1
```

These replacements apply to 3.0. The checked 2.0b4 and 2.0b6 releases return
false for `int i = 1; i instanceof Number`. Both releases reject the assignment
above.

The [Primitive 3.0 discussion (#687)](https://github.com/beanshell/beanshell/issues/687)
contains the original compatibility analysis and details of numeric convenience
methods. The Java implementation still uses `bsh.Primitive` internally.

## Java helpers used below

The varargs changes apply to calls to **compiled Java methods and constructors**.
With this helper, you can check overload selection and property access. Save
the following code in `MigrationExamples.java`:

```java
package examples;

import java.util.Arrays;

public class MigrationExamples {
    public final Object[] values;
    public MigrationExamples(Object... values) { this.values = values; }
    public static String arguments(Object... values) {
        return values == null ? "null array" : Arrays.toString(values);
    }
    public static String integers(int... values) {
        return values == null ? "null array" : Arrays.toString(values);
    }
    public static String pick(Object value) { return "Object"; }
    public static String pick(Object... values) { return "Object[]"; }
    public static boolean same(Object left, Object right) { return left == right; }

    public static class Switch {
        private boolean raised;
        public void up() { raised = true; }
        public boolean isUp() { return raised; }
    }
    public static class GetterOnly {
        public boolean isUp() { return true; }
    }
    public static class Label {
        public String title(Object value) { return "method"; }
        public void setTitle(String value) {}
    }
}
```

The compiler creates the `examples` package under the current directory. Use
these steps to make the helper available:

1. Compile the file with `javac -d . MigrationExamples.java`.
2. Before you start BeanShell, add the current directory to the Java classpath.

Import the helper in the scripts that use it:

```java
import examples.MigrationExamples;
```

## Null arguments and Java varargs

A Java `Object...` parameter is an `Object[]`. A null array, an array with one
null element, and an empty array are different values. 3.0 uses the declared
type of a null argument, if one is available, to choose between them
([#797](https://github.com/beanshell/beanshell/pull/797)). 2.x passed a null
array whenever the argument was null.

For the Java helpers above:

| Call | 2.0b4 and 2.0b6 | 3.0 |
| --- | --- | --- |
| `MigrationExamples.arguments(null)` | `null array` | `null array` |
| `MigrationExamples.arguments((Object[]) null)` | `null array` | `null array` |
| `MigrationExamples.arguments((Object) null)` | `null array` | `[null]` |
| `MigrationExamples.arguments()` | Method-not-found error | `[]` |
| `MigrationExamples.arguments(new Object[] {null})` | `[null]` | `[null]` |
| `MigrationExamples.integers(null)` | `null array` | `null array` |

To pass a null array, use bare `null` or `(Object[]) null`. To pass one null
element for `Object...`, use `(Object) null` or an explicit
`new Object[] {null}`.

These rules also apply to null values from typed variables. In 2.x both calls
below passed a null array:

```java
Object[] array = null;
Object element = null;
MigrationExamples.arguments(array);   // "null array"
MigrationExamples.arguments(element); // "[null]" on 3.0
```

BeanShell also keeps the declared type of null arguments from casts, fields,
array elements, and method results. This affects overload selection. Bare null
selects `pick(Object...)` instead of `pick(Object)` because the array
parameter is more specific.

| Call | 2.0b4 and 2.0b6 | 3.0 |
| --- | --- | --- |
| `MigrationExamples.pick(null)` | `Object[]` | `Object[]` |
| `MigrationExamples.pick((Object[]) null)` | `Object[]` | `Object[]` |
| `MigrationExamples.pick((Object) null)` | `Object[]` | `Object` |

Constructor calls follow the same rule. `new MigrationExamples(null).values`
is a null array, but on 3.0 `new MigrationExamples((Object) null).values`
contains one null element. In 2.x it was a null array.

This does not switch all dispatch to Java's declared-type rules. BeanShell
still uses runtime types for non-null arguments. This also applies to non-null
arrays in `Object` variables. BeanShell still prefers String for ambiguous
bare-null calls. If an expression gives no declared type for null, BeanShell
keeps its untyped-null behavior. This change does not include script-defined
varargs packing or generated constructor delegation.

## Object arrays passed to typed Java varargs

In 2.x, passing an `Object[]` for a Java method's typed varargs parameter
failed with "method not found". 3.0 converts each element with BeanShell's
usual coercion rules and builds the declared array type, so the call below
works even though an `Object[]` is not a `Class[]`
([#822](https://github.com/beanshell/beanshell/pull/822)):

```java
class Sample { void run(String s, int i) {} }
Object[] types = new Object[] { String.class, Integer.TYPE };
m = Sample.class.getDeclaredMethod("run", types);
print(m); // public void Sample.run(java.lang.String,int) :Method
```

You no longer need to build a correctly typed array yourself before making a
call like this. Already typed arrays, null arrays, and expanded varargs
(`f(a, b, c)`) keep their existing behavior, and method overload selection is
unchanged.

## Method calls and property aliases

A call such as `device.up()` invokes the real `up()` method in both 2.x and
3.0. 3.0 adds a fallback: if no real method with that name is applicable,
the call uses a JavaBean property accessor of the same name. 2.x reported a
method-not-found error instead.

With the Java helpers above:

```java
device = new MigrationExamples.Switch();
device.up();
device.isUp(); // true: the real up() changed the state

label = new MigrationExamples.Label();
label.title("x"); // "method": selects title(Object), not setTitle(String)

getter = new MigrationExamples.GetterOnly();
getter.up(); // true on 3.0 through isUp(); method-not-found error in 2.x
```

A real method takes precedence even if the alias has a more-specific
parameter. To invoke the setter explicitly, call `label.setTitle("x")`. An
exception from a selected real method does not trigger alias fallback.
Property reads and writes keep their existing accessors and field precedence.
[#798](https://github.com/beanshell/beanshell/pull/798)

## Lambda expressions

BeanShell 3.x adds lambda expressions: `x -> ...`, `(a, b) -> ...`, and
`(Type a) -> ...`, with either an expression or a block body. A lambda can be
used as a functional-interface value, without a cast, anywhere BeanShell
resolves a method call against a Java or scripted interface parameter
([#675](https://github.com/beanshell/beanshell/issues/675)). `->` was not
legal syntax before this, so no existing script parses differently because of
it; what follows are the places new lambda syntax runs into behavior scripts
already relied on.

A block body is BeanShell's `{ ... }` block, not its array-initializer
shorthand. `x -> {1,2,3}` tries to parse `1,2,3` as three statements and
fails:

```java
x -> {1,2,3}; // Unable to parse code syntax. Encountered: ,
```

Parenthesize the body to get the array value instead:

```java
call(java.util.function.IntFunction f) { return f.apply(5); }
call(x -> ({1,2,3})); // int[] {1, 2, 3}
```

A lambda captures its declaring scope by reference, not by value, so a later
change to a captured variable is visible to the lambda, and the lambda can
change it:

```java
int n = 0;
java.util.function.IntSupplier next = () -> ++n;
next.getAsInt();
next.getAsInt();
n; // 2
```

A lambda created in a loop sees its own iteration's variables, as described
in [Objects created inside loops](#objects-created-inside-loops).

A lambda's result must fit the interface method's return type, under the same
rules as [numeric assignments](#numeric-values-that-dont-fit-their-type). A
`Comparator` written as `(a, b) -> a - b` fails when it compares `3.5` and
`1.0`, because `2.5` doesn't fit the `int` that `compare` returns. Use
`Double.compare(a, b)`, or cast the result: `(a, b) -> (int) (a - b)`.

Where several overloads accept a lambda argument, BeanShell picks one by the
body's statically known result and shape. A body with a result selects
`Supplier` over `Runnable`, and a block body with no result selects
`Runnable`. BeanShell also picks an overload where javac would report the call
ambiguous: with `m(Callable)` and `m(Supplier)`, `m(() -> 1)` calls
`m(Callable)` whichever is declared first. Cast the lambda to choose an
overload explicitly: `m((java.util.function.Supplier) () -> 1)`. The lambda
entry in [CHANGES.md](CHANGES.md) lists the interfaces that can't be lambda
targets.

## Removed features

The built-in HTTP server and remote console are gone: the `server()` command,
`bsh.util.Httpd`, `bsh.util.Sessiond`, the `bsh.servlet` package, and the
remote console applets. None of them were authenticated. On 3.0, `server(1234)`
fails with `Command not found: server(int)`
([#496](https://github.com/beanshell/beanshell/issues/496)).

## Embedding applications

- **Maven coordinates:** the artifact is `io.github.beanshell:bsh`. 2.x was
  published as `org.beanshell:bsh` and `org.apache-extras.beanshell:bsh`. The
  OSGi bundle symbolic name is `org.beanshell.bsh`.
- **Java version:** the JAR targets Java 8 and needs Java 8 or newer to run.
- **Error messages:** the text of `EvalError.getMessage()` and
  `TargetError.getMessage()` has a new format
  ([#895](https://github.com/beanshell/beanshell/pull/895)). For a script that
  throws `new RuntimeException("boom")` through `eval()`, the message starts
  with `Sourced file: inline evaluation of:` and ends with `: TargetError` in
  2.x. On 3.0 it starts with `Uncaught Exception` and ends with
  `Caused by: java.lang.RuntimeException: boom`. If your code parses the
  message, use the structured accessors instead: `getErrorLineNumber()`,
  `getErrorSourceFile()`, `getErrorText()` and `getCause()`.

## Changes since earlier 3.0 builds

These fixes matter only if you used a 3.0 snapshot or 3.0.0 beta 1. In each
case 2.0b4 and 2.0b6 already gave the result 3.0 gives now, so a script
coming straight from 2.x needs no change.

- **Numeric reference casts
  ([#792](https://github.com/beanshell/beanshell/pull/792)):**
  `(Number) Double.valueOf(1)` keeps the original numeric object, so
  `MigrationExamples.same(original, (Number) original)` is true.
- **Scalar and array overloads
  ([#793](https://github.com/beanshell/beanshell/pull/793)):** overload
  selection uses array rank, so `pick("a")` with overloads `pick(value)` and
  `pick(String[] values)` selects the scalar one. Parameter brackets after the
  name count as array dimensions: `first(String[] values[])` takes a
  `String[][]`. 2.x could not parse that form.
- **Boxed unary operators
  ([#795](https://github.com/beanshell/beanshell/pull/795)):** `boxed++` on an
  `Integer` returns the original boxed value and stores an `Integer`.
- **Float arithmetic
  ([#796](https://github.com/beanshell/beanshell/pull/796)):** float operands
  use float arithmetic, as in Java. `16777216f + 1` is `16777216.0`,
  `Float.MAX_VALUE * 2f` is infinity and `(1f * 2f).getClass()` is
  `float.class`. Widen an operand first if you need a double calculation:
  `(double) value + 1`.
- **Values no longer convert between unrelated types
  ([#759](https://github.com/beanshell/beanshell/issues/759)):** beta 1 treated
  any value as a boolean and converted strings, booleans and null to numbers.
  `if (0.5)`, `(Boolean) "false"`, `Math.sqrt("4")`, `1 && true`,
  `int i = null;` and `f(null)` for `f(int)` are errors again. `{1, 2, null}`
  is an `Integer[]` that keeps the null, and a scripted class's `Integer` field
  starts out null.
- **Method variable lookup
  ([#906](https://github.com/beanshell/beanshell/issues/906)):** a scripted
  method looks up variables it doesn't declare where the method was declared,
  not in its caller's block.
- **Byte widening ([#911](https://github.com/beanshell/beanshell/issues/911)):**
  `byte b = -1; int i = b;` gives `-1` again; beta 1 gave `255`.
- **Missing fields and enum constants
  ([#918](https://github.com/beanshell/beanshell/issues/918)):** a field,
  property or enum constant that doesn't exist on a Java class, Java object or
  scripted class is an error again. Beta 1 gave `void`, so
  `TimeUnit.SECONDS == TimeUnit.FOO` was `false`. A missing map key, a missing
  variable of a scripted object and an undefined variable still give `void`.

This guide covers the linked changes, not every difference between historical
BeanShell releases. Test representative application scripts against the exact
candidate version, especially where results choose overloads or values cross
Java/BeanShell boundaries.
