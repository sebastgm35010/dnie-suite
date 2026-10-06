import javax.smartcardio.*;
import java.security.*;
import java.io.*;
import java.util.*;
import java.util.logging.LogManager;

/** Process-local reader selection for the unmodified RENIEC reader JAR.
 * Never reorders Windows readers, selects another card, or replaces card responses. */
public final class BridgeReaderLauncher {
    static final String READER = "DNIe Bridge Android NFC Private Reader 0";
    static final String TYPE = "DNIeBridgeOnly";
    public static final class BridgeProvider extends Provider {
        public BridgeProvider() {
            super("DNIeBridgeSelector", 1.0, "Process-local exact reader filter");
            put("TerminalFactory." + TYPE, BridgeSpi.class.getName());
        }
    }
    public static final class BridgeSpi extends TerminalFactorySpi {
        protected CardTerminals engineTerminals() {
            try {
                return new FilteredTerminals(TerminalFactory.getInstance("PC/SC", null, "SunPCSC").terminals());
            } catch (GeneralSecurityException e) { throw new IllegalStateException("PCSC provider unavailable", e); }
        }
    }
    static final class FilteredTerminals extends CardTerminals {
        final CardTerminals original;
        FilteredTerminals(CardTerminals original) { this.original = original; }
        public List<CardTerminal> list(State state) throws CardException {
            Objects.requireNonNull(state);
            List<CardTerminal> result = new ArrayList<CardTerminal>();
            for (CardTerminal terminal : original.list(state)) {
                if (READER.equals(terminal.getName())) result.add(terminal);
            }
            return Collections.unmodifiableList(result);
        }
        public boolean waitForChange(long timeout) throws CardException { return original.waitForChange(timeout); }
    }
    static void requireFilteredFactory(TerminalFactory factory) throws CardException {
        if (!TYPE.equals(factory.getType()) || !"DNIeBridgeSelector".equals(factory.getProvider().getName())) {
            throw new IllegalStateException("Reader filter not selected; refusing fallback");
        }
        for (CardTerminal terminal : factory.terminals().list()) {
            if (!READER.equals(terminal.getName())) throw new IllegalStateException("Unexpected reader");
        }
    }
    static void event(String name) {
        System.err.println("{\"time\":" + System.currentTimeMillis() + ",\"event\":\"" + name + "\"}");
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--self-test".equals(args[0])) { selfTest(); return; }
        if (args.length != 1 || !("--metadata".equals(args[0]) || "--read".equals(args[0]))) {
            throw new IllegalArgumentException("Use --metadata, --read or --self-test");
        }
        System.setProperty("javax.smartcardio.TerminalFactory.DefaultType", TYPE);
        Security.insertProviderAt(new BridgeProvider(), 1);
        TerminalFactory factory = TerminalFactory.getDefault();
        requireFilteredFactory(factory);
        event("exact_reader_filter_ready");
        List<CardTerminal> all = factory.terminals().list();
        if ("--metadata".equals(args[0])) {
            event(all.isEmpty() ? "bridge_reader_missing" : "bridge_reader_visible");
            event(factory.terminals().list(CardTerminals.State.CARD_PRESENT).isEmpty() ? "bridge_card_absent" : "bridge_card_present");
            return;
        }
        if (all.size() != 1) throw new IllegalStateException("Bridge reader absent or ambiguous");
        CardTerminal terminal = all.get(0);
        event("waiting_for_bridge_card");
        if (!terminal.isCardPresent() && !terminal.waitForCardPresent(300000)) {
            event("bridge_card_wait_timeout"); System.exit(2); return;
        }
        // Secret arrives through stdin, never through a command-line argument.
        String can = new BufferedReader(new InputStreamReader(System.in, "UTF-8")).readLine();
        if (can == null || !can.matches("[0-9]{6}")) throw new IllegalArgumentException("CAN unavailable");
        // Initialize the official logging configuration before removing its handlers.
        // RENIEC's main logs its arguments; no handlers remain when main is invoked.
        Class<?> app = Class.forName("reniec.pkiep.App");
        LogManager.getLogManager().reset();
        event("official_reader_started_on_bridge");
        app.getMethod("main", String[].class).invoke(null, (Object)new String[]{can});
        event("official_reader_completed");
    }
    static final class FakeTerminal extends CardTerminal {
        final String name;
        final boolean present;
        FakeTerminal(String name, boolean present) { this.name=name; this.present=present; }
        public String getName() { return name; }
        public Card connect(String protocol) { throw new AssertionError("Test must not connect"); }
        public boolean isCardPresent() { return present; }
        public boolean waitForCardPresent(long timeout) { throw new AssertionError("Test must not wait"); }
        public boolean waitForCardAbsent(long timeout) { throw new AssertionError("Test must not wait"); }
    }
    static void check(boolean value) { if (!value) throw new AssertionError("Reader isolation failed"); }
    static void selfTest() throws Exception {
        final List<CardTerminal> rows = Arrays.<CardTerminal>asList(
            new FakeTerminal(READER,true),new FakeTerminal("Other virtual reader",false),
            new FakeTerminal("Windows Hello for Business 1",true));
        CardTerminals fake = new CardTerminals() {
            public List<CardTerminal> list(State state) {
                List<CardTerminal> out = new ArrayList<CardTerminal>();
                for(CardTerminal t:rows) {
                    boolean present=((FakeTerminal)t).present;
                    if(state==State.ALL || (state==State.CARD_PRESENT && present) ||
                            (state==State.CARD_ABSENT && !present))out.add(t);
                }
                return out;
            }
            public boolean waitForChange(long timeout) { throw new AssertionError("Test must not wait"); }
        };
        FilteredTerminals filtered = new FilteredTerminals(fake);
        check(filtered.list().size()==1);
        check(filtered.list(CardTerminals.State.CARD_PRESENT).size()==1);
        check(filtered.list(CardTerminals.State.CARD_ABSENT).isEmpty());
        check(filtered.list(CardTerminals.State.CARD_PRESENT).get(0).getName().equals(READER));
        FilteredTerminals missing = new FilteredTerminals(new CardTerminals() {
            public List<CardTerminal> list(State state) { return Collections.<CardTerminal>singletonList(new FakeTerminal("Windows Hello for Business 1",true)); }
            public boolean waitForChange(long timeout) { throw new AssertionError(); }
        });
        check(missing.list().isEmpty());
        check(missing.list(CardTerminals.State.CARD_PRESENT).isEmpty());
        event("six_reader_filter_checks_passed");
    }
}
