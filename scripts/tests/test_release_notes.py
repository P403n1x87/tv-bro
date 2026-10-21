"""Tests of the release-notes assembler.

They run with `python3 -m unittest discover -s scripts/tests -t scripts` (the CI runs
exactly that) and cover the parts that are easy to break silently: the publication order
of the fragments — the order they landed in the repository — the grouping of the
generated changelog, which is reconstructed from the git history alone, and the checks
that guard a release.
"""

import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import release_notes  # noqa: E402  (the path is set up right above)


def run(*args: str, cwd: Path, env: dict | None = None) -> None:
    subprocess.run(args, cwd=str(cwd), check=True, capture_output=True, text=True, env=env)


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


def commit(repo: Path, message: str, date: str | None = None) -> None:
    """Commits everything in the working tree, at a given date when one is given."""
    (repo / "file.txt").write_text(message, encoding="utf-8")
    run("git", "add", "-A", cwd=repo)
    env = os.environ.copy()
    if date:
        env["GIT_AUTHOR_DATE"] = date
        env["GIT_COMMITTER_DATE"] = date
    subprocess.run(
        ["git", "commit", "-q", "-m", message],
        cwd=str(repo),
        check=True,
        capture_output=True,
        text=True,
        env=env,
    )


def write_fragment(repo: Path, name: str, text: str) -> Path:
    directory = repo / release_notes.NOTES_DIR_NAME
    directory.mkdir(parents=True, exist_ok=True)
    path = directory / name
    path.write_text(text, encoding="utf-8")
    return path


