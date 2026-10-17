#!/usr/bin/env python3
"""Apply the tracked payment/coin UI fixes to the Android project extracted from the source ZIP."""
from pathlib import Path
import sys

project = Path(sys.argv[1])
java_root = project / "app" / "src" / "main" / "java"
if not java_root.exists():
    raise SystemExit(f"Android Java source directory not found: {java_root}")

def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match in {path.name}, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")
    print(f"PATCHED: {label} ({path.name})")

home_matches = list(java_root.rglob("HomeActivity.java"))
topup_matches = list(java_root.rglob("TopUpActivity.java"))
if len(home_matches) != 1 or len(topup_matches) != 1:
    raise SystemExit("Could not uniquely locate HomeActivity.java and TopUpActivity.java")

replace_once(
    home_matches[0],
    'Long coins=d.getLong("coinBalance");',
    'Long coins=d.getLong("coins");if(coins==null)coins=d.getLong("coinBalance");',
    "Home balance reads backend coins field with legacy fallback",
)
replace_once(
    topup_matches[0],
    'Long c=d.getLong("coinBalance");',
    'Long c=d.getLong("coins");if(c==null)c=d.getLong("coinBalance");',
    "Top-up balance reads backend coins field with legacy fallback",
)
replace_once(
    topup_matches[0],
    'TextView note=Ui.body(this,"Test payment: ₹15 → 300 Coins\\nCashfree Sandbox"); note.setTextColor(Color.rgb(255,220,120)); r.addView(note);',
    "",
    "Remove temporary ₹15 test-payment notice",
)
replace_once(
    topup_matches[0],
    'addPack(r,"₹15  •  300 Coins  •  TEST", "test_15", true);',
    "",
    "Remove temporary ₹15 test coin pack",
)
print("Android payment UI patch completed.")
