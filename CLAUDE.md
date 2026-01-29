# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew assembleDebug          # Debug APK
./gradlew assembleRelease        # Release APK (ProGuard enabled)
./gradlew installDebug           # Build and install on connected device
./gradlew test                   # Unit tests
./gradlew connectedAndroidTest   # Instrumented tests
./gradlew clean                  # Clean build
```

## Project Overview

DoNotNotify is an Android app that filters and blocks unwanted notifications using user-defined rules. It operates entirely offline with no network dependencies.

- **Language:** Kotlin (100%), Java 11 target
- **UI:** Jetpack Compose with Material 3
- **Min SDK:** 24, **Target/Compile SDK:** 36
- **Single module** (`:app`), package: `com.donotnotify.donotnotify`

## Architecture

### Core Flow

1. **NotificationBlockerService** (extends `NotificationListenerService`) intercepts all system notifications
2. **RuleMatcher** evaluates each notification against stored **BlockerRule**s
3. Rules are either **BLACKLIST** (block matching notifications) or **WHITELIST** (allow only matching, block rest)
4. Matching supports both simple `CONTAINS` and `REGEX` on title/text fields, with optional time-window scheduling via `AdvancedRuleConfig`

### Key Source Files

- `NotificationBlockerService.kt` — Service that receives and processes all notifications
- `RuleMatcher.kt` — Rule evaluation logic (time checks, regex/contains matching, whitelist/blacklist resolution)
- `BlockerRule.kt` — Data models: `BlockerRule`, `AdvancedRuleConfig`, `MatchType`, `RuleType`
- `SimpleNotification.kt` — Notification data model with Parcelable support
- `MainActivity.kt` — Compose entry point with tabbed UI (History, Rules, Blocked)

### Storage Layer

| Class | Mechanism | Purpose |
|---|---|---|
| `RuleStorage` | JSON file (Gson) | User-defined blocking rules |
| `NotificationHistoryStorage` | JSON file (Gson) | All received notifications (5-day default retention) |
| `BlockedNotificationHistoryStorage` | JSON file (Gson) | Blocked notification history |
| `AppInfoStorage` | SQLite (`AppInfoDatabaseHelper`) | App icons (BLOB) and display names |
| `UnmonitoredAppsStorage` | SharedPreferences | Apps excluded from monitoring |
| `StatsStorage` | SharedPreferences | Blocked notification counts |
| `NotificationActionRepository` | In-memory ConcurrentHashMap | Cached notification action intents |
| `PrebuiltRulesRepository` | Assets JSON (`prebuilt_rules.json`) | Pre-configured rules for popular apps |

### UI Structure

Compose screens in `ui/screens/`, dialogs in `ui/components/`. Main UI is a `HorizontalPager` with three tabs. Rule creation dialogs can be launched from notification history items. Prebuilt rules auto-install when corresponding apps are detected (package queries declared in manifest).
