# OCaml IDEA Plugin

OCaml language support for IntelliJ-platform IDEs (developed against CLion), including Dune and opam.

This is a **personal fork** of [giraud/reasonml-idea-plugin](https://github.com/giraud/reasonml-idea-plugin),
which is in maintenance mode. Nothing here is intended to go back upstream.

Two things differ from upstream:

- **It is OCaml only.** Reason, ReScript, BuckleScript and Esy are no longer registered, so `.re`, `.rei`,
  `.res` and `.resi` files are left to whatever else handles them. Their sources and tests are still in the
  tree (see [Re-enabling a language](#re-enabling-a-language)), just not wired into `plugin.xml`.
- **It has a different plugin ID** (`com.andrewloebs.ocaml`, upstream is `reasonml`), so the IDE will not
  try to replace a locally built copy with the published upstream plugin.

Registered file types: `.ml`, `.mli`, `.ml4`, `.mlg`, `.mll`, `.mly`, `dune` / `dune-project` / `jbuild`,
and `.cmt`.

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
honoured. Note that reformatting the whole file (ctrl+alt+L) still goes through `ocamlformat`, which is a
separate tool with its own configuration.

### Rincewind (inferred type hints) — must be built by hand

Rincewind is a small OCaml binary that reads `.cmt` files to produce inferred type hints. **Upstream never
published a build newer than OCaml 4.14**, for any platform, so on any modern switch you have to build it
yourself. This is the one piece of external setup that cannot be automated here.

Everything else — compiler errors and warnings, completion, navigation, hover documentation — is independent
of rincewind. Without it you get a single warning per session and no inferred type hints.

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
