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
 * The Class BSHType.
 */
class BSHType extends SimpleNode implements BshClassManager.Listener {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;
    /**
     * baseType is used during evaluation of full type and retained for the
     * case where we are an array type.
     * In the case where we are not an array this will be the same as type.
     */
    private Class baseType;
    /**
     * If we are an array type this will be non zero and indicate the
     * dimensionality of the array. e.g. 2 for String[][];
     */
    private int arrayDims;
    /**
     * Internal cache of the type. Cleared on classloader change.
     */
    private Class type;
    /** The descriptor. */
    String descriptor;

    /**
     * Instantiates a new BSH type.
     *
     * @param id
     *            the id
     */
    BSHType(final int id) {
        super(id);
    }

    /**
     * Used by the grammar to indicate dimensions of array types
     * during parsing.
     */
    public void addArrayDimension() {
        this.arrayDims++;
    }

    /**
     * Gets the type node.
     *
     * @return the type node
     */
    SimpleNode getTypeNode() {
        return (SimpleNode) this.jjtGetChild(0);
    }

    /**
     * Returns a class descriptor for this type.
     * If the type is an ambiguous name (object type) evaluation is
     * attempted through the namespace in order to resolve imports.
     * If it is not found and the name is non-compound we assume the default
     * package for the name.
     *
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     * @param defaultPackage
     *            the default package
     * @return the type descriptor
     */
    public String getTypeDescriptor(final CallStack callstack,
            final Interpreter interpreter, final String defaultPackage) {
        // return cached type if available
        if (this.descriptor != null)
            return this.descriptor;
        String descriptor;
        // first node will either be PrimitiveType or AmbiguousName
        final SimpleNode node = this.getTypeNode();
        if (node instanceof BSHPrimitiveType)
            descriptor = getTypeDescriptor(((BSHPrimitiveType) node).type);
        else {
            String clasName = ((BSHAmbiguousName) node).text;
            final BshClassManager bcm = interpreter.getClassManager();
            // Note: incorrect here - we are using the hack in bsh class
            // manager that allows lookup by base name. We need to eliminate
            // this limitation by working through imports. See notes in class
            // manager.
            final String definingClass = bcm.getClassBeingDefined(clasName);
            Class clas = null;
            if (definingClass == null)
                try {
                    clas = ((BSHAmbiguousName) node).toClass(callstack,
                            interpreter);
                } catch (final EvalError e) {
                    // throw new InterpreterError("unable to resolve type: "+e);
                    // ignore and try default package
                    // System.out.println("BSHType: "+node+" class not found");
                }
            else
                clasName = definingClass;
            if (clas != null)
                // System.out.println("found clas: "+clas);
                descriptor = getTypeDescriptor(clas);
            else if (defaultPackage == null || Name.isCompound(clasName))
                descriptor = "L" + clasName.replace('.', '/') + ";";
            else
                descriptor = "L" + defaultPackage.replace('.', '/') + "/"
                        + clasName + ";";
        }
        for (int i = 0; i < this.arrayDims; i++)
            descriptor = "[" + descriptor;
        this.descriptor = descriptor;
        // System.out.println("BSHType: returning descriptor: "+descriptor);
        return descriptor;
    }

    /**
     * Gets the type.
     *
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     * @return the type
     * @throws EvalError
     *             the eval error
     */
    public Class getType(final CallStack callstack,
            final Interpreter interpreter) throws EvalError {
        // return cached type if available
        if (this.type != null)
            return this.type;
        // first node will either be PrimitiveType or AmbiguousName
        final SimpleNode node = this.getTypeNode();
        if (node instanceof BSHPrimitiveType)
            this.baseType = ((BSHPrimitiveType) node).getType();
        else
            this.baseType = ((BSHAmbiguousName) node).toClass(callstack,
                    interpreter);
        if (this.arrayDims > 0)
            try {
                // Get the type by constructing a prototype array with
                // arbitrary (zero) length in each dimension.
                final int[] dims = new int[this.arrayDims]; // int array default
                                                            // zeros
                final Object obj = Array.newInstance(this.baseType, dims);
                this.type = obj.getClass();
            } catch (final Exception e) {
                throw new EvalError("Couldn't construct array type", this,
                        callstack);
            }
        else
            this.type = this.baseType;
        // hack... sticking to first interpreter that resolves this
        // see comments on type instance variable
        interpreter.getClassManager().addListener(this);
        return this.type;
    }

    /**
     * baseType is used during evaluation of full type and retained for the
     * case where we are an array type.
     * In the case where we are not an array this will be the same as type.
     *
     * @return the base type
     */
    public Class getBaseType() {
        return this.baseType;
    }

    /**
     * If we are an array type this will be non zero and indicate the
     * dimensionality of the array. e.g. 2 for String[][];
     *
     * @return the array dims
     */
    public int getArrayDims() {
        return this.arrayDims;
    }

    /** {@inheritDoc} */
    public void classLoaderChanged() {
        this.type = null;
        this.baseType = null;
    }

    /**
     * Gets the type descriptor.
     *
     * @param clas
     *            the clas
     * @return the type descriptor
     */
    public static String getTypeDescriptor(final Class clas) {
        if (clas == Boolean.TYPE)
            return "Z";
        if (clas == Character.TYPE)
            return "C";
        if (clas == Byte.TYPE)
            return "B";
        if (clas == Short.TYPE)
            return "S";
        if (clas == Integer.TYPE)
            return "I";
        if (clas == Long.TYPE)
            return "J";
        if (clas == Float.TYPE)
            return "F";
        if (clas == Double.TYPE)
            return "D";
        if (clas == Void.TYPE)
            return "V";
        // Is getName() ok? test with 1.1
        final String name = clas.getName().replace('.', '/');
        if (name.startsWith("[") || name.endsWith(";"))
            return name;
        else
            return "L" + name.replace('.', '/') + ";";
    }
}
