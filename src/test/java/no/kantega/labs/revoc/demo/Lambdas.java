package no.kantega.labs.revoc.demo;

/**
 *
 */
public class Lambdas {

    public static void main(String[] args) {
        new Lambdas().run();
    }

    private void run() {
        int number = 42;

        Runnable runnable = () ->
                System.out.println("Inside lambda: " +number);

        runnable.run();
    }
}
