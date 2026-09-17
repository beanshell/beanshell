# BeanShell changelog


## 3.0.0 (in progress)

Work has resumed on the long-dormant 3.0 development line (`master`; the JAR targets Java 8 and is tested on Java 8 through 25) after a multi-year gap. This entry will grow as the release is prepared; changes so far:

BeanShell could fail to start, or silently report the wrong version, when another JAR earlier on a shared classpath also provided a root-level `version.properties` (#736, #783). BeanShell's own version metadata is now packaged and loaded from a namespaced `bsh/version.properties` resource instead.

Unary operators (`++`, `--`, unary `+`/`-`, `~`) previously rejected boxed numeric and character wrapper types (`Byte`, `Short`, `Character`, `Integer`, `Long`, `Float`, `Double`, `BigInteger`, `BigDecimal`), throwing `EvalError` for code as simple as `Integer i = new Integer(0); ++i;` (#762). These now behave consistently with primitives, including correct boxed results for increment/decrement.

Fixed a method-lookup regression from BeanShell 2.0b5 where overload resolution mismatched array-typed parameters against scalar arguments in some declaration orders (#731), plus follow-on gaps in split array dimensions (`String[] values[]`) and generated JVM method/constructor descriptors for array parameters.

Fixed numeric reference casts such as `(Number) Double.valueOf(1)`, which previously threw `ClassCastException`, while preserving the original object identity across casts and assignments (#725).

Fixed a parser input-buffer defect where long string literals, identifiers, or comments could corrupt the parser's buffer position when a token crossed a buffer growth or wraparound boundary, causing `ArrayIndexOutOfBoundsException`, parse errors, or incorrect token contents (#734, #743). The fix comes from upgrading the parser generator (`ph-javacc-maven-plugin` 5.0.2, ParserGeneratorCC 2.0.3), so building BeanShell from source now requires JDK 17 or newer; the resulting JAR still targets Java 8.

Float arithmetic now follows Java's numeric promotion (#767). `+`, `-`, `*`, `/` and `%` on `float` operands, including `float` mixed with `byte`, `short`, `char`, `int` or `long`, are computed in `float` and return `Float` instead of being widened to `double`. This deliberately changes the behavior documented in #71: a float result that overflows is now `Infinity` rather than a larger `Double` (for example `Float.MAX_VALUE * 2`), and compound assignments such as `long += float` round through `float` as compiled Java does. Arithmetic involving `double`, `BigInteger` or `BigDecimal` is unchanged.

Fixed intermittent wrong-variable lookups in nested blocks and loops (#659). An internal cache treated any two keys with the same hash code as the same entry, so a block could occasionally be given another block's namespace; the class-member cache had the same flaw. The cache now compares keys exactly.

When a class declares a real method whose name matches one of its property accessors, BeanShell could call the accessor instead: for example, calling `level(...)` could run `setLevel(...)`, and `up()` could run `isUp()` (#780). Real methods now take precedence, and a class that only defines `isUp()` can still be called as `up()`.

Updated the bundled ASM bytecode library to 9.10.1, still relocated under `bsh.org.objectweb.asm`. This fixes scripted classes with many overloaded constructors, which could fail with `Can't find default constructor` (#788).

Passing a bare `null` to a Java varargs method or constructor now passes a null array, as compiled Java does, instead of wrapping it in a one-element array (#778). More generally, a bare `null` argument now matches array-typed overloads: `String.valueOf(null)` and `Arrays.asList(null)` now throw `NullPointerException`, as in Java, where they previously returned `"null"` and `[null]`. `(Object) null` still passes a single null element.

An error that escapes a `try` block uncaught, whether through `finally` or because no `catch` matches, is now reported at the line where it occurred instead of at the `try` statement (#726).

Fixed three defects in the `desktop()` command reported by gary_nunes (#416): the taskbar button listener used its frame before checking it for null, so an action from a button with no registered frame threw a `NullPointerException`; the desktop's context menu opened on any mouse press, and now opens only on the platform's popup trigger, checked on both press and release as `MouseEvent.isPopupTrigger()` requires for cross-platform behavior; and closing the desktop window, or choosing "Exit" from its menu, called `exit()` and terminated the whole JVM. It now disposes and unsets `bsh.system.desktop` instead, so an embedding application can close and reopen the desktop without exiting.

Calling an overloaded method of a scripted class or enum with a variable that is declared as a reference type but holds `null` now selects the overload for the declared type, as compiled Java does (#150): `Object o = null; x.test(o)` calls `test(Object)` rather than `test(Integer)`. Java code that calls a specific method of a generated class, such as `test(Object)`, now runs that scripted overload for any argument; previously the argument's runtime type could re-select `test(Integer)`. In script code, overload selection for non-null values is unchanged.

Tab completion in the Swing console (`JConsole`) no longer corrupts the command line when several completions are shown (#292). With Windows line endings the command boundary drifted into the prompt, so pressing Enter sent part of the prompt to the interpreter, and a prompt on the first line was reprinted without its first character.

Pressing Enter after pasting a large block of code into the Swing console (`JConsole`) no longer freezes it (#585). The command was written into the interpreter's input pipe on the Swing event thread; once the 64 KB pipe filled, the event thread waited for the interpreter to read more, while the interpreter waited for the event thread to print its output. Commands are now written to the pipe on a separate thread, in order.

`JConsole.addHistory(String)` adds a line to the Swing console's command history, so applications can preload commands that the user recalls with the up arrow (#405).

Fixed scripted classes whose subclass has the same simple name as its superclass, for example `a.b.A extends a.A`: calling an inherited method threw `NullPointerException` because the superclass's instance state was never initialized (#383). Also, a non-varargs Java method with only one overload is no longer selected for a call with more arguments than it accepts. Such a call now reports the method as not found instead of throwing `ArrayIndexOutOfBoundsException`, so a scripted class method such as `print()` no longer hides the `print` command when called with an argument.

Scripts evaluated from deep call stacks no longer slow down in proportion to the stack depth (#551). Reaching the end of each evaluated script created exceptions whose stack traces grew with the caller's stack; the interpreter now signals end of input to the parser with a reused exception that carries no stack trace. In a benchmark of 5,000 `eval("'abc'+'def'")` calls at a stack depth of 1,000 on Java 8, the time dropped from about 115 ms to 17 ms, the same as at depth 0.

Fixed a parse error when a method-call argument, wrapped in extra parentheses, contained an anonymous class declaring a method with a class return type, for example `print(((String) new Supplier() { public Object get() { return "2"; }}.get()));` (#423).

Extended the null-varargs fix (#778) to three more places a null argument's declared type was lost, causing the same wrong-array-vs-wrong-element dispatch: an assignment expression used directly as a call argument (`Target.objects(object = null)`), a bean property getter accessed with dot or brace syntax (`bean.foo`, `bean{"foo"}`), and a ternary expression (`cond ? object : object`). Also fixed anonymous-class construction, which lost the same type information because the generated constructor's `super()` call is static per-argument-slot bytecode with no vararg-wrapping logic of its own: `new Target(object) {}` now matches `new Target(object)`.

Restored weak and soft key behavior in the class-member and block-namespace caches (#659). Their replacement kept cache keys in a plain `HashMap`, so a key (and, transitively, the `NameSpace` or `Class` it held onto) stayed reachable until its value happened to be garbage collected and the cache's periodic cleanup ran, regardless of whether anything outside the cache still referenced it.

Packaged the ASM library's BSD-3-Clause license notice into the binary JAR at `META-INF/licenses/ASM-LICENSE.txt` (#788); it previously shipped with the vendored, relocated ASM classes but not their license.

Fixed a `ThreadLocal` state leak in `Interpreter.DEBUG` (#785): debug mode enabled on one `Interpreter` could remain enabled for later, unrelated `Interpreter` instances that happen to run on the same pooled thread. A new top-level `Interpreter` now starts with a clean debug state, unless it is constructed while that thread is already evaluating, in which case it belongs to the evaluation in progress and leaves the flag as it is; debug mode toggled with the `debug()` command still persists normally across statements and sourced/child evaluations within the same interpreter's session. Reading the `debug` system property is also guarded, so constructing an `Interpreter` under a `SecurityManager` that denies it no longer throws.

Added `Interpreter.setOutputFile(String)` to redirect a specific interpreter's own output and error streams to a file without touching `System.out`/`System.err` (#516). The existing static `Interpreter.redirectOutputToFile(String)`, despite its name, always redirected the JVM-wide system streams rather than any particular interpreter; it is now deprecated in favor of the new instance method.

Fixed an inner class that extends its own enclosing class resolving to the wrong (or, on a first definition, no) superclass (#698): `class A { class B extends A {} }` generated `B` before `A` itself was defined, so `B`'s superclass baked in whatever `A` previously existed, if any. That specific inner class is now generated only after its enclosing class is fully defined; other inner classes are unaffected and keep generating first, as before.

`bsh.SimpleNode`, the base implementation of the public `bsh.Node` AST interface, is now a public class (#730), so embedding applications can name and cast to it (for example `instanceof SimpleNode`) when walking a parsed script's syntax tree.

The result of a void method call may now be assigned to an untyped or `Object`-declared variable, for example `x = voidMethod();` or `Object x = voidMethod();` (#775), instead of throwing `illegal void assignment` / `Void initializer.`. Assigning to any other declared type, any compound assignment (`x += voidMethod()`), and a bare or dotted reference to an undefined name or property (`x = undefinedVar;`, `x = obj.noSuchProperty;`) are all unchanged and still errors.

Removed the built-in HTTP server (`server()` command, `bsh.util.Httpd`, `bsh.util.Sessiond`), the `bsh.servlet` package, and the remote console applets (#496). None of it was authenticated: `server()` opened an unauthenticated `bsh` session on the network, and the servlet evaluated a script passed in as a request parameter. Local, non-networked applet demos (`JDemoApplet`, `AWTDemoApplet`) are unaffected.

A class name that failed to resolve was remembered as not being a class, so adding the archive that contains it with `addClassPath()` afterwards still reported it not found (#811). The negative lookup cache is now discarded whenever a class path change installs a new class loader. The positive and member caches are left alone: a new path component can turn "no such class" into a class, never the reverse, and the member cache is shared by every interpreter in the JVM.

A directory added to the class path now also contributes the archives it holds (#646), so `addClassPath("file:/path/lib/")` picks up the JARs in `lib/` the way `java -cp 'lib/*'` does. Nested directories are not searched. Only a class path a script adds is expanded this way; the class path the host JVM was started with is left as it is, so BeanShell does not resolve classes that JVM itself cannot load. The expanded path keeps its order, which decides which of two archives declaring the same class wins, and a directory whose URL is percent-encoded is expanded like any other.

A field declared with the type of the class being generated, such as `class Node { Node next; }`, was silently dropped from that class (#813); its type could not be resolved while the class was still being defined, and the resulting error was swallowed. Assigning to the field then failed with `LHS (next) can't access field`. Such a field is now described by its own class's descriptor instead of by a name lookup, so it also cannot be captured by an unrelated imported class of the same name. Relatedly, `values()` on a scripted enum selected fields by their type, so a field declared with the enum's own type was returned among the constants; only real enum constants are now returned.

Three constructs that compiled Java accepts were rejected by the parser and now parse (#814). Annotations are accepted on class, method, field and parameter declarations, including qualified names, named pairs and nested annotations; they are parsed and discarded, so nothing is retained on the generated class and none of them are visible through reflection. A method declarator may carry its array brackets C-style, `int f()[] { ... }`, which add to the dimensions of the return type and precede any `throws` clause. And a class literal may be parenthesized, `(String[].class)`, `(int[].class)`, where it previously parsed as an attempted cast. The `@word` operators (`@or`, `@and`, `@pow` and the rest) still lex as operators with or without surrounding space.

The `exec()` command now runs its command through `ProcessBuilder` (#683). It prints what the application writes to standard error as well as its standard output (#273), and it waits for the application to exit rather than reading an exit status from a process that may still be running, so the value it returns is the real one. A `String[]` form was added, because the single-`String` form splits the command on whitespace and cannot express an argument that contains spaces. A script interrupted while waiting keeps its interrupt flag, and the output reader and the child process are released on every exit path.

An anonymous subclass of a class with a varargs constructor now passes the trailing arguments as the declared array type. `new Target("a", "b") {}` against `Target(String... parts)` built an `Object[]` and so matched no constructor at all. The elements also go through the same coercion an ordinary call applies, instead of reaching the constructor as BeanShell's internal primitive wrappers.

An anonymous subclass of a Java class whose constructor takes two or more parameters of different declared types failed with `Typed variable: null was previously declared with type: int`. The wrapper BeanShell builds for a Java super constructor supplied an array of null parameter names, so every parameter was declared into one namespace under the same null name and the second collided with the first. Constructors whose parameters all share one declared type were unaffected. Names are now synthesized per position. They also stay usable identifiers past the twenty-sixth parameter, where counting on through the character set used to reach `~`, `DEL` and the unprintable controls, which then appeared in the method signatures BeanShell prints in error messages.

Four caches shared by every interpreter in the JVM were read and updated without synchronization: the compound-name parts cache, the property accessor name cache, the cache of default instances used to read a class's declared members, and the class-existence cache behind `Capabilities`. Concurrent updates could splice one of their bucket chains into a cycle, after which a lookup spun at full CPU and never returned, with no exception and no timeout to break it. Every access is now guarded, with class loading and construction kept outside the lock; the accessor-name cache was removed outright rather than locked, since it saved only a single character conversion. The default instance cache had two further defects, fixed with them: callers that race now receive the same instance, where each could previously get its own and the separate namespace it carries, and a construction that succeeds is no longer discarded because another caller's attempt failed first. A failed class lookup likewise no longer replaces a class another caller has already resolved. The parts cache now holds its entries by reference, so the compound names a script resolves are no longer retained for the life of the JVM. Anonymous class names are minted with an atomic counter, so two threads generating anonymous classes at once cannot produce the same generated name.

`Name.countParts`, `Name.isCompound`, `Name.prefix` and `Name.suffix` no longer throw `ArrayIndexOutOfBoundsException` for a name made of nothing but dots. Such a name now has no parts at all: `countParts(".")` returns 0, `isCompound(".")` is false, and `prefix` and `suffix` return null. The parser does not accept a name of that shape, so this reaches only embedding applications that call these methods directly.

The parser is now generated by JavaCC 8.1.0 through `org.javacc.plugin:javacc-maven-plugin` 3.8.0 instead of ParserGeneratorCC, the JavaCC 7 fork adopted for #734. JavaCC 8 runs on Java 8, so building from source needs JDK 8 or newer again; the grammar, the syntax tree and the error messages the test suite checks are unchanged, and the generated parser no longer allocates a lookahead sentinel per instance. Two generated types visible to embedders change with the generator: a lexical error is once more `bsh.TokenMgrError`, an `Error` as in BeanShell 2.x, rather than ParserGeneratorCC's `bsh.TokenMgrException` (a `RuntimeException`), and `bsh.ParseException`'s generated constructor takes the two additional arguments JavaCC 8 passes. `bsh.Token` is kept in the source tree so tokens stay serializable with the `serialVersionUID` they always had, and objects saved by earlier builds still load. The end-of-input cost left over from #551 is gone for every `Parser`, not only the one inside `Interpreter`: `bsh.JavaCharStream` is now kept in the source tree and signals end of input with one reused exception that carries no stack trace, leaving the reader open for its owner, so the `StacklessEofReader` wrapper has been removed.

An out-of-bounds array or `List` index store, including the compound forms `arr[i] += x`, `arr[i]++` and `++arr[i]`, previously escaped as a plain `EvalError`, so no script `catch` block could see it, not even `catch (Throwable)`; an out-of-bounds *read* was already catchable. Both now surface as ordinary target exceptions (`ArrayIndexOutOfBoundsException`/`IndexOutOfBoundsException`), consistent with reads. A script with a broad `catch (Exception)` around such code, which previously ran to completion after the failed store, will now enter the catch block.

Added lambda expressions: `x -> ...`, `(a, b) -> ...` and `(Type a) -> ...`, with an expression or a block body, usable anywhere Java accepts one, including as an argument to a Java method with no cast (#675); method references (`Foo::bar`) are not included. A lambda captures its declaring scope by reference rather than by effectively-final value, so a later change to a captured variable is visible to the lambda, including a loop variable, which BeanShell does not refresh per iteration the way Java does, so `for (String s : list) list2.add(() -> s)` gives every lambda the loop's last value. Its value is opaque until converted to a functional interface, by cast, assignment, or overload resolution, and until then it can sit in an untyped or `Object` variable. An unchecked exception, or a checked exception the target interface's method declares, reaches the caller as itself; any other checked exception, or a script error, comes out as `bsh.RuntimeEvalError` (a `bsh.This` proxy for a scripted interface instead throws `UndeclaredThrowableException` for an undeclared checked exception, since it runs through `java.lang.reflect.Proxy`). The body's result is checked against the target interface only when it is statically evident — a literal, a cast, a declared parameter read back, or an operator over those — so most mismatches surface only when the lambda runs, as `RuntimeEvalError`, not when it converts, and a raw generic target such as `Supplier` can't be checked at all, so a Java caller expecting `Supplier<String>` may still see `ClassCastException`. A lambda converted to a `Serializable` interface serializes; deserializing it throws rather than running, the same rule `bsh.This` proxies already follow, because deserialization has to initialize the named interface fresh. When several overloads accept a lambda argument, BeanShell picks by the body's statically known result and shape, deterministically and never by declaration order, in some cases where Java would report the call ambiguous, and, for a body whose result BeanShell cannot statically know, sometimes differently from javac's own single answer; a `void` method-call body is accepted for a value-returning target and yields `null` for a reference-returning one, and fails at call time for a primitive-returning one. `x -> {1,2,3}` needs parentheses around the body. Wrapper classes are generated per functional interface in their own class loader, independent of `bsh.classpath`, and a `SecurityGuard` may veto the interface through `canImplements`. The wrapper generator and its test suite are ported from Net-0's PR #766.

## 2.1.1

Fix src/bsh/util/AWTConsole.java breakage with newer Java versions

## 2.1.0

This release formalizes the merge of 2.0b6 with suitable backports from
the development (HEAD) version of BeanShell (3). Also included are
are some ALv2 contributions to the BeanShell2 fork that had not been
folded into BeanShell but are still applicable to this version. For
backwards compatibility purposes, the 2.x branch of BeanShell still
supports a minimum Java version of 1.6.

## 2.0 beta 6

### Security fix

This release fixes a remote code execution vulnerability that was identified in BeanShell by [Alvaro Muñoz](https://twitter.com/pwntester) and [Christian Schneider](https://twitter.com/cschneider4711). The BeanShell team would like to thank them for their help and contributions to this fix!

An application that includes BeanShell on the classpath may be vulnerable if another part of the application uses [Java serialization](https://docs.oracle.com/javase/tutorial/jndi/objects/serial.html) or [XStream](http://x-stream.github.io/) to deserialize data from an untrusted source.

A vulnerable application could be exploited for remote code execution, including executing arbitrary shell commands.

This update fixes the vulnerability in BeanShell, but it is worth noting that applications doing such deserialization might still be insecure through other libraries. It is recommended that application developers take further measures such as using a restricted class loader when deserializing. See notes on [Java serialization security](http://www.oracle.com/technetwork/java/seccodeguide-139067.html#8),  [XStream security](http://x-stream.github.io/security.html) and [How to secure deserialization from untrusted input without using encryption or sealing](http://www.ibm.com/developerworks/library/se-lookahead/).

A [CVE number](http://cve.mitre.org/cve/) will be requested.


## 2.0 beta 5

### Persistent Scriptable Classes! (Experimental Feature)

Using the system property `-DsaveClasses=<savedir>` you can instruct
BeanShell to write out persistent class files for class definitions found
in the script.  In this mode BeanShell will not execute code, but will
generate and store a class files for each class definition it finds.
These classes are the same as the ordinary runtime generated delegate
classes used internally by BeanShell but additionally have static
initializer code that allows them to bootstrap an instance of the BeanShell
interpreter to execute their scripted body if the class is used from
an external application.

Currently we are using a simple convention whereby a class `Foo.class`
expects to find its associated scripted class definition in a file
`Foo.bsh` in the same location as the class file.  Each stored class
must have a corresponding script definition.  The scripts itself may
contain any amount of BeanShell code beyond the class definition
and may refer to other scripted classes or saved classes.  However each
saved class will currently initialize its own interpreter instance.

So, for example, to create a class for the following scripted class
definition:

```java
    class Foo {
        Foo() { print("I'm being constructed!"); }
    }
```

you could place the script into a file `Foo.bsh` in the current directory
and execute:

```sh
    java -DsaveClasses=. bsh.Interpreter Foo.bsh
```

This will create the class `Foo.class`.  The Foo class may be used like any
ordinary Java class and invoked from ordinary Java code.  Upon being
loaded it will automatically create a BeanShell interpreter instance
to load and execute its corresponding `Foo.bsh` script file.  The script
is expected to correspond to the structure of the class: to initialize
static instance members and implement all static and instance methods
required by the class.  Beyond that, the script is free to change and
include additional script body outside the scripted class definition.

For example, we might later change the `Foo.bsh` script to the following:

```java
    welcomeMessage = "Hello World!";

    class Foo {
        Foo() { print( "I'm being constructed: "+ welcomeMessage ); }
    }
```

Here's another example showing loose code in the script:

```java
    //  Begin script Foo.bsh

    class Foo {
        public static void main( String [] args ) { }
    }

    message = "Hey";
    message += " you!";
    num = 2 * 2;
    message += " The answer is: "+num;

    // end script Foo.bsh
```

now we have a script that can be launched like any other Java class
via `java Foo`.  The loose script body is run upon class initialization.
Moving that text into the class body would cause it to be run on class
construction (instance creation... along with any constructor).


### Added JSR223 javax.script adapter engine implementation in a new bsh.engine package

This implementation of the spec supports pluggable ScriptContext.  Please
see the [TestBshScriptEngine.java](src/test/java/bsh/TestBshScriptEngine.java) file (and the spec) for a starting point
on how to use this.

BeanShell maintains all of its info on variables (type, modifiers,
etc.) and all of its internal state for a given ScriptContext via a
BeanShell NameSpace embedded in the context.  This  allows us to be
completely pluggable with respect to the spec, but still keep the full
interpreter state between evaluations.

### Added a new class, ExternalNameSpace to the distribution

This is a BeanShell namespace that knows how to externalize its variables
to a standard Map.  This class is, of necessity, dependent on JDK1.2+,
however there is no dependency in the core on this class, so you can
exclude it in the usual way.

- Fixed static import syntax.  It's now the same as Java 5:
    ```java
    import static Foo.*;
    ```
    (previously was `static import` ... )

### Static blocks in scripted classes are now working.

Added test case.


## 2.0 beta 4

- Source compiles under JDK 1.5

- Better defined classloading precedence:
    * in-script evaluated class (scripted class)
    * in-script added / modified classpath
    * optionally, external classloader
    * optionally, thread context classloader
    * plain `Class.forName()`
    * source class (.java file in classpath)

- Method selection code has been rewritten to move towards Java 5 rules.
This should correct a number of problems where inaccessible methods are
selected and also correct security problems in applets and other secure
environments.  Method selection should no longer call `getDeclaredMethods()`
unless accessibility is explicitly activated.  The same is true for
constructors and fields.

- Updated applet demos (http://beanshell.org/demo).  JConsole and desktop
now run in untrusted environment, with limitations.  Applets cannot
create classloaders, so BeanShell cannot script classes in the unsigned
environment.  These demos are very rough and need some work.

- The `show()` command now works in both interactive and non-interactive
modes.  This means that for experimentation, instead of doing things like:
  ```java
  print( myObject instanceof Blah );
  print( myObject.getFoo() );
  print( myObject.somethingElse() );
  ```
  .. now you can simply do:
  ```java
  show();
  myObject instanceof Blah;
  myObject.getFoo();
  myObject.somethingElse();
  ```
  This can be combined with the trace property `-Dtrace=true` (currently a
  static, interpreter wide flag) to display line by line execution and
  results.

- The `bsh.show` variable is gone and has been replaced with a property
on the interpreter.

- Fixed bug in `super.method()` resolution inside nested blocks.

- Added workaround for `load()` command to use the BshClassManager for
    deserializing classes.

- Fixed some cases where SecurityException could not be caught by scripts
  and handled.


## Changes in 2.0 beta 3

* String literals are now interned, as per JSL ch3 10.5.

## Changes in 2.0 beta 2

Added code to address the class redefinition problem.  BeanShell will now
use the bsh classloader to allow the classes to be reloaded as necessary.

Note: this only affects users who are utilizing the new BeanShell 2.0+
features to script classes and interfaces using the standard Java
class/interface syntax.  There is no problem with scripting interfaces
using bsh objects or the anonymous inner class style syntax that has always
been supported.


## Changes in 2.0 beta 1

With release 2.0 BeanShell supports the full Java syntax.
You can now script Java classes that serve as real Java types.
Scripted classes may extend ordinary Java classes, abstract classes, and
implement interfaces as usual.  Constructors and methods invoked on the
scripted classes are delegated to the BeanShell interpreter.  This
means that within scripted classes you may use the full power of
BeanShell scripting including commands, loose types, and other BeanShell
Java extensions such as method closures if you wish.

BeanShell will now attempt to load classes from ".java" source files in
the classpath if the compiled class is not found.

Current Limitations:

- In order to run a class file's main method with arguments from the
  command line the class containing your main method must be the last
  item in the file.  e.g. a .java file with the public class appearing
  last.

- Static final fields (constants) in interfaces are currently broken.

- Non-static inner classes are currently implemented in a non-standard
  way (using mix-ins) so mixing interpreted classes with their
  pre-compiled inner classes will not work properly in all cases.

- Inner classes are not properly "imported" in some cases. e.g. Class A
  contains one or more inner classes.  Class B extends class A and
  attempt to refer to those inner classes by their unqualified names.

- Static blocks in the class body are not yet implemented.

- If you have a source class at the wrong place in the classpath you
  may get a non-fatal error when bsh attempts to load the class again.


- JDK 1.5 style static class imports.
You can import the static methods and fields of a java Class into a
BeanShell namespace. e.g.

   ```java
   static import java.lang.Math.*;
   sqrt(4.0);
   ```

- Instance object imports (mix-ins) with the `importObject()` command.
You can import the methods and fields of a Java object instance into
a BeanShell namespace.  e.g.

  ```java
  Map map = new HashMap();
  importObject( map );
  put("foo", "bar");
  print( get("foo") ); // "bar"
  ```


## Changes in 1.3 beta 2

- Added reserved Java keywords.  Added: `strictfp`, `implements`,
  `extends`, `package`, and the new Java 1.5 keyword `enum`.  `strictfp`
  is now accepted as a modifier and ignored.  The rest are not supported
  (yet).

- Properties style auto-allocation of variables.
  ```java
  // foo is initially undefined
  foo.bar.gee = 42;
  print( foo.bar.gee ); // 42
  print( foo.bar ); //'this' reference (XThis) to Bsh object: auto: bar
  print( foo ); //'this' reference (XThis) to Bsh object: auto: foo
  ```

  When assigning to a compound name, undefined components being resolved
  relative to a scripted object (bsh `this` type reference) will be
  auto-allocated as scripted objects.

  This means that you can use BeanShell scripts just like properties
  files for initialization parameters.

- BeanShell `this` type references can now implement more than one
  interface.  Use `getInterface( Class[] )`.

- BshMethod `getReturnType()` now returns `Void.TYPE` for voids, rather than
  the (incorrect) wrapper value `Primitive.VOID`.

## Changes in 1.3 beta 1

- Class management Changes - class management is now on a per-Interpreter
    (per-global namespace) basis.  Multiple interpreter instances do not
    any longer by default share classpath or cached class information.
    (This will help in application server deployments).
    Added the method: Interpreter `getClassManager()`
    %% some minor incompatible changes in [NameSpace.java](src/main/java/bsh/NameSpace.java) api.
    The NameSpace constructor now requires a BshClassManager instance.

- Fixed bug where using `reloadClasses()` with no arguments (to reload
    the entire classpath) did not work properly when user classpath
    included components with relative paths.

- Scripts executed with the `run()` command (as opposed to `eval()` or
    `source()`) now have their own class manager and therefore classpath.
    Any namespace the is disconnected from its parent ( `pruned()` ) now
    creates a new class manager.  In the case of the `run()` command we
    inherit the old classpath explicitly.  This behavior **may evolve**
    to cover all cases of a namespace with no class manager or parent.
    **Note:** the test suite runs a bit slower now because each test is
    `run()` and starts with a clean class cache.  Note that creating
    variants on the `run()` behavior (e.g. the old behavior) is trivial...
    See `run.bsh`.

- The `bsh.system` object which is shared across Interpreter instances
    has been renamed `bsh.shared`.  For backwards compatibility it is
    still referenced as `bsh.system` as well.

- Implemented some experimental caching of class method resolution for
    performance.  When a method (overloaded or single) is resolved
    for a set of arguments, that method is cached based on the class,
    argument types, and method name.  In the test case of a loop simply
    calling a Java method on an object or class this results in about a
    40% speedup.

- Consolidated bsh scripted method dispatching code into [This.java](src/main/java/bsh/This.java).

- All scripted objects now implement the standard object protocol of
    `toString()`, `hashcode()` and `equals()`.  Scripted object behavior is now
    more consistent.
    %% One minor incompatible change: the `invoke()` meta-method no longer
    intercepts `toString()`, `hashcode()` or `equals()`.  If you wish to
    override these you must script them directly in the object.

- All normal error messages should now include line numbers and invocation
    text.  Removed the no context constructors for EvalError and
    TargetError.  Added checked parallel exceptions UtilEvalError and
    UtilTargetError for use in other areas.  Use UtilEvalError
    toEvalError( node, callstack ) to rethrow these as necessary.

- Added script stack trace to error messages (e.g. method a() called
    method b(), etc.).  EvalError and TargeError now require callstack.
    Added `getScriptStackTrace()` method to EvalError API.  `toString()`
    prints the script stack trace by default.

- Errors indicating bad return control or return type from method now
    point to the exactly location of the return.

- Removed deprecated get/setVariable methods...  optimized for common case
    in 1.2 branch so there should be no performance penalty for using
    set over setVariable.

- Remote client can now utilize bsh:// URLs to talk to embedded bsh server.

- Added "bsh" target to build.xml for running a script from the current
    build.  e.g. `ant -Dfile=foo.bsh bsh`

- Stopped squelching NoClassDefFound errors... This was the source of
much frustration where BeanShell would indicate that a class was not found
when it was really a dependent class that was missing.  We now catch
NoClassDefFoundError and add a message specifying which class was being
loaded when the missing class was encountered.

- Added catch block in `bshdoc.bsh` script to report the file it was
    processing when an error occurs.

- Fixed bug allowing `try { }` without `catch` or `finally`.

- Enhanced bsh shell script launcher to add a default bsh JAR if none
    is in the classpath (as determined by a simplistic name test).  Works
    on Cygwin and Unix.

- added `sourceRelative()` command, which sources the file relative to the
  caller's directory, not the working directory. (See the bshdoc for
  more explanation).

- Upgraded to JavaCC 3.0 available from:
  http://www.experimentalstuff.com/Technologies/JavaCC/index.html
  (Let's see, how many places have owned JavaCC now?  Sun Test, then
  Metamata, then WebGain, then Sun "experimental stuff"?  Sun has
  promised to open source this... I hope they follow through).
  Modified build.xml to use the new package name (no package) and to assume
  that the jar is in the classpath for now.

- Refactored and removed the ugly "LHS" part of the grammar.  This removed
  two ASTs which essentially duplicated the "straight" part of the grammar.
  There is still a bsh.LHS class (used as a wrapper for the target of an
  assignment expression) but it is now created based on context rather than
  via a special part of the grammar.  Perhaps no one but the author will
  care about this but this always bugged me.  This also fixes some corner
  cases in assignments that were broken (tests `fields1.bsh` `fields2.bsh`).

- Switched to BSF 2.3 package name by default.  BSF was donated from
  IBM to Jakarta and changed its package name with 2.3.  If you want to use
  an older release just grab the bsh-bsf-1.2x.jar and drop it in your
  classpath ahead of your bsh 1.3 dist.

- Enhanced array creation to support undefined array dimensions as in Java.
  e.g. `int [][][] foo = new int [3][][];`

- Enhanced array creation to support auto-casting in array initializers,
  e.g.  even though a primitive number is an int by default, the following
  work (as in Java):
  ```java
  long [] la = { 1, 2 };  float [] fa = { 1, 2 }; byte [] ba = { 1, 2 };
  ```
- Fixed import caching bugs.

- Fixed problems with the method resolution caching: added hashcode to
  [Primitive.java](src/main/java/bsh/Primitive.java), fixed null argument issue, separated caches into object
  and static, removed side-effect from resolveJavaMethod in Reflect.  Did
  some initial profiling.  Overall I'm seeing about a 50% speedup in code
  that does primarily method dispatching.

- Improved BshServlet - EvalErrors (and ParseExceptions) no longer spew the
  whole script again in the error message.  Inline evals now report the
  "file" name as an abbreviated portion of the script.  Errors in the
  servlet results show the offending line highlighted (html) red with four
  lines of context and line numbers.  Tweaked the template html just a bit.

- Removed deprecated overloaded `NameSpace.getImportedClasses()`

- Moved `Name.ClassIdentifier` to `bsh.ClassIdentifier` top level class.
  `ClassIdentifier` allows scripted methods to accept plain class names as
  arguments.  e.g.  `javap( java.lang.String )`;

- Enhanced error message for undefined argument to method - now prints the
  argument text instead of the method parameter name and position.

- Clarified the scoping of closures and blocks and fixed related bugs.
  This should now work as expected.  A `this` reference always refers to
  the nearest enclosing method or the root scope.  This is true even when
  the expression returning or assigning the `this` reference is inside one
  or more nested Java blocks. e.g.
    ```java
    myMethod() {
        if ( true ) { /* inside block */ } else { /* */ }
        { /* gratuitous block */ }
    }
    ```

  Returning or assigning `this` from inside `myMethod()` or inside any of
  the blocks all use the same `this` reference (myMethod).  You may
  refer to local, typed variables declared in the block `this.varname` as
  expected, but they are not visible outside of the block (as in Java).
  See `tests/this.bsh`.

  Methods defined inside a block are now set in the parent block, as
  is consistent now with everything else blocks do.

- Enhanced error message on missing or incorrect constructor.

- Switch statements should now work with all types.  Previously numeric
  switch statements only worked on the type int.  Now any numeric type or
  wrapper will serve as the switch value and auto-boxing/unboxing occur as
  elsewhere in BeanShell.  Switch statements also work with object types -
  e.g. you can switch on Strings, Dates, or any objects that test properly
  with `equals()`.  The only comparison that is not allowed is primitive to
  arbitrary (non wrapper) object type.  e.g. compare 2 to a "two".  This
  will cause a primitive / object mismatch error.

- Did some minor refactoring and cleanup in [Reflect.java](src/main/java/bsh/Reflect.java).

- Added enhanced for loop as per JDK 1.5.  (Belated thanks to Daniel Leuck
  for submitting his "each" statement long, long ago.)
  So you can now do things like:
    ```java
    List foo = getSomeList();

    for ( untypedElement : foo )
        print( untypedElement );

    for ( Object typedElement: foo )
        print( typedElement );

    int [] array = new int [] { 1, 2, 3 };

    for( i : array )
        print(i);

    for( char c : "a string" )
        print( c );
    ```
  Supported iterable types include all the obvious things.  See the new
  BshIterator API addition below for specifics.

- Added BshIterator interface and bsh CollectionManager.  BeanShell now
  supports a unified API for iteration over composite types including:

   JDK 1.1+ (no collections): Enumeration, arrays, Vector, String,
   StringBuffer
   JDK 1.2+ (w/collections): Collections, Iterator

  This was added to support the enhanced for loop above, but can also be
  used in BeanShell commands that wish to loop over composite objects to
  display them.  Usage e.g.:
    ```java
    cm = CollectionManager.getCollectionManager();
    if ( cm.isBshIterable( myObject ) )
    {
        BshIterator iterator = cm.getBshIterator( myObject );
        while ( iterator.hasNext() )
            i = iterator.next();
    }
    ```
  If this proves to be useful we could add additional syntactic magic
  later such as making all composite types implement the magic interface
  BshIterable, etc.

- Maps can now be used with the hashtable style accessor syntax.  e.g.
    ```java
    Map map = new HashMap();
    map{"foo"} = "bar":
    ```
  This is another feature of the new CollectionManager (1.1 compatibility
  is preserved).

- Fixed void initializer bug whereby a void value in an initializer was
  allowed.

- Refactored variable management a bit - all variables (both typed and
  untyped) are now wrapped in the wrapper `NameSpace.Variable`.  We could put
  a listener API on there if we wanted to "watch" variables.

- Implemented BeanShell 1.3 scoping changes and preserved backwards
  compatibility with the `-Doldscoping=true` switch.

- Variable assignments now search up the parent chain for the variable
  definition rather than defaulting to local scope.  Variable assignments
  will therefore find the nearest enclosing variable definition - as one
  would expect in Java.  Prior to BeanShell 1.3 this was only true when
  "strict Java" mode was enabled.

  So, for example, the following idiomatic things now work as expected:
    ```java
    incrementX() { x=x+1; }
    x=1;
    incrementX();
    assert( x == 2 ); // true!

    setFlag() { flag=true; }
    setFlag();
    assert( flag == true ); // true!
    ```
 - A typed variable declaration will define the variable as usual in Java.
  An untyped variable assignment - for which there is no definition in any
  enclosing scope - will end up as a global variable assignment. e.g.
    ```java
    int x = 1; // parent scope

    foo()
    {
        // foo scope
        x = 2; // x assigned in parent scope

        int y = 1;
        y = 2; // y assigned in foo scope

        z = 99; // z has no enclosing scope, assigned global
    }
    ```
 - An untyped variable can be made local in scope by assigning it with
  the qualifier `this` (as usual) or by using the new keyword `var` to
  explicitly declare it as an untyped variable.
    ```java
    foo()
    {
        // secret1 and secret2 are local to foo()
        this.secret1 = 42;
        var secret2 = 42;
    }
    ```
 - The qualifier `super` may be used, as in Java, to begin the search for a
  variable definition in the parent scope.
    ```java
    int y = 1; // parent scope
    foo()
    {
        int y = 2;
        super.y = 3; // y assigned in parent scope
    }
    ```
 - Object closures (scripted objects created by returning `this` from a
   method) may be assigned variables as usual:
    ```java
    foo() { return this; }
    foo=foo();
    foo.a=1;
    print( foo.a ); // 1
    print( a ); // ERROR 'a' undefined here
    ```
 - The old scoping rules have been preserved and can be switched on by
  setting the system property `oldscoping` to `true`.
  See `newscoping.bsh` in the test suite for more examples.
  Note: There is exactly *one* line of code in [NameSpace.java](src/main/java/bsh/NameSpace.java) that looks
  at the `Interpreter.LOCALSCOPING` flag to determine what to do.
  Everything else happens naturally.  So there should be no fear that we
  are accumulating legacy baggage.  On the contrary, BeanShell refactoring
  is making it more powerful and simpler.

  - Fixed bug in evaluation of operator-assignments with postfix in the
    RHS.  (Java behavior seems strange to me.) e.g.
    ```java
    i=1; i+=i++; // should be 2 apparently
    i=1; i+=i++ + i++; // should be 4 apparently
    ```
  - BeanShell now supports modifiers (e.g. public, private, static, etc.)
    on methods and typed variable declarations.  Basic sanity checks are
    done at parse time (e.g. you can't apply volatile to a method, mix
    public/private, etc.)  "final" is implemented for typed variables but
    other modifiers are currently ignored.

  - The "synchronized" modifier is now implemented for methods and
    synchronized blocks.  Synchronized blocks work as in Java.
    Synchronized methods lock their parent namespace's This reference, so
    for purposes of serialization (executing exclusively one one at a time)
    they work as in Java.
    ```java
    // The following synchronize on the same lock
    synchronized ( this ) { }     // block
    synchronized int foo () { }   // method foo
    synchronized int bar () { }   // method bar
    int gee() {
        synchronized( super ) { }  // inside gee()
    }
    ```
  - The "throws" clause on methods is now supported.  Throws clause names
    are validated to insure that they are known class types, however they
    are not enforced beyond that.

  - Field style object property access now supports `isFoo()` style getters
    for boolean properties. e.g.
    ```java
    Float flot = new Float(0f);
    print( flot.infinite ); // float.isInifinite()
    ```
  - Improved error message on attempting to access private/protected
    members without setAccessibility(true).  Error now shows non-public
    method and suggests turning on accessibility.

  - User defined command prompt in interactive mode.  The user may set the
    value for the prompt string using the variable `bsh.prompt` or by
    defining the scripted method (or command) `getBshPrompt()`.
    If the command or method `getBshPrompt()` is defined it will be called to
    get a string to display as the user prompt.  For example, one could
    define the following method to place the current working directory into
    their command prompt:
    ```java
    getBshPrompt() { return bsh.cwd + " % "; }
    ```
   The default `getBshPrompt()` command returns the value of the variable
   `bsh.prompt` if it is defined or the string "bsh % " if not.  If the
   `getBshPrompt()` method does not exist, throws an exception, or does not
   return a String, a default prompt of "bsh % " will be used.

  - Fixed JConsole backspace through prompt bug.

  - User Command Path - You may now import BeanShell scripted or compiled
    commands from any package path using the `importCommands()` method.
    You may use either "/" path or "." package notation.  e.g.
    ```java
    // equivalent
    importCommands("/bsh/commands")
    importCommands("bsh.commands")
    importCommands("/mypackage/commands")
    ```
 -  When searching for a command each path will be checked for first, a file
   named `'command'.bsh` and second a class file named `'command'.class`.

  - You may add to the BeanShell classpath using the `addClassPath()` or
   `setClassPath()` commands and then import them as usual.
    ```java
    addClassPath("mycommands.jar");
    importCommands("/mypackage/commands");
    ```
  - If a relative path style specifier is used then it is made into an
   absolute path by prepending "/".  Later imports take precedence over
   earlier ones.

  - Imported commands are scoped just like imported classes.

  - See news about the `invoke()` meta-method for information about how you
   could implement your own, arbitrary command loading.

  - The `getResource()` command now utilizes the BeanShell classpath. i.e.
    you can get resources from paths added with `addClassPath()`, etc.

  - The BshClassManager now contains the methods `getResource()` and
    `getResourceAsStream()`.

  - The `invoke()` meta-method is now allowed directly in scope, e.g.
    ```java
    invoke( String methodName, Object [] arguments ) { ... }

    // invoke() will be called to handle noSuchMethod()
    noSuchMethod("foo");
    ```
    Previously `invoke()` was only allowed when called through a `this` type
    reference, e.g. a scripted interface.

 - One could use this capability to implement arbitrary command loading
   capabilities.  You would simply have to handle wrapping/ unwrapping
   of primitive value argument and return types with `bsh.Primitive`.

  - Fixed bug in `bsh.util.Httpd` causing class format errors
  - Changed BshServlet redirect to a relative path rather than absolute URL


## Changes in 1.2b8

- Some Strict Java mode bugs fixed. %(Incompatible API Change)
    Strict Java mode now functions on a per-interpreter basis.  The
    static strictJava variable is gone (it was always labeled as
    experimental) and is replaced by the instance method Interpreter
    `setStrictJava()`.  Interpreter `set()` methods now work (untyped
    assignments using Interpreter `set()` is allowed and existing untyped
    variables can be assigned in scripts in either mode).
    +Added a test case.
    Most bsh commands still fail in strict Java mode for simple reasons.
    These will be cleaned up in a future release.

- Added check for return of value from void method.  Fixed a couple of
    places where we made this mistake.

- Added `dirname()` command

- Added "test" target to build file (runs `tests/RunAllTests.bsh`).  Modified
    `RunAllTests.bsh` to `cd()` to the directory containing the `RunAllTests.bsh`
    script before executing the tests.

- Certain return type errors now point to specific locations, rather
    than the method declaration.

- Fixed Interpreter `setErr()` bug

- Exceptions thrown from proxy interfaces are now unwrapped.
    If your script throws an exception (or causes one to be thrown) that is
    declared in the throws clause of your interface method then it will
    be thrown from the method as one would expect.


<h2>Changes in 1.2b7 </h2>
<pre>
    - Fixed bug where Interpreter was not closing Reader streams after
    sourcing files.
    - Updated JConsole to work with Java 1.4.1 (changed getText.length() calls
    to getDocument().getLength()).
</pre>

<h2>Changes in 1.2b6 </h2>
<pre>
    - Made bsh.Parser public and added a main() method allowing users to
    call the parser on files for simple validity checking.
    - Made a small addition to grammar to provide an option to retain
    formal (JavaDoc style) comments in the parse tree.
    - Fixed accessibility bug in finding fields
    - Fixed minor bugs in bsh.servlet.BshServlet
    - Fixed scoping on catch blocks such that untyped variables
    in the catch parameter do not leak out of the block. They now act as they
    would with a declared type in Java (local).
    - Fixed some thread safety bugs with try/catch blocks.
    - Fixed Interpreter serialization issue - reset streams
    - Fixed bug in accessibility affecting access to package
        hidden superclasses
    - Added bshdoc.bsh script and bshdoc.xsl stylesheet to the scripts dir.
    These can be used to support JavaDoc style documentation of bsh files
    and commands.
    - Exposed bsh.BshMethod and added a public invoke() method.
    - Added getMethod() method to namespace to enumerate methods
</pre>

<h2>Changes in 1.2b5 </h2>
<pre>
    - Fixed bug in using loosely typed vars as indexes to arrays.
</pre>

<h2>Changes in 1.2b43 </h2>
<pre>
    - Modified Interpreter so that it is serializable.  It is now possible
    to serialize the entire bsh instance.  The load() and save() commands
    however recognize when you are trying to save a bsh object (bsh.This)
    type and detach it from the parent namespace, so that saving a bsh object
    in this way doesn't drag along the whole interpreter  See This.bind()
    and This.unbind().
</pre>

<h2>Changes in 1.2b42 </h2>
<pre>
    - Fixed bug affecting scoping in blocks.  This was breaking the test
    harness in a way that caused it not to be caught before!
    - Added a special test for the test harness to catch this sort of thing
    in the future.
    - Fixed problem with classloading causing bsh extensions not to be found
    in some environments.
</pre>

<h2>Changes in 1.2b3 and 1.2b4 </h2>
<pre>
    - The class manager now uses the Thread "context classloader" to factory
    classes when available.  This allows bsh to be installed in the
    jre/lib/ext directory (previously user classes wouldn't be seen).  Yeah!
    This may also allow bsh to see user classpath in application servers
    like WebLogic, which include it in the core classpath.
    - added the clear() command to clear variables and methods from a namespace.
    - added setOut() and setErr() methods to Interpreter.
    - added new bsh.servlet package to the main distribution
    - added bsh.Remote launcher
</pre>

<h2>Changes in 1.2b2 and 1.2b3 </h2>

    - Fixed nasty bug in grammar, added test cases to expression.bsh
    Note: this bug was preventing the latest version of bsh from being used
    with WebLogic 6.1 (which uses bsh internally and was failing).
    - Handled the "single line comment with no terminating linefeed" issue.
    - Added getInterface() method to Interpreter.
    - Fixed issue with identity semantics for this in cast case, e.g.:
        ((ActionListener)this == (ActionListener)this) is now true.
    - cat() command will now print files, URLs, streams, and readers
    - Exceptions in native (compiled) user classes will now print with
        both the BeanShell script trace and native Java stack trace.
        Exceptions generated directly from scripts continue to show only the
        script trace (as that is all that is meaningful).
    - ParseException is now a subtype of EvalError... previously these were
        caught and the contents were moved to an EvalError.  This means that
        now the ParseEx implements the contract as far as getErrorLineNumber(),
        getErrorText(), and getErrorSourceFile()
    - In interactive mode, the last uncaught exception is now available in
        the variable $_e (like the $_ last result).
    - Improved the way that imported class names are cached in namespaces,
        reducing the number of caches created and improving performance.
    - Improved javap() command slightly
    - Modified This references so that you can invoke the methods: getClass()
        and invokeMethod() on them.


<h2>Changes in 1.2b1</h2>
<pre>
    - Added support for accessibility - Using a dynamically loaded extension
    (ReflectManager) bsh will use the accessibility API to allow access to
    private/protected fields, methods and non-public classes.
    - added the setAccessible() command to turn this behavior on/off
    (it is *off* by default in this release...  we may change that later.)
    - added the bsh.reflect package and ReflectManager to support the above.
    - Added support for IBM's Bean Scripting Framework (BSF).
    - Added a new jar with the BSF adapter class bsh.util.BeanShellBSFEngine.
    This is also included in the full dist (it's small).
</pre>

<h2>Changes in 1.1alpha19</h2>
<pre>
    - Added a somewhat experimental "strict java" mode.  When activated with
    setStrictJava(true) BeanShell will:
        1) require typed variable declarations, method arguments and
            return types
        2) modify the scoping of variables to look for the variable declaration
            first in the parent namespace, as in a java method inside a java
            class.  e.g. if you can write a method called incrementFoo() that
            will do the correct thing without referring to super.foo.

    -Added another form of the run() command that accepts an argument to be
        passed to the child context.
</pre>

<h2>Changes in 1.1alpha18</h2>
<pre>
    - Fixed a bug affecting static field access.
    - Added a static setClassLoader() method to Interpreter and BshClassManager.
        This allows an external classloader to be specified.
</pre>

<h2>Changes in 1.1alpha17</h2>
<pre>
    - Added simple mechanism to allow scripted commands/methods to accept
      class identifiers as arguments. e.g.:
        foo( java.lang.String );
      see Name.identifierToClass();
    - added which() command - shows class source
    - modified class browser to show class source when class is selected
    - Changed the type of the (very recently added) this.callstack reference
        from an array to the actual internal CallStack type.  You can use
        toArray() to get it as an array or get(depth).
    - added unset() method to interpreter.  Fixed bug in set(name, null);
</pre>

<h2>Changes in 1.1alpha16</h2>
<pre>
    Improved error reporting on errors during method invocation.
</pre>

<h2>Changes in 1.1alpha15</h2>
<pre>
    - Fixed server mode and re-added demo applets
</pre>

<h2>Changes in 1.1alpha14</h2>
<pre>
    - Important addition - nodes now remember the source file from which
    they were parsed...  This means that debug messages are much more useful
    now, especially with errors thrown from multiple files.
    - Re-worked classpath mapping user feedback mechanism (will support a GUI
    now).
    - Fixed general block scoping bugs.  Things should act like Java now.
    typed vars declared within blocks does not leak out.  e.g. { int i=2; }
    - Added getSourceFileInfo() command to return the name of the file or
    source from which the current interpreter is reading.
    (uses this.interpreter.getSourceFileInfo())
    - Added a parent ref to the interpreter for future use by the child
    eval interpreters.
    - Added new form of source() command that takes a URL.
    - Tentatively added new feature to get the text of a method invocation
    from within the method body...  may be useful along with this.caller.
    See namespace.getInovcationText() namespace.getInvocationLine().
</pre>
<h2>Changes in 1.1alpha13</h2>
<pre>
    - Removed core bsh package dependencies on 1.2.  bsh will now compile and
        run under 1.2  with some features limited.
    - Moved some console related functionality out of the core and into
        bsh.util.  There is a new bsh.util.GUIConsoleInterface.  NameCompletion
        is now in bsh.util and has been replaced with a more primitive
        supporting interface bsh.NameSource in the core.
</pre>
<h2>Changes in 1.1alpha13</h2>
<pre>
    - Moved all Name object construction into NameSpace.  This will support
    Name resolvers caching information in the future. e.g. bsh could
    know that a variable is final and optimize the resolution.  Or a long
    chain of names (java.lang.Integer.MAX_INTEGER) could be optimized.
    - .class on array types now working. e.g.  "int [][].class"
    - Fixed bugs in grammar which disallowed certain simple expressions
    unless they were parenthesized.
    - Fixed related case relating to expressions with assignment to a
    throw-away method invocation.
</pre>


<h2>Changes in 1.1alpha12</h2>
<pre>
    - Added "trace" option.
        java -Dtrace=true bsh.Interpreter or trace() command.
        turns on printing of each line before it is executed.  Note that
        this currently prints only top level lines as they are parsed and
        executed by the interpreter.  Trace skips over method executions
        (including bsh commands) etc.  We may enhance this in the future.
    - Fixed bugs in primitive handling:
        primitives smaller than int are now properly assigned in declarations:
            e.g.: byte b = 5;
        primitives are internally promoted to the correct type on assignment
        to a typed variable,
            e.g.:  long l = (byte)5;  // l is a long value internally
    - Cleanup in cast operation: re-factored the cast code into two types
        of cast operations.
    - Tightened up re-declaration of typed variables...  You can only redeclare
        a typed var with the same type, else you can an error.  Previously
        there was odd behavior where you could redeclare with an assignable
        type.
    - Fixed bugs in casting
    - Fixed the remaining known bugs in array initializers.  They should now
    work precisely as they do in Java.  See the test case for examples.
</pre>
<h2>Changes in 1.1alpha11</h2>
<pre>
    - Improved error reporting.  There should no longer be any errors that
        report "unknown location".  At minimum, any error should report the
        source file and line number.  Normally errors should report the text
        of the offending construct.
    - Cleanup in array handling
        - fixed bugs with null initializers, loose types, promotion
</pre>
<h2>Changes in 1.1alpha10</h2>
<pre>
    - Major internal change: added callstack.
        two new magic references:
            bsh.This this.caller
            NameSpace [] this.callstack
    - Some commands now behave correctly, some slight changes- see notes.

    - made change to ensure that pathToFile always returns canonical path
    this fixed bug in addClassPath
</pre>

<h2>Changes in 1.1alpha9</h2>
<pre>
    - Added a very nice ANT build file, yeah!
    - added a bit more synchronization in console
    - removed unnecessary file/cwd related code
    - removed unnecessary code and lib file supporting old java packages
        optimization

    - Redefined scoping behavior in for-init to act like java.  See notes
    elsewhere.
    - Added switch statement.
    - made sure Ctrl-d in interactive mode shuts down interpreter.
    - Fixed cleanup of weak references in bsh class manager
</pre>

<h2>Changes in 1.1alpha8</h2>
<pre>
    - import changes -
        - Defined order of precedence - in bsh ambiguous imports are compile
        time error.  In bsh (where imports are allowed at any time) the later
        import takes precedence.
        - fixed bug causing stack overflow

    - Internal restructuring - made Interpreter its own class rather than being
        auto-generated by JavaCC.  This allows us to generate decent JavaDoc
        without hundreds of parser methods.  Improved JavaDoc.

    - Fixed the precedence of name resolution to be consistent with Java.
        variables now always trump class name resolution.  This simplifies
        name resolution a bit and also makes things a bit faster.  However
        sometimes you have to use import now to get around a variable name
        hiding a qualified class name.

    - Performance improvement: empty loop test case four times faster
        overall appears as much as 30% faster
    - Fixed bugs in certain runtime exception handling
    - Fixed bug in static vs. dynamic overloaded method resolution
</pre>

<h2>Changes in 1.1alpha6</h2>
<pre>
    - Fixed grammar relating to Expression/BlockStatement parsing...  This was
    causing the bug with declaring a typed var with a fully qualified class
    name.  e.g. java.sql.Date date;

    - Fixed 'return' control problem...  return now works properly for
    interactive and non-interactive scripts.

    - Fixed CTRL-D bug - now exits properly in interactive mode.
</pre>

<h2>
<h2>Changes in 1.1alpha51</h2>
<pre>
    - Fixed bug causing debug code to call toString() for every method call on
    an object, whether debug was on or not.
</pre>
</h2>

<h2>Changes in 1.1alpha5</h2>
<pre>
    - License change only...  Added SPL Sun Public License agreement with
    dual licensing under the LGPL
</pre>

<h2>Changes in bsh1.1alpha3</h2>
<pre>
    - Added .field style properties access in addition to the {} style
    - Added anonymous inner class style allocation for interfaces
    - Class browser now reflects changes in class path
</pre>

<h2>Changes in bsh1.1alpha2</h2>
<pre>
    - Added namespace based name completion for console and classbrowser
        completes variables, classes, (coming... bsh methods)

    - Added import * capability to read full classpath.
    - Added overloading of bsh scripted methods
    - Added classpath extension and class reloading
        reloadClasses() reloadClasses(item)

    - improved the class browser - now has tree for packages and reflects
    the extended classpath , other improvements

    - Loose arguments in catch clauses now work.
    - deprecated getVariable/setVariable api.  Moving to get()/set()/eval();
    - internally removed BshVariable - it wasn't doing what it was historically
    intended for.  Replaced with a new bsh.system object which is shared
    across all interpreter instances
    - Added bsh.system.shutdownOnExit variable... set to false to prevent
    exit() command and desktop close from exiting the VM.
    - Loosened grammar to allow import statements in any block scope.
    e.g. you can now import within a bsh method and the import will be local
    to that method
    -Added javap() command...  accepts object, class, or string name and
    prints public fields, methods

    -Added -Doutfile=foo capture output to file (for Windows nightmare)
    - Cleaned up a lot of internal structure in NameSpace, etc.
    -Interpreter now implements ConsoleInterface
</pre>

## Changes in bsh-1.01
<pre>
    - Modified parse exceptions to print more tersely...
    Removed the "Was expecting one of..." text *except* when debug is on.
    - Fixed the for statement forinit scope bug.
    - Got rid of remaining deprecation (using Readers vs. InputStreams)
    - Fixed top level expression bug #111342
    - Fixed problems with compiling bsh under limited JDK verions (1.1, 1.2).
      We now have factories insulating the core from all extended version
      capabilities (e.g. proxy mechanism stuff, accessibility TBD)
    - Modified browseClass() command to accept string class name,
        class instance, or arbitrary object
    - Modified search for constructors to accommodate package and private
        access.
    - Added close button to class browser.
</pre>

<h2>Changes in bsh-1.0</h2>
<em>Not necessarily in order of importance</em>
<p>
<ul>

<li>Added generalized support for scripts implementing interfaces (e.g.
arbitrary event listeners).
This uses the important new JDK1.3 reflection proxy mechanism to manufacture
a proxy interface at run time.  No code generation is necessary!
<ul>
    <li>Added support to the cast operation to use the new mechanism.
    <li>Added support for automatic conversion to interface on method selection.
    e.g. if you attempt to pass a bsh scripted object as a method argument
    where the method signature calls for an interface an automatic cast to
    the appropriate interface type will be attempted.
    <li>Added a magic method invoke(method,args) which can be used to
    handle method invocations on undefined interface methods in bsh objects.
    This takes the place of "dummy" adapters; allowing a script to ignore one
    or more methods of an interface that it is implementing.
    <em>Note: one special case - direct invocations within scope
    (e.g. command invocations) are not currently sent to invoke.</em>
</ul>

<li> Added startup file (.rc file) support.
Bsh will source the file "user.home"/.bshrc upon startup.
This defaults to C:\Windows under win98 and $HOME under Unix.
(can the home be set with an env var under Win?  "home" doesn't seem to do it).

<li>Added arguments to file invocation on command line.
e.g.
<pre>
    java bsh.Interpreter MyClass foo bar
</pre>
Args are accessible through the root bsh object:  String [] bsh.args

<li>Enhancements to JConsole submitted by Daniel Leuck;
Added color and image support, fixed several bugs.

<li>Added support for inner classes.  This should all work as expected, but
it's new so let me know if you find weirdness.
<ul>
    <li>Added support for inner classes to import statement.
</ul>

<li>Changed the way eval()/source() handle script errors.  Instead of
returning the error object as a value it is now wrapped up with some context
and rethrown as an EvalError.  So you can simply catch the error with a normal
try/catch block if you want to.  Previously errors in sourced/eval'd files
were being squelched.  This was bad.  Note: exceptions generated by the
script or through code called by the script are thrown as TargetErrors (a
subclass of EvalError) which can also be caught and examined.


<li>Improved error reporting in many areas.
Fixed really annoying error reporting bug that squelched target error
info in sourced files (and commands).

<li>Improved bsh cast operation so that it throws standard ClassCastException
for invalid cast.  You can now guard against them with the ordinary try/catch
in a bsh script.

<li>Modified the command line portion of the grammar to accept arbitrary
expressions. e.g. you can type ``5*2;'' or ``foo instanceof Foo;'' on the
command line now without any enclosing parens.
(Of course you won't see anything unless you're using the show() option).

<li>Removed the old AWT version of the GUI console.  If you need it you can
get it on the web site separately.  I may reconsider this.

<li>Removed the console() command which was primarily for the old AWT console.

<li>Modified the browseClass() command to take an object instead of a string
class name.  Now you can simple say browseClass( someObject ) and pop up
the class browser to the correct place.  Special hack: If the specified object
is a Class it will use the class.  This will all probably be replaced by
a general browse() method for the upcoming object inspector.

<li>Changed the return type of the frame() command to allow it to return an
internal frame when desktop is active.  Frame will now do the correct thing
whether the desktop is up or not.

<li>Rebuilt the distribution with JavaCC / JJTree version 1.1.
Haven't notice any difference yet.

<li>Fixed the 'for' scoping bug - See docs on for scoping for clarification.
Previously variables declared within the for-init section were leaking out
into the outer namespace.

<li>Fixed a bug in which tokenizer errors would cause the interpreter to hang
or exit.  They are now handled like other parsing errors.  In the future we
may want to break them out so that they can be handled separately from
EvalError.

<li>Added missing += form of string concatenation.

<li>Incorporated a patch and test suite case from Roger Bolsius that corrects
some of the package / hidden reflected class access.  Previously the code did
not handle the more difficult cases.

<li>Incorporated a patch from Mike Woolley which works around JDK bug 4071281
(EOF problem) under Windows JDK v1.1.

<li>Fixed most of the bugs in server mode.  Run the server pair (httpd /
sessiond) using the server( port ) command.  Then you can telnet to port+1
or attach your web browser to port.  Note that the web browser must support
swing to run the remote JConsole.  We could supply the AWTConsole back for
compatibility with old browsers...  but I'd like to move on.

<li>Internal trivia - changed the prefix of the names of all of the parser
node classes from AST to BSH.

<li>Fixed a bug which caused ClassCastException during (ironically) a bsh
cast operation.

<li>Improved the test harness slightly and added a number of new files to the
test suite for all of the new features.  Please send more test cases for
the test suite!

<li>Internal change: Simplified the code that determines array base types.

<li> Fixed bug where special characters on input (e.g. control character ^D)
would cause the tokenizer to loop on errors.  Non printable chars are now
skipped as white space.

<li>Added the missing do-while statement

<li>Internal: Tightened up the code a bit by combining the BSH node conditional
evaluation into one place.

<li>Fixed some race conditions in the JConsole.
Fixed multi-writer console problems.

<li>Fixed order of evaluation bugs: classes now always first, then bsh vars.
Note: this may not always be desirable.  e.g. if you have a class named
"x" in your path (violating the common naming conventions) then you can't
use a variable of name 'x' in your scripts.  Conversely though, it prevents
one from doing "Integer = 5;" and shadowing the java.lang.Integer class
with a variable name.  Any thoughts on this?

<li>Corrected handling of the bsh root object.

<li>Added a menu item to console to redirect stdin/stdout/stderr.
If you close the console they are restored to the original System.in,out,err.

</ul>
