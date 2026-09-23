package bsh.engine;

import javax.script.ScriptContext;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static javax.script.ScriptContext.ENGINE_SCOPE;

//  Adopted from http://ikayzo.org/svn/beanshell/BeanShell/engine/src/bsh/engine/ScriptContextEngineView.java
/**
 * This class implements an ENGINE_SCOPE centric Map view of the ScriptContext
 * for engine implementations.  This class can be used to simplify engine
 * implementations which have the capability to bind their namespaces to Maps
 * or other external interfaces.
 * <p>
 * Get operations on this view delegate to the
 * ScriptContext inheriting get() method that automatically traverses the
 * binding scopes in order or precedence.  Put operations on this view always
 * store values in the ENGINE_SCOPE bindings.  Other operations such as
 * size() and contains() are implemented appropriately, but perhaps not as
 * efficiently as possible.
 */
public class ScriptContextEngineView implements Map<String, Object> {

    ScriptContext context;


    public ScriptContextEngineView(ScriptContext context) {
        this.context = context;
    }


    /**
     * Returns the number of unique object bindings in all scopes.
     * (duplicate, shadowed, bindings count as a single binging).
     */
    @Override
    public int size() {
        return totalMap().size();
    }


    /**
     * Returns true if no bindings are present in any scope of the context.
     */
    @Override
    public boolean isEmpty() {
        return totalMap().isEmpty();
    }


    /**
     * Returns true if the key name is bound in any scope in the context.
     * The key must be a String.
     *
     * @param key key whose presence in this map is to be tested.
     * @return <code>true</code> if this map contains a mapping for the specified key.
     * @throws ClassCastException   if the key is of an inappropriate type for this
     *                              map (optional).
     * @throws NullPointerException if the key is <code>null</code> and this map does
     *                              not permit <code>null</code> keys (optional).
     */
    @Override
    public boolean containsKey(Object key) {
        return totalMap().containsKey(key);
    }


    /**
     * Returns <code>true</code> if this map maps one or more keys to the specified
     * value.  More formally, returns <code>true</code> if and only if this map
     * contains at least one mapping to a value <code>v</code> such that
     * <code>(value==null ? v==null : value.equals(v))</code>.  This operation will
     * probably require time linear in the map size for most implementations of the
     * <code>Map</code> interface.
     *
     * @param value value whose presence in this map is to be tested.
     * @return <code>true</code> if this map maps one or more keys to the specified
     *         value.
     * @throws ClassCastException   if the value is of an inappropriate type for this
     *                              map (optional).
     * @throws NullPointerException if the value is <code>null</code> and this map does
     *                              not permit <code>null</code> values (optional).
     */
    @Override
    public boolean containsValue(Object value) {
        return totalMap().containsValue(value);
    }


    /**
     * Returns the value bound in the most specific (lowest numbered)
     * bindings space for this key.
     * key must be a String.
     *
     * @param key key whose associated value is to be returned.
     * @return the value to which this map maps the specified key, or <code>null</code>
     *         if the map contains no mapping for this key.
     * @throws ClassCastException   if the key is of an inappropriate type for this
     *                              map (optional).
     * @throws NullPointerException if the key is <code>null</code> and this map does
     *                              not permit <code>null</code> keys (optional).
     * @see #containsKey(Object)
     */
    @Override
    public Object get(Object key) {
        return context.getAttribute((String) key);
    }


    /**
     * Set the key, value binding in the scope where the key is found, or
     * in the ENGINE_SCOPE if the key is not found.
     *
     * @param key   key with which the specified value is to be associated.
     * @param value value to be associated with the specified key.
     * @return previous value associated with specified key, or <code>null</code> if
     *         there was no mapping for key.  A <code>null</code> return can also
     *         indicate that the map previously associated <code>null</code> with the
     *         specified key, if the implementation supports <code>null</code> values.
     * @throws UnsupportedOperationException if the <code>put</code> operation is not
     *                                       supported by this map.
     * @throws ClassCastException           if the class of the specified key or value
     *                                       prevents it from being stored in this map.
     * @throws IllegalArgumentException   if some aspect of this key or value
     *                                       prevents it from being stored in this map.
     * @throws NullPointerException       if this map does not permit <code>null</code> keys
     *                                       or values, and the specified key or value is <code>null</code>.
     */
    @Override
    public Object put(String key, Object value) {
       /*
        * Check the scope for this key.  If not found then set the scope to
        * ENGINE_SCOPE
        */
       int scope = context.getAttributesScope(key);
       if (scope<0)
          scope = ENGINE_SCOPE;
        Object oldValue = context.getAttribute(key);

        /*
         * Update the value in the correct scope
         */
        context.setAttribute(key, value, scope);
        return oldValue;
    }


