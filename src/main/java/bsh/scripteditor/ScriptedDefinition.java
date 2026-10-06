package bsh.scripteditor;

import bsh.ParseException;

/**
 * The editable definition behind a scripted object. Get one from
 * {@link ScriptedObjectEditor#definitionOf(Object)}.
 * <p>
 * Contract for every edit: the definition's identity and inputs may not change.
 * <ul>
 * <li>Method-created objects: same method name and parameter list; parameters
 *     may not be reassigned.</li>
 * <li>Anonymous classes: same type and constructor argument expressions; the
 *     originally evaluated argument values are reused, not evaluated again.</li>
 * </ul>
 * Edited text is a single method declaration for a method-created object, or a
 * single <code>new Type(args) { ... }</code> expression for an anonymous class
 * (a trailing semicolon is optional).
 */
public interface ScriptedDefinition {

    /**
     * Source text of the definition, reconstructed from its tokens, so comments are
     * included and tabs come back as spaces. For a method-created object this is the
     * method declaration; for an anonymous class it is the <code>new Type(args) { ... }</code>
     * expression. It reflects any earlier {@link #patchInPlace(String)}.
     */
    String sourceText();

    /**
     * Validate edited text against the contract without evaluating anything.
     * @throws ParseException if the text does not parse, is not exactly one declaration
     *   or expression of the right kind, has no body, or changes the method name,
     *   parameter list, anonymous type or constructor argument expressions
     */
    void checkEdit(String editedText) throws ParseException;

    /**
     * A fresh instance from the current definition, with the original inputs: the
     * same parameter values for a method-created object, the same evaluated
     * constructor arguments for an anonymous class. The new object starts with
     * fresh state; the original object is not changed. For a method-created object
     * the method body runs again.
     * @throws bsh.EvalError if the script fails
     * @throws IllegalStateException if a parameter is reassigned by the body
     */
    ScriptedInstance reinstantiate() throws Exception;

    /**
     * A fresh instance from edited text, with the original inputs. The edit is evaluated
     * in a scratch namespace whose parent is the original scope, so enclosing variables
     * stay visible but no shared definition is replaced. The original object is not
     * changed, and state is not carried over to the new object.
     * @throws ParseException if the edit breaks the contract (see {@link #checkEdit(String)})
     * @throws bsh.EvalError if the script fails
     * @throws IllegalStateException if the edit reassigns a parameter
     */
    ScriptedInstance reinstantiateEdited(String editedText) throws Exception;

    /**
     * Apply edited method declarations to the existing object, so every reference to it
     * sees the change and its state is kept. Only method declarations may differ: other
     * statements in the body, such as field declarations, must be unchanged, methods may
     * not be removed, and for a generated class (an anonymous subclass) a new method may
     * not override an inherited one, because the generated class could never call it.
     * Anything else needs {@link #reinstantiateEdited(String)}.
     * <p>
     * Every method declaration in the edit is re-evaluated, not only the changed ones,
     * because the object may hold an earlier patch. {@link #sourceText()} and
     * {@link #reinstantiate()} reflect the patch afterwards.
     * @throws ParseException if the edit breaks the contract (see {@link #checkEdit(String)})
     * @throws IllegalArgumentException if the edit changes more than method declarations
     * @throws bsh.EvalError if the script fails
     */
    void patchInPlace(String editedText) throws Exception;

    /**
     * Make edited text the definition used from now on, for a method-created object:
     * the method is redefined in the scope it was declared in, so later calls of the
     * method create objects from the edit. Objects that already exist are not changed.
     * An anonymous class has no shared definition to replace and throws
     * {@link UnsupportedOperationException}; use {@link #patchInPlace(String)} or keep
     * the result of {@link #reinstantiateEdited(String)}.
     * @throws ParseException if the edit breaks the contract (see {@link #checkEdit(String)})
     * @throws UnsupportedOperationException for an anonymous class
     * @throws bsh.EvalError if the script fails
     */
    void commit(String editedText) throws Exception;
}
