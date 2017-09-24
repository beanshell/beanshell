/***
 * ASM: a very small and fast Java bytecode manipulation framework
 * Copyright (c) 2000,2002,2003 INRIA, France Telecom
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holders nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Contact: Eric.Bruneton@rd.francetelecom.com
 *
 * Author: Eric Bruneton
 * with contributions from: Chris Nokleberg
 */
package bsh.org.objectweb.asm;

import java.lang.reflect.Method;

/**
 * A Java type. This class can be used to make it easier to manipulate type
 * and method descriptors.
 */
public class Type {

    /**
     * The sort of the <tt>void</tt> type. See {@link #getSort getSort}.
     */
    public static final int VOID = 0;
    /**
     * The sort of the <tt>boolean</tt> type. See {@link #getSort getSort}.
     */
    public static final int BOOLEAN = 1;
    /**
     * The sort of the <tt>char</tt> type. See {@link #getSort getSort}.
     */
    public static final int CHAR = 2;
    /**
     * The sort of the <tt>byte</tt> type. See {@link #getSort getSort}.
     */
    public static final int BYTE = 3;
    /**
     * The sort of the <tt>short</tt> type. See {@link #getSort getSort}.
     */
    public static final int SHORT = 4;
    /**
     * The sort of the <tt>int</tt> type. See {@link #getSort getSort}.
     */
    public static final int INT = 5;
    /**
     * The sort of the <tt>float</tt> type. See {@link #getSort getSort}.
     */
    public static final int FLOAT = 6;
    /**
     * The sort of the <tt>long</tt> type. See {@link #getSort getSort}.
     */
    public static final int LONG = 7;
    /**
     * The sort of the <tt>double</tt> type. See {@link #getSort getSort}.
     */
    public static final int DOUBLE = 8;
    /**
     * The sort of array reference types. See {@link #getSort getSort}.
     */
    public static final int ARRAY = 9;
    /**
     * The sort of object reference type. See {@link #getSort getSort}.
     */
    public static final int OBJECT = 10;
    /**
     * The <tt>void</tt> type.
     */
    public static final Type VOID_TYPE = new Type(VOID);
    /**
     * The <tt>boolean</tt> type.
     */
    public static final Type BOOLEAN_TYPE = new Type(BOOLEAN);
    /**
     * The <tt>char</tt> type.
     */
    public static final Type CHAR_TYPE = new Type(CHAR);
    /**
     * The <tt>byte</tt> type.
     */
    public static final Type BYTE_TYPE = new Type(BYTE);
    /**
     * The <tt>short</tt> type.
     */
    public static final Type SHORT_TYPE = new Type(SHORT);
    /**
     * The <tt>int</tt> type.
     */
    public static final Type INT_TYPE = new Type(INT);
    /**
     * The <tt>float</tt> type.
     */
    public static final Type FLOAT_TYPE = new Type(FLOAT);
    /**
     * The <tt>long</tt> type.
     */
    public static final Type LONG_TYPE = new Type(LONG);
    /**
     * The <tt>double</tt> type.
     */
    public static final Type DOUBLE_TYPE = new Type(DOUBLE);
    // --------------------------------------------------------------------------
    // Fields
    // --------------------------------------------------------------------------
    /**
     * The sort of this Java type.
     */
    private final int sort;
    /**
     * A buffer containing the descriptor of this Java type.
     * This field is only used for reference types.
     */
    private char[] buf;
    /**
     * The offset of the descriptor of this Java type in {@link #buf buf}.
     * This field is only used for reference types.
     */
    private int off;
    /**
     * The length of the descriptor of this Java type.
     */
    private final int len;

    // --------------------------------------------------------------------------
    // Constructors
    // --------------------------------------------------------------------------
    /**
     * Constructs a primitive type.
     *
     * @param sort
     *            the sort of the primitive type to be constructed.
     */
    private Type(final int sort) {
        this.sort = sort;
        this.len = 1;
    }

    /**
     * Constructs a reference type.
     *
     * @param sort
     *            the sort of the reference type to be constructed.
     * @param buf
     *            a buffer containing the descriptor of the previous type.
     * @param off
     *            the offset of this descriptor in the previous buffer.
     * @param len
     *            the length of this descriptor.
     */
    private Type(final int sort, final char[] buf, final int off,
            final int len) {
        this.sort = sort;
        this.buf = buf;
        this.off = off;
        this.len = len;
    }

    /**
     * Returns the Java type corresponding to the given type descriptor.
     *
     * @param typeDescriptor
     *            a type descriptor.
     * @return the Java type corresponding to the given type descriptor.
     */
    public static Type getType(final String typeDescriptor) {
        return getType(typeDescriptor.toCharArray(), 0);
    }

