"""Cross device clocks using the latest authorization anchor; persist only diagnostic metadata."""
import argparse, datetime as dt, json, pathlib, re

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--android',required=True)
    parser.add_argument('--host',required=True)
    parser.add_argument('--middleware',required=True)
    parser.add_argument('--java-log',required=True)
    parser.add_argument('--output',required=True)
    args=parser.parse_args()
    android=[json.loads(line) for line in pathlib.Path(args.android).read_text(encoding='utf-8-sig').splitlines() if line.strip()]
    host=[]
    for line in pathlib.Path(args.host).read_text(encoding='utf-8-sig').splitlines():
        match=re.match(r'(\S+) bridge_phase=(\w+)(.*)',line)
        if match:
            stamp=int(dt.datetime.fromisoformat(match[1]).timestamp()*1000)
            fields=dict(re.findall(r'(header|status|type)=([A-Za-z0-9_]+)',match[3]))
            host.append(dict(time=stamp,category='windows_bridge',event=match[2],fields=fields))
    phone_anchor=[row for row in android if row['category']=='bridge' and row['event']=='peer_authorized'][-1]
    host_anchor=[row for row in host if row['event']=='peer_authorized'][-1]
    offset=host_anchor['time']-phone_anchor['time']
    rows=list(host)
    allowed={'header','status','state','durationMs','activity','version'}
    for row in android:
        if row['time']<phone_anchor['time']-1500 or row['category'] not in ('bridge','apdu','lifecycle'):continue
        rows.append(dict(time=row['time']+offset,originalTime=row['time'],category='android_'+row['category'],event=row['event'],fields={k:v for k,v in row['fields'].items() if k in allowed}))
    # Official .NET logger stamps local Bogota time; retain only fixed known phase names.
    for line in pathlib.Path(args.middleware).read_text(encoding='utf-8',errors='replace').splitlines():
        match=re.match(r'(\d{4}-\d\d-\d\d \d\d:\d\d:\d\d,\d+)',line)
        if not match:continue
        local=dt.datetime.strptime(match[1],'%Y-%m-%d %H:%M:%S,%f').replace(tzinfo=dt.timezone(dt.timedelta(hours=-5)))
        stamp=int(local.timestamp()*1000)
        if not host_anchor['time']<=stamp<=max(row['time'] for row in host)+30000:continue
        event=None
        if 'La tarjeta es un DNIe v3' in line:event='v3_recognized'
        elif 'java.exe' in line:event='java_started'
        elif 'Al parecer' in line:event='dni_connection_error'
        if event:rows.append(dict(time=stamp,category='official_dotnet',event=event,fields={}))
    java=[]
    for line in pathlib.Path(args.java_log).read_text(encoding='utf-8',errors='replace').splitlines():
        match=re.search(r'Resultado de verificaci.{0,25}v([23]) es ([0-9A-Fa-f]{4})',line)
        if match:java.append(dict(version=int(match[1]),status=match[2]))
    output=pathlib.Path(args.output);output.mkdir(parents=True,exist_ok=True)
    with (output/'timeline.jsonl').open('w',encoding='utf-8') as stream:
        for row in sorted(rows,key=lambda r:r['time']):stream.write(json.dumps(row,ensure_ascii=False)+'\n')
    summary=dict(phoneToHostEstimatedOffsetMs=offset,anchor='peer_authorized',offsetIncludesNetworkLatency=True,
        latestJavaVersionProbes=java[-2:],middlewareTimezoneAssumption='America/Bogota UTC-05',events=len(rows),
        browserHistoricalCaptureAvailable=False)
    (output/'summary.json').write_text(json.dumps(summary,indent=2),encoding='utf-8')
    print(json.dumps(summary))

if __name__=='__main__':main()
