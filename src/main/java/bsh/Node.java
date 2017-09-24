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
/* Generated By:JJTree: Do not edit this line. Node.java */
package bsh;

/**
 * The Interface Node.
 *
 * All BSH nodes must implement this interface. It provides basic
 * machinery for constructing the parent and child relationships
 * between nodes.
 */
interface Node extends java.io.Serializable {

    /**
     * This method is called after the node has been made the current
     * node. It indicates that child nodes can now be added to it.
     */
    public void jjtOpen();

    /**
     * This method is called after all the child nodes have been
     * added.
     */
    public void jjtClose();

    /**
     * This pair of methods are used to inform the node of its
     * parent.
     *
     * @param n
     *            the n
     */
    public void jjtSetParent(Node n);

    /**
     * Jjt get parent.
     *
     * @return the node
     */
    public Node jjtGetParent();

    /**
     * This method tells the node to add its argument to the node's
     * list of children.
     *
     * @param n
     *            the n
     * @param i
     *            the i
     */
    public void jjtAddChild(Node n, int i);

    /**
     * This method returns a child node. The children are numbered
     * from zero, left to right.
     *
     * @param i
     *            the i
     * @return the node
     */
    public Node jjtGetChild(int i);

    /**
     * Return the number of children the node has.
     *
     * @return the int
     */
    public int jjtGetNumChildren();
}
