#!/usr/bin/env python3
"""Compare production reveal layouts with the approved WebGL reference.

Run build.py first. Only the small reflection harness is compiled here. The
production classes are loaded from build/classes, never a reimplementation of
the production formulas. Requires Java 17+, Node, and the local approved art
reference (not shipped in the game jar). Temporary output stays under build/.
"""
from pathlib import Path
import argparse
import json
import os
import subprocess

PROJECT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference", type=Path,
                        default=PROJECT / "art/revision84-school-preview")
    parser.add_argument("--output", type=Path,
                        default=PROJECT / "testing/reveal-integration-20261020/layout.json")
    args = parser.parse_args()
    reference = args.reference.resolve()
    output = args.output.resolve()
    build = PROJECT / "build/reveal-layout-verification"
    build.mkdir(parents=True, exist_ok=True)
    production = PROJECT / "build/classes"
    required = production / "dev/expedition/arcane/client/ChamberScrollRevealRenderer$Layout.class"
    if not required.is_file():
        raise SystemExit("Production renderer classes missing; run python3 build.py first")
    dependency_cp = (PROJECT / "build/compile-classpath.txt").read_text().strip()
    cp = os.pathsep.join((str(production), dependency_cp))
    subprocess.run(["javac", "--release", "17", "-proc:none", "-cp", cp, "-d", str(build),
                    str(PROJECT / "tools/VerifyScrollRevealLayout.java")], check=True, cwd=PROJECT)
    layout = build / "production-layouts.json"
    subprocess.run(["java", "-cp", os.pathsep.join((str(build), cp)),
                    "VerifyScrollRevealLayout", str(reference / "native-assets.json"), str(layout)],
                   check=True, cwd=PROJECT)
    check = subprocess.run(["node", str(PROJECT / "tools/compare-scroll-reveal-layout.cjs"),
                            str(reference), str(layout)],
                           check=True, capture_output=True, text=True, cwd=PROJECT)
    result = json.loads(check.stdout)
    result["implementation"] = "reflection on built production ChamberScrollRevealRenderer.Layout"
    result["reference"] = str(reference.relative_to(PROJECT)) if reference.is_relative_to(PROJECT) else str(reference)
    result["limits"] = "Math/layout only; native GPU appearance and texture reload require client checks."
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
