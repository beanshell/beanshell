package bsh.scripteditor;

import bsh.Interpreter;
import bsh.NameSpace;
import bsh.ParseException;
import bsh.This;

/**
 * Entry point for editing scripted objects: show an object's definition as source
 * text, let the user edit it, and create a new object from the edit.
 * <p>
 * The objects that can be edited are those whose definition the interpreter recorded
 * when it created them:
 * <ul>
 * <li>a method that returned <code>this</code>, whether the result is a
 *     {@link This} or an interface proxy for one;</li>
 * <li>an anonymous interface implementation, <code>new Runnable() { ... }</code>;</li>
 * <li>an anonymous subclass, <code>new java.util.TimerTask() { ... }</code>.</li>
 * </ul>
 * Create the editor with the {@link Interpreter} that created the objects. The
 * operations delegate to the {@link ScriptedDefinition} that {@link #definitionOf(Object)}
 * returns, which documents the rules for edits.
 * <p>
 * Typical use:
 * <pre>
 *   ScriptedObjectEditor editor = new ScriptedObjectEditor(interpreter);
 *   String text = editor.sourceText(summary);                 // show in the editor
 *   editor.checkEdit(summary, editedText);                    // optional early check
 *   ScriptedInstance inst = editor.reinstantiateEdited(summary, editedText);
 *   Object html = inst.as(Callable.class).call();
 * </pre>
 * Instances are not documented as thread safe, as the interpreter itself is not.
 */
public class ScriptedObjectEditor {

    private final Interpreter interp;

    /** @param interp the interpreter that created the scripted objects to edit */
    public ScriptedObjectEditor(Interpreter interp) {
        this.interp = interp;
    }

    /**
     * The editable definition behind a scripted object.
     * @param obj a scripted object: a {@link This}, an interface proxy, or an
     *   instance of a generated anonymous class
     * @throws IllegalArgumentException if the object is not scripted, or has no recorded
     *   definition (for example the global <code>this</code>)
     */
    public ScriptedDefinition definitionOf(Object obj) {
        This self = This.of(obj);
        if (self == null)
            throw new IllegalArgumentException("Not a scripted object: " + obj);
        NameSpace ns = self.getNameSpace();
        if (ns.getAllocation() != null)
            return new AnonymousClassDefinition(interp, self);
        if (ns.getDeclaringMethod() != null)
            return new MethodDefinition(interp, self);
        throw new IllegalArgumentException(
            "Scripted object has no recorded definition (e.g. the global 'this')");
    }

    /**
     * The source text of an object's definition, comments included.
     * @throws IllegalArgumentException as {@link #definitionOf(Object)}
     * @see ScriptedDefinition#sourceText()
     */
    public String sourceText(Object obj) {
        return definitionOf(obj).sourceText();
    }

    /**
     * Validate edited text without evaluating anything.
     * @throws ParseException if the text does not parse or breaks the edit contract
     * @throws IllegalArgumentException as {@link #definitionOf(Object)}
     * @see ScriptedDefinition#checkEdit(String)
     */
    public void checkEdit(Object obj, String editedText) throws ParseException {
        definitionOf(obj).checkEdit(editedText);
    }

    /**
     * A new object from the object's current definition and original inputs.
     * @throws IllegalArgumentException as {@link #definitionOf(Object)}
     * @see ScriptedDefinition#reinstantiate()
     */
    public ScriptedInstance reinstantiate(Object obj) throws Exception {
        return definitionOf(obj).reinstantiate();
    }

    /**
     * A new object from edited text and the original inputs; the original object
     * is not changed.
     * @throws ParseException if the edit breaks the contract
     * @throws IllegalArgumentException as {@link #definitionOf(Object)}
     * @throws IllegalStateException if the edit reassigns a parameter
     * @see ScriptedDefinition#reinstantiateEdited(String)
     */
    public ScriptedInstance reinstantiateEdited(Object obj, String editedText) throws Exception {
        return definitionOf(obj).reinstantiateEdited(editedText);
    }

    /**
     * Change the existing object so every reference to it sees the edit. Only method
     * declarations may differ.
     * @throws IllegalArgumentException if the edit changes more than method declarations,
     *   or as {@link #definitionOf(Object)}
     * @see ScriptedDefinition#patchInPlace(String)
     */
    public void patchInPlace(Object obj, String editedText) throws Exception {
        definitionOf(obj).patchInPlace(editedText);
    }

    /**
     * Make the edit the definition used by later calls of the method that created
     * the object; existing objects are not changed.
     * @throws UnsupportedOperationException for an anonymous class
     * @see ScriptedDefinition#commit(String)
     */
    public void commit(Object obj, String editedText) throws Exception {
        definitionOf(obj).commit(editedText);
    }
}
