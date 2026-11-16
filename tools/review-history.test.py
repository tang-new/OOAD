"""使用独立临时 Git 仓库验证口试索引工具，不修改主仓库历史或运行产品服务。"""

import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


SPEC = importlib.util.spec_from_file_location("review_history", Path(__file__).with_name("review-history.py"))
REVIEW = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(REVIEW)


class HistoryTests(unittest.TestCase):
    """真实 Git 区间、成员关注过滤、私有路径排除和只读性质。"""

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="ooad-review-tool-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.env = dict(os.environ, GIT_AUTHOR_NAME="Fixture Author", GIT_COMMITTER_NAME="Fixture Author",
                        GIT_AUTHOR_EMAIL="fixture@example.invalid", GIT_COMMITTER_EMAIL="fixture@example.invalid",
                        GIT_CONFIG_NOSYSTEM="1", GIT_CONFIG_GLOBAL=os.devnull)
        self.command("init", "-q")
        self.command("config", "core.hooksPath", str(self.root / "no-hooks"))
        self.write("rbac-ddd/Shared.java", "class Shared {}\n")
        self.write(".gitignore", ".tmp/\n.runtime/\ntarget/\nnode_modules/\n")
        self.base = self.commit("baseline")
        self.manifest = {"baselineCommit": self.base, "sourceFiles": [
            {"path": "rbac-ddd/Shared.java", "owners": ["A", "F"], "lead": "A"},
            {"path": "rbac-web/view.ts", "owners": ["E"], "lead": "E"},
        ]}

    def command(self, *args):
        return subprocess.check_output(
            ["git", "-c", "safe.directory=" + self.root.as_posix(), "-C", str(self.root), *args],
            env=self.env, stderr=subprocess.PIPE,
        ).decode("utf-8").strip()

    def write(self, name, value):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(value, encoding="utf-8", newline="\n")

    def commit(self, subject):
        self.command("add", ".")
        self.command("commit", "-qm", subject)
        return self.command("rev-parse", "HEAD")

    def test_empty_interval_does_not_invent_review_or_signatures(self):
        report = REVIEW.history(self.root, self.manifest)
        self.assertEqual([], report["commits"])
        self.assertIn("没有匹配提交", REVIEW.adoc(report))

    def test_real_commits_and_shared_attention_are_not_author_mapping(self):
        self.write("rbac-ddd/Shared.java", "class Shared { int revision; }\n")
        first = self.commit("review shared source")
        self.write("rbac-web/view.ts", "export const value = 1\n")
        second = self.commit("review web")
        report = REVIEW.history(self.root, self.manifest, member="F")
        self.assertEqual([first], [row["commit"] for row in report["commits"]])
        row = report["commits"][0]
        self.assertEqual("Fixture Author", row["author"])
        self.assertEqual(["A", "F"], row["files"][0]["attention"])
        self.assertEqual("NOT_RECORDED", row["humanReviewState"])
        self.assertEqual(second, report["untilInclusive"])

    def test_dirty_working_tree_is_reported_but_never_committed(self):
        self.write("rbac-ddd/Shared.java", "class Shared { int notCommitted; }\n")
        before = self.command("status", "--porcelain")
        report = REVIEW.history(self.root, self.manifest)
        self.assertTrue(report["workingTreeHasChanges"])
        self.assertEqual([], report["commits"])
        self.assertEqual(before, self.command("status", "--porcelain"))
        self.assertEqual(self.base, self.command("rev-parse", "HEAD"))

    def test_private_paths_are_not_in_report_even_if_tracked_in_old_history(self):
        self.write(".runtime/private.txt", "synthetic-private-fixture\n")
        self.command("add", "-f", ".runtime/private.txt")
        self.command("commit", "-qm", "fixture private change")
        report = REVIEW.history(self.root, self.manifest)
        self.assertEqual(1, report["excludedPrivatePathCount"])
        self.assertEqual([], report["commits"])
        self.assertNotIn("private.txt", str(report))

    def test_invalid_ref_and_reversed_range_fail(self):
        for ref in ["--all", "HEAD\n", "missing-commit"]:
            with self.assertRaises(ValueError):
                REVIEW.history(self.root, self.manifest, since_ref=ref)
        self.write("rbac-ddd/Shared.java", "class Shared { int change; }\n")
        latest = self.commit("later")
        with self.assertRaises(ValueError):
            REVIEW.history(self.root, self.manifest, since_ref=latest, until_ref=self.base)

    def test_source_inventory_includes_new_source_and_omits_ignored_artifacts(self):
        self.write("rbac-web/new.ts", "export {}\n")
        self.write("rbac-web/package-lock.json", "{}\n")
        self.write("rbac-ddd/target/Generated.java", "class Generated {}\n")
        self.write(".tmp/context.java", "class Context {}\n")
        paths = REVIEW.source_paths(self.root)
        self.assertIn("rbac-web/new.ts", paths)
        self.assertIn("rbac-ddd/Shared.java", paths)
        self.assertNotIn("rbac-web/package-lock.json", paths)
        self.assertNotIn("rbac-ddd/target/Generated.java", paths)
        self.assertNotIn(".tmp/context.java", paths)

    def test_adoc_escapes_commit_table_delimiters(self):
        self.write("rbac-web/view.ts", "export {}\n")
        self.commit("review | field")
        rendered = REVIEW.adoc(REVIEW.history(self.root, self.manifest))
        self.assertIn("review \\| field", rendered)

    def test_context_distinguishes_same_missing_changed_without_copying_content(self):
        import hashlib
        self.write(".tmp/same.md", "synthetic-local-context\n")
        self.write(".tmp/changed.md", "new content\n")
        original = b"synthetic-local-context\n"
        expected = {"missingOriginals": ["formal grading original"], "files": [
            {"name": name, "bytes": len(original), "sha256": hashlib.sha256(original).hexdigest()}
            for name in ["same.md", "changed.md", "absent.md"]
        ]}
        report = REVIEW.context(self.root, expected)
        self.assertEqual(["MATCH", "CHANGED", "MISSING"], [row["state"] for row in report["files"]])
        self.assertFalse(report["allAvailableAndMatching"])
        self.assertFalse(report["humanReadConfirmed"])
        self.assertNotIn("synthetic-local-context", str(report))
        self.assertEqual(original, (self.root / ".tmp/same.md").read_bytes())


if __name__ == "__main__":
    unittest.main(verbosity=2)
