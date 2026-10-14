using System.Net;
using System.Security.Cryptography;
using DniBridge;
internal static class NetworkDiscoveryTests {
    public static int Run() {
        int count=0;void Check(bool ok){if(!ok)throw new Exception("Discovery invariant failed");count++;}
        byte[] token=Enumerable.Range(0,32).Select(i=>(byte)i).ToArray();
        byte[] pin=Enumerable.Range(0,32).Select(i=>(byte)(255-i)).ToArray();
        byte[] nonce=Enumerable.Range(32,32).Select(i=>(byte)i).ToArray();
        var query=NetworkDiscovery.Query(1,pin,nonce,token);
        Check(Convert.ToHexString(query)=="444E4946494E443101FFFEFDFCFBFAF9F8F7F6F5F4F3F2F1F0EFEEEDECEBEAE9E8E7E6E5E4E3E2E1E0202122232425262728292A2B2C2D2E2F303132333435363738393A3B3C3D3E3F48758F7FC802107C165D4CE5D179BEABCC8CBAEEDFDF716D3A5D4338ECA01745");
        var reply=Convert.FromHexString("444E49464E44523101FFFEFDFCFBFAF9F8F7F6F5F4F3F2F1F0EFEEEDECEBEAE9E8E7E6E5E4E3E2E1E0202122232425262728292A2B2C2D2E2F303132333435363738393A3B3C3D3E3F02C0A8010B64444655F266F1BA2F8FDC24BE9E251CD8433E3194E50229108C7D6F25E26A28586A22FD");
        Check(NetworkDiscovery.ReadReply(reply,query,token)!.Select(a=>a.ToString()).SequenceEqual(new[]{"192.168.1.11","100.68.70.85"}));
        for(int i=0;i<reply.Length;i++){var bad=reply.ToArray();bad[i]^=1;Check(NetworkDiscovery.ReadReply(bad,query,token)==null);}
        Check(NetworkDiscovery.ReadReply(reply,NetworkDiscovery.Query(1,pin,RandomNumberGenerator.GetBytes(32),token),token)==null);
        Check(NetworkDiscovery.ReadReply(reply,NetworkDiscovery.Query(2,pin,nonce,token),token)==null);
        Check(NetworkDiscovery.ReadReply(reply.Concat(new byte[]{0}).ToArray(),query,token)==null);
        Check(NetworkDiscovery.ReadReply(reply,query,new byte[32])==null);
        var peers=TailnetPeers.Parse(System.Text.Encoding.UTF8.GetBytes("{\"Peer\":{\"one\":{\"Online\":true,\"TailscaleIPs\":[\"100.68.70.85\",\"8.8.8.8\"]},\"two\":{\"Online\":false,\"TailscaleIPs\":[\"100.68.70.86\"]}}}"));
        Check(peers.Length==1 && peers[0].ToString()=="100.68.70.85");
        return count;
    }
}
