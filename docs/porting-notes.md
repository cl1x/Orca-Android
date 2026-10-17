# Porting notes

How Orca-Android is put together, and the findings about OrcaSlicer's code that were expensive to
discover. Each item was verified in this port (by the test suite or by debugging on a device);
the file and function names refer to OrcaSlicer 2.5.0-dev, commit `2769b12`.

## Architecture

```
src-orca/                     OrcaSlicer, unmodified (git submodule)
android/deps/                 CMake superbuild of Boost, TBB, CGAL, OCCT, OpenCV, OpenVDB, … for arm64
android/core/                 Replaces OrcaSlicer's top-level CMakeLists: libslic3r + liborca_jni.so
  jni/orca_jni.cpp            JNI entry points: init, call(method, json), slice, viewGcode, cancel
  jni/EngineCalls.cpp         Table that maps call() method names to OrcaEngine functions
  jni/OrcaEngine.cpp          Presets, selection, slicing, G-code export
  jni/OrcaScene.cpp           Model, plates, transforms, arrange, cut, undo
  jni/OrcaTools.cpp           Picking, painting, variable layer height, custom G-code, option rules
  jni/OrcaExtras.cpp          Shapes, text, SVG, height ranges, calibration tests
  jni/OrcaProject.cpp         3MF projects, .gcode.3mf, STL export, preset import/export
  jni/Preview.cpp             Toolpaths → binary buffers for the OpenGL preview
  jni/Thumbnails.cpp          Software renderer for plate thumbnails in 3MF and G-code
android/app/                  Kotlin/Compose app, OpenGL ES 3 renderer, instrumented tests
android/scripts/              build.sh, build_release.sh, resource packing, extraction scripts
```

- **One generic JNI call.** Apart from slicing (which reports progress), every engine function is
  reached through `call(method, argsJson)` and returns JSON; errors come back as `{"error": …}` and
  become a Kotlin exception. Adding a function means one C++ method, one table entry and one line
  in `Engine.kt`.
- **One engine thread.** libslic3r objects are not thread-safe; `Engine.kt` runs every call on a
  single-thread dispatcher, and the C++ side additionally holds a mutex. Only `cancel` is called
  concurrently.
- **Desktop GUI code is reused without copying it by hand.** Three pieces of OrcaSlicer's GUI are
  extracted at build time: the settings pages and groups from `Tab.cpp`
  (`extract_settings_layout.py`), the rules for greyed-out/hidden options from
  `ConfigManipulation::toggle_print_fff_options` (`extract_option_toggles.py`, compiled against the
  small shims in `OptionToggles.hpp`), and the colour conversion of the flushing-volume calculator
  (`extract_color_space.py`). The calibration tests (`Plater::calib_*`) are ported by hand into
  `OrcaExtras.cpp`.

## Findings in OrcaSlicer

### Arranging

- **Items must start with `bed_idx = 0`.** `ArrangePolygon::bed_idx` defaults to `UNARRANGED`
  (-1), which has the same value as libnest2d's `BIN_ID_UNFIT`. The first-fit selection skips such
  items silently and returns them unarranged. `ArrangeJob.cpp` sets `ap.bed_idx = 0` when it
  prepares the items; without it, arrange does nothing at all.
- **The spacing only works through `inflation`.** `_arrange()` sets `min_obj_distance` to 0
  ("items are already inflated"). Distance between objects, the extruder clearance for sequential
  printing and brims are applied by `update_selected_items_inflation` /
  `update_unselected_items_inflation`, which need the print config. Without them objects are
  packed edge to edge, and sequential printing fails with "too close to others".
- **Degenerate outlines crash the nester.** `process_arrangeable` drops polygons with fewer than
  three points, which shifts the item order, and zero-area polygons reached `nfpConvexOnly` in a
  TBB worker, where the resulting exception crashed the process (seen under arm64 emulation). The
  port skips such objects and runs the nester with `parallel = false`, which is fast enough for a
  phone-sized scene.

### Cutting

- **The cut matrix is relative to the instance offset.** `Cut::perform_with_plane` transforms the
  mesh with the instance matrix *without* offset (`get_matrix_no_offset`), so the plane has to be
  given as `plane_center - instance_offset` (see `GLGizmoCut3D::get_cut_matrix`). With world
  coordinates the plane ends up outside the object and the "lower part" is the whole object.
- **Cut parts have no convex hull.** `add_cut_volume` creates volumes without calling
  `calculate_convex_hull()`. `ModelObject::ensure_on_bed()` and the arrange polygon are both
  computed from the convex hull, so cut parts float or sink and cannot be arranged until the hull
  is calculated.

### Presets

- **User presets need their folder before loading.** `PresetBundle::load_user_presets` returns
  early in read-only mode when `data_dir/user/default` does not exist, and then the collections
  have no directory; saving a user preset fails with
  `boost::filesystem::create_directories: Invalid argument`. The port creates the folder first.
- **Loaded presets must be marked visible.** The desktop hides printers that were not ticked in
  its setup wizard, and `select_preset_by_name` silently falls back to the first visible preset.
  Here the installed vendors already are the user's choice, so all loaded presets are visible.
