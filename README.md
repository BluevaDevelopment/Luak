<p align="center">
  <img src="docs/media/logo.png" alt="Luak" width="760">
</p>

<p align="center">
  <strong>A Kotlin Multiplatform implementation of an embeddable Lua 5.5.1 runtime.</strong>
</p>

<p align="center">
  <img alt="Version" src="https://img.shields.io/badge/version-26.7-blue">
  <img alt="Kotlin Multiplatform" src="https://img.shields.io/badge/Kotlin_Multiplatform-JVM_%7C_JS_%7C_Wasm_%7C_Native-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.4.10-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Gradle" src="https://img.shields.io/badge/Gradle-9.6.1-02303A?logo=gradle&logoColor=white">
  <img alt="JVM" src="https://img.shields.io/badge/JVM-17+-ED8B00?logo=openjdk&logoColor=white">
  <img alt="Lua" src="https://img.shields.io/badge/Lua-5.5.1-000080?logo=lua&logoColor=white">
  <img alt="License" src="https://img.shields.io/badge/license-MIT-green">
</p>

## Overview

Luak is a Kotlin-first implementation of an embeddable Lua runtime, built as a **Kotlin Multiplatform** library. Its shared module currently targets:

- **JVM 17+**
- **JavaScript IR**, tested on Node.js
- **WebAssembly**, tested on Node.js
- **Kotlin/Native** for Linux x64, Linux ARM64, Windows x64, macOS x64, and macOS ARM64

The Lua runtime, value model, bytecode compiler, and standard libraries live in `commonMain`. JVM-specific integration is isolated from the shared runtime.

Luak currently implements **Lua 5.5.1** and provides:

- An embeddable Lua VM written entirely in Kotlin.
- Lua bytecode compilation and execution across the configured KMP targets.
- Tables, metatables, functions, coroutines, and Lua 5.5.1 standard libraries.
- `LuaPlatform.standardGlobals()`, one entry point that builds a fully loaded `Globals` on every target.
- A shared `io` library (`io.open`, `io.lines`, `io.tmpfile`, file handles, `os.remove`/`rename`/`tmpname`) on every target, not just the JVM.
- Shared tests for the runtime, compiler, and libraries across KMP targets.
- JVM integrations for processes, Java reflection, script engines, and `luajava`.

## Multiplatform Architecture

| Source set or module | Purpose |
|---|---|
| [`luak-core/src/commonMain/kotlin/`](luak-core/src/commonMain/kotlin/) | Shared Lua runtime, compiler, and libraries |
| [`luak-core/src/jvmMain/kotlin/`](luak-core/src/jvmMain/kotlin/) | JVM implementations of platform abstractions |
| [`luak-core/src/nonJvmMain/kotlin/`](luak-core/src/nonJvmMain/kotlin/) | Portable implementations shared by JavaScript and Wasm |
| [`luak-core/src/jsHostMain/kotlin/`](luak-core/src/jsHostMain/kotlin/) | JavaScript-host implementations (`node:fs`, `process`) for the JS and Wasm-JS targets |
| [`luak-core/src/wasmWasiMain/kotlin/`](luak-core/src/wasmWasiMain/kotlin/) | WASI implementations over raw `wasi_snapshot_preview1` syscalls |
| [`luak-core/src/nativeMain/kotlin/`](luak-core/src/nativeMain/kotlin/) | Kotlin/Native implementations of platform abstractions |
| [`luak-core/src/nativePosixMain/kotlin/`](luak-core/src/nativePosixMain/kotlin/) | 64-bit file offsets for Linux and macOS |
| [`luak-core/src/nativeWindowsMain/kotlin/`](luak-core/src/nativeWindowsMain/kotlin/) | 64-bit file offsets for Windows |
| [`luak-core/src/commonTest/kotlin/`](luak-core/src/commonTest/kotlin/) | Tests shared by all core targets |
| [`luak-jvm/src/main/kotlin/`](luak-jvm/src/main/kotlin/) | JVM-only integrations and command-line tooling |
| [`examples/`](examples/) | Kotlin and Lua usage examples |

Gradle modules:

| Module | Targets | Purpose |
|---|---|---|
| `luak-core` | JVM, JavaScript IR, Wasm, Kotlin/Native | Multiplatform Lua runtime, compiler, and libraries |
| `luak-jvm` | JVM | JVM platform adapters, `luajava`, scripting, CLI, and JIT support |

