package bsh;

import java.lang.reflect.Array;
import java.util.AbstractList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Queue;
import java.util.RandomAccess;
import java.util.Map.Entry;

/** Collection of array manipulation functions. */
public class BshArray {
    /** Constructor private no instance required. */
    private BshArray() {}

    /** Get object from array or list at index.
     * @param array to retrieve from
     * @param index of the element to retrieve
     * @return Array.get for array or List.get for list
     * @throws UtilTargetError wrapped Index out of bounds */
    public static Object getIndex(Object array, int index)
            throws UtilTargetError {
        Interpreter.debug("getIndex: ", array, ", index=", index);
        try {
            if ( array instanceof List )
                return ((List<?>) array).get(index);
            Object val = Array.get(array, index);
            return Primitive.wrap( val, Types.arrayElementType(array.getClass()) );
        } catch( IndexOutOfBoundsException e1 ) {
            int len = array instanceof List
                    ? ((List<?>) array).size()
                    : Array.getLength(array);
            throw new UtilTargetError("Index " + index
                    + " out-of-bounds for length " + len, e1);
        }
    }

    /** Set element value of array or list at index.
     * Array.set for array or List.set for list.
     * @param array to set value for.
     * @param index of the element to set
     * @param val the value to set
     * @throws UtilTargetError wrapped target exceptions */
    @SuppressWarnings("unchecked")
    public static void setIndex(Object array, int index, Object val)
            throws ReflectError, UtilTargetError {
        try {
            val = Primitive.unwrap(val);
            if ( array instanceof List )
                ((List<Object>) array).set(index, val);
            else
                Array.set(array, index, val);
        }
        catch( IllegalArgumentException e1 ) {
            // fabricated array store exception
            throw new UtilTargetError(
                new ArrayStoreException( e1.getMessage() ) );
        } catch( IndexOutOfBoundsException e1 ) {
            int len = array instanceof List
                    ? ((List<?>) array).size()
                    : Array.getLength(array);
            throw new UtilTargetError("Index " + index
                    + " out-of-bounds for length " + len, e1);
        }
    }

    /** Slice the supplied list for range and step.
     * @param list to slice
     * @param from start index inclusive
     * @param to to index exclusive
     * @param step size of step or 0 if no step
     * @return a sliced view of the supplied list */
    public static List<Object> slice(List<Object> list, int from, int to, int step) {
        int length = list.size();
        if ( to > length ) to = length;
        if ( 0 > from ) from = 0;
        length = to - from;
        if ( 0 >= length )
            return list.subList(0, 0);
        if ( step == 0 || step == 1 )
            return list.subList(from, to);
        List<Integer> slices = new ArrayList<>();
        for ( int i = 0; i < length; i++ )
            if ( i % step == 0 )
                slices.add(step < 0 ? length-1-i : i+from);
        return new SteppedSubList(list, slices);
    }

    /** Slice the supplied array for range and step.
     * @param arr to slice
     * @param from start index inclusive
     * @param to to index exclusive
     * @param step size of step or 0 if no step
     * @return a new array instance sliced */
    public static Object slice(Object arr, int from, int to, int step) {
        Class<?> toType = Types.arrayElementType(arr.getClass());
        int length = Array.getLength(arr);
        if ( to > length ) to = length;
        if ( 0 > from ) from = 0;
        length = to - from;
        if ( 0 >= length )
            return Array.newInstance(toType, 0);
        if ( step == 0 || step == 1 ) {
            Object toArray = Array.newInstance(toType, length);
            System.arraycopy(arr, from, toArray, 0, length);
            return toArray;
        }
        Object[] tmp = new Object[(int)Math.ceil((0.0+length)/Math.abs(step))];
        for ( int i = 0, j = 0; i < length; i++ )
            if ( i % step == 0 )
                tmp[j++] = Array.get(arr, step < 0 ? length-1-i : i+from);
        Object toArray = Array.newInstance(toType, tmp.length);
        copy(toType, toArray, (Object[])tmp);
        return toArray;
    }

    /** Repeat the contents of a list a number of times.
     * @param list the list to repeat
     * @param times number of repetitions
     * @return a new list instance with repeated contents */
    public static List<Object> repeat(List<Object> list, int times) {
        if ( times < 1 )
            if (list instanceof Queue)
                return new LinkedList<>();
            else
                return new ArrayList<>(0);
        List<Object> lst = list instanceof Queue
                            ? new LinkedList<>(list)
                            : new ArrayList<>(list);
        if ( times == 1 )
            return lst;
        while ( times-- > 1 )
            lst.addAll(list);
        return lst;
    }

