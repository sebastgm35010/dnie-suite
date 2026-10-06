param([Parameter(Mandatory)][string]$Directory,[int]$Seconds=240)
# UI metadata only: never reads edit values, keystrokes, PIN/CAN or arbitrary label text.
Add-Type -AssemblyName UIAutomationClient
Add-Type -AssemblyName UIAutomationTypes
Add-Type @'
using System;
using System.Text;
using System.Runtime.InteropServices;
public static class IdplugStaticLabels {
 public delegate bool Callback(IntPtr hwnd,IntPtr data);
 [DllImport("user32.dll")] static extern bool EnumChildWindows(IntPtr hwnd,Callback callback,IntPtr data);
 [DllImport("user32.dll",CharSet=CharSet.Unicode)] static extern int GetClassName(IntPtr hwnd,StringBuilder text,int count);
 [DllImport("user32.dll",CharSet=CharSet.Unicode)] static extern IntPtr SendMessageTimeout(IntPtr hwnd,uint msg,IntPtr wp,StringBuilder text,uint flags,uint timeout,out IntPtr result);
 public static bool[] Classify(IntPtr parent) {
  bool can=false,pin=false;
  EnumChildWindows(parent,(hwnd,data)=> {
   var cls=new StringBuilder(128);GetClassName(hwnd,cls,128);
   if(cls.ToString()=="Static" || cls.ToString()=="Button") {
    var text=new StringBuilder(512);IntPtr result;
    SendMessageTimeout(hwnd,13,(IntPtr)512,text,2,100,out result);
    can|=System.Text.RegularExpressions.Regex.IsMatch(text.ToString(),@"\bCAN\b",System.Text.RegularExpressions.RegexOptions.IgnoreCase);
    pin|=System.Text.RegularExpressions.Regex.IsMatch(text.ToString(),@"\bPIN\b",System.Text.RegularExpressions.RegexOptions.IgnoreCase);
   }
   return true;
  },IntPtr.Zero);
  return new[]{can,pin};
 }
}
'@
$until=[DateTime]::UtcNow.AddSeconds($Seconds)
$seen=@{}
while([DateTime]::UtcNow -lt $until -and -not(Test-Path -LiteralPath "$Directory\stop.txt")) {
 try {
  $windows=[System.Windows.Automation.AutomationElement]::RootElement.FindAll([System.Windows.Automation.TreeScope]::Children,[System.Windows.Automation.Condition]::TrueCondition)
  foreach($window in $windows) {
   $windowPid=$window.Current.ProcessId
   $process=Get-Process -Id $windowPid -ErrorAction SilentlyContinue
   if($process.ProcessName -notmatch 'idplug|credential|pinui'){continue}
   $nodes=$window.FindAll([System.Windows.Automation.TreeScope]::Descendants,[System.Windows.Automation.Condition]::TrueCondition)
   $can=$false;$pin=$false;$edits=0;$login=$false
   $native=[IdplugStaticLabels]::Classify([IntPtr]$window.Current.NativeWindowHandle)
   $can=$native[0];$pin=$native[1]
   foreach($node in $nodes) {
    if($node.Current.ControlType -eq [System.Windows.Automation.ControlType]::Edit){$edits++}
    # Only classify static labels/buttons; edit controls can expose input in Name.
    if($node.Current.ControlType -in @([System.Windows.Automation.ControlType]::Text,[System.Windows.Automation.ControlType]::Button)) {
     $label=$node.Current.Name
     if($label -match '\bCAN\b'){$can=$true}
     if($label -match '\bPIN\b'){$pin=$true}
     if($label -match 'login|iniciar sesi'){$login=$true}
    }
   }
   $key="$windowPid|$($window.Current.NativeWindowHandle)|$can|$pin|$edits|$login"
   if(-not $seen.ContainsKey($key)) {
    $seen[$key]=$true
    @{time=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds();event='ui_shape';pid=$windowPid;process=$process.ProcessName;canLabel=$can;pinLabel=$pin;editControls=$edits;loginButton=$login} | ConvertTo-Json -Compress | Add-Content -LiteralPath "$Directory\idplug-windows.jsonl"
   }
  }
 } catch { }
 Start-Sleep -Milliseconds 300
}
