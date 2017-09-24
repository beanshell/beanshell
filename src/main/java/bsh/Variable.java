/*****************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one                *
 * or more contributor license agreements.  See the NOTICE file              *
 * distributed with this work for additional information                     *
 * regarding copyright ownership.  The ASF licenses this file                *
 * to you under the Apache License, Version 2.0 (the                         *
 * "License"); you may not use this file except in compliance                *
 * with the License.  You may obtain a copy of the License at                *
 *                                                                           *
 *     http://www.apache.org/licenses/LICENSE-2.0                            *
 *                                                                           *
 * Unless required by applicable law or agreed to in writing,                *
 * software distributed under the License is distributed on an               *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY                    *
 * KIND, either express or implied.  See the License for the                 *
 * specific language governing permissions and limitations                   *
 * under the License.                                                        *
 *                                                                           *
 *                                                                           *
 * This file is part of the BeanShell Java Scripting distribution.           *
 * Documentation and updates may be found at http://www.beanshell.org/       *
 * Patrick Niemeyer (pat@pat.net)                                            *
 * Author of Learning Java, O'Reilly & Associates                            *
 *                                                                           *
 *****************************************************************************/
package bsh;

/**
 * The Class Variable.
 */
public class Variable implements java.io.Serializable {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;
    /** The Constant ASSIGNMENT. */
    static final int DECLARATION = 0, ASSIGNMENT = 1;
    /** A null type means an untyped variable. */
    String name;
    /** The type. */
    Class type = null;
    /** The type descriptor. */
    String typeDescriptor;
    /** The value. */
    Object value;
    /** The modifiers. */
    Modifiers modifiers;
    /** The lhs. */
    LHS lhs;

    /**
     * Instantiates a new variable.
     *
     * @param name
     *            the name
     * @param type
     *            the type
     * @param lhs
     *            the lhs
     */
    Variable(final String name, final Class type, final LHS lhs) {
        this.name = name;
        this.lhs = lhs;
        this.type = type;
    }

    /**
     * Instantiates a new variable.
     *
     * @param name
     *            the name
     * @param value
     *            the value
     * @param modifiers
     *            the modifiers
     * @throws UtilEvalError
     *             the util eval error
     */
    Variable(final String name, final Object value, final Modifiers modifiers)
            throws UtilEvalError {
        this(name, (Class) null/* type */, value, modifiers);
    }

    /**
     * This constructor is used in class generation.
     *
     * @param name
     *            the name
     * @param typeDescriptor
     *            the type descriptor
     * @param value
     *            the value
     * @param modifiers
     *            the modifiers
     * @throws UtilEvalError
     *             the util eval error
     */
    Variable(final String name, final String typeDescriptor, final Object value,
            final Modifiers modifiers) throws UtilEvalError {
        this(name, (Class) null/* type */, value, modifiers);
        this.typeDescriptor = typeDescriptor;
    }

    /**
     * Instantiates a new variable.
     *
     * @param name
     *            the name
     * @param type
     *            the type
     * @param value
     *            may be null if this
     * @param modifiers
     *            the modifiers
     * @throws UtilEvalError
     *             the util eval error
     */
    Variable(final String name, final Class type, final Object value,
            final Modifiers modifiers) throws UtilEvalError {
        this.name = name;
        this.type = type;
        this.modifiers = modifiers;
        this.setValue(value, DECLARATION);
    }

    /**
     * Set the value of the typed variable.
     *
     * @param value
     *            should be an object or wrapped bsh Primitive type.
     *            if value is null the appropriate default value will be set for
     *            the
     *            type: e.g. false for boolean, zero for integer types.
     * @param context
     *            the context
     * @throws UtilEvalError
     *             the util eval error
     */
    public void setValue(final Object value, final int context)
            throws UtilEvalError {
        // check this.value
        if (this.hasModifier("final") && this.value != null)
            throw new UtilEvalError("Final variable, can't re-assign.");
        this.value = value;
        if (this.value == null)
            this.value = Primitive.getDefaultValue(this.type);
        if (this.lhs != null) {
            this.value = this.lhs.assign(Primitive.unwrap(value),
                    false/* strictjava */);
            return;
        }
        // TODO: should add isJavaCastable() test for strictJava
        // (as opposed to isJavaAssignable())
        if (this.type != null)
            this.value = Types.castObject(value, this.type,
                    context == DECLARATION ? Types.CAST : Types.ASSIGNMENT);
    }

    /**
     * Gets the value.
     *
     * @return the value
     * @throws UtilEvalError
     *             the util eval error
     *
     * Note: UtilEvalError here comes from lhs.getValue().
     * A Variable can represent an LHS for the case of an imported class or
     * object field.
     */
    Object getValue() throws UtilEvalError {
        if (this.lhs != null)
            return this.type == null ? this.lhs.getValue()
                    : Primitive.wrap(this.lhs.getValue(), this.type);
        return this.value;
    }

    /**
     * A type of null means loosely typed variable.
     *
     * @return the type
     */
    public Class getType() {
        return this.type;
    }

    /**
     * Gets the type descriptor.
     *
     * @return the type descriptor
     */
    public String getTypeDescriptor() {
        return this.typeDescriptor;
    }

    /**
     * Gets the modifiers.
     *
     * @return the modifiers
     */
    public Modifiers getModifiers() {
        return this.modifiers;
    }

    /**
     * Gets the name.
     *
     * @return the name
     */
    public String getName() {
        return this.name;
    }

    /**
     * Checks for modifier.
     *
     * @param name
     *            the name
     * @return true, if successful
     */
    public boolean hasModifier(final String name) {
        return this.modifiers != null && this.modifiers.hasModifier(name);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "Variable: " + super.toString() + " " + this.name + ", type:"
                + this.type + ", value:" + this.value + ", lhs = " + this.lhs;
    }
}
