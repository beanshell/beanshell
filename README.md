# BeanShell

## Moved from apache-extras.org

On 2015-09-23, the BeanShell repository moved from https://code.google.com/a/apache-extras.org/p/beanshell/ to its new home on https://github.com/beanshell/beanshell/ as Google Code has been discontinued.

BeanShell had previously moved to Apache Extras from http://beanshell.org/, which remains available for older versions.


## Introduction

BeanShell is a small, free, embeddable Java source interpreter with object scripting language features, written in Java. BeanShell dynamically executes standard Java syntax and extends it with common scripting conveniences such as loose types, commands, and method closures like those in Perl and JavaScript.

You can use BeanShell interactively for Java experimentation and debugging as well as to extend your applications in new ways. Scripting Java lends itself to a wide variety of applications including rapid prototyping, user scripting extension, rules engines, configuration, testing, dynamic deployment, embedded systems, and even Java education.

BeanShell is small and embeddable, so you can call BeanShell from your Java applications to execute Java code dynamically at run-time or to provide extensibility in your applications. Alternatively, you can use standalone BeanShell scripts to manipulate Java applications; working with Java objects and APIs dynamically. Since BeanShell is written in Java and runs in the same VM as your application, you can freely pass references to "live" objects into scripts and return them as results.

### Summary of features

 - Dynamic execution of the full Java syntax, Java code fragments, as well as loosely typed Java and additional scripting conveniences.
 - Transparent access to all Java objects and APIs.
 - Runs in four modes: Command Line, Console, Applet, Remote Session Server.
 - Can work in security constrained environments without a classloader or bytecode generation for most features.
 - The interpreter is small ~150K jar file.
 - Pure Java.
 - It's Free!! 

### Java evaluation features

- Evaluate full Java source classes dynamically as well as isolated Java methods, statements, and expressions. 

### Scripting features

- Optionally typed variables.
- Scripted methods with optionally typed arguments and return values
- Scripted objects (method closures)
- Scripted interfaces and event handlers.
- Convenience syntax for working with JavaBean? properties, hashtables, and primitive wrapper types.
- Auto-allocation of variables to emulate Java properties files.
- Extensible set of utility and shell-like commands
- Dynamic classpath management including find grained class reloading
- Dynamic command loading and user command path
- Sophisticated namespace and callstack management
- Detailed error reporting 

### BeanShell Uses

- Interactive Java - try out object features, APIs and GUI widgets - "hands on".
- Scripting extension for applications - Allow your applications to be extended via scripts in an intuitive and simple way.
- Macro Languages - Generate scripts as macros and execute them live in your VM easily.
- Education - Teach Java in a hands-on, live environment
- Expression evaluator for scientific, financial apps and rules engines - evaluate complex expressions with conditions and loops.
- Remote debugging - Embed a live, remotely accessible shell / command line in your application with just a few lines of code.
- Use BeanShell declaratively to replace properties files and replace startup config files with real scripts that perform complex initialization and setup with the full Java syntax at their disposal. 
