using System.Buffers.Binary;
using System.Security.Cryptography;

namespace DniBridge;

public sealed record PcscRequest(ulong Id, uint Opcode, uint OutputCapacity, byte[] Payload);

/// <summary>Driver boundary; implementations must never manufacture successful card responses.</summary>
public interface IPcscDevice : IDisposable
{
    void Attach(byte[] atr, uint protocol);
    PcscRequest? Poll();
    void Complete(ulong id, uint status, byte[] payload);
    void Detach();
}

public static class PcscAbi
{
    public const uint Attach = 0x22E000, Detach = 0x22E004, GetRequest = 0x22E008, Complete = 0x22E00C;
    public const uint Transmit = 1, Reset = 2, PowerOff = 3;
    public const uint DeviceDisconnected = 0xC000009D;
    public const uint NotSupported = 0xC00000BB;
    public const uint BufferTooSmall = 0xC0000023;
    public const int MaxPayload = 65535;

    public static byte[] EncodeAttach(byte[] atr, uint protocol)
    {
        ValidateAtr(atr);
        if (protocol is not (1 or 2)) throw new InvalidDataException("Invalid protocol.");
        var result = new byte[16 + atr.Length];
        BinaryPrimitives.WriteUInt32LittleEndian(result, 1);
        BinaryPrimitives.WriteUInt32LittleEndian(result.AsSpan(4), protocol);
        BinaryPrimitives.WriteUInt32LittleEndian(result.AsSpan(8), (uint)atr.Length);
        atr.CopyTo(result, 16);
        return result;
    }

    public static PcscRequest DecodeRequest(ReadOnlySpan<byte> bytes)
    {
        if (bytes.Length < 24) throw new InvalidDataException("Truncated driver request.");
        ulong id = BinaryPrimitives.ReadUInt64LittleEndian(bytes);
        uint opcode = BinaryPrimitives.ReadUInt32LittleEndian(bytes[8..]);
        uint length = BinaryPrimitives.ReadUInt32LittleEndian(bytes[12..]);
        uint capacity = BinaryPrimitives.ReadUInt32LittleEndian(bytes[16..]);
        if (id == 0 || length > MaxPayload || capacity > MaxPayload || bytes.Length != 24 + length ||
            BinaryPrimitives.ReadUInt32LittleEndian(bytes[20..]) != 0)
            throw new InvalidDataException("Invalid driver request bounds.");
        return new(id, opcode, capacity, bytes[24..].ToArray());
    }

    public static byte[] EncodeCompletion(ulong id, uint status, byte[] payload)
    {
        if (id == 0 || payload.Length > MaxPayload || (status != 0 && payload.Length != 0))
            throw new InvalidDataException("Invalid completion.");
        var result = new byte[16 + payload.Length];
        BinaryPrimitives.WriteUInt64LittleEndian(result, id);
        BinaryPrimitives.WriteUInt32LittleEndian(result.AsSpan(8), status);
        BinaryPrimitives.WriteUInt32LittleEndian(result.AsSpan(12), (uint)payload.Length);
        payload.CopyTo(result, 16);
        return result;
    }

    // Parse the actual ATR including interface groups, declared historical bytes and TCK.
    public static void ValidateAtr(ReadOnlySpan<byte> atr)
    {
        if (atr.Length is < 2 or > 33 || atr[0] is not (0x3B or 0x3F))
            throw new InvalidDataException("Invalid ATR.");
        int index = 2, presence = atr[1] >> 4, historical = atr[1] & 15;
        bool checksum = false;
        while (presence != 0)
        {
            for (int bit = 1; bit <= 4; bit <<= 1)
                if ((presence & bit) != 0 && ++index > atr.Length) throw new InvalidDataException("Truncated ATR.");
            if ((presence & 8) == 0) break;
            if (index >= atr.Length) throw new InvalidDataException("Truncated ATR.");
            byte td = atr[index++]; checksum |= (td & 15) != 0; presence = td >> 4;
        }
        if (index + historical + (checksum ? 1 : 0) != atr.Length)
            throw new InvalidDataException("Invalid ATR length.");
        if (checksum)
        {
            byte xor = 0; foreach (byte item in atr[1..]) xor ^= item;
            if (xor != 0) throw new InvalidDataException("Invalid ATR checksum.");
        }
    }
}

