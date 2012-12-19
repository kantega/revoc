package no.kantega.labs.revoc.analysis;

/**
 *
 */
public class OneLiner {

    public void run() {
        int oneline = 0;

        int t  =0;
        for(int i = 0; i < 80; i++) {
            t++;
        }

        if(t == 0) {
            try {
                System.out.println("blue");
            } catch (Exception e) {
                System.out.println("red");
            } finally {
                System.out.println("green");
            }
        }

        System.out.println(80);
    }
}
