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

import java.util.Hashtable;

/**
 * The Class Modifiers.
 *
 * @author Pat Niemeyer (pat@pat.net)
 *
 * Note: which of these things should be checked at parse time vs. run time?
 */
public class Modifiers implements java.io.Serializable {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;
    /** The Constant FIELD. */
    public static final int CLASS = 0, METHOD = 1, FIELD = 2;
    /** The modifiers. */
    Hashtable modifiers;

    /**
     * Adds the modifier.
     *
     * @param context
     *            is METHOD or FIELD
     * @param name
     *            the name
     */
    public void addModifier(final int context, final String name) {
        if (this.modifiers == null)
            this.modifiers = new Hashtable();
        final Object existing = this.modifiers.put(name,
                Void.TYPE/* arbitrary flag */);
        if (existing != null)
            throw new IllegalStateException("Duplicate modifier: " + name);
        int count = 0;
        if (this.hasModifier("private"))
            ++count;
        if (this.hasModifier("protected"))
            ++count;
        if (this.hasModifier("public"))
            ++count;
        if (count > 1)
            throw new IllegalStateException(
                    "public/private/protected cannot be used in combination.");
        switch (context) {
            case CLASS:
                this.validateForClass();
                break;
            case METHOD:
                this.validateForMethod();
                break;
            case FIELD:
                this.validateForField();
                break;
        }
    }

    /**
     * Checks for modifier.
     *
     * @param name
     *            the name
     * @return true, if successful
     */
    public boolean hasModifier(final String name) {
        if (this.modifiers == null)
            this.modifiers = new Hashtable();
        return this.modifiers.get(name) != null;
    }

    // could refactor these a bit
    /**
     * Validate for method.
     */
    private void validateForMethod() {
        this.insureNo("volatile", "Method");
        this.insureNo("transient", "Method");
    }

    /**
     * Validate for field.
     */
    private void validateForField() {
        this.insureNo("synchronized", "Variable");
        this.insureNo("native", "Variable");
        this.insureNo("abstract", "Variable");
    }

    /**
     * Validate for class.
     */
    private void validateForClass() {
        this.validateForMethod(); // volatile, transient
        this.insureNo("native", "Class");
        this.insureNo("synchronized", "Class");
    }

    /**
     * Insure no.
     *
     * @param modifier
     *            the modifier
     * @param context
     *            the context
     */
    private void insureNo(final String modifier, final String context) {
        if (this.hasModifier(modifier))
            throw new IllegalStateException(
                    context + " cannot be declared '" + modifier + "'");
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "Modifiers: " + this.modifiers;
    }
}
