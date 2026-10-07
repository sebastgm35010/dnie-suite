namespace DniBridge.Control;
public static class ControlServer {
    public static async Task RunAsync(string owner,HostController controller,CancellationToken stop,string name=ControlPipe.Name,Action<string>? fault=null) {
        using var pipe=ControlPipe.Create(owner,name);
        while(!stop.IsCancellationRequested) {
            await pipe.WaitForConnectionAsync(stop);
            string stage="read";
            try {
                using var deadline=CancellationTokenSource.CreateLinkedTokenSource(stop);deadline.CancelAfter(TimeSpan.FromSeconds(35));
                var request=await ControlWire.ReadAsync<ControlRequest>(pipe,deadline.Token);
                stage="identity";
                if(ControlPipe.CallerSid(pipe)!=owner)throw new UnauthorizedAccessException("Caller is not the enrolled Windows user.");
                stage="command";
                await ControlWire.WriteAsync(pipe,await controller.HandleAsync(request),deadline.Token);
                // DisconnectNamedPipe discards unread bytes. Await a bounded receipt
                // instead of racing the desktop's response read across processes.
                stage="receipt";
                using var receipt=CancellationTokenSource.CreateLinkedTokenSource(stop);
                receipt.CancelAfter(TimeSpan.FromSeconds(3));
                byte[] ack=new byte[1];await pipe.ReadExactlyAsync(ack,receipt.Token);
                if(ack[0]!=1)throw new InvalidDataException("Invalid receipt.");
            }catch(Exception error) when(!stop.IsCancellationRequested) {
                fault?.Invoke(stage+":"+error.GetType().Name+(error is FileNotFoundException missing?":"+missing.FileName:""));
                // Invalid clients cannot terminate the privileged broker; no raw payload in logs.
            }finally {if(pipe.IsConnected)pipe.Disconnect();}
        }
    }
}