    /** Repeat the contents of an array a number of times.
     * @param arr the array object to repeat
     * @param times number of repetitions
     * @return a new array instance with repeated contents */
    public static Object repeat(Object arr, int times) {
        Class<?> toType = Types.arrayElementType(arr.getClass());
        if ( times < 1 )
            return Array.newInstance(toType, 0);
        int[] dims = dimensions(arr);
        int length = dims[0];
        dims[0] *= times;
        int i = 0, total = dims[0];
        Object toArray = Array.newInstance(toType, dims);
        while ( i < total ) {
            System.arraycopy(arr, 0, toArray, i, length);
            i += length;
        }
        return toArray;
    }

    /** Concatenate two lists.
     * @param lhs 1st list
     * @param rhs 2nd list
     * @return a new list instance of concatenated contents. */
    public static List<Object> concat(List<?> lhs, List<?> rhs) {
        List<Object> list = lhs instanceof Queue
                        ? new LinkedList<>(lhs)
                        : new ArrayList<>(lhs);
        list.addAll(rhs);
        return list;
    }

    /** Concatenate two arrays.
     * @param lhs 1st array
     * @param rhs 2nd array
     * @return a new array instance of concatenated contents.
     * @throws UtilEvalError for inconsistent dimensions. */
    public static Object concat(Object lhs, Object rhs) throws UtilEvalError {
        Class<?> lhsType = lhs.getClass();
        Class<?> rhsType = rhs.getClass();
        if ( Types.arrayDimensions(lhsType) != Types.arrayDimensions(rhsType) )
            throw new UtilEvalError("Cannot concat arrays with inconsistent dimensions."
                                    + " Attempting to concat array of type " + StringUtil.typeString(lhs)
                                    + " with array of type " + StringUtil.typeString(rhs) + '.');
        Class<?> toType = Types.getCommonType(
                Types.arrayElementType(lhsType),
                Types.arrayElementType(rhsType));
        int[] dims = dimensions(lhs);
        dims[0] = Array.getLength(lhs) + Array.getLength(rhs);
        Object toArray = Array.newInstance(toType, dims);
        copy(toType, toArray, lhs, rhs);
        return toArray;
    }

    /** Collect dimensions array of supplied array object.
     * Returns the integer array used for Array.newInstance.
     * @param arr to inspect
     * @return int array of dimensions */
    public static int[] dimensions(Object arr) {
        int[] dims = new int[Types.arrayDimensions(arr.getClass())];
        if ( 0 == dims.length || 0 == (dims[0] = Array.getLength(arr)) )
            return dims;
        for ( int i = 1; i < dims.length; i++ )
            if ( null != (arr = Array.get(arr, 0)) )
                dims[i] = Array.getLength(arr);
            else break;
        return dims;
    }

    /** Copy and cast the elements of from arrays to type in to array.
     * Recursively traverse dimensions to populate the elements at to array.
     * @param toType the element type to cast to
     * @param to the destination array
     * @param from the list of origin arrays */
    private static void copy(Class<?> toType, Object to, Object... from) {
        int f = 0, fi = 0,
            length = Array.getLength(from[0]),
            total = from.length > 1 ? Array.getLength(to) : length;
        if ( Types.arrayDimensions(to.getClass()) == 1 ) {
            for ( int i = 0; i < total; i++ ) {
                Object value = Array.get(from[f], fi++);
                try {
                    value = Primitive.unwrap(
                            Types.castObject(value, toType, Types.CAST));
                } catch (UtilEvalError e) { /* ignore cast errors */ }
                if ( Byte.TYPE == toType )
                    Array.setByte(to, i, (byte) value);
                else if ( Short.TYPE == toType )
                    Array.setShort(to, i, (short) value);
                else if ( Integer.TYPE == toType )
                    Array.setInt(to, i, (int) value);
                else if ( Long.TYPE == toType )
                    Array.setLong(to, i, (long) value);
                else if ( Float.TYPE == toType )
                    Array.setFloat(to, i, (float) value);
                else if ( Double.TYPE == toType )
                    Array.setDouble(to, i, (double) value);
                else if ( Character.TYPE == toType )
                    Array.setChar(to, i, (char) value);
                else if ( Boolean.TYPE == toType )
                    Array.setBoolean(to, i, (boolean) value);
                else
                    Array.set(to, i, value);

                // concatenate multiple from arrays
                if ( length < total && fi == length && f+1 < from.length ) {
                    length = Array.getLength(from[++f]);
                    fi = 0;
                }
            }
        } else for ( int i = 0; i < total; i++ ) {
            // concatenate multiple from arrays
            if ( length < total && fi == length && f+1 < from.length ) {
                length = Array.getLength(from[++f]);
                fi = 0;
            }

            Object frm = Array.get(from[f], fi++);

            // null dimension example: new Integer[2][]
            if ( null == frm ) {
                Array.set(to, i, null);
                continue;
            }

            Object tto = Array.get(to, i);

            // mixed array lengths in multiple dimensions ex: {{1,2}, {3}}
            if ( Array.getLength(frm) != Array.getLength(tto) )
                Array.set(to, i,
                    tto = Array.newInstance(toType, dimensions(frm)));

            // recurse copy for next array dimension
            copy(toType, tto, frm);
        }
    }

