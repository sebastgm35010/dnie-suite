"""Local independent QR decoding check; never print a pairing credential."""
import base64
import json
import re
import sys
from pathlib import Path

import cv2
import numpy as np

page = Path(sys.argv[1])
html = page.read_text(encoding="utf-8-sig")
png = base64.b64decode(re.search(r'data:image/png;base64,([^"\s]+)', html)[1])
image = cv2.imdecode(np.frombuffer(png, dtype=np.uint8), cv2.IMREAD_GRAYSCALE)
payload, _, _ = cv2.QRCodeDetector().detectAndDecode(image)
assert payload.startswith("DNIEBRIDGE1:"), "QR did not decode as a bridge pairing"
encoded = payload.split(":", 1)[1]
provision = json.loads(base64.urlsafe_b64decode(encoded + "=" * ((4 - len(encoded) % 4) % 4)))
config = json.loads((page.parent / "server-private.json").read_text(encoding="utf-8-sig"))
assert provision["pairingTokenBase64"] == config["pairingTokenBase64"]
assert provision["expiresAt"] == config["pairingExpiresAt"]
assert provision["host"] == config["host"]
assert provision["port"] == config["port"]
assert "https://" not in html and "http://" not in html
print("PASS QR: independent PNG decoding; endpoint, token and expiry match; no remote assets.")
