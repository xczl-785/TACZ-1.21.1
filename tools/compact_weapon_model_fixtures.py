#!/usr/bin/env python3
"""Project presentation fixtures to the structural fields their tests consume."""
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / "modules/weapon_models/src/test/fixtures/assets"


def compact(path: Path) -> None:
    source = json.loads(path.read_text())
    models = []
    for model in source["models"]:
        models.append({
            "definitionId": model["definitionId"],
            "name": model.get("name", model["definitionId"]),
            "attachmentOrigin": model["attachmentOrigin"],
            "slots": model["slots"],
            "boxes": [],
            "meshes": [],
        })
    path.write_text(json.dumps({"schemaVersion": source["schemaVersion"], "models": models},
                               ensure_ascii=False, indent=2) + "\n")


if __name__ == "__main__":
    manifests = sorted(FIXTURES.glob("*/*/manifest.json"))
    if len(manifests) != 3:
        raise SystemExit(f"Expected three presentation fixtures, found {len(manifests)}")
    for fixture in manifests:
        compact(fixture)
        print(f"Compacted {fixture.relative_to(ROOT)}")
