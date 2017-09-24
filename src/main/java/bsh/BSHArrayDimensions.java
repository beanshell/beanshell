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

import java.lang.reflect.Array;

/**
 * The name of this class is somewhat misleading. This covers both the case
 * where there is an array initializer and
 */
class BSHArrayDimensions extends SimpleNode {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;
    /** The base type. */
    public Class baseType;
    /** The num defined dims. */
    public int numDefinedDims;
    /** The num undefined dims. */
    public int numUndefinedDims;
    /**
     * The Length in each defined dimension. This value set by the eval()
     * Since the values can come from Expressions we should be re-eval()d each
     * time.
     */
    public int[] definedDimensions;

    /**
     * Instantiates a new BSH array dimensions.
     *
     * @param id
     *            the id
     */
    BSHArrayDimensions(final int id) {
        super(id);
    }

    /**
     * Adds the defined dimension.
     */
    public void addDefinedDimension() {
        this.numDefinedDims++;
    }

    /**
     * Adds the undefined dimension.
     */
    public void addUndefinedDimension() {
        this.numUndefinedDims++;
    }

    /**
     * Eval.
     *
     * @param type
     *            the type
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     * @return the object
     * @throws EvalError
     *             the eval error
     */
    public Object eval(final Class type, final CallStack callstack,
            final Interpreter interpreter) throws EvalError {
        if (Interpreter.DEBUG)
            Interpreter.debug("array base type = " + type);
        this.baseType = type;
        return this.eval(callstack, interpreter);
    }

    /**
     * Evaluate the structure of the array in one of two ways:
     *
     * a) an initializer exists, evaluate it and return
     * the fully constructed array object, also record the dimensions
     * of that array
     *
     * b) evaluate and record the lengths in each dimension and
     * return void.
     *
     * The structure of the array dims is maintained in dimensions.
     *
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     * @return the object
     * @throws EvalError
     *             the eval error
     */
    @Override
    public Object eval(final CallStack callstack, final Interpreter interpreter)
            throws EvalError {
        final SimpleNode child = (SimpleNode) this.jjtGetChild(0);
        /*
         * Child is array initializer. Evaluate it and fill in the
         * dimensions it returns. Initialized arrays are always fully defined
         * (no undefined dimensions to worry about).
         * The syntax uses the undefinedDimension count.
         * e.g. int [][] { 1, 2 };
         */
        if (child instanceof BSHArrayInitializer) {
            if (this.baseType == null)
                throw new EvalError(
                        "Internal Array Eval err:  unknown base type", this,
                        callstack);
            final Object initValue = ((BSHArrayInitializer) child).eval(
                    this.baseType, this.numUndefinedDims, callstack,
                    interpreter);
            final Class arrayClass = initValue.getClass();
            final int actualDimensions = Reflect.getArrayDimensions(arrayClass);
            this.definedDimensions = new int[actualDimensions];
            // Compare with number of dimensions actually created with the
            // number specified (syntax uses the undefined ones here)
            if (this.definedDimensions.length != this.numUndefinedDims)
                throw new EvalError(
                        "Incompatible initializer. Allocation calls for a "
                                + this.numUndefinedDims
                                + " dimensional array, but initializer is a "
                                + actualDimensions + " dimensional array",
                        this, callstack);
            // fill in definedDimensions [] lengths
            Object arraySlice = initValue;
            for (int i = 0; i < this.definedDimensions.length; i++) {
                this.definedDimensions[i] = Array.getLength(arraySlice);
                if (this.definedDimensions[i] > 0)
                    arraySlice = Array.get(arraySlice, 0);
            }
            return initValue;
        } else {
            // Evaluate the defined dimensions of the array
            this.definedDimensions = new int[this.numDefinedDims];
            for (int i = 0; i < this.numDefinedDims; i++)
                try {
                    final Object length = ((SimpleNode) this.jjtGetChild(i))
                            .eval(callstack, interpreter);
                    this.definedDimensions[i] = ((Primitive) length).intValue();
                } catch (final Exception e) {
                    throw new EvalError(
                            "Array index: " + i
                                    + " does not evaluate to an integer",
                            this, callstack);
                }
        }
        return Primitive.VOID;
    }
}
