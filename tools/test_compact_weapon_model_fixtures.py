import json
import hashlib
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / "modules/weapon_models/src/test/fixtures/assets"


class CompactWeaponModelFixturesTest(unittest.TestCase):
    def test_presentation_fixtures_do_not_copy_render_geometry(self):
        manifests = sorted(FIXTURES.glob("*/*/manifest.json"))
        self.assertEqual(3, len(manifests))
        sources = json.loads((ROOT / "modules/fixture-sources.json").read_text())
        registered = {ROOT / row["new"]: row for row in sources["files"] if row["new"].endswith("/manifest.json")}
        for manifest in manifests:
            payload = json.loads(manifest.read_text())
            self.assertLess(manifest.stat().st_size, 256 * 1024, manifest)
            self.assertEqual(hashlib.sha256(manifest.read_bytes()).hexdigest(), registered[manifest]["sha256"])
            self.assertIn("source_sha256", registered[manifest])
            for model in payload["models"]:
                self.assertEqual([], model["boxes"], manifest)
                self.assertEqual([], model["meshes"], manifest)
                self.assertTrue(model["slots"] or model["attachmentOrigin"] == [0, 0, 0], manifest)


if __name__ == "__main__":
    unittest.main()