    /**
     * Put the bindings into the ENGINE_SCOPE of the context.
     *
     * @param t Mappings to be stored in this map.
     * @throws UnsupportedOperationException if the <code>putAll</code> method is not
     *                                       supported by this map.
     * @throws ClassCastException           if the class of a key or value in the specified
     *                                       map prevents it from being stored in this map.
     * @throws IllegalArgumentException   some aspect of a key or value in the
     *                                       specified map prevents it from being stored in this map.
     * @throws NullPointerException       if the specified map is <code>null</code>, or if
     *                                       this map does not permit <code>null</code> keys or values, and the specified map
     *                                       contains <code>null</code> keys or values.
     */
    @Override
    public void putAll(Map<? extends String, ? extends Object> t) {
        context.getBindings(ENGINE_SCOPE).putAll(t);
    }


    /**
     * Removes the mapping from the engine scope.
     *
     * <p>Returns the value to which the map previously associated the key, or
     * <code>null</code> if the map contained no mapping for this key.  (A
     * <code>null</code> return can also indicate that the map previously associated
     * <code>null</code> with the specified key if the implementation supports
     * <code>null</code> values.)  The map will not contain a mapping for the specified
     * key once the call returns.
     *
     * @param key key whose mapping is to be removed from the map.
     * @return previous value associated with specified key, or <code>null</code> if
     *         there was no mapping for key.
     * @throws ClassCastException           if the key is of an inappropriate type for this
     *                                       map (optional).
     * @throws NullPointerException       if the key is <code>null</code> and this map does
     *                                       not permit <code>null</code> keys (optional).
     * @throws UnsupportedOperationException if the <code>remove</code> method is not
     *                                       supported by this map.
     */
    // Why is the compiler complaining about this?
    //public Object remove( String key )
    @Override
    public Object remove(Object key) {
        int scope = context.getAttributesScope((String) key);
        if (scope > -1)
            return context.removeAttribute((String) key, scope);
        return null;
    }


    /**
     * Removes all mappings from this map (optional operation).
     *
     * @throws UnsupportedOperationException clear is not supported by this map.
     */
    @Override
    public void clear() {
        for (int i : context.getScopes())
            context.getBindings(i).clear();
    }


    /**
     * Returns the total key set of all scopes.
     * This method violates the Map contract by returning an unmodifiable set.
     *
     * @return a set view of the keys contained in this map.
     */
    @Override
    public Set<String> keySet() {
        return totalMap().keySet();
    }


    /**
     * Returns the total values set of all scopes.
     * This method violates the Map contract by returning an unmodifiable set.
     *
     * @return a collection view of the values contained in this map.
     */
    @Override
    public Collection<Object> values() {
        return totalMap().values();
    }


    /**
     * Returns a set view of the mappings contained in this map.  Each element in
     * the returned set is a {@link java.util.Map.Entry}.  The set is backed by the
     * map, so changes to the map are reflected in the set, and vice-versa. If the
     * map is modified while an iteration over the set is in progress (except
     * through the iterator's own <code>remove</code> operation, or through the
     * <code>setValue</code> operation on a map entry returned by the iterator) the
     * results of the iteration are undefined.  The set supports element removal,
     * which removes the corresponding mapping from the map, via the
     * <code>Iterator.remove</code>, <code>Set.remove</code>, <code>removeAll</code>,
     * <code>retainAll</code> and <code>clear</code> operations.  It does not support the
     * <code>add</code> or <code>addAll</code> operations.
     *
     * @return a set view of the mappings contained in this map.
     */
    @Override
    public Set<Entry<String, Object>> entrySet() {
        return totalMap().entrySet();
    }


    private Map<String, Object> totalMap() {
        Map<String, Object> map = new HashMap<String,Object>();
        for (int i : context.getScopes())
            if (null != context.getBindings(i))
                map.putAll(context.getBindings(i));
        return map;
    }

}

