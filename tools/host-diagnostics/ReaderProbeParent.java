/** Synthetic child-gating test: reader metadata only, no card connection. */
public final class ReaderProbeParent {
    public static void main(String[] args) throws Exception {
        Process child = new ProcessBuilder(System.getProperty("java.home") + "\\bin\\java.exe",
            "-client", "-cp", System.getProperty("java.class.path"), "ReaderMetadata").start();
        child.waitFor();
    }
}
