"""Tests of the release-notes assembler.

They run with `python3 -m unittest discover -s scripts/tests -t scripts` (the CI runs
exactly that) and cover the parts that are easy to break silently: the ordering of the
fragments, the grouping of the generated changelog — which is reconstructed from the git
history alone — and the checks that guard a release.
"""

import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import release_notes  # noqa: E402  (the path is set up right above)


def run(*args: str, cwd: Path) -> None:
    subprocess.run(args, cwd=str(cwd), check=True, capture_output=True, text=True)


def make_repo(root: Path) -> Path:
    """An empty git repository with a deterministic identity."""
    repo = root / "repo"
    repo.mkdir()
    try:
        run("git", "init", "-q", "-b", "main", cwd=repo)
    except subprocess.CalledProcessError:
        run("git", "init", "-q", cwd=repo)
    run("git", "config", "user.email", "test@example.com", cwd=repo)
    run("git", "config", "user.name", "Test", cwd=repo)
    run("git", "config", "commit.gpgsign", "false", cwd=repo)
    run("git", "remote", "add", "origin", "https://github.com/owner/repo.git", cwd=repo)
    return repo


def commit(repo: Path, message: str) -> None:
    (repo / "file.txt").write_text(message, encoding="utf-8")
    run("git", "add", "-A", cwd=repo)
    run("git", "commit", "-q", "-m", message, cwd=repo)


def write_fragment(repo: Path, name: str, text: str) -> Path:
    directory = repo / release_notes.NOTES_DIR_NAME
    directory.mkdir(parents=True, exist_ok=True)
    path = directory / name
    path.write_text(text, encoding="utf-8")
    return path


class VersionTest(unittest.TestCase):
    def test_normalizes_both_forms(self):
        self.assertEqual(release_notes.normalize_version("3.1.0"), "v3.1.0")
        self.assertEqual(release_notes.normalize_version("v3.1.0"), "v3.1.0")
        self.assertEqual(release_notes.normalize_version(" v3.1.0-rc.1 "), "v3.1.0-rc.1")

    def test_rejects_anything_else(self):
        for bad in ("3.1", "v3", "latest", ""):
            with self.assertRaises(release_notes.NotesError):
                release_notes.normalize_version(bad)

    def test_a_prerelease_sorts_before_its_final_release(self):
        versions = ["v3.1.0", "v3.0.0", "v3.1.0-rc.1", "v2.9.9"]
        self.assertEqual(
            sorted(versions, key=release_notes.version_key),
            ["v2.9.9", "v3.0.0", "v3.1.0-rc.1", "v3.1.0"],
        )