    /** Relaxed implementation of the java 9 Map.ofEntries.
     * LinkedHashMap ensures element order remains as supplied.
     * @param entries array of Map.Entry elements
     * @return a mutable, type relaxed LinkedHashMap */
    private static Map<?, ?> mapOfEntries( Entry<?, ?>... entries ) {
        Map<Object, Object> looseTypedMap = new LinkedHashMap<>( entries.length );
        for (Entry<?, ?> entry : entries)
            looseTypedMap.put(entry.getKey(), entry.getValue());
        return looseTypedMap;
    }

    /** Cast an array to the specified element type.
     * @param toType element type of cast array
     * @param fromType type of from value
     * @param fromValue the array value to cast
     * @return a new instance of to type
     * @throws UtilEvalError cast error */
    static Object castArray(
        Class<?> toType, Class<?> fromType, Object fromValue )
        throws UtilEvalError
    {
        // Collection type cast from array
        if ( Types.isJavaAssignable(Collection.class, toType) )
            if ( Types.isJavaAssignable(List.class, toType) || Queue.class == toType ) {
                if ( Types.isJavaAssignable(toType, ArrayList.class) )
                    // List type is implemented as a mutable ArrayList
                    return new ArrayList<>(Arrays.asList((Object[])
                        Types.castObject(fromValue, Object.class, Types.CAST)));
                else if ( Types.isJavaAssignable(toType, LinkedList.class) )
                    // Queue type is implemented as a mutable LinkedList
                    return new LinkedList<>(Arrays.asList((Object[])
                        Types.castObject(fromValue, Object.class, Types.CAST)));
            } else if ( Types.isJavaAssignable(toType, ArrayDeque.class) )
                // Deque type is implemented as a mutable ArrayDeque
                return new ArrayDeque<>(Arrays.asList((Object[])
                    Types.castObject(fromValue, Object.class, Types.CAST)));
            else if ( Types.isJavaAssignable(toType, LinkedHashSet.class) )
                // Set type is implemented as a mutable LinkedHashSet
                return new LinkedHashSet<>(Arrays.asList((Object[])
                    Types.castObject(fromValue, Object.class, Types.CAST)));

        // Map type gets converted to a mutable LinkedHashMap
        if ( Types.isJavaAssignable(Map.class, toType) ) {
            if ( Types.isJavaAssignable(Entry.class, Types.arrayElementType(fromType)) )
                return mapOfEntries((Entry[]) fromValue);
            if ( Types.isJavaAssignable(toType, LinkedHashMap.class) ) {
                int length = Array.getLength(fromValue);
                Map<Object, Object> map = new LinkedHashMap<>(
                        (int) Math.ceil((0.0 + length) / 2));
                for ( int i = 0; i < length; i++ )
                    map.put(Array.get(fromValue, i),
                        ++i < length ? Array.get(fromValue, i) : null);
                return map;
            }
        }

        // Entry type gets converted to LHS.MapEntry
        if ( Types.isJavaAssignable(Entry.class, toType) ) {
            int length = Array.getLength(fromValue);
            if ( length == 1 )
                return new LHS(Array.get(fromValue, 0)).assign(null);
            else if ( length == 2 )
                return new LHS(Array.get(fromValue, 0))
                        .assign(Array.get(fromValue, 1));
            else
                throw new UtilEvalError(
                    "Maximum array length for Map.Entry is 2, array length "
                        + length + " exceeds.");
        }

        // cast array to element type of toType
        toType = Types.arrayElementType(toType);
        int[] dims = dimensions(fromValue);
        Object toArray = Array.newInstance(toType, dims);
        BshArray.copy(toType, toArray, fromValue);
        return toArray;
    }

    /** Provides a view of a parent list for a specific list of parent indexes (steps).
     * Based on @see ArrayList.SubList. */
    private static class SteppedSubList extends AbstractList<Object> implements RandomAccess {
        private final List<Object> parent;
        private final List<Integer> steps;

        /** Default constructor.
         * @param parent the referenced parent list
         * @param steps a list of parent indexes for this view. */
        SteppedSubList(List<Object> parent, List<Integer> steps) {
            this.parent = parent;
            this.steps = steps;
        }

