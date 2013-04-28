package bsh;

import bsh.classpath.ClassManagerImpl;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.lang.ref.WeakReference;
import java.util.concurrent.Callable;

import static bsh.TestUtil.eval;

/**
 * todo:<p>
 * <a href="http://sourceforge.net/tracker/?func=detail&aid=1641777&group_id=4075&atid=104075">Sourceforge issue "clone is broken" - ID: 1641777</a>.<p>
 * <a href="http://sourceforge.net/tracker/?func=detail&aid=1656318&group_id=4075&atid=104075">Sourceforge issue "There is a bug, when implementing interfaces" - ID: 1656318</a>.<p>
 * <a href="http://sourceforge.net/tracker/?func=detail&aid=1641052&group_id=4075&atid=104075">Sourceforge issue "grammar bug: final causes syntax error" - ID: 1641052</a>.<p>
 * <a href="http://sourceforge.net/tracker/?func=detail&aid=1657960&group_id=4075&atid=104075">Sourceforge issue "Parse error when parsing valid Java code (2.0b4)" - ID: 1657960</a>.<p>
 * <a href="http://sourceforge.net/tracker/?func=detail&aid=1631576&group_id=4075&atid=104075">Sourceforge issue "Cannot anonymous subclass with arguments" - ID: 1631576</a>.<p>
 * <a href="http://sourceforge.net/tracker/?func=detail&aid=1554705&group_id=4075&atid=104075">Sourceforge issue "Memory leak due to unclaimed Tokens" - ID: 1554705</a>.<p>
 * <a href="http://sourceforge.net/tracker/?func=detail&aid=1529825&group_id=4075&atid=104075">Sourceforge issue "Self reference in an Interpreter causes memory leak" - ID: 1529825</a>.<p>
 * <a href="http://sourceforge.net/tracker/?func=detail&aid=1627493&group_id=4075&atid=104075">Sourceforge issue "Problem defining anonymous Action object" - ID: 1627493</a>.<p>
 */
@RunWith(FilteredTestRunner.class)
public class SourceForgeIssuesTest {

    @After
    public void after() {
        Interpreter.DEBUG = false;
    }


    /**
     * <a href="http://sourceforge.net/tracker/?func=detail&aid=2898046&group_id=4075&atid=104075">Sourceforge issue "Error HERE! thrown while SAX parsing" - ID: 2898046</a>.
     */
    @Test
    public void sourceforge_issue_2898046() throws Exception {
        final String CODE_2898046 =
                /* 1*/ "import javax.xml.parsers.*;\n" +
                /* 2*/ "import org.xml.sax.InputSource;\n" +
                /* 3*/ "events = new ArrayList();" +
                /* 4*/ "factory = SAXParserFactory.newInstance();\n" +
                /* 5*/ "saxParser = factory.newSAXParser();\n" +
                /* 6*/ "parser = saxParser.getXMLReader();\n" +
                /* 7*/ "parser.setContentHandler( this );\n" +
                /* 8*/ "\n" +
                /* 9*/ "invoke( name, args ) {\n" +
                /*10*/ "	events.add( name );\n" +
                /*11*/ "}\n" +
                /*12*/ "\n" +
                /*13*/ "source = new InputSource(new StringReader(\"<xml>test</xml>\"));\n" +
                /*14*/ "parser.parse( source );" +
                /*15*/ "return events;";
        Assert.assertEquals("[setDocumentLocator, startDocument, startElement, characters, endElement, endDocument]", eval(CODE_2898046).toString());
    }


    /**
     * <a href="http://sourceforge.net/tracker/?func=detail&aid=2884749&group_id=4075&atid=104075">Sourceforge issue "Memory leak with WeakReferences" - ID: 2884749</a>.
     */
    @Test
    public void sourceforge_issue_2884749() throws Exception {
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
        Assert.assertNull(weakRef.get());
    }


    /**
     * <a href="http://sourceforge.net/tracker/?func=detail&aid=2945459&group_id=4075&atid=104075">Sourceforge issue "Parsing of long hex literals fails" - ID: 2945459</a>.
     */
    @Test
    public void sourceforge_issue_2945459() throws Exception {
        Assert.assertEquals(0x0000000001L, eval("long foo = 0x0000000001L;", "return foo"));
    }


    /**
     * <a href="http://sourceforge.net/tracker/?func=detail&aid=2562805&group_id=4075&atid=104075">Sourceforge issue "Debug fails if called method argument is null" - ID: 2562805</a>.
     */
    @Test
    public void sourceforge_issue_2562805() throws Exception {
        Interpreter.DEBUG = true;
        eval("System.out.println(null);");
    }


