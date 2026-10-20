#!/usr/bin/env python3
"""Launch only the isolated offline Arcane Industry client, or stop its own PID.

Reads one game_versions.data_json row through SQLite mode=ro. Never reads accounts.
"""
from __future__ import annotations

import argparse
from datetime import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import sqlite3
import subprocess
import uuid

from prepare_instance import INSTANCE, MANIFEST, ROOT, WORKSPACE

LAUNCHER_DB = Path.home() / "Library/Application Support/Swift Craft Launcher/data/data.db"
PROFILE = "机械远征-CMI-迁移测试"
PLAYER = "ArcanePrototype"
EVIDENCE = ROOT / "run"
PID_FILE = EVIDENCE / "client-pid.json"
CONSOLE = EVIDENCE / "client-console.log"


def dump(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def process_field(pid: int, field: str) -> str:
    result = subprocess.run(["ps", "-p", str(pid), "-o", field + "="],
                            text=True, capture_output=True, check=False)
    return result.stdout.strip() if result.returncode == 0 else ""


def owned_process(record: dict) -> bool:
    if record.get("instance") != str(INSTANCE) or not isinstance(record.get("pid"), int):
        return False
    signature = process_field(record["pid"], "lstart")
    command = process_field(record["pid"], "command")
    return bool(signature and signature == record.get("startSignature")
                and str(INSTANCE) in command and "--gameDir" in command
                and f"--username {PLAYER}" in command)


def stop(*, dry_run: bool = False) -> None:
    if not PID_FILE.exists():
        print("No prototype client PID has been recorded.")
        return
    record = json.loads(PID_FILE.read_text(encoding="utf-8"))
    if not owned_process(record):
        print("Recorded prototype PID is no longer running with the same identity; no signal sent.")
        return
    if not dry_run:
        os.kill(record["pid"], signal.SIGTERM)
    print(json.dumps({"action": "would_stop" if dry_run else "SIGTERM_sent",
                      "pid": record["pid"], "instance": str(INSTANCE)}, ensure_ascii=False))


def replace_flag(arguments: list[str], flag: str, value: str) -> None:
    while flag in arguments:
        index = arguments.index(flag)
        del arguments[index:index + 2]
    arguments.extend([flag, value])


def build_command(world: str | None, server: str | None = None, *, presentation_trace: bool = False) -> tuple[list[str], dict]:
    if world is not None and (not world or world in {".", ".."} or Path(world).name != world):
        raise ValueError("World must be a single save-directory name inside this prototype instance")
    with sqlite3.connect(LAUNCHER_DB.as_uri() + "?mode=ro", uri=True) as connection:
        connection.execute("PRAGMA query_only = ON")
        row = connection.execute("SELECT data_json FROM game_versions WHERE game_name = ?", (PROFILE,)).fetchone()
    if row is None:
        raise RuntimeError(f"Installed local Forge launch metadata is unavailable for {PROFILE}")
    metadata = json.loads(row[0])
    if metadata.get("gameVersion") != "1.20.1" or metadata.get("modVersion") != "47.4.10":
        raise RuntimeError("Expected installed Minecraft 1.20.1 / Forge 47.4.10 metadata")
    offline_uuid = uuid.UUID(bytes=hashlib.md5(("OfflinePlayer:" + PLAYER).encode()).digest(), version=3).hex
    replacements = {"xms": "512", "xmx": "6144", "game_directory": str(INSTANCE),
                    "auth_player_name": PLAYER, "auth_access_token": "0", "auth_uuid": offline_uuid,
                    "auth_xuid": "0"}

    def substitute(match: re.Match) -> str:
        key = match.group(1)
        if key not in replacements:
            raise RuntimeError(f"Unsupported installed launch placeholder: {key}")
        return replacements[key]

    arguments = [re.sub(r"\$\{([^}]+)\}", substitute, argument)
                 for argument in metadata["launchCommand"]]
    arguments = [argument for argument in arguments
                 if not argument.startswith(("-Xms", "-Xmx", "-XX:ErrorFile=", "-XX:HeapDumpPath="))]
    # Clear saved connections and Quick Play state before adding this instance's optional save.
    for flag in ("--quickPlaySingleplayer", "--quickPlayMultiplayer", "--quickPlayRealms",
                 "--quickPlayPath", "--server", "--port", "--width", "--height"):
        while flag in arguments:
            index = arguments.index(flag)
            del arguments[index:index + 2]
    for flag, value in {"--gameDir": str(INSTANCE), "--username": PLAYER,
                        "--uuid": offline_uuid, "--accessToken": "0", "--xuid": "0",
                        "--userType": "legacy", "--width": "1280", "--height": "800"}.items():
        replace_flag(arguments, flag, value)
    arguments[:0] = ["-Xms512M", "-Xmx6144M", f"-XX:ErrorFile={EVIDENCE}/hs_err_pid%p.log",
                     f"-XX:HeapDumpPath={EVIDENCE}", "-Darcane.prototype=true"]
    if presentation_trace:
        arguments.insert(0, "-Darcane.presentation.trace=true")
    save = INSTANCE / "saves" / world if world else None
    quick_play = bool(save and (save / "level.dat").is_file())
    if server:
        if server != "127.0.0.1:25576":
            raise ValueError("The test-server shortcut is restricted to this prototype's local server")
        arguments.extend(["--quickPlayMultiplayer", server])
        quick_play = False
    elif quick_play:
        arguments.extend(["--quickPlaySingleplayer", world])
    java = Path(metadata["javaPath"])
    if not java.is_file():
        raise RuntimeError(f"Installed Java runtime is missing: {java}")
    receipt = {"instance": str(INSTANCE), "minecraft": "1.20.1", "forge": "47.4.10",
               "java": str(java), "player": PLAYER, "offline": True, "maxHeapMiB": 6144,
               "window": [1280, 800], "renderDistance": 8, "requestedWorld": world,
               "quickPlayExistingWorld": quick_play, "destination": server or (world if quick_play else "title_screen"),
               "accountsRead": False, "launcherDatabaseMode": "read_only_data_json",
               "addonPrepared": MANIFEST.is_file() and
                   json.loads(MANIFEST.read_text(encoding="utf-8")).get("addonPresent", False),
               "console": str(CONSOLE)}
    return [str(java), *arguments], receipt


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dry-run", action="store_true", help="Print offline launch settings without starting Java")
    parser.add_argument("--world", default="Arcane-Prototype", help="Quick Play only if this prototype save already exists")
    parser.add_argument("--server", help="Connect only to the disposable local test server")
    parser.add_argument("--stop", action="store_true", help="SIGTERM only this tool's verified recorded Java PID")
    parser.add_argument("--wait", action="store_true", help="Wait for this client and record its exit code")
    parser.add_argument("--presentation-trace", action="store_true", help="Opt in to per-tick reveal diagnostics for acceptance")
    options = parser.parse_args()
    if options.stop:
        stop(dry_run=options.dry_run)
        return
    command, receipt = build_command(options.world, options.server, presentation_trace=options.presentation_trace)
    print(json.dumps({**receipt, "dryRun": options.dry_run}, ensure_ascii=False, indent=2), flush=True)
    if options.dry_run:
        return
    if not receipt["addonPrepared"]:
        raise RuntimeError("Build the addon and run tools/prepare_instance.py before launching")
    if PID_FILE.exists() and owned_process(json.loads(PID_FILE.read_text(encoding="utf-8"))):
        raise RuntimeError("This prototype client is already running; use --stop or its own window")
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    environment = dict(os.environ)
    environment.pop("__CFBundleIdentifier", None)
    with CONSOLE.open("w", encoding="utf-8") as output:
        process = subprocess.Popen(command, cwd=INSTANCE, env=environment, stdout=output,
                                   stderr=subprocess.STDOUT, start_new_session=True)
    record = {"pid": process.pid, "instance": str(INSTANCE), "player": PLAYER,
              "startSignature": process_field(process.pid, "lstart"),
              "startedAt": datetime.now().astimezone().isoformat()}
    dump(PID_FILE, record)
    dump(EVIDENCE / "launch.json", {**receipt, **record})
    print(f"Started isolated prototype client PID {process.pid}. Console: {CONSOLE}", flush=True)
    if options.wait:
        code = process.wait()
        dump(EVIDENCE / "exit.json", {"pid": process.pid, "exitCode": code,
                                      "at": datetime.now().astimezone().isoformat()})
        print(f"Prototype client exited: {code}")


if __name__ == "__main__":
    main()
