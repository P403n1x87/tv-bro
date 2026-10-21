# Release notes

The notes of a release are a set of **fragments**, one per **feature**, in the
`release-notes/` folder. Each fragment is a single bullet: a brief description of what the
user notices.

**One file per feature, one file per pull request.** A pull request that adds a feature
adds its own fragment, so two pull requests never touch the same file: the notes never
collide, and nobody has to rebase a shared file to land a feature.

| File | What it holds |
|---|---|
| `history-screen.md` | One feature: one bullet saying what the user notices. The file name is kebab-case and describes the feature. |

The folder holds the notes of the release being prepared, and nothing else. Every past
release keeps its own copy in the git history, at its tag:
`git show v3.1.0:release-notes/history-screen.md` is the note that release shipped with.
There is nothing to archive by hand.

The release body published on GitHub is the list of those fragments — **in the order they
landed in the repository**, which is the order of the work — plus an automatically
generated section that lists everything the commit history holds since the previous
release. The hand-written fragments tell the story; the generated section makes sure
nothing is missed.

## Adding a note to a pull request

Drop a file in `release-notes/`, named after the feature:

```markdown
* The browsing history has a screen of its own, where every entry can be deleted on
  its own or all at once.
```

That is the whole convention: one file, one bullet, one feature.

- **No title.** The fragment is an item of a list, not a section of its own: a `###`
  heading would break the list the release body is made of.
- **Brief.** One sentence, or two: what the user notices.
- **User-visible.** The result, not the implementation. No class names, no policies, no
  internal names — "the history screen has a title bar and a delete button", not "the
  focus routing lives in a `HistoryFocusPolicy`".
- **One feature.** A fragment that needs two bullets is two features: two files.

## Cutting a release

```sh
# 1. Remove the fragments the previous release shipped: the next release starts empty.
python3 scripts/release_notes.py new v3.1.0

# 2. Check the notes. Errors block the release, warnings do not.
python3 scripts/release_notes.py check v3.1.0

# 3. Preview the release body.
python3 scripts/release_notes.py build v3.1.0

# 4. Cut the release: the tag triggers the workflow, which publishes the assembled body.
gh release create v3.1.0 --target master
```

`new` removes the fragments the previous release shipped — their copy lives in the git
history, at the tag — and keeps the ones that were not shipped, which are the work in
progress for the next release. It refuses to run when a shipped fragment was edited
after the release, unless `--force` is passed: the edit would be lost.

`check` fails when the release has no fragment at all, and warns about a fragment that is
empty, that is not a single bullet, or that is byte for byte the one the previous release
shipped — a leftover: the feature was already announced, so the fragment must not be
published again.

## The generated section

`build` appends a `## Changes since <previous tag>` section built from the commit history
between the previous release tag and this one. It is reconstructed from the git history
alone — the previous tag, the commits in between, the diffstat — and reads nothing from
the notes folder:

- the commits are grouped by their conventional-commit type (`feat`, `fix`, `refactor`,
  `ci`, …); anything else lands in "Other changes";
- merge commits are skipped — they only repeat the title of the branch they brought in;
- the commits that only touched the notes are skipped as well — the one that clears the
  fragments the previous release shipped is the bookkeeping of a release, not a change of
  the product, and the diffstat leaves the notes out for the same reason (a release that
  changed nothing but the notes says so, instead of listing an empty diff);
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
| `new [version]` | Removes the fragments the previous release shipped, so the next release starts empty. `--force` removes them even when they were edited after the release. |
| `build [version]` | Assembles the release body (fragments + generated section) and writes it to stdout, or to `--output <file>`. |
| `changelog [version]` | Prints only the generated section. |
| `check [version]` | Validates the notes: a missing folder or a release without any fragment is an error; an empty, malformed or already shipped fragment is a warning. |

The version is optional: it defaults to the tag at HEAD, and only drives the generated
section (which release the diff is computed against).

The tool only needs Python 3.8+ and git. Its own tests run with:

```sh
python3 -m unittest discover -s scripts/tests -t scripts
```
