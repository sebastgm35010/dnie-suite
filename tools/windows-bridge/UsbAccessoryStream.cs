using System.Runtime.InteropServices;
using System.Net.Sockets;
using System.Text;
using Microsoft.Win32.SafeHandles;
namespace DniBridge;

/// <summary>AOA bulk transport. UsbDk is an optional signed Windows dependency; no ADB/RNDIS.</summary>
public sealed class UsbAccessoryStream : Stream
{
    private IntPtr redirect;
    private readonly byte inputEndpoint, outputEndpoint;
    private readonly CancellationTokenRegistration stopRegistration;
    private UsbAccessoryStream(IntPtr handle, byte input, byte output, int packetSize, CancellationToken stop) {
        redirect=handle; inputEndpoint=input; outputEndpoint=output; received=new byte[packetSize];
        stopRegistration=stop.Register(() => Native.CancelIoEx(Native.UsbDk_GetRedirectorSystemHandle(handle),IntPtr.Zero));
    }
    private static class Native {
        static Native() {
            if (!Environment.Is64BitProcess) throw new PlatformNotSupportedException("USB requires a 64-bit host.");
            NativeLibrary.SetDllImportResolver(typeof(Native).Assembly,(name,assembly,path) => {
                if(name!="UsbDkHelper.dll") return IntPtr.Zero;
                string installed=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles),"UsbDk Runtime Library","UsbDkHelper.dll");
                if(!File.Exists(installed)) throw new DllNotFoundException("Install the signed UsbDk x64 helper before using USB.");
                return NativeLibrary.Load(installed);
            });
        }
        [StructLayout(LayoutKind.Sequential,CharSet=CharSet.Unicode)] public struct Id {
            [MarshalAs(UnmanagedType.ByValTStr,SizeConst=200)] public string Device;
            [MarshalAs(UnmanagedType.ByValTStr,SizeConst=200)] public string Instance;
        }
        [StructLayout(LayoutKind.Sequential,Pack=1)] public struct Descriptor {
            public byte Length,Type; public ushort Usb; public byte Class,SubClass,Protocol,MaxPacket;
            public ushort Vendor,Product,Revision; public byte Manufacturer,Model,Serial,Configurations;
        }
        [StructLayout(LayoutKind.Sequential)] public struct DeviceInfo { public Id Id; public ulong Filter,Port,Speed; public Descriptor Descriptor; }
        [StructLayout(LayoutKind.Sequential)] public struct ConfigRequest { public Id Id; public ulong Index; }
        [StructLayout(LayoutKind.Sequential)] public struct Request {
            public ulong Endpoint,Buffer,Length,Type,IsoCount,IsoPackets,Transferred,Status,IsoResults;
        }
        [DllImport("UsbDkHelper.dll")] [return:MarshalAs(UnmanagedType.Bool)] public static extern bool UsbDk_GetDevicesList(out IntPtr devices,out uint count);
        [DllImport("UsbDkHelper.dll")] public static extern void UsbDk_ReleaseDevicesList(IntPtr devices);
        [DllImport("UsbDkHelper.dll")] public static extern IntPtr UsbDk_StartRedirect(ref Id id);
        [DllImport("UsbDkHelper.dll")] [return:MarshalAs(UnmanagedType.Bool)] public static extern bool UsbDk_StopRedirect(IntPtr redirect);
        [DllImport("UsbDkHelper.dll")] public static extern IntPtr UsbDk_GetRedirectorSystemHandle(IntPtr redirect);
        [DllImport("UsbDkHelper.dll")] public static extern int UsbDk_ReadPipe(IntPtr redirect,IntPtr request,IntPtr overlapped);
        [DllImport("UsbDkHelper.dll")] public static extern int UsbDk_WritePipe(IntPtr redirect,IntPtr request,IntPtr overlapped);
        [DllImport("UsbDkHelper.dll")] [return:MarshalAs(UnmanagedType.Bool)] public static extern bool UsbDk_GetConfigurationDescriptor(ref ConfigRequest request,out IntPtr descriptor,out uint length);
        [DllImport("UsbDkHelper.dll")] public static extern void UsbDk_ReleaseConfigurationDescriptor(IntPtr descriptor);
        [DllImport("kernel32.dll",SetLastError=true)] [return:MarshalAs(UnmanagedType.Bool)] public static extern bool GetOverlappedResult(IntPtr handle,IntPtr overlapped,out uint count,[MarshalAs(UnmanagedType.Bool)] bool wait);
        [DllImport("kernel32.dll",SetLastError=true)] [return:MarshalAs(UnmanagedType.Bool)] public static extern bool CancelIoEx(IntPtr handle,IntPtr overlapped);
    }
    private static Native.DeviceInfo[] Devices() {
        if(!Native.UsbDk_GetDevicesList(out var list,out var count)) throw new IOException("UsbDk cannot enumerate devices.");
        try { if(count>1024) throw new IOException("Invalid USB device count.");
            return Enumerable.Range(0,(int)count).Select(i=>Marshal.PtrToStructure<Native.DeviceInfo>(list+i*Marshal.SizeOf<Native.DeviceInfo>())).ToArray();
        } finally {Native.UsbDk_ReleaseDevicesList(list);}
    }
    public static string[] ListPhones() => Devices().Where(d=>d.Descriptor.Vendor is 0x2717 or 0x18D1 or 0x04E8 or 0x22B8 or 0x2A70)
        .Select(d=>$"{d.Descriptor.Vendor:X4}:{d.Descriptor.Product:X4} {d.Id.Instance}").ToArray();
    private static bool Accessory(Native.DeviceInfo device) => device.Descriptor.Vendor==0x18D1 && device.Descriptor.Product is 0x2D00 or 0x2D01;
    private static IntPtr Redirect(Native.DeviceInfo device) {
        var id=device.Id; var handle=Native.UsbDk_StartRedirect(ref id);
        if(handle==IntPtr.Zero || handle==new IntPtr(-1)) throw new IOException("Selected USB device is busy or redirect failed.");
        return handle;
    }
    public static async Task<UsbAccessoryStream> OpenAsync(CancellationToken stop,string? serial=null,Action<string>? log=null) {
        if(string.IsNullOrWhiteSpace(serial)) throw new InvalidDataException("Select the phone USB serial before connecting; arbitrary USB devices are never probed.");
        while(true) {
            stop.ThrowIfCancellationRequested();
            var selected=Devices().Where(d=>string.Equals(d.Id.Instance,serial,StringComparison.OrdinalIgnoreCase)).ToArray();
            if(selected.Length==0) selected=Devices().Where(d=>Accessory(d) && d.Id.Instance==UsbAccessoryIdentity.Serial).ToArray();
            if(selected.Length==0) { log?.Invoke("usb_waiting_phone");await Task.Delay(1000,stop);continue; }
            if(selected.Length!=1) throw new IOException("USB selection is ambiguous.");
            var device=selected[0];
            if(!Accessory(device)) {
                log?.Invoke("usb_switching_accessory");
                log?.Invoke("usb_redirect_initial");
                IntPtr initial=Redirect(device);
                try {
                    log?.Invoke("usb_query_protocol");
                    byte[] protocol=Control(initial,true,51,0,new byte[2],stop);
                    if(protocol[8]==0 && protocol[9]==0) throw new PlatformNotSupportedException("Phone does not support Android Open Accessory.");
                    string[] identity=UsbAccessoryIdentity.Strings();
                    log?.Invoke("usb_send_identity");
                    for(ushort index=0;index<identity.Length;index++) Control(initial,false,52,index,Encoding.UTF8.GetBytes(identity[index]+"\0"),stop);
                    log?.Invoke("usb_start_accessory");
                    Control(initial,false,53,0,[],stop);
                } finally {log?.Invoke("usb_release_initial");Native.UsbDk_StopRedirect(initial);}
                log?.Invoke("usb_waiting_accessory");
                device=await UsbAccessoryTransition.WaitAsync<Native.DeviceInfo>(()=> {
                    var accessories=Devices().Where(d=>Accessory(d) && (string.Equals(d.Id.Instance,serial,StringComparison.OrdinalIgnoreCase) || d.Id.Instance==UsbAccessoryIdentity.Serial)).ToArray();
                    if(accessories.Length>1)throw new IOException("USB selection is ambiguous.");
                    return accessories.Length==1?accessories[0]:null;
                },TimeSpan.FromSeconds(20),stop);
            }
            var request=new Native.ConfigRequest {Id=device.Id,Index=0};
            log?.Invoke("usb_read_configuration");
            if(!Native.UsbDk_GetConfigurationDescriptor(ref request,out var ptr,out var length)) throw new IOException("Missing accessory configuration.");
            byte[] config;
            try {if(length>65535) throw new IOException("Invalid USB configuration.");config=new byte[length];Marshal.Copy(ptr,config,0,(int)length);}
            finally {Native.UsbDk_ReleaseConfigurationDescriptor(ptr);}
            var endpoints=UsbAccessoryDescriptors.Endpoints(config);
            log?.Invoke("usb_accessory_open");
            log?.Invoke("usb_redirect_accessory");
            return new UsbAccessoryStream(Redirect(device),endpoints.Input,endpoints.Output,endpoints.PacketSize,stop);
        }
    }
    private static byte[] Control(IntPtr handle,bool input,byte request,ushort index,byte[] data,CancellationToken stop) {
        var buffer=new byte[8+data.Length];buffer[0]=input?(byte)0xC0:(byte)0x40;buffer[1]=request;
        buffer[4]=(byte)index;buffer[5]=(byte)(index>>8);buffer[6]=(byte)data.Length;buffer[7]=(byte)(data.Length>>8);data.CopyTo(buffer,8);
        using var deadline=CancellationTokenSource.CreateLinkedTokenSource(stop);deadline.CancelAfter(TimeSpan.FromSeconds(5));
        Transfer(handle,0,buffer,0,buffer.Length,input,0,deadline.Token);
        return buffer;
    }
    private static int Transfer(IntPtr handle,byte endpoint,byte[] bytes,int offset,int count,bool input,ulong type,CancellationToken stop) {
        stop.ThrowIfCancellationRequested();
        var pinned=GCHandle.Alloc(bytes,GCHandleType.Pinned);
        var requestPtr=Marshal.AllocHGlobal(Marshal.SizeOf<Native.Request>());
        var overlapped=Marshal.AllocHGlobal(32);
        using var done=new EventWaitHandle(false,EventResetMode.ManualReset);
        try {
            Marshal.Copy(new byte[32],0,overlapped,32);Marshal.WriteIntPtr(overlapped,24,done.SafeWaitHandle.DangerousGetHandle());
            Marshal.StructureToPtr(new Native.Request {Endpoint=endpoint,Buffer=(ulong)(pinned.AddrOfPinnedObject()+offset).ToInt64(),Length=(ulong)count,Type=type},requestPtr,false);
            IntPtr system=Native.UsbDk_GetRedirectorSystemHandle(handle);
            int result=input ? Native.UsbDk_ReadPipe(handle,requestPtr,overlapped) : Native.UsbDk_WritePipe(handle,requestPtr,overlapped);
            if(result==0) throw new IOException("USB transfer rejected.");
            using var cancellation=stop.Register(()=>Native.CancelIoEx(system,overlapped));
            if(result==2 && !Native.GetOverlappedResult(system,overlapped,out _,true)) {
                stop.ThrowIfCancellationRequested();throw new IOException("USB transfer interrupted: "+Marshal.GetLastWin32Error());
            }
            var completed=Marshal.PtrToStructure<Native.Request>(requestPtr);
            if(completed.Status!=0 || completed.Transferred>(ulong)count) throw new IOException("USB transfer failed.");
            return (int)completed.Transferred;
        } finally {Marshal.FreeHGlobal(overlapped);Marshal.FreeHGlobal(requestPtr);pinned.Free();}
    }
    private readonly SemaphoreSlim readLock=new(1,1);
    private readonly byte[] received;
    private int receivedOffset,receivedCount;
    public override int Read(byte[] buffer,int offset,int count) => ReadAsync(buffer.AsMemory(offset,count)).AsTask().GetAwaiter().GetResult();
    public override void Write(byte[] buffer,int offset,int count) => WriteAsync(buffer.AsMemory(offset,count)).AsTask().GetAwaiter().GetResult();
    public override async ValueTask<int> ReadAsync(Memory<byte> buffer,CancellationToken stop=default) {
        if(buffer.Length==0)return 0;
        await readLock.WaitAsync(stop);
        try {
            while(receivedCount==0) {
                // Zero-length USB packets terminate a bulk write; they are not stream EOF.
                stop.ThrowIfCancellationRequested();
                receivedOffset=0;
                receivedCount=await Task.Run(()=>Transfer(redirect,inputEndpoint,received,0,received.Length,true,1,stop),CancellationToken.None);
            }
            int count=Math.Min(buffer.Length,receivedCount);
            received.AsMemory(receivedOffset,count).CopyTo(buffer);
            receivedOffset+=count;receivedCount-=count;
            return count;
        } finally {readLock.Release();}
    }
    public override async ValueTask WriteAsync(ReadOnlyMemory<byte> buffer,CancellationToken stop=default) {
        byte[] bytes=buffer.ToArray();try {await Task.Run(()=>{int offset=0;while(offset<bytes.Length){int sent=Transfer(redirect,outputEndpoint,bytes,offset,bytes.Length-offset,false,1,stop);if(sent==0)throw new IOException("USB short write.");offset+=sent;}},CancellationToken.None);}
        finally {Array.Clear(bytes);}
    }
    protected override void Dispose(bool disposing) {if(redirect!=IntPtr.Zero){stopRegistration.Dispose();Native.UsbDk_StopRedirect(redirect);redirect=IntPtr.Zero;}base.Dispose(disposing);}
    public override bool CanRead=>redirect!=IntPtr.Zero;public override bool CanWrite=>CanRead;public override bool CanSeek=>false;
    public override void Flush(){}public override long Length=>throw new NotSupportedException();public override long Position {get=>throw new NotSupportedException();set=>throw new NotSupportedException();}
    public override long Seek(long o,SeekOrigin s)=>throw new NotSupportedException();public override void SetLength(long v)=>throw new NotSupportedException();
}

public static class UsbAccessoryDescriptors
{
    public static (byte Input,byte Output,int PacketSize) Endpoints(byte[] config) {
        byte input=0,output=0;int packetSize=0;bool accessory=false;
        for(int at=0;at<config.Length;) {
            int length=config[at];if(length<2 || at+length>config.Length) throw new InvalidDataException("Invalid USB descriptor.");
            if(config[at+1]==4) {if(length<9)throw new InvalidDataException("Invalid interface.");accessory=config[at+3]==0 && config[at+5]==0xFF && config[at+6]==0xFF && config[at+7]==0;}
            if(config[at+1]==5 && accessory) {
                if(length<7)throw new InvalidDataException("Invalid endpoint.");
                if((config[at+3]&3)==2) {byte ep=config[at+2];if((ep&0x80)!=0){input=ep;packetSize=(config[at+4] | config[at+5]<<8)&0x7FF;}else output=ep;}
            }
            at+=length;
        }
        if(input==0 || output==0 || packetSize is < 8 or > 1024)throw new InvalidDataException("No accessory bulk endpoints; ADB is not a substitute.");
        return(input,output,packetSize);
    }
}