    /**
     * Returns the Java type corresponding to the given class.
     *
     * @param c
     *            a class.
     * @return the Java type corresponding to the given class.
     */
    public static Type getType(final Class c) {
        if (c.isPrimitive()) {
            if (c == Integer.TYPE)
                return INT_TYPE;
            else if (c == Void.TYPE)
                return VOID_TYPE;
            else if (c == Boolean.TYPE)
                return BOOLEAN_TYPE;
            else if (c == Byte.TYPE)
                return BYTE_TYPE;
            else if (c == Character.TYPE)
                return CHAR_TYPE;
            else if (c == Short.TYPE)
                return SHORT_TYPE;
            else if (c == Double.TYPE)
                return DOUBLE_TYPE;
            else if (c == Float.TYPE)
                return FLOAT_TYPE;
            else
                return LONG_TYPE;
        } else
            return getType(getDescriptor(c));
    }

    /**
     * Returns the Java types corresponding to the argument types of the given
     * method descriptor.
     *
     * @param methodDescriptor
     *            a method descriptor.
     * @return the Java types corresponding to the argument types of the given
     *         method descriptor.
     */
    public static Type[] getArgumentTypes(final String methodDescriptor) {
        final char[] buf = methodDescriptor.toCharArray();
        int off = 1;
        int size = 0;
        while (true) {
            final char car = buf[off++];
            if (car == ')')
                break;
            else if (car == 'L') {
                while (buf[off++] != ';') {}
                ++size;
            } else if (car != '[')
                ++size;
        }
        final Type[] args = new Type[size];
        off = 1;
        size = 0;
        while (buf[off] != ')') {
            args[size] = getType(buf, off);
            off += args[size].len;
            size += 1;
        }
        return args;
    }

    /**
     * Returns the Java types corresponding to the argument types of the given
     * method.
     *
     * @param method
     *            a method.
     * @return the Java types corresponding to the argument types of the given
     *         method.
     */
    public static Type[] getArgumentTypes(final Method method) {
        final Class[] classes = method.getParameterTypes();
        final Type[] types = new Type[classes.length];
        for (int i = classes.length - 1; i >= 0; --i)
            types[i] = getType(classes[i]);
        return types;
    }

    /**
     * Returns the Java type corresponding to the return type of the given
     * method descriptor.
     *
     * @param methodDescriptor
     *            a method descriptor.
     * @return the Java type corresponding to the return type of the given
     *         method descriptor.
     */
    public static Type getReturnType(final String methodDescriptor) {
        final char[] buf = methodDescriptor.toCharArray();
        return getType(buf, methodDescriptor.indexOf(')') + 1);
    }

    /**
     * Returns the Java type corresponding to the return type of the given
     * method.
     *
     * @param method
     *            a method.
     * @return the Java type corresponding to the return type of the given
     *         method.
     */
    public static Type getReturnType(final Method method) {
        return getType(method.getReturnType());
    }

    /**
     * Returns the Java type corresponding to the given type descriptor.
     *
     * @param buf
     *            a buffer containing a type descriptor.
     * @param off
     *            the offset of this descriptor in the previous buffer.
     * @return the Java type corresponding to the given type descriptor.
     */
    private static Type getType(final char[] buf, final int off) {
        int len;
        switch (buf[off]) {
            case 'V':
                return VOID_TYPE;
            case 'Z':
                return BOOLEAN_TYPE;
            case 'C':
                return CHAR_TYPE;
            case 'B':
                return BYTE_TYPE;
            case 'S':
                return SHORT_TYPE;
            case 'I':
                return INT_TYPE;
            case 'F':
                return FLOAT_TYPE;
            case 'J':
                return LONG_TYPE;
            case 'D':
                return DOUBLE_TYPE;
            case '[':
                len = 1;
                while (buf[off + len] == '[')
                    ++len;
                if (buf[off + len] == 'L') {
                    ++len;
                    while (buf[off + len] != ';')
                        ++len;
                }
                return new Type(ARRAY, buf, off, len + 1);
            // case 'L':
            default:
                len = 1;
                while (buf[off + len] != ';')
                    ++len;
                return new Type(OBJECT, buf, off, len + 1);
        }
    }

