/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package bsh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.w3c.dom.Document;

public class BshDocTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void commentOnlyFilePreservesFirstFormalComment() throws Exception {
        Documentation docs = render("/* ignored */ /** FILE_DOC */ /** LATER_DOC */");
        assertEquals("FILE_DOC", docs.text("/BshDoc/File/Comment/Text"));
        assertEquals("1", docs.text("count(/BshDoc/File/Comment)"));
    }

    @Test
    public void trailingCommentIsPreserved() throws Exception {
        Documentation docs = render("value = 42; /** TRAILING_DOC */");
        assertEquals("TRAILING_DOC", docs.text("/BshDoc/File/Comment/Text"));
    }

    @Test
    public void trailingCommentDoesNotReplaceFileHeader() throws Exception {
        Documentation docs = render("/** FILE_DOC */ value = 42; /** TRAILING_DOC */");
        assertEquals("FILE_DOC", docs.text("/BshDoc/File/Comment/Text"));
        assertEquals("1", docs.text("count(/BshDoc/File/Comment)"));
    }

    @Test
    public void emptyAndOrdinaryCommentFilesHaveNoDocumentation() throws Exception {
        Documentation docs = render("", "/* ordinary */ // line\n");
        assertEquals("2", docs.text("count(/BshDoc/File)"));
        assertEquals("0", docs.text("count(//Comment)"));
    }

    @Test
    public void eofAccessorPreservesCommentOrderAndFiltersOrdinaryComments() throws Exception {
        Parser parser = new Parser(new StringReader(
                "/** FIRST */ /* ordinary */ /*** SECOND */"));
        assertTrue(parser.Line());
        assertEquals(ParserConstants.EOF, parser.getToken(0).kind);
        assertEquals(Arrays.asList("/** FIRST */", "/*** SECOND */"),
                Parser.getFormalCommentsBeforeToken(parser.getToken(0)));
        assertTrue(Parser.getFormalCommentsBeforeToken(null).isEmpty());
    }

    @Test
    public void adjacentFileAndMethodCommentsAreBothPreserved() throws Exception {
        Documentation docs = render("/** FILE_DOC */ /** METHOD_DOC */ f() { return 42; }");
        assertEquals("FILE_DOC", docs.text("/BshDoc/File/Comment/Text"));
        assertEquals("METHOD_DOC", docs.text("/BshDoc/File/Method/Comment/Text"));
        assertEquals("2", docs.text("count(//Comment)"));
    }

    @Test
    public void identicalFileAndMethodCommentTextRemainsSeparate() throws Exception {
        Documentation docs = render("/** SAME_DOC */ /** SAME_DOC */ f() {}");
        assertEquals("SAME_DOC", docs.text("/BshDoc/File/Comment/Text"));
        assertEquals("SAME_DOC", docs.text("/BshDoc/File/Method/Comment/Text"));
        assertEquals("2", docs.text("count(//Comment)"));
    }

    @Test
    public void separatedFileAndMultipleMethodCommentsKeepTheirOwners() throws Exception {
        Documentation docs = render("/** FILE_DOC */ value = 1;"
                + " /** FIRST_METHOD */ f() {} /** SECOND_METHOD */ g() {}");
        assertEquals("FILE_DOC", docs.text("/BshDoc/File/Comment/Text"));
        assertEquals("FIRST_METHOD", docs.text("/BshDoc/File/Method[Name='f']/Comment/Text"));
        assertEquals("SECOND_METHOD", docs.text("/BshDoc/File/Method[Name='g']/Comment/Text"));
        assertEquals("3", docs.text("count(//Comment)"));
    }

    @Test
    public void singleMethodCommentIsNotDuplicatedAsFileDocumentation() throws Exception {
        Documentation docs = render("/** METHOD_DOC */ f() {}");
        assertEquals("METHOD_DOC", docs.text("/BshDoc/File/Method/Comment/Text"));
        assertEquals("0", docs.text("count(/BshDoc/File/Comment)"));
        assertEquals("1", docs.text("count(//Comment)"));
    }

    @Test
    public void normalCliProducesXmlWithoutDebugOutput() throws Exception {
        Documentation docs = render("/** METHOD_DOC */ f() {}", "/** FILE_DOC */ value = 1;");
        assertEquals("2", docs.text("count(/BshDoc/File)"));
        assertEquals("METHOD_DOC", docs.text("/BshDoc/File[1]/Method/Comment/Text"));
        assertEquals("FILE_DOC", docs.text("/BshDoc/File[2]/Comment/Text"));
        assertEquals("", docs.stderr);
    }

    private Documentation render(String... sources) throws Exception {
        File output = temporary.newFile();
        File errors = temporary.newFile();
        String classpath = new File(Interpreter.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).getAbsolutePath();
        List<String> command = new ArrayList<>(Arrays.asList(
                new File(System.getProperty("java.home"), "bin/java").getAbsolutePath(),
                "-cp", classpath, Interpreter.class.getName(),
                new File("../scripts/bshdoc.bsh").getAbsolutePath()));
        for (int i = 0; i < sources.length; i++) {
            File script = temporary.newFile("input" + i + ".bsh");
            Files.write(script.toPath(), sources[i].getBytes(StandardCharsets.UTF_8));
            command.add(script.getAbsolutePath());
        }
        // Run the actual CLI without changing the test JVM's stderr or accessibility settings.
        Process process = new ProcessBuilder(command)
                .redirectOutput(output).redirectError(errors).start();
        try {
            assertTrue("bshdoc timed out", process.waitFor(30, TimeUnit.SECONDS));
            String stderr = new String(Files.readAllBytes(errors.toPath()), StandardCharsets.UTF_8);
            assertEquals(stderr, 0, process.exitValue());
            Document document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().parse(output);
            return new Documentation(document, stderr);
        } finally {
            process.destroy();
        }
    }

    private static class Documentation {
        private final Document document;
        private final String stderr;

        private Documentation(Document document, String stderr) {
            this.document = document;
            this.stderr = stderr;
        }

        private String text(String expression) throws Exception {
            return XPathFactory.newInstance().newXPath().evaluate(expression, document).trim();
        }
    }
}
