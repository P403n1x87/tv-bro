# Release notes

The notes of a release are not one file but a set of **fragments**, one per topic, in the
`release-notes/` folder. Each fragment is a self-contained markdown file: it carries its
own heading, and the file name only decides the order it is published in (the numeric
prefix). Splitting the notes keeps unrelated topics in unrelated files, so a reworded
section never collides with the rest of the notes.

| File | What it holds |
|---|---|
| `00-summary.md` | The headline of the release: one or two sentences on what a user would notice. Mandatory — it is what the release page shows first, and what the in-app update dialog shows as the summary of the new version. |
| `10-….md`, `20-…` | One file per topic (`### Video playback`, `### Settings`, …). Delete the ones that do not apply to the release instead of leaving them empty. |

The folder holds the notes of the release being prepared, and nothing else. Every past
release keeps its own copy in the git history, at its tag:
`git show v3.1.0:release-notes/00-summary.md` is the headline that release shipped with.
There is nothing to archive by hand.

The release body published on GitHub is the concatenation of the fragments, **plus** an
automatically generated section that lists everything the commit history holds since the
previous release. The hand-written fragments tell the story; the generated section makes
sure nothing is missed.

## Cutting a release

```sh
# 1. Restore the notes of the release that was published last and reset the headline.
python3 scripts/release_notes.py new v3.1.0

# 2. Write the notes: the headline in 00-summary.md, then one fragment per topic.
#    The restored fragments are a starting point — delete the ones that do not apply.

# 3. Check them. Errors block the release, warnings do not.
python3 scripts/release_notes.py check v3.1.0

# 4. Preview the release body.
python3 scripts/release_notes.py build v3.1.0

# 5. Cut the release: the tag triggers the workflow, which publishes the assembled body.
gh release create v3.1.0 --target master
```

`new` refuses to run when the notes hold changes that are not in the previous release
(commit them first, or pass `--force`): the notes of a release live in the git history, so
the published copy is never the only one.

`new` writes a placeholder headline, and `check` refuses a release whose headline is still
the placeholder — a release never goes out with an empty summary. `check` also warns about
a fragment that is byte for byte the one the previous release kept in the history: it
usually describes something that did not change.

## The generated section

`build` appends a `## Changes since <previous tag>` section built from the commit history
between the previous release tag and this one. It is reconstructed from the git history
alone — the previous tag, the commits in between, the diffstat — and reads nothing from
the notes folder:

- the commits are grouped by their conventional-commit type (`feat`, `fix`, `refactor`,
  `ci`, …); anything else lands in "Other changes";
- merge commits are skipped — they only repeat the title of the branch they brought in;
- a subject that references a pull request (`… (#42)`) links to it;
- the section ends with a link to the compare view of the two versions.

Only tags that are ancestors of the release count as its predecessor, so a tag cut from an
unrelated branch never becomes the baseline of the diff.

The section is generated, not written by hand: it is regenerated on every release and
never edited. The fragments above it are the curated part.

### The fork point

The first release of this fork has no previous release tag, and the releases before it
belong to the upstream project (`truefedex/tv-bro`) — they are not ours to describe. The
`fork-point` tag marks the commit where this repository left the upstream one, and it is
the baseline of that first release:

```
## Changes since the fork point
```

The tag is named `fork-point` on purpose: it does not match the `v*` pattern the release
workflow triggers on, so pushing it cannot cut a release.

## Commands

| Command | What it does |
|---|---|
| `new [version]` | Restores the fragments of the previous release from its tag and writes the placeholder headline. `--force` restores them even when the notes hold changes. |
| `build [version]` | Assembles the release body (fragments + generated section) and writes it to stdout, or to `--output <file>`. |
| `changelog [version]` | Prints only the generated section. |
| `check [version]` | Validates the notes: a missing folder, a missing headline or a placeholder headline is an error; an empty or untouched fragment is a warning. |

The version is optional: it defaults to the tag at HEAD, and only drives the generated
section (which release the diff is computed against).

The tool only needs Python 3.8+ and git. Its own tests run with:

```sh
python3 -m unittest discover -s scripts/tests -t scripts
```
