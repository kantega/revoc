package no.kantega.labs.methodhandles;

import org.junit.Test;

/**
 *
 */
public class MethodHandlesTest {


    public static int shiftIndex(int originalIndex, int amount) {
        return originalIndex+amount;
    }

    @Test
    public void testHandler() throws Throwable {


        /*
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        MethodHandle toUpperCase = lookup.findVirtual(String.class, "toUpperCase", MethodType.methodType(String.class));

        String result = (String) toUpperCase.invokeExact("eirik");

        System.out.println(result);


        MethodHandle arraySetter = MethodHandles.arrayElementSetter(long[].class);


        MethodHandle filterTarget = lookup.findStatic(MethodHandlesTest.class, "shiftIndex", MethodType.methodType(int.class, int.class, int.class));

        MethodHandle insert = MethodHandles.insertArguments(filterTarget, 1, 2);

        MethodHandle shiftingHandle = MethodHandles.filterArguments(arraySetter, 1, insert);


        long[] array = new long[3];
        shiftingHandle.invokeExact(array, 0, 2l);

        System.out.println(Arrays.toString(array));
                    */
    }
}
