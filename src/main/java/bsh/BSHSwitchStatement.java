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

class BSHSwitchStatement
    extends SimpleNode
    implements ParserConstants
{

    public BSHSwitchStatement(int id) { super(id); }

    public Object eval( CallStack callstack, Interpreter interpreter )
        throws EvalError
    {
        int numchild = jjtGetNumChildren();
        int child = 0;
        Node switchExp = jjtGetChild(child++);
        Object switchVal = switchExp.eval( callstack, interpreter );

        if ( switchVal == Primitive.VOID )
            throw new EvalException( "illegal use of undefined variable, "
                + "class, or 'void' literal", this, callstack );

        // as in Java 21, a null value only matches a case null label and
        // throws a NullPointerException when there is none
        boolean isNull = Primitive.unwrap(switchVal) == null;
        if ( isNull && !hasNullLabel() )
            throw new TargetError( new NullPointerException(
                "null value in switch" ), this, callstack );

        // import enum constants
        if ( !isNull && switchVal.getClass().isEnum() )
            callstack.top().importStatic( switchVal.getClass() );


        /*
            Note: this could be made clearer by adding an inner class for the
            cases and an object context for the child traversal.
        */
        // first label
        BSHSwitchLabel label;
        Node node;
        ReturnControl returnControl=null;

        // get the first label
        if ( child >= numchild )
            throw new EvalException("Empty switch statement.", this, callstack );
        label = ((BSHSwitchLabel)jjtGetChild(child++));

        // while more labels or blocks and haven't hit return control
        while ( child < numchild && returnControl == null )
        {
            // if label is default or equals switchVal
            if ( isNull ? isNullLabel(label)
                : label.isDefault || primitiveEquals(
                    switchVal, label.eval( callstack, interpreter ),
                    callstack, switchExp )
                )
            {
                // execute nodes, skipping labels, until a break or return
                while ( child < numchild )
                {
                    node = jjtGetChild(child++);
                    if ( node instanceof BSHSwitchLabel )
                        continue;
                    // eval it
                    Object value =
                        node.eval( callstack, interpreter );

                    // should check to disallow continue here?
                    if ( value instanceof ReturnControl ) {
                        returnControl = (ReturnControl)value;
                        break;
                    }
                }
            } else
            {
                // skip nodes until next label
                while ( child < numchild )
                {
                    node = jjtGetChild(child++);
                    if ( node instanceof BSHSwitchLabel ) {
                        label = (BSHSwitchLabel)node;
                        break;
                    }
                }
            }
        }

        if ( returnControl != null && returnControl.kind == RETURN )
            return returnControl;
        else
            return Primitive.VOID;
    }

    /** Whether any label of this switch is case null. */
    private boolean hasNullLabel() {
        for ( int i = 1; i < jjtGetNumChildren(); i++ )
            if ( jjtGetChild(i) instanceof BSHSwitchLabel
                    && isNullLabel((BSHSwitchLabel) jjtGetChild(i)) )
                return true;
        return false;
    }

    /** Whether the label is case null, which is checked without evaluating
     * any label, as Java checks a null value before matching. */
    private static boolean isNullLabel( BSHSwitchLabel label ) {
        if ( label.isDefault )
            return false;
        Node node = label.jjtGetChild(0);
        while ( node instanceof BSHAssignment
                && ((BSHAssignment) node).operator == null
                || node instanceof BSHPrimaryExpression
                && node.jjtGetNumChildren() == 1 )
            node = node.jjtGetChild(0);
        return node instanceof BSHLiteral
            && ((BSHLiteral) node).value == Primitive.NULL;
    }

    /**
        Helper method for testing equals on two primitive or boxable objects.
        yuck: factor this out into Primitive.java
    */
    private boolean primitiveEquals(
        Object switchVal, Object targetVal,
        CallStack callstack, Node switchExp  )
        throws EvalError
    {
        // a value that isn't null never matches case null
        if (targetVal == Primitive.VOID || targetVal == Primitive.NULL)
            return false;
        if ( switchVal instanceof Primitive || targetVal instanceof Primitive )
            try {
                // binaryOperation can return Primitive or wrapper type
                Object result = Operators.binaryOperation(
                    switchVal, targetVal, ParserConstants.EQ );
                result = Primitive.unwrap( result );
                return result.equals( Boolean.TRUE );
            } catch ( UtilEvalError e ) {
                throw e.toEvalError(
                    "Switch value: "+switchExp.getText()+": ",
                    this, callstack );
            }
        else
            return switchVal.equals( targetVal );
    }

    @Override
    public String toString() {
        return super.toString() + ": switch";
    }
}

