package bsh.scripteditor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import org.junit.Before;
import org.junit.Test;

import bsh.Interpreter;
import bsh.ParseException;
import bsh.Primitive;
import bsh.This;

/** The scripted object editor, working on a simplified "zone summary" example. */
public class ScriptedObjectEditorTest {

    private static final String SUMMARY = String.join("\n",
        "Callable zoneSummary(Object zonedef) {",
        "    String call() { return \"zone:\" + zonedef; }",
        "    return this;",
        "}");

    private Interpreter interpreter;
    private ScriptedObjectEditor editor;
    private List<String> log;
    private StringBuilder zone;

    @Before
    public void setUp() throws Exception {
        interpreter = new Interpreter();
        editor = new ScriptedObjectEditor(interpreter);
        log = new ArrayList<>();
        zone = new StringBuilder("alpha");
        interpreter.set("log", log);
        interpreter.set("zone", zone);
        interpreter.eval("import java.util.concurrent.Callable;");
    }

    private Object summary() throws Exception {
        interpreter.eval(SUMMARY);
        return interpreter.eval("zoneSummary(zone);");
    }

    private Object callOf(Object summary) throws Exception {
        return ((Callable<?>) summary).call();
    }

    private static String withBody(String body) {
        return String.join("\n",
            "Callable zoneSummary(Object zonedef) {",
            "    " + body,
            "    return this;",
            "}");
    }

    private Object runnable() throws Exception {
        return interpreter.eval("new Runnable() { run() { log.add(\"before\"); } };");
    }

    private Object timerTask() throws Exception {
        return interpreter.eval("new java.util.TimerTask() { public void run() { log.add(\"before\"); } };");
    }

    private static final String RUNNABLE_AFTER =
        "new Runnable() { run() { log.add(\"after\"); } }";
    private static final String TASK_AFTER =
        "new java.util.TimerTask() { public void run() { log.add(\"after\"); } }";

    // ---- method-created objects --------------------------------------------

    @Test
    public void sourceText_ofMethodObject() throws Exception {
        assertEquals(SUMMARY, editor.sourceText(summary()));
    }

    @Test
    public void reinstantiate_keepsParameterIdentity() throws Exception {
        Object original = summary();
        ScriptedInstance copy = editor.reinstantiate(original);
        assertNotSame(original, copy.object());
        assertEquals("zone:alpha", ((Callable<?>) copy.as(Callable.class)).call());
        zone.append("-beta");
        assertSame(zone, copy.self().getNameSpace().getVariable("zonedef", false));
        copy.check();
    }

    @Test
    public void reinstantiateEdited_changesBehaviourOnlyOfNewObject() throws Exception {
        Object original = summary();
        String edited = SUMMARY.replace("\"zone:\"", "\"edited:\"");
        ScriptedInstance copy = editor.reinstantiateEdited(original, edited);
        assertEquals("edited:alpha", copy.as(Callable.class).call());
        assertEquals("zone:alpha", callOf(original));
        // the shared definition is unchanged
        assertEquals("zone:alpha", callOf(interpreter.eval("zoneSummary(zone);")));
        assertEquals(SUMMARY, editor.sourceText(original));
        assertEquals(edited, editor.sourceText(copy.object()));
    }

    @Test
    public void checkEdit_rejectsContractChangesWithoutEvaluating() throws Exception {
        Object original = summary();
        String touch = "touched = true;";
        assertRejected(original, withBody(touch).replace("zoneSummary", "renamed"));
        assertRejected(original, withBody(touch).replace("(Object zonedef)", "(Object zonedef, int n)"));
        assertRejected(original, withBody(touch).replace("(Object zonedef)", "(Object other)"));
        assertRejected(original, "not a method declaration;");
        assertRejected(original, SUMMARY + "\n" + SUMMARY);
        assertNull("nothing was evaluated", interpreter.get("touched"));
    }

    @Test
    public void checkEdit_acceptsBodyChange() throws Exception {
        editor.checkEdit(summary(), SUMMARY.replace("zone:", "other:"));
    }

