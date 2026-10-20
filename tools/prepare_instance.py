#!/usr/bin/env python3
"""Prepare an isolated minimum-dependency prototype from installed local JARs.

No launcher database, account data, production configuration, or source world is changed.
Embedded libraries stay inside their parent JARs and are recorded in the manifest.
"""
from __future__ import annotations

import argparse
from dataclasses import dataclass, field
from datetime import datetime
import hashlib
import io
import json
from pathlib import Path
import shutil
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[1]
WORKSPACE = ROOT.parents[1]
SOURCE = WORKSPACE / "clients/cmi-expedition/instance/mods"
INSTANCE = ROOT / "instance"
MANIFEST = INSTANCE / "prototype-manifest.json"
ADDON = ROOT / "build/arcane-industry-0.1.0.jar"
ROOT_JARS = (
    "create-1.20.1-6.0.8.jar",
    "create-enchantment-industry-2.5.4b.jar",
    "irons_spellbooks-1.20.1-3.16.3.jar",
    "sophisticatedcore-1.20.1-1.5.6.2383.jar",
    "sophisticatedbackpacks-1.20.1-3.26.9.2194.jar",
)
BUILTINS = {"minecraft": "1.20.1", "forge": "47.4.10"}


def sha256(path: Path) -> str:
    with path.open("rb") as source:
        digest = hashlib.file_digest(source, "sha256")
    return digest.hexdigest()


@dataclass
class JarInfo:
    path: Path
    mods: dict[str, str] = field(default_factory=dict)
    dependencies: list[dict] = field(default_factory=list)
    embedded: list[dict] = field(default_factory=list)


def inspect_archive(archive: zipfile.ZipFile, origin: str = "") -> tuple[dict, list, list]:
    mods, dependencies, embedded = {}, [], []
    names = archive.namelist()
    if "META-INF/mods.toml" in names:
        metadata = tomllib.loads(archive.read("META-INF/mods.toml").decode("utf-8-sig"))
        for mod in metadata.get("mods", []):
            mods[mod["modId"]] = str(mod.get("version", "unknown"))
        requirements_by_owner = metadata.get("dependencies", {})
        if isinstance(requirements_by_owner, list):
            requirements_by_owner = {next(iter(mods), "unknown"): requirements_by_owner}
        for owner, requirements in requirements_by_owner.items():
            for requirement in requirements:
                if requirement.get("mandatory", False):
                    dependencies.append({"owner": owner, "origin": origin or "root", **requirement})
    if "META-INF/jarjar/metadata.json" in names:
        for entry in json.loads(archive.read("META-INF/jarjar/metadata.json")).get("jars", []):
            nested_path = entry["path"]
            data = archive.read(nested_path)
            location = f"{origin}!/{nested_path}" if origin else nested_path
            with zipfile.ZipFile(io.BytesIO(data)) as child:
                child_mods, child_dependencies, child_embedded = inspect_archive(child, location)
            mods.update(child_mods)
            dependencies.extend(child_dependencies)
            embedded.append({"path": location, "identifier": entry.get("identifier", {}),
                             "version": entry.get("version", {}), "mods": child_mods,
                             "sha256": hashlib.sha256(data).hexdigest()})
            embedded.extend(child_embedded)
    return mods, dependencies, embedded


def inspect_jar(path: Path) -> JarInfo:
    with zipfile.ZipFile(path) as archive:
        return JarInfo(path, *inspect_archive(archive))


def is_ars(info: JarInfo) -> bool:
    return info.path.name.lower().startswith("ars") or any(mod.startswith("ars") for mod in info.mods)


