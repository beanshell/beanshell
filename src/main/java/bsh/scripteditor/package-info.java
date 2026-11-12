/**
 * Show, edit and re-create scripted objects.
 * <p>
 * A scripted object is an object whose behavior is defined in script text: a method
 * that returns <code>this</code>, or an anonymous class,
 * <code>new Type(args) { ... }</code>. This package lets an application get the source
 * of such an object, let a user edit it, and create a new object from the edit that
 * keeps the original inputs. It uses only the public API of the <code>bsh</code> package.
 * <p>
 * The workflow, with {@link bsh.scripteditor.ScriptedObjectEditor} as the entry point:
 * <ol>
 * <li><b>Get the source:</b> {@link bsh.scripteditor.ScriptedObjectEditor#sourceText(Object)}.</li>
 * <li><b>Validate an edit</b> without evaluating it:
 *     {@link bsh.scripteditor.ScriptedObjectEditor#checkEdit(Object, String)}.</li>
 * <li><b>Create the new object:</b>
 *     {@link bsh.scripteditor.ScriptedObjectEditor#reinstantiateEdited(Object, String)}
 *     returns a {@link bsh.scripteditor.ScriptedInstance}; its
 *     {@link bsh.scripteditor.ScriptedInstance#as(Class) as} method gives a typed view
 *     that keeps checking that the object's inputs are not reassigned.</li>
 * </ol>
 * The original object is not changed by these steps. Two further operations are
 * available when that is not what you need: patching an existing object in place
 * ({@link bsh.scripteditor.ScriptedObjectEditor#patchInPlace(Object, String)}), and making
 * an edit the definition that later calls of a method use
 * ({@link bsh.scripteditor.ScriptedObjectEditor#commit(Object, String)}).
 * <p>
 * An edit may not change what identifies the object. For a method-created object that
 * is the method name and parameter list; for an anonymous class it is the type and the
 * constructor argument expressions. The values the object was created with are reused,
 * not evaluated again.
 * <p>
 * Neither this package nor the interpreter is documented as thread safe.
 */
package bsh.scripteditor;
