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
package bsh.util;

import java.util.ArrayList;
import java.util.List;

import bsh.NameSource;
import bsh.StringUtil;

/**
 * NameCompletionTable is a utility that implements simple name completion for
 * a collection of names, NameSources, and other NameCompletionTables.
 * This implementation uses a trivial linear search and comparison...
 */
public class NameCompletionTable extends ArrayList implements NameCompletion {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;
    /** Unimplemented - need a collection here. */
    NameCompletionTable table;
    /** The sources. */
    List sources;

    /**
     * Unimplemented - need a collection of sources here.
     **
    */
    public NameCompletionTable() {}

    /**
     * Add a NameCompletionTable, which is more optimized than the more
     * general NameSource.
     *
     * @param table
     *            the table
     */
    public void add(final NameCompletionTable table) {
        /** Unimplemented - need a collection here */
        if (this.table != null)
            throw new RuntimeException("Unimplemented usage error");
        this.table = table;
    }

    /**
     * Add a NameSource which is monitored for names.
     * Unimplemented - behavior is broken... no updates
     *
     * @param source
     *            the source
     */
    public void add(final NameSource source) {
        /*
         * Unimplemented -
         * Need to add an inner class util here that holds the source and
         * monitors it by registering a listener
         */
        if (this.sources == null)
            this.sources = new ArrayList();
        this.sources.add(source);
    }

    /**
     * Add any matching names to list (including any from other tables).
     *
     * @param part
     *            the part
     * @param found
     *            the found
     */
    protected void getMatchingNames(final String part, final List found) {
        // check our table
        for (int i = 0; i < this.size(); i++) {
            final String name = (String) this.get(i);
            if (name.startsWith(part))
                found.add(name);
        }
        // Check other tables.
        /** Unimplemented - need a collection here */
        if (this.table != null)
            this.table.getMatchingNames(part, found);
        // Check other sources
        // note should add caching in source adapters
        if (this.sources != null)
            for (int i = 0; i < this.sources.size(); i++) {
                final NameSource src = (NameSource) this.sources.get(i);
                final String[] names = src.getAllNames();
                for (final String name : names)
                    if (name.startsWith(part))
                        found.add(name);
            }
    }

    /** {@inheritDoc} */
    public String[] completeName(final String part) {
        final List found = new ArrayList();
        this.getMatchingNames(part, found);
        if (found.size() == 0)
            return new String[0];
        // Find the max common prefix
        String maxCommon = (String) found.get(0);
        for (int i = 1; i < found.size() && maxCommon.length() > 0; i++) {
            maxCommon = StringUtil.maxCommonPrefix(maxCommon,
                    (String) found.get(i));
            // if maxCommon gets as small as part, stop trying
            if (maxCommon.equals(part))
                break;
        }
        // Return max common or all ambiguous
        if (maxCommon.length() > part.length())
            return new String[] {maxCommon};
        else
            return (String[]) found.toArray(new String[0]);
    }
    /**
     * class SourceCache implements NameSource.Listener
     * {
     * NameSource src;
     * SourceMonitor(NameSource src) {
     * this.src = src;
     * }
     * public void nameSourceChanged(NameSource src) {
     * }
     * }
     */
}
