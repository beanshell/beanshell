package bsh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/** Core support for the scripted object editor: This.of, recorded definitions, source text. */
public class ScriptedSourceTest {

    private Interpreter interpreter;

    @Before
    public void setUp() {
        interpreter = new Interpreter();
    }

    private Object eval(String... code) throws Exception {
        return interpreter.eval(TestUtil.script(code));
    }

    // ---- This.of ------------------------------------------------------

    @Test
    public void thisOf_aThis() throws Exception {
        Object obj = eval("Object mk(Object p) { return this; }", "mk(1);");
        assertTrue(obj instanceof This);
        assertSame(obj, This.of(obj));
    }

    @Test
    public void thisOf_interfaceProxy() throws Exception {
        Object proxy = eval("new Runnable() { run() { } };");
        assertTrue(java.lang.reflect.Proxy.isProxyClass(proxy.getClass()));
        assertNotNull(This.of(proxy));
        assertEquals("AnonymousBlock", This.of(proxy).getNameSpace().getName());
    }

    @Test
    public void thisOf_generatedClassInstance() throws Exception {
        Object task = eval("new java.util.TimerTask() { run() { } };");
        assertTrue(Reflect.isGeneratedClass(task.getClass()));
        This self = This.of(task);
        assertNotNull(self);
        assertSame(Reflect.getThisNS(task), self.getNameSpace());
    }

