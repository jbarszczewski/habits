# Habits

An offline-only Android app for tracking recurring tasks — daily or on
specific weekdays — some with a time target (e.g. "practice singing, 30 min").
It records which days each task was done, partially done, or skipped, shows
statistics derived from that history, and provides a home-screen widget for
completing tasks and running timers without opening the app.

No accounts, no sync, no network. Single user, single device.

## Features

- Create tasks that recur daily or on chosen weekdays
- Plain checkbox tasks, or timed tasks with a target number of minutes
- A "Today" screen for marking tasks done and tracking timed progress
- A task editor with a weekday picker, optional target minutes, and
  archive/restore
- A home-screen widget (Jetpack Glance) showing today's tasks, with
  checkbox toggling and timer start/stop
- Statistics computed on the fly from completion history (streaks,
  completion rates, per-weekday rates, calendar heatmap) — nothing derived
  is stored
- A configurable day-start hour, so "today" doesn't have to mean midnight

Some of these (the timer's foreground service and the stats screen) are
still in progress — see [Build order](#build-order) below.

## Tech stack

- Kotlin, Jetpack Compose (Material 3), single-activity app
- [Room](https://developer.android.com/training/data-storage/room) for
  persistence
- [Jetpack Glance](https://developer.android.com/develop/ui/compose/glance)
  for the home-screen widget
- A foreground service for the running timer
- WorkManager for the daily midnight widget refresh
- Kotlin Coroutines + Flow
- Simple hand-written MVVM (no DI framework): `ui/` (screens + ViewModels),
  `data/` (Room + repository), `widget/`, `timer/`
- Min SDK 26, target SDK 36

## Project structure

```
app/src/main/java/com/jbarszczewski/habits/
├── data/           Room entities, DAOs, repository, DateProvider, stats calculators
├── ui/             Compose screens and ViewModels (today, editor, tasks, theme)
├── widget/         Glance widget, widget actions, and the daily refresh worker
├── AppContainer.kt Hand-written dependency container
└── MainActivity.kt Single activity hosting the Compose UI
```

## Data model

- **tasks** — name, a 7-bit weekday schedule mask, an optional target in
  minutes, timer state, and an archive date. Editing a task's schedule or
  target only affects future days; history is never rewritten.
- **completions** — one row per task per day (`DONE`, `PARTIAL`, or
  `SKIPPED`), with accumulated minutes for timed tasks. A scheduled day with
  no completion row is treated as a miss and is never stored explicitly.

Dates are local `LocalDate` values stored as ISO strings — never epoch
timestamps — and "today" always goes through a single `DateProvider.today()`
function that respects the configurable day-start hour.

All statistics (streaks, completion rates, minute totals) are computed from
`completions` and the task schedule at query time; nothing derived is
persisted.

## Build order

The app is being built incrementally, committing after each step:

1. Room entities, DAO, repository, `DateProvider` — done
2. Today screen (list, mark done, debug seed task) — done
3. Task editor (create/edit/archive, weekday picker, target minutes) — done
4. Glance home-screen widget — done
5. Timer + foreground service + notification, manual minute entry — in progress
6. Stats screen
7. Settings (day-start hour)

## Building and running

```bash
./gradlew assembleDebug        # build the debug APK
./gradlew testDebugUnitTest    # run unit tests
```

Open the project in Android Studio and run the `app` configuration, or
install the debug APK directly:

```bash
./gradlew installDebug
```

## License

MIT — see [LICENSE](LICENSE).
