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

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.lang.reflect.Array;

class BSHArrayInitializer extends SimpleNode {
    private static final long serialVersionUID = 1L;
    boolean isMapInArray = false;

    BSHArrayInitializer(int id) { super(id); }

    /** Hook into node creation to apply additional configurations.
     * Inform expression children that they are array expressions.
     * @see BSHPrimaryExpression.setArrayExpression
     * {@inheritDoc} */
    @Override
    public void jjtSetParent(Node n) {
        parent = n;
        if ( null != children ) for ( Node c : children )
            if ( c.jjtGetNumChildren() > 0
                    && c.jjtGetChild(0) instanceof BSHPrimaryExpression )
            ((BSHPrimaryExpression) c.jjtGetChild(0)).setArrayExpression(this);
    }

    /** Default node eval is disabled for this node type.
     * {@inheritDoc} */
    @Override
    public Object eval( CallStack callstack, Interpreter interpreter )
        throws EvalError {
        throw new EvalError( "Array initializer has no base type.",
            this, callstack );
    }

    /** Construct the array from the initializer syntax.
     * @param baseType the base class type of the array (no dimensionality)
     * @param dimensions the top number of dimensions of the array
     *      e.g. 2 for a String [][];
     * @param callstack default eval call stack
     * @param interpreter default eval interpreter
     * @return array initializer
     * @throws EvalError produced by throwTypeError */
    public Object eval( Class<?> baseType, int dimensions,
                        CallStack callstack, Interpreter interpreter )
        throws EvalError
    {
        int numInitializers = jjtGetNumChildren();
        if ( 0 == numInitializers )
            dimensions = 0;

        // if dimensions are 0 then our work here is done
        if ( 0 == dimensions ) {
            if ( baseType == Void.TYPE )
                baseType = Object.class;
            return Array.newInstance(baseType, 0);
        }

        // we may infer the baseType, reference the original to not be lost
        Class<?> originalBaseType = baseType;

        // loose typed arrays ex. a = {1, 2, 3}
        if ( -1 == dimensions ) {
            // apply strict java when loose type arrays or maps are invalid
            if ( interpreter.getStrictJava() )
                throw new EvalError("No declared array type or dimensions.",
                        this, callstack );

            // infer dimensions starting with 1 dimension, this initializer node
            dimensions = this.inferDimensions(1, 0, this, callstack, interpreter);

            // we still do type inference for List and Map types
            if ( Types.isJavaAssignable(Collection.class, baseType)
                    || Types.isJavaAssignable(Map.class, baseType)
                    || Types.isJavaAssignable(Entry.class, baseType) )
                baseType = Void.TYPE;
        }

        // infer the element type
        if ( baseType == Void.TYPE )
            baseType = inferCommonType(null, this, callstack, interpreter);

        // no common type was inferred
        if ( null == baseType ) {
            baseType = Object.class;
            // assume null value indicates undefined dimension
            // example: {null} makes Object[][]
            dimensions++;
        }

        if ( LHS.MapEntry.class == baseType
                && ( originalBaseType == baseType
                || originalBaseType == Void.TYPE) )
            originalBaseType = Map.class;

        // allocate the array to store the initializers
        int [] dims = new int [dimensions]; // description of the array
        // The other dimensions default to zero and are assigned when
        // the values are set.
        dims[0] = numInitializers;
        Object initializers =  Array.newInstance( baseType, dims );

        // Evaluate the child nodes
        for (int i = 0; i < numInitializers; i++)
        {
            SimpleNode node = (SimpleNode)jjtGetChild(i);
            Object currentInitializer;
            if ( node instanceof BSHArrayInitializer )
                // nested arrays needs at least 2 dimensions to be valid
                if ( dimensions < 2 )
                    // maps in arrays are not arrays, they are typed java.util.Map
                    // identified during node creation as map in array, ensure type
                    // assignable by java.util.Map and evaluate the initializer as
                    // a 1 dimensional array of type LHS.MapEntry
                    if ( isMapInArray((BSHArrayInitializer) node) )
                        currentInitializer =
                            ((BSHArrayInitializer) node).eval(
                            LHS.MapEntry.class, 1, callstack, interpreter);
                    else
                        // this is an invalid dimension, raise error
                        throw new EvalError(
                            "Invalid Intializer for "+baseType+", at position: "+i,
                            this, callstack );
                else
                    // multidimensional array is supported by dimension size
                    currentInitializer =
                        ((BSHArrayInitializer) node).eval(
                        baseType, dimensions-1, callstack, interpreter);
            else
                // evaluate array element node for value
                currentInitializer = node.eval( callstack, interpreter);

            if ( currentInitializer == Primitive.VOID )
                throw new EvalError(
                    "Void in array initializer, position "+i, this, callstack );

            Object value = currentInitializer;
            try {
                // If the dimensionality of the array is 1 the value element
                // can be Primitive or Object types otherwise we expect an array.
                // Null elements indicate undefined dimensions, we do not want to
                // cast those values unless they are value elements.
                if ( dimensions == 1 || value != Primitive.NULL )
                    value = Types.castObject(
                        currentInitializer, baseType, Types.CAST );
            } catch ( UtilEvalError e ) {
                throw e.toEvalError(
                    "Error in array initializer", this, callstack );
            }
            // unwrap any primitive, map voids to null, etc.
            value = Primitive.unwrap( value );
            try {
                // store the value in the array
                Array.set(initializers, i, value);
            } catch( IllegalArgumentException e ) {
                Interpreter.debug("illegal arg", e);
                throwTypeError( baseType, value, i, callstack );
            }
        }

        // cast List and Map type
        if ( Types.isJavaAssignable(Collection.class, originalBaseType)
                || Types.isJavaAssignable(Map.class, originalBaseType)
                || Types.isJavaAssignable(Entry.class, originalBaseType) ) try {
            initializers = Types.castObject(initializers, originalBaseType, Types.CAST);
        } catch (UtilEvalError e) {
            throw new EvalError(e.getMessage(), this, callstack, e);
        }

        return initializers;
    }

