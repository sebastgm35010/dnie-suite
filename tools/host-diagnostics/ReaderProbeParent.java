/** Synthetic child-gating test: reader metadata only, no card connection. */
public final class ReaderProbeParent {
    public static void main(String[] args) throws Exception {
        java.util.List<String> command = new java.util.ArrayList<String>(java.util.Arrays.asList(
            System.getProperty("java.home") + "\\bin\\java.exe", "-client", "-cp",
            System.getProperty("java.class.path"), "ReaderMetadata"));
        command.addAll(java.util.Arrays.asList(args));
        Process child = new ProcessBuilder(command).inheritIO().start();
        if (!child.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) {
            child.destroyForcibly();
            throw new AssertionError("Synthetic child timeout");
        }
        if (child.exitValue() != 0) System.exit(child.exitValue());
    }
}
