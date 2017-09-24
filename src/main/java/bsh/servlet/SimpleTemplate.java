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
package bsh.servlet;

/**

    This file is derived from Pat Niemeyer's free utilities package.
    Now part of BeanShell.

    @see http://www.pat.net/javautil/
    @version 1.0
    @author Pat Niemeyer (pat@pat.net)
*/
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * This is a simple template engine. An instance of SimpleTemplate wraps
 * a StringBuffer and performs replace operations on one or more parameters
 * embedded as HMTL style comments. The value can then be retrieved as a
 * String or written to a stream.
 *
 * Template values in the text are of the form:
 *
 * <!-- TEMPLATE-NAME -->
 *
 * Substitutions then take the form of:
 *
 * template.replace("NAME", value);
 *
 * Two static util methods are provided to help read the text of a template
 * from a stream (perhaps a URL or resource). e.g.
 *
 * @author Pat Niemeyer
 */
public class SimpleTemplate {

    /** The buff. */
    StringBuffer buff;
    /** The no template. */
    static String NO_TEMPLATE = "NO_TEMPLATE"; // Flag for non-existent
    /** The template data. */
    static Map templateData = new HashMap();
    /** The cache templates. */
    static boolean cacheTemplates = true;

    /**
     * Get a template by name, with caching.
     * Note: this should be updated to search the resource path first, etc.
     *
     * Create a new instance of a template from the specified file.
     *
     * The file text is cached so lookup is fast. Failure to find the
     * file is also cached so the read will not happen twice.
     *
     * @param file
     *            the file
     * @return the template
     */
    public static SimpleTemplate getTemplate(final String file) {
        String templateText = (String) templateData.get(file);
        if (templateText == null || !cacheTemplates)
            try {
                final FileReader fr = new FileReader(file);
                templateText = SimpleTemplate.getStringFromStream(fr);
                templateData.put(file, templateText);
            } catch (final IOException e) {
                // Not found
                templateData.put(file, NO_TEMPLATE);
            }
        else
        // Quick check prevents trying each time
        if (templateText.equals(NO_TEMPLATE))
            return null;
        if (templateText == null)
            return null;
        else
            return new SimpleTemplate(templateText);
    }

    /**
     * Gets the string from stream.
     *
     * @param ins
     *            the ins
     * @return the string from stream
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    public static String getStringFromStream(final InputStream ins)
            throws IOException {
        return getStringFromStream(new InputStreamReader(ins));
    }

    /**
     * Gets the string from stream.
     *
     * @param reader
     *            the reader
     * @return the string from stream
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    public static String getStringFromStream(final Reader reader)
            throws IOException {
        final StringBuffer sb = new StringBuffer();
        final BufferedReader br = new BufferedReader(reader);
        String line;
        while ((line = br.readLine()) != null)
            sb.append(line + "\n");
        return sb.toString();
    }

    /**
     * Instantiates a new simple template.
     *
     * @param template
     *            the template
     */
    public SimpleTemplate(final String template) {
        this.init(template);
    }

    /**
     * Instantiates a new simple template.
     *
     * @param reader
     *            the reader
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    public SimpleTemplate(final Reader reader) throws IOException {
        final String template = getStringFromStream(reader);
        this.init(template);
    }

    /**
     * Instantiates a new simple template.
     *
     * @param url
     *            the url
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    public SimpleTemplate(final URL url) throws IOException {
        final String template = getStringFromStream(url.openStream());
        this.init(template);
    }

    /**
     * Inits the.
     *
     * @param s
     *            the s
     */
    private void init(final String s) {
        this.buff = new StringBuffer(s);
    }

    /**
     * Substitute the specified text for the parameter.
     *
     * @param param
     *            the param
     * @param value
     *            the value
     */
    public void replace(final String param, final String value) {
        int[] range;
        while ((range = this.findTemplate(param)) != null)
            this.buff.replace(range[0], range[1], value);
    }

    /**
     * Find the starting (inclusive) and ending (exclusive) index of the
     * named template and return them as a two element int [].
     * Or return null if the param is not found.
     *
     * @param name
     *            the name
     * @return the int[]
     */
    int[] findTemplate(final String name) {
        final String text = this.buff.toString();
        final int len = text.length();
        int start = 0;
        while (start < len) {
            // Find begin and end comment
            final int cstart = text.indexOf("<!--", start);
            if (cstart == -1)
                return null; // no more comments
            int cend = text.indexOf("-->", cstart);
            if (cend == -1)
                return null; // no more complete comments
            cend += "-->".length();
            // Find template tag
            final int tstart = text.indexOf("TEMPLATE-", cstart);
            if (tstart == -1) {
                start = cend; // try the next comment
                continue;
            }
            // Is the tag inside the comment we found?
            if (tstart > cend) {
                start = cend; // try the next comment
                continue;
            }
            // find begin and end of param name
            final int pstart = tstart + "TEMPLATE-".length();
            int pend = len;
            for (pend = pstart; pend < len; pend++) {
                final char c = text.charAt(pend);
                if (c == ' ' || c == '\t' || c == '-')
                    break;
            }
            if (pend >= len)
                return null;
            final String param = text.substring(pstart, pend);
            // If it's the correct one, return the comment extent
            if (param.equals(name))
                return new int[] {cstart, cend};
            // System.out.println("Found param: {"+param+"} in comment: "+
            // text.substring(cstart, cend) +"}");
            // Else try the next one
            start = cend;
        }
        // Not found
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return this.buff.toString();
    }

    /**
     * Write.
     *
     * @param out
     *            the out
     */
    public void write(final PrintWriter out) {
        out.println(this.toString());
    }

    /**
     * Write.
     *
     * @param out
     *            the out
     */
    public void write(final PrintStream out) {
        out.println(this.toString());
    }

    /**
     * usage: filename param value.
     *
     * @param args
     *            the arguments
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    public static void main(final String[] args) throws IOException {
        final String filename = args[0];
        final String param = args[1];
        final String value = args[2];
        final FileReader fr = new FileReader(filename);
        final String templateText = SimpleTemplate.getStringFromStream(fr);
        final SimpleTemplate template = new SimpleTemplate(templateText);
        template.replace(param, value);
        template.write(System.out);
    }

    /**
     * Sets the cache templates.
     *
     * @param b
     *            the new cache templates
     */
    public static void setCacheTemplates(final boolean b) {
        cacheTemplates = b;
    }
}