    /** Maps are not array dimensions they are type java.util.Map.
     * Configures this initializer as a map expression within an
     * array, not to be treated as an array dimension.
     * @param is for isMapInArray */
    void setMapInArray(boolean is) {
        isMapInArray = is;
    }

    /** Convenience method to query the provided node's map in array flag.
     * @param init the BSHArrayInitializer to query
     * @return the given initializer's isMapInArray state  */
    boolean isMapInArray(BSHArrayInitializer init) {
        return init.isMapInArray;
    }

    /** Infer array dimensions for loose typed array expressions.
     * @param dimensions the current dimension count
     * @param idx the child node index
     * @param node the node to query
     * @param callstack the evaluation call stack
     * @param interpreter the evaluation interpreter
     * @return the number of dimensions defined
     * @throws EvalError thrown at node evaluation  */
    private int inferDimensions(int dimensions, int idx, Node node,
            CallStack callstack, Interpreter interpreter) throws EvalError {
        // count ArrayInitializer nodes in this hierarchy
        while ( node.jjtGetNumChildren() > idx
                && (node = node.jjtGetChild(idx)) instanceof BSHArrayInitializer
                && !isMapInArray((BSHArrayInitializer) node)
                && node.jjtGetNumChildren() > 0 ) {
            dimensions++;
            idx = 0;
        }

        // certain value elements may require more inference
        if ( !(node instanceof BSHArrayInitializer) ) {
            Object ot = ((SimpleNode) node).eval(callstack, interpreter);

            // if the value element is null look for more dimensions
            // example: {null, {1, 2}} makes int[][]
            if ( ot == Primitive.NULL )
                return inferDimensions(dimensions, ++idx, node.jjtGetParent(),
                        callstack, interpreter);

            // if the value element is an array we can append dimensions
            // example: new {new {1, 2}} makes int[][]
            dimensions += Types.arrayDimensions(Types.getType(ot));
        }
        // if we found an empty array element look for more dimensions
        // example: {{}, {1, 2}} makes int[][] but {{}} makes Object[][]
        else if ( node.jjtGetNumChildren() == 0 )
            return inferDimensions(dimensions, ++idx, node.jjtGetParent(),
                    callstack, interpreter);
        return dimensions;
    }

    /** Helper function to traverse array dimensions to find the common type.
     * Recursive calling for each element in an array initializer or finds the
     * common type relative to a value cell.
     * @param common the current common type
     * @param node the node to query
     * @param callstack the evaluation call stack
     * @param interpreter the evaluation interpreter
     * @return the common type for all cells
     * @throws EvalError thrown at node evaluation  */
    private Class<?> inferCommonType(Class<?> common, Node node,
            CallStack callstack, Interpreter interpreter ) throws EvalError {
        // Object is already the most common type and maps are typed LHS.MapEntry
        if ( Object.class == common || LHS.MapEntry.class == common )
            return common;
        // inspect value elements for common type
        if ( !(node instanceof BSHArrayInitializer) ) {
            Object value = ((SimpleNode) node).eval(callstack, interpreter);
            Class<?> type = Types.getType(value, Primitive.isWrapperType(common));
            return Types.getCommonType(common, Types.arrayElementType(type));
        }
        // avoid traversing maps as arrays when nested in array
        if ( isMapInArray((BSHArrayInitializer) node) )
            return Types.getCommonType(common, Map.class);
        // recurse through nested array initializer nodes
        int count = node.jjtGetNumChildren();
        for ( int i = 0; i < count; i++ )
            common = this.inferCommonType(common, node.jjtGetChild(i),
                        callstack, interpreter);
        return common;
    }

    /** Helper function to build appropriate EvalError on type exceptions.
     * @param baseType the array's component type
     * @param initializer current array dimension
     * @param argNum current cell index
     * @param callstack call stack from eval
     * @throws EvalError the produced type exception */
    private void throwTypeError(
        Class<?> baseType, Object initializer, int argNum, CallStack callstack )
        throws EvalError
    {
        String rhsType = StringUtil.typeString(initializer);

        throw new EvalError ( "Incompatible type: " + rhsType
            +" in initializer of array type: "+ baseType.getSimpleName()
            +" at position: "+argNum, this, callstack );
    }

}
