"""Write an artifact identity manifest; no publishing or credentials required."""
import hashlib
import json
import pathlib
import subprocess
import sys

artifact = pathlib.Path(sys.argv[1])
revision = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
dirty = bool(subprocess.check_output(["git", "status", "--porcelain"], text=True).strip())
with artifact.open("rb") as stream:
    digest = hashlib.file_digest(stream, "sha256").hexdigest()
data = {
    "schemaVersion": 1,
    "gitRevision": revision,
    "workingTreeDirty": dirty,
    "artifact": artifact.name,
    "sizeBytes": artifact.stat().st_size,
    "sha256": digest,
    "javaTarget": 21,
    "mavenWrapper": "3.9.11",
}
output = artifact.parent / "release-manifest.json"
output.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
print(output)
