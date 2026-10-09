using System.Buffers.Binary;
using System.Net;
using System.Net.Sockets;
using DniBridge;

static class PcscBackendTests
{
    public static async Task<int> RunAsync()
    {
        int checks = 0;
        void Check(bool value) { if (!value) throw new Exception("PCSC contract regression."); checks++; }
        void Reject(Action action)
        {
            try { action(); throw new Exception("Expected ABI rejection."); }
            catch (InvalidDataException) { checks++; }
        }
        byte[] atr = [0x3B, 0x80, 0x80, 0x01, 0x01];
        // EOF during a command must survive both completion and detach failures.
        using (var cleanupDevice = new CleanupFailureDevice())
        using (var cleanupStream = new ScriptedStream([0, 5, .. atr]))
        {
            var phases = new List<string>();
            try { await PcscBackend.RunAsync(cleanupDevice,cleanupStream,CancellationToken.None,phases.Add); throw new Exception("Expected EOF"); }
            catch (EndOfStreamException) { Check(cleanupDevice.DetachAttempted); }
            Check(phases.Any(x => x.StartsWith("pcsc_completion_cleanup_failed")));
            Check(phases.Any(x => x.StartsWith("pcsc_detach_cleanup_failed")));
            Check(!phases.Contains("pcsc_card_removed"));
        }
        using (var rejectedOwner = new CleanupFailureDevice { RejectAttach = true })
        using (var attachStream = new ScriptedStream([0, 5, .. atr]))
        {
            try { await PcscBackend.RunAsync(rejectedOwner,attachStream,CancellationToken.None); throw new Exception("Expected rejected owner"); }
            catch (UnauthorizedAccessException) { Check(!rejectedOwner.DetachAttempted); }
        }
        var attach = PcscAbi.EncodeAttach(atr, 2);
        Check(attach.Length == 21 && BinaryPrimitives.ReadUInt32LittleEndian(attach.AsSpan(8)) == 5 && attach.AsSpan(16).SequenceEqual(atr));
        Reject(() => PcscAbi.EncodeAttach([0x3B, 0x80, 0x80, 1, 0], 2));
        Reject(() => PcscAbi.EncodeAttach([0x3B, 0x8F], 2));
        Reject(() => PcscAbi.EncodeAttach(atr, 3));
        byte[] encoded = new byte[28];
        BinaryPrimitives.WriteUInt64LittleEndian(encoded, 9);
        BinaryPrimitives.WriteUInt32LittleEndian(encoded.AsSpan(8), PcscAbi.Transmit);
        BinaryPrimitives.WriteUInt32LittleEndian(encoded.AsSpan(12), 4);
        BinaryPrimitives.WriteUInt32LittleEndian(encoded.AsSpan(16), 258);
        byte[] apdu = [0, 0xB0, 0, 0]; apdu.CopyTo(encoded, 24);
        var parsed = PcscAbi.DecodeRequest(encoded);
        Check(parsed.Id == 9 && parsed.Payload.SequenceEqual(apdu) && parsed.OutputCapacity == 258);
        Reject(() => PcscAbi.DecodeRequest(encoded.AsSpan(0, 27)));
        encoded[20] = 1; Reject(() => PcscAbi.DecodeRequest(encoded)); encoded[20] = 0;
        BinaryPrimitives.WriteUInt64LittleEndian(encoded, 0); Reject(() => PcscAbi.DecodeRequest(encoded));
        Reject(() => PcscAbi.EncodeCompletion(1, PcscAbi.DeviceDisconnected, [0x90, 0]));
        var completion = PcscAbi.EncodeCompletion(9, 0, [0x6A, 0x82]);
        Check(completion.Length == 18 && completion[16] == 0x6A && completion[17] == 0x82);

        using var stop = new CancellationTokenSource(TimeSpan.FromSeconds(5));
        using var listener = new TcpListener(IPAddress.Loopback, 0); listener.Start();
        using var local = new TcpClient();
        var accept = listener.AcceptTcpClientAsync(stop.Token);
        await local.ConnectAsync(IPAddress.Loopback, ((IPEndPoint)listener.LocalEndpoint).Port, stop.Token);
        using var remote = await accept;
        var card = Task.Run(async () =>
        {
            var stream = remote.GetStream();
            Check((await ReadFrame(stream, stop.Token)).SequenceEqual(new byte[] { 4 }));
            await WriteFrame(stream, atr, stop.Token);
            Check((await ReadFrame(stream, stop.Token)).SequenceEqual(apdu));
            // Preserve an actual unsuccessful SW, not fake success.
            await WriteFrame(stream, [0x6A, 0x82], stop.Token);
            Check((await ReadFrame(stream, stop.Token)).SequenceEqual(apdu));
            await WriteFrame(stream, [1, 2, 0x90, 0], stop.Token);
            Check((await ReadFrame(stream, stop.Token)).SequenceEqual(new byte[] { 2 }));
            await WriteFrame(stream, atr, stop.Token);
            Check((await ReadFrame(stream, stop.Token)).SequenceEqual(new byte[] { 0 }));
            await WriteFrame(stream, [1], stop.Token);
            remote.Close();
        });
        using var device = new FakeDevice(new Queue<PcscRequest>([
            new(1, 99, 0, []), new(2, PcscAbi.Transmit, 258, apdu.ToArray()),
            new(3, PcscAbi.Transmit, 2, apdu.ToArray()), new(4, PcscAbi.Reset, 33, []),
            new(5, PcscAbi.PowerOff, 0, []), new(6, PcscAbi.Transmit, 258, apdu.ToArray())]));
        try { await PcscBackend.RunAsync(device, local.GetStream(), stop.Token); throw new Exception("Expected EOF."); }
        catch (Exception e) when (e is EndOfStreamException or IOException) { }
        await card;
        Check(device.Atr!.SequenceEqual(atr) && device.Detached);
        Check(device.Results[0].Status == PcscAbi.NotSupported);
        Check(device.Results[1].Id == 2 && device.Results[1].Bytes.SequenceEqual(new byte[] { 0x6A, 0x82 }));
        Check(device.Results[2].Status == PcscAbi.BufferTooSmall && device.Results[2].Bytes.Length == 0);
        Check(device.Results[3].Status == 0 && device.Results[3].Bytes.SequenceEqual(atr));
        Check(device.Results[4].Status == 0 && device.Results[4].Bytes.Length == 0);
        Check(device.Results[5].Id == 6 && device.Results[5].Status == PcscAbi.DeviceDisconnected);

        // A malformed reset ATR fails closed, completing the outstanding reset as disconnected.
        using var resetDevice = new FakeDevice(new Queue<PcscRequest>([new(8, PcscAbi.Reset, 33, [])]));
        using var resetStream = new ScriptedStream([0, 5, .. atr, 0, 2, 0x3B, 0x8F]);
        try { await PcscBackend.RunAsync(resetDevice, resetStream, stop.Token); throw new Exception("Expected invalid reset ATR."); }
        catch (InvalidDataException) { Check(resetDevice.Detached && resetDevice.Results.Single().Status == PcscAbi.DeviceDisconnected); }

        using var heartbeatStop = new CancellationTokenSource();
        using var heartbeatDevice = new FakeDevice(new Queue<PcscRequest>());
        using var heartbeatStream = new ScriptedStream([0, 5, .. atr, 0, 1, 1]);
        heartbeatDevice.OnEmpty = () => { if (heartbeatStream.Written.Length >= 6) heartbeatStop.Cancel(); };
        try { await PcscBackend.RunAsync(heartbeatDevice, heartbeatStream, heartbeatStop.Token, heartbeatInterval: TimeSpan.FromMilliseconds(1)); }
        catch (OperationCanceledException) { }
        Check(heartbeatStream.Written.ToArray().SequenceEqual(new byte[] { 0, 1, 4, 0, 1, 5 }) && heartbeatDevice.Detached);

        // Malformed ATR never establishes ownership; do not detach another owner.
        using var invalidDevice = new FakeDevice(new Queue<PcscRequest>());
        using var invalidStream = new ScriptedStream([0, 2, 0x3B, 0x8F]);
        try { await PcscBackend.RunAsync(invalidDevice, invalidStream, stop.Token); throw new Exception("Expected invalid ATR."); }
        catch (InvalidDataException) { Check(invalidDevice.Atr == null && !invalidDevice.Detached); }

        // EOF during an APDU completes its outstanding id as disconnected, then removes card.
        using var lostDevice = new FakeDevice(new Queue<PcscRequest>([new(42, PcscAbi.Transmit, 258, apdu.ToArray())]));
        using var lostStream = new ScriptedStream([0, 5, .. atr]);
        try { await PcscBackend.RunAsync(lostDevice, lostStream, stop.Token); throw new Exception("Expected EOF."); }
        catch (EndOfStreamException)
        { Check(lostDevice.Detached && lostDevice.Results.Single().Id == 42 && lostDevice.Results.Single().Status == PcscAbi.DeviceDisconnected); }
        // A paired PC link may wait for physical NFC without announcing a synthetic card.
        using (var cancellation = new CancellationTokenSource())
        using (var waitingDevice = new FakeDevice(new Queue<PcscRequest>()))
        using (var waitingStream = new WaitingAtrStream(cancellation.Token)) {
            var session = PcscBackend.RunAsync(waitingDevice, waitingStream, cancellation.Token);
            await waitingStream.Reading.Task.WaitAsync(TimeSpan.FromSeconds(2));
            Check(waitingStream.SessionTokenUsed);
            Check(waitingDevice.Atr == null && !waitingDevice.Detached);
            cancellation.Cancel();
            try { await session; throw new Exception("Expected cancelled card wait."); }
            catch (OperationCanceledException) { Check(waitingDevice.Atr == null && !waitingDevice.Detached); }
        }
        return checks;
    }
    private static async Task<byte[]> ReadFrame(Stream stream, CancellationToken ct)
    {
        byte[] header = new byte[2]; await stream.ReadExactlyAsync(header, ct);
        var bytes = new byte[(header[0] << 8) | header[1]]; await stream.ReadExactlyAsync(bytes, ct); return bytes;
    }
    private static async Task WriteFrame(Stream stream, byte[] bytes, CancellationToken ct)
    { await stream.WriteAsync(new byte[] { (byte)(bytes.Length >> 8), (byte)bytes.Length }, ct); await stream.WriteAsync(bytes, ct); }
    private sealed class CleanupFailureDevice : IPcscDevice
    {
        public bool RejectAttach, DetachAttempted;
        public void Attach(byte[] atr,uint protocol) { if (RejectAttach) throw new UnauthorizedAccessException(); }
        public PcscRequest Poll() => new(1,PcscAbi.Transmit,258,[0,0xB0,0,0]);
        public void Complete(ulong id,uint status,byte[] payload) => throw new System.ComponentModel.Win32Exception(5);
        public void Detach() { DetachAttempted=true; throw new System.ComponentModel.Win32Exception(5); }
        public void Dispose() { }
    }
    private sealed class FakeDevice(Queue<PcscRequest> requests) : IPcscDevice
    {
        public byte[]? Atr; public bool Detached;
        public Action? OnEmpty;
        public List<(ulong Id, uint Status, byte[] Bytes)> Results = [];
        public void Attach(byte[] atr, uint protocol) { PcscAbi.ValidateAtr(atr); Atr = atr.ToArray(); }
        public PcscRequest? Poll() { if (requests.TryDequeue(out var item)) return item; OnEmpty?.Invoke(); return null; }
        public void Complete(ulong id, uint status, byte[] payload) => Results.Add((id, status, payload.ToArray()));
        public void Detach() => Detached = true;
        public void Dispose() { }
    }
    private sealed class WaitingAtrStream(CancellationToken session) : Stream {
        public readonly TaskCompletionSource Reading = new(TaskCreationOptions.RunContinuationsAsynchronously);
        public bool SessionTokenUsed;
        public override bool CanRead => true; public override bool CanWrite => true; public override bool CanSeek => false;
        public override long Length => throw new NotSupportedException();
        public override long Position {get=>throw new NotSupportedException();set=>throw new NotSupportedException();}
        public override async ValueTask<int> ReadAsync(Memory<byte> buffer, CancellationToken cancellation=default) {
            SessionTokenUsed = cancellation == session && buffer.Length == 1;
            Reading.TrySetResult(); await Task.Delay(Timeout.Infinite, cancellation); return 0;
        }
        public override ValueTask WriteAsync(ReadOnlyMemory<byte> buffer,CancellationToken cancellation=default)=>ValueTask.CompletedTask;
        public override Task FlushAsync(CancellationToken cancellation)=>Task.CompletedTask;
        public override void Flush() {}
        public override int Read(byte[] b,int o,int c)=>throw new NotSupportedException();
        public override void Write(byte[] b,int o,int c) {}
        public override long Seek(long o,SeekOrigin origin)=>throw new NotSupportedException();
        public override void SetLength(long length)=>throw new NotSupportedException();
    }
    private sealed class ScriptedStream(byte[] input) : Stream
    {
        private readonly MemoryStream incoming = new(input);
        public readonly MemoryStream Written = new();
        public override bool CanRead => true; public override bool CanWrite => true; public override bool CanSeek => false;
        public override long Length => throw new NotSupportedException();
        public override long Position { get => throw new NotSupportedException(); set => throw new NotSupportedException(); }
        public override int Read(byte[] buffer, int offset, int count) => incoming.Read(buffer, offset, count);
        public override ValueTask<int> ReadAsync(Memory<byte> buffer, CancellationToken ct = default) => incoming.ReadAsync(buffer, ct);
        public override void Write(byte[] buffer, int offset, int count) => Written.Write(buffer, offset, count);
        public override ValueTask WriteAsync(ReadOnlyMemory<byte> buffer, CancellationToken ct = default) { Written.Write(buffer.Span); return ValueTask.CompletedTask; }
        public override void Flush() { }
        public override long Seek(long offset, SeekOrigin origin) => throw new NotSupportedException();
        public override void SetLength(long length) => throw new NotSupportedException();
        protected override void Dispose(bool disposing) { if (disposing) incoming.Dispose(); base.Dispose(disposing); }
    }
}
