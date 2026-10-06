using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Security.Principal;
using System.Text.Json;
using DniBridge;

if (args.Length == 2 && args[0] == "--idplug-fixture") {
    await IdplugFixture.RunAsync(args[1]); return;
}
if (args.Length == 2 && args[0] == "--idplug-self-test") {
    IdplugFixture.SelfTest(args[1]); return;
}

if (args.Length == 2 && args[0] == "--session-relay") {
    await SessionRelayTest.RunAsync(args[1]); return;
}
if (args.Length == 2 && args[0] == "--browser-fixture") {
    await BrowserInteropFixture.RunAsync(args[1]); return;
}

// Native installed-driver tests. Never connects to NFC, TLS, Bix or a physical card.
var checks = new List<string>();
if (!OperatingSystem.IsWindows()) throw new PlatformNotSupportedException();
var result = args.Length > 0 ? args[0] : "D:/DNIeToolchain/ReaderSynthetic/result.json";
IntPtr context = IntPtr.Zero, card = IntPtr.Zero;
WindowsPcscDevice? backend = null;
CancellationTokenSource? stop = null;
Task? worker = null;
void Check(bool success, string name) { if (!success) throw new Exception(name); checks.Add(name); }
void Ok(uint status, string name) => Check(status == 0, $"{name} (0x{status:X8})");
byte[] atr = [0x3B, 0x80, 0x01, 0x81]; // Explicitly simulated T=1 card; valid TCK.
try
{
    Check(WindowsIdentity.GetCurrent().User?.Value == "S-1-5-18", "test runs as SYSTEM under initial device ACL");
    string[] devices = WindowsPcscDevice.Enumerate();
    Check(devices.Length == 1, "one original backend interface");
    Ok(Native.SCardEstablishContext(2, IntPtr.Zero, IntPtr.Zero, out context), "PCSC context");
    uint length = 0;
    Ok(Native.SCardListReadersW(context, null, null, ref length), "reader list size");
    var names = new char[length];
    Ok(Native.SCardListReadersW(context, null, names, ref length), "reader list");
    string[] readers = new string(names).Split('\0', StringSplitOptions.RemoveEmptyEntries);
    Console.WriteLine("PCSC readers: " + string.Join(" | ", readers));
    string[] ours = readers.Where(x => x.Contains("DNI", StringComparison.OrdinalIgnoreCase)).ToArray();
    Check(ours.Length == 1, "original reader visible to SCardListReaders");
    string reader = ours[0];
    Console.WriteLine($"Testing original reader: {reader}");
    var state = new[] { new Native.ReaderState { Reader = reader, Atr = new byte[36] } };
    Ok(Native.SCardGetStatusChangeW(context, 0, state, 1), "initial presence query");
    Check((state[0].EventState & 0x10) != 0, "card initially absent");
    backend = new WindowsPcscDevice(devices[0]);
    backend.Attach(atr, 2);
    using (var outsider = new WindowsPcscDevice(devices[0]))
    {
        try { outsider.Poll(); throw new Exception("another handle read owner requests"); }
        catch (Win32Exception e) { Check(e.NativeErrorCode == 5, "non-owner cannot poll"); }
        try { outsider.Attach(atr, 2); throw new Exception("duplicate attach succeeded"); }
        catch (Win32Exception) { checks.Add("second attach rejected"); }
    }
    Check(backend.Poll() == null, "no pending request gives empty poll");
    stop = new CancellationTokenSource();
    worker = Task.Run(async () =>
    {
        while (!stop.IsCancellationRequested)
        {
            var request = backend.Poll();
            if (request == null) { await Task.Delay(10); continue; }
            byte[] answer = request.Opcode switch
            {
                PcscAbi.Reset => atr,
                PcscAbi.PowerOff => [],
                PcscAbi.Transmit => request.Payload.SequenceEqual(new byte[] { 0, 0xB0, 0, 0, 1 })
                    ? new byte[] { 0xDE, 0xAD, 0x90, 0 } : new byte[] { 0x6A, 0x82 },
                _ => throw new Exception("unexpected driver opcode")
            };
            backend.Complete(request.Id, 0, answer);
        }
    });
    state[0].CurrentState = 0;
    Ok(Native.SCardGetStatusChangeW(context, 3000, state, 1), "attached presence query");
    Check((state[0].EventState & 0x20) != 0, "card present after attach");
    Ok(Native.SCardConnectW(context, reader, 2, 2, out card, out uint protocol), "T1 connect and synthetic reset");
    Check(protocol == 2, "T1 negotiation");
    byte[] Send(byte[] command)
    {
        var pci = new Native.Pci { Protocol = protocol, Length = 8 };
        var output = new byte[256]; uint size = (uint)output.Length;
        Ok(Native.SCardTransmit(card, ref pci, command, (uint)command.Length, IntPtr.Zero, output, ref size), "transmit");
        return output[..(int)size];
    }
    Check(Send([0, 0xA4, 4, 0, 0]).SequenceEqual(new byte[] { 0x6A, 0x82 }), "card status word 6A82 preserved");
    Check(Send([0, 0xB0, 0, 0, 1]).SequenceEqual(new byte[] { 0xDE, 0xAD, 0x90, 0 }), "response bytes preserved");
    Ok(Native.SCardReconnect(card, 2, 2, 1, out protocol), "warm reset via backend");
    Ok(Native.SCardDisconnect(card, 2), "disconnect with power off"); card = IntPtr.Zero;
    stop.Cancel(); await worker.WaitAsync(TimeSpan.FromSeconds(5)); worker = null;
    // Wait from the last observed present state, rather than accepting the manager's
    // immediate cached snapshot before the asynchronous file-cleanup notification.
    state[0].CurrentState = state[0].EventState & ~2u;
    backend.Dispose(); backend = null;
    Ok(Native.SCardGetStatusChangeW(context, 3000, state, 1), "owner-close query");
    Check((state[0].EventState & 0x10) != 0, "owner close removes card");
    state[0].CurrentState = state[0].EventState & ~2u;
    var wait = Task.Run(() => Native.SCardGetStatusChangeW(context, 10000, state, 1));
    await Task.Delay(200);
    Ok(Native.SCardCancel(context), "cancel context wait");
    Check(await wait.WaitAsync(TimeSpan.FromSeconds(3)) == 0x80100002, "presence wait cancellation");
    await File.WriteAllTextAsync(result, JsonSerializer.Serialize(new { success = true, checks, syntheticOnly = true }, new JsonSerializerOptions { WriteIndented = true }));
    Console.WriteLine($"PASS {checks.Count} installed-reader checks");
}
catch (Exception error)
{
    Console.Error.WriteLine(error);
    await File.WriteAllTextAsync(result, JsonSerializer.Serialize(new { success = false, checks, error = error.ToString(), syntheticOnly = true }, new JsonSerializerOptions { WriteIndented = true }));
    Environment.ExitCode = 1;
}
finally
{
    stop?.Cancel();
    if (worker != null) { try { await worker.WaitAsync(TimeSpan.FromSeconds(3)); } catch { } }
    if (card != IntPtr.Zero) Native.SCardDisconnect(card, 0);
    backend?.Dispose(); stop?.Dispose();
    if (context != IntPtr.Zero) Native.SCardReleaseContext(context);
}

