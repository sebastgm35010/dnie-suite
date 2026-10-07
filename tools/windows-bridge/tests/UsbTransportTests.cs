using DniBridge;
static class UsbTransportTests {
    public static int Run() {
        byte[] config={9,2,55,0,2,1,0,128,250, 9,4,0,0,2,255,255,0,0, 7,5,0x81,2,0,2,0, 7,5,2,2,0,2,0,
            9,4,1,0,2,255,66,1,0, 7,5,0x83,2,0,2,0, 7,5,4,2,0,2,0};
        var endpoints=UsbAccessoryDescriptors.Endpoints(config);
        if(endpoints.Input!=0x81 || endpoints.Output!=2 || endpoints.PacketSize!=512)throw new Exception("Accessory must exclude the ADB interface.");
        int count=1;
        foreach(byte[] invalid in new[]{new byte[]{0,2},new byte[]{9,4,0},config[32..]}) {
            try {UsbAccessoryDescriptors.Endpoints(invalid);throw new Exception("Invalid/ADB-only configuration accepted.");}
            catch(InvalidDataException){count++;}
        }
        return count;
    }
}
