# Migrating scripts toward BeanShell 3.x

This guide describes changes that can affect existing scripts and ways to make
their intent explicit. It compares behavior on the current development branch
against the historical 2.x releases below. Build and Java integration notices
are in the [changelog](CHANGES.md).

## Versions and changes covered here

We checked the examples on 17 September 2026. Historical comparisons use the
published artifacts **`org.beanshell:bsh:2.0b4`** and
**`org.apache-extras.beanshell:bsh:2.0b6`**. The development baseline is
upstream main [**`bb153e0b`**](https://github.com/beanshell/beanshell/tree/bb153e0be0f30a245f2a03e152a7a36077628476).
These are specific versions, not a claim that every 2.x release behaves alike.

The changes below were open pull requests the last time we checked this guide.
All of them have since merged, so the examples describe main's actual
behavior, not a proposal. We re-ran every example against the current head to
confirm it.

| Change | PR | Merged |
| --- | --- | --- |
| Numeric reference casts | [#792](https://github.com/beanshell/beanshell/pull/792) | 2026-09-11 |
| Scalar/array overloads and parameter dimensions | [#793](https://github.com/beanshell/beanshell/pull/793) | 2026-09-11 |
| Boxed unary operators | [#795](https://github.com/beanshell/beanshell/pull/795) | 2026-09-11 |
| Float arithmetic | [#796](https://github.com/beanshell/beanshell/pull/796) | 2026-09-11 |
| Java null arguments and varargs | [#797](https://github.com/beanshell/beanshell/pull/797) | 2026-09-11 |
| Methods and property aliases | [#798](https://github.com/beanshell/beanshell/pull/798) | 2026-09-11 |
| Object arrays passed to typed Java varargs | [#822](https://github.com/beanshell/beanshell/pull/822) | 2026-09-17 |
| Catchable out-of-bounds array/List store errors | [#824](https://github.com/beanshell/beanshell/pull/824) | 2026-09-15 |
| Lambda expressions | [#826](https://github.com/beanshell/beanshell/pull/826) | 2026-09-17 |

## Variables created inside blocks

In 2.0b4, a new untyped variable assigned inside an ordinary block is visible in
the enclosing scope. In 2.0b6, the new variable belongs to the block. Main uses
this behavior.

```java
{ x = 5; }
x == void; // false in 2.0b4; true in 2.0b6 and main
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
The checked main has no `setBsh2ScopingCompatibility` API. The existing
`setCompatibility()` / `bsh.compatibility` setting controls Java source loading.
It does not restore the block scope from 2.0b4.

## Undefined values in expressions

Both 2.0b4 and 2.0b6 turn an undefined value into the text `void` in this string
expression. Main reports an evaluation error:

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

`missing == void` remains a supported existence check on main. A variable with
an assigned `null` value is different from an undefined variable. If you
initialize a variable to `null`, it is still defined. Do not replace every
`void` check with a null check.

[#746](https://github.com/beanshell/beanshell/issues/746) asks for documentation of
these differences. It does not ask for a change to string concatenation with an
undefined value.

## Catching out-of-bounds array and List store errors

Before #824 (merged 2026-09-15), an out-of-bounds array or `List` store threw a
plain `bsh.EvalError` that no script `catch` block could see, not even
`catch (Throwable)`. Reads (`x = arr[3]`) already surfaced correctly; only
stores, and the read-before-write step in `arr[i] += x`, `arr[i]++`, and
`++arr[i]`, were affected.

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

Older scripts could observe the internal `bsh.Primitive` wrapper. On main,
script-visible primitive operations expose the value and its primitive type.
For a fresh `int i = 1`, the checked versions behave as follows:

| Expression | 2.0b4 and 2.0b6 | Main |
| --- | --- | --- |
| `i.getClass()` | `bsh.Primitive.class` | `int.class` |
| `i.getType()` | `int.class` | `int.class` |
| `i.getValue()` | `1` | Method-not-found error |

Use `i` directly instead of `i.getValue()`. To ask whether its exposed type is
primitive, use `i.getClass().isPrimitive()`. `getType()` remains available on
primitive values. Java objects do not all have a `getType()` method.

On main, `i instanceof Number` can replace `isNumber()` for numeric values.
Characters are not instances of `Number`. To get a `Number`, assign or cast the
value to that type instead of the `numberValue()` call:

```java
int i = 1;
Number value = i;
value.intValue(); // 1
```

These replacements apply to main. The checked 2.0b4 and 2.0b6 releases return
false for `int i = 1; i instanceof Number`. Both releases reject the assignment
above.

The [Primitive 3.0 discussion (#687)](https://github.com/beanshell/beanshell/issues/687)
contains the original compatibility analysis and details of numeric convenience
methods. The Java implementation still uses `bsh.Primitive` internally.

## Java helpers used below

The varargs changes apply to calls to **compiled Java methods and constructors**.
With this helper, you can check overload selection, reference identity, and
property access. Save the following code in `MigrationExamples.java`:

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
null element, and an empty array are different values. Before #797, main
packed a single null argument into a one-element array regardless of its
declared type. The fix (merged 2026-09-11) uses the declared argument type, if
available, to select the correct form.

For the Java helpers above:

| Call | Before #797 | Main |
| --- | --- | --- |
| `MigrationExamples.arguments(null)` | `[null]` | `null array` |
| `MigrationExamples.arguments((Object[]) null)` | `[null]` | `null array` |
| `MigrationExamples.arguments((Object) null)` | `[null]` | `[null]` |
| `MigrationExamples.arguments()` | `[]` | `[]` |
| `MigrationExamples.arguments(new Object[] {null})` | `[null]` | `[null]` |
| `MigrationExamples.integers(null)` | `[0]` | `null array` |

To pass one null element for `Object...`, use `(Object) null` or an explicit
`new Object[] {null}`. To pass an empty array, call the method with no varargs
arguments or use an empty array. If you need one zero element for `int...`, use
`new int[] {0}`. A null array is not a zero element.

These rules also apply to null values from typed variables:

```java
Object[] array = null;
Object element = null;
MigrationExamples.arguments(array);   // "null array"
MigrationExamples.arguments(element); // "[null]"
```

BeanShell also keeps the declared type of null arguments from casts, fields,
array elements, and method results. This affects overload selection. Bare null
selects `pick(Object...)` instead of `pick(Object)` because the array
parameter is more specific. Use `(Object) null` to select the scalar overload.

| Call | Before #797 | Main |
| --- | --- | --- |
| `MigrationExamples.pick(null)` | `Object` | `Object[]` |
| `MigrationExamples.pick((Object[]) null)` | `Object` | `Object[]` |
| `MigrationExamples.pick((Object) null)` | `Object` | `Object` |

Constructor calls follow the same rule. `new MigrationExamples(null).values`
is a null array, but `new MigrationExamples((Object) null).values` contains
one null element.

This fix does not switch all dispatch to Java's declared-type rules. BeanShell
still uses runtime types for non-null arguments. This also applies to non-null
arrays in `Object` variables. BeanShell still prefers String for ambiguous
bare-null calls. If an expression gives no declared type for null, BeanShell
keeps its untyped-null behavior. This change does not include script-defined
varargs packing or generated constructor delegation.

## Object arrays passed to typed Java varargs

Before #822 (merged 2026-09-17), passing an `Object[]` for a Java method's
typed varargs parameter tried to cast the whole array to the declared
component type, rather than converting each element. An `Object[]` is not a
`Class[]`, even when every element is a `Class`, so the call below used to
throw a `ClassCastException`:

```java
class Sample { void run(String s, int i) {} }
Object[] types = new Object[] { String.class, Integer.TYPE };
m = Sample.class.getDeclaredMethod("run", types);
print(m); // public void Sample.run(java.lang.String,int) :Method
```

The fix converts each element with BeanShell's usual coercion rules and builds
the declared array type, so you no longer need to build a correctly typed
array yourself before making a call like this. Already typed arrays, null
arrays, and expanded varargs (`f(a, b, c)`) keep their existing behavior, and
method overload selection is unchanged.
[#822](https://github.com/beanshell/beanshell/pull/822)

## Float arithmetic and numeric overloads

Float arithmetic follows Java promotion and rounding rules. For Java numeric
primitives, these rules apply to `+`, `-`, `*`, `/`, and `%`. With a `float`
operand, BeanShell uses `float` arithmetic for byte, short, char, int, long,
and float operands. With a `double` operand, BeanShell uses `double`
arithmetic. #796 (merged 2026-09-11) changed these rules from the earlier
double-first calculation and float-overflow widening described in
[#71](https://github.com/beanshell/beanshell/issues/71):

| Expression | Before #796 | Main |
| --- | --- | --- |
| `(1f * 2f).getClass()` | `double.class` | `float.class` |
| `16777216f + 1` | `16777217.0` | `16777216.0` |
| `Float.MAX_VALUE * 2f` | Finite double | Float positive infinity |
| `1f + 2d` | Double `3.0` | Double `3.0` |

Promotion happens **before** the calculation. If you assign the result to a
double afterward, you cannot recover precision lost during the float operation.
If you need a double calculation, widen an operand before the calculation:

```java
float value = 16777216f;
double rounded = value + 1;        // 16777216.0
double widened = (double)value + 1; // 16777217.0
```

Result types can select a different overload:

```java
choose(float value) { return "float"; }
choose(double value) { return "double"; }
choose(1f * 2f); // "float"
```

Compound assignments first calculate with the promoted operand types. They then
convert the result back to the variable's type. For example,
`floatValue /= integerValue` rounds as a float operation.

With `longValue += floatValue`, BeanShell converts the long to float before the
addition. It then converts the result back to long. That can lose integer
precision. If you need more precision or range than float gives, use double
operands or explicit big-number arithmetic.

Float results keep Java's signed zero, NaN, infinity, and gradual-underflow
behavior. A float overflow no longer triggers a wider finite result. The fix
does not change double arithmetic, explicit `BigInteger`/`BigDecimal` promotion,
or the `**` power extension. The float table does not specify their rules.
See [#796](https://github.com/beanshell/beanshell/pull/796) for the
Java comparisons and the relationship to #767/#768.

## Scalar and array overloads

Method applicability distinguishes complete types. The complete type includes
array rank. A scalar string is not a `String[]`. BeanShell uses the complete
types to select an overload, so overload declaration order doesn't push the
scalar call below into the array method:

```java
pick(value) { return value; }
pick(String[] values) { return pick(values[1]); }
pick("a"); // "a"
```

If you need the array overload, pass `new String[] {"a", "b"}`. An overload with a
`String[]` parameter receives a one-dimensional array. An overload with a
`String[][]` parameter receives a two-dimensional array. If your code relied on
accidental scalar-to-array selection, fixed by #793 (merged 2026-09-11),
construct the intended array explicitly instead.

Parameter brackets after the name count too. `String values[]` means `String[]`,
and `String[] values[]` means `String[][]`:

```java
first(String[] values[]) { return values[0][0]; }
first(new String[][] {{"a"}}); // "a"
```

Main already parsed these forms before #793; the fix applies the combined
dimensions consistently to overload selection and generated Java signatures.
You can write the parameters as `String[] values` or `String[][] values` to
make the dimensions clearer. The changelog explains the effect on Java
reflection.

## Method calls and property aliases

BeanShell selects an applicable real method before a JavaBean property alias
of the same name. Before #798 (merged 2026-09-11), the two shared one overload
list, so reflection order could make a call such as `up()` invoke `isUp()`
instead, and an inherited cache could keep the accessor in place of the real
method.

With the Java `Switch` helper above:

```java
device = new MigrationExamples.Switch();
device.up();
device.isUp(); // true: the real up() changed the state
```

To call the getter, use `device.isUp()`. Property reads and writes keep their
existing accessors and field precedence. `device.up` can still read the property.
`device.up()` makes a method call.

Real-method precedence applies even if the alias has a more-specific parameter:

```java
label = new MigrationExamples.Label();
label.title("x"); // "method": selects title(Object), not setTitle(String)
```

To invoke the setter explicitly, call `label.setTitle("x")`. If no real method
with that name is applicable, the property alias remains available:

```java
getter = new MigrationExamples.GetterOnly();
getter.up(); // true; falls back to isUp()
```

An exception from a selected real method does not trigger alias fallback. Static
imports also give priority to static methods with the requested name. They use a
property alias only if no applicable real method exists.
[#798](https://github.com/beanshell/beanshell/pull/798) keeps property naming rules.
It does not introduce a general redesign of JavaBean setter selection.

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
change to a captured variable is visible to the lambda. That includes a loop
variable: BeanShell doesn't give each iteration its own copy.

```java
l = new java.util.ArrayList();
for (String s : new String[] {"a", "b", "c"})
    l.add((java.util.function.Supplier) () -> s);
// every element of l returns "c", not "a", "b", "c"
```

A `bsh.This` proxy and an anonymous inner class capturing the same loop
variable behave the same way. This isn't new to lambdas; it's just newly
visible through them.

Where several overloads accept a lambda argument, BeanShell picks by the
body's statically known result and shape, using the same declaration-order
tie-break it already uses everywhere else, rather than reporting the call
ambiguous the way javac sometimes would. See the lambda entry in
[CHANGES.md](CHANGES.md) for the complete list of resolution rules and
rejected interface shapes.

## Workarounds you can remove

These fixes correct operations that used to fail. If you added a workaround
for either, you can remove it now.

- **Numeric reference casts (#792):** `(Number) Double.valueOf(1)` used to
  throw a `ClassCastException`. Now an assignable reference cast or assignment
  keeps the original numeric object. You no longer need to rebox or convert
  through another number type only to prevent that failure. This fix applies
  to reference casts. It does not change narrowing primitive conversions.
  [#792](https://github.com/beanshell/beanshell/pull/792)
- **Boxed unary operators (#795):** numeric and character wrappers support the
  applicable unary operators without manual unboxing. Increment and decrement
  keep stored values and prefix results boxed. Postfix returns the original
  boxed value. Other unary results follow primitive promotion. BeanShell also
  applies its existing arithmetic support to raw `BigInteger` and `BigDecimal`
  values.
  Unsupported operator/type combinations still fail.
  [#795](https://github.com/beanshell/beanshell/pull/795)

```java
Double original = Double.valueOf(1);
Number reference = (Number) original;
MigrationExamples.same(original, reference); // true
```

```java
Integer boxed = Integer.valueOf(5);
Integer before = boxed++;
before; // 5; boxed now holds Integer 6
```

This guide covers the linked changes, not every difference between historical
BeanShell releases. Test representative application scripts against the exact
candidate version, especially where results choose overloads or values cross
Java/BeanShell boundaries.
