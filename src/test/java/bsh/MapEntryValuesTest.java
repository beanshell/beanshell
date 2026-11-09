package bsh;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;
import java.util.Map.Entry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Maps and entries built or changed by a script hold plain Java keys and
 * values, as Java code that receives them expects. */
@RunWith(FilteredTestRunner.class)
public class MapEntryValuesTest {

    @Test
    @SuppressWarnings("unchecked")
    public void map_of_entries_holds_plain_values() throws Exception {
        Map<Object, Object> m = (Map<Object, Object>) new Interpreter().eval(
            "Map m = {null=1, \"b\"=null}; return m;");
        assertTrue(m.containsKey(null));
        assertEquals(Integer.valueOf(1), m.get(null));
        assertTrue(m.containsKey("b"));
        assertNull(m.get("b"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void map_index_assignment_uses_plain_key_and_value() throws Exception {
        Map<Object, Object> m = (Map<Object, Object>) new Interpreter().eval(
            "Map m = new HashMap(); m[2] = 3; m[null] = null; return m;");
        assertEquals(Integer.valueOf(3), m.get(Integer.valueOf(2)));
        assertTrue(m.containsKey(null));
        assertNull(m.get(null));
    }

    @Test
    public void entries_hold_plain_values() throws Exception {
        Entry<?, ?>[] es = (Entry<?, ?>[]) new Interpreter().eval(
            "Map.Entry[] es = {1=2, null=null}; return es;");
        assertEquals(Integer.valueOf(1), es[0].getKey());
        assertEquals(Integer.valueOf(2), es[0].getValue());
        assertNull(es[1].getKey());
        assertNull(es[1].getValue());
    }

    @Test
    public void entry_assignment_stores_plain_value() throws Exception {
        Entry<?, ?> e = (Entry<?, ?>) new Interpreter().eval(
            "e = new Entry {\"k\", 1}; e[\"k\"] = 2; return e;");
        assertEquals(Integer.valueOf(2), e.getValue());
    }
}
