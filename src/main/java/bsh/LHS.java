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

import java.lang.reflect.Field;

/**
 * An LHS is a wrapper for an variable, field, or property. It ordinarily
 * holds the "left hand side" of an assignment and may be either resolved to
 * a value or assigned a value.
 * <p>
 *
 * There is one special case here termed METHOD_EVAL where the LHS is used
 * in an intermediate evaluation of a chain of suffixes and wraps a method
 * invocation. In this case it may only be resolved to a value and cannot be
 * assigned. (You can't assign a value to the result of a method call e.g.
 * "foo() = 5;").
 * <p>
 */
class LHS implements ParserConstants, java.io.Serializable {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;
    /** The name space. */
    NameSpace nameSpace;
    /** The assignment should be to a local variable. */
    boolean localVar;
    /**
     * Identifiers for the various types of LHS.
     */
    static final int VARIABLE = 0, FIELD = 1, PROPERTY = 2, INDEX = 3,
            METHOD_EVAL = 4;
    /** The type. */
    int type;
    /** The var name. */
    String varName;
    /** The prop name. */
    String propName;
    /** The field. */
    Field field;
    /** The object. */
    Object object;
    /** The index. */
    int index;

    /**
     * Instantiates a new lhs.
     *
     * @param nameSpace
     *            the name space
     * @param varName
     *            the var name
     * @param localVar
     *            if true the variable is set directly in the This
     *            reference's local scope. If false recursion to look for the
     *            variable
     *            definition in parent's scope is allowed. (e.g. the default
     *            case for
     *            undefined vars going to global).
     */
    LHS(final NameSpace nameSpace, final String varName,
            final boolean localVar) {
        this.type = VARIABLE;
        this.localVar = localVar;
        this.varName = varName;
        this.nameSpace = nameSpace;
    }

    /**
     * Static field LHS Constructor.
     * This simply calls Object field constructor with null object.
     *
     * @param field
     *            the field
     */
    LHS(final Field field) {
        this.type = FIELD;
        this.object = null;
        this.field = field;
    }

    /**
     * Object field LHS Constructor.
     *
     * @param object
     *            the object
     * @param field
     *            the field
     */
    LHS(final Object object, final Field field) {
        if (object == null)
            throw new NullPointerException("constructed empty LHS");
        this.type = FIELD;
        this.object = object;
        this.field = field;
    }

    /**
     * Object property LHS Constructor.
     *
     * @param object
     *            the object
     * @param propName
     *            the prop name
     */
    LHS(final Object object, final String propName) {
        if (object == null)
            throw new NullPointerException("constructed empty LHS");
        this.type = PROPERTY;
        this.object = object;
        this.propName = propName;
    }

    /**
     * Array index LHS Constructor.
     *
     * @param array
     *            the array
     * @param index
     *            the index
     */
    LHS(final Object array, final int index) {
        if (array == null)
            throw new NullPointerException("constructed empty LHS");
        this.type = INDEX;
        this.object = array;
        this.index = index;
    }

    /**
     * Gets the value.
     *
     * @return the value
     * @throws UtilEvalError
     *             the util eval error
     */
    public Object getValue() throws UtilEvalError {
        if (this.type == VARIABLE)
            return this.nameSpace.getVariableOrProperty(this.varName, null);
        // return nameSpace.getVariable(varName);
        if (this.type == FIELD)
            try {
                final Object o = this.field.get(this.object);
                return Primitive.wrap(o, this.field.getType());
            } catch (final IllegalAccessException e2) {
                throw new UtilEvalError("Can't read field: " + this.field);
            }
        if (this.type == PROPERTY) {
            // return the raw type here... we don't know what it's supposed
            // to be...
            final CollectionManager cm = CollectionManager
                    .getCollectionManager();
            if (cm.isMap(this.object))
                return cm.getFromMap(this.object/* map */, this.propName);
            else
                try {
                    return Reflect.getObjectProperty(this.object,
                            this.propName);
                } catch (final ReflectError e) {
                    Interpreter.debug(e.getMessage());
                    throw new UtilEvalError(
                            "No such property: " + this.propName);
                }
        }
        if (this.type == INDEX)
            try {
                return Reflect.getIndex(this.object, this.index);
            } catch (final Exception e) {
                throw new UtilEvalError("Array access: " + e);
            }
        throw new InterpreterError("LHS type");
    }

    /**
     * Assign a value to the LHS.
     *
     * @param val
     *            the val
     * @param strictJava
     *            the strict java
     * @return the object
     * @throws UtilEvalError
     *             the util eval error
     */
    public Object assign(final Object val, final boolean strictJava)
            throws UtilEvalError {
        if (this.type == VARIABLE) {
            // Set the variable in namespace according to localVar flag
            if (this.localVar)
                this.nameSpace.setLocalVariableOrProperty(this.varName, val,
                        strictJava);
            else
                this.nameSpace.setVariableOrProperty(this.varName, val,
                        strictJava);
        } else if (this.type == FIELD)
            try {
                // This should probably be in Reflect.java
                ReflectManager.RMSetAccessible(this.field);
                this.field.set(this.object, Primitive.unwrap(val));
                return val;
            } catch (final NullPointerException e) {
                throw new UtilEvalError("LHS (" + this.field.getName()
                        + ") not a static field.");
            } catch (final IllegalAccessException e2) {
                throw new UtilEvalError("LHS (" + this.field.getName()
                        + ") can't access field: " + e2);
            } catch (final IllegalArgumentException e3) {
                final String type = val instanceof Primitive
                        ? ((Primitive) val).getType().getName()
                        : val.getClass().getName();
                throw new UtilEvalError("Argument type mismatch. "
                        + (val == null ? "null" : type)
                        + " not assignable to field " + this.field.getName());
            }
        else if (this.type == PROPERTY) {
            final CollectionManager cm = CollectionManager
                    .getCollectionManager();
            if (cm.isMap(this.object))
                cm.putInMap(this.object/* map */, this.propName,
                        Primitive.unwrap(val));
            else
                try {
                    Reflect.setObjectProperty(this.object, this.propName, val);
                } catch (final ReflectError e) {
                    Interpreter.debug("Assignment: " + e.getMessage());
                    throw new UtilEvalError(
                            "No such property: " + this.propName);
                }
        } else if (this.type == INDEX)
            try {
                Reflect.setIndex(this.object, this.index, val);
            } catch (final UtilTargetError e1) { // pass along target error
                throw e1;
            } catch (final Exception e) {
                throw new UtilEvalError("Assignment: " + e.getMessage());
            }
        else
            throw new InterpreterError("unknown lhs");
        return val;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "LHS: "
                + (this.field != null ? "field = " + this.field.toString() : "")
                + (this.varName != null ? " varName = " + this.varName : "")
                + (this.nameSpace != null
                        ? " nameSpace = " + this.nameSpace.toString()
                        : "");
    }
}
