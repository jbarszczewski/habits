# Habit Tracker — project guide for Claude Code

## What this is

An offline-only Android app for recurring tasks (daily or on specific weekdays),
some with a time target (e.g. "practice singing, 30 min"). It tracks which days
each task was done / skipped / missed and shows statistics. A home-screen widget
lets the user complete tasks and start/stop timers without opening the app.

No accounts, no sync, no network for app data. Single user, single device. The
one exception: on app start it checks GitHub Releases for a newer version and
shows a dismissible banner if one exists (see `update/`). This never sends or
syncs any user data.

## Stack (do not substitute)

- Kotlin, Jetpack Compose (Material 3), single-activity app
- Room for persistence
- Jetpack Glance for the widget
- Foreground service for the running timer
- WorkManager for the daily midnight widget refresh and the noon/4pm reminder check (both use
  the same self-rescheduling one-time-work pattern; no other background scheduling)
- Coroutines + Flow; no RxJava, no LiveData
- Min SDK 26, target latest stable
- Simple MVVM: `ui/` (screens + ViewModels), `data/` (Room + repository),
  `widget/`, `timer/`
- No dependency-injection framework; use a hand-written `AppContainer` in the
  Application class

The developer is new to Android. Prefer boring, well-documented approaches over
clever ones. Explain any non-obvious Android concept briefly in code comments
(e.g. why a foreground service is needed).

## Data model (source of truth — keep this exact)

```
tasks
  id             Long (PK, autogenerate)
  name           String
  days_mask      Int      -- 7 bits, bit 0 = Monday ... bit 6 = Sunday; 127 = every day
  target_minutes Int?     -- null = plain checkbox task
  notifications_enabled Int -- 0/1, default 1; per-task opt-out of the noon/4pm reminder
  timer_started_at Long?  -- epoch millis while a timer is running, else null
  created_at     String   -- ISO local date "yyyy-MM-dd"
  archived_at    String?  -- ISO local date; archived tasks keep their history

completions
  task_id        Long (FK -> tasks.id, cascade delete)
  date           String   -- ISO local date "yyyy-MM-dd"; PK is (task_id, date)
  status         Enum: DONE | PARTIAL | SKIPPED
  actual_minutes Int?     -- timed tasks only; accumulates across sessions in a day
```

Rules:

- Dates are LOCAL dates stored as ISO strings. Never store completion dates as
  epoch timestamps. Use `java.time.LocalDate`.
- "Today" starts at a configurable hour (`dayStartHour`, default 0, stored in
  DataStore). All "what is today" logic goes through one function:
  `DateProvider.today()`. Nothing else may call `LocalDate.now()` directly.
- One completions row per task per day. Timed tasks add minutes to the existing
  row rather than creating new rows.
- Timed task status: `actual_minutes >= target_minutes` -> DONE,
  `0 < actual < target` -> PARTIAL.
- A scheduled day with no completions row is a MISS. Do not store misses.
- Never store streaks, counts, or any derived stat. Compute them from
  completions + schedule at query time.
- Changing a task's `days_mask` or `target_minutes` affects future days only.
  Never rewrite or reinterpret history.

## Statistics (all derived)

- Per task: current streak, longest streak, done / partial / skipped / missed
  counts, completion rate (strict: DONE only; weighted: actual/target capped
  at 1)
- Per task, timed: total minutes per week/month, average session length
- Per weekday: completion rate across all tasks
- Calendar heatmap for the last N weeks

## Widget

- Shows today's scheduled tasks only. Keep the query small.
- Checkbox tasks: tap toggles DONE.
- Timed tasks: show `actual/target min` and a Start/Stop button.
- Any write from the widget goes through the same repository as the app, then
  calls `updateAll()` on the widget.
- While a timer runs, the foreground service refreshes the widget once a minute.

## Timer

- Starting a timer sets `timer_started_at` on the task and starts a foreground
  service with a persistent notification (Stop action in the notification).
- Elapsed time is always computed as `now - timer_started_at`; never keep it
  only in memory.
- Stopping adds elapsed minutes to today's completions row and clears
  `timer_started_at`.
- Manual minute entry must also be possible from the task detail screen.

## Notifications

- At 12:00 and 16:00 local wall-clock time, a WorkManager job checks which active,
  reminder-enabled tasks are scheduled today and not yet DONE or SKIPPED (a MISS with no
  completions row still counts as unfinished).
- If any are unfinished, show one grouped notification listing them (not one per habit).
  Tapping it opens the app.
- `Task.notifications_enabled` opts a single task out; there is no global on/off switch. Default
  is enabled. Editable from the add/edit screen alongside the other per-task fields.
- Requires runtime `POST_NOTIFICATIONS` permission on API 33+, requested once from
  `MainActivity`. If declined, the reminder check still runs but shows nothing.
- The job re-schedules its own next run after each check (same pattern as the widget's midnight
  refresh), so it needs no boot receiver.

## Build order (finish and run each step before starting the next)

1. Room entities, DAO, repository, `DateProvider`. Unit tests for streak and
   completion-rate calculations using fake data.
2. Today screen: list today's tasks with checkbox / progress; mark done. Seed
   one test task in debug builds.
3. Task editor: create/edit/archive, weekday picker, optional target minutes.
4. Glance widget for today's tasks.
5. Timer + foreground service + notification; manual minute entry.
6. Stats screen.
7. Settings: day-start hour.

## Conventions

- Commit after each build step with a message describing the step.
- Run `./gradlew assembleDebug` and `./gradlew testDebugUnitTest` before saying
  a step is complete.
- Don't add libraries beyond the stack above without asking.
- When a decision isn't covered here, ask rather than guess.
- Don't leave code in worktree uncommitted.