/// <summary>Sequential PC/SC backend over authenticated session-local VPCD framing.</summary>
public static class PcscBackend
{
    public static async Task RunAsync(IPcscDevice device, Stream card, CancellationToken cancellation,
        Action<string>? log = null, TimeSpan? heartbeatInterval = null)
    {
        ulong pending = 0;
        try
        {
            byte[] atr = await ExchangeAsync(card, [4], cancellation);
            try { device.Attach(atr, 2); }
            finally { CryptographicOperations.ZeroMemory(atr); }
            log?.Invoke("pcsc_card_present");
            long lastTraffic = Environment.TickCount64;
            double pulseMs = (heartbeatInterval ?? TimeSpan.FromSeconds(10)).TotalMilliseconds;
            if (pulseMs < 1 || pulseMs > 10000) throw new ArgumentOutOfRangeException(nameof(heartbeatInterval));
            while (true)
            {
                cancellation.ThrowIfCancellationRequested();
                PcscRequest? request = device.Poll();
                cancellation.ThrowIfCancellationRequested();
                if (request == null)
                {
                    if (Environment.TickCount64 - lastTraffic >= pulseMs)
                    {
                        byte[] pulse = await ExchangeAsync(card, [5], cancellation);
                        try { if (!pulse.AsSpan().SequenceEqual(new byte[] { 1 })) throw new InvalidDataException("Heartbeat rejected."); }
                        finally { CryptographicOperations.ZeroMemory(pulse); }
                        lastTraffic = Environment.TickCount64;
                    }
                    await Task.Delay(50, cancellation); continue;
                }
                pending = request.Id;
                try
                {
                    if (request.Opcode is PcscAbi.Reset or PcscAbi.PowerOff)
                    {
                        log?.Invoke(request.Opcode == PcscAbi.Reset ? "pcsc_reset_requested" : "pcsc_power_off_requested");
                        byte[] control = await ExchangeAsync(card, request.Opcode == PcscAbi.Reset ? [2] : [0], cancellation);
                        try
                        {
                            if (request.Opcode == PcscAbi.Reset)
                            {
                                PcscAbi.ValidateAtr(control);
                                if (control.Length > request.OutputCapacity) device.Complete(pending, PcscAbi.BufferTooSmall, []);
                                else device.Complete(pending, 0, control);
                            }
                            else
                            {
                                if (!control.AsSpan().SequenceEqual(new byte[] { 1 })) throw new InvalidDataException("Power-off rejected.");
                                device.Complete(pending, 0, []);
                            }
                            pending = 0;
                            lastTraffic = Environment.TickCount64;
                            log?.Invoke("pcsc_control_completed");
                        }
                        finally { CryptographicOperations.ZeroMemory(control); }
                        continue;
                    }
                    if (request.Opcode != PcscAbi.Transmit || request.Payload.Length < 4)
                    {
                        device.Complete(pending, PcscAbi.NotSupported, []); pending = 0;
                        continue;
                    }
                    log?.Invoke($"pcsc_apdu_requested requestId={pending} header={Convert.ToHexString(request.Payload.AsSpan(0, 4))}");
                    byte[] response = await ExchangeAsync(card, request.Payload, cancellation);
                    lastTraffic = Environment.TickCount64;
                    try
                    {
                        if (response.Length < 2) throw new InvalidDataException("Missing card status.");
                        log?.Invoke($"pcsc_apdu_returned header={Convert.ToHexString(request.Payload.AsSpan(0, 4))} status={Convert.ToHexString(response.AsSpan(response.Length - 2))}");
                        if (response.Length > request.OutputCapacity)
                            device.Complete(pending, PcscAbi.BufferTooSmall, []);
                        else device.Complete(pending, 0, response);
                        pending = 0;
                    }
                    finally { CryptographicOperations.ZeroMemory(response); }
                }
                finally { CryptographicOperations.ZeroMemory(request.Payload); }
            }
        }
        finally
        {
            try { if (pending != 0) device.Complete(pending, PcscAbi.DeviceDisconnected, []); }
            finally { device.Detach(); }
            log?.Invoke("pcsc_card_removed");
        }
    }

    private static async Task<byte[]> ExchangeAsync(Stream stream, byte[] command, CancellationToken cancellation)
    {
        if (command.Length is < 1 or > PcscAbi.MaxPayload) throw new InvalidDataException("Invalid card frame.");
        using var deadline = CancellationTokenSource.CreateLinkedTokenSource(cancellation);
        deadline.CancelAfter(TimeSpan.FromSeconds(30));
        byte[] header = [(byte)(command.Length >> 8), (byte)command.Length];
        await stream.WriteAsync(header, deadline.Token);
        await stream.WriteAsync(command, deadline.Token);
        await stream.FlushAsync(deadline.Token);
        await stream.ReadExactlyAsync(header, deadline.Token);
        int size = (header[0] << 8) | header[1];
        if (size == 0) throw new InvalidDataException("Empty card response.");
        var result = new byte[size];
        try { await stream.ReadExactlyAsync(result, deadline.Token); return result; }
        catch { CryptographicOperations.ZeroMemory(result); throw; }
    }
}