        /** Overridden method to set the parent value for the parent index of
         * the step at the supplied index.
         * {@inheritDoc} */
        @Override
        public Object set(int index, Object e) {
            return this.parent.set(this.steps.get(index), e);
        }

        /** Overridden method to get the parent value for the parent index of
         * the step at the supplied index.
         * {@inheritDoc} */
        @Override
        public Object get(int index) {
            return this.parent.get(this.steps.get(index));
        }

        /** Overridden method to retrieve the size of the view.
         * {@inheritDoc} */
        @Override
        public int size() {
            return this.steps.size();
        }

        /** Overridden method to add a value to the parent at the parent index of
         * the step at the supplied index. Update remaining step indexes moved down.
         * {@inheritDoc} */
        @Override
        public void add(int index, Object e) {
            int idx = index == this.size()
                    ? this.steps.get(index - 1) + 1
                    : this.steps.get(index);
            this.parent.add(idx, e);
            for ( int i = index; i < this.size(); i++ )
                this.steps.set(i, this.steps.get(i) + 1);
            this.steps.add(index, idx);
        }

        /** Overridden method to remove a value from the parent at the parent index of
         * the step at the supplied index. Update remaining step indexes moved up.
         * {@inheritDoc} */
        @Override
        public Object remove(int index) {
            int idx = this.steps.get(index);
            for ( int i = index + 1; i < this.size(); i++ )
                this.steps.set(i, this.steps.get(i) - 1);
            this.steps.remove(index);
            return this.parent.remove(idx);
        }

        /** Overridden method delegates to addAll at index size.
         * {@inheritDoc} */
        @Override
        public boolean addAll(Collection<? extends Object> c) {
            return addAll(this.steps.size(), c);
        }

        /** Overridden method traverses supplied list and delegates to add method for each.
         * {@inheritDoc} */
        @Override
        public boolean addAll(int index, Collection<? extends Object> c) {
            int cnt = 0;
            for ( Object e : c )
                this.add(index + cnt++, e);
            return cnt > 0;
        }

        /** Overridden method to retrieve a stepped sub list of a sub list of this view.
         * {@inheritDoc} */
        @Override
        public List<Object> subList(int fromIndex, int toIndex) {
            return new SteppedSubList(this.parent, this.steps.subList(fromIndex, toIndex));
        }

        /** Overridden method delegates to list iterator method.
         * {@inheritDoc} */
        @Override
        public Iterator<Object> iterator() {
            return listIterator();
        }

        /** Overridden method supplying a modifiable list iterator which delegates to methods
         * on this view.
         * {@inheritDoc} */
        @Override
        public ListIterator<Object> listIterator(final int index) {
            /** A copy of the list iterator to avoid concurrent modification issues. */
            ListIterator<Integer> sliceIter = new ArrayList<>(this.steps).listIterator(index);
            return new ListIterator<Object>() {
                int lastIndex = 0;
                /** Overridden method delegates to the copy.
                 * {@inheritDoc} */
                @Override
                public boolean hasNext() {
                    return sliceIter.hasNext();
                }

                /** Overridden method delegates to this view get method.
                 * {@inheritDoc} */
                @Override
                public Object next() {
                    sliceIter.next();
                    lastIndex = previousIndex();
                    return SteppedSubList.this.get(lastIndex);
                }

                /** Overridden method delegates to the copy.
                 * {@inheritDoc} */
                @Override
                public boolean hasPrevious() {
                    return sliceIter.hasPrevious();
                }

                /** Overridden method delegates to this view get method.
                 * {@inheritDoc} */
                @Override
                public Object previous() {
                    sliceIter.previous();
                    lastIndex = nextIndex();
                    return SteppedSubList.this.get(lastIndex);
                }

                /** Overridden method delegates to the copy.
                 * {@inheritDoc} */
                @Override
                public int nextIndex() {
                    return sliceIter.nextIndex();
                }

                /** Overridden method delegates to the copy.
                 * {@inheritDoc} */
                @Override
                public int previousIndex() {
                    return sliceIter.previousIndex();
                }

                /** Overridden method delegates to this view remove method.
                 * {@inheritDoc} */
                @Override
                public void remove() {
                    SteppedSubList.this.remove(lastIndex);
                    sliceIter.remove();
                    lastIndex = -1;
                }

                /** Overridden method delegates to this view set method.
                 * {@inheritDoc} */
                @Override
                public void set(Object e) {
                    SteppedSubList.this.set(lastIndex, e);
                }

                /** Overridden method delegates to this view add method.
                 * {@inheritDoc} */
                @Override
                public void add(Object e) {
                    SteppedSubList.this.add(lastIndex, e);
                    sliceIter.add(steps.get(lastIndex));
                    lastIndex = -1;
                }
            };
        }
    }
}
