# BeanShell

## 2016-02-18 Security update

**Note**: A _security vulnerability_ has been identified in BeanShell that could be
exploited for _remote code execution_ in applications that has
BeanShell on its classpath. The vulnerability has been
fixed in the security update [BeanShell 2.0b6](https://github.com/beanshell/beanshell/releases/tag/2.0b6). 
This is a **recommended update** for all BeanShell users.


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

The source code releases can be downloaded from [GitHub releases](https://github.com/beanshell/beanshell/releases)
or [Bintray](https://bintray.com/beanshell/Beanshell/bsh/view).

Latest release: 

 - [BeanShell 2.0b6](https://github.com/beanshell/beanshell/releases/tag/2.0b6) - [bsh-2.0b6-src.zip](http://dl.bintray.com/beanshell/Beanshell/org/apache-extras/beanshell/bsh/2.0b6/bsh-2.0b6-src.zip)


### Maven

Beanshell releases are published to [Maven Central](http://central.maven.org/maven2/org/apache-extras/beanshell/bsh/). To use Beanshell with Maven, add to to your `pom.xml`: 

```xml
    <dependencies>
       <dependency>
         <groupId>org.apache-extras.beanshell</groupId>
         <artifactId>bsh</artifactId>
         <version>2.0b6</version>
       </dependency>
    </dependencies>
```

Alternatively you can use 
[our BinTray Maven repository](http://dl.bintray.com/beanshell/Beanshell) or
[JCenter](http://jcenter.bintray.com/org/apache-extras/beanshell/bsh/2.0b6/):

```xml
<!-- just beanshell -->
<repository>
  <id>bintray-beanshell-Beanshell</id>
  <name>bintray</name>
  <url>http://dl.bintray.com/beanshell/Beanshell</url>
  <snapshots><enabled>false</enabled></snapshots>
</repository>
<!-- or use JCenter -->
<repository>
  <id>central</id>
  <name>bintray</name>
  <url>http://jcenter.bintray.com</url>
  <snapshots><enabled>false</enabled></snapshots>
</repository>
```

### JAR binary

You can also download the `bsh.jar` binary from Bintray. 

- [bsh-2.06b.jar](https://bintray.com/artifact/download/beanshell/Beanshell/org/apache-extras/beanshell/bsh/2.0b6/bsh-2.0b6.jar)

If you want to execute the Beanshell [User Interface](https://github.com/beanshell/beanshell/wiki/Desktop), either double-click the JAR file, or run it with: 

    java -jar bsh-2.0b6.jar 

You will need [Java](http://java.com/) 5 or later installed. 

**Note**: There is a race-condition bug #4 that sometimes prevent the GUI from starting on Java 8.


## Contribute

You are encouraged to raise a Github [Pull Request](https://github.com/beanshell/beanshell/pulls) with any suggested improvements and fixes!

You can also raise an [issue](https://github.com/beanshell/beanshell/issues) for any questions or bugs. Remember, your stacktrace might be particularly useful for others!


## Documentation

For full documentation, see the [BeanShell wiki](https://github.com/beanshell/beanshell/wiki) (recently moved from the
[user manual](https://cdn.rawgit.com/beanshell/beanshell/2.0b6/docs/manual/html/index.html))
and the [FAQ](https://cdn.rawgit.com/beanshell/beanshell/2.0b6/docs/faq/faq.html) for frequently
asked questions.

### Summary of features

 - Dynamic execution of the full Java syntax, Java code fragments, as well as loosely typed Java and additional scripting conveniences.
 - Transparent access to all Java objects and APIs.
 - Runs in four modes: Command Line, Console, Applet, Remote Session Server.
 - Can work in security constrained environments without a classloader or bytecode generation for most features.
 - The interpreter is small, ~400K jar file.
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

# History

## 2000: beanshell.org

BeanShell was originally maintained by Patrick Niemeyer at http://beanshell.org/ - distributed as 
BeanShell (2.0b4 and earlier) were distributed under 
GNU Lesser General Public License (LGPL) and Sun Public License (SPL).

## 2012: Move to apache-extras.org

BeanShell was [proposed as an incubator project](https://wiki.apache.org/incubator/BeanShellProposal) to
move to [Apache Software Foundation](http://www.apache.org/). In preparation for this, the codebase
for BeanShell 2.0b4 was donated to ASF by a code grant, and the license changed to 
[Apache License, version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

The source code was moved to http://apache-extras.org/ - a project home hosted by Google Code, that was only informerly associated with Apache Software Foundation. Many of the BeanShell committers were Apache committers, and thus Apache Extras seemed a natural home.

However the project did not to move into the [Apache incubator](http://incubator.apache.org/), and remained at apache-extras.org as an independent project.

In March 2015 Google announced it would discontinue Google Code, which provided the hosting for Apache Extras.

## 2015: Moved to github.com

On 2015-09-23, the BeanShell repository moved from https://code.google.com/a/apache-extras.org/p/beanshell/ to its new home on https://github.com/beanshell/beanshell/ as Google Code has been discontinued.

The project adapted an open collaboraive approach using [GitHub pull requests](https://github.com/beanshell/beanshell/pulls) and has since grown its committer base beyond the original Apache Extra team.

http://beanshell.org/ remains available for older versions.
