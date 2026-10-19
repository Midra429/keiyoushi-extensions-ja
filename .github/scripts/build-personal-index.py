import argparse
import gzip
import hashlib
import json
import re
import shutil
from pathlib import Path

import index_pb2
from google.protobuf import json_format


def build_index(root: Path, output: Path, tag: str, signing_key: str):
    config = json.loads((root / "store.json").read_text(encoding="utf-8"))
    repository = config["repository"]
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
        raise ValueError("Invalid repository")
    if not re.fullmatch(r"[A-Za-z0-9_.-]+", tag):
        raise ValueError("Invalid release tag")
    if not re.fullmatch(r"[0-9a-f]{64}", signing_key):
        raise ValueError("Expected a SHA-256 signing certificate fingerprint")
    output.mkdir(parents=True, exist_ok=True)
    base_url = f"https://github.com/{repository}/releases/download/{tag}"
    extensions = []
    source_ids = set()
    packages = set()
    for module in config["modules"]:
        if not re.fullmatch(r"[a-z]+/[a-z0-9]+", module):
            raise ValueError(f"Invalid module: {module}")
        directory = root / "src" / module
        build = directory / "build"
        info = json.loads((build / "keiyoushi-source-info.json").read_text(encoding="utf-8"))
        if info["module"] != module.replace("/", "."):
            raise ValueError(f"Unexpected metadata for {module}")
        package = info["packageName"]
        if package in packages:
            raise ValueError(f"Duplicate package: {package}")
        packages.add(package)
        resources = {}
        for extension in ("apk", "jar"):
            files = list((build / "outputs" / extension / "release").glob(f"*.{extension}"))
            if len(files) != 1:
                raise ValueError(f"Expected one {extension} for {module}, found {len(files)}")
            file = files[0]
            shutil.copy2(file, output / file.name)
            resources[f"{extension}Url"] = f"{base_url}/{file.name}"
        icon_path = Path("res/mipmap-xhdpi/ic_launcher.png")
        candidates = [directory / icon_path]
        if info.get("theme"):
            candidates.append(root / "lib-multisrc" / info["theme"] / icon_path)
        candidates.append(root / "core/src/main" / icon_path)
        icon = next(path for path in candidates if path.is_file())
        icon_name = f"icon-{module.replace('/', '-')}.png"
        shutil.copy2(icon, output / icon_name)
        resources["iconUrl"] = f"{base_url}/{icon_name}"
        sources = []
        for source in info["sources"]:
            source_id = int(source["id"])
            if source_id in source_ids:
                raise ValueError(f"Duplicate source ID: {source_id}")
            source_ids.add(source_id)
            sources.append(index_pb2.Source(
                id=source_id,
                name=source["name"],
                language=source["lang"],
                homeUrl=source["baseUrl"],
                mirrorUrls=source.get("mirrorUrls", []),
            ))
        extensions.append(index_pb2.Extension(
            name=info["name"],
            packageName=package,
            resources=index_pb2.Resources(**resources),
            extensionLib=info["extensionLib"],
            versionCode=info["versionCode"],
            versionName=info["versionName"],
            contentWarning=info["contentWarning"],
            sources=sources,
        ))
    index = index_pb2.Index(
        name=config["name"],
        badgeLabel=config["badgeLabel"],
        signingKey=signing_key,
        contact=index_pb2.Contact(website=f"https://github.com/{repository}"),
        extensionList=index_pb2.ExtensionList(extensions=sorted(extensions, key=lambda ext: ext.packageName)),
    )
    (output / "index.json").write_text(
        json_format.MessageToJson(index, preserving_proto_field_name=True, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    (output / "index.pb").write_bytes(gzip.compress(index.SerializeToString(deterministic=True), mtime=0))
    checksums = "".join(
        f"{hashlib.sha256(file.read_bytes()).hexdigest()}  {file.name}\n"
        for file in sorted(output.iterdir()) if file.is_file() and file.name != "SHA256SUMS"
    )
    (output / "SHA256SUMS").write_text(checksums, encoding="utf-8")
    return index


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--signing-key", required=True)
    args = parser.parse_args()
    result = build_index(args.root, args.output, args.tag, args.signing_key)
    print(f"Prepared {len(result.extensionList.extensions)} extensions")
