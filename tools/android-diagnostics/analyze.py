"""Summarize captured evidence by declared layer; overlapping spans are not additive CPU time."""
import argparse
from collections import Counter, defaultdict
import json
from pathlib import Path
import statistics


def analyze(path, since_minutes=10, after_id=0, process=None):
    rows = []
    with path.open(encoding="utf-8") as source:
        for line in source:
            row = json.loads(line)
            if "id" in row:
                rows.append(row)
    if not rows:
        return {"events": 0}
    if max(row["id"] for row in rows) < after_id:
        return {"events": 0, "cursorBeyondHistory": True, "reason": "History reset or different capture; choose a new cursor"}
    end = max(row["time"] for row in rows)
    rows = [row for row in rows if row["id"] > after_id and
        (not since_minutes or row["time"] >= end - since_minutes * 60000) and
        (not process or row["fields"].get("process") == process)]
    if not rows:
        return {"events": 0}
    durations = defaultdict(list)
    signals = Counter()
    processes = set()
    for row in rows:
        f = row["fields"]
        if f.get("process"):
            processes.add(f["process"])
        layer = f.get("layer", "unclassified")
        if row["event"] in ("failure", "main_thread_stall", "main_thread_io", "queue_overflow", "slow_frame", "uncaught"):
            signals[(layer, row["event"])] += 1
        if row["event"] in ("operation_finished", "main_thread_recovered", "rx", "slow_frame") and "durationMs" in f:
            key = (layer, row["category"], f.get("operation", f.get("stage", row["event"])))
            durations[key].append(float(f["durationMs"]))
    return {"events": len(rows), "windowStart": min(r["time"] for r in rows), "windowEnd": end,
        "processes": len(processes), "signals": [{"layer": k[0], "event": k[1], "count": v} for k, v in sorted(signals.items())],
        "durations": [{"layer": k[0], "category": k[1], "operation": k[2], "samples": len(v),
            "medianMs": round(statistics.median(v), 3), "maxMs": round(max(v), 3)} for k, v in sorted(durations.items())],
        "limits": "Wall-clock spans overlap and include IO/waiting. Frame or IO signals alone do not prove engine causality. Historical events without a layer stay unclassified."}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("--since-minutes", type=float, default=10)
    parser.add_argument("--after-id", type=int, default=0)
    parser.add_argument("--process")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = json.dumps(analyze(args.input, args.since_minutes, args.after_id, args.process), ensure_ascii=False, indent=2)
    if args.output:
        args.output.write_text(result + "\n", encoding="utf-8")
    else:
        print(result)
