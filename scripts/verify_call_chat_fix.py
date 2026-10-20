"""Static contract checks for the V50 build's Android call/chat patching workflow."""
from pathlib import Path
import re
import sys

workflow = Path(".github/workflows/main.yml").read_text()
start = workflow.index("      - name: Apply V49")
stop = workflow.index("      - name: Upload source diagnostics", start)
section = workflow[start:stop]

checks = {
    "call payload includes both participants": 'm.put("participants",Arrays.asList(uid,otherUid))' in section,
    "in-call chat room initializes before listener": "ensureChatThenListen" in section,
    "ICE write failure is shown": "ICE signaling failed" in section,
    "call chat listener error is shown": "Call chat unavailable" in section,
    "V50 version guard is present": 'Expected V49 version markers not found' in section,
    "no duplicate V50 bump block": section.count("g = g.replace('versionCode 49', 'versionCode 50', 1)") == 1,
}
for label, ok in checks.items():
    print(("PASS" if ok else "FAIL") + ": " + label)
if not all(checks.values()):
    sys.exit(1)
