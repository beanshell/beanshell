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
/****************************************************************************/

package bsh;

import bsh.classpath.ClassManagerImpl;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import static bsh.Capabilities.haveAccessibility;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.hamcrest.Matchers.instanceOf;
import static bsh.TestUtil.eval;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.lang.ref.WeakReference;
import java.util.concurrent.Callable;

@RunWith(FilteredTestRunner.class)
public class SourceForgeIssuesTest {

    @After
    public void after() {
        Interpreter.DEBUG.set(false);
    }

//    @Test
//    @Category(KnownIssue.class)
//    public void skip_test() throws Throwable {
//
//    }

    /** <a href="http://sourceforge.net/tracker/?func=detail&aid=2898046&group_id=4075&atid=104075">Sourceforge issue "Error HERE! thrown while SAX parsing" - ID: 2898046</a>. */
    @Test
    public void sourceforge_issue_2898046_sax_parsing_error() throws Exception {
        assumeTrue("testing illegal access assumes accessibility", haveAccessibility());
        final String CODE_2898046 =
                /* 1*/ "import javax.xml.parsers.*;\n"+
                /* 2*/ "import org.xml.sax.InputSource;\n"+
                /* 3*/ "events = new ArrayList();"+
                /* 4*/ "factory = SAXParserFactory.newInstance();\n"+
                /* 5*/ "saxParser = factory.newSAXParser();\n"+
                /* 6*/ "parser = saxParser.getXMLReader();\n"+
                /* 7*/ "parser.setContentHandler( this );\n"+
                /* 8*/ "\n"+
                /* 9*/ "invoke( name, args ) {\n"+
                /*10*/ "    events.add( name );\n"+
                /*11*/ "}\n"+
                /*12*/ "\n"+
                /*13*/ "source = new InputSource(new StringReader(\"<xml>test</xml>\"));\n"+
                /*14*/ "parser.parse( source );" +
                /*15*/ "return events;";
        assertEquals(
                "[setDocumentLocator, startDocument, startElement, characters, endElement, endDocument]",
                eval(CODE_2898046).toString());
    }


    /** <a href="http://sourceforge.net/tracker/?func=detail&aid=2884749&group_id=4075&atid=104075">Sourceforge issue "Memory leak with WeakReferences" - ID: 2884749</a>. */
    @Test
    public void sourceforge_issue_2884749_weakreference_memory_leak() throws Exception {
        final ClassManagerImpl classManager = new ClassManagerImpl();
        final WeakReference<BshClassManager.Listener> weakRef;
        {
            final BshClassManager.Listener listener = new DummyListener(1024 * 1000);
            classManager.addListener(listener);
            weakRef = new WeakReference<BshClassManager.Listener>(listener);
        }
        for (int i = 0; i < 10000; i++) {
              classManager.addListener(new DummyListener(1024 * 100));
        }
        assertNull(weakRef.get());
    }

    /** <a href="http://sourceforge.net/tracker/?func=detail&aid=2562805&group_id=4075&atid=104075">Sourceforge issue "Debug fails if called method argument is null" - ID: 2562805</a>. */
    @Test
    public void sourceforge_issue_2562805_debug_nullpointerexception() throws Exception {
        try (ByteArrayOutputStream baOut = new ByteArrayOutputStream();
            PrintStream out = new PrintStream(baOut);
            Interpreter bsh = new Interpreter(new StringReader(""), out, out, false)) {
            Interpreter.DEBUG.set(true);
            String ret = "" + bsh.eval(
                "ByteArrayOutputStream baOut = new ByteArrayOutputStream();" +
                "PrintStream out = new PrintStream(baOut);" +
                "out.println(null);" +
                "String ret = baOut.toString();" +
                "out.close();" +
                "baOut.close();" +
                "return ret;"
            );
            Interpreter.DEBUG.set(false);
            assertEquals("null", ret.trim());
            assertTrue(baOut.toString().contains("args[0] = null type"));
        }
    }


    /** <a href="http://sourceforge.net/tracker/?func=detail&aid=2081602&group_id=4075&atid=104075">Sourceforge issue "NullPointerException Thrown by Overriden Method" - ID: 2081602</a>.
     * Just a "learning test" to check the call flow for constructors of generated classes.
     * @see #sourceforge_issue_2081602 */
    @Test
    public void sourceforge_issue_2081602_nullpointerexception_overridden_method() throws Exception {
        final Object result = eval(
                "Object echo(msg, x) {",
                "//   print(msg + ' ' + x);",
                "   return x;",
                "}",
                "public class A implements java.util.concurrent.Callable {",
                "   int _id;",
                "   public A (int id) {",
                "      _id = echo(\"A\", id);",
                "   }",
                "   public Object call() { return _id; }",
                "}",
                "public class B extends A {",
                "   public B (int id) {",
                "      super (echo(\"B\", id * 2));",
                "   }",
                "}",
                "return new B (2);");
        assertEquals(4, ( (Callable<?>) result).call());
    }


