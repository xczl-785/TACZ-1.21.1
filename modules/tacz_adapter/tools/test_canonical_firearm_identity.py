"""Canonical firearm identity and authoring-boundary regression tests."""
import json
from pathlib import Path
import unittest


MODULE = Path(__file__).resolve().parents[1]
RESOURCES = MODULE / "weapon-content/resources"
INDEX = RESOURCES / "data/tactical_tacz_adapter/assembled_weapons.json"
CANONICAL_NAMESPACE = "tacz_fork_tarkov"
RETIRED_NAMESPACES = {"tacz_assembly", "newmod_adar", "newmod_m4a1", "newmod_radian"}


class CanonicalFirearmIdentityTest(unittest.TestCase):
    def test_only_fifteen_canonical_firearms_are_published(self):
        index = json.loads(INDEX.read_text())
        self.assertEqual(15, len(index["weapons"]))
        identities = []
        for relative in index["weapons"]:
            weapon = json.loads((RESOURCES / relative).read_text())
            identities.append(weapon["gunId"])
            self.assertEqual(CANONICAL_NAMESPACE, weapon["gunId"].split(":", 1)[0])
            self.assertEqual("tacz_fork_tarkov", weapon["developmentCategory"])
            self.assertRegex(weapon["authoringSource"], r"^native_[a-z0-9_]+$")
            self.assertNotIn("developmentSource", weapon)
        self.assertEqual(15, len(set(identities)))

    def test_retired_runtime_namespaces_are_absent(self):
        for kind in ("assets", "data"):
            names = {entry.name for entry in (RESOURCES / kind).iterdir() if entry.is_dir()}
            self.assertTrue(RETIRED_NAMESPACES.isdisjoint(names), names & RETIRED_NAMESPACES)

    def test_preview_geometry_has_one_canonical_runtime_copy(self):
        index = json.loads(INDEX.read_text())
        for relative in index["weapons"]:
            weapon = json.loads((RESOURCES / relative).read_text())
            namespace, _ = weapon["gunId"].split(":", 1)
            directory = weapon["resourceDirectory"]
            self.assertTrue((RESOURCES / "data" / namespace / directory / "preview.json").is_file())
            asset_copy = RESOURCES / "assets" / namespace / directory
            self.assertFalse((asset_copy / "icon_geometry.json").exists())
            self.assertFalse((asset_copy / "icon_library.json").exists())
            self.assertFalse((asset_copy / "icon_materials.json").exists())


if __name__ == "__main__":
    unittest.main()
