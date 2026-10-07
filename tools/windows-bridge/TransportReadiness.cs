namespace DniBridge;
/// <summary>USB/reverse-LAN transport gate before bounded TLS authentication.</summary>
public static class TransportReadiness {
    public static async Task WaitAsync(Stream stream,CancellationToken stop) {
        var first=new byte[1];await stream.ReadExactlyAsync(first,stop);
        using var deadline=CancellationTokenSource.CreateLinkedTokenSource(stop);deadline.CancelAfter(TimeSpan.FromSeconds(15));
        var rest=new byte[8];await stream.ReadExactlyAsync(rest,deadline.Token);
        if(first[0]!='D' || !rest.AsSpan().SequenceEqual("NIREADY1"u8)) throw new InvalidDataException("Invalid transport readiness.");
    }
}
