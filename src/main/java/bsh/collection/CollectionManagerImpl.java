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
package bsh.collection;

import java.util.Map;

import bsh.BshIterator;

/**
 * Dynamically loaded extension supporting post 1.1 collections iterator.
 *
 * @author Pat Niemeyer
 */
public class CollectionManagerImpl extends bsh.CollectionManager {

    /** {@inheritDoc} */
    @Override
    public BshIterator getBshIterator(final Object obj)
            throws IllegalArgumentException {
        return new CollectionIterator(obj);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isMap(final Object obj) {
        if (obj instanceof Map)
            return true;
        else
            return super.isMap(obj);
    }

    /** {@inheritDoc} */
    @Override
    public Object getFromMap(final Object map, final Object key) {
        // Hashtable implements Map
        return ((Map) map).get(key);
    }

    /** {@inheritDoc} *
     * Place the raw value into the map... should be unwrapped.
     */
    @Override
    public Object putInMap(final Object map, final Object key,
            final Object value) {
        // Hashtable implements Map
        return ((Map) map).put(key, value);
    }
}
