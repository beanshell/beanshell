package bsh;

import java.lang.reflect.Array;

/** Collection of array manipulation functions. */
public class BshArray {
    /** Constructor private no instance required. */
    private BshArray() {}

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
}