    // --------------------------------------------------------------------------
    // Accessors
    // --------------------------------------------------------------------------
    /**
     * Returns the sort of this Java type.
     *
     * @return {@link #VOID VOID}, {@link #BOOLEAN BOOLEAN}, {@link #CHAR CHAR},
     *         {@link #BYTE BYTE}, {@link #SHORT SHORT}, {@link #INT INT},
     *         {@link
     *         #FLOAT FLOAT}, {@link #LONG LONG}, {@link #DOUBLE DOUBLE}, {@link
     *         #ARRAY ARRAY} or {@link #OBJECT OBJECT}.
     */
    public int getSort() {
        return this.sort;
    }

    /**
     * Returns the number of dimensions of this array type.
     * This method should only be used for an array type.
     *
     * @return the number of dimensions of this array type.
     */
    public int getDimensions() {
        int i = 1;
        while (this.buf[this.off + i] == '[')
            ++i;
        return i;
    }

    /**
     * Returns the type of the elements of this array type.
     * This method should only be used for an array type.
     *
     * @return Returns the type of the elements of this array type.
     */
    public Type getElementType() {
        return getType(this.buf, this.off + this.getDimensions());
    }

    /**
     * Returns the name of the class corresponding to this object type.
     * This method should only be used for an object type.
     *
     * @return the fully qualified name of the class corresponding to this
     *         object
     *         type.
     */
    public String getClassName() {
        return new String(this.buf, this.off + 1, this.len - 2).replace('/',
                '.');
    }

    /**
     * Returns the internal name of the class corresponding to this object type.
     * The internal name of a class is its fully qualified name, where '.' are
     * replaced by '/'. * This method should only be used for an object type.
     *
     * @return the internal name of the class corresponding to this object type.
     */
    public String getInternalName() {
        return new String(this.buf, this.off + 1, this.len - 2);
    }

    // --------------------------------------------------------------------------
    // Conversion to type descriptors
    // --------------------------------------------------------------------------
    /**
     * Returns the descriptor corresponding to this Java type.
     *
     * @return the descriptor corresponding to this Java type.
     */
    public String getDescriptor() {
        final StringBuffer buf = new StringBuffer();
        this.getDescriptor(buf);
        return buf.toString();
    }

    /**
     * Returns the descriptor corresponding to the given argument and return
     * types.
     *
     * @param returnType
     *            the return type of the method.
     * @param argumentTypes
     *            the argument types of the method.
     * @return the descriptor corresponding to the given argument and return
     *         types.
     */
    public static String getMethodDescriptor(final Type returnType,
            final Type[] argumentTypes) {
        final StringBuffer buf = new StringBuffer();
        buf.append('(');
        for (final Type argumentType : argumentTypes)
            argumentType.getDescriptor(buf);
        buf.append(')');
        returnType.getDescriptor(buf);
        return buf.toString();
    }

    /**
     * Appends the descriptor corresponding to this Java type to the given
     * string
     * buffer.
     *
     * @param buf
     *            the string buffer to which the descriptor must be appended.
     */
    private void getDescriptor(final StringBuffer buf) {
        switch (this.sort) {
            case VOID:
                buf.append('V');
                return;
            case BOOLEAN:
                buf.append('Z');
                return;
            case CHAR:
                buf.append('C');
                return;
            case BYTE:
                buf.append('B');
                return;
            case SHORT:
                buf.append('S');
                return;
            case INT:
                buf.append('I');
                return;
            case FLOAT:
                buf.append('F');
                return;
            case LONG:
                buf.append('J');
                return;
            case DOUBLE:
                buf.append('D');
                return;
            // case ARRAY:
            // case OBJECT:
            default:
                buf.append(this.buf, this.off, this.len);
        }
    }

    // --------------------------------------------------------------------------
    // Direct conversion from classes to type descriptors,
    // without intermediate Type objects
    // --------------------------------------------------------------------------
    /**
     * Returns the internal name of the given class. The internal name of a
     * class
     * is its fully qualified name, where '.' are replaced by '/'.
     *
     * @param c
     *            an object class.
     * @return the internal name of the given class.
     */
    public static String getInternalName(final Class c) {
        return c.getName().replace('.', '/');
    }

    /**
     * Returns the descriptor corresponding to the given Java type.
     *
     * @param c
     *            an object class, a primitive class or an array class.
     * @return the descriptor corresponding to the given class.
     */
    public static String getDescriptor(final Class c) {
        final StringBuffer buf = new StringBuffer();
        getDescriptor(buf, c);
        return buf.toString();
    }

    /**
     * Returns the descriptor corresponding to the given method.
     *
     * @param m
     *            a {@link Method Method} object.
     * @return the descriptor of the given method.
     */
    public static String getMethodDescriptor(final Method m) {
        final Class[] parameters = m.getParameterTypes();
        final StringBuffer buf = new StringBuffer();
        buf.append('(');
        for (final Class parameter : parameters)
            getDescriptor(buf, parameter);
        buf.append(')');
        getDescriptor(buf, m.getReturnType());
        return buf.toString();
    }

