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
/****************************************************************************/

package bsh;

import static org.junit.Assert.assertEquals;
import bsh.legacy.ParserConstants;

import org.junit.Test;

public class NodeStringTest {

    /**
     * Verify allocation expression to string.
     */
    @Test
    public void test_allocation_expression_string() {
        assertEquals("AllocationExpression", new BSHAllocationExpression().toString());
    }

    /**
     * Verify ambiguous name to string.
     */
    @Test
    public void test_ambiguous_name_string() {
        assertEquals("AmbiguousName: null", new BSHAmbiguousName().toString());
    }

    /**
     * Verify arguments to string.
     */
    @Test
    public void test_arguments_string() {
        assertEquals("Arguments", new BSHArguments().toString());
    }

    /**
     * Verify array dimensions to string.
     */
    @Test
    public void test_array_dimensions_string() {
        assertEquals("ArrayDimensions: 0, 0", new BSHArrayDimensions().toString());
    }

    /**
     * Verify array initializer to string.
     */
    @Test
    public void test_array_initializer_string() {
        assertEquals("ArrayInitializer: false", new BSHArrayInitializer().toString());
    }

    /**
     * Verify assignment to string.
     */
    @Test
    public void test_assignment_string() {
        BSHAssignment node = new BSHAssignment();
        assertEquals("Assignment", node.toString());
        node.setOperator(ParserConstants.EOF);
        assertEquals("Assignment: <EOF>", node.toString());
    }

    /**
     * Verify binary expression to string.
     */
    @Test
    public void test_binary_expression_string() {
        assertEquals("BinaryExpression: <EOF>", new BSHBinaryExpression().toString());
    }

    /**
     * Verify block to string.
     */
    @Test
    public void test_block_string() {
        assertEquals("Block: static=false, synchronized=false", new BSHBlock().toString());
    }

    /**
     * Verify class declaration to string.
     */
    @Test
    public void test_class_declaration_string() {
        assertEquals("ClassDeclaration: null", new BSHClassDeclaration().toString());
    }

    /**
     * Verify enhanced for statement to string.
     */
    @Test
    public void test_enhanced_for_statement_string() {
        assertEquals("EnhancedForStatement: null: null, final=false", new BSHEnhancedForStatement().toString());
    }

    /**
     * Verify enum constant to string.
     */
    @Test
    public void test_enum_constant_string() {
        assertEquals("EnumConstant: Modifiers: public static final enum class java.lang.Enum null", new BSHEnumConstant().toString());
    }

    /**
     * Verify formal comment to string.
     */
    @Test
    public void test_formal_comment_string() {
        assertEquals("FormalComment: null", new BSHFormalComment().toString());
    }

    /**
     * Verify formal parameter to string.
     */
    @Test
    public void test_formal_parameter_string() {
        assertEquals("FormalParameter: null, final=false, varargs=false", new BSHFormalParameter().toString());
    }

    /**
     * Verify for statement to string.
     */
    @Test
    public void test_for_statement_string() {
        assertEquals("ForStatement: null: false ; false ; false", new BSHForStatement().toString());
    }

    /**
     * Verify import declaration to string.
     */
    @Test
    public void test_import_declaration_string() {
        assertEquals("ImportDeclaration: static=false, *=false, super import=false", new BSHImportDeclaration().toString());
    }

    /**
     * Verify literal to string.
     */
    @Test
    public void test_literal_string() {
        assertEquals("Literal: null", new BSHLiteral().toString());
    }

    /**
     * Verify method declaration to string.
     */
    @Test
    public void test_method_declaration_string() {
        assertEquals("MethodDeclaration: null", new BSHMethodDeclaration().toString());
    }

    /**
     * Verify multi catch to string.
     */
    @Test
    public void test_multi_catch_string() {
        assertEquals("MultiCatch: null, final=false", new BSHMultiCatch().toString());
    }

    /**
     * Verify primary suffix to string.
     */
    @Test
    public void test_primary_suffix_string() {
        BSHPrimarySuffix node = new BSHPrimarySuffix();
        assertEquals("PrimarySuffix:NO OPERATION", node.toString());
        node.operation = BSHPrimarySuffix.CLASS;
        assertEquals("PrimarySuffix:CLASS class", node.toString());
        node.operation = BSHPrimarySuffix.INDEX;
        assertEquals("PrimarySuffix:INDEX [false:false false:false]", node.toString());
        node.operation = BSHPrimarySuffix.NAME;
        assertEquals("PrimarySuffix:NAME null", node.toString());
        node.operation = BSHPrimarySuffix.PROPERTY;
        assertEquals("PrimarySuffix:PROPERTY {}", node.toString());
        node.operation = BSHPrimarySuffix.NEW;
        assertEquals("PrimarySuffix:NEW new", node.toString());
    }

    /**
     * Verify primitive type to string.
     */
    @Test
    public void test_primitive_type_string() {
        assertEquals("PrimitiveType: null", new BSHPrimitiveType().toString());
    }

    /**
     * Verify return statement to string.
     */
    @Test
    public void test_return_statement_string() {
        assertEquals("ReturnStatement: <EOF> null:", new BSHReturnStatement().toString());
    }

    /**
     * Verify labeled statement to string.
     */
    @Test
    public void test_labeled_statement_string() {
        assertEquals("LabeledStatement: null:", new BSHLabeledStatement().toString());
    }

    /**
     * Verify return type to string.
     */
    @Test
    public void test_return_type_string() {
        assertEquals("ReturnType: void=false", new BSHReturnType().toString());
    }

    /**
     * Verify switch label to string.
     */
    @Test
    public void test_switch_label_string() {
        BSHSwitchLabel node = new BSHSwitchLabel();
        assertEquals("SwitchLabel: case", node.toString());
        node.isDefault = true;
        assertEquals("SwitchLabel: default", node.toString());
    }

    /**
     * Verify typed variable declaration to string.
     */
    @Test
    public void test_typed_variable_declaration_string() {
        assertEquals("TypedVariableDeclaration: Modifiers: ", new BSHTypedVariableDeclaration().toString());
    }

    /**
     * Verify unary expression to string.
     */
    @Test
    public void test_unary_expression_string() {
        assertEquals("UnaryExpression: <EOF>", new BSHUnaryExpression().toString());
    }

    /**
     * Verify variable declarator to string.
     */
    @Test
    public void test_variable_declarator_string() {
        assertEquals("VariableDeclarator: null", new BSHVariableDeclarator().toString());
    }

    /**
     * Verify while statement to string.
     */
    @Test
    public void test_while_statement_string() {
        assertEquals("WhileStatement: null: do=false", new BSHWhileStatement().toString());
    }
}