    @Test
    public void thisOf_nonScriptedObject() throws Exception {
        assertNull(This.of(null));
        assertNull(This.of("a string"));
        assertNull(This.of(new Object()));
        // a proxy that is not backed by a This
        Object other = java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[] { Runnable.class },
            (proxy, method, args) -> null);
        assertNull(This.of(other));
    }

    // ---- recorded definitions ------------------------------------------

    @Test
    public void declaringMethod_isSetOnMethodThis() throws Exception {
        This self = (This) eval("Object mk(Object p) { return this; }", "mk(1);");
        BshMethod method = self.getNameSpace().getDeclaringMethod();
        assertNotNull(method);
        assertEquals("mk", method.getName());
        assertEquals("mk", ScriptedSource.of(method).name());
    }

    @Test
    public void declaringMethod_isNotSetOnGlobalNamespace() throws Exception {
        assertNull(interpreter.getNameSpace().getDeclaringMethod());
        assertNull(interpreter.getNameSpace().getAllocation());
    }

    @Test
    public void allocation_isRecordedOnInterfaceInstance() throws Exception {
        Object proxy = eval("new Runnable() { run() { } };");
        AllocationInfo info = This.of(proxy).getNameSpace().getAllocation();
        assertNotNull(info);
        assertSame(Runnable.class, info.getType());
        assertEquals(0, info.getArgs().length);
        assertSame(interpreter.getNameSpace(), info.getScope());
    }

    @Test
    public void allocation_isRecordedOnClassInstanceWithEvaluatedArgs() throws Exception {
        eval("int start = 5;");
        Object obj = eval("new java.util.concurrent.atomic.AtomicInteger(start + 1) { };");
        AllocationInfo info = This.of(obj).getNameSpace().getAllocation();
        assertNotNull(info);
        assertSame(java.util.concurrent.atomic.AtomicInteger.class, info.getType());
        Object[] args = info.getArgs();
        assertEquals(1, args.length);
        assertEquals(6, ((Number) Primitive.unwrap(args[0])).intValue());
        // the argument values are a copy
        args[0] = "changed";
        assertEquals(6, ((Number) Primitive.unwrap(info.getArgs()[0])).intValue());
    }

    @Test
    public void ordinaryAllocationIsUnaffected() throws Exception {
        assertEquals("x", eval("new String(\"x\");"));
        assertEquals(3, ((Number) eval("new java.util.concurrent.atomic.AtomicInteger(3) { }.get();"))
            .intValue());
    }

    // ---- getSourceText --------------------------------------------------

    private static final String COMMENTED = TestUtil.script(
        "/** Adds one. */",
        "int inc(int a) {",
        "    // step",
        "    return a + 1; /* tail",
        "       comment */",
        "}").trim();

    @Test
    public void sourceText_keepsCommentsAndLayout() throws Exception {
        assertEquals(COMMENTED, ScriptedSource.parseMethod(COMMENTED).sourceText());
    }

    @Test
    public void sourceText_isRelativeToFirstColumn() throws Exception {
        String indented = "      foo(a) {\n        return a;\n      }";
        assertEquals("foo(a) {\n  return a;\n}",
            ScriptedSource.parseMethod(indented).sourceText());
    }

    @Test
    public void sourceText_nestedIndentation() throws Exception {
        String text = TestUtil.script(
            "foo(a) {",
            "    if (a) {",
            "        return 1;",
            "    }",
            "    return 2;",
            "}").trim();
        assertEquals(text, ScriptedSource.parseMethod(text).sourceText());
    }

    @Test
    public void sourceText_ofRecordedMethodAndAllocation() throws Exception {
        This self = (This) eval(
            "// builds a thing",
            "Object mk(Object p) { return this; }",
            "mk(1);");
        // a comment ahead of the declaration is part of its source text
        assertEquals("// builds a thing\nObject mk(Object p) { return this; }",
            ScriptedSource.of(self.getNameSpace().getDeclaringMethod()).sourceText());

        Object proxy = eval("new Runnable() { run() { /* nothing */ } };");
        assertEquals("new Runnable() { run() { /* nothing */ } }",
            ScriptedSource.of(This.of(proxy).getNameSpace().getAllocation()).sourceText());
    }

    // ---- ScriptedSource: methods ------------------------------------------

    @Test
    public void method_accessors() throws Exception {
        ScriptedSource source = ScriptedSource.parseMethod(TestUtil.script(
            "Callable mk(Object a, int b) {",
            "    int count = 0;",
            "    call() { return a; }",
            "    call(x) { return x; }",
            "    return this;",
            "}"));
        assertEquals(ScriptedSource.Kind.METHOD, source.kind());
        assertEquals("mk", source.name());
        assertEquals("( Object a , int b )", source.parameterText());
        assertTrue(source.hasBody());
        assertEquals(Arrays.asList("int count = 0", "return this ;"), source.nonMethodStatements());

        List<ScriptedSource.Member> methods = source.methods();
        assertEquals(2, methods.size());
        assertEquals("call", methods.get(0).name());
        assertEquals(0, methods.get(0).parameterCount());
        assertEquals("( )", methods.get(0).parameterText());
        assertEquals("call( )", methods.get(0).signature());
        assertEquals(1, methods.get(1).parameterCount());
        assertEquals("call( x )", methods.get(1).signature());
    }

    @Test
    public void method_defineIn() throws Exception {
        ScriptedSource source = ScriptedSource.parseMethod("Object mk(Object p) { return this; }");
        NameSpace scratch = new NameSpace(interpreter.getNameSpace(), "scratch");
        BshMethod method = source.defineIn(scratch, interpreter);
        assertEquals("mk", method.getName());
        assertSame(method, scratch.getMethods()[0]);
        assertEquals(0, interpreter.getNameSpace().getMethods().length);
        assertTrue(method.invoke(new Object[] { 1 }, interpreter) instanceof This);
    }

    @Test
    public void method_defineMethodsIn() throws Exception {
        ScriptedSource source = ScriptedSource.parseMethod(
            "Object mk(Object p) { a() { return 1; } b() { return 2; } return this; }");
        NameSpace target = new NameSpace(interpreter.getNameSpace(), "target");
        source.defineMethodsIn(target, interpreter);
        assertEquals(2, target.getMethods().length);
    }

    // ---- ScriptedSource: anonymous classes --------------------------------

    @Test
    public void allocation_accessors() throws Exception {
        ScriptedSource source = ScriptedSource.parseAllocation(TestUtil.script(
            "new javax.swing.AbstractAction(label, 5) {",
            "    String field = \"x\";",
            "    actionPerformed(e) { }",
            "    helper(a, b) { }",
            "}"));
        assertEquals(ScriptedSource.Kind.ANONYMOUS_CLASS, source.kind());
        assertEquals("javax.swing.AbstractAction", source.name().replace(" ", ""));
        assertEquals("( label , 5 )", source.parameterText());
        assertTrue(source.hasBody());
        assertEquals(Collections.singletonList("String field = \"x\""), source.nonMethodStatements());
        assertEquals(2, source.methods().size());
        assertEquals(2, source.methods().get(1).parameterCount());
    }

    @Test
    public void allocation_constructAnonymousUsesGivenArguments() throws Exception {
        ScriptedSource source = ScriptedSource.parseAllocation(
            "new java.util.concurrent.atomic.AtomicInteger(unusedVariable) { }");
        Object obj = source.constructAnonymous(java.util.concurrent.atomic.AtomicInteger.class,
            new Object[] { 7 }, interpreter.getNameSpace(), interpreter);
        assertEquals(7, ((java.util.concurrent.atomic.AtomicInteger) obj).get());
        assertNotNull(This.of(obj).getNameSpace().getAllocation());
    }

    @Test
    public void allocation_constructAnonymousFromRecordedAllocation() throws Exception {
        eval("int start = 5;");
        Object first = eval("new java.util.concurrent.atomic.AtomicInteger(start) { };");
        AllocationInfo info = This.of(first).getNameSpace().getAllocation();
        eval("start = 99;");
        Object second = ScriptedSource.of(info).constructAnonymous(info, info.getScope(), interpreter);
        assertEquals(5, ((java.util.concurrent.atomic.AtomicInteger) second).get());
    }

    // ---- ScriptedSource: parse failures -------------------------------------

    private static void assertRejectsMethod(String text) {
        try {
            ScriptedSource.parseMethod(text);
            fail("expected a ParseException for: " + text);
        } catch (ParseException expected) {
            // fine
        }
    }

    private static void assertRejectsAllocation(String text) {
        try {
            ScriptedSource.parseAllocation(text);
            fail("expected a ParseException for: " + text);
        } catch (ParseException expected) {
            // fine
        }
    }

    @Test
    public void parseMethod_rejectsEmptyMultipleAndWrongKind() {
        assertRejectsMethod("");
        assertRejectsMethod("   ");
        assertRejectsMethod("a() { } b() { }");
        assertRejectsMethod("int x = 1;");
        assertRejectsMethod("new Runnable() { run() { } };");
    }

    @Test
    public void parseAllocation_rejectsEmptyMultipleAndWrongKind() {
        assertRejectsAllocation("");
        assertRejectsAllocation("new Runnable() { run() { } }; new Runnable() { run() { } };");
        assertRejectsAllocation("a() { }");
        assertRejectsAllocation("new Object();");              // no body
        assertRejectsAllocation("int x = 1;");
    }

    @Test
    public void parseAllocation_acceptsTrailingSemicolonOrNone() throws Exception {
        assertEquals("Runnable", ScriptedSource.parseAllocation("new Runnable() { };").name());
        assertEquals("Runnable", ScriptedSource.parseAllocation("new Runnable() { }").name());
    }

    @Test
    public void wrongKind_isRejectedWhenEvaluating() throws Exception {
        ScriptedSource method = ScriptedSource.parseMethod("a() { }");
        try {
            method.constructAnonymous(Runnable.class, new Object[0],
                interpreter.getNameSpace(), interpreter);
            fail();
        } catch (IllegalStateException expected) {
            // fine
        }
        try {
            ScriptedSource.parseAllocation("new Runnable() { }")
                .defineIn(interpreter.getNameSpace(), interpreter);
            fail();
        } catch (IllegalStateException expected) {
            // fine
        }
    }

    // ---- coverage of the remaining paths ---------------------------------------

    @Test
    public void toString_describesTheSource() throws Exception {
        ScriptedSource method = ScriptedSource.parseMethod("Object mk(Object p) { a() { } return this; }");
        assertEquals("METHOD mk( Object p )", method.toString());
        assertEquals("a( )", method.methods().get(0).toString());
        assertTrue(ScriptedSource.parseAllocation("new Runnable() { }").toString()
            .startsWith("ANONYMOUS_CLASS Runnable"));
    }

    @Test
    public void of_methodWithoutDeclarationIsRejected() throws Exception {
        NameSpace ns = new NameSpace(interpreter.getNameSpace(), "javaMethods");
        ns.importObject(new java.util.ArrayList<Object>());
        BshMethod javaMethod = ns.getMethod("size", new Class<?>[0]);
        assertNotNull(javaMethod);
        try {
            ScriptedSource.of(javaMethod);
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("size"));
        }
    }

    @Test
    public void methodWithoutBody_hasNoBodyMembers() throws Exception {
        ScriptedSource source = ScriptedSource.parseMethod("int size(int a);");
        assertEquals(false, source.hasBody());
        assertTrue(source.methods().isEmpty());
        assertTrue(source.nonMethodStatements().isEmpty());
    }

    @Test
    public void defineIn_findsTheMethodAmongOthers() throws Exception {
        NameSpace target = new NameSpace(interpreter.getNameSpace(), "target");
        ScriptedSource.parseMethod("pick(x) { return 1; }").defineIn(target, interpreter);
        ScriptedSource.parseMethod("other() { return 2; }").defineIn(target, interpreter);
        BshMethod defined = ScriptedSource.parseMethod("pick() { return 3; }").defineIn(target, interpreter);
        assertEquals(0, defined.getParameterNames().length);
        assertEquals(3, target.getMethods().length);
    }
}
