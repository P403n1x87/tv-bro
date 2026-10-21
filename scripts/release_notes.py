#!/usr/bin/env python3
"""Assembles the GitHub release body out of the release-note fragments.

The notes of a release are not one file but a set of markdown *fragments* under
``release-notes/<tag>/``: one file per topic, each self-contained (it carries its own
heading) and ordered by the numeric prefix of its file name.  Splitting them keeps
unrelated topics in unrelated files, so a reworded section never conflicts with the
rest of the notes.

This tool concatenates those fragments and appends an automatically generated section
that lists everything the commit history holds since the previous release, so the
release page never misses a change the hand-written notes forgot.

Commands:
  new <version>        create the fragment folder of a new release, seeded with the
                        fragments of the previous one
  build <version>      write the release body (fragments + generated changelog)
  changelog <version>  write only the generated changelog section
  check <version>      validate the fragments of a release
  list                 list the releases that have fragments

Only the standard library is used, so the tool runs on a bare CI runner and on any
developer machine with Python 3.8+.
"""

from __future__ import annotations

import argparse
import re
import shutil
import subprocess
import sys
from pathlib import Path

# The folder holding the fragments of every release, one sub-folder per tag.
NOTES_DIR_NAME = "release-notes"
# The fragment holding the headline of a release.  It is the only mandatory one: it is
# what the release page shows first, and what the in-app update dialog shows as the
# summary of the new version.
SUMMARY_FRAGMENT = "00-summary.md"
# Marker left in a freshly seeded summary, so `check` can tell an unwritten headline
# from a written one.
SUMMARY_PLACEHOLDER = "<!-- release-notes: summary placeholder -->"
# The hash of the empty tree, used to diff against when a release has no predecessor.
EMPTY_TREE = "4b825dc642cb6eb9a060e54bf8d69288fbee4904"

VERSION_RE = re.compile(r"^v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?$")
# A pull request reference at the end of a subject, the way GitHub writes it when a
# branch is squash-merged.
PR_RE = re.compile(r"\(#(\d+)\)\s*$")
# A conventional-commit subject: "type(scope)!: subject".
CONVENTIONAL_RE = re.compile(r"^(?P<type>[a-zA-Z]+)(?:\((?P<scope>[^)]*)\))?(?P<breaking>!)?: (?P<subject>.+)$")

# The commit types that get a section of their own, in the order they are printed.
# Anything else lands in "Other changes".
CATEGORY_TITLES = {
    "feat": "New features",
    "fix": "Fixes",
    "perf": "Performance",
    "refactor": "Refactoring",
    "style": "Code style",
    "test": "Tests",
    "ci": "Build and CI",
    "build": "Build and CI",
    "docs": "Documentation",
    "chore": "Housekeeping",
}
CATEGORY_ORDER = [
    "New features",
    "Fixes",
    "Performance",
    "Refactoring",
    "Code style",
    "Tests",
    "Build and CI",
    "Documentation",
    "Housekeeping",
    "Other changes",
]


class GitError(RuntimeError):
    """Raised when a git command fails (no repository, no such tag, ...)."""


class NotesError(RuntimeError):
    """Raised when the fragments of a release cannot be used."""


# --------------------------------------------------------------------------------------
# Versions
# --------------------------------------------------------------------------------------


def normalize_version(version: str) -> str:
    """Returns the canonical "vX.Y.Z" form of a version, with or without the prefix."""
    match = VERSION_RE.match(version.strip())
    if not match:
        raise NotesError(f"not a vX.Y.Z version: {version!r}")
    major, minor, patch, prerelease = match.groups()
    base = f"v{major}.{minor}.{patch}"
    return f"{base}-{prerelease}" if prerelease else base


def version_key(version: str) -> tuple:
    """Sort key of a version: a pre-release sorts before the final release of the same version."""
    match = VERSION_RE.match(version.strip())
    if not match:
        return (0, 0, 0, 0, "")
    major, minor, patch, prerelease = match.groups()
    return (int(major), int(minor), int(patch), 0 if prerelease else 1, prerelease or "")


# --------------------------------------------------------------------------------------
# Git
# --------------------------------------------------------------------------------------


def git(repo_root: Path, *args: str) -> str:
    """Runs a git command in the repository and returns its stdout."""
    result = subprocess.run(
        ["git", *args],
        cwd=str(repo_root),
        capture_output=True,
        text=True,
        check=False,
    )
    if result.returncode != 0:
        raise GitError(f"git {' '.join(args)} failed: {result.stderr.strip()}")
    return result.stdout


