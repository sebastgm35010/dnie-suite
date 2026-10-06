namespace DniBridge;

/// <summary>Cleanup diagnostics preserve the original failure and never include exception messages.</summary>
internal static class PcscShutdown
{
    internal static void LogFailure(Action<string>? log, string phase, Exception failure)
    {
        string code = failure is System.ComponentModel.Win32Exception native ? $" code={native.NativeErrorCode}" : "";
        try { log?.Invoke($"{phase} type={failure.GetType().Name}{code}"); }
        catch { /* An observer must not replace the failure being diagnosed. */ }
    }
}
