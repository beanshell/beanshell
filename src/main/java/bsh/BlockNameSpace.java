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

import java.util.concurrent.atomic.AtomicInteger;

import bsh.util.ReferenceCache;
import bsh.util.ReferenceCache.Type;

/**
    A specialized namespace for Blocks (e.g. the body of a "for" statement).
    The Block acts like a child namespace but only for typed variables
    declared within it (block local scope) or untyped variables explicitly set
    in it via setBlockVariable().  Otherwise variable assignment
    (including untyped variable usage) acts like it is part of the containing
    block.
    <p>
*/
/*
    Note: This class essentially just delegates most of its methods to its
    parent.  The setVariable() indirection is very small.  We could probably
    fold this functionality back into the base NameSpace as a special case.
    But this has changed a few times so I'd like to leave this abstraction for
    now.
*/
class BlockNameSpace extends NameSpace
{
    /** Atomic block count of unique block instances. */
    public static final AtomicInteger blockCount = new AtomicInteger();

    /** Unique key for cached name spaces. */
    private static class UniqueBlock {
        NameSpace ns;
        int id;
        /** Unique block consists of a namespace and unique id.
         * @param ns the Namespace parent
         * @param id a unique id for block namespaces */
        UniqueBlock(NameSpace ns, int id) {
            this.ns = ns;
            this.id = id;
        }

        /** Compares the calculated hash code with object for equality.
         *  {@inheritDoc} */
        @Override
        public boolean equals(final Object obj) {
            return null != obj && hashCode() == obj.hashCode();
        }

        /** Return a calculated hash code from name space and block id.
         * {@inheritDoc} */
        @Override
        public int hashCode() { return ns.hashCode() + id; }
    }

    /** Weak reference cache for reusable block namespaces */
    private static final ReferenceCache<UniqueBlock,NameSpace> blockspaces
        = new ReferenceCache<UniqueBlock, NameSpace>(Type.Weak, Type.Weak, 4000) {
            /** Create block namespace based on unique block key as required */
            protected NameSpace create(UniqueBlock key) {
                return new BlockNameSpace(key.ns, key.id);
            }
    };

    /** Static method to get a unique block name space. With the
     * supplied namespace as parent and unique block id obtained
     * from blockCount.
     * @param parent name space
     * @param blockId unique id for block
     * @return new or cached instance of a unique block name space */
    public static NameSpace getInstance(NameSpace parent, int blockId ) {
        return blockspaces.get(new UniqueBlock(parent, blockId));
    }

    /** Public constructor to create a non cached instance.
     * @param parent name space
     * @param blockId unique id for block */
    public BlockNameSpace( NameSpace parent, int blockId )
    {
        super( parent, parent.getName()+ "/BlockNameSpace" + blockId );
        this.isMethod = parent.isMethod;
    }

    /**
        Override the standard namespace behavior to make assignments
        happen in our parent (enclosing) namespace, unless the variable has
        already been assigned here via a typed declaration or through
        the special setBlockVariable() (used for untyped args in try/catch).
        <p>
        i.e. only allow typed var declaration to happen in this namespace.
        Typed vars are handled in the ordinary way local scope.  All untyped
        assignments are delegated to the enclosing context.
    */
    /*
        Note: it may see like with the new 1.3 scoping this test could be
        removed, but it cannot.  When recurse is false we still need to set the
        variable in our parent, not here.
    */
    public Variable setVariable(
        String name, Object value, boolean strictJava, boolean recurse )
        throws UtilEvalError
    {
        if ( weHaveVar( name ) )
            // set the var here in the block namespace
            return super.setVariable( name, value, strictJava, false );
        else
            // set the var in the enclosing (parent) namespace
            return getParent().setVariable( name, value, strictJava, recurse );
    }

    /**
        Set an untyped variable in the block namespace.
        The BlockNameSpace would normally delegate this set to the parent.
        Typed variables are naturally set locally.
        This is used in try/catch block argument.
    */
    public void setBlockVariable( String name, Object value )
        throws UtilEvalError
    {
        super.setVariable( name, value, false/*strict?*/, false );
    }

    /**
        We have the variable: either it was declared here with a type, giving
        it block local scope or an untyped var was explicitly set here via
        setBlockVariable().
    */
    private boolean weHaveVar( String name )
    {
        // super.variables.containsKey( name ) not any faster, I checked
        try {
            return super.getVariableImpl( name, false ) != null;
        } catch ( UtilEvalError e ) { return false; }
    }

/**
        Get the actual BlockNameSpace 'this' reference.
        <p/>
        Normally a 'this' reference to a BlockNameSpace (e.g. if () { } )
        resolves to the parent namespace (e.g. the namespace containing the
        "if" statement).  However when code inside the BlockNameSpace needs to
        resolve things relative to 'this' we must use the actual block's 'this'
        reference.  Name.java is smart enough to handle this using
        getBlockThis().
        @see #getThis( Interpreter )
    This getBlockThis( Interpreter declaringInterpreter )
    {
        return super.getThis( declaringInterpreter );
    }
*/

    //
    // Begin methods which simply delegate to our parent (enclosing scope)
    //

    /**
        This method recurses to find the nearest non-BlockNameSpace parent.

    public NameSpace getParent()
    {
        NameSpace parent = super.getParent();
        if ( parent instanceof BlockNameSpace )
            return parent.getParent();
        else
            return parent;
    }
*/
    /** do we need this? */
    private NameSpace getNonBlockParent()
    {
        NameSpace parent = super.getParent();
        if ( parent instanceof BlockNameSpace )
            return ((BlockNameSpace)parent).getNonBlockParent();
        else
            return parent;
    }

    /**
        Get a 'this' reference is our parent's 'this' for the object closure.
        e.g. Normally a 'this' reference to a BlockNameSpace (e.g. if () { } )
        resolves to the parent namespace (e.g. the namespace containing the
        "if" statement).
        @see #getBlockThis( Interpreter )
    */
    public This getThis( Interpreter declaringInterpreter ) {
        return getNonBlockParent().getThis( declaringInterpreter );
    }

    /**
        super is our parent's super
    */
    public This getSuper( Interpreter declaringInterpreter ) {
        return getNonBlockParent().getSuper( declaringInterpreter );
    }

    /**
        delegate import to our parent
    */
    public void importClass(String name) {
        getParent().importClass( name );
    }

    /**
        delegate import to our parent
    */
    public void importPackage(String name) {
        getParent().importPackage( name );
    }

    public void setMethod(BshMethod method) {
        getParent().setMethod( method );
    }
}

