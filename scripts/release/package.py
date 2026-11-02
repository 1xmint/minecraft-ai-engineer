"""Package already-built runtime files, never a repository or user-data snapshot."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import zipfile


def digest(data):
    return hashlib.sha256(data).hexdigest()


def build(root, output, jar=None):
    root = root.resolve(strict=True)
    if subprocess.run(["git", "diff", "--quiet", "HEAD", "--"], cwd=root).returncode:
        raise ValueError("Commit tracked changes before packaging a source identity")
    source = subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=root, text=True
    ).strip()
    if len(source) != 40 or any(c not in "0123456789abcdef" for c in source):
        raise ValueError("A full checkout SHA is required")

    def read(path):
        path = root / path
        if not path.resolve(strict=True).is_relative_to(root):
            raise ValueError("Input resolves outside the checkout")
        for part in [path, *path.parents]:
            if part == root:
                break
            if part.is_symlink():
                raise ValueError("Symlink inputs are not packaged")
        if not path.is_file():
            raise ValueError(f"Required regular file missing: {path}")
        return path.read_bytes()

    files = {}
    for name in [
        "package.json", "package-lock.json", "LICENSE", ".node-version",
        "scripts/planner-launcher.sh", "scripts/planner-launcher.cs",
    ]:
        files[name] = read(name)
    # Derive emitted JS names from tracked production sources, not arbitrary dist contents.
    sources = subprocess.check_output(
        ["git", "ls-files", "-z", "--", "companion/src"], cwd=root
    ).decode().split("\0")
    modules = [name for name in sources if name.endswith(".ts") and not name.endswith(".d.ts")]
    if not modules:
        raise ValueError("No tracked companion runtime sources")
    for name in modules:
        emitted = "dist/" + name[:-3] + ".js"
        files[emitted] = read(emitted)
    for name in ["mechanisms.json", "shutter.json"]:
        emitted = f"dist/catalog/{name}"
        files[emitted] = read(emitted)
    for required in [
        "dist/companion/src/main.js", "dist/companion/src/launcher.js",
        "dist/catalog/mechanisms.json", "dist/catalog/shutter.json",
    ]:
        if required not in files:
            raise ValueError(f"Build first: missing {required}")
    files["START-HERE.md"] = read("docs/install.md")
    for name in ["mod-guide.md", "companion-guide.md", "comparators.md", "acceptance-guide.md", "protocol.md"]:
        files[f"docs/{name}"] = read(f"docs/{name}")
    images = subprocess.check_output(
        ["git", "ls-files", "-z", "--", "docs/images"], cwd=root
    ).decode().split("\0")
    for name in images:
        if name.endswith(".png"):
            files[name] = read(name)
    manifest = {
        "schemaVersion": 1,
        "checkoutSha": source,
        "runId": os.environ.get("GITHUB_RUN_ID"),
        "node": files[".node-version"].decode().strip(),
        "files": {name: digest(data) for name, data in sorted(files.items())},
    }
    files["BUILD.json"] = (json.dumps(manifest, indent=2) + "\n").encode()

    jar_bytes = None
    if jar:
        jar_path = Path(jar).resolve(strict=True)
        if not jar_path.is_relative_to(root / "mod/build/libs"):
            raise ValueError("Use the deployable jar from this checkout's build/libs")
        jar_bytes = read(jar_path.relative_to(root))
        with zipfile.ZipFile(jar_path) as archive:
            metadata = json.loads(archive.read("fabric.mod.json"))
            if metadata["id"] != "engineer" or jar_path.name.endswith("-sources.jar"):
                raise ValueError("Expected the deployable engineer jar")
            for name in ["mechanisms.json", "shutter.json"]:
                if archive.read(f"engineer/{name}") != read(f"catalog/{name}"):
                    raise ValueError("Jar catalog differs from the companion checkout")

    # Exclusive output creation is deliberate: never clean or overwrite a user's folder.
    output.mkdir(parents=True, exist_ok=False)
    bundle_name = f"minecraft-ai-engineer-companion-{source[:12]}.zip"
    bundle = output / bundle_name
    with zipfile.ZipFile(bundle, "x", compression=zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(files.items()):
            info = zipfile.ZipInfo(name, date_time=(2020, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, data)
    hashes = {bundle_name: digest(bundle.read_bytes())}
    if jar_bytes is not None:
        jar_name = f"minecraft-ai-engineer-{source[:12]}.jar"
        with (output / jar_name).open("xb") as target:
            target.write(jar_bytes)
        hashes[jar_name] = digest(jar_bytes)
    release = {"schemaVersion": 1, "checkoutSha": source,
               "runId": manifest["runId"], "artifacts": hashes}
    with (output / "BUILD.json").open("x", encoding="utf-8") as target:
        json.dump(release, target, indent=2)
        target.write("\n")
    with (output / "SHA256SUMS").open("x", encoding="ascii", newline="\n") as target:
        for name, checksum in sorted(hashes.items()):
            target.write(f"{checksum}  {name}\n")
    print(json.dumps(release))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--jar", type=Path)
    args = parser.parse_args()
    build(args.root, args.out, args.jar)
