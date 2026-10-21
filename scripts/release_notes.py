#!/usr/bin/env python3
"""Assembles the GitHub release body out of the release-note fragments.

The notes of a release live in a flat ``release-notes/`` folder: one markdown file per
**feature**, each self-contained (it carries its own heading).  Every pull request that
adds a feature adds its own file, so two pull requests never touch the same one and the
notes never collide.  The folder holds the notes of the release being prepared; every
past release keeps its own copy in the git history, at its tag, so the notes of a release
are always recoverable — there is nothing to archive by hand.

This tool concatenates the fragments — in the order they landed in the repository, which
is the order of the work — and appends an automatically generated section that lists
everything the commit history holds since the previous release.  That section is
reconstructed from the git history alone — the previous tag, the commits in between, the
diffstat — so it never depends on anything stored in the working tree.  The first release
of the fork is compared against the fork point (the `fork-point` tag), because the
releases before it belong to the upstream project.

The documentation of the notes lives in `docs/release-notes.md`.

Commands:
  new [version]        start the notes of the next release: remove the fragments the
                        previous release shipped
  build [version]      write the release body (fragments + generated changelog)
  changelog [version]  write only the generated changelog section
  check [version]      validate the fragments

Only the standard library is used, so the tool runs on a bare CI runner and on any
developer machine with Python 3.8+.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

# The flat folder holding the fragments of the release being prepared.
NOTES_DIR_NAME = "release-notes"
# The file of the folder that is documentation, not a fragment.  The documentation of
# the notes lives in `docs/release-notes.md`; this only guards against a stray readme
# being published as a fragment.
NOTES_README = "README.md"
# The tag marking the commit where this repository left the upstream one.  It is the
# baseline of the first release of the fork: the releases before it belong to the
# upstream project and are not ours to describe.
FORK_POINT_TAG = "fork-point"
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


def git_show(repo_root: Path, ref: str, path: str) -> str | None:
    """The content of a file at a revision, or None when it does not exist there."""
    try:
        return git(repo_root, "show", f"{ref}:{path}")
    except GitError:
        return None


def current_release(repo_root: Path, version: str | None) -> str | None:
    """The release a command works on: the given version, or the tag at HEAD."""
    if version:
        return normalize_version(version)
    try:
        described = git(repo_root, "describe", "--tags", "--exact-match", "HEAD").strip()
    except GitError:
        return None
    return described if VERSION_RE.match(described) else None


def resolve_commit(repo_root: Path, tag: str | None) -> str:
    """The commit of a release, or HEAD when the tag does not exist yet (a local dry run).

    An empty string means the repository has no commit yet, which the callers treat as
    "there is nothing to compare".
    """
    if tag:
        try:
            return git(repo_root, "rev-list", "-n", "1", tag).strip()
        except GitError:
            pass
    try:
        return git(repo_root, "rev-parse", "HEAD").strip()
    except GitError:
        return ""


def previous_tag(repo_root: Path, tag: str | None, commit: str) -> str | None:
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


def fork_point(repo_root: Path, commit: str) -> str | None:
    """The fork point, when it is an ancestor of the commit: the baseline of the first release."""
    if not commit:
        return None
    try:
        listed = git(repo_root, "tag", "--list", FORK_POINT_TAG, "--merged", commit)
    except GitError:
        return None
    return FORK_POINT_TAG if listed.strip() else None


def baseline(repo_root: Path, tag: str | None, commit: str) -> tuple[str | None, bool]:
    """The reference a release is compared against, and whether it is the fork point.

    A release is compared against the previous release tag. The first release of the fork
    has none, so it is compared against the fork point instead: the commits before it are
    the upstream project's, and the releases they shipped do not belong to this fork.
    """
    previous = previous_tag(repo_root, tag, commit)
    if previous:
        return previous, False
    point = fork_point(repo_root, commit)
    if point:
        return point, True
    return None, False


def baseline_label(ref: str, is_fork_point: bool) -> str:
    """How the baseline is named in the generated section."""
    return "the fork point" if is_fork_point else ref


def commit_paths(repo_root: Path, commit: str) -> list[str]:
    """The files a commit touched."""
    try:
        listed = git(repo_root, "show", "--name-only", "--format=", commit)
    except GitError:
        return []
    return [line.strip() for line in listed.splitlines() if line.strip()]


def touches_only_the_notes(repo_root: Path, commit: str) -> bool:
    """Whether a commit only touched the notes.

    The notes are the bookkeeping of a release — the commit that clears the fragments the
    previous release shipped, for instance. It is not a change of the product, so it does
    not belong in the changelog of the next one.
    """
    paths = commit_paths(repo_root, commit)
    if not paths:
        return False
    return all(path.startswith(f"{NOTES_DIR_NAME}/") for path in paths)


def commit_subjects(repo_root: Path, previous: str | None, commit: str) -> list[str]:
    """The subjects of the commits between the previous release and this one.

    Merge commits are skipped: they only repeat the title of the branch they brought in,
    and the commits they carry are listed on their own. The commits that only touched the
    notes are skipped as well: they are the bookkeeping of a release.
    """
    if not commit:
        return []
    revision = f"{previous}..{commit}" if previous else commit
    listed = git(repo_root, "log", "--no-merges", "--pretty=format:%H%x09%s", revision)
    subjects = []
    for line in listed.splitlines():
        if not line.strip():
            continue
        sha, _, subject = line.partition("\t")
        if touches_only_the_notes(repo_root, sha.strip()):
            continue
        subjects.append(subject.strip())
    return subjects


def diff_shortstat(repo_root: Path, previous: str | None, commit: str) -> str:
    """The one-line summary of the diff against the previous release.

    The notes are left out: the line describes the change of the product, not the
    bookkeeping of the release.
    """
    if not commit:
        return ""
    revision = f"{previous}..{commit}" if previous else f"{EMPTY_TREE}..{commit}"
    listed = git(
        repo_root,
        "diff",
        "--shortstat",
        revision,
        "--",
        ".",
        f":(exclude){NOTES_DIR_NAME}",
    )
    return listed.strip()


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


def notes_dir(repo_root: Path) -> Path:
    """The flat folder holding the fragments."""
    return repo_root / NOTES_DIR_NAME


def natural_key(name: str) -> list[tuple[int, object]]:
    """Sort key that orders "2-x.md" before "10-x.md"."""
    return [(0, int(part)) if part.isdigit() else (1, part.lower()) for part in re.split(r"(\d+)", name)]


def fragment_paths(repo_root: Path) -> list[Path]:
    """The fragments of the notes, in the order they are published in."""
    directory = notes_dir(repo_root)
    if not directory.is_dir():
        return []
    fragments = [
        path
        for path in directory.iterdir()
        if path.is_file() and path.suffix == ".md" and path.name != NOTES_README
    ]
    return sorted(fragments, key=lambda path: natural_key(path.name))


def read_fragment(path: Path) -> str:
    """The text of a fragment, without its surrounding blank lines."""
    return path.read_text(encoding="utf-8").strip()


def added_at(repo_root: Path, path: Path) -> int | None:
    """The timestamp of the commit that added a file, or None when it is not in the history.

    The date is the one of the author of the commit, not the one of the commit itself: it
    does not change when the commit is rebased or cherry-picked.
    """
    try:
        relative = path.relative_to(repo_root).as_posix()
    except ValueError:
        return None
    try:
        listed = git(repo_root, "log", "--diff-filter=A", "--reverse", "--format=%at", "--", relative)
    except GitError:
        return None
    stamps = [int(line.strip()) for line in listed.splitlines() if line.strip().isdigit()]
    return stamps[0] if stamps else None


def fragment_order(repo_root: Path, fragments: list[Path]) -> list[Path]:
    """The fragments in publication order: the order they landed in the repository.

    Every pull request adds its own fragment, so the order of the files is the order of
    the work. A fragment that is not committed yet comes last: it is the newest one.
    """
    dated = [(added_at(repo_root, path), path.name, path) for path in fragments]
    dated.sort(key=lambda item: (item[0] is None, item[0] or 0, item[1]))
    return [item[2] for item in dated]


def fragments_at(repo_root: Path, ref: str) -> list[str]:
    """The names of the fragments a revision held, in publication order.

    The names are relative to the notes folder, like the ones of the working tree.
    """
    try:
        listed = git(repo_root, "ls-tree", "--name-only", ref, f"{NOTES_DIR_NAME}/")
    except GitError:
        return []
    prefix = f"{NOTES_DIR_NAME}/"
    names = []
    for line in listed.splitlines():
        name = line.strip()
        if not name.startswith(prefix):
            continue
        name = name[len(prefix) :]
        if not name.endswith(".md") or name == NOTES_README:
            continue
        names.append(name)
    return sorted(names, key=natural_key)


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


def changelog_section(repo_root: Path, version: str | None) -> str:
    """The generated section: everything the commit history holds since the previous release.

    Everything it shows comes from the git history: the previous tag, the commits in
    between and the diffstat. Nothing is read from the notes folder.
    """
    tag = current_release(repo_root, version)
    commit = resolve_commit(repo_root, tag)
    if not commit:
        return ""
    reference, is_fork_point = baseline(repo_root, tag, commit)
    subjects = commit_subjects(repo_root, reference, commit)
    repo_url = github_repo_url(repo_root)

    lines: list[str] = []
    if reference:
        lines.append(f"## Changes since {baseline_label(reference, is_fork_point)}")
        stats = diff_shortstat(repo_root, reference, commit)
        if not subjects:
            # Everything that happened since the previous release was the bookkeeping of
            # the notes: there is no change of the product to describe.
            lines.append("_No change of the product since the previous release._")
        else:
            summary = f"_{len(subjects)} commit{'s' if len(subjects) != 1 else ''}"
            if stats:
                summary += f", {stats}"
            lines.append(summary + "._")
    else:
        # Without a predecessor there is no diff to show: the whole history is not a
        # comparison, and listing it would bury the notes of the release.
        lines.append("## Changes")
        lines.append(f"_{len(subjects)} commits — the whole history of the repository._")
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

    if tag and repo_url:
        lines.append("")
        lines.append(f"[Compare {reference}...{tag}]({repo_url}/compare/{reference}...{tag})")

    return "\n".join(lines)


# --------------------------------------------------------------------------------------
# Commands
# --------------------------------------------------------------------------------------


def build_body(repo_root: Path, version: str | None) -> str:
    """The release body: the fragments of the notes, then the generated section."""
    fragments = fragment_order(repo_root, fragment_paths(repo_root))
    if not fragments:
        raise NotesError(f"no fragment in {notes_dir(repo_root)}")
    parts = []
    for fragment in fragments:
        text = read_fragment(fragment)
        if text:
            parts.append(text)
    changelog = changelog_section(repo_root, version)
    if changelog:
        parts.append(changelog)
    return "\n\n".join(parts).rstrip() + "\n"


def command_new(repo_root: Path, version: str | None, force: bool) -> int:
    """Starts the notes of the next release.

    The fragments the previous release shipped are removed from the working tree: their
    copy lives in the git history, at the tag of that release, and the next release starts
    from an empty folder. Every pull request adds its own fragment, so two pull requests
    never touch the same file.

    A fragment that was edited after the release is not removed — the edit would be lost.
    The command refuses to run in that case, unless it is forced.
    """
    tag = current_release(repo_root, version)
    commit = resolve_commit(repo_root, tag)
    reference, is_fork_point = baseline(repo_root, tag, commit)
    directory = notes_dir(repo_root)
    if not directory.is_dir():
        print(f"Nothing to clear: {directory} does not exist.")
        return 0

    shipped = fragments_at(repo_root, reference) if reference and not is_fork_point else []
    if not shipped:
        print("No previous release to clear the notes of.")
        return 0

    removable: list[Path] = []
    edited: list[str] = []
    for name in shipped:
        published = git_show(repo_root, reference, f"{NOTES_DIR_NAME}/{name}")
        local = directory / name
        if not local.exists():
            continue
        if published is not None and local.read_text(encoding="utf-8").strip() != published.strip():
            edited.append(name)
            if not force:
                continue
        removable.append(local)

    if edited and not force:
        print(
            f"error: these fragments were edited after {reference} ({', '.join(edited)}); "
            f"commit them or pass --force to drop them",
            file=sys.stderr,
        )
        return 1

    for path in removable:
        path.unlink()
    if removable:
        print(f"Removed {len(removable)} fragments shipped by {reference}: {', '.join(path.name for path in removable)}")
    else:
        print(f"Nothing to remove: {reference} shipped no fragment.")
    print("The next release starts empty: every pull request adds its own fragment.")
    return 0


def command_build(repo_root: Path, version: str | None, output: str | None) -> int:
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


def command_changelog(repo_root: Path, version: str | None) -> int:
    """Prints only the generated section of the release body."""
    section = changelog_section(repo_root, version)
    if section:
        sys.stdout.write(section + "\n")
    return 0


def command_check(repo_root: Path, version: str | None) -> int:
    """Validates the notes: the errors block the release, the warnings do not."""
    directory = notes_dir(repo_root)
    errors: list[str] = []
    warnings: list[str] = []

    fragments = fragment_paths(repo_root)
    if not directory.is_dir():
        errors.append(f"no notes: {directory} does not exist")
    elif not fragments:
        errors.append(f"{directory} holds no fragment: a release needs at least one feature fragment")

    for fragment in fragments:
        if not read_fragment(fragment):
            warnings.append(f"{fragment.name} is empty")

    # A fragment that is byte for byte the one the previous release shipped is a leftover:
    # the feature was already announced, and the fragment must not be published again.
    tag = current_release(repo_root, version)
    reference, _ = baseline(repo_root, tag, resolve_commit(repo_root, tag))
    if reference:
        for fragment in fragments:
            published = git_show(repo_root, reference, f"{NOTES_DIR_NAME}/{fragment.name}")
            if published is not None and published.strip() == read_fragment(fragment):
                warnings.append(f"{fragment.name} already shipped in {reference}: remove it")

    for warning in warnings:
        print(f"warning: {warning}")
    for error in errors:
        print(f"error: {error}", file=sys.stderr)
    if errors:
        return 1
    print(f"The notes are ready: {len(fragments)} fragments")
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

    new_command = commands.add_parser("new", help="prepare the notes of the next release")
    new_command.add_argument("version", nargs="?", help="the release being prepared (defaults to the tag at HEAD)")
    new_command.add_argument("--force", action="store_true", help="restore the notes even when they hold changes")

    build_command = commands.add_parser("build", help="assemble the release body")
    build_command.add_argument("version", nargs="?", help="the release being built (defaults to the tag at HEAD)")
    build_command.add_argument("-o", "--output", help="the file to write (defaults to stdout)")

    changelog_command = commands.add_parser("changelog", help="print the generated changelog section")
    changelog_command.add_argument("version", nargs="?", help="the release (defaults to the tag at HEAD)")

    check_command = commands.add_parser("check", help="validate the notes")
    check_command.add_argument("version", nargs="?", help="the release being checked (defaults to the tag at HEAD)")

    args = parser.parse_args(argv)
    repo_root = Path(args.repo_root).resolve()

    try:
        if args.command == "new":
            return command_new(repo_root, args.version, args.force)
        if args.command == "build":
            return command_build(repo_root, args.version, args.output)
        if args.command == "changelog":
            return command_changelog(repo_root, args.version)
        return command_check(repo_root, args.version)
    except (NotesError, GitError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
