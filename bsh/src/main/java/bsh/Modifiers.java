/** Copyright 2023 Nick nickl- Lombard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. */
package bsh;

import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/** Bsh equivalent and compatible, for the most part, with JAVA's Modifiers.
 * The JAVA Modifier class does not include default as a modifier for methods
 * and although it does define enum as a modifier, albeit not accessible, it
 * does not include it as a valid field modifier. For these we were obligated
 * to make accommodations for. */
public class Modifiers implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    public static final int CLASS=0, INTERFACE=1, METHOD=2, FIELD=3, PARAMETER=4, CONSTRUCTOR=5;
    public static final Map<String, Integer> CONST = new HashMap<>(17);
    static {
        CONST.put("public", Modifier.PUBLIC);
        CONST.put("private", Modifier.PRIVATE);
        CONST.put("protected", Modifier.PROTECTED);
        CONST.put("static", Modifier.STATIC);
        CONST.put("final", Modifier.FINAL);
        CONST.put("synchronized", Modifier.SYNCHRONIZED);
        CONST.put("volatile", Modifier.VOLATILE);
        CONST.put("transient", Modifier.TRANSIENT);
        CONST.put("native", Modifier.NATIVE);
        CONST.put("interface", Modifier.INTERFACE);
        CONST.put("abstract", Modifier.ABSTRACT);
        CONST.put("strict", Modifier.STRICT);
        CONST.put("synthetic", 4096);
        CONST.put("annotation", 8192);
        CONST.put("enum", 16384);  // not visible from Modifier
        CONST.put("mandated", 32768);
        CONST.put("default", 65536); // not included in Modifier
    }
    private static final int ACCESS_MODIFIERS = 7;
    private String type;
    private int valid, context, modifiers = 0;

    /** Default constructor specifying the context these modifiers apply to.
     * @param context identifier for the application of modifiers */
    public Modifiers(int context) {
        appliedContext(context);
    }

    /** Add a modifier by modifier name. Will do a constant lookup to retrieve
     * the integer bitwise specifier corresponding te the name.
     * @param name the string name of the modifier to add. */
    public void addModifier( String name ) {
        addModifier(toModifier(name));
    }

    /** Add modifier by unique identifier number. The validity of the identifier
     * applicable to this context is verified and whether only one of the access
     * modifiers public, private, or protected is used which will result in an
     * IllegalStateException thrown if failed.
     * The duplicate check has been removed because the mathematical assignment
     * will simply roll duplicates in automatically, as apposed to maintaining a
     * list of names.
     * @param mod unique bit definition for a modifier to apply */
    public void addModifier( int mod ) {
        if ((valid & mod) == 0)
            throw new IllegalStateException(type + " cannot be declared '" + toModifier(mod) + "'");
        else if (mod < ACCESS_MODIFIERS && (modifiers & ACCESS_MODIFIERS) > 0 && (modifiers | mod) != modifiers)
            throw new IllegalStateException("public/private/protected cannot be used in combination." );
        modifiers |= mod;
    }

    /** Add multiple modifiers defined by a destinct bit definition. We can itterate
     * through the supplied definition and mathematically detect which modifiers are
     * to be added one by one.
     * @param mods distinct bit definition of modifiers to apply */
    public void addModifiers(int mods) {
        for (int mod = 1; mod <= mods; mod *= 2)
            if ((mods & mod) != 0)
                addModifier(mod);
    }

    /** Retrieve the distinct bit definition of all applied modifiers. This value
     * can be decoded with the Modifier class as it is compatible with the modifiers
     * returned from Class or Member.
     * @return distinct bit definition of applied modifiers */
    public int getModifiers() {
        return modifiers;
    }

    /** Change the current applied context and revalidate the modifiers. This allows
     * us to capture modifiers in one context then later change the context without
     * loosing the existing modifiers which are also applicable to the new context.
     * @param context identifier for the application of modifiers */
    public void changeContext(int context) {
        int mods = modifiers;
        modifiers = 0;
        appliedContext(context);
        addModifiers(mods);
    }

    /** Verify whether the expected application context is configured. Used to ensure
     * the expected context has been configured for the purpose, before changing the
     * context unecessarily.
     * @param context the query to verify
     * @return true if the supplied context is cenfigured. */
    public boolean isAppliedContext(int context) {
        return this.context == context;
    }

    /** Query whether the modifier has been applied.
     * @param name modifier name for lookup
     * @return true if modifier is applied */
    public boolean hasModifier( String name ) {
        return hasModifier(toModifier(name));
    }

    /** Query whether the modifier has been applied.
     * @param mod unique modifier bit value for lookup
     * @return true if modifier is applied */
    public boolean hasModifier( int mod ) {
        return (modifiers & mod) != 0;
    }

    /** Convenience method to assign modifiers for a constant definition. */
    public void setConstant() {
        modifiers = Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL;
    }

    /** Configures the applied context for these modifiers. For each context there is
     * a destinct bit definition that corresponds to the valid or allowed modifiers
     * applicable. The type name of the context is kept for informative error reporting.
     * @param context identifier for the application of modifiers */
    private void appliedContext(int context) {
        this.context = context;
        switch( context ) {
            case CLASS:
                valid = Modifier.classModifiers();
                type = "Class";
                break;
            case INTERFACE:
                valid = Modifier.interfaceModifiers();
                type = "Interface";
                break;
            case METHOD:
                valid = Modifier.methodModifiers() | CONST.get("default");
                type = "Method";
                break;
            case FIELD:
                valid = Modifier.fieldModifiers() | CONST.get("enum");
                type = "Field";
                break;
            case PARAMETER:
                valid = Modifier.parameterModifiers();
                type = "Parameter";
                break;
            case CONSTRUCTOR:
                valid = Modifier.constructorModifiers();
                type = "Constructor";
                break;
            default:
                valid = 0;
                type = "Unknown";
        }
    }

    /** Modifier name to distinct bit value lookup. If the name is not found it
     * is considered unknown and an IllegalStateException will be thrown.
     * @param name the modifier name
     * @return corresponding bit value */
    private int toModifier(String name) {
        Integer mod = CONST.get(name);
        if (null == mod)
            throw new IllegalStateException("Unknown modifier: '" + name + "'");
        return mod.intValue();
    }

    /** Distinct bit identifier to modifier name lookup, This is a convenience
     * method to produce a legible form of a modifier and is not relied upon to
     * produce a valid answer. If no corresponding name was found a string value
     * of the number is returned.
     * @param mod the bit value of a modifier
     * @return corresponding modifier name */
    private String toModifier(int mod) {
        for (String name:CONST.keySet())
            if (mod == CONST.get(name))
                return name;
        return String.valueOf(mod);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "Modifiers: " + Modifier.toString(modifiers) + (
            (modifiers & CONST.get("enum")) != 0 ? " enum" :
            (modifiers & CONST.get("default")) != 0 ? " default" : "");
    }
}
