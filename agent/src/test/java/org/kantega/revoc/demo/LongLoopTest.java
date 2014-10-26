package org.kantega.revoc.demo;

import org.kantega.helloworld.LongLoop;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

/**
 *
 */
public class LongLoopTest {
    @Test
    public void testLoop()
    {
        for(int i = 0; i < 100; i++) {
            LongLoop.main(null);
        }
        long before = System.nanoTime();

        for(int i = 0; i < 5; i++) {
            LongLoop.main(null);
        }
        System.out.println("LongLoopTest took: " + (TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-before)) +"ms.");
    }
}