Platform-dependent functionality is exposed through `expect`/`actual` implementations. Code intended to run on every target belongs in `commonMain`; Java and JVM APIs remain confined to JVM source sets and `luak-jvm`. No type in the public `commonMain` API is platform-specific.

The host surface every shared library is built on is deliberately small: console streams, resource lookup, a random-access file handle, delete/rename/temp-name, environment variables, exit, GC, and weak references. Everything else (the value model, the compiler, and all nine standard libraries) is shared code.

## Installation

Releases publish to [repo.blueva.net](https://repo.blueva.net/releases), a public Maven repository, so no authentication is needed to depend on Luak.

### JVM projects

Two artifacts are available. Pick one:

| Artifact | Contains | Use it when |
|---|---|---|
| `luak-jvm` | The multiplatform core (as a compile dependency) plus `JvmPlatform.standardGlobals()`, `luajava`, `io.popen`/`os.execute`, the `luajc` JIT compiler, CLI tooling, and `javax.script` integration | You want a ready-to-use Lua runtime, the common case |
| `luak-core-jvm` | Just the shared runtime, compiler, and standard libraries on the JVM target, including `LuaPlatform.standardGlobals()`, but without `luajava`, `io.popen`, `os.execute`, or the JIT | You don't need the JVM-only integrations, or want the smallest possible footprint |

`luak-jvm` pulls in `luak-core-jvm` transitively, so depending on it alone is enough for most projects.

**Gradle (Kotlin DSL)**

```kotlin
repositories {
    maven("https://repo.blueva.net/releases")
}

dependencies {
    implementation("net.blueva:luak-jvm:26.7")
}
```

**Maven**

```xml
<repositories>
  <repository>
    <id>blueva</id>
    <url>https://repo.blueva.net/releases</url>
  </repository>
</repositories>

<dependency>
  <groupId>net.blueva</groupId>
  <artifactId>luak-jvm</artifactId>
  <version>26.7</version>
</dependency>
```

### Other Kotlin Multiplatform targets

`luak-core` is only distributed as a Kotlin Multiplatform library: every non-JVM target is a Kotlin `.klib`, consumable from another Kotlin Multiplatform Gradle project. It is not a raw JS/npm package, and not a C-callable Native library.

`LuaPlatform.standardGlobals()` works on every target, so no target needs a hand-assembled `Globals`:

```kotlin
import net.blueva.luak.lib.LuaPlatform

val globals = LuaPlatform.standardGlobals()
globals.load("print('hello, world')")!!.call()
```

`LuaPlatform.debugGlobals()` adds the `debug` library. Loading the individual classes in `net.blueva.luak.lib` (`BaseLib`, `PackageLib`, `StringLib`, `TableLib`, `MathLib`, `CoroutineLib`, `OsLib`, `IoLib`, `Bit32Lib`) by hand remains available when you want a smaller footprint.

Add the `repo.blueva.net/releases` repository shown above at the project level, then depend on the shared `net.blueva:luak-core:26.7

| Target | Gradle target function | Source set | Tested on |
|---|---|---|---|
| JavaScript IR | `js { nodejs() }` | `jsMain` | Node.js |
| WebAssembly | `wasmJs { nodejs() }` | `wasmJsMain` | Node.js |
| WebAssembly (WASI) | `wasmWasi { nodejs() }` | `wasmWasiMain` | Node.js's experimental `node:wasi` |
| Kotlin/Native | `linuxX64()`, `linuxArm64()`, `mingwX64()`, `macosX64()`, `macosArm64()` | `linuxX64Main`, `linuxArm64Main`, `mingwX64Main`, `macosX64Main`, `macosArm64Main` | Matching GitHub Actions runners in CI |

```kotlin
repositories {
    maven("https://repo.blueva.net/releases")
}

kotlin {
    js { nodejs() }
    wasmJs { nodejs() }
    wasmWasi { nodejs() }
    linuxX64()
    linuxArm64()
    macosArm64()

    sourceSets {
        commonMain {
            dependencies {
                implementation("net.blueva:luak-core:26.7")
            }
        }
    }
}
```

## Sandboxing Untrusted Code

A host that runs plugins it did not write needs bounds the plugin cannot lift. These live on `Globals`, are off by default, and cost next to nothing when unused.

```kotlin
val globals = LuaPlatform.standardGlobals()

globals.budget = Budget().apply { instructions = 10_000_000 }  // per resumption
globals.memoryceiling = 64L * 1024 * 1024                      // bytes
globals.textonly = true                                        // no binary chunks
globals.seedrandom(laneId, runId)                              // reproducible draws

globals.bind(plugin).call()
```

| Bound | What it does | What it costs when unused |
|---|---|---|
| `Globals.budget` | Stops a resumption that runs too long, or one a watchdog calls `Budget.interrupt()` on, with an ordinary Lua error. Needs no `debug` library, unlike `debug.sethook`. | One null check per instruction |
| `Globals.memoryceiling` | Raises Lua's own `not enough memory` past a cap on what the state has been charged for. Each state counts its own objects, so one lane cannot spend another's. | Nothing |
| `Globals.textonly` | Refuses binary chunks through every route into the loader — `load`, `loadfile`, `dofile`, `require`, and the host's own `Globals.load`. The undumper reads a format, not a language, and a malformed dump is not something checking makes safe. | Nothing |
| `Globals.seedrandom(x, y)` | Fixes `math.random`'s sequence from outside Lua. Unseeded states already differ from one another; this is for repeating a run. | Nothing |

Both ceilings stay reached once they are: catching the error and carrying on meets it again, so only the host — through `Budget.refill()`, which the next resumption does anyway, or `Globals.startmemorycount()` — puts the state back to work.

### Starting a lane cheaply

Compiling the plugin is what a new lane costs, not building the standard libraries: for a 200-function chunk on the JVM that is 1094 µs against 61 µs. Compile it once and hand it to each lane, which a `Prototype` is safe for — it is finished code and constants, never written to again.

```kotlin
val plugin: Prototype = template.compile(source, "@plugin.lua")
for (lane in lanes) lane.bind(plugin).call()
```

A whole lane costs 34 µs that way against 901 µs compiling per lane. Sharing the *environment* instead is not on offer, and deliberately: `math.random` carries a generator, `io` carries open files, and `package` carries what has been required, so a lane reaching into another's would be no sandbox at all.

## Building

Build every target and module from a clean checkout:

```bash
./gradlew clean build
```

Build only the multiplatform core:

```bash
./gradlew :luak-core:build
```

Compile an individual target:

```bash
./gradlew :luak-core:compileKotlinJvm
./gradlew :luak-core:compileKotlinJs
./gradlew :luak-core:compileKotlinWasmJs
./gradlew :luak-core:compileKotlinMacosArm64
```

## Testing

Run every test suite available on the current host:

```bash
./gradlew :luak-core:allTests
```

Run an individual target suite:

```bash
./gradlew :luak-core:jvmTest
./gradlew :luak-core:jsNodeTest
./gradlew :luak-core:wasmJsNodeTest
./gradlew :luak-core:wasmWasiNodeTest
./gradlew :luak-core:macosArm64Test
```

Native tests can only run on their matching host. Cross-platform Native compilation remains available from supported hosts. 

## Requirements

| Component | Requirement |
|---|---|
| JDK | 17 or later, for Gradle and JVM targets |
| Kotlin | 2.4.10 |
| Gradle | 9.6.1 through the included wrapper |
| Node.js | Used for JavaScript and Wasm tests; managed by the Kotlin Gradle plugin |
| Native toolchain | Required only to link or run Kotlin/Native binaries on the host |

Use the included wrapper rather than a system Gradle installation.

## Platform Support and Limitations

The shared runtime, compiler, and standard libraries behave identically on every target. What differs is what the *host* can provide, and Luak reports those gaps the way Lua does, returning `nil` plus a message or raising an ordinary Lua error, rather than omitting functions:

| Capability | JVM | Kotlin/Native | JavaScript / Wasm-JS | Wasm-WASI |
|---|---|---|---|---|
| Files (`io.open`, `io.lines`, `os.remove`, `os.rename`) | Yes | Yes, POSIX `stdio` | Yes under Node (`node:fs`); unavailable in a browser | Yes, limited to the directories the host pre-opens |
| Script lookup (`require`, `dofile`) | Filesystem, then classpath | Filesystem | Filesystem under Node | Pre-opened directories |
| `os.getenv` | Environment, then system properties | `getenv` | `process.env` under Node | WASI `environ_get` |
| `io.popen`, `os.execute` | Yes | No (no portable process API) | No | No |
| `package.loadlib` | Yes | No | No | No |
| Weak tables (`__mode`) | Yes | Yes | No (no weak references in the host) | No |
| `os.date` / `os.time` | UTC | UTC | UTC | UTC |

Where a host grants no filesystem at all, `io.open` returns `nil` and a message and the rest of the library keeps working. `io.popen` behaves the same way outside `luak-jvm`.

## License

Luak is distributed under the [MIT License](LICENSE).
