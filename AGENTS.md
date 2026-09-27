# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

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
3. Rules are **DENYLIST** (block matching notifications), **ALLOWLIST** (allow only matching, block rest), or **STACK** (don't block — cancel the source and re-post matching notifications as one native notification group)
4. Matching supports both simple `CONTAINS` and `REGEX` on title/text fields, with optional time-window scheduling via `AdvancedRuleConfig`. STACK reuses the exact same matching; precedence is: a DENYLIST/allowlist-gating block wins over STACK; ongoing notifications are never stacked

### Key Source Files

- `NotificationBlockerService.kt` — Service that receives and processes all notifications. Holds a self-package reentrancy guard (our own re-posted stack notifications must not re-enter processing) and the `StackPoster` wiring
- `RuleMatcher.kt` — Rule evaluation logic. `matches()` (time/regex/contains) and the pure `planNotificationDecision()` → `NotificationDecision` (block/stack precedence, first-match-wins, hitCount indices) extracted so the precedence matrix is JVM-testable
- `StackedNotificationManager.kt` — STACK registry + transactional re-post engine. Pure `planAbsorb()` → `AbsorbPlan`, the `StackPoster` side-effect seam, hex SHA-256 `groupKeyFor()`, eviction caps, privacy redaction, restart reconciliation
- `BlockerRule.kt` — Data models: `BlockerRule`, `AdvancedRuleConfig`, `MatchType`, `RuleType` (`DENYLIST`/`ALLOWLIST`/`STACK`)
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
| `StackedNotificationManager` | In-memory ConcurrentHashMap (volatile) | STACK group registry — entries, summary ids, cumulative counts; cleared/reconciled on listener (re)connect |
| `PrebuiltRulesRepository` | Assets JSON (`prebuilt_rules.json`) | Pre-configured rules for popular apps |

### UI Structure

Compose screens in `ui/screens/`, dialogs in `ui/components/`. Main UI is a `HorizontalPager` with three tabs. Rule creation dialogs can be launched from notification history items. Prebuilt rules auto-install when corresponding apps are detected (package queries declared in manifest).
