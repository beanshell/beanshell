# BeanShell

## Moved from apache-extras.org

On 2015-09-23, the BeanShell repository moved from https://code.google.com/a/apache-extras.org/p/beanshell/ to its new home on https://github.com/beanshell/beanshell/ as Google Code has been discontinued.

BeanShell had previously moved to Apache Extras from http://beanshell.org/, which remains available for older versions.


## Introduction

BeanShell is a small, free, embeddable Java source interpreter with object scripting language features, written in Java. BeanShell dynamically executes standard Java syntax and extends it with common scripting conveniences such as loose types, commands, and method closures like those in Perl and JavaScript.

You can use BeanShell interactively for Java experimentation and debugging as well as to extend your applications in new ways. Scripting Java lends itself to a wide variety of applications including rapid prototyping, user scripting extension, rules engines, configuration, testing, dynamic deployment, embedded systems, and even Java education.

BeanShell is small and embeddable, so you can call BeanShell from your Java applications to execute Java code dynamically at run-time or to provide extensibility in your applications. Alternatively, you can use standalone BeanShell scripts to manipulate Java applications; working with Java objects and APIs dynamically. Since BeanShell is written in Java and runs in the same VM as your application, you can freely pass references to "live" objects into scripts and return them as results.


## License

BeanShell is licensed under the 
[Apache License, version 2.0](http://www.apache.org/licenses/LICENSE-2.0). See [LICENSE](LICENSE) for details, and the [NOTICE](NOTICE) file for required attributions.

Earlier versions of BeanShell (2.0b4 and earlier) were distributed under 
GNU Lesser General Public License (LGPL) and Sun Public License (SPL).

## Download

### Source code

The source code releases can be downloaded from [Bintray](https://bintray.com/beanshell/Beanshell/bsh/view#files).

Latest source code:

- [bsh-2.0b5-src.zip](https://bintray.com/artifact/download/beanshell/Beanshell/bsh-2.0b5-src.zip)

### Maven

Beanshell releases are published to [Maven Central](http://central.maven.org/maven2/org/apache-extras/beanshell/bsh/). To use Beanshell with Maven, add to to your `pom.xml`: 

```xml
    <dependencies>
       <dependency>
         <groupId>org.apache-extras.beanshell</groupId>
         <artifactId>bsh</artifactId>
         <version>2.0b5</version>
       </dependency>
    </dependencies>
```


### JAR binary

You can also download the `bsh.jar` binary from Bintray. 

- [bsh-2.05b.jar](https://bintray.com/artifact/download/beanshell/Beanshell/org/apache-extras/beanshell/bsh/2.0b5/bsh-2.0b5.jar)

To execute the Beanshell user interface, either double-click the JAR file, or run it with: 

    java -jar bsh-2.0b5.jar 

You will need [Java](http://java.com/) 5 or later installed.

## Build

    mvn clean install -DskipTests


## Contribute

You are encouraged to raise a Github [Pull Request](https://github.com/beanshell/beanshell/pulls) with any suggested improvements and fixes!

You can also raise an [issue](https://github.com/beanshell/beanshell/issues) for any questions or bugs. Remember, your stacktrace might be particularly useful for others!


## Documentation

For full documentation, see the [BeanShell user manual](https://cdn.rawgit.com/beanshell/beanshell/2.0b5/docs/manual/html/index.html)
and the [FAQ](https://cdn.rawgit.com/beanshell/beanshell/2.0b5/docs/faq/faq.html) for frequently
asked questions.

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