    /**
     * Appends the descriptor of the given class to the given string buffer.
     *
     * @param buf
     *            the string buffer to which the descriptor must be appended.
     * @param c
     *            the class whose descriptor must be computed.
     */
    private static void getDescriptor(final StringBuffer buf, final Class c) {
        Class d = c;
        while (true)
            if (d.isPrimitive()) {
                char car;
                if (d == Integer.TYPE)
                    car = 'I';
                else if (d == Void.TYPE)
                    car = 'V';
                else if (d == Boolean.TYPE)
                    car = 'Z';
                else if (d == Byte.TYPE)
                    car = 'B';
                else if (d == Character.TYPE)
                    car = 'C';
                else if (d == Short.TYPE)
                    car = 'S';
                else if (d == Double.TYPE)
                    car = 'D';
                else if (d == Float.TYPE)
                    car = 'F';
                else
                    car = 'J';
                buf.append(car);
                return;
            } else if (d.isArray()) {
                buf.append('[');
                d = d.getComponentType();
            } else {
                buf.append('L');
                final String name = d.getName();
                final int len = name.length();
                for (int i = 0; i < len; ++i) {
                    final char car = name.charAt(i);
                    buf.append(car == '.' ? '/' : car);
                }
                buf.append(';');
                return;
            }
    }

    // --------------------------------------------------------------------------
    // Corresponding size and opcodes
    // --------------------------------------------------------------------------
    /**
     * Returns the size of values of this type.
     *
     * @return the size of values of this type, i.e., 2 for <tt>long</tt> and
     *         <tt>double</tt>, and 1 otherwise.
     */
    public int getSize() {
        return this.sort == LONG || this.sort == DOUBLE ? 2 : 1;
    }

    /**
     * Returns a JVM instruction opcode adapted to this Java type.
     *
     * @param opcode
     *            a JVM instruction opcode. This opcode must be one of ILOAD,
     *            ISTORE, IALOAD, IASTORE, IADD, ISUB, IMUL, IDIV, IREM, INEG,
     *            ISHL,
     *            ISHR, IUSHR, IAND, IOR, IXOR and IRETURN.
     * @return an opcode that is similar to the given opcode, but adapted to
     *         this
     *         Java type. For example, if this type is <tt>float</tt> and
     *         <tt>opcode</tt> is IRETURN, this method returns FRETURN.
     */
    public int getOpcode(final int opcode) {
        if (opcode == Constants.IALOAD || opcode == Constants.IASTORE)
            switch (this.sort) {
                case BOOLEAN:
                case BYTE:
                    return opcode + 5;
                case CHAR:
                    return opcode + 6;
                case SHORT:
                    return opcode + 7;
                case INT:
                    return opcode;
                case FLOAT:
                    return opcode + 2;
                case LONG:
                    return opcode + 1;
                case DOUBLE:
                    return opcode + 3;
                // case ARRAY:
                // case OBJECT:
                default:
                    return opcode + 4;
            } else
            switch (this.sort) {
                case VOID:
                    return opcode + 5;
                case BOOLEAN:
                case CHAR:
                case BYTE:
                case SHORT:
                case INT:
                    return opcode;
                case FLOAT:
                    return opcode + 2;
                case LONG:
                    return opcode + 1;
                case DOUBLE:
                    return opcode + 3;
                // case ARRAY:
                // case OBJECT:
                default:
                    return opcode + 4;
            }
    }

    // --------------------------------------------------------------------------
    // Equals and hashcode
    // --------------------------------------------------------------------------
    /** {@inheritDoc} */
    @Override
    public boolean equals(final Object o) {
        if (this == o)
            return true;
        if (o == null || !(o instanceof Type))
            return false;
        final Type t = (Type) o;
        if (this.sort != t.sort)
            return false;
        if (this.sort == Type.OBJECT || this.sort == Type.ARRAY) {
            if (this.len != t.len)
                return false;
            for (int i = this.off, j = t.off, end = i + this.len;
                    i < end;
                    i++, j++)
                if (this.buf[i] != t.buf[j])
                    return false;
        }
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        int hc = 13 * this.sort;
        if (this.sort == Type.OBJECT || this.sort == Type.ARRAY)
            for (int i = this.off, end = i + this.len; i < end; i++)
                hc = 17 * (hc + this.buf[i]);
        return hc;
    }
}
