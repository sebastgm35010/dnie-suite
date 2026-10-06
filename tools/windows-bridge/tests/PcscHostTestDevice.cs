using DniBridge;

sealed class PcscHostTestDevice : IPcscDevice
{
    public volatile bool Attached, Completed, Detached, Disposed;
    private bool requested;
    public void Attach(byte[] atr, uint protocol) { PcscAbi.ValidateAtr(atr); Attached = true; }
    public PcscRequest? Poll() { if (requested) return null; requested = true; return new(1, PcscAbi.Reset, 33, []); }
    public void Complete(ulong id, uint status, byte[] payload) {
        if (id != 1 || status != 0) throw new Exception("Reset completion failed");
        PcscAbi.ValidateAtr(payload); Completed = true;
    }
    public void Detach() => Detached = true;
    public void Dispose() => Disposed = true;
}
