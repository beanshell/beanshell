// ASM: a very small and fast Java bytecode manipulation framework
// Copyright (c) 2000-2011 INRIA, France Telecom
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions
// are met:
// 1. Redistributions of source code must retain the above copyright
//    notice, this list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright
//    notice, this list of conditions and the following disclaimer in the
//    documentation and/or other materials provided with the distribution.
// 3. Neither the name of the copyright holders nor the names of its
//    contributors may be used to endorse or promote products derived from
//    this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
// THE POSSIBILITY OF SUCH DAMAGE.
package bsh.org.objectweb.asm;

/**
 * A visitor to visit a Java class. The methods of this class must be called in the following order:
 * <tt>visit</tt> [ <tt>visitSource</tt> ] [ <tt>visitModule</tt> ][ <tt>visitNestHost</tt> ][
 * <tt>visitOuterClass</tt> ] ( <tt>visitAnnotation</tt> | <tt>visitTypeAnnotation</tt> |
 * <tt>visitAttribute</tt> )* ( <tt>visitNestMember</tt> | <tt>visitInnerClass</tt> |
 * <tt>visitField</tt> | <tt>visitMethod</tt> )* <tt>visitEnd</tt>.
 *
 * @author Eric Bruneton
 */
public abstract class ClassVisitor {

  /**
   * The ASM API version implemented by this visitor. The value of this field must be one of {@link
   * Opcodes#ASM4}, {@link Opcodes#ASM5}, {@link Opcodes#ASM6} or {@link Opcodes#ASM7_EXPERIMENTAL}.
   */
  protected final int api;

  /** The class visitor to which this visitor must delegate method calls. May be null. */
  protected ClassVisitor cv;

  /**
   * Constructs a new {@link ClassVisitor}.
   *
   * @param api the ASM API version implemented by this visitor. Must be one of {@link
   *     Opcodes#ASM4}, {@link Opcodes#ASM5}, {@link Opcodes#ASM6} or {@link
   *     Opcodes#ASM7_EXPERIMENTAL}.
   */
  public ClassVisitor(final int api) {
    this(api, null);
  }

  /**
   * Constructs a new {@link ClassVisitor}.
   *
   * @param api the ASM API version implemented by this visitor. Must be one of {@link
   *     Opcodes#ASM4}, {@link Opcodes#ASM5}, {@link Opcodes#ASM6} or {@link
   *     Opcodes#ASM7_EXPERIMENTAL}.
   * @param classVisitor the class visitor to which this visitor must delegate method calls. May be
   *     null.
   */
  public ClassVisitor(final int api, final ClassVisitor classVisitor) {
    if (api != Opcodes.ASM6
        && api != Opcodes.ASM5
        && api != Opcodes.ASM4) {
      throw new IllegalArgumentException();
    }
    this.api = api;
    this.cv = classVisitor;
  }

  /**
   * Visits the header of the class.
   *
   * @param version the class version. The minor version is stored in the 16 most significant bits,
   *     and the major version in the 16 least significant bits.
   * @param access the class's access flags (see {@link Opcodes}). This parameter also indicates if
   *     the class is deprecated.
   * @param name the internal name of the class (see {@link Type#getInternalName()}).
   * @param signature the signature of this class. May be <tt>null</tt> if the class is not a
   *     generic one, and does not extend or implement generic classes or interfaces.
   * @param superName the internal of name of the super class (see {@link Type#getInternalName()}).
   *     For interfaces, the super class is {@link Object}. May be <tt>null</tt>, but only for the
   *     {@link Object} class.
   * @param interfaces the internal names of the class's interfaces (see {@link
   *     Type#getInternalName()}). May be <tt>null</tt>.
   */
  public void visit(
      final int version,
      final int access,
      final String name,
      final String signature,
      final String superName,
      final String[] interfaces) {
    if (cv != null) {
      cv.visit(version, access, name, signature, superName, interfaces);
    }
  }

  /**
   * Visits the source of the class.
   *
   * @param source the name of the source file from which the class was compiled. May be
   *     <tt>null</tt>.
   * @param debug additional debug information to compute the correspondence between source and
   *     compiled elements of the class. May be <tt>null</tt>.
   */
  public void visitSource(final String source, final String debug) {
    if (cv != null) {
      cv.visitSource(source, debug);
    }
  }