def resolve(include_jei: bool = True) -> tuple[list[JarInfo], list[dict], list[str]]:
    if not SOURCE.is_dir():
        raise RuntimeError(f"Installed source mods directory is missing: {SOURCE}")
    inventory = [inspect_jar(path) for path in sorted(SOURCE.glob("*.jar"))]
    excluded = [info.path.name for info in inventory if is_ars(info)]
    allowed = [info for info in inventory if not is_ars(info)]
    by_name = {info.path.name: info for info in allowed}
    providers: dict[str, list[JarInfo]] = {}
    for info in allowed:
        for mod in info.mods:
            providers.setdefault(mod, []).append(info)
    selected = {}
    for name in ROOT_JARS:
        if name not in by_name:
            raise RuntimeError(f"Pinned installed JAR is missing: {name}")
        selected[name] = by_name[name]
    if include_jei and "jei" in providers:
        choices = [info for info in providers["jei"] if info.path.name.lower().startswith("jei-")]
        if len(choices) != 1:
            raise RuntimeError("Expected one installed standalone JEI JAR")
        selected[choices[0].path.name] = choices[0]
    if ADDON.is_file():
        selected[ADDON.name] = inspect_jar(ADDON)
    while True:
        provided = set(BUILTINS)
        for info in selected.values():
            provided.update(info.mods)
        missing = {dep["modId"] for info in selected.values() for dep in info.dependencies
                   if dep["modId"] not in provided}
        if not missing:
            break
        for mod in sorted(missing):
            candidates = providers.get(mod, [])
            # Prefer its standalone JAR to an unrelated mod that happens to bundle it.
            direct = []
            for info in candidates:
                with zipfile.ZipFile(info.path) as archive:
                    metadata = tomllib.loads(archive.read("META-INF/mods.toml").decode("utf-8-sig"))
                if mod in {entry["modId"] for entry in metadata.get("mods", [])}:
                    direct.append(info)
            choices = direct or candidates
            if len(choices) != 1:
                raise RuntimeError(f"Mandatory dependency {mod!r} has {len(choices)} eligible providers")
            selected[choices[0].path.name] = choices[0]
    supplied = dict(BUILTINS)
    for info in selected.values():
        supplied.update(info.mods)
    dependencies = [{"parentJar": info.path.name, **dep,
                     "providedVersion": supplied[dep["modId"]]}
                    for info in selected.values() for dep in info.dependencies]
    return sorted(selected.values(), key=lambda info: info.path.name.lower()), dependencies, excluded


def prepare(*, dry_run: bool = False, include_jei: bool = True) -> dict:
    selected, dependencies, excluded = resolve(include_jei)
    receipt = {
        "preparedAt": datetime.now().astimezone().isoformat(),
        "instance": str(INSTANCE), "sourceMods": str(SOURCE),
        "minecraft": "1.20.1", "forge": "47.4.10", "addonPresent": ADDON.is_file(),
        "manaRegenMultiplier": 0.05, "excludedArsJars": excluded,
        "dependencyResolution": "Recursive mandatory mods.toml, including embedded JARs; Forge validates version ranges at launch",
        "mandatoryDependencies": dependencies,
        "jars": [{"file": info.path.name, "source": str(info.path), "mods": info.mods,
                  "sha256": sha256(info.path), "embeddedJars": info.embedded} for info in selected],
    }
    if dry_run:
        return receipt
    mods = INSTANCE / "mods"
    mods.mkdir(parents=True, exist_ok=True)
    previous = json.loads(MANIFEST.read_text()) if MANIFEST.exists() else {"jars": []}
    managed = {entry["file"] for entry in previous.get("jars", [])}
    wanted = {info.path.name for info in selected}
    unknown = {path.name for path in mods.glob("*.jar")} - managed - wanted
    if unknown:
        raise RuntimeError(f"Unmanaged JARs exist in the prototype instance; preserve and remove them before preparing: {sorted(unknown)}")
    for name in managed - wanted:
        (mods / name).unlink(missing_ok=True)
    for info, entry in zip(selected, receipt["jars"]):
        target = mods / info.path.name
        if target.is_symlink():
            raise RuntimeError(f"Refusing to overwrite a linked mod: {target}")
        if not target.is_file() or sha256(target) != entry["sha256"]:
            shutil.copy2(info.path, target)
        if sha256(target) != entry["sha256"]:
            raise RuntimeError(f"Copied JAR failed SHA256 verification: {target}")
    (INSTANCE / "defaultconfigs").mkdir(exist_ok=True)
    (INSTANCE / "defaultconfigs/irons_spellbooks-server.toml").write_text(
        "# Isolated Arcane Industry prototype. Forge fills other defaults.\n[Misc]\nmanaRegenMultiplier = 0.05\n",
        encoding="utf-8")
    options = INSTANCE / "options.txt"
    existing = options.read_text(encoding="utf-8").splitlines() if options.exists() else []
    overrides = {"fullscreen": "false", "renderDistance": "8", "simulationDistance": "8",
                 "overrideWidth": "1280", "overrideHeight": "800", "maxFps": "60", "lang": "zh_cn"}
    existing = [line for line in existing if line.partition(":")[0] not in overrides]
    options.write_text("\n".join(existing + [f"{key}:{value}" for key, value in overrides.items()]) + "\n",
                       encoding="utf-8")
    MANIFEST.write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return receipt


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--without-jei", action="store_true")
    args = parser.parse_args()
    receipt = prepare(dry_run=args.dry_run, include_jei=not args.without_jei)
    print(json.dumps({"instance": receipt["instance"], "dryRun": args.dry_run,
                      "addonPresent": receipt["addonPresent"], "jars": [jar["file"] for jar in receipt["jars"]],
                      "embeddedJarCount": sum(len(jar["embeddedJars"]) for jar in receipt["jars"]),
                      "manifest": str(MANIFEST)}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
