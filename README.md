# OCaml IDEA Plugin

OCaml language support for IntelliJ-platform IDEs (developed against CLion), including Dune and opam.

This is a **personal fork** of [giraud/reasonml-idea-plugin](https://github.com/giraud/reasonml-idea-plugin),
which is in maintenance mode. Nothing here is intended to go back upstream.

Three things differ from upstream:

- **Semantic features come from `ocaml-lsp-server`,** not from this plugin's own PSI. See
  [Architecture](#architecture) — this is the big one, and it is why the plugin requires
  [LSP4IJ](https://plugins.jetbrains.com/plugin/23257-lsp4ij).
- **It is OCaml only.** Reason, ReScript, BuckleScript and Esy are no longer registered, so `.re`, `.rei`,
  `.res` and `.resi` files are left to whatever else handles them. Their sources and tests are still in the
  tree (see [Re-enabling a language](#re-enabling-a-language)), just not wired into `plugin.xml`.
- **It has a different plugin ID** (`com.andrewloebs.ocaml`, upstream is `reasonml`), so the IDE will not
  try to replace a locally built copy with the published upstream plugin.

Registered file types: `.ml`, `.mli`, `.ml4`, `.mlg`, `.mll`, `.mly`, `dune` / `dune-project` / `jbuild`,
and `.cmt`.

## Architecture

Upstream answers "what is this symbol, and what is its type?" by parsing OCaml into its own PSI, building
stub indexes over it, and resolving references itself. That works, but it means re-implementing the OCaml
name resolution and type rules inside a Java plugin, and keeping up as the language moves. Every fix in this
fork's early history was a symptom of that: the implicitly-opened module was looked up as `Pervasives`
(removed in OCaml 5.0), inferred types were read out of `.cmt` files under a name dune never produces, and a
parser change silently invalidated persisted stub trees.

So the semantic half is delegated to [ocaml-lsp-server](https://github.com/ocaml/ocaml-lsp), which is built
on merlin — the same engine tuareg and the VS Code extension use. **merlin gets its per-module compiler
flags from dune** (via `dune ocaml-merlin`), so wrapped-module naming, include paths and the implicit
`Stdlib` are dune's answer rather than something this plugin has to reconstruct.

What each side owns:

| This plugin | ocaml-lsp-server (via LSP4IJ) |
|---|---|
| Lexer, parser, PSI | Hover: types and documentation |
| Syntax highlighting, brace matching | Completion |
| Folding, structure view, commenter | Go to definition / implementation |
| Indentation on enter (`ocp-indent`) | Find usages, rename |
| Dune file support and build actions | Diagnostics (from merlin) |
| opam root / switch settings | Formatting (`ocamlformat`) |
| `.cmt` viewer (debug aid) | Signature help, inlay hints |

The extensions that used to answer the right-hand column are **unregistered, not deleted** — the sources are
still in the tree, and `plugin.xml` lists each one against the LSP feature that replaced it. Three
consequences worth knowing:

- **The stub and file indexes stay registered, and `ORStubVersions` still needs a bump when the parser
  changes.** The parser still builds stub-based PSI, and the platform's `Stubs` index calls `indexStub()`
  on every stub, which sinks into those extensions *by key* — unregistering one breaks indexing for every
  OCaml file rather than just disabling a feature. Nothing queries them for doc or completion any more, so
  they are cheap to keep, but they are not optional.
- **`RPsiUpperSymbol.getReference()` and `RPsiLowerSymbol.getReference()` return null.** References are part
  of the PSI, not an extension point, so unregistering providers did not stop resolution from running. They
  also take priority over the language server — a resolvable PSI reference is what the platform navigates
  with — so returning null is what makes ctrl-click actually reach merlin.
- **Rincewind is off the critical path.** merlin types from source against dependency `.cmi` files, so
  inferred types no longer need a `.cmt` for the file itself, and `dune build @check` is no longer required
  to see a type on hover. The rincewind plumbing survives only behind the `.cmt` viewer.

### Prerequisites

The plugin does not ship or install a language server. You need, in the selected opam switch:

```bash
opam install ocaml-lsp-server
```

and **LSP4IJ installed in the IDE** — it is a hard `<depends>`, so without it this plugin will not load at
all. The server is launched as `ocamllsp` through `opam exec`, inheriting the switch environment computed
from the opam settings, which is what puts `dune` on its `PATH`. A project must have been built (`dune
build`) at least once before merlin has any configuration to answer from.

The **LSP console** (View → Tool Windows → Language Servers) shows the server's lifecycle and every request,
and is the first place to look when a semantic feature returns nothing.

## Building

Any JDK **21 to 25** works. If none is installed, every JetBrains IDE ships one — point `JAVA_HOME` at its
bundled JBR:

| OS | Bundled JBR |
|---|---|
| Windows | `%LOCALAPPDATA%\Programs\CLion\jbr` |
| macOS | `/Applications/CLion.app/Contents/jbr/Contents/Home` |
| Linux | `<clion-install-dir>/jbr` |

Toolbox installations live elsewhere; on macOS that is
`~/Library/Application Support/JetBrains/Toolbox/apps/CLion/<version>/CLion.app/Contents/jbr/Contents/Home`.

Then:

```bash
JAVA_HOME=/Applications/CLion.app/Contents/jbr/Contents/Home ./gradlew buildPlugin
```

That writes `build/distributions/ocaml-idea-plugin-<version>-<platform>.zip`. Install it with
**Settings → Plugins → gear → Install Plugin from Disk…**, then restart the IDE.

Run the tests with `./gradlew test` (same `JAVA_HOME`).

Gradle must be **9.x**: 8.11 cannot run on Java 25 (`Unsupported class file major version 69`), and JetBrains
IDEs now bundle a Java 25 JBR. The wrapper is already pinned to 9.7.1.

## Per-machine setup

### opam

The plugin resolves the `opam` binary in this order: the **Opam executable** setting, then `PATH`, then a
list of well-known install locations. On macOS and Linux those are `~/.local/bin`, `~/bin`,
`/usr/local/bin`, `/opt/homebrew/bin`, `/opt/local/bin` and `/usr/bin`; on Windows the WinGet shim
directory, `WindowsApps`, `%LOCALAPPDATA%\Programs\opam`, `%LOCALAPPDATA%\opam`, `%ProgramFiles%\opam`
and `~\bin`.

`PATH` alone is not reliable. An IDE started from the Dock, Finder, the Windows shell or Toolbox inherits
that launcher's environment rather than your shell's, so a binary you can run in a terminal may still be
invisible to the IDE. **If opam is installed somewhere else, or the switch list comes up empty, set the path
explicitly** in Settings → Languages & Frameworks → **OCaml → Opam → Opam executable**. Leaving it blank
means auto-detect.

Set **Opam root location** in the same tab to your opam root (`~/.opam` on macOS and Linux,
`%LOCALAPPDATA%\opam` on native-Windows opam), then pick a switch. The configured root is passed to opam as
`--root`, so it is honoured rather than silently falling back to opam's own default.

On Windows only, WSL and Cygwin roots are also detected and commands are run inside them.

### ocp-indent (indentation while typing) — optional

Pressing enter in an OCaml file indents the new line using
[`ocp-indent`](https://github.com/OCamlPro/ocp-indent), taken from the selected opam switch:

```
opam install ocp-indent
```

The plugin runs `ocp-indent --numeric --lines=n-n` on the text above and including the caret's line, and
uses the column it reports. **The opam root and switch must be configured** (see above) — that is how the
binary is found, so with them unset nothing is indented. If ocp-indent isn't installed either, nothing
breaks: the editor falls back to keeping the previous line's indentation, and the attempt is retried at
most every 30 seconds so a missing binary doesn't cost a process per keystroke.

Every reason indentation gives up is logged under `format.ocaml.indent` in `idea.log`, so that is the first
place to look if enter leaves the caret in column 0.

`ocp-indent` reads a `.ocp-indent` file from the project root, so per-project indentation settings are
honoured. Indentation is the one editing feature deliberately **not** delegated to the language server: LSP
has no "indent this new line" request, only whole-range formatting, which is the wrong shape for pressing
enter.

Reformatting the whole file (ctrl+alt+L) still runs `ocamlformat`, but now through the language server
rather than this plugin's own post-format processor. It remains a separate tool with its own configuration,
and needs `opam install ocamlformat` plus an `.ocamlformat` file in the project.

### Rincewind — no longer needed

**Nothing in normal editing uses rincewind any more.** Inferred types come from merlin through the language
server, so you can skip this section entirely; it is kept only because the `.cmt` viewer still shells out to
the binary, and because the naming convention is impossible to guess.

Rincewind is a small OCaml binary that reads `.cmt` files to produce inferred type hints. **Upstream never
published a build newer than OCaml 4.14**, for any platform, so on any modern switch you have to build it
yourself.

To build it:

```bash
git clone https://github.com/giraud/rincewind
cd rincewind
opam exec -- dune build
```

OCaml 5.3 and newer need source changes; an `ocaml-5.5.patch` covering 5.5 lives alongside the local clone
at `~/projects/rincewind`. Its `cppo` guards are written as `>= 5.5` because that is the only compiler they
were tested against, so 5.3 and 5.4 will still fail to build until someone narrows them.

Then copy the binary in, under the **exact** name the plugin looks for:

```
rincewind_<os><ocaml-version>-<rincewind-version>.exe
```

- `<os>` is `w` on Windows, `o` on macOS, `l` on Linux
- `<ocaml-version>` is the switch's major.minor, e.g. `5.5`
- `<rincewind-version>` is `0.10` (only OCaml 4.02 uses `0.4`)
- the `.exe` suffix is part of the naming convention on **every** platform, macOS included

So a macOS OCaml 5.5 build is `rincewind_o5.5-0.10.exe`. It goes in the IDE's system directory:

| OS | Directory |
|---|---|
| Windows | `%LOCALAPPDATA%\JetBrains\<IDE><version>\ocaml` |
| macOS | `~/Library/Caches/JetBrains/<IDE><version>/ocaml` |
| Linux | `~/.cache/JetBrains/<IDE><version>/ocaml` |

On macOS and Linux, make it executable (`chmod +x`).

This directory is **per IDE version**, so upgrading e.g. CLion 2026.1 → 2026.2 needs the binary copied
across once. This is the only location checked — there are no fallbacks to older layouts.

## Backing out to PSI-based semantics

The PSI resolution layer was unregistered, not deleted, so going back is a `plugin.xml` edit rather than a
revert. The LSP section of `plugin.xml` lists every removed extension against the LSP feature that replaced
it; restore the ones you want and drop the matching `exclude` lines from the `test` block in `build.gradle`.

Three things to remember if you do:

- **Restore the two `getReference()` methods first.** `RPsiUpperSymbol` and `RPsiLowerSymbol` return null, so
  every provider that resolves a symbol will quietly find nothing until they hand back a real reference again.
  This is the piece that is *not* just a `plugin.xml` edit.
- **Bump `ORStubVersions.OCL_FILE` and `MODULE`, and `ORModuleResolutionPsiGist.VERSION`.** Stub trees on disk
  were built by whatever parser was current when they were written; querying them from a changed parser is the
  `UpToDateStubIndexMismatch` situation, and it shows up as a SEVERE blaming this plugin rather than as a
  wrong answer.
- **Expect duplicates.** Nothing suppresses the plugin's own providers while the language server is running,
  so re-registering completion or documentation gives you two of each. Remove the
  `com.redhat.devtools.lsp4ij` extensions block as well if you want the old behaviour back cleanly.

## Re-enabling a language

The Reason and ReScript parsers, PSI, tests and highlighting were **not deleted**, only unregistered. To
bring one back, restore its `<fileType>`, `<lang.parserDefinition>`, `<lang.ast.factory>` and
`<lang.syntaxHighlighterFactory>` entries in `src/main/resources/META-INF/plugin.xml`, plus whichever
per-language extensions you want, and drop the matching `exclude` lines from the `test` block in
`build.gradle`.

Note that `ORCodeFactory` now builds its throwaway rename PSI as OCaml rather than Reason, and the stub
element type holders for Reason and ReScript are still registered — they have to be created before index
initialization completes even though the languages are not.

The plugin also no longer touches `com.intellij.json`, which the platform split out into a separate plugin
in 2025.1: its classes are not on the classpath and referencing one throws `NoClassDefFoundError` at
runtime. The `ORConfigJsonFileType` that gave `bsconfig.json`/`rescript.json` a BuckleScript icon was
deleted for that reason. Restoring it means adding `<depends>com.intellij.modules.json</depends>` to
`plugin.xml` (and declaring the bundled plugin in `gradle.properties`), which raises the minimum platform
version — the file-name checks in `FileHelper` do not need any of that.

## License

MIT, as upstream. See [LICENSE](LICENSE).
