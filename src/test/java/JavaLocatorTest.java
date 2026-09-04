import org.example.launcher.java.JavaVerifier;

public class JavaLocatorTest {

    public static void main(String[] args) {

        test(
                "openjdk version \"21.0.8\" 2025-07-15",
                21
        );

        test(
                "openjdk version \"17.0.12\" 2024-07-16",
                17
        );

        test(
                "java version \"1.8.0_451\"",
                8
        );

        System.out.println(
                "All JavaVerifier tests passed."
        );
    }

    private static void test(
            String output,
            int expected
    ) {

        int actual =
                JavaVerifier.parseVersion(output);

        if (actual != expected) {

            throw new AssertionError(
                    "Expected Java "
                            + expected
                            + " but got Java "
                            + actual
            );
        }

        System.out.println(
                "PASS: Java "
                        + expected
        );
    }
}