# Bundled OW2 ASM

This directory contains the complete sources of `org.ow2.asm:asm:9.10.1`,
including its signature package. BeanShell bundles ASM under
`bsh.org.objectweb.asm` to keep its bytecode generator independent of ASM
versions used by host applications and scripts.

Source archive:
https://repo.maven.apache.org/maven2/org/ow2/asm/asm/9.10.1/asm-9.10.1-sources.jar

SHA-256: `cb29bf42b4008972c566944d377192aa1b2d90275b9cfa9a7fa86de669fde11c`

The Java sources and `package.html` files match that archive with these three
literal replacements, applied in order:

1. `org.objectweb.asm` to `bsh.org.objectweb.asm`
2. `org/objectweb/asm` to `bsh/org/objectweb/asm`
3. `@Deprecated(forRemoval = false)` to `@Deprecated` for compilation on JDK 8

Retain the upstream license headers and `LICENSE.txt` when updating. The
complete core includes the class reader used internally by the writer to
rewrite long jumps and stack map frames. BeanShell continues to generate
Java 8 class files; updating ASM does not change that target.