def resolve_commit(repo_root: Path, tag: str) -> str:
    """The commit a tag points at, or HEAD when the tag does not exist yet (a local dry run).

    An empty string means the repository has no commit yet, which the callers treat as
    "there is nothing to compare".
    """
    try:
        return git(repo_root, "rev-list", "-n", "1", tag).strip()
    except GitError:
        pass
    try:
        return git(repo_root, "rev-parse", "HEAD").strip()
    except GitError:
        return ""


def previous_tag(repo_root: Path, tag: str, commit: str) -> str | None:
    """The newest release tag that is an ancestor of `commit`, i.e. the version this one succeeds.

    Only ancestors count: a tag cut from an unrelated branch is not a predecessor of this
    release, and diffing against it would list changes that never happened here.
    """
    if not commit:
        return None
    try:
        listed = git(repo_root, "tag", "--list", "v*", "--merged", commit)
    except GitError:
        return None
    candidates = [line.strip() for line in listed.splitlines()]
    candidates = [name for name in candidates if name != tag and VERSION_RE.match(name)]
    if not candidates:
        return None
    return max(candidates, key=version_key)


def commit_subjects(repo_root: Path, previous: str | None, commit: str) -> list[str]:
    """The subjects of the commits between the previous release and this one.

    Merge commits are skipped: they only repeat the title of the branch they brought in,
    and the commits they carry are listed on their own.
    """
    if not commit:
        return []
    revision = f"{previous}..{commit}" if previous else commit
    listed = git(repo_root, "log", "--no-merges", "--pretty=format:%s", revision)
    return [line.strip() for line in listed.splitlines() if line.strip()]


def diff_shortstat(repo_root: Path, previous: str | None, commit: str) -> str:
    """The one-line summary of the diff against the previous release."""
    if not commit:
        return ""
    revision = f"{previous}..{commit}" if previous else f"{EMPTY_TREE}..{commit}"
    return git(repo_root, "diff", "--shortstat", revision).strip()


def github_repo_url(repo_root: Path) -> str | None:
    """The browsable URL of the repository, read from the `origin` remote.

    The links in the generated section point at the pull requests and the compare view of
    the repository the release is cut from, so they must follow the remote instead of
    being hardcoded. Only a GitHub remote gets them: the paths of the pull request and
    compare pages are GitHub-specific.
    """
    try:
        remote = git(repo_root, "remote", "get-url", "origin").strip()
    except GitError:
        return None
    if not remote:
        return None

    host = ""
    path = remote
    if "://" in remote:
        host, _, path = remote.split("://", 1)[1].partition("/")
    elif ":" in remote:
        # The scp-style remote ("git@github.com:owner/repo").
        host, _, path = remote.partition(":")
    else:
        # A local path: there is nothing to link to.
        return None

    host = host.split("@")[-1].lower()
    if host not in ("github.com", "www.github.com"):
        return None

    path = path.rstrip("/")
    if path.endswith(".git"):
        path = path[: -len(".git")]
    parts = [part for part in path.split("/") if part]
    if len(parts) < 2:
        return None
    return f"https://github.com/{parts[-2]}/{parts[-1]}"


# --------------------------------------------------------------------------------------
# Fragments
# --------------------------------------------------------------------------------------


def notes_dir(repo_root: Path, version: str) -> Path:
    """The folder holding the fragments of a release."""
    return repo_root / NOTES_DIR_NAME / normalize_version(version)


def natural_key(name: str) -> list[tuple[int, object]]:
    """Sort key that orders "2-x.md" before "10-x.md"."""
    return [(0, int(part)) if part.isdigit() else (1, part.lower()) for part in re.split(r"(\d+)", name)]


def fragment_paths(directory: Path) -> list[Path]:
    """The fragments of a release, in the order they are published in."""
    if not directory.is_dir():
        return []
    fragments = [path for path in directory.iterdir() if path.is_file() and path.suffix == ".md"]
    return sorted(fragments, key=lambda path: natural_key(path.name))


def read_fragment(path: Path) -> str:
    """The text of a fragment, without its surrounding blank lines."""
    return path.read_text(encoding="utf-8").strip()


def release_folders(repo_root: Path) -> list[Path]:
    """The fragment folders of every release that has one, oldest first."""
    base = repo_root / NOTES_DIR_NAME
    if not base.is_dir():
        return []
    folders = [path for path in base.iterdir() if path.is_dir() and VERSION_RE.match(path.name)]
    return sorted(folders, key=lambda path: version_key(path.name))


def latest_release_folder(repo_root: Path, exclude: str | None = None) -> Path | None:
    """The fragment folder of the newest release, optionally skipping one version."""
    folders = [folder for folder in release_folders(repo_root) if folder.name != exclude]
    return folders[-1] if folders else None