  /**
   * Visits the enclosing class of the class. This method must be called only if the class has an
   * enclosing class.
   *
   * @param owner internal name of the enclosing class of the class.
   * @param name the name of the method that contains the class, or <tt>null</tt> if the class is
   *     not enclosed in a method of its enclosing class.
   * @param descriptor the descriptor of the method that contains the class, or <tt>null</tt> if the
   *     class is not enclosed in a method of its enclosing class.
   */
  public void visitOuterClass(final String owner, final String name, final String descriptor) {
    if (cv != null) {
      cv.visitOuterClass(owner, name, descriptor);
    }
  }

  /**
   * Visits a non standard attribute of the class.
   *
   * @param attribute an attribute.
   */
  public void visitAttribute(final Attribute attribute) {
    if (cv != null) {
      cv.visitAttribute(attribute);
    }
  }

  /**
   * Visits information about an inner class. This inner class is not necessarily a member of the
   * class being visited.
   *
   * @param name the internal name of an inner class (see {@link Type#getInternalName()}).
   * @param outerName the internal name of the class to which the inner class belongs (see {@link
   *     Type#getInternalName()}). May be <tt>null</tt> for not member classes.
   * @param innerName the (simple) name of the inner class inside its enclosing class. May be
   *     <tt>null</tt> for anonymous inner classes.
   * @param access the access flags of the inner class as originally declared in the enclosing
   *     class.
   */
  public void visitInnerClass(
      final String name, final String outerName, final String innerName, final int access) {
    if (cv != null) {
      cv.visitInnerClass(name, outerName, innerName, access);
    }
  }

  /**
   * Visits a field of the class.
   *
   * @param access the field's access flags (see {@link Opcodes}). This parameter also indicates if
   *     the field is synthetic and/or deprecated.
   * @param name the field's name.
   * @param descriptor the field's descriptor (see {@link Type}).
   * @param signature the field's signature. May be <tt>null</tt> if the field's type does not use
   *     generic types.
   * @param value the field's initial value. This parameter, which may be <tt>null</tt> if the field
   *     does not have an initial value, must be an {@link Integer}, a {@link Float}, a {@link
   *     Long}, a {@link Double} or a {@link String} (for <tt>int</tt>, <tt>float</tt>,
   *     <tt>long</tt> or <tt>String</tt> fields respectively). <i>This parameter is only used for
   *     static fields</i>. Its value is ignored for non static fields, which must be initialized
   *     through bytecode instructions in constructors or methods.
   * @return a visitor to visit field annotations and attributes, or <tt>null</tt> if this class
   *     visitor is not interested in visiting these annotations and attributes.
   */
  public FieldVisitor visitField(
      final int access,
      final String name,
      final String descriptor,
      final String signature,
      final Object value) {
    if (cv != null) {
      return cv.visitField(access, name, descriptor, signature, value);
    }
    return null;
  }

  /**
   * Visits a method of the class. This method <i>must</i> return a new {@link MethodVisitor}
   * instance (or <tt>null</tt>) each time it is called, i.e., it should not return a previously
   * returned visitor.
   *
   * @param access the method's access flags (see {@link Opcodes}). This parameter also indicates if
   *     the method is synthetic and/or deprecated.
   * @param name the method's name.
   * @param descriptor the method's descriptor (see {@link Type}).
   * @param signature the method's signature. May be <tt>null</tt> if the method parameters, return
   *     type and exceptions do not use generic types.
   * @param exceptions the internal names of the method's exception classes (see {@link
   *     Type#getInternalName()}). May be <tt>null</tt>.
   * @return an object to visit the byte code of the method, or <tt>null</tt> if this class visitor
   *     is not interested in visiting the code of this method.
   */
  public MethodVisitor visitMethod(
      final int access,
      final String name,
      final String descriptor,
      final String signature,
      final String[] exceptions) {
    if (cv != null) {
      return cv.visitMethod(access, name, descriptor, signature, exceptions);
    }
    return null;
  }

  /**
   * Visits the end of the class. This method, which is the last one to be called, is used to inform
   * the visitor that all the fields and methods of the class have been visited.
   */
  public void visitEnd() {
    if (cv != null) {
      cv.visitEnd();
    }
  }
}
