# Release notes

The notes of a release are not one file but a set of **fragments**, one per topic, under
`release-notes/<tag>/` (the tag of the release, e.g. `release-notes/v3.1.0/`).

Each fragment is a self-contained markdown file: it carries its own heading, and the file
name only decides the order it is published in (the numeric prefix). Splitting the notes
keeps unrelated topics in unrelated files, so a reworded section never collides with the
rest of the notes.

| File | What it holds |
|---|---|
| `00-summary.md` | The headline of the release: one or two sentences on what a user would notice. Mandatory — it is what the release page shows first, and what the in-app update dialog shows as the summary of the new version. |
| `10-….md`, `20-…` | One file per topic (`### Video playback`, `### Settings`, …). Delete the ones that do not apply to the release instead of leaving them empty. |

The release body published on GitHub is the concatenation of the fragments, **plus** an
automatically generated section that lists everything the commit history holds since the
previous release. The hand-written fragments tell the story; the generated section makes
sure nothing is missed.

## Cutting a release

```sh
# 1. Seed the fragments of the new release with the ones of the previous release.
python3 scripts/release_notes.py new v3.1.0

# 2. Write the notes: the headline in 00-summary.md, then one fragment per topic.
#    The seeded fragments are a starting point — delete the ones that do not apply.

# 3. Check them. Errors block the release, warnings do not.
python3 scripts/release_notes.py check v3.1.0

# 4. Preview the release body.
python3 scripts/release_notes.py build v3.1.0

# 5. Cut the release: the tag triggers the workflow, which publishes the assembled body.
gh release create v3.1.0 --target master
```

`new` writes a placeholder headline, and `check` refuses a release whose headline is still
the placeholder — a release never goes out with an empty summary. `check` also warns about
a fragment that is byte for byte the one of the previous release: it usually describes
something that did not change.

## The generated section

`build` appends a `## Changes since <previous tag>` section built from the commit history
between the previous release tag and this one:

- the commits are grouped by their conventional-commit type (`feat`, `fix`, `refactor`,
  `ci`, …); anything else lands in "Other changes";
- merge commits are skipped — they only repeat the title of the branch they brought in;
- a subject that references a pull request (`… (#42)`) links to it;
- the section ends with a link to the compare view of the two versions.

Only tags that are ancestors of the release count as its predecessor, so a tag cut from an
unrelated branch never becomes the baseline of the diff.

The section is generated, not written by hand: it is regenerated on every release and
never edited. The fragments above it are the curated part.

## Commands

| Command | What it does |
|---|---|
| `new <version>` | Creates `release-notes/<version>/`, seeded with the fragments of the previous release and a placeholder headline. |
| `build <version>` | Assembles the release body (fragments + generated section) and writes it to stdout, or to `--output <file>`. |
| `changelog <version>` | Prints only the generated section. |
| `check <version>` | Validates the fragments: a missing folder, a missing headline or a placeholder headline is an error; an empty or untouched fragment is a warning. |
| `list` | Lists the releases that have fragments. |

The tool only needs Python 3.8+ and git. Its own tests run with:

```sh
python3 -m unittest discover -s scripts/tests -t scripts
```