# --------------------------------------------------------------------------------------
# The generated changelog
# --------------------------------------------------------------------------------------


def split_pull_request(subject: str) -> tuple[str, str | None]:
    """Splits a subject into its text and the pull request it references, if any."""
    match = PR_RE.search(subject)
    if not match:
        return subject.strip(), None
    return subject[: match.start()].strip(), match.group(1)


def classify(subject: str) -> tuple[str, str, str | None]:
    """Splits a subject into the section it belongs to, its text and its scope.

    The text is the subject without the pull request reference, which is rendered as a
    link of its own.
    """
    text, _ = split_pull_request(subject)
    match = CONVENTIONAL_RE.match(text)
    if not match:
        return "Other changes", text, None
    category = CATEGORY_TITLES.get(match.group("type").lower(), "Other changes")
    scope = match.group("scope")
    body = match.group("subject").strip()
    if match.group("breaking"):
        body = f"Breaking: {body}"
    return category, body, scope


def format_entry(subject: str, repo_url: str | None) -> str:
    """Renders one commit as a bullet of the generated section."""
    _, text, scope = classify(subject)
    _, pull_request = split_pull_request(subject)
    entry = text.strip()
    if scope:
        entry = f"[{scope}] {entry}"
    if entry:
        entry = entry[0].upper() + entry[1:]
    if pull_request and repo_url:
        entry = f"{entry} ([#{pull_request}]({repo_url}/pull/{pull_request}))"
    return entry


def changelog_section(repo_root: Path, version: str) -> str:
    """The generated section: everything the commit history holds since the previous release."""
    tag = normalize_version(version)
    commit = resolve_commit(repo_root, tag)
    if not commit:
        return ""
    previous = previous_tag(repo_root, tag, commit)
    subjects = commit_subjects(repo_root, previous, commit)
    repo_url = github_repo_url(repo_root)

    lines: list[str] = []
    if previous:
        lines.append(f"## Changes since {previous}")
        stats = diff_shortstat(repo_root, previous, commit)
        summary = f"_{len(subjects)} commit{'s' if len(subjects) != 1 else ''}"
        if stats:
            summary += f", {stats}"
        lines.append(summary + "._")
    else:
        # Without a predecessor there is no diff to show: the whole history is not a
        # comparison, and listing it would bury the notes of the release.
        lines.append("## Changes")
        lines.append(f"_{len(subjects)} commits — the first release of this repository._")
        return "\n".join(lines)

    grouped: dict[str, list[str]] = {}
    for subject in subjects:
        category, _, _ = classify(subject)
        grouped.setdefault(category, []).append(format_entry(subject, repo_url))

    for category in CATEGORY_ORDER:
        entries = grouped.get(category)
        if not entries:
            continue
        lines.append("")
        lines.append(f"### {category}")
        lines.extend(f"* {entry}" for entry in entries)

    if previous and repo_url:
        lines.append("")
        lines.append(f"[Compare {previous}...{tag}]({repo_url}/compare/{previous}...{tag})")

    return "\n".join(lines)


# --------------------------------------------------------------------------------------
# Commands
# --------------------------------------------------------------------------------------


def build_body(repo_root: Path, version: str) -> str:
    """The release body: the fragments of the release, then the generated section."""
    directory = notes_dir(repo_root, version)
    if not directory.is_dir():
        raise NotesError(
            f"no fragments for {normalize_version(version)}: {directory} does not exist "
            f"(create it with `release_notes.py new {normalize_version(version)}`)"
        )
    parts = []
    for fragment in fragment_paths(directory):
        text = read_fragment(fragment)
        if text:
            parts.append(text)
    if not parts:
        raise NotesError(f"{directory} holds no fragment with any content")
    changelog = changelog_section(repo_root, version)
    if changelog:
        parts.append(changelog)
    return "\n\n".join(parts).rstrip() + "\n"


def command_new(repo_root: Path, version: str) -> int:
    """Creates the fragment folder of a release, seeded with the fragments of the previous one."""
    tag = normalize_version(version)
    directory = notes_dir(repo_root, tag)
    if directory.exists():
        print(f"error: {directory} already exists", file=sys.stderr)
        return 1
    directory.mkdir(parents=True)

    previous_folder = latest_release_folder(repo_root, exclude=tag)
    if previous_folder:
        for fragment in fragment_paths(previous_folder):
            shutil.copy2(fragment, directory / fragment.name)
        print(f"Seeded {directory} from {previous_folder.name} ({len(fragment_paths(directory))} fragments)")
    else:
        print(f"Created {directory}")

    # The headline is release-specific, so the copied one is replaced by a placeholder:
    # `check` refuses to publish a release whose headline is still the placeholder.
    previous = previous_tag(repo_root, tag, resolve_commit(repo_root, tag)) or "the previous release"
    (directory / SUMMARY_FRAGMENT).write_text(
        f"{SUMMARY_PLACEHOLDER}\n"
        f"Changes since {previous}: what this release is about, in one or two sentences.\n",
        encoding="utf-8",
    )
    print(f"Wrote the headline placeholder {directory / SUMMARY_FRAGMENT}")
    print("Edit the fragments, then cut the release with `gh release create`.")
    return 0


