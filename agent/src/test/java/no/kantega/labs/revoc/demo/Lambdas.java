package no.kantega.labs.revoc.demo;

/**
 *
 */
public class Lambdas {

    public final int number;

    public Lambdas() {
        this(42);
    }

    public Lambdas(Integer number) {
        this.number = number;
    }
    public static void main(String[] args) {
        new Lambdas().run();
    }

    private void run() {

        Runnable runnable = () ->
                System.out.println("Inside lambda: " +number);

        runnable.run();
    }
}
