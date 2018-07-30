package bsh;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/** Collection of array manipulation functions. */
public class BshArray {
    /** Constructor private no instance required. */
    private BshArray() {}

    /** Repeat the contents of a list a number of times.
     * @param list the list to repeat
     * @param times number of repetitions
     * @return a new list instance with repeated contents */
    public static Object repeat(List<Object> list, int times) {
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
    public static Object concat(List<?> lhs, List<?> rhs) {
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
                + " with array of type " + StringUtil.typeString(rhs) + ".");
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
}