def command_build(repo_root: Path, version: str, output: str | None) -> int:
    """Assembles the release body and writes it to a file (or to stdout)."""
    body = build_body(repo_root, version)
    if output:
        path = Path(output)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(body, encoding="utf-8")
        print(f"Wrote {path} ({len(body)} characters)")
    else:
        sys.stdout.write(body)
    return 0


def command_changelog(repo_root: Path, version: str) -> int:
    """Prints only the generated section of the release body."""
    sys.stdout.write(changelog_section(repo_root, version) + "\n")
    return 0


def command_check(repo_root: Path, version: str) -> int:
    """Validates the fragments of a release: the errors block the release, the warnings do not."""
    tag = normalize_version(version)
    directory = notes_dir(repo_root, tag)
    errors: list[str] = []
    warnings: list[str] = []

    if not directory.is_dir():
        errors.append(f"no fragments for {tag}: {directory} does not exist (run `release_notes.py new {tag}`)")
    else:
        fragments = fragment_paths(directory)
        if not fragments:
            errors.append(f"{directory} holds no fragment")
        summary = directory / SUMMARY_FRAGMENT
        if not summary.exists():
            errors.append(f"{directory} has no {SUMMARY_FRAGMENT}: a release needs a headline")
        elif SUMMARY_PLACEHOLDER in summary.read_text(encoding="utf-8"):
            errors.append(f"{summary} still holds the placeholder headline")
        for fragment in fragments:
            if not read_fragment(fragment):
                warnings.append(f"{fragment.name} is empty")

        # A fragment that is byte for byte the one of the previous release is very likely
        # a leftover: the section describes something that did not change.
        previous_folder = latest_release_folder(repo_root, exclude=tag)
        if previous_folder:
            for fragment in fragments:
                previous_fragment = previous_folder / fragment.name
                if previous_fragment.exists() and previous_fragment.read_bytes() == fragment.read_bytes():
                    warnings.append(f"{fragment.name} is unchanged since {previous_folder.name}")

    for warning in warnings:
        print(f"warning: {warning}")
    for error in errors:
        print(f"error: {error}", file=sys.stderr)
    if errors:
        return 1
    print(f"{tag}: the fragments are ready")
    return 0


def command_list(repo_root: Path) -> int:
    """Lists the releases that have fragments."""
    folders = release_folders(repo_root)
    if not folders:
        print("No release has fragments yet.")
        return 0
    for folder in folders:
        print(f"{folder.name}: {len(fragment_paths(folder))} fragments")
    return 0


# --------------------------------------------------------------------------------------
# Entry point
# --------------------------------------------------------------------------------------


def default_repo_root() -> Path:
    """The repository the script belongs to (its own parent folder)."""
    return Path(__file__).resolve().parent.parent


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument(
        "--repo-root",
        default=str(default_repo_root()),
        help="the repository to work in (defaults to the one this script lives in)",
    )
    commands = parser.add_subparsers(dest="command", required=True)

    new_command = commands.add_parser("new", help="create the fragments of a new release")
    new_command.add_argument("version", help="the version of the release, e.g. v3.1.0")

    build_command = commands.add_parser("build", help="assemble the release body")
    build_command.add_argument("version", help="the version of the release, e.g. v3.1.0")
    build_command.add_argument("-o", "--output", help="the file to write (defaults to stdout)")

    changelog_command = commands.add_parser("changelog", help="print the generated changelog section")
    changelog_command.add_argument("version", help="the version of the release, e.g. v3.1.0")

    check_command = commands.add_parser("check", help="validate the fragments of a release")
    check_command.add_argument("version", help="the version of the release, e.g. v3.1.0")

    commands.add_parser("list", help="list the releases that have fragments")

    args = parser.parse_args(argv)
    repo_root = Path(args.repo_root).resolve()

    try:
        if args.command == "new":
            return command_new(repo_root, args.version)
        if args.command == "build":
            return command_build(repo_root, args.version, args.output)
        if args.command == "changelog":
            return command_changelog(repo_root, args.version)
        if args.command == "check":
            return command_check(repo_root, args.version)
        return command_list(repo_root)
    except (NotesError, GitError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
