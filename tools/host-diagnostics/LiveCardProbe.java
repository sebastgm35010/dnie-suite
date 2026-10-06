import javax.smartcardio.*;
import java.util.*;

/** Read-only app/file selection, explicit reader. Never login, sign or pick a different card. */
public final class LiveCardProbe {
    private static final String READER = "DNIe Bridge Android NFC Private Reader 0";
    private static void event(String name, String fields) {
        System.out.println("{\"time\":"+System.currentTimeMillis()+",\"category\":\"java_live_probe\",\"event\":\""+name+"\",\"fields\":{"+fields+"}}");
    }
    private static byte[] decode(String text) {
        byte[] bytes=new byte[text.length()/2];
        for(int i=0;i<bytes.length;i++)bytes[i]=(byte)Integer.parseInt(text.substring(i*2,i*2+2),16);
        return bytes;
    }
    public static void main(String[] args) {
        Card card=null;
        boolean exclusive=false;
        try {
            CardTerminal selected=null;
            int presentIndex=0;
            for(CardTerminal terminal:TerminalFactory.getDefault().terminals().list()) {
                if(READER.equals(terminal.getName()))selected=terminal;
            }
            for(CardTerminal terminal:TerminalFactory.getDefault().terminals().list(CardTerminals.State.CARD_PRESENT)) {
                String kind=READER.equals(terminal.getName()) ? "our_bridge" :
                    terminal.getName().startsWith("Windows Hello for Business") ? "windows_hello" : "other";
                event("present_reader_order","\"index\":"+(presentIndex++)+",\"kind\":\""+kind+"\"");
            }
            if(args.length==1 && "--readers-only".equals(args[0])) return;
            if(selected!=null && args.length==1 && "--wait".equals(args[0]) && !selected.isCardPresent()) {
                event("waiting_for_our_card","\"limitSeconds\":300");
                selected.waitForCardPresent(300000);
            }
            if(selected==null || !selected.isCardPresent()) {
                event("our_card_absent","\"reader\":\""+READER+"\"");
                System.exit(2); return;
            }
            card=selected.connect("T=1");
            event("connected","\"reader\":\""+READER+"\",\"protocol\":\""+card.getProtocol()+"\"");
            card.beginExclusive(); exclusive=true;
            String[][] probes={
                {"ias_version3","00A4040C10A000000077030C60000000FE00000500"},
                {"ias_java_p2_04","00A4040410A000000077030C60000000FE0000050000"},
                {"card_access_select","00A4020C02011C"}
            };
            for(String[] probe:probes) {
                long start=System.nanoTime();
                ResponseAPDU response=card.getBasicChannel().transmit(new CommandAPDU(decode(probe[1])));
                event("select_result","\"label\":\""+probe[0]+"\",\"status\":\""+String.format(Locale.ROOT,"%04X",response.getSW())+"\",\"responseBytes\":"+response.getBytes().length+",\"durationMs\":"+(System.nanoTime()-start)/1000000);
            }
        } catch(Exception failure) {
            event("failed","\"exception\":\""+failure.getClass().getSimpleName()+"\"");
        } finally {
            if(card!=null) {
                if(exclusive)try{card.endExclusive();}catch(CardException ignored){event("transaction_end_failed","");}
                try{card.disconnect(false);}catch(CardException ignored){event("disconnect_failed","");}
            }
        }
    }
}
