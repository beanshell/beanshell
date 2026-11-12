package bsh.scripteditor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

import bsh.EvalError;
import bsh.Interpreter;
import bsh.NameSpace;
import bsh.ScriptedSource;
import bsh.ScriptedSource.Member;

/** The rules for patching an edit into an existing instance namespace. */
final class Patcher {

    private Patcher() { }

    /**
     * Evaluate the edited body's method declarations into an existing instance namespace.
     * Refuses if anything other than method declarations changed, if a method was removed,
     * or if an added method fails addAllowed.
     */
    static void patch(NameSpace target, ScriptedSource original, ScriptedSource edited,
            Predicate<Member> addAllowed, Interpreter interpreter) throws EvalError {
        if ( !original.nonMethodStatements().equals(edited.nonMethodStatements()) )
            throw new IllegalArgumentException(
                "Only method declarations can be patched in place; other changes to the body "
                + "need reinstantiateEdited()");

        Map<String, Member> before = bySignature(original);
        Map<String, Member> after = bySignature(edited);

        Set<String> removed = new TreeSet<>(before.keySet());
        removed.removeAll(after.keySet());
        if ( !removed.isEmpty() )
            throw new IllegalArgumentException(
                "Methods cannot be removed in place: " + removed + "; use reinstantiateEdited()");

        for ( Map.Entry<String, Member> e : after.entrySet() )
            if ( !before.containsKey(e.getKey()) && !addAllowed.test(e.getValue()) )
                throw new IllegalArgumentException(
                    "New method " + e.getKey() + " overrides an inherited method, which the "
                    + "generated class cannot dispatch to; use reinstantiateEdited()");

        // Evaluate every method, not only changed ones: the instance may hold an earlier patch.
        edited.defineMethodsIn(target, interpreter);
    }

    private static Map<String, Member> bySignature(ScriptedSource source) {
        Map<String, Member> out = new LinkedHashMap<>();
        for ( Member member : source.methods() )
            out.put(member.signature(), member);
        return out;
    }
}