    /**
     * <a href="http://sourceforge.net/tracker/?func=detail&aid=2081602&group_id=4075&atid=104075">Sourceforge issue "NullPointerException Thrown by Overriden Method" - ID: 2081602</a>.
     * Just a "learning test" to check the call flow for constructors of generated classes.
     *
     * @see #sourceforge_issue_2081602
     */
    @Test
    public void sourceforge_issue_2081602_learning_test() throws Exception {
        final Object result = TestUtil.eval("Object echo(msg, x) {", "   print(msg + ' ' + x);", "   return x;", "}", "public class A implements java.util.concurrent.Callable {", "   int _id;", "   public A (int id) {", "      _id = echo(\"A\", id);", "   }", "   public Object call() { return _id; }", "}", "public class B extends A {", "   public B (int id) {", "      super (echo(\"B\", id * 2));", "   }", "}", "return new B (2);");
        Assert.assertEquals(4, ((java.util.concurrent.Callable) result).call());
    }


    /**
     * <a href="http://sourceforge.net/tracker/?func=detail&aid=2081602&group_id=4075&atid=104075">Sourceforge issue "NullPointerException Thrown by Overriden Method" - ID: 2081602</a>.
     * Overriding a method which is invoked from super-constructor issues a NPE.
     */
    @Test
    @Category(KnownIssue.class)
    public void sourceforge_issue_2081602() throws Exception {
        // Interpreter.DEBUG = true;
        Callable result = (Callable) TestUtil.eval("Object echo(msg, x) {", "   print(msg + ' ' + x);", "   return x;", "}", "public class A implements " + Callable.class.getName() + " {", "   int _id;", "   public A (int id) {", "      print (\" A.<init> \" + id);", "      setId(id);", "   }", "   public void setId (int id) {", "      print (\" A.setId \" + id);", "      _id = id;", "   }", "   public Object call() { return _id; }", "}", "public class B extends A {", "   public B (int id) {", "      super (echo(\" B.<init>\", id * 3));", "   }", "   public void setId (int id) {", "      print (\" B.setId \" + id);", "      super.setId(id * 5);", "   }", "}", "return new B (1);");
        Assert.assertEquals(15, result.call());
    }


    /**
     * <a href="http://sourceforge.net/tracker/?func=detail&aid=1897313&group_id=4075&atid=104075">Sourceforge issue "error when looping over collections containing null" - ID: 1897313</a>.
     */
    @Test
    public void sourceforge_issue_1897313() throws Exception {
        eval("for (x: new String[]{\"foo\",null,\"bar\"}) { System.out.println(x); }");
    }


    /**
     * <a href="http://sourceforge.net/tracker/?func=detail&aid=1796035&group_id=4075&atid=104075">Sourceforge issue "Grammar error when defining arrays" - ID: 1796035</a>.
     */
    @Test
    @Category(KnownIssue.class)
    public void sourceforge_issue_1796035() throws Exception {
        eval("byte array[] = new byte[0]; return array;");
    }


    /**
     * <a href="http://sourceforge.net/tracker/?func=detail&aid=1796035&group_id=4075&atid=104075">Sourceforge issue "Grammar error when defining arrays" - ID: 1796035</a>.
     */
    @Test
    public void sourceforge_issue() throws Exception {
        Assert.assertEquals("A", eval("import " + getClass().getName() + "; return " + getClass().getSimpleName() + ".A.staticMethod();"));
        Assert.assertEquals("B", eval("import " + getClass().getName() + "; return " + getClass().getSimpleName() + ".B.staticMethod();"));
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
        Assert.assertEquals(true, TestUtil.eval("return true == true;"));
        Assert.assertEquals(true, TestUtil.eval("return false == false;"));
        Assert.assertEquals(false, TestUtil.eval("return true == false;"));
        Assert.assertEquals(false, TestUtil.eval("return false == true;"));
        try {
            TestUtil.eval("throw new RuntimeException();");
            Assert.fail();
        } catch (TargetError e) {
            Assert.assertTrue(e.getTarget().getClass() == RuntimeException.class);
        }
        Assert.assertEquals("foobar", TestUtil.eval("String a;", "try {", "	a = \"foobar\";", "} catch (Exception e) {", "	throw e;", "}", "return a;"));
        String script = "boolean fieldBool = false;\n" +
                "int fieldInt = 0;\n" +
                "Boolean fieldBool2 = false;\n" +
                "void run() {\n" +
                "fieldBool = ! fieldBool;\n" +
                "fieldBool2 = ! fieldBool2;\n" +
                "fieldInt++;\n" +
                "System.out.println(\"fieldBool: \"+fieldBool);\n" +
                "System.out.println(\"fieldBool2: \"+fieldBool2);\n" +
                "System.out.println(\"fieldInt: \"+fieldInt);\n" +
                "}\n";
        Interpreter bsh = new Interpreter();
        bsh.eval(script);
        Runnable runnable = (Runnable) bsh.getInterface(Runnable.class);
        runnable.run();
    }


    private static class DummyListener implements BshClassManager.Listener {

        final byte[] _memory;


        public DummyListener(int numBytes) {
            _memory = new byte[numBytes];
        }


        public void classLoaderChanged() {
            // noop
        }

    }
}
