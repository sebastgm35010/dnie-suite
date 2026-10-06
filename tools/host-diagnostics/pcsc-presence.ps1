# Metadata only: no SCardConnect, APDU, PIN or ATR bytes.
if (-not ('DniPresence' -as [type])) {
Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class DniPresence {
 [StructLayout(LayoutKind.Sequential,CharSet=CharSet.Unicode)] public struct State {
  public string Reader; public IntPtr User; public uint Current,Event,Length;
  [MarshalAs(UnmanagedType.ByValArray,SizeConst=36)] public byte[] Atr;
 }
 [DllImport("winscard.dll")] public static extern uint SCardEstablishContext(uint s,IntPtr a,IntPtr b,out IntPtr c);
 [DllImport("winscard.dll")] public static extern uint SCardReleaseContext(IntPtr c);
 [DllImport("winscard.dll",CharSet=CharSet.Unicode)] public static extern uint SCardListReadersW(IntPtr c,string g,[Out]char[] r,ref uint n);
 [DllImport("winscard.dll",CharSet=CharSet.Unicode)] public static extern uint SCardGetStatusChangeW(IntPtr c,uint t,[In,Out]State[] r,uint n);
 public static object[] Read() {
  IntPtr c; uint status=SCardEstablishContext(2,IntPtr.Zero,IntPtr.Zero,out c);
  if(status!=0) return new object[]{new {error=status}};
  try {
   uint n=0; status=SCardListReadersW(c,null,null,ref n);
   if(status!=0) return new object[]{new {error=status}};
   char[] names=new char[n]; status=SCardListReadersW(c,null,names,ref n);
   if(status!=0) return new object[]{new {error=status}};
   string[] readers=new string(names).Split(new char[]{'\0'},StringSplitOptions.RemoveEmptyEntries);
   State[] states=new State[readers.Length];
   for(int i=0;i<readers.Length;i++){states[i].Reader=readers[i];states[i].Atr=new byte[36];}
   status=SCardGetStatusChangeW(c,0,states,(uint)states.Length);
   if(status!=0) return new object[]{new {error=status}};
   object[] result=new object[states.Length];
   for(int i=0;i<states.Length;i++)result[i]=new {name=states[i].Reader,index=i,present=(states[i].Event&32)!=0,flags=states[i].Event,atrBytes=states[i].Length};
   return result;
  }finally{SCardReleaseContext(c);}
 }
}
'@
}
[DniPresence]::Read()
