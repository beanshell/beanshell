package bsh.scripteditor;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import bsh.AllocationInfo;
import bsh.Interpreter;
import bsh.NameSpace;
import bsh.ParseException;
import bsh.ScriptedSource;
import bsh.This;

/**
 * Definition of an anonymous class instance, "new Type(args) { ... }":
 *   - Type is an interface: the object is a Proxy backed by a This;
 *   - Type is a class: the object is an instance of a generated class whose
 *     methods delegate to its instance This.
 *
 * The originally evaluated constructor arguments are reused; argument expressions
 * in the edited text must match the original but are not re-evaluated.
 */
final class AnonymousClassDefinition implements ScriptedDefinition {

    private final Interpreter interp;
    private final NameSpace instanceNs;

    AnonymousClassDefinition(Interpreter interp, This self) {
        this.interp = interp;
        this.instanceNs = self.getNameSpace();
    }

    /** Re-read each time: patchInPlace() records the patched expression. */
    private AllocationInfo info() {
        return instanceNs.getAllocation();
    }

    @Override
    public String sourceText() {
        return ScriptedSource.of(info()).sourceText();
    }

    @Override
    public void checkEdit(String editedText) throws ParseException {
        parseEdit(editedText);
    }

    @Override
    public ScriptedInstance reinstantiate() throws Exception {
        AllocationInfo info = info();
        return construct(ScriptedSource.of(info), info.getScope());
    }

    @Override
    public ScriptedInstance reinstantiateEdited(String editedText) throws Exception {
        ScriptedSource edited = parseEdit(editedText);
        NameSpace scratch = new NameSpace(info().getScope(), "edit_anon");
        return construct(edited, scratch);
    }

    @Override
    public void patchInPlace(String editedText) throws Exception {
        ScriptedSource edited = parseEdit(editedText);
        AllocationInfo info = info();
        Class<?> type = info.getType();
        Patcher.patch(instanceNs, ScriptedSource.of(info), edited,
                      // A generated class has delegators only for the methods it was
                      // generated with, so a new override would never be called.
                      member -> type.isInterface() || !overridesInherited(type, member),
                      interp);
        instanceNs.setAllocation(info.withSource(edited));
    }

    @Override
    public void commit(String editedText) {
        throw new UnsupportedOperationException(
            "An anonymous class has no shared definition to replace. Use patchInPlace(), or "
            + "store the result of reinstantiateEdited() where the old object was kept.");
    }

    // ------------------------------------------------------------------

    private ScriptedInstance construct(ScriptedSource source, NameSpace scope) throws Exception {
        Object obj = source.constructAnonymous(info(), scope, interp);
        This self = This.of(obj);
        if (self == null)
            throw new IllegalStateException("Anonymous class instance has no scripted namespace");
        return new ScriptedInstance(obj, self, new String[0], new Object[0]);
    }

    private ScriptedSource parseEdit(String editedText) throws ParseException {
        ScriptedSource edited = ScriptedSource.parseAllocation(editedText);
        ScriptedSource original = ScriptedSource.of(info());
        if (!edited.name().equals(original.name()))
            throw new ParseException("The type may not be changed");
        if (!edited.parameterText().equals(original.parameterText()))
            throw new ParseException("The constructor arguments may not be changed");
        return edited;
    }

    /** True if the declaration overrides or implements an inherited method (name + arity). */
    private static boolean overridesInherited(Class<?> type, ScriptedSource.Member member) {
        String name = member.name();
        int arity = member.parameterCount();
        for (Method m : type.getMethods())                     // public, incl. interface methods
            if (m.getName().equals(name) && m.getParameterCount() == arity)
                return true;
        for (Class<?> c = type; c != null; c = c.getSuperclass())
            for (Method m : c.getDeclaredMethods())            // protected / package-private
                if (m.getName().equals(name) && m.getParameterCount() == arity
                        && !Modifier.isPrivate(m.getModifiers()) && !Modifier.isStatic(m.getModifiers()))
                    return true;
        return false;
    }
}
