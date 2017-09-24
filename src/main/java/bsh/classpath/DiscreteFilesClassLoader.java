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
package bsh.classpath;

import java.util.HashMap;

import bsh.BshClassManager;
import bsh.classpath.BshClassPath.ClassSource;

/**
 * A classloader which can load one or more classes from specified sources.
 * Because the classes are loaded via a single classloader they change as a
 * group and any versioning cross dependencies can be managed.
 */
public class DiscreteFilesClassLoader extends BshClassLoader {

    /**
     * Map of class sources which also implies our coverage space.
     */
    ClassSourceMap map;

    /**
     * The Class ClassSourceMap.
     */
    public static class ClassSourceMap extends HashMap {

        /** The Constant serialVersionUID. */
        private static final long serialVersionUID = 1L;

        /**
         * Put.
         *
         * @param name
         *            the name
         * @param source
         *            the source
         */
        public void put(final String name, final ClassSource source) {
            super.put(name, source);
        }

        /**
         * Gets the.
         *
         * @param name
         *            the name
         * @return the class source
         */
        public ClassSource get(final String name) {
            return (ClassSource) super.get(name);
        }
    }

    /**
     * Instantiates a new discrete files class loader.
     *
     * @param classManager
     *            the class manager
     * @param map
     *            the map
     */
    public DiscreteFilesClassLoader(final BshClassManager classManager,
            final ClassSourceMap map) {
        super(classManager);
        this.map = map;
    }

    /** {@inheritDoc} */
    @Override
    public Class findClass(final String name) throws ClassNotFoundException {
        // Load it if it's one of our classes
        final ClassSource source = this.map.get(name);
        if (source != null) {
            final byte[] code = source.getCode(name);
            return this.defineClass(name, code, 0, code.length);
        } else
            // Let superclass BshClassLoader (URLClassLoader) findClass try
            // to find the class...
            return super.findClass(name);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return super.toString() + "for files: " + this.map;
    }
}
