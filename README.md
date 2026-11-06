# Anu Keystone v0.01

Keystone is a free, offline-first "second brain" for your whole life: tasks and projects (PARA),
habits, journaling, sleep, goals and life setup, plus 9 life hubs covering health, food, career,
money, learning, home & car, people & love, and leisure & travel. Everything is stored on your phone
in SQLite. No account, no network, no tracking.

License: **GPL-3.0-or-later** · Copyright (C) 2026 Tylerlikesps4 · see `CREDITS.md` for third-party licenses.

## How it works

Keystone is built on one small, tested engine (`engine/`, pure Kotlin) that behaves like a tiny Notion:

- **Collections** with typed fields: text, long text, number, currency, date, time, select, multi-select,
  checkbox, relation, rating, URL, email, phone, file, image, progress, **rollup** and **formula**.
- **Views**: list, board (kanban), calendar, gallery, table and stats (bar/column/line charts).
  Views have filters (incl. "due within N days", "this month", "today") and sorts.
- **PARA everywhere**: every collection automatically gets *Area*, *Project* and *Tags* relations.
  Open any project/area/tag to see everything linked to it (backlinks).
- **Rollups** (project progress from tasks, learning progress from resources, service spend per car…)
  and **formulas** (`pct(saved,target)`, `hours_between(bed,wake)`, `days_until(expires)`,
  `age(birthday)`, `days_until_annual(birthday)`, `monthly(price,cycle)`, …).
- **Recurring items**: repeating tasks and home maintenance respawn when marked done.
- **Periodic notes**: Day → Month → Year notes are created and linked automatically.
- **Reminders**: any date field flagged `remind` (birthdays yearly, warranties, renewals, car service,
  follow-ups, deadlines) feeds the Upcoming widget and an optional daily notification (WorkManager).
- **Backup**: full JSON export/import (replace or merge, newer edit wins), CSV export per collection.
- **Create your own**: new collections, fields and views from inside the app.

Each hub is just a preset schema + views + dashboard widgets on that engine.

## Features

| Area | Collections / screens |
|---|---|
| Life Setup | Wheel of Life (radar chart), Core Values, End & Means goals, Vision statement, onboarding wizard |
| Dashboards | Simple, Command Center (full), Mobile, My Dashboard (custom) + 100+ widget catalog (22 widget types × collections) |
| Basics | Tasks (today/upcoming/board/calendar), Projects, Areas, Resources Manager (Collections → Resources with learning progress), Archive, Tags, Habits & daily tracker (streaks), Days/Months/Years notes, Journal, Sleep tracker, Setup guide, Search |
| Health & Fitness | Workouts, Exercise library (PBs), Body metrics (weight trend) |
| Food & Meals | Recipes, Meal planner, Grocery list |
| Career | Job applications, Professional CRM, Interactions, CV (experience, education, skills) + CV builder/share |
| Finance | Transactions, Budgets (auto spent vs limit), Savings goals, Subscriptions |
| Learning & Growth | Learning (courses/skills), Reading tracker, Vocabulary + flashcards |
| Home & Car | Home maintenance & improvement, Home inventory, Vehicles, Car service log, Warranties |
| People & Love | People CRM & birthdays, Love hub, Gift ideas, Wishlist, Bucket list |
| Leisure & Travel | Travel planner, Itinerary & packing, Series & movies, Video games library |

## Build

Requirements: JDK 17, Android SDK (platform 35, build-tools 35).

```bash
./gradlew :engine:test           # engine unit tests
./gradlew :app:assembleRelease   # APK in app/build/outputs/apk/release/
```

Release signing reads `keys/keystore.properties` (never committed):

```
storeFile=keys/keystone-release.jks
storePassword=…
keyAlias=keystone
keyPassword=…
```

Without it, `assembleRelease` produces an unsigned APK. CI (`.github/workflows/build.yml`) runs the tests and
builds the APK; add `KEYSTORE_BASE64` and `KEYSTORE_PASSWORD` repository secrets to sign it.

## Project layout

```
engine/   pure-Kotlin engine: Model, Engine (CRUD, coercion, relations, rollups, views, stats,
          periodic notes, habits, reminders, backup), Formula, Presets (all hubs), Insights; JUnit tests
app/      Android app (Jetpack Compose, Material 3): SQLite store, screens, widgets, reminders
```

Keystone is an independent project and is not affiliated with Notion or any Notion template vendor.
