using System.ComponentModel;
using System.IO.Pipes;
using System.Runtime.InteropServices;
using System.Security.AccessControl;
using System.Security.Principal;
using Microsoft.Win32.SafeHandles;
using DniBridge;

internal static class PrivateCardPipeTests
{
    public static async Task<int> RunAsync()
    {
        if (!OperatingSystem.IsWindows()) throw new PlatformNotSupportedException();
        int passed = 0;
        string name = "DNIeBridge-" + Guid.NewGuid().ToString("N");
        using var server = PrivateCardPipe.Create(name);
        try { using var collision = PrivateCardPipe.Create(name); throw new Exception("Pipe collision allowed."); }
        catch (Win32Exception) { passed++; }
        try { using var bad = PrivateCardPipe.Create("arbitrary-pipe"); throw new Exception("Invalid name allowed."); }
        catch (ArgumentException) { passed++; }

        GetKernelObjectSecurity(server.SafePipeHandle, 4, null, 0, out uint needed);
        var descriptorBytes = new byte[needed];
        if (!GetKernelObjectSecurity(server.SafePipeHandle, 4, descriptorBytes, needed, out _)) throw new Win32Exception();
        var descriptor = new RawSecurityDescriptor(descriptorBytes, 0);
        if (descriptor.DiscretionaryAcl is not { Count: 1 } acl || acl[0] is not CommonAce ace ||
            ace.AceQualifier != AceQualifier.AccessAllowed ||
            !ace.SecurityIdentifier.Equals(WindowsIdentity.GetCurrent().User!)) throw new Exception("Unexpected pipe ACL.");
        passed++;

        using var deadline = new CancellationTokenSource(TimeSpan.FromSeconds(5));
        var accepted = server.WaitForConnectionAsync(deadline.Token);
        using var client = new NamedPipeClientStream(".", name, PipeDirection.InOut, PipeOptions.Asynchronous);
        await client.ConnectAsync(deadline.Token); await accepted;
        await client.WriteAsync(new byte[] { 1, 2, 3 }, deadline.Token);
        var received = new byte[3]; await server.ReadExactlyAsync(received, deadline.Token);
        if (!received.SequenceEqual(new byte[] { 1, 2, 3 })) throw new Exception("Private pipe round trip failed.");
        passed++;

        using var waiting = PrivateCardPipe.Create("DNIeBridge-" + Guid.NewGuid().ToString("N"));
        using var cancelled = new CancellationTokenSource();
        var wait = waiting.WaitForConnectionAsync(cancelled.Token); cancelled.Cancel();
        try { await wait; throw new Exception("Cancellation did not stop private pipe."); }
        catch (OperationCanceledException) { passed++; }
        return passed;
    }

    [DllImport("advapi32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool GetKernelObjectSecurity(SafePipeHandle handle, uint information,
        byte[]? descriptor, uint length, out uint needed);
}
