using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;

namespace DniBridge;

/// <summary>LAN and USB networking bind one assigned private IPv4 address, always with TLS.</summary>
public static class LocalNetworkPolicy
{
    public static bool IsPrivateAddress(IPAddress address)
    {
        if (address.AddressFamily != AddressFamily.InterNetwork) return false;
        var b = address.GetAddressBytes();
        return b[0] == 127 || b[0] == 10 || (b[0] == 172 && b[1] >= 16 && b[1] <= 31)
            || (b[0] == 192 && b[1] == 168);
    }

    public static bool IsBindingAllowed(IPAddress address, IEnumerable<IPAddress> assigned) =>
        IsPrivateAddress(address) && (IPAddress.IsLoopback(address) || assigned.Contains(address));

    public static void VerifyBinding(IPAddress address)
    {
        var assigned = NetworkInterface.GetAllNetworkInterfaces()
            .Where(n => n.OperationalStatus == OperationalStatus.Up)
            .SelectMany(n => n.GetIPProperties().UnicastAddresses).Select(a => a.Address);
        if (!IsBindingAllowed(address, assigned)) throw new InvalidDataException("Choose an assigned private LAN or USB IPv4 address.");
    }
}