def ship(repo: Path, version: str) -> None:
    """Commits the notes and tags them as a release."""
    run("git", "add", "-A", cwd=repo)
    run("git", "commit", "-q", "-m", "the notes", cwd=repo)
    run("git", "tag", version, cwd=repo)


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
    def test_the_fragments_are_published_in_the_order_they_landed(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = make_repo(Path(directory))
            write_fragment(repo, "second.md", "### Second")
            commit(repo, "the second feature", date="2026-01-02T00:00:00")
            write_fragment(repo, "first.md", "### First")
            commit(repo, "the first feature", date="2026-01-01T00:00:00")
            write_fragment(repo, "third.md", "### Third")
            commit(repo, "the third feature", date="2026-01-03T00:00:00")

            fragments = release_notes.fragment_order(repo, release_notes.fragment_paths(repo))
            self.assertEqual(
                [path.name for path in fragments],
                ["first.md", "second.md", "third.md"],
            )

    def test_a_fragment_that_is_not_committed_yet_comes_last(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = make_repo(Path(directory))
            write_fragment(repo, "committed.md", "### Committed")
            commit(repo, "the committed feature", date="2026-01-01T00:00:00")
            write_fragment(repo, "fresh.md", "### Fresh")

            fragments = release_notes.fragment_order(repo, release_notes.fragment_paths(repo))
            self.assertEqual([path.name for path in fragments], ["committed.md", "fresh.md"])

    def test_fragments_landed_together_are_ordered_by_name(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = make_repo(Path(directory))
            write_fragment(repo, "beta.md", "### Beta")
            write_fragment(repo, "alpha.md", "### Alpha")
            commit(repo, "both features", date="2026-01-01T00:00:00")

            fragments = release_notes.fragment_order(repo, release_notes.fragment_paths(repo))
            self.assertEqual([path.name for path in fragments], ["alpha.md", "beta.md"])

    def test_the_readme_of_the_folder_is_not_a_fragment(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            write_fragment(repo, "a-feature.md", "### A feature")
            write_fragment(repo, "README.md", "How the notes work.")
            fragments = release_notes.fragment_paths(repo)
            self.assertEqual([path.name for path in fragments], ["a-feature.md"])


class BuildTest(unittest.TestCase):
    def test_body_is_the_fragments_then_the_generated_section(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = make_repo(Path(directory))
            commit(repo, "the work")
            write_fragment(repo, "a-feature.md", "### A feature\n\n* Something.")
            body = release_notes.build_body(repo, "v3.1.0")
            self.assertTrue(body.startswith("### A feature"))
            self.assertIn("## Changes", body)

    def test_the_body_follows_the_order_the_fragments_landed_in(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = make_repo(Path(directory))
            write_fragment(repo, "second.md", "### Second")
            commit(repo, "the second feature", date="2026-01-02T00:00:00")
            write_fragment(repo, "first.md", "### First")
            commit(repo, "the first feature", date="2026-01-01T00:00:00")

            body = release_notes.build_body(repo, "v3.1.0")
            self.assertLess(body.index("### First"), body.index("### Second"))

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
            self.assertIn("the whole history of the repository", section)
            # The whole history is not a diff against a previous version: it would bury the
            # notes of the release.
            self.assertNotIn("The old work", section)

    def test_a_commit_that_only_touches_the_notes_is_not_listed(self):
        # The commit that clears the fragments the previous release shipped is the
        # bookkeeping of a release, not a change of the product.
        with tempfile.TemporaryDirectory() as directory:
            repo = make_repo(Path(directory))
            commit(repo, "the work")
            run("git", "tag", "v3.0.0", cwd=repo)
            commit(repo, "feat: the new work")
            write_fragment(repo, "a-feature.md", "### A feature")
            run("git", "add", "-A", cwd=repo)
            run("git", "commit", "-q", "-m", "chore: clear the notes of the previous release", cwd=repo)
            run("git", "tag", "v3.1.0", cwd=repo)

            section = release_notes.changelog_section(repo, "v3.1.0")
            self.assertNotIn("clear the notes", section)
            self.assertIn("The new work", section)

    def test_the_diffstat_leaves_the_notes_out(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = make_repo(Path(directory))
            commit(repo, "the work")
            run("git", "tag", "v3.0.0", cwd=repo)
            commit(repo, "feat: the new work")
            write_fragment(repo, "a-feature.md", "\n".join(f"line {index}" for index in range(100)))
            run("git", "add", "-A", cwd=repo)
            run("git", "commit", "-q", "-m", "the notes", cwd=repo)
            run("git", "tag", "v3.1.0", cwd=repo)

            section = release_notes.changelog_section(repo, "v3.1.0")
            self.assertIn("1 file changed, 1 insertion(+)", section)

    def test_a_release_without_a_product_change_says_so(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = make_repo(Path(directory))
            commit(repo, "the work")
            run("git", "tag", "v3.0.0", cwd=repo)
            write_fragment(repo, "a-feature.md", "### A feature")
            run("git", "add", "-A", cwd=repo)
            run("git", "commit", "-q", "-m", "the notes", cwd=repo)
            run("git", "tag", "v3.1.0", cwd=repo)

            section = release_notes.changelog_section(repo, "v3.1.0")
            self.assertIn("## Changes since v3.0.0", section)
            self.assertIn("No change of the product", section)

    def test_the_section_is_reconstructed_from_the_history_alone(self):
        # The notes folder holds no versioned copy of the diff: the section is what the
        # git history says, and nothing else.
        with tempfile.TemporaryDirectory() as directory:
            repo = make_repo(Path(directory))
            commit(repo, "the work")
            run("git", "tag", "v3.0.0", cwd=repo)
            commit(repo, "feat: the new work")
            run("git", "tag", "v3.1.0", cwd=repo)
            write_fragment(repo, "a-feature.md", "### A feature")

            with_notes = release_notes.changelog_section(repo, "v3.1.0")
            without_notes = release_notes.changelog_section(repo, "v3.1.0")
            self.assertEqual(with_notes, without_notes)
            self.assertIn("The new work", with_notes)


class ForkPointTest(unittest.TestCase):
    """The first release of the fork is compared against the fork point.

    The commits before it belong to the upstream project: their releases are not ours to
    describe, so they must not show up in the diff.
    """

    def test_the_first_release_is_compared_against_the_fork_point(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = make_repo(Path(directory))
            commit(repo, "the upstream work")
            run("git", "tag", "-a", release_notes.FORK_POINT_TAG, "-m", "the fork point", cwd=repo)
            commit(repo, "feat: the work of the fork (#7)")
            run("git", "tag", "v3.0.0", cwd=repo)

            section = release_notes.changelog_section(repo, "v3.0.0")
            self.assertIn("## Changes since the fork point", section)
            self.assertIn("* The work of the fork", section)
            # The upstream commits are before the fork point: they are not part of the diff.
            self.assertNotIn("The upstream work", section)
            self.assertIn(
                "[Compare fork-point...v3.0.0](https://github.com/owner/repo/compare/fork-point...v3.0.0)",
                section,
            )

    def test_a_previous_release_wins_over_the_fork_point(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = make_repo(Path(directory))
            commit(repo, "the upstream work")
            run("git", "tag", "-a", release_notes.FORK_POINT_TAG, "-m", "the fork point", cwd=repo)
            commit(repo, "feat: the work of the fork")
            run("git", "tag", "v3.0.0", cwd=repo)
            commit(repo, "fix: the next one")
            run("git", "tag", "v3.1.0", cwd=repo)

            section = release_notes.changelog_section(repo, "v3.1.0")
            self.assertIn("## Changes since v3.0.0", section)
            self.assertNotIn("the fork point", section)
            self.assertNotIn("The work of the fork", section)

    def test_without_a_fork_point_the_history_is_not_compared(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = make_repo(Path(directory))
            commit(repo, "the upstream work")
            commit(repo, "feat: the work of the fork")
            run("git", "tag", "v3.0.0", cwd=repo)

            section = release_notes.changelog_section(repo, "v3.0.0")
            self.assertIn("## Changes", section)
            self.assertNotIn("## Changes since", section)
            self.assertNotIn("The upstream work", section)


class CheckTest(unittest.TestCase):
    def test_notes_without_a_fragment_are_an_error(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            self.assertEqual(release_notes.command_check(repo, "v3.1.0"), 1)

    def test_an_empty_folder_is_an_error(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            (repo / release_notes.NOTES_DIR_NAME).mkdir()
            self.assertEqual(release_notes.command_check(repo, "v3.1.0"), 1)

    def test_a_written_release_passes(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            write_fragment(repo, "a-feature.md", "### A feature")
            self.assertEqual(release_notes.command_check(repo, "v3.1.0"), 0)

    def test_an_empty_fragment_is_only_a_warning(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            write_fragment(repo, "a-feature.md", "   \n")
            self.assertEqual(release_notes.command_check(repo, "v3.1.0"), 0)

    def test_a_fragment_that_already_shipped_is_only_a_warning(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = make_repo(Path(directory))
            commit(repo, "the work")
            write_fragment(repo, "a-feature.md", "### A feature")
            ship(repo, "v3.0.0")
            # The fragment is byte for byte the one the previous release shipped: it is a
            # leftover, but it does not block the release.
            self.assertEqual(release_notes.command_check(repo, "v3.1.0"), 0)


class NewTest(unittest.TestCase):
    """`new` starts the notes of the next release: the fragments the previous release
    shipped are removed, the ones that were not are kept."""

    def test_removes_the_fragments_the_previous_release_shipped(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = make_repo(Path(directory))
            commit(repo, "the work")
            write_fragment(repo, "shipped.md", "### The shipped feature")
            ship(repo, "v3.0.0")

            self.assertEqual(release_notes.command_new(repo, "v3.1.0", False), 0)
            self.assertFalse((repo / release_notes.NOTES_DIR_NAME / "shipped.md").exists())

    def test_keeps_the_fragments_that_were_not_shipped(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = make_repo(Path(directory))
            commit(repo, "the work")
            write_fragment(repo, "shipped.md", "### The shipped feature")
            ship(repo, "v3.0.0")
            write_fragment(repo, "fresh.md", "### The feature in progress")

            self.assertEqual(release_notes.command_new(repo, "v3.1.0", False), 0)
            directory = repo / release_notes.NOTES_DIR_NAME
            self.assertFalse((directory / "shipped.md").exists())
            self.assertTrue((directory / "fresh.md").exists())

    def test_refuses_to_drop_a_fragment_edited_after_the_release(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = make_repo(Path(directory))
            commit(repo, "the work")
            write_fragment(repo, "shipped.md", "### The shipped feature")
            ship(repo, "v3.0.0")
            write_fragment(repo, "shipped.md", "### The shipped feature, edited")

            self.assertEqual(release_notes.command_new(repo, "v3.1.0", False), 1)
            # The edit is still there: the command refuses before touching anything.
            self.assertTrue((repo / release_notes.NOTES_DIR_NAME / "shipped.md").exists())
            self.assertEqual(release_notes.command_new(repo, "v3.1.0", True), 0)
            self.assertFalse((repo / release_notes.NOTES_DIR_NAME / "shipped.md").exists())

    def test_nothing_to_clear_without_a_previous_release(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = make_repo(Path(directory))
            commit(repo, "the work")
            write_fragment(repo, "a-feature.md", "### A feature")

            self.assertEqual(release_notes.command_new(repo, "v3.1.0", False), 0)
            self.assertTrue((repo / release_notes.NOTES_DIR_NAME / "a-feature.md").exists())


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
