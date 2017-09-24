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
 * The Class BSHBlock.
 */
class BSHBlock extends SimpleNode {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;
    /** The is synchronized. */
    public boolean isSynchronized = false;
    /** The is static. */
    public boolean isStatic = false;

    /**
     * Instantiates a new BSH block.
     *
     * @param id
     *            the id
     */
    BSHBlock(final int id) {
        super(id);
    }

    /** {@inheritDoc} */
    @Override
    public Object eval(final CallStack callstack, final Interpreter interpreter)
            throws EvalError {
        return this.eval(callstack, interpreter, false);
    }

    /**
     * Eval.
     *
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     * @param overrideNamespace
     *            if set to true the block will be executed
     *            in the current namespace (not a subordinate one).
     *            <p>
     *            If true *no* new BlockNamespace will be swapped onto the stack
     *            and
     *            the eval will happen in the current
     *            top namespace. This is used by BshMethod, TryStatement, etc.
     *            which must intialize the block first and also for those that
     *            perform
     *            multiple passes in the same block.
     * @return the object
     * @throws EvalError
     *             the eval error
     */
    public Object eval(final CallStack callstack, final Interpreter interpreter,
            final boolean overrideNamespace) throws EvalError {
        Object syncValue = null;
        if (this.isSynchronized) {
            // First node is the expression on which to sync
            final SimpleNode exp = (SimpleNode) this.jjtGetChild(0);
            syncValue = exp.eval(callstack, interpreter);
        }
        Object ret;
        if (this.isSynchronized) // Do the actual synchronization
            synchronized (syncValue) {
                ret = this.evalBlock(callstack, interpreter, overrideNamespace,
                        null/* filter */);
            }
        else
            ret = this.evalBlock(callstack, interpreter, overrideNamespace,
                    null/* filter */);
        return ret;
    }

    /**
     * Eval block.
     *
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     * @param overrideNamespace
     *            the override namespace
     * @param nodeFilter
     *            the node filter
     * @return the object
     * @throws EvalError
     *             the eval error
     */
    Object evalBlock(final CallStack callstack, final Interpreter interpreter,
            final boolean overrideNamespace, final NodeFilter nodeFilter)
            throws EvalError {
        Object ret = Primitive.VOID;
        NameSpace enclosingNameSpace = null;
        if (!overrideNamespace) {
            enclosingNameSpace = callstack.top();
            final BlockNameSpace bodyNameSpace = new BlockNameSpace(
                    enclosingNameSpace);
            callstack.swap(bodyNameSpace);
        }
        final int startChild = this.isSynchronized ? 1 : 0;
        final int numChildren = this.jjtGetNumChildren();
        try {
            /*
             * Evaluate block in two passes:
             * First do class declarations then do everything else.
             */
            for (int i = startChild; i < numChildren; i++) {
                final SimpleNode node = (SimpleNode) this.jjtGetChild(i);
                if (nodeFilter != null && !nodeFilter.isVisible(node))
                    continue;
                if (node instanceof BSHClassDeclaration)
                    node.eval(callstack, interpreter);
            }
            for (int i = startChild; i < numChildren; i++) {
                final SimpleNode node = (SimpleNode) this.jjtGetChild(i);
                if (node instanceof BSHClassDeclaration)
                    continue;
                // filter nodes
                if (nodeFilter != null && !nodeFilter.isVisible(node))
                    continue;
                ret = node.eval(callstack, interpreter);
                // statement or embedded block evaluated a return statement
                if (ret instanceof ReturnControl)
                    break;
            }
        } finally {
            // make sure we put the namespace back when we leave.
            if (!overrideNamespace)
                callstack.swap(enclosingNameSpace);
        }
        return ret;
    }

    /**
     * The Interface NodeFilter.
     */
    public interface NodeFilter {

        /**
         * Checks if is visible.
         *
         * @param node
         *            the node
         * @return true, if is visible
         */
        public boolean isVisible(SimpleNode node);
    }
}
