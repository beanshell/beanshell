package bsh.scripteditor;

import bsh.BshMethod;
import bsh.EvalError;
import bsh.Interpreter;
import bsh.NameSpace;
import bsh.ParseException;
import bsh.ScriptedSource;
import bsh.This;
import bsh.UtilEvalError;

/**
 * Definition of a scripted object created by a method that returns 'this',
 * e.g. "Callable htmlZoneSummary(Object zonedef) { ... return this; }".
 */
final class MethodDefinition implements ScriptedDefinition {

    private final Interpreter interp;
    private final NameSpace instanceNs;

    MethodDefinition(Interpreter interp, This self) {
        this.interp = interp;
        this.instanceNs = self.getNameSpace();
    }

    /** Re-read each time: patchInPlace() replaces it with the patched declaration. */
    private BshMethod method() {
        return instanceNs.getDeclaringMethod();
    }

    private ScriptedSource source() {
        return ScriptedSource.of(method());
    }

    @Override
    public String sourceText() {
        return source().sourceText();
    }

    @Override
    public void checkEdit(String editedText) throws ParseException {
        parseEdit(editedText);
    }

    @Override
    public ScriptedInstance reinstantiate() throws Exception {
        return instantiate(method(), parameterValues());
    }

    @Override
    public ScriptedInstance reinstantiateEdited(String editedText) throws Exception {
        ScriptedSource edited = parseEdit(editedText);
        return instantiate(defineInScratch(edited), parameterValues());
    }

    @Override
    public void patchInPlace(String editedText) throws Exception {
        ScriptedSource edited = parseEdit(editedText);
        Patcher.patch(instanceNs, source(), edited,
                      member -> true,          // a This dispatches any method by name
                      interp);
        // Record the patched declaration so sourceText() and reinstantiate() reflect it.
        instanceNs.setDeclaringMethod(defineInScratch(edited));
    }

    /** Future calls of the method use the edit. Existing instances are unaffected. */
    @Override
    public void commit(String editedText) throws Exception {
        parseEdit(editedText).defineIn(instanceNs.getParent(), interp);
    }

    // ------------------------------------------------------------------

    private ScriptedSource parseEdit(String editedText) throws ParseException {
        ScriptedSource edited = ScriptedSource.parseMethod(editedText);
        ScriptedSource original = source();
        if (!edited.name().equals(original.name()))
            throw new ParseException("The method name may not be changed");
        if (!edited.parameterText().equals(original.parameterText()))
            throw new ParseException("The parameter list may not be changed");
        if (!edited.hasBody())
            throw new ParseException("The method must have a body");
        return edited;
    }

    /** Evaluate a declaration in a fresh child of the declaring namespace. */
    private BshMethod defineInScratch(ScriptedSource decl) throws EvalError {
        NameSpace scratch = new NameSpace(instanceNs.getParent(), "edit_" + decl.name());
        return decl.defineIn(scratch, interp);
    }

    private ScriptedInstance instantiate(BshMethod m, Object[] args) throws Exception {
        Object obj = m.invoke(args, interp);           // runs the "constructor" body
        This self = This.of(obj);
        if (self == null)
            throw new IllegalStateException(m.getName() + " did not return a scripted object (this)");
        ScriptedInstance inst = new ScriptedInstance(obj, self, m.getParameterNames(), args);
        inst.check();
        return inst;
    }

    /** Values as stored, so primitive parameters stay wrapped as bsh.Primitive. */
    private Object[] parameterValues() throws UtilEvalError {
        String[] names = method().getParameterNames();
        Object[] values = new Object[names.length];
        for (int i = 0; i < names.length; i++)
            values[i] = instanceNs.getVariable(names[i], false);
        return values;
    }
}
