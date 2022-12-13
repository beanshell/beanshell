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

import java.util.ArrayList;
import java.util.List;

/**
 * A node reprresenting a code block
 */
class BSHBlock extends SimpleNode {
    /**
     * The block has a synchronized caluse and requires synchronization
     */
    public boolean isSynchronized = false;

    /**
     * This block has a static modifier, so to be used as a static
     *  initialization block within a class.
     */
    public boolean isStatic = false;

    /**
     * A flag to immplement a minor optimization of skipping
     * class declarations when we know there are none.
     */
    private boolean hasClassDeclaration = false;

    /**
     * A flag to immplement a minor optimization of only checking
     * for class declarations the first time through.
     */
    private boolean isFirst = true;

    BSHBlock(int id) { super(id); }

    public Object eval( CallStack callstack, Interpreter interpreter)
        throws EvalError
    {
        return eval( callstack, interpreter, false );
    }

   /**
    * Evaluate this block using the top of the callstack to resolve names
    * and in the context of the given interpreter object.
    * <p>
    * In the normal course of events each eval will allocate a new namespace
    * object using the top of the stack as the parent, and then
    * this new namespace will be swapped on to the top of the namespace
    * stack.  During block evaluations this new namespace will be used
    * for local variables.  When the block is finished the new namespace
    * is released and then old namespace is restored to the top of the stack.
    * <p>
    * There are some situations where the caller has already set up the
    * namespace ahead of time and does not want another namespace,
    * for example:
    * <ul>
    * <li>BshMethod.invokeImpl()
    * <br>The method namespace has already been set up containing
    * the formal parameters.
    * <li>BSHAllocationExpression.constructWithInterfaceBody()
    * <br>The caller sets up a namespace the same as if it would be
    * done using overrideChild=false.  I cannot see the purpose for this.
    * <li>BSHEnumConstant.eval()
    * <br>The enum constants should be set within the current namespace.
    * </ul>
    * In these situations where the overrideChild flag has been set to true
    * then use the namespace at top of the callstack.
    * @param callstack the stack of namespace chains ued to resolve names
    * @param interpreter the interpreter object used for evaluation
    * @param overrideNamespace if true the block will be executed
    * in the current namespace. If false then create an new namespace
    * and put it at the top of the callstack, restoring the top of the
    * callstack when done.
    */
    public Object eval(
        CallStack callstack, Interpreter interpreter,
        boolean overrideNamespace )
        throws EvalError
    {
        Object syncValue = null;
        if ( isSynchronized ) {
            // First node is the expression on which to sync
            Node exp = jjtGetChild(0);
            syncValue = exp.eval(callstack, interpreter);
        }

        Object ret;
        if ( isSynchronized )
            // Do the actual synchronization
            synchronized( syncValue ) {
                ret = evalBlock(callstack, interpreter,
                                overrideNamespace, null/*filter*/);
            }
        else
            // Unsynchronized
            ret = evalBlock(callstack, interpreter,
                            overrideNamespace, null/*filter*/);

        return ret;
    }

    Object evalBlock(CallStack callstack, Interpreter interpreter,
                     boolean overrideNamespace, NodeFilter nodeFilter )
        throws EvalError {

        Object ret = Primitive.VOID;
        NameSpace enclosingNameSpace = null;
        if ( !overrideNamespace ) {
           /*
            * Each block gets its own namespace, unless for
            * some special reason the overrideNamespace is set.
            * Make a new namespace with the current as a parent
            * and replace it on to the top of the stack.
            */
           enclosingNameSpace= callstack.top();
           BlockNameSpace bodyNameSpace =
              new BlockNameSpace( enclosingNameSpace );
           callstack.swap( bodyNameSpace );
        }

        int startChild = isSynchronized ? 1 : 0;
        int numChildren = jjtGetNumChildren();

        try {
            /*
             * Evaluate block in two passes:
             * First do class declarations then do everything else.
             */
            if (isFirst || hasClassDeclaration) {
                for(int i=startChild; i<numChildren; i++) {
                    Node node = jjtGetChild(i);

                    if ( nodeFilter != null && !nodeFilter.isVisible( node ) )
                        continue;

                    if ( node instanceof BSHClassDeclaration ) {
                        hasClassDeclaration = true;
                        node.eval( callstack, interpreter );
                    }
                }
            }

            List<Node> enumBlocks = null;
            for(int i=startChild; i<numChildren; i++) {
                Node node = jjtGetChild(i);

                if ( node instanceof BSHClassDeclaration )
                    continue;

                // filter nodes
                if ( nodeFilter != null && !nodeFilter.isVisible( node ) )
                    continue;

                // enum blocks need to override enum class members
                // let the class finish initializing first
                if (node instanceof BSHEnumConstant) {
                    if (enumBlocks == null)
                        enumBlocks = new ArrayList<>();
                    enumBlocks.add(node);
                    continue;
                }

                ret = node.eval( callstack, interpreter );

                // statement or embedded block evaluated a return statement
                if ( ret instanceof ReturnControl )
                    break;
            }

            /*
             * Run the enum constants blocks.  They will get eval'ed
             * with overriideChild=true and will get set within the
             * namespace for this block.
             */
            if (enumBlocks != null)
                for (Node n : enumBlocks)
                    n.eval( callstack, interpreter );

        } finally {
           /*
            * make sure to restore the original namespace when leaving,
            * if required.
            * The namespace created for this block will be gc'ed away
            */
            if ( !overrideNamespace ) {
                callstack.swap( enclosingNameSpace );
            }
        }
        isFirst = false;
        return ret;
    }

    public interface NodeFilter {
        boolean isVisible( Node node );
    }

    @Override
    public String toString() {
        return super.toString() + ": static=" + isStatic + ", synchronized=" + isSynchronized;
    }
}
