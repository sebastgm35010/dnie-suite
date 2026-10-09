using System.Runtime.InteropServices;
namespace DniBridge.Control;

/// <summary>WinSCard enumeration only. Never connects, resets or transmits to a card.</summary>
public static class ReaderPublication {
    public const string ReaderName="DNIe Bridge Android NFC Private Reader 0";
    public static bool IncludesOwnReader(IEnumerable<string> readers) =>
        readers.Any(name=>string.Equals(name,ReaderName,StringComparison.OrdinalIgnoreCase));
    public static bool? Query() {
        if(!OperatingSystem.IsWindows())return null;
        if(SCardEstablishContext(2,IntPtr.Zero,IntPtr.Zero,out var context)!=0)return null;
        try {
            for(int attempt=0;attempt<3;attempt++) {
                uint size=0;uint status=SCardListReadersW(context,null,null,ref size);
                if(status==0x8010002E)return false; // SCARD_E_NO_READERS_AVAILABLE
                if(status!=0 || size is <1 or >65536)return null;
                var names=new char[size];status=SCardListReadersW(context,null,names,ref size);
                if(status==0x80100008)continue; // SCARD_E_INSUFFICIENT_BUFFER: list changed
                if(status==0x8010002E)return false;
                if(status!=0 || size>names.Length)return null;
                return IncludesOwnReader(new string(names,0,(int)size).Split('\0',StringSplitOptions.RemoveEmptyEntries));
            }
            return null;
        } finally {SCardReleaseContext(context);}
    }
    [DllImport("winscard.dll")]private static extern uint SCardEstablishContext(uint scope,IntPtr a,IntPtr b,out IntPtr context);
    [DllImport("winscard.dll")]private static extern uint SCardReleaseContext(IntPtr context);
    [DllImport("winscard.dll",CharSet=CharSet.Unicode)]private static extern uint SCardListReadersW(IntPtr context,string? groups,[Out]char[]? readers,ref uint size);
}
