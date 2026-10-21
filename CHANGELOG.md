# Changelog

> Orca-Android was developed with an AI coding assistant (Claude, Anthropic); see the
> [AI disclosure](README.md#ai-disclosure) in the README.

## Unreleased

- Favourites are grouped by settings page (Quality, Strength, Support, …) instead of one heading
  per option: in the print panel each page is a card that can be collapsed (showing how many
  options it holds or how many are changed), and number fields of a page share a row.
- Faster start: the workspace opens as soon as the profiles are loaded; the last session's models
  follow in the background ("Restoring last session…" over the view).
- Opening a project with other settings asks in a banner above the view instead of a dialog, so the
  project's models stay visible while choosing.
- Print panel without repeated labels: the printer and process fields no longer repeat their
  section title, "Choose printers" moved into the printer list, and all preset edit buttons use
  the same icon.
- Unit tests for app logic (favourites, picker lists, profile update hashes); the release script
  runs them before every build, and with `--device-tests` also the engine tests on the device.

## 0.1.8

- The preview only navigates: touching an object there no longer moves it and drops the slice.
- Slimmer layout, the same on phones and tablets: one top bar (plate, undo/redo, a menu for the
  view, plates and the project), a slim tool bar on the left edge of the view, and a summary line
  at the top of the panel with the main action (slice, or print and send). In the preview the
  layer slider runs along the right edge and the move slider along the bottom, without a card.
  On phones the collapsed sheet is just that summary line and the navigation bar is lower.
- No information shown twice: the summary line in Prepare shows the plate's state (objects,
  sliced or not) instead of repeating printer and presets, and the preview's details card leaves
  out time, filament total and layer count, which are shown in the summary line and at the slider.

## 0.1.7

- Favourites are grouped like the settings (e.g. "Supports", "Seam"), in the settings' order.
- A star on each settings group adds all of its options to the favourites at once.
- In the print panel, favourites that do not apply to the current values are hidden, so dependent
  options (e.g. support type and style) appear once their parent option is switched on.

## 0.1.6

- Opening a project whose printer, process, filaments or setting changes differ from the current
  ones asks whether to use the project's settings or keep the current ones.
- Favourite settings: star any option in the settings editor. Favourites form the first page of
  the editor and replace the fixed quick settings in the print panel (by default the same five
  options as before).
- Setting groups in the editor are collapsible and start collapsed; a collapsed group shows how
  many of its options are changed.

## 0.1.5

- The camera in the printer's web UI (Fluidd, Mainsail) works: the web view computed the CSS
  viewport height as 0, so the camera image – and Fluidd's navigation bar – had no height.

## 0.1.4

- Fixed a crash when opening the filament list for printers whose filaments come from both the
  vendor and the Orca filament library (e.g. Qidi): a group heading appeared twice. Selection
  lists now show each group once.

## 0.1.3

- Much lower battery use: the 3D view only draws when something changes (it used to redraw at the
  display's refresh rate all the time), the printer status is only polled while the app is
  visible, and the printer's web UI is unloaded in the background and reloaded on return.
- The progress notification of a running print checks the printer every 15 seconds instead of
  every 5.

## 0.1.2

- Profile updates download only the files that changed (seconds instead of minutes) and run in
  the background while the app stays usable.

## 0.1.1

- The plate, presets and setting changes are saved when the app goes to the background and come
  back on the next start.
- Device tab: a one-line status bar above the printer's web UI instead of a side column, so
  Mainsail/Fluidd get the full width.
- The printer status says why it is missing instead of waiting forever; the preview says when a
  connection (web UI only) cannot receive prints.
- Filament colours are the same on the plate, in the G-code, thumbnails and saved projects.
- Fixes from testing: object settings no longer count the filament assignment as a change,
  OrcaSlicer's error messages and units are translated, back from a preset comparison returns to
  the preset, the settings search gets the focus, single-key shortcuts no longer fire in editors,
  projects are named after the first object.

## 0.1.0 — first release

First public version, based on OrcaSlicer 2.5.0-dev (commit `2769b12`).

- OrcaSlicer's slicing engine and all printer, filament and process profiles, running on the
  device; printers are chosen by vendor, model and nozzle.
- Full settings editor with the desktop's pages, modes and option rules; user presets, import,
  export and comparison; profile updates from the OrcaSlicer repository.
- Prepare: plates, arrange, auto-orient, lay on face, transforms, cut, split, simplify, repair,
  measure, undo/redo; modifiers, blockers, enforcers and negative volumes; per-object, per-part and
  height-range settings; painting of supports, seams, fuzzy skin and colours; variable layer
  height; pauses, filament changes and custom G-code; shapes, text and SVG.
- All calibration tests of the desktop.
- 3MF projects compatible with the desktop, STL export, G-code and `.gcode.3mf` export.
- Toolpath preview with layer and move sliders, colour schemes, statistics and G-code view;
  external G-code files.
- Printing via Klipper/Moonraker, OctoPrint, PrusaLink, Duet, Repetier, ESP3D, MKS and Bambu Lab
  LAN; the printer's web interface in the Device tab; progress notification.
- Tablet and phone layouts, light and dark theme, English and German.
