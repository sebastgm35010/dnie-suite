import javax.smartcardio.*;
import java.util.*;
/** Reader metadata only. Never connect or transmit to any card. */
public final class ReaderMetadata {
    static String quote(String s) { return "\""+s.replace("\\","\\\\").replace("\"","\\\"")+"\""; }
    public static void main(String[] args) throws Exception {
        List<CardTerminal> all=TerminalFactory.getDefault().terminals().list();
        List<CardTerminal> present=TerminalFactory.getDefault().terminals().list(CardTerminals.State.CARD_PRESENT);
        String first=all.isEmpty()?"":all.get(0).getName();
        String last=present.isEmpty()?"":present.get(present.size()-1).getName();
        StringBuilder rows=new StringBuilder();
        for(CardTerminal t:all){if(rows.length()>0)rows.append(',');rows.append("{\"name\":").append(quote(t.getName())).append(",\"present\":").append(t.isCardPresent()).append('}');}
        System.out.println("{\"time\":"+System.currentTimeMillis()+",\"category\":\"java_pcsc\",\"event\":\"selection_snapshot\",\"fields\":{\"dotnetFirst\":"+quote(first)+",\"javaLastPresent\":"+quote(last)+",\"readers\":["+rows+"]}}");
    }
}