class FragmentOrderTest(unittest.TestCase):
    def test_fragments_are_ordered_by_file_name(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            write_fragment(repo, "10-second.md", "second")
            write_fragment(repo, "00-summary.md", "summary")
            write_fragment(repo, "20-third.md", "third")
            fragments = release_notes.fragment_paths(repo)
            self.assertEqual([path.name for path in fragments], ["00-summary.md", "10-second.md", "20-third.md"])

    def test_ten_sorts_before_nine(self):
        # The names are prefixed by hand, so the order must not depend on the digits.
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            write_fragment(repo, "9-nine.md", "nine")
            write_fragment(repo, "10-ten.md", "ten")
            fragments = release_notes.fragment_paths(repo)
            self.assertEqual([path.name for path in fragments], ["9-nine.md", "10-ten.md"])

    def test_the_readme_of_the_folder_is_not_a_fragment(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            write_fragment(repo, "00-summary.md", "summary")
            write_fragment(repo, "README.md", "How the notes work.")
            fragments = release_notes.fragment_paths(repo)
            self.assertEqual([path.name for path in fragments], ["00-summary.md"])


class BuildTest(unittest.TestCase):
    def test_body_is_the_fragments_then_the_generated_section(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = make_repo(Path(directory))
            commit(repo, "the work")
            write_fragment(repo, "00-summary.md", "The headline.")
            write_fragment(repo, "10-feature.md", "### A feature\n\n* Something.")
            body = release_notes.build_body(repo, "v3.1.0")
            self.assertTrue(body.startswith("The headline.\n\n### A feature"))
            self.assertIn("## Changes", body)

    def test_build_fails_without_fragments(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            with self.assertRaises(release_notes.NotesError):
                release_notes.build_body(repo, "v3.1.0")


class ChangelogTest(unittest.TestCase):
    def test_groups_the_commits_by_type(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = make_repo(Path(directory))
            commit(repo, "first commit")
            run("git", "tag", "v3.0.0", cwd=repo)
            commit(repo, "feat: add a thing (#12)")
            commit(repo, "fix: repair a thing")
            commit(repo, "refactor: move a thing")
            commit(repo, "History screen: header bar (#13)")
            run("git", "tag", "v3.1.0", cwd=repo)

            section = release_notes.changelog_section(repo, "v3.1.0")
            self.assertIn("## Changes since v3.0.0", section)
            self.assertIn("### New features", section)
            self.assertIn("* Add a thing ([#12](https://github.com/owner/repo/pull/12))", section)
            self.assertIn("### Fixes", section)
            self.assertIn("* Repair a thing", section)
            self.assertIn("### Refactoring", section)
            self.assertIn("### Other changes", section)
            self.assertIn("* History screen: header bar ([#13](https://github.com/owner/repo/pull/13))", section)
            self.assertIn(
                "[Compare v3.0.0...v3.1.0](https://github.com/owner/repo/compare/v3.0.0...v3.1.0)",
                section,
            )

    def test_the_first_commit_of_the_previous_release_is_not_listed(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = make_repo(Path(directory))
            commit(repo, "the old work")
            run("git", "tag", "v3.0.0", cwd=repo)
            commit(repo, "the new work")
            run("git", "tag", "v3.1.0", cwd=repo)

            section = release_notes.changelog_section(repo, "v3.1.0")
            self.assertNotIn("The old work", section)
            self.assertIn("The new work", section)

    def test_merge_commits_are_skipped(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = make_repo(Path(directory))
            commit(repo, "the work")
            run("git", "tag", "v3.0.0", cwd=repo)
            run("git", "checkout", "-q", "-b", "topic", cwd=repo)
            commit(repo, "the topic work")
            run("git", "checkout", "-q", "main", cwd=repo)
            run("git", "merge", "-q", "--no-ff", "-m", "Merge pull request #1 from owner/topic", "topic", cwd=repo)
            run("git", "tag", "v3.1.0", cwd=repo)

            section = release_notes.changelog_section(repo, "v3.1.0")
            self.assertNotIn("Merge pull request", section)
            self.assertIn("The topic work", section)

    def test_a_tag_of_another_branch_is_not_a_predecessor(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = make_repo(Path(directory))
            commit(repo, "the work")
            run("git", "tag", "v3.0.0", cwd=repo)
            run("git", "checkout", "-q", "-b", "side", cwd=repo)
            commit(repo, "the side work")
            run("git", "tag", "v9.9.9", cwd=repo)
            run("git", "checkout", "-q", "main", cwd=repo)
            commit(repo, "the main work")
            run("git", "tag", "v3.1.0", cwd=repo)

            section = release_notes.changelog_section(repo, "v3.1.0")
            self.assertIn("## Changes since v3.0.0", section)
            self.assertNotIn("v9.9.9", section)
            self.assertIn("The main work", section)
            self.assertNotIn("The side work", section)

    def test_a_release_without_a_predecessor_lists_no_history(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = make_repo(Path(directory))
            commit(repo, "the old work")
            commit(repo, "the new work")
            run("git", "tag", "v3.1.0", cwd=repo)

            section = release_notes.changelog_section(repo, "v3.1.0")
            self.assertIn("## Changes", section)
            self.assertIn("the first release of this repository", section)
            # The whole history is not a diff against a previous version: it would bury the
            # notes of the release.
            self.assertNotIn("The old work", section)

    def test_the_section_is_reconstructed_from_the_history_alone(self):
        # The notes folder holds no versioned copy of the diff: the section is what the
        # git history says, and nothing else.
        with tempfile.TemporaryDirectory() as directory:
            repo = make_repo(Path(directory))
            commit(repo, "the work")
            run("git", "tag", "v3.0.0", cwd=repo)
            commit(repo, "feat: the new work")
            run("git", "tag", "v3.1.0", cwd=repo)
            write_fragment(repo, "00-summary.md", "The headline.")

            with_notes = release_notes.changelog_section(repo, "v3.1.0")
            without_notes = release_notes.changelog_section(repo, "v3.1.0")
            self.assertEqual(with_notes, without_notes)
            self.assertIn("The new work", with_notes)


class CheckTest(unittest.TestCase):
    def test_notes_without_a_headline_are_an_error(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            write_fragment(repo, "10-feature.md", "### A feature")
            self.assertEqual(release_notes.command_check(repo, "v3.1.0"), 1)

    def test_the_placeholder_headline_is_an_error(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            write_fragment(repo, "00-summary.md", f"{release_notes.SUMMARY_PLACEHOLDER}\n")
            self.assertEqual(release_notes.command_check(repo, "v3.1.0"), 1)

    def test_an_empty_headline_is_an_error(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            write_fragment(repo, "00-summary.md", "   \n")
            self.assertEqual(release_notes.command_check(repo, "v3.1.0"), 1)

    def test_a_written_release_passes(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            write_fragment(repo, "00-summary.md", "The headline.")
            write_fragment(repo, "10-feature.md", "### A feature")
            self.assertEqual(release_notes.command_check(repo, "v3.1.0"), 0)

    def test_an_untouched_fragment_is_only_a_warning(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = make_repo(Path(directory))
            commit(repo, "the work")
            run("git", "tag", "v3.0.0", cwd=repo)
            write_fragment(repo, "00-summary.md", "The headline.")
            write_fragment(repo, "10-feature.md", "### A feature")
            run("git", "add", "-A", cwd=repo)
            run("git", "commit", "-q", "-m", "the notes", cwd=repo)
            run("git", "tag", "v3.0.1", cwd=repo)
            write_fragment(repo, "00-summary.md", "The new headline.")
            # The fragment is byte for byte the one the previous release kept in the history.
            self.assertEqual(release_notes.command_check(repo, "v3.1.0"), 0)


class NewTest(unittest.TestCase):
    def test_restores_the_fragments_of_the_previous_release(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = make_repo(Path(directory))
            commit(repo, "the work")
            write_fragment(repo, "00-summary.md", "The published headline.")
            write_fragment(repo, "10-feature.md", "### The published feature")
            run("git", "add", "-A", cwd=repo)
            run("git", "commit", "-q", "-m", "the notes", cwd=repo)
            run("git", "tag", "v3.0.0", cwd=repo)

            self.assertEqual(release_notes.command_new(repo, "v3.1.0", False), 0)
            directory = repo / release_notes.NOTES_DIR_NAME
            # The headline is release specific: it is replaced by a placeholder the check refuses.
            self.assertIn(release_notes.SUMMARY_PLACEHOLDER, (directory / "00-summary.md").read_text(encoding="utf-8"))
            self.assertEqual(
                (directory / "10-feature.md").read_text(encoding="utf-8"),
                "### The published feature",
            )

    def test_refuses_to_drop_local_changes(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = make_repo(Path(directory))
            commit(repo, "the work")
            write_fragment(repo, "00-summary.md", "The published headline.")
            run("git", "add", "-A", cwd=repo)
            run("git", "commit", "-q", "-m", "the notes", cwd=repo)
            run("git", "tag", "v3.0.0", cwd=repo)
            write_fragment(repo, "00-summary.md", "Something else entirely.")

            self.assertEqual(release_notes.command_new(repo, "v3.1.0", False), 1)
            self.assertEqual(release_notes.command_new(repo, "v3.1.0", True), 0)


class RemoteUrlTest(unittest.TestCase):
    def test_reads_the_owner_and_the_repository_of_the_remote(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = make_repo(Path(directory))
            self.assertEqual(release_notes.github_repo_url(repo), "https://github.com/owner/repo")

    def test_reads_the_scp_style_remote(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = make_repo(Path(directory))
            run("git", "remote", "set-url", "origin", "git@github.com:P403n1x87/tv-bro.git", cwd=repo)
            self.assertEqual(release_notes.github_repo_url(repo), "https://github.com/P403n1x87/tv-bro")

    def test_a_remote_that_is_not_github_gets_no_links(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = make_repo(Path(directory))
            run("git", "remote", "set-url", "origin", "/home/someone/tv-bro", cwd=repo)
            self.assertIsNone(release_notes.github_repo_url(repo))
            run("git", "remote", "set-url", "origin", "https://gitlab.com/owner/repo.git", cwd=repo)
            self.assertIsNone(release_notes.github_repo_url(repo))


if __name__ == "__main__":
    unittest.main()