static class Native
{
    [StructLayout(LayoutKind.Sequential)] public struct Pci { public uint Protocol, Length; }
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    public struct ReaderState
    {
        [MarshalAs(UnmanagedType.LPWStr)] public string Reader;
        public IntPtr UserData; public uint CurrentState, EventState, AtrLength;
        [MarshalAs(UnmanagedType.ByValArray, SizeConst = 36)] public byte[] Atr;
    }
    [DllImport("winscard.dll")] public static extern uint SCardEstablishContext(uint scope, IntPtr r1, IntPtr r2, out IntPtr context);
    [DllImport("winscard.dll")] public static extern uint SCardReleaseContext(IntPtr context);
    [DllImport("winscard.dll", CharSet=CharSet.Unicode)] public static extern uint SCardListReadersW(IntPtr context, string? groups, [Out] char[]? readers, ref uint size);
    [DllImport("winscard.dll", CharSet=CharSet.Unicode)] public static extern uint SCardGetStatusChangeW(IntPtr context, uint timeout, [In,Out] ReaderState[] states, uint count);
    [DllImport("winscard.dll", CharSet=CharSet.Unicode)] public static extern uint SCardConnectW(IntPtr context, string reader, uint share, uint protocols, out IntPtr card, out uint protocol);
    [DllImport("winscard.dll")] public static extern uint SCardReconnect(IntPtr card, uint share, uint protocols, uint initialization, out uint protocol);
    [DllImport("winscard.dll")] public static extern uint SCardDisconnect(IntPtr card, uint disposition);
    [DllImport("winscard.dll")] public static extern uint SCardCancel(IntPtr context);
    [DllImport("winscard.dll")] public static extern uint SCardTransmit(IntPtr card, ref Pci pci, byte[] input, uint inputLength, IntPtr receivePci, [Out] byte[] output, ref uint outputLength);
}