    private void assertRejected(Object object, String text) throws Exception {
        try {
            editor.checkEdit(object, text);
            fail("expected the edit to be rejected: " + text);
        } catch (ParseException expected) {
            // fine
        }
        try {
            editor.reinstantiateEdited(object, text);
            fail("expected reinstantiateEdited to reject: " + text);
        } catch (ParseException expected) {
            // fine
        }
    }

    @Test
    public void parameterReassignedInBody_isAContractViolation() throws Exception {
        Object original = summary();
        try {
            editor.reinstantiateEdited(original, withBody("zonedef = \"other\";"));
            fail();
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("zonedef"));
        }
    }

    @Test
    public void parameterReassignedInMethod_isCaughtThroughTheInstance() throws Exception {
        Object original = summary();
        String edited = SUMMARY.replace("return \"zone:\" + zonedef;",
            "zonedef = \"other\"; return \"x\";");
        ScriptedInstance copy = editor.reinstantiateEdited(original, edited);
        try {
            copy.as(Callable.class).call();
            fail();
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("zonedef"));
        }
        try {
            copy.invoke("call");
            fail();
        } catch (IllegalStateException expected) {
            // fine
        }
    }

    @Test
    public void patchInPlace_changesExistingMethodObject() throws Exception {
        Object original = summary();
        Object otherReference = original;
        editor.patchInPlace(original, SUMMARY.replace("\"zone:\"", "\"patched:\""));
        assertEquals("patched:alpha", callOf(original));
        assertEquals("patched:alpha", callOf(otherReference));
        // later instances from the shared definition are not affected
        assertEquals("zone:alpha", callOf(interpreter.eval("zoneSummary(zone);")));
    }

    @Test
    public void patchInPlace_updatesSourceTextAndReinstantiate() throws Exception {
        Object original = summary();
        String patched = SUMMARY.replace("\"zone:\"", "\"patched:\"");
        editor.patchInPlace(original, patched);
        assertEquals(patched, editor.sourceText(original));
        assertEquals("patched:alpha", editor.reinstantiate(original).as(Callable.class).call());
    }

    @Test
    public void patchInPlace_rejectsChangedStatementAndRemovedMethod() throws Exception {
        interpreter.eval(String.join("\n",
            "Callable withField(Object zonedef) {",
            "    int level = 1;",
            "    call() { return level; }",
            "    other() { return 2; }",
            "    return this;",
            "}"));
        Object obj = interpreter.eval("withField(zone);");
        String text = editor.sourceText(obj);
        expectIllegalArgument(obj, text.replace("level = 1", "level = 2"));
        expectIllegalArgument(obj, text.replace("    other() { return 2; }\n", ""));
        editor.patchInPlace(obj, text.replace("return 2;", "return 3;"));
        assertEquals(3, ((Number) Primitive.unwrap(This.of(obj).invokeMethod("other", new Object[0]))).intValue());
    }

    @Test
    public void patchInPlace_twice_revertRestoresOriginal() throws Exception {
        Object original = summary();
        editor.patchInPlace(original, SUMMARY.replace("\"zone:\"", "\"one:\""));
        assertEquals("one:alpha", callOf(original));
        editor.patchInPlace(original, SUMMARY);
        assertEquals("zone:alpha", callOf(original));
        assertEquals(SUMMARY, editor.sourceText(original));
    }

    @Test
    public void commit_affectsNewInstancesOnly() throws Exception {
        Object existing = summary();
        editor.commit(existing, SUMMARY.replace("\"zone:\"", "\"committed:\""));
        assertEquals("zone:alpha", callOf(existing));
        assertEquals("committed:alpha", callOf(interpreter.eval("zoneSummary(zone);")));
    }

    // ---- anonymous interface implementations -----------------------------------

    @Test
    public void anonymousInterface_sourceTextAndReinstantiate() throws Exception {
        Object r = runnable();
        assertEquals("new Runnable() { run() { log.add(\"before\"); } }", editor.sourceText(r));
        ScriptedInstance copy = editor.reinstantiate(r);
        assertNotSame(r, copy.object());
        copy.as(Runnable.class).run();
        assertEquals(Arrays.asList("before"), log);
    }

    @Test
    public void anonymousInterface_reinstantiateEdited() throws Exception {
        Object r = runnable();
        editor.reinstantiateEdited(r, RUNNABLE_AFTER).as(Runnable.class).run();
        ((Runnable) r).run();
        assertEquals(Arrays.asList("after", "before"), log);
    }

    @Test
    public void anonymousInterface_patchInPlace() throws Exception {
        Object r = runnable();
        Runnable other = (Runnable) r;
        editor.patchInPlace(r, RUNNABLE_AFTER);
        other.run();
        assertEquals(Arrays.asList("after"), log);
        assertEquals(RUNNABLE_AFTER, editor.sourceText(r));
        editor.reinstantiate(r).as(Runnable.class).run();
        assertEquals(Arrays.asList("after", "after"), log);
    }

    // ---- anonymous subclasses ------------------------------------------------

    @Test
    public void anonymousClass_reinstantiateEdited() throws Exception {
        Object task = timerTask();
        ScriptedInstance copy = editor.reinstantiateEdited(task, TASK_AFTER);
        copy.as(Runnable.class).run();
        ((Runnable) task).run();
        assertEquals(Arrays.asList("after", "before"), log);
    }

    @Test
    public void anonymousClass_patchInPlace() throws Exception {
        Object task = timerTask();
        editor.patchInPlace(task, TASK_AFTER);
        ((Runnable) task).run();
        assertEquals(Arrays.asList("after"), log);
    }

    @Test
    public void anonymousClass_patchRejectsNewOverrideButAcceptsHelper() throws Exception {
        Object task = timerTask();
        String withCancel = "new java.util.TimerTask() { public void run() { log.add(\"before\"); } "
            + "public boolean cancel() { return true; } }";
        expectIllegalArgument(task, withCancel);

        String withHelper = "new java.util.TimerTask() { public void run() { log.add(helper()); } "
            + "helper() { return \"helped\"; } }";
        editor.patchInPlace(task, withHelper);
        ((Runnable) task).run();
        assertEquals(Arrays.asList("helped"), log);
    }

    @Test
    public void anonymous_patchRejectsChangedStatementAndRemovedMethod() throws Exception {
        Object r = interpreter.eval(
            "new Runnable() { int n = 1; run() { log.add(\"a\"); } extra() { } };");
        expectIllegalArgument(r,
            "new Runnable() { int n = 2; run() { log.add(\"a\"); } extra() { } }");
        expectIllegalArgument(r, "new Runnable() { int n = 1; run() { log.add(\"a\"); } }");
    }

    @Test
    public void anonymous_patchTwice_revertRestoresOriginal() throws Exception {
        Object r = runnable();
        String original = editor.sourceText(r);
        editor.patchInPlace(r, RUNNABLE_AFTER);
        editor.patchInPlace(r, original);
        ((Runnable) r).run();
        assertEquals(Arrays.asList("before"), log);
    }

    @Test
    public void anonymous_checkEditRejectsTypeAndArgumentChanges() throws Exception {
        Object r = runnable();
        assertRejected(r, "new Callable() { call() { touched = true; return 1; } }");
        assertRejected(r, "new Runnable(1) { run() { touched = true; } }");
        assertRejected(r, "new Runnable() ");
        assertNull(interpreter.get("touched"));

        Object action = interpreter.eval(
            "new javax.swing.AbstractAction(\"name\") { actionPerformed(e) { } };");
        assertRejected(action,
            "new javax.swing.AbstractAction(\"other\") { actionPerformed(e) { touched = true; } }");
        assertNull(interpreter.get("touched"));
    }

    @Test
    public void anonymousClass_constructorArgumentsAreNotReEvaluated() throws Exception {
        interpreter.set("label", "first");
        Object action = interpreter.eval(
            "new javax.swing.AbstractAction(label) { actionPerformed(e) { } };");
        interpreter.set("label", "second");
        javax.swing.AbstractAction copy = (javax.swing.AbstractAction) editor
            .reinstantiate(action).object();
        assertEquals("first", copy.getValue(javax.swing.Action.NAME));
        javax.swing.AbstractAction edited = (javax.swing.AbstractAction) editor
            .reinstantiateEdited(action,
                "new javax.swing.AbstractAction(label) { actionPerformed(e) { log.add(\"e\"); } }")
            .object();
        assertEquals("first", edited.getValue(javax.swing.Action.NAME));
    }

    @Test
    public void anonymous_commitIsUnsupported() throws Exception {
        try {
            editor.commit(runnable(), RUNNABLE_AFTER);
            fail();
        } catch (UnsupportedOperationException expected) {
            // fine
        }
    }

    // ---- coverage of the remaining paths ---------------------------------------

    private static final String OBJECT_SUMMARY = String.join("\n",
        "Object zoneSummary(Object zonedef) {",
        "    String call() { return \"zone:\" + zonedef; }",
        "    return this;",
        "}");

    private static final String COUNTER = String.join("\n",
        "Object counter(int n) {",
        "    Object call() { return n + 1; }",
        "    return this;",
        "}");

    private static final String FIXTURE = "new bsh.scripteditor.OverrideFixture() { "
        + "public String work() { return \"w\"; } %s}";

    private Object fixture() throws Exception {
        return interpreter.eval(String.format(FIXTURE, "") + ";");
    }

    @Test
    public void as_classType_isAPlainCast() throws Exception {
        ScriptedInstance copy = editor.reinstantiate(timerTask());
        assertSame(copy.object(), copy.as(java.util.TimerTask.class));
    }

    @Test
    public void as_interfaceTheObjectDoesNotImplement_isBackedByTheThis() throws Exception {
        interpreter.eval(OBJECT_SUMMARY);
        Object obj = interpreter.eval("zoneSummary(zone);");
        assertTrue(obj instanceof This);
        ScriptedInstance copy = editor.reinstantiate(obj);
        assertEquals("zone:alpha", copy.as(Callable.class).call());
    }

    @Test
    public void as_interfaceRefusedBySecurityGuard_isReported() throws Exception {
        interpreter.eval(OBJECT_SUMMARY);
        ScriptedInstance copy = editor.reinstantiate(interpreter.eval("zoneSummary(zone);"));
        bsh.security.SecurityGuard noCallable = new bsh.security.SecurityGuard() {
            @Override
            public boolean canImplements(Class<?> type) {
                return type != Callable.class;
            }
        };
        Interpreter.mainSecurityGuard.add(noCallable);
        try {
            copy.as(Callable.class);
            fail();
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("Callable"));
            assertTrue(expected.getCause() instanceof bsh.security.SecurityError);
        } finally {
            Interpreter.mainSecurityGuard.remove(noCallable);
        }
    }

    @Test
    public void exceptionFromScriptedMethod_passesThroughTheTypedView() throws Exception {
        ScriptedInstance copy = editor.reinstantiateEdited(summary(), SUMMARY.replace(
            "return \"zone:\" + zonedef;", "throw new java.io.IOException(\"boom\");"));
        try {
            copy.as(Callable.class).call();
            fail();
        } catch (java.io.IOException expected) {
            assertEquals("boom", expected.getMessage());
        }
    }

    @Test
    public void invoke_returnsTheResultAndChecks() throws Exception {
        ScriptedInstance copy = editor.reinstantiate(summary());
        assertEquals("zone:alpha", copy.invoke("call"));
    }

    @Test
    public void primitiveParameter_isKeptAndChecked() throws Exception {
        interpreter.eval(COUNTER);
        Object obj = interpreter.eval("counter(5);");
        ScriptedInstance copy = editor.reinstantiate(obj);
        assertEquals(6, ((Number) Primitive.unwrap(copy.invoke("call"))).intValue());

        ScriptedInstance edited = editor.reinstantiateEdited(obj,
            COUNTER.replace("return n + 1;", "n = 99; return n;"));
        try {
            edited.invoke("call");
            fail();
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("'n'"));
        }
    }

    @Test
    public void methodObject_patchCanAddAHelperMethod() throws Exception {
        Object obj = summary();
        editor.patchInPlace(obj, SUMMARY.replace("    return this;",
            "    String extra() { return \"extra\"; }\n    return this;"));
        assertEquals("extra", This.of(obj).invokeMethod("extra", new Object[0]));
    }

    @Test
    public void methodObject_editWithoutBodyIsRejected() throws Exception {
        try {
            editor.checkEdit(summary(), "Callable zoneSummary(Object zonedef);");
            fail();
        } catch (ParseException expected) {
            // fine
        }
    }

    @Test
    public void methodObject_editThatDoesNotReturnThisIsRejected() throws Exception {
        interpreter.eval(OBJECT_SUMMARY);
        Object obj = interpreter.eval("zoneSummary(zone);");
        try {
            editor.reinstantiateEdited(obj, OBJECT_SUMMARY.replace("return this;", "return \"plain\";"));
            fail();
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("did not return a scripted object"));
        }
    }

    @Test
    public void anonymous_editMayEndWithAComment() throws Exception {
        Object r = runnable();
        String edited = RUNNABLE_AFTER + " // changed by the user";
        editor.checkEdit(r, edited);
        editor.reinstantiateEdited(r, edited).as(Runnable.class).run();
        assertEquals(Arrays.asList("after"), log);
        editor.patchInPlace(r, edited);
        ((Runnable) r).run();
        assertEquals(Arrays.asList("after", "after"), log);
    }

    @Test
    public void anonymous_validCheckEditPasses() throws Exception {
        editor.checkEdit(runnable(), RUNNABLE_AFTER);
        editor.checkEdit(timerTask(), TASK_AFTER);
    }

    @Test
    public void anonymousInterface_patchCanAddAHelperMethod() throws Exception {
        Object r = runnable();
        editor.patchInPlace(r,
            "new Runnable() { run() { log.add(helper()); } helper() { return \"h\"; } }");
        ((Runnable) r).run();
        assertEquals(Arrays.asList("h"), log);
    }

    @Test
    public void anonymousClass_patchRejectsOverridingAnInheritedProtectedMethod() throws Exception {
        expectIllegalArgument(fixture(),
            String.format(FIXTURE, "protected String hook() { return \"changed\"; } "));
    }

    @Test
    public void anonymousClass_patchRejectsANewOverloadOnlyWhenArityMatches() throws Exception {
        Object obj = fixture();
        // same name, different arity: not an override, so it is accepted
        editor.patchInPlace(obj, String.format(FIXTURE, "hook(a) { return a; } "));
        editor.patchInPlace(obj, String.format(FIXTURE, "hook(a) { return a; } work(x) { return x; } "));
    }

    @Test
    public void anonymousClass_patchAcceptsMethodsThatAreNotInherited() throws Exception {
        Object obj = fixture();
        // a private method is not inherited, a static one is not overridden
        editor.patchInPlace(obj, String.format(FIXTURE, "secret() { return \"mine\"; } "));
        editor.patchInPlace(obj, String.format(FIXTURE, "secret() { return \"mine\"; } shared() { return 1; } "));
        assertEquals("w", ((bsh.scripteditor.OverrideFixture) obj).work());
    }

    // ---- entry point ---------------------------------------------------------

    @Test
    public void definitionOf_rejectsGlobalThisAndNonScripted() throws Exception {
        Object global = interpreter.eval("this;");
        try {
            editor.definitionOf(global);
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("global 'this'"));
        }
        try {
            editor.definitionOf("a string");
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("Not a scripted object"));
        }
    }

    private void expectIllegalArgument(Object obj, String text) throws Exception {
        try {
            editor.patchInPlace(obj, text);
            fail("expected the patch to be rejected: " + text);
        } catch (IllegalArgumentException expected) {
            // fine
        }
    }
}
