# BeanShell - Simple Java Scripting
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Build workflow](https://github.com/beanshell/beanshell/actions/workflows/maven.yml/badge.svg?branch=master)](https://github.com/beanshell/beanshell/actions/workflows/maven.yml) [![codecov](https://codecov.io/gh/beanshell/beanshell/branch/master/graph/badge.svg)](https://codecov.io/gh/beanshell/beanshell)
[![coverity](https://scan.coverity.com/projects/16379/badge.svg)](https://scan.coverity.com/projects/beanshell-beanshell)
[![Known Vulnerabilities](https://snyk.io/test/github/beanshell/beanshell/badge.svg)](https://snyk.io/test/github/beanshell/beanshell)

The **official** and **active** project home for BeanShell.

## NOTICE: Pending new release

The only reccomended version is a manual build of the master branch. Support for legacy releases reached end of line, only issues and pull requests against master can be accepted.

The next release will be BeanShell 3.0 as outlined in the [development roadmap](https://github.com/beanshell/beanshell#development-road-map). It [was decided](https://github.com/beanshell/beanshell/issues/81) that the next release will be a production ready major version with all outstanding issues resolved to encourage community envolvement and testing.

Most of the outstanding issues were [imported from sourceforge](https://github.com/beanshell/beanshell/labels/auto-migrated%20sourceforge), reported many years ago and now orphaned without owners. The majority will already be resolved in master, the valid ones need to be identified to the developers. **An earnest call goes out for assistance in processing these issues to verify if they are still valid, reproducible or already resolved.**

New language enhancement remain open for [comments and discussion](https://github.com/beanshell/beanshell/labels/discussion).

Items identified as [out of scope for 3.0](https://github.com/beanshell/beanshell/labels/out%20of%20scope%20v3.0) are scheduled for the next release.
 * `StrictJava` compliance, unit tests and outstanding issues
 * Updated [documentation](http://beanshell.org/manual/contents.html). The [discussions](https://github.com/beanshell/beanshell/labels/discussion) double as future documentation.

## Introduction

BeanShell is a small, free, embeddable Java source interpreter with object scripting language features, written in Java. BeanShell dynamically executes standard Java syntax and extends it with common scripting conveniences such as loose types, commands, and method closures like those in Perl and JavaScript.

You can use BeanShell interactively for Java experimentation and debugging as well as to extend your applications in new ways. Scripting Java lends itself to a wide variety of applications including rapid prototyping, user scripting extension, rules engines, configuration, testing, dynamic deployment, embedded systems, and even Java education.

BeanShell is small and embeddable, so you can call BeanShell from your Java applications to execute Java code dynamically at run-time or to provide extensibility in your applications. Alternatively, you can use standalone BeanShell scripts to manipulate Java applications; working with Java objects and APIs dynamically. Since BeanShell is written in Java and runs in the same VM as your application, you can freely pass references to "live" objects into scripts and return them as results.

## License

Starting with version 2.0b5, BeanShell is licensed under the
[Apache License, version 2.0](http://www.apache.org/licenses/LICENSE-2.0). See [LICENSE](LICENSE) for details, and the [NOTICE](NOTICE) file for required attributions.

## Download

### Source code

The development snapshot branch is master, the currently recommended version for use. To build, pull the project and run the maven command.

```shell
$ mvn install
```

Requires as a minimum JDK 8 but will build with Java 9 and Java 10 as well.

The source code releases can be downloaded from [GitHub releases](https://github.com/beanshell/beanshell/releases)

Latest release: **not supported** (use the recommended development snapshot)

 - [BeanShell 2.1.1](https://github.com/beanshell/beanshell/releases/tag/2.1.1) - [bsh-2.1.1-src.zip](https://github.com/beanshell/beanshell/releases/download/2.1.1/bsh-2.1.1-src.zip)


### Maven

Beanshell releases are published to [Maven Central](http://central.maven.org/maven2/org/apache-extras/beanshell/bsh/). To use Beanshell with Maven, add this to your `pom.xml`:

```xml
    <dependencies>
       <dependency>
         <groupId>org.apache-extras.beanshell</groupId>
         <artifactId>bsh</artifactId>
         <version>2.1.1</version>
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

For a BeanShell interactive shell you can either use the java command:

```shell
$ java -cp bsh-2.1.1.jar bsh.Interpreter
```

or the supplied helper scripts `bsh` or `bsh.bat` available under the scripts folder.


You will need [Java](http://java.com/) 5 or later installed.

## Build

```shell
$ mvn clean install
```

## Contribute

You are encouraged to raise a Github [Pull Request](https://github.com/beanshell/beanshell/pulls) with any suggested improvements and fixes!

You can also raise an [issue](https://github.com/beanshell/beanshell/issues) for any questions or bugs. Remember, your stacktrace might be particularly useful for others!

Please note, only issues and pull requests made against the development branch merge-fork-beanshell2 will be considered.

## Documentation

For full documentation, see the [BeanShell wiki](https://github.com/beanshell/beanshell/wiki) and the [FAQ](https://github.com/beanshell/beanshell/wiki/FAQ) for frequently asked questions.

The old documentation available at [http://beanshell.org](http://www.beanshell.org/docs.html) may also be useful.

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

## Development road map

Current development effort is focused towards releasing BeanShell 3.0. The following road map serves as a guide to gauge progress to the next release.

 - [x] Merge fork BeanShell2
 - [x] Support for Java 9/10 with illegal access denied
 - [x] Implement varargs
 - [x] Implement try with resources
 - [x] Implement multi-catch
 - [x] Implement interfaces: constants, static and default methods
 - [x] Implement generics parsing
 - [x] Implement final modifier
 - [x] Implement BigInteger/BigDecimal and number coercion
 - [x] Make all current unit tests pass
 - [x] Increase unit tests code coverage 70%
 - [ ] Resolve all outstanding issues and process pull requests
 - [ ] Apply uniform code style and javadocs
 - [ ] Consider feedback from [community discussions](/beanshell/beanshell/labels/discussion)

## Projects using BeanShell

Projects that we know of which use BeanShell. Is your project not listed here? Let us know by submitting an [issue](/beanshell/beanshell/issues).

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

## History

### 2015: Move to github.com

On 2015-09-23, the BeanShell repository moved from https://code.google.com/a/apache-extras.org/p/beanshell/ to its new home on https://github.com/beanshell/beanshell/ as Google Code was been discontinued.

The project adapted an open collaborative approach using [GitHub pull requests](https://github.com/beanshell/beanshell/pulls) and has since grown its committer base beyond the original Apache Extra team.

http://beanshell.org/ remains available for older versions.

### 2012: Move to apache-extras.org

BeanShell was [proposed as an incubator project](https://wiki.apache.org/incubator/BeanShellProposal) to
move to [Apache Software Foundation](http://www.apache.org/). In preparation for this, the codebase
for BeanShell 2.0b4 was donated to ASF by a code grant, and the license changed to
[Apache License, version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

The source code was moved to http://apache-extras.org/ - a project home hosted by Google Code, that was only informally associated with Apache Software Foundation. Many of the BeanShell committers were Apache committers, and thus Apache Extras seemed a natural home.

However the project did not move into the [Apache incubator](http://incubator.apache.org/), and remained at apache-extras.org as an independent project.

In March 2015 Google announced it would discontinue Google Code, which provided the hosting for Apache Extras.

### 2007: Community fork beanshell2

The community forked BeanShell in May 2007 creating the [BeanShell2](https://code.google.com/archive/p/beanshell2/) project hosted on google code. The new fork saw crucial fixes and updates with several releases between 2011 and 2014.

The project moved to [GitHub](/pejobo/beanshell2) in June 2016 after google code was discontinued and is independently maintained.

In August 2017 BeanShell decided to merge all the changes from the BeanShell2 fork back upstream ensuring that no effort was lost during this period.

### 2005: JSR 274: The BeanShell Scripting Language

In 2005 JSR 274 is accepted for officially defining the language but this was never completed.
The current status is dormant as voted by the JCP in June 2011.

[JSR 274: The BeanShell Scripting Language](https://jcp.org/en/jsr/detail?id=274)

### 1999: beanshell.org

BeanShell was originally developed by Patrick Niemeyer at http://beanshell.org/ - distributed as
BeanShell (2.0b4 and earlier) were distributed under
GNU Lesser General Public License (LGPL) and Sun Public License (SPL).

In 2000 the project was [hosted on sourceforge](https://sourceforge.net/projects/beanshell/) which quickly saw interest in the new java scripting language grow.
