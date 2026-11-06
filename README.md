# Anu Keystone v0.01

**Your whole life in one private, offline app. Tasks, habits, money, goals & more.**

[![Build](https://github.com/Tylerlikesps4/Anu-Keystone/actions/workflows/build.yml/badge.svg)](https://github.com/Tylerlikesps4/Anu-Keystone/actions/workflows/build.yml)
![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84)
![License](https://img.shields.io/badge/license-GPL--3.0--or--later-blue)
![Version](https://img.shields.io/badge/version-0.01-orange)

Anu Keystone is a free, open-source second brain for your whole life. It runs entirely on your phone, with no account, no internet and no tracking.

Start with the Life Setup wizard: rate your Wheel of Life, pick your core values, write your vision and set your goals. Then Anu Keystone builds your dashboard around you.

---

## Contents

- [Features](#features)
- [Download & install](#download--install)
- [Getting started](#getting-started)
- [Privacy](#privacy)
- [How it works](#how-it-works)
- [Formulas](#formulas)
- [Building from source](#building-from-source)
- [Project layout](#project-layout)
- [Roadmap](#roadmap)
- [Known limitations](#known-limitations)
- [Contributing](#contributing)
- [FAQ](#faq)
- [License](#license)

---

## Features

### Everything in one place
- **Tasks and projects**, organized by Areas, Projects and Tags
- **Habits** with streaks, a **journal**, a **sleep tracker** and daily, monthly and yearly notes
- **Health:** workouts, an exercise library with personal bests, and body metrics
- **Food:** recipes, a meal planner and a grocery list
- **Money:** transactions, budgets, savings goals and subscriptions
- **Career:** job applications, a professional CRM and a CV builder
- **Learning:** courses, a reading tracker and vocabulary flashcards
- **Home and car:** maintenance, inventory, vehicles, service logs and warranties
- **People:** birthdays, gift ideas, a wishlist and a bucket list
- **Leisure:** travel plans, packing lists, shows, movies and your games library

### Make it yours
- 18 field types, including relations, rollups and formulas
- 6 ways to view anything: list, board, calendar, gallery, table and charts
- 4 dashboards with 22 widget types
- Create your own collections, fields and views
- Repeating tasks and reminders for birthdays, renewals, deadlines and more
- An optional daily notification with your day at a glance
- Dark, light or system theme (with Material You colours on Android 12+)

### Your data stays yours
- Stored only on your device
- Full backup and restore, plus CSV export
- Free software under the GPL-3.0-or-later licence

### All hubs at a glance

| Hub | Collections / screens |
|---|---|
| Life Setup | Wheel of Life (radar chart), Core Values, End & Means goals, Vision statement, onboarding wizard |
| Dashboards | Simple, Command Center (full), Mobile, My Dashboard (custom), 100+ widget combinations (22 widget types × collections) |
| Basics | Tasks (today / upcoming / board / calendar), Projects, Areas, Resources Manager (Collections → Resources with learning progress), Archive, Tags, Habits & daily tracker (streaks), Day / Month / Year notes, Journal, Sleep tracker, Setup guide, Search |
| Health & Fitness | Workouts, Exercise library (PBs), Body metrics (weight trend) |
| Food & Meals | Recipes, Meal planner, Grocery list |
| Career | Job applications, Professional CRM, Interactions, CV (experience, education, skills) + CV builder / share |
| Finance | Transactions, Budgets (auto spent vs limit), Savings goals, Subscriptions |
| Learning & Growth | Learning (courses / skills), Reading tracker, Vocabulary + flashcards |
| Home & Car | Home maintenance & improvement, Home inventory, Vehicles, Car service log, Warranties |
| People & Love | People CRM & birthdays, Love hub, Gift ideas, Wishlist, Bucket list |
| Leisure & Travel | Travel planner, Itinerary & packing, Series & movies, Video games library |

---

## Download & install

1. Go to **[Releases](https://github.com/Tylerlikesps4/Anu-Keystone/releases/latest)** and download `AnuKeystone-v0.01.apk` on your Android phone.
2. Open the file. If Android asks, allow your browser or file manager to **install unknown apps**.
3. Tap **Install**, then open **Anu Keystone**.

**Requirements:** Android 8.0 (Oreo, API 26) or newer. About 2 MB.

**Updating:** install the new APK over the old one. Your data is kept, because every release is signed with the same key.

---

## Getting started

1. **Life Setup.** The first launch walks you through the Wheel of Life, your core values, your vision and your first goals. You can skip it and come back from **More → Life Setup** any time.
2. **Pick a dashboard.** *Simple* for a calm start, *Command Center* for everything, *Mobile* for one-handed use, or build *My Dashboard* from the widget catalog.
3. **Add your Areas** (Health, Work, Home…) and a few **Projects**. Every item in the app can link to an Area, a Project and Tags, so opening any of them shows everything connected to it.
4. **Turn on the daily digest** in the **More** tab for a morning notification with today's tasks, habits and upcoming dates.
5. **Back up.** Use **More → Export** to save a JSON file now and then. Keep it somewhere safe, like your cloud drive.

---

## Privacy

- No account, no sign-in, no ads, no analytics.
- The app does not request internet access. Nothing leaves your phone unless you export it yourself.
- Your data lives in a local SQLite database inside the app's private storage.
- Uninstalling the app deletes your data, so export a backup first.

---

## How it works

Anu Keystone is built on one small, tested engine (`engine/`, pure Kotlin) that behaves like a tiny Notion:

- **Collections** with typed fields: text, long text, number, currency, date, time, select, multi-select, checkbox, relation, rating, URL, email, phone, file, image, progress, **rollup** and **formula**.
- **Views:** list, board (kanban), calendar, gallery, table and stats (bar / column / line charts). Views have filters (including "due within N days", "this month", "today") and sorts.
- **PARA everywhere:** every collection automatically gets *Area*, *Project* and *Tags* relations. Open any project, area or tag to see everything linked to it (backlinks).
- **Rollups:** project progress from tasks, learning progress from resources, service spend per car, and more.
- **Recurring items:** repeating tasks and home maintenance respawn when marked done.
- **Periodic notes:** Day → Month → Year notes are created and linked automatically.
- **Reminders:** any date field flagged `remind` (birthdays yearly, warranties, renewals, car service, follow-ups, deadlines) feeds the Upcoming widget and the optional daily notification (WorkManager).
- **Backup:** full JSON export / import (replace or merge, newer edit wins) and CSV export per collection.
- **Create your own:** new collections, fields and views from inside the app.

Each hub is just a preset schema, views and dashboard widgets on that engine, so anything a hub does, your own collections can do too.

---

## Formulas

Formula fields compute a value from other fields in the same item. Some built-ins:

| Function | What it returns | Example use |
|---|---|---|
| `pct(saved, target)` | Percentage of a target reached | Savings goal progress |
| `hours_between(bed, wake)` | Hours between two times | Sleep length |
| `days_until(expires)` | Days left until a date | Warranty or passport expiry |
| `age(birthday)` | Age in years | People CRM |
| `days_until_annual(birthday)` | Days until the next yearly date | Upcoming birthdays |
| `monthly(price, cycle)` | Monthly cost of a recurring price | Subscriptions |

---

## Building from source

**Requirements:** JDK 17 and the Android SDK (platform 35, build-tools 35).

```bash
git clone https://github.com/Tylerlikesps4/Anu-Keystone.git
cd Anu-Keystone
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

Without it, `assembleRelease` produces an unsigned APK.

**CI:** `.github/workflows/build.yml` runs the tests and builds the APK on every push. Add `KEYSTORE_BASE64` and `KEYSTORE_PASSWORD` repository secrets to have CI sign it.

**Tech stack:** Kotlin, Jetpack Compose (Material 3), SQLite, WorkManager, kotlinx.serialization, JUnit.

---

## Project layout

```
engine/   pure-Kotlin engine: Model, Engine (CRUD, coercion, relations, rollups, views, stats,
          periodic notes, habits, reminders, backup), Formula, Presets (all hubs), Insights; JUnit tests
app/      Android app (Jetpack Compose, Material 3): SQLite store, screens, widgets, reminders
.github/  CI workflow that tests and builds the APK
```

---

## Roadmap

- [ ] Drag and drop on boards (currently long-press to move)
- [ ] More formula functions
- [ ] Currencies other than USD
- [ ] Home-screen widgets
- [ ] Optional encrypted backup
- [ ] Screenshots and a Play Store / F-Droid listing

Ideas welcome in [Issues](https://github.com/Tylerlikesps4/Anu-Keystone/issues).

---

## Known limitations

v0.01 is an early build:

- Boards use long-press to move cards, not drag.
- The formula language is small.
- Money fields are USD only.
- Not yet tested on a wide range of devices.

---

## Contributing

Bug reports, ideas and pull requests are welcome.

1. Open an [issue](https://github.com/Tylerlikesps4/Anu-Keystone/issues) describing the bug or idea.
2. Fork the repo and create a branch.
3. Run `./gradlew :engine:test` before opening a pull request.

By contributing, you agree your contribution is licensed under GPL-3.0-or-later.

---

## FAQ

**Is it really free?** Yes. No ads, no subscription, no paywalled features.

**Does it sync between devices?** Not yet. Use JSON backup and import to move your data.

**Where is my data?** Only on your phone, in the app's private storage.

**Can I make my own trackers?** Yes. Create collections, fields and views from inside the app.

**Why does Android warn me when installing?** The APK comes from GitHub instead of the Play Store. That's normal for sideloaded apps.

---

## License

**GPL-3.0-or-later** · Copyright (C) 2026 Tylerlikesps4

Anu Keystone is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. See [`LICENSE`](LICENSE) and [`CREDITS.md`](CREDITS.md) for third-party licences.

The "Anu Keystone" name and logo are not covered by the GPL.

Anu Keystone is an independent project and is not affiliated with Notion or any Notion template vendor.
