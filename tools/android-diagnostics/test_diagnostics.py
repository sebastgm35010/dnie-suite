import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from collect import validate, collect
from analyze import analyze


class DiagnosticTransportTest(unittest.TestCase):
    def fixture(self, directory, rows):
        file = Path(directory) / "sample.jsonl"
        file.write_text("".join(json.dumps(row) + "\n" for row in rows), encoding="utf-8")
        return file

    def test_empty_complete_export(self):
        with tempfile.TemporaryDirectory() as d:
            self.assertEqual((0, 0), validate(self.fixture(d, [{"type": "export_complete", "schema": 2, "until": 0}])))

    def test_truncation_and_bad_watermark_rejected(self):
        event = {"id": 2, "fields": {}}
        with tempfile.TemporaryDirectory() as d:
            for rows in [[event], [event, {"type": "export_complete", "schema": 2, "until": 3}], [event, event]]:
                with self.assertRaises(ValueError):
                    validate(self.fixture(d, rows))

    def test_layers_and_window_do_not_mix_old_trials(self):
        with tempfile.TemporaryDirectory() as d:
            rows = [{"id": i + 1, "time": t, "category": "performance", "event": "operation_finished",
                "fields": {"layer": layer, "durationMs": value, "operation": "op", "process": "synthetic"}}
                for i, (t, layer, value) in enumerate([(0, "engine", 999), (700000, "ui", 5), (701000, "engine", 3)])]
            report = analyze(self.fixture(d, rows), 1)
            self.assertEqual(2, report["events"])
            self.assertEqual(2, len(report["durations"]))
            self.assertEqual(5, max(r["maxMs"] for r in report["durations"]))
            self.assertEqual(1, analyze(self.fixture(d, rows), 0, after_id=2)["events"])
            self.assertTrue(analyze(self.fixture(d, rows), 0, after_id=4)["cursorBeyondHistory"])
            self.assertEqual(0, analyze(self.fixture(d, rows), 0, process="other")["events"])

    def test_failed_transport_preserves_previous_capture_and_removes_partial(self):
        with tempfile.TemporaryDirectory() as d:
            destination = Path(d) / "kept.jsonl"
            destination.write_text("previous capture", encoding="utf-8")
            def incomplete(*args, **kwargs):
                kwargs["stdout"].write(b'{"id":1,"fields":{}}\n')
                return type("Result", (), {"returncode": 0})()
            with patch("collect.subprocess.run", side_effect=incomplete):
                with self.assertRaises(ValueError):
                    collect(Path("adb"), "fixture.debug", destination)
            self.assertEqual("previous capture", destination.read_text(encoding="utf-8"))
            self.assertFalse(list(Path(d).glob("*.part")))
