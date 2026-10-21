<div align="center">
  <img src="docs/icon.svg" alt="Logo" width="80" height="80">
  <h2>Orca-Android</h2>
  <p>
    <a href="https://github.com/SoftFever/OrcaSlicer">OrcaSlicer</a> for Android tablets and
    phones – the real slicing engine, running on the device.
  </p>
  <p>
    <a href="https://github.com/cl1x/Orca-Android/releases">Download</a>
    ·
    <a href="https://github.com/cl1x/Orca-Android/issues">Report a bug</a>
  </p>
</div>

> **Orca-Android is an unofficial port of [OrcaSlicer](https://github.com/SoftFever/OrcaSlicer)
> by SoftFever and the OrcaSlicer contributors.** Everything that makes a good print – the
> slicing engine, the profiles and the calibration know-how – is their work; this port adds an
> Android app around it. It is not affiliated with OrcaSlicer or any printer manufacturer; please
> report problems with this port here, not upstream.

> **A personal project, shared as is.** I built this for my own use and publish it in case it is
> useful to someone else. There is no support, no promise of maintenance or updates, and no
> warranty – check your G-code before long prints and use it at your own risk (see
> [License](#license)).

## Credits

Orca-Android would not exist without **[OrcaSlicer](https://github.com/SoftFever/OrcaSlicer)**.
SoftFever and the OrcaSlicer contributors built the slicer: Arachne walls, tree and organic
supports, seams, the calibration tests, the hundreds of carefully tuned printer, filament and
process profiles, and the translators made it available in more than twenty languages. OrcaSlicer
itself builds on **[Bambu Studio](https://github.com/bambulab/BambuStudio)**,
**[PrusaSlicer](https://github.com/prusa3d/PrusaSlicer)** and
**[Slic3r](https://github.com/slic3r/Slic3r)** by Alessandro Ranellucci and contributors. This
port only repackages that work for Android – many thanks to all of them! If you want to support
the original project, please do so there:
[OrcaSlicer](https://github.com/SoftFever/OrcaSlicer) ·
[orcaslicer.com](https://www.orcaslicer.com) ·
[sponsor SoftFever](https://github.com/sponsors/SoftFever).

## Why this port

I wanted to slice directly on my Android tablet – with the same profiles and results as
OrcaSlicer on the desktop – and send the job to the printer from there. There is no server
involved: OrcaSlicer's engine (`libslic3r`) is compiled for Android and runs on the device.

## What it does

Based on **OrcaSlicer 2.5.0-dev** (commit `2769b12`, see
[android/UPSTREAM.md](android/UPSTREAM.md)). The desktop user interface (wxWidgets) cannot run on
Android, so the app has a new touch interface; the slicing itself is OrcaSlicer's unchanged code.

### Printers and profiles

- **All printer, filament and process profiles of OrcaSlicer.** Pick your printers by vendor,
  model and nozzle; only those vendors' profiles are installed. Profile updates can be fetched
  from the OrcaSlicer repository.
- **Full settings editor** with the desktop's pages and groups, search, simple/advanced/expert
  modes and the desktop's rules for greyed-out and hidden options. Save, delete, export, import
  (also `.orca_printer` / `.orca_filament` bundles) and compare presets. Labels and tooltips use
  OrcaSlicer's translations.
- Several filaments with colours, flushing volumes and prime tower; plate types (Bambu Lab).

### Prepare

- Load **STL, 3MF (projects with plates and settings), OBJ, STEP, AMF, DRC and SVG** – also via
  "Open with" and "Share" from other apps. Add shapes, **embossed text**, SVGs and the Orca sample
  models.
- Multiple **plates**, arrange, auto-orient, lay on face, move/rotate/scale/mirror, copies,
  **cut**, split, simplify, repair, measure, undo/redo.
- Parts, modifiers, support blockers/enforcers and negative volumes; **per-object, per-part and
  height-range settings**; filament per object.
- **Painting** of supports, seams, fuzzy skin and colours.
- **Variable layer height** (adaptive, smooth, manual).
- Pauses, filament changes and custom G-code at any height.
- **Calibration tests** of the desktop: temperature tower, flow rate, pressure advance (line and
  tower), retraction, max volumetric speed, VFA, input shaping and cornering.
- Save and open projects (3MF, compatible with the desktop), export STL.

### Preview and printing

- Slicing in the background with notification, cancel and "slice all plates".
- Toolpath preview with layer and move sliders, colour schemes (line type, speed, height, width,
  fan, temperature, flow, filament), travels, retractions and seams, and the **G-code** of the
  current move. Print time, filament, cost and per-type statistics; open external G-code files.
- **Device tab** with the printer's own web interface (Mainsail, Fluidd, OctoPrint, …) and the job
  status with pause/resume/cancel. Upload and start prints on **Klipper/Moonraker, OctoPrint,
  PrusaLink, Duet, Repetier, ESP3D, MKS and Bambu Lab (LAN mode)**; finds printers in the network;
  print progress as a notification.
- Save or share the G-code or the sliced plate as `.gcode.3mf`.

### App

- Tablet layout with side panel, phone layout with bottom sheet; keyboard and mouse shortcuts.
- The plate, presets and setting changes are kept when the app is closed and come back on the next start.
- Light and dark theme, optional Material You colours. English and German interface; option
  labels in all languages OrcaSlicer is translated to.

### Not included

SLA printing, the CAD tab, the Python plugins, Bambu cloud features, and anything that needs the
desktop's network plugin.

## AI disclosure

This port was developed with **Claude (Anthropic), an AI coding assistant**. I (cl1x) defined the
requirements, made the design decisions and tested the app on my devices and printers; Claude wrote
most of the code – the Android build of the native libraries, the JNI bridge, the app and the
tests. OrcaSlicer's own code is used unchanged. Commits are marked with `Co-Authored-By: Claude`.
Please review the code with this in mind and report anything that looks wrong.

What is tested: an instrumented test suite
([EngineTest.kt](android/app/src/androidTest/java/app/orcaandroid/EngineTest.kt)) slices on a real
Android runtime with printers of five vendors, every sparse and surface infill pattern, walls,
seams, supports, brims, ironing, vase mode, multi-material, custom G-code, all calibration tests,
projects and presets. Unit tests
([AppLogicTest.kt](android/app/src/test/java/app/orcaandroid/AppLogicTest.kt)) cover app logic
that needs no device, such as the grouping of favourites and the profile update checks. The app was tried on an Android tablet and in the Android emulator, connected
to a Klipper printer (Qidi Q1 Pro).
What is not tested: Bambu Lab LAN printing (no printer available), and many printer/profile
combinations.

## Installation

Download the APK from the [releases](https://github.com/cl1x/Orca-Android/releases) and open it
(allow installing apps from this source when asked). Requires **Android 10 or newer on an arm64
device** – virtually every tablet and phone of the last years.

On first start, choose your printers; only their profiles are unpacked on the device. To send
prints, open the **Device** tab and enter the printer's address (Android 17 asks for permission to
access the local network).

## Building

Requirements: Linux, Android SDK with NDK 28.2, CMake 3.20+, Ninja, JDK 17+, Python 3, and
autotools and perl (for GMP, MPFR and OpenSSL).

```bash
git clone --recursive https://github.com/cl1x/Orca-Android.git
cd Orca-Android/android
scripts/build.sh debug
```

The first run builds all native dependencies (about an hour); outputs go to
`~/build/orca-android` (override with `ORCA_BUILD_ROOT`). The APK ends up in
`~/build/orca-android/gradle/app/outputs/apk/`.

Signed release builds: create a keystore and `android/key.properties` (`storeFile`,
`storePassword`, `keyAlias`, `keyPassword`; never commit either of them), then run
`scripts/build_release.sh`. It runs the unit tests first (with `--device-tests` also the engine
tests on the connected device) and writes the APK to `dist/`. With `--publish` it also creates the
GitHub release for the tag `v<version>` on the current commit, with the version's CHANGELOG
section as notes (needs the [GitHub CLI](https://cli.github.com), logged in with `gh auth login`;
the repository is set by `ORCA_GITHUB_REPO`).

Tests: unit tests on the build machine, engine tests on a connected device or an arm64-capable
emulator (Gradle uninstalls the debug app afterwards):

```bash
cd android && ./gradlew testDebugUnitTest
cd android && ./gradlew connectedDebugAndroidTest
```

How the port is put together, and what to check when updating to a newer OrcaSlicer:
[docs/porting-notes.md](docs/porting-notes.md) and [android/UPSTREAM.md](android/UPSTREAM.md).
Changes per version: [CHANGELOG.md](CHANGELOG.md).

## Repository layout

| Path | Content |
|---|---|
| `src-orca/` | OrcaSlicer, unmodified (git submodule) |
| `android/deps/` | CMake superbuild of the native dependencies for Android arm64 |
| `android/core/` | Builds `libslic3r` and the JNI bridge `liborca_jni.so` |
| `android/app/` | The Android app (Kotlin, Jetpack Compose, OpenGL ES 3) and its tests |
| `android/scripts/` | Build, release, resource packing and test helpers |
| `docs/` | Notes on how the port works |

## Contributing and forks

Pull requests are welcome, but please don't expect quick reviews – this is a spare time project
for my own use. You are just as welcome to fork it and continue on your own. Problems of the
slicing itself (wrong toolpaths, profiles) are best reported
[upstream](https://github.com/SoftFever/OrcaSlicer/issues), after checking that they also occur in
the desktop version.

The app's own strings exist in English and German; option names and tooltips come from
OrcaSlicer's translations.

## License

Orca-Android is licensed under the [GNU Affero General Public License v3.0](LICENSE), like
OrcaSlicer. As required by the license: this program contains OrcaSlicer 2.5.0-dev (commit
`2769b12`), unmodified, combined with an Android user interface and build system written in
September 2026 as described above. The components it bundles and their licenses are listed in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

This program is distributed in the hope that it will be useful, but **without any warranty**;
without even the implied warranty of merchantability or fitness for a particular purpose. See the
license for details.

"OrcaSlicer" and printer brand names belong to their respective owners.
