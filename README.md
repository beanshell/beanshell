# BeanShell - Simple Java Scripting
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Build workflow](https://github.com/beanshell/beanshell/actions/workflows/maven.yml/badge.svg?branch=main)](https://github.com/beanshell/beanshell/actions/workflows/maven.yml) [![codecov](https://codecov.io/gh/beanshell/beanshell/branch/main/graph/badge.svg)](https://codecov.io/gh/beanshell/beanshell)
[![coverity](https://scan.coverity.com/projects/16379/badge.svg)](https://scan.coverity.com/projects/beanshell-beanshell)
[![Known Vulnerabilities](https://snyk.io/test/github/beanshell/beanshell/badge.svg)](https://snyk.io/test/github/beanshell/beanshell)

The **official** and **active** project home for BeanShell.

## NOTICE: Pending new release
The only recommended version is a manual build of the main branch. Support for legacy releases reached end-of-life; only issues and pull requests against main will be accepted.

The next release will be BeanShell 3.0, as the [development roadmap](https://github.com/beanshell/beanshell#development-road-map) outlines. It [was decided](https://github.com/beanshell/beanshell/issues/81) that the next release would be a production-ready major version with all outstanding issues resolved to encourage community involvement and testing.

Most of the outstanding issues were [imported from Sourceforge](https://github.com/beanshell/beanshell/labels/auto-migrated%20sourceforge), where they were reported many years ago and are now orphaned without owners. The majority has already been resolved on main. **An earnest call goes out for assistance in processing these issues to verify whether they are still valid, reproducible, or already resolved.**

New language enhancements remain open for [comments and discussion](https://github.com/beanshell/beanshell/labels/discussion).

Items identified as [out of scope for 3.0](https://github.com/beanshell/beanshell/labels/out%20of%20scope%20v3.0) are scheduled for the next release.
 * `StrictJava` compliance, unit tests, and outstanding issues
 * Updated [documentation](http://beanshell.org/manual/contents.html). The [discussions](https://github.com/beanshell/beanshell/labels/discussion) double as future documentation.

## Introduction
BeanShell is a small, free, embeddable Java source interpreter written in Java with object scripting language features. BeanShell executes standard Java syntax dynamically and extends it with common scripting conveniences such as loose types, commands, and method closures like those in Perl and JavaScript.

You can use BeanShell interactively for Java experimentation and debugging as well as to extend your applications in new ways. Scripting Java lends itself to various applications, including rapid prototyping, user scripting extension, rules engines, configuration, testing, dynamic deployment, embedded systems, and Java education.

BeanShell is small and embeddable, so you can call BeanShell from your Java applications to execute Java code dynamically at run-time or to provide extensibility in your applications. Alternatively, you can use standalone BeanShell scripts to work with Java objects and APIs to manipulate Java applications dynamically. Since BeanShell is written in Java and runs in the same VM as your application, you can freely pass references to "live" objects into scripts and return them as results.

## License

Starting with version 2.0b5, BeanShell is licensed under the
[Apache License, version 2.0](http://www.apache.org/licenses/LICENSE-2.0). See [LICENSE](LICENSE) for details and the [NOTICE](NOTICE) file for required attributions.

## Download

### Source code
The development branch is main, and it is currently recommended that you use that version. To build, pull the project, and run the maven command.

```shell
$ mvn install
```

Building BeanShell requires JDK 8 or newer. The resulting JAR targets Java 8 and runs on Java 8, Java 11, Java 17, Java 21, and Java 25.

The source code releases can be downloaded from [GitHub releases](https://github.com/beanshell/beanshell/releases)

Latest release:

 - [BeanShell 2.1.1](https://github.com/beanshell/beanshell/releases/tag/2.1.1) - [bsh-2.1.1-src.zip](https://github.com/beanshell/beanshell/releases/download/2.1.1/bsh-2.1.1-src.zip)


### Maven

Beanshell 3.0.0 currently only has a SNAPSHOT release published to [Sonatype](https://oss.sonatype.org/content/repositories/snapshots/org/beanshell/bsh/). To use Beanshell with Maven, add this to your `pom.xml`:

```xml
    <dependencies>
       <dependency>
         <groupId>org.beanshell</groupId>
         <artifactId>bsh</artifactId>
         <version>3.0.0-SNAPSHOT</version>
       </dependency>
    </dependencies>
```

### JAR binary

You can also download the `bsh.jar` binary from the releases page or the link below:

- [bsh-2.1.1.jar](https://github.com/beanshell/beanshell/releases/download/2.1.1/bsh-2.1.1.jar)

If you want to execute the Beanshell [User Interface](https://github.com/beanshell/beanshell/wiki/Desktop), either double-click the JAR file, or run it with:

```shell
$ java -jar bsh-2.1.1.jar
```

For a BeanShell interactive shell, you can either use the `java` command:

```shell
$ java -cp bsh-2.1.1.jar bsh.Interpreter
```

or the supplied helper scripts `bsh` or `bsh.bat`, available under the scripts folder.


You will need [Java](http://java.com/) 5 or later installed.

## Build

```shell
$ mvn clean install
```

## Contribute

You are encouraged to raise a Github [Pull Request](https://github.com/beanshell/beanshell/pulls) with any suggested improvements and fixes!

You can also raise an [issue](https://github.com/beanshell/beanshell/issues) for any questions or bugs. Remember, your stack trace might be particularly useful for others!

Please note only issues and pull requests made against the main branch will be considered.

## Documentation

For full documentation, see the [BeanShell wiki](https://github.com/beanshell/beanshell/wiki) and the [FAQ](https://github.com/beanshell/beanshell/wiki/FAQ) for frequently asked questions.

See the [script migration guide](MIGRATION.md) for compatibility examples and the [changelog](CHANGES.md) for build and Java integration notices.

See the [SecurityGuard guide](SecurityGuard.md) for Java embedding examples and application-defined script policies.

The old documentation available at [http://beanshell.org](http://www.beanshell.org/docs.html) may also be useful.

### Summary of features

 - Dynamic execution of the entire Java syntax, Java code fragments, loosely typed Java, and additional scripting conveniences.
 - Transparent access to all Java objects and APIs.
 - Runs in three modes: Command Line, Console, and Applet.
 - Works in security-constrained environments without a classloader or bytecode generation for most features.
 - The interpreter is small, ~400K jar file.
 - Pure Java.
 - It's Free!!!

### Java evaluation features

- Dynamically evaluate full Java source classes, isolated Java methods, statements, and expressions.

### Scripting features

- Optionally typed variables.
- Scripted methods with optionally typed arguments and return values
- Scripted objects (method closures)
- Scripted interfaces and event handlers.
- Lambda expressions (Java 8 syntax), usable with Java APIs without casts.
- Convenience syntax for working with JavaBean? Properties, hashtables, and primitive wrapper types.
- Auto-allocation of variables to emulate Java properties files.
- Extensible set of utility and shell-like commands
- Dynamic classpath management, including fine-grained class reloading
- Dynamic command loading and user command path
- Sophisticated namespace and callstack management
- Detailed error reporting

### BeanShell Uses

- Interactive Java - try out object features, APIs, and GUI widgets - "hands-on".
- Scripting extension for applications - Allow your applications to be extended via scripts in an intuitive and simple way.
- Macro Languages - Generate scripts as macros and execute them live in your VM easily.
- Education - Teach Java in a hands-on, live environment
- Expression evaluator for scientific, financial apps, and rules engines - evaluate complex expressions with conditions and loops.
- Remote debugging - Embed a live, remotely accessible shell/command line in your application with just a few lines of code.
- Use BeanShell declaratively to replace properties files and replace startup config files with real scripts that perform complex initialization and setup with the full Java syntax at their disposal.

## Threading and concurrency

The supported contract is one `Interpreter` per thread, with each thread's own `Interpreter` used only from that thread.

What is not safe:

- Calling `eval()` or `source()` concurrently on one shared `Interpreter`. Its global `NameSpace` and the AST nodes a parsed script produces hold plain, unsynchronized state that gets read and written during evaluation, so two threads sharing an interpreter can corrupt each other's results or throw spurious errors ([#881](https://github.com/beanshell/beanshell/issues/881)).
- Sharing one `NameSpace` across threads for the same reason, even without a shared `Interpreter`.
- Sharing a scripted method, class, or other parsed artifact across threads: the first evaluation of a shared AST node can cache resolved state (a method's parameter types, a variable declarator) into the node itself, and that caching isn't synchronized.
- Using `bsh.system` (or its alias `bsh.shared`) from more than one thread. Every `Interpreter` in the JVM is wired to the same underlying `NameSpace`, so this one is shared even between otherwise fully independent interpreters on separate threads.

`PreparsedScript` narrows the exposure — each `invoke()` runs in a fresh child scope — but resolving an unqualified class name for the first time can still fall through to the shared parent namespace and write its class cache unsynchronized, so it's not a guarantee under concurrent use.

One further known, bounded limitation: declaring a scripted class that's never instantiated or otherwise initialized can pin its declaring `Interpreter` in memory indefinitely ([#843](https://github.com/beanshell/beanshell/issues/843)). A class that goes on to be used releases normally.

## Development road map

The current development effort focuses on releasing BeanShell 3.0. The following road map serves as a guide to gauge progress to the next release.

 - [x] Merge fork BeanShell2
 - [x] Support for Java 9/10 with illegal access denied
 - [x] Implement varargs
 - [x] Implement try with resources
 - [x] Implement multi-catch
 - [x] Implement interfaces: constants, static, and default methods
 - [x] Implement generics parsing
 - [x] Implement final modifier
 - [x] Implement BigInteger/BigDecimal and number coercion
 - [x] Make all current unit tests pass
 - [x] Increase unit tests code coverage 70%
 - [ ] Resolve all critical outstanding issues and process pull requests
 - [ ] Apply uniform code style and Javadocs
 - [ ] Consider feedback from [community discussions](/beanshell/beanshell/labels/discussion)

## Projects using BeanShell

Projects that we know of that are using BeanShell. Is your project not listed here? Let us know by submitting an [issue](/beanshell/beanshell/issues).

 * [Apache Ant](https://ant.apache.org/manual/Tasks/script.html)
 * [Apache Camel](http://camel.apache.org/beanshell.html)
 * [Apache Maven](https://maven.apache.org/plugin-tools/maven-plugin-plugin/examples/beanshell-mojo.html)
 * [Apache OpenOffice](http://www.openoffice.org/framework/scripting/scriptingf1/developer-guide.html)
 * [Apache Taverna](https://taverna.incubator.apache.org/introduction/services-in-taverna)
 * [Apache jMeter](https://jmeter.apache.org/usermanual/component_reference.html#BeanShell_Sampler)
 * [CA DevTest](https://docops.ca.com/devtest-solutions/8-0-2/en/using/using-ca-application-test/using-the-workstation-and-console-with-ca-application-test/advanced-features/using-beanshell-in-devtest/using-beanshell-scripting-language)
 * [Cisco Prime Network](https://www.cisco.com/c/en/us/td/docs/net_mgmt/prime/network/5-0/customization/guide/CiscoPrimeNetwork-5-0-CustomizationGuide/appendix-commandbuilder.html)
 * [ImageJ](https://imagej.net/BeanShell_Scripting)
 * [JDE for Emacs](https://www.emacswiki.org/emacs/JavaDevelopmentEnvironment)
 * [Joget](https://dev.joget.org/community/display/KBv6/Bean+Shell+Programming+Guide)
 * [LibreOffice](https://help.libreoffice.org/Common/Scripting_LibreOffice)
 * [LifeRay](https://dev.liferay.com/discover/portal/-/knowledge_base/7-0/using-liferays-script-engine)
 * [Mentawai](http://old.mentaframework.org/configuration.jsp?loc=en)
 * [Micro-Manager](https://micro-manager.org/wiki/Script_Panel_GUI)
 * [NetBeans](http://plugins.netbeans.org/plugin/40982/beanshell)
 * [OpenJUMP](http://ojwiki.soldin.de/index.php?title=Scripting_with_BeanShell)
 * [OpenKM](https://www.openkm.com/wiki/index.php/Scripting_-_OpenKM_6.2)
 * [Spring](https://docs.spring.io/spring-framework/docs/3.2.x/spring-framework-reference/html/dynamic-language.html)
 * [TestNG](https://testng.org/doc/documentation-main.html#beanshell)
 * [jAlbum](https://jalbum.net/help/en/Scripting)
 * [jEdit](http://www.jedit.org/users-guide/beanshell-intro.html)
 * [Jupyter Notebooks](https://github.com/opeongo/jupyter_beanshell)

