using System.Net;
using System.Net.Sockets;

namespace DniBridge;

public static class TailnetTransport
{
    public static bool IsAddress(IPAddress address)
    {
        var bytes = address.GetAddressBytes();
        return bytes.Length == 4 && bytes[0] == 100 && bytes[1] is >= 64 and <= 127;
    }
    public static bool IsHost(string host) =>
        System.Text.RegularExpressions.Regex.IsMatch(host, @"^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.[a-z0-9-]+\.ts\.net$") ||
        (IPAddress.TryParse(host, out var ip) && IsAddress(ip));
    public static IPAddress Resolve(string host)
    {
        if (!IsHost(host)) throw new ArgumentException("Expected Tailscale host or address.");
        return Dns.GetHostAddresses(host).FirstOrDefault(IsAddress)
            ?? throw new InvalidDataException("No IPv4 Tailscale address.");
    }
    public static void VerifyPeer(TcpClient client, string peerHost)
    {
        if (client.Client.LocalEndPoint is not IPEndPoint local || !IsAddress(local.Address) ||
            client.Client.RemoteEndPoint is not IPEndPoint remote || !IsAddress(remote.Address) ||
            !remote.Address.Equals(Resolve(peerHost)))
            throw new InvalidDataException("Unexpected Tailscale peer.");
    }
}
