using DniBridge;
static class UsbTransportTests {
    public static async Task<int> RunAsync() {
        byte[] config={9,2,55,0,2,1,0,128,250, 9,4,0,0,2,255,255,0,0, 7,5,0x81,2,0,2,0, 7,5,2,2,0,2,0,
            9,4,1,0,2,255,66,1,0, 7,5,0x83,2,0,2,0, 7,5,4,2,0,2,0};
        var endpoints=UsbAccessoryDescriptors.Endpoints(config);
        if(endpoints.Input!=0x81 || endpoints.Output!=2 || endpoints.PacketSize!=512)throw new Exception("Accessory must exclude the ADB interface.");
        int count=1;
        foreach(byte[] invalid in new[]{new byte[]{0,2},new byte[]{9,4,0},config[32..]}) {
            try {UsbAccessoryDescriptors.Endpoints(invalid);throw new Exception("Invalid/ADB-only configuration accepted.");}
            catch(InvalidDataException){count++;}
        }
        var identity=UsbAccessoryIdentity.Strings();
        if(identity.Length!=6 || identity[0]!="DNIeBridge" || identity[1]!="NFC PC Bridge" || identity[4]!="")throw new Exception("AOA identity must match Android registration and omit external URI.");
        count++;
        int polls=0;
        int found=await UsbAccessoryTransition.WaitAsync<int>(()=>++polls==4?42:null,TimeSpan.FromSeconds(1),CancellationToken.None,TimeSpan.FromMilliseconds(5));
        if(found!=42 || polls!=4)throw new Exception("Delayed accessory re-enumeration was not awaited.");
        count++;
        using(var canceled=new CancellationTokenSource()) {
            canceled.Cancel();
            try {await UsbAccessoryTransition.WaitAsync<int>(()=>null,TimeSpan.FromSeconds(1),canceled.Token);throw new Exception("Canceled discovery continued.");}
            catch(OperationCanceledException){count++;}
        }
        try {await UsbAccessoryTransition.WaitAsync<int>(()=>null,TimeSpan.FromMilliseconds(20),CancellationToken.None,TimeSpan.FromMilliseconds(5));throw new Exception("Accessory discovery is unbounded.");}
        catch(IOException){count++;}
        return count;
    }
}
