package no.kantega.labs.revoc.registry;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

/**
 *
 */
public class UnsafeAtomicLongArray {

    private static final Unsafe unsafe;
    private static final int shift;

    private static final int base;
    private final long[] array;
    int faults = 0;
    private int tries;

    static {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            unsafe = (Unsafe) theUnsafe.get(null);
            int scale = unsafe.arrayIndexScale(long[].class);
            if ((scale & (scale - 1)) != 0)
                throw new Error("data type scale not a power of two");
            shift = 31 - Integer.numberOfLeadingZeros(scale);
            base = unsafe.arrayBaseOffset(long[].class);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public UnsafeAtomicLongArray(int length) {
        array = new long[length];
    }

    public static long byteOffset(int i) {
        return ((long) i << shift) + base;
    }

    private long getRaw(long offset) {
        return unsafe.getLongVolatile(array, offset);
    }

    public void incrementOffset(int i) {
        add(i, 1);
    }
    public void add(int i, long amount) {

        //array[i]++;
        while (true) {
            long offset = byteOffset(i);
            long current = getRaw(offset);
            long next = current + amount;
            if (unsafe.compareAndSwapLong(array, offset, current, next))
                return;
        }
    }

    public final void set(int i, long newValue) {
        unsafe.putLongVolatile(array, byteOffset(i), newValue);
    }

    public final int length() {
        return array.length;
    }

    public final long get(int i) {
        //return array[i];
        return getRaw(byteOffset(i));
    }

}