- **Supports "manual" need painted enforcers**, and **organic tree supports** decide on their own
  where support is needed: on a sphere standing on the bed they generate none, on a Benchy they
  do. Both are OrcaSlicer's behaviour, not a porting bug. Likewise, brim types "auto", "painted"
  and "inner only" legitimately produce no brim on a small cube.

### Slicing settings that the desktop GUI fills in

These are not stored in presets; the desktop's GUI supplies them, so a port has to as well.

- **Every height range needs a `layer_height`.** `layer_height_profile_from_ranges` (Slicing.cpp)
  reads `option("layer_height")` without checking it; a range without one crashes slicing with a
  null pointer. The desktop's object list always adds the object's or the process's layer height
  to a new range.
- **The flushing matrix must match the filament count.** `flush_volumes_matrix` is part of the
  project in the desktop, which recalculates it from the colours whenever filaments are added or
  removed. With a preset's 1×1 matrix and two filaments, G-code export fails with "Flush volumes
  matrix do not match to the correct size!". The port recalculates it in the same way
  (`FlushVolCalculator`) when the size does not fit.
- **A colour change is a filament change.** `ProcessLayer::emit_custom_gcode_per_print_z` no longer
  emits anything for `CustomGCode::ColorChange` (the code is under `#if 0`, "BBS: inserting color
  gcode is removed"). The desktop changes to another filament slot instead (`ToolChange`), which
  runs the printer's `change_filament_gcode` (on many single-extruder printers an M600).
- **Plate types exist for every Bambu Lab printer**, not only when `support_multi_bed_types` is set:
  the desktop shows them for `PresetBundle::is_bbl_vendor()` too. Without a plate type for the plate,
  the printer's `default_bed_type` applies.
- **Input shaping calibration reuses `Calib_Params` fields.** In the frequency test, `start` is the
  fixed *damping* and `freqStartX/EndX` (and Y) is the frequency range; in the damping test,
  `start`/`end` is the damping range and `freqStartX`/`freqStartY` the fixed frequency.
  `GCodeWriter::set_input_shaping` rejects anything else ("Invalid input shaping parameters").

### Compiling libslic3r for Android

- `enum_bitmask`'s `operator|=` does not compile (it is unused upstream); use `a = a | b`.
- `FontProp prop(float(height))` is parsed as a function declaration; use braces.
- `NSVGLineParams` has no default constructor; pass the tessellation tolerance.
- `GCodeProcessorResult` is not copyable; bind `extract_result()` to an rvalue reference.
- The nanosvg implementation is defined in the GUI sources; `nanosvg_impl.cpp` provides it.
- `Hollowing.cpp` (SLA) needs OpenVDB even though SLA is not used, so OpenVDB, Blosc and OpenEXR
  are built.
- OpenSSL 1.1.1 (upstream's version) is not compatible with the AGPL; the port uses OpenSSL 3.
- nlohmann::json: `for (auto &[k, v] : j.value("key", json::object()).items())` iterates a destroyed
  temporary (only the `items()` proxy has its lifetime extended) and fails with
  "invalid_iterator.214 cannot get value" or worse. Bind the value to a variable first.
- MPFR's release tarball wants a specific automake version; `autoreconf -f -i` fixes it. OpenCV
  needs `BUILD_ANDROID_PROJECTS=OFF` and friends.

## Findings in the Android app

- **Compose's `onFocusChanged` fires once on composition** with "not focused". A text field that
  commits its value on focus loss must remember whether it ever had the focus – otherwise every
  number field writes its rounded display value back when it appears, which here changed the scene
  and threw away every slice result.
- **`GLSurfaceView` must not render continuously.** `RENDERMODE_CONTINUOUSLY` redraws at the
  display's refresh rate even when nothing moves and drained a phone battery in hours; the view
  renders on demand (`requestRender()` after touches and every change pushed to the GL thread).
- **Printer web UIs keep working in the background.** Mainsail and Fluidd hold a websocket with
  live temperatures; even a paused WebView keeps processing it. The device tab unloads the page
  when the app is stopped and loads it again on return.
- **Objects carry `extruder` in their config.** The filament assignment is stored like a setting
  override (`ModelObject::config`), so counting an object's "changed settings" has to leave it out,
  as the desktop's object list does.
- **Engine errors are translatable.** libslic3r throws messages wrapped in `_u8L`, which only mark
  them for translation; the app looks the text up in OrcaSlicer's catalog before showing it.
- **Android 17 requires `ACCESS_LOCAL_NETWORK`** to reach devices in the local network (printers);
  the app declares and requests it. The browser has it already, so "works in the browser, not in
  the app" points here.
- **Web UI addresses carry fragments.** Users paste `http://printer/#/` from the browser; the API
  base URL must drop `#…` and `?…`, or every request asks for the web page and fails to parse.
- **Moonraker behind nginx:** Mainsail, Fluidd and most printer vendors proxy the API on port 80;
  plain Moonraker listens on 7125. The app tries the given address and falls back to 7125.
