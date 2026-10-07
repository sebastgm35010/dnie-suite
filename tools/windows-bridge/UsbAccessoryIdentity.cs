namespace DniBridge;

/// <summary>Discovery strings; authentication remains TLS and the paired token.</summary>
public static class UsbAccessoryIdentity
{
    public const string Manufacturer = "DNIeBridge";
    public const string Model = "NFC PC Bridge";
    public const string Serial = "DNIeBridge-PC";

    public static string[] Strings() =>
        [Manufacturer, Model, "Direct USB secure card bridge", "2.0", "", Serial];
}

/// <summary>Wait through device re-enumeration without repeating START_ACCESSORY.</summary>
public static class UsbAccessoryTransition
{
    public static async Task<T> WaitAsync<T>(Func<T?> discover, TimeSpan timeout,
        CancellationToken stop, TimeSpan? pollInterval = null) where T : struct
    {
        using var deadline = CancellationTokenSource.CreateLinkedTokenSource(stop);
        deadline.CancelAfter(timeout);
        try
        {
            while (true)
            {
                deadline.Token.ThrowIfCancellationRequested();
                if (discover() is { } accessory) return accessory;
                await Task.Delay(pollInterval ?? TimeSpan.FromMilliseconds(250), deadline.Token);
            }
        }
        catch (OperationCanceledException) when (!stop.IsCancellationRequested)
        {
            throw new IOException("El telefono no reaparecio en modo accesorio USB. Reconecta el cable y comprueba la app Android.");
        }
    }
}
