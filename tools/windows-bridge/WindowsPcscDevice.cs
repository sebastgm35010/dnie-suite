using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using Microsoft.Win32.SafeHandles;

namespace DniBridge;

/// <summary>Only the original DNIe UMDF reader's private interface, never the Bix TCP driver.</summary>
public sealed class WindowsPcscDevice : IPcscDevice
{
    public static readonly Guid InterfaceId = new("6D735823-37A5-4DF8-98C4-0E512B45D8F1");
    private readonly SafeFileHandle handle;

    public static string[] Enumerate()
    {
        if (!OperatingSystem.IsWindows()) throw new PlatformNotSupportedException();
        Guid guid = InterfaceId;
        for (int attempt = 0; attempt < 3; attempt++)
        {
            uint status = CM_Get_Device_Interface_List_SizeW(out uint size, ref guid, IntPtr.Zero, 0);
            if (status != 0) throw new IOException("Could not enumerate reader interfaces.");
            if (size is < 1 or > 65536) throw new IOException("Invalid interface list size.");
            var list = new char[size];
            status = CM_Get_Device_Interface_ListW(ref guid, IntPtr.Zero, list, size, 0);
            if (status == 26) continue; // CR_BUFFER_SMALL, list changed after sizing.
            if (status != 0) throw new IOException("Could not enumerate reader interfaces.");
            return new string(list).Split('\0', StringSplitOptions.RemoveEmptyEntries);
        }
        throw new IOException("Reader interfaces changed during enumeration.");
    }

    public WindowsPcscDevice(string path)
    {
        if (!Enumerate().Contains(path, StringComparer.OrdinalIgnoreCase))
            throw new ArgumentException("Device is not a present DNIe bridge interface.");
        // Deliberately synchronous: all four private IOCTLs are nonblocking polling operations.
        handle = CreateFileW(path, 0xC0000000, 3, IntPtr.Zero, 3, 0, IntPtr.Zero);
        if (handle.IsInvalid)
        {
            int error = Marshal.GetLastWin32Error(); handle.Dispose();
            throw new Win32Exception(error, "Reader backend access denied or unavailable; no Windows policies were changed.");
        }
    }

    public void Attach(byte[] atr, uint protocol)
    {
        byte[] encoded = PcscAbi.EncodeAttach(atr, protocol);
        try { Call(PcscAbi.Attach, encoded, []); }
        finally { CryptographicOperations.ZeroMemory(encoded); }
    }

    public PcscRequest? Poll()
    {
        byte[] output = new byte[24 + PcscAbi.MaxPayload];
        try
        {
            if (!DeviceIoControl(handle, PcscAbi.GetRequest, [], 0, output, output.Length, out int length, IntPtr.Zero))
            {
                int error = Marshal.GetLastWin32Error();
                if (error == 259) return null; // STATUS_NO_MORE_ENTRIES -> ERROR_NO_MORE_ITEMS
                throw new Win32Exception(error);
            }
            if (length < 0 || length > output.Length) throw new InvalidDataException("Invalid driver output size.");
            return PcscAbi.DecodeRequest(output.AsSpan(0, length));
        }
        finally { CryptographicOperations.ZeroMemory(output); }
    }

    public void Complete(ulong id, uint status, byte[] payload)
    {
        byte[] encoded = PcscAbi.EncodeCompletion(id, status, payload);
        try { Call(PcscAbi.Complete, encoded, []); }
        finally { CryptographicOperations.ZeroMemory(encoded); }
    }

    public void Detach() => Call(PcscAbi.Detach, [], []);
    public void Republish() => Call(PcscAbi.Republish, [], []);

    public static bool TryRepublish()
    {
        try
        {
            string[] devices = Enumerate();
            if (devices.Length != 1) return false;
            using var device = new WindowsPcscDevice(devices[0]);
            device.Republish();
            return true;
        }
        catch
        {
            return false;
        }
    }

    private void Call(uint code, byte[] input, byte[] output)
    {
        if (!DeviceIoControl(handle, code, input, input.Length, output, output.Length, out _, IntPtr.Zero))
            throw new Win32Exception(Marshal.GetLastWin32Error());
    }
    public void Dispose() => handle.Dispose(); // Driver file cleanup also detaches on crash.

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern SafeFileHandle CreateFileW(string path, uint access, uint sharing, IntPtr security,
        uint disposition, uint flags, IntPtr template);
    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool DeviceIoControl(SafeFileHandle device, uint code, byte[] input, int inputSize,
        byte[] output, int outputSize, out int returned, IntPtr overlapped);
    [DllImport("cfgmgr32.dll", CharSet = CharSet.Unicode)]
    private static extern uint CM_Get_Device_Interface_List_SizeW(out uint size, ref Guid guid, IntPtr device, uint flags);
    [DllImport("cfgmgr32.dll", CharSet = CharSet.Unicode)]
    private static extern uint CM_Get_Device_Interface_ListW(ref Guid guid, IntPtr device,
        [Out] char[] buffer, uint size, uint flags);
}