    /** <a href="http://sourceforge.net/tracker/?func=detail&aid=2081602&group_id=4075&atid=104075">Sourceforge issue "NullPointerException Thrown by Overriden Method" - ID: 2081602</a>.
     * Overriding a method which is invoked from super-constructor issues a NPE. */
    @Test
    public void sourceforge_issue_2081602_overridden_method_called_from_super() throws Exception {
        Callable<?> result = (Callable<?>) eval(
                "Object echo(msg, x) {",
                "//   print(msg + ' ' + x);",
                "   return x;",
                "}",
                "public class A implements " + Callable.class.getName() + " {",
                "   int _id;",
                "   public A (int id) {",
                "//      print (\" A.<init> \" + id);",
                "      setId(id);",
                "   }",
                "   public void setId (int id) {",
                "//      print (\" A.setId \" + id);",
                "      _id = id;",
                "   }",
                "   public Object call() { return _id; }",
                "}",
                "public class B extends A {",
                "   public B (int id) {",
                "      super (echo(\" B.<init>\", id * 3));",
                "   }",
                "   public void setId (int id) {",
                "//      print (\" B.setId \" + id);",
                "      super.setId(id * 5);",
                "   }",
                "}",
                "return new B (1);");
        assertEquals(15, result.call());
    }


    /** <a href="http://sourceforge.net/tracker/?func=detail&aid=1897313&group_id=4075&atid=104075">Sourceforge issue "error when looping over collections containing null" - ID: 1897313</a>.*/
    @Test
    public void sourceforge_issue_1897313_collection_containing_null() throws Exception {
        eval("for (x: new String[]{\"foo\",null,\"bar\"}) { a = x; }");
    }


    /** <a href="http://sourceforge.net/tracker/?func=detail&aid=1796035&group_id=4075&atid=104075">Sourceforge issue "Grammar error when defining arrays" - ID: 1796035</a>. */
    @Test
    public void sourceforge_issue_1796035_variable_declared_array() throws Exception {
        Object ret = eval("byte array[] = new byte[0]; return array;");
        assertThat(ret, instanceOf(new byte[0].getClass()));
        assertArrayEquals(new byte[0], (byte[]) ret);
        ret = eval("int array[][] = new int[0][0]; return array;");
        assertThat(ret, instanceOf(new int[0][0].getClass()));
        assertArrayEquals(new int[0][0], (int[][]) ret);
        ret = eval("array = new int[] {1,2}; return array;");
        assertThat(ret, instanceOf(new int[0].getClass()));
        assertArrayEquals(new int[] {1,2}, (int[]) ret);
        ret = eval("String array[] = new String[0]; return array;");
        assertThat(ret, instanceOf(new String[0].getClass()));
        assertArrayEquals(new String[0], (Object[]) ret);
        ret = eval("String array[] = new String[] {'foo'}; return array;");
        assertThat(ret, instanceOf(new String[0].getClass()));
        assertArrayEquals(new String[] {"foo"}, (Object[]) ret);
        ret = eval("String array[][] = new String[0][0]; return array;");
        assertThat(ret, instanceOf(new String[0][0].getClass()));
        assertArrayEquals(new String[0][0], (Object[][]) ret);
        ret = eval("String array[][] = new String[][] {new String[]{'foo'}}; return array;");
        assertThat(ret, instanceOf(new String[0][0].getClass()));
        assertArrayEquals(new String[][] { new String[] {"foo"} }, (Object[][]) ret);
    }


    public static class A {

        public static String staticMethod() {
            return "A";
        }
    }

    public static class B extends A {

        public static String staticMethod() {
            return "B";
        }
    }


    @Test
    public void misc_tests() throws Exception {
        assertEquals(true, eval("return true == true;"));
        assertEquals(true, eval("return false == false;"));
        assertEquals(false, eval("return true == false;"));
        assertEquals(false, eval("return false == true;"));
        try {
            eval("throw new RuntimeException();");
            fail();
        } catch (TargetError e) {
            assertTrue(e.getTarget().getClass() == RuntimeException.class);
        }
        assertEquals("foobar", eval("String a=null;", "try {", " a = \"foobar\";", "} catch (Exception e) {", "  throw e;", "}", "return a;"));
        String script = "boolean fieldBool = false;\n" +
                "int fieldInt = 0;\n" +
                "Boolean fieldBool2 = false;\n" +
                "void run() {\n" +
                "fieldBool = ! fieldBool;\n" +
                "fieldBool2 = ! fieldBool2;\n" +
                "fieldInt++;\n" +
                "//System.out.println(\"fieldBool: \"+fieldBool);\n" +
                "//System.out.println(\"fieldBool2: \"+fieldBool2);\n" +
                "//System.out.println(\"fieldInt: \"+fieldInt);\n" +
                "}\n";
        Interpreter bsh = new Interpreter();
        bsh.eval(script);
        Runnable runnable = (Runnable) bsh.getInterface(Runnable.class);
        runnable.run();
        bsh.close();
    }


    private static class DummyListener implements BshClassManager.Listener {

        @SuppressWarnings("unused")
        final byte[] _memory;


        public DummyListener(int numBytes) {
            _memory = new byte[numBytes];
        }


        public void classLoaderChanged() {
            // noop
        }

    }
}
