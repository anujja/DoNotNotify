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
3. Rules are **DENYLIST** (block matching notifications), **ALLOWLIST** (allow only matching, block rest), **STACK** (don't block — cancel the source and re-post matching notifications as one native notification group), or **THROTTLE** (let one matching notification through, then block matches for `AdvancedRuleConfig.throttleSeconds`, default 60 s)
4. Matching supports both simple `CONTAINS` and `REGEX` on title/text fields, with optional conditions via `AdvancedRuleConfig` (time window; phone in use = screen on and unlocked, read via `PowerManager`/`KeyguardManager` at notification time). STACK reuses the exact same matching; precedence is: a DENYLIST/allowlist-gating block wins over STACK; ongoing notifications are never stacked. A rule whose conditions don't hold is skipped entirely — an inactive ALLOWLIST rule does not gate the app
5. **THROTTLE** never gates like allowlist. Every matching THROTTLE rule (not just the first) gets its own window (`ThrottleManager`); it's checked only for notifications that weren't blocked and aren't ongoing, a suppression wins over stacking, and a pass then stacks normally. A suppressed notification is cancelled and logged as blocked, and never restarts the window. A THROTTLE rule with empty filters throttles the whole app

### Key Source Files

- `NotificationBlockerService.kt` — Service that receives and processes all notifications. Holds a self-package reentrancy guard (our own re-posted stack notifications must not re-enter processing) and the `StackPoster` wiring
- `RuleMatcher.kt` — Rule evaluation logic. `matches()` (conditions via `RuleConditions`, then `findMatch()` per field: regex/contains on the visible text; the rule wizard's live filter preview highlights the same ranges) and the pure `planNotificationDecision()` → `NotificationDecision` (block/stack precedence, first-match-wins, hitCount indices) extracted so the precedence matrix is JVM-testable
- `RuleConditions.kt` — The "only apply when" half of `AdvancedRuleConfig` (time window, `DeviceUsage`). Pure `isActive(config, MatchContext)`; `MatchContext` is a once-per-notification snapshot of time/device state, passed through `matches()`/`planNotificationDecision()` so conditions are JVM-testable. New device-state conditions add a field to `MatchContext`
- `ThrottleManager.kt` — Pure THROTTLE planning: `secondsFor()`, `windowsFor()` (one window per matched rule), `shouldSuppress()`, `evaluate()` → suppress or windows to restart. JVM-tested in `ThrottleManagerTest`; which rules match is in `planNotificationDecision().throttleRules`
- `ui/components/RuleOptions.kt` — `RuleOption` registry (`RULE_OPTIONS`): each condition/action declares its section, icon, applicable rule types, summary and editor; the same registry drives the rule card's icons. Adding an option = one object in the list
- `ui/components/AdvancedRuleConfigDialog.kt` — the Advanced Configuration dialog (Enable Rule, the registry's options, then type-specific settings: THROTTLE duration, STACK channel name + sound link), opened from the rule editor (`Dialogs.kt`, whose type picker is a dropdown) and the new-rule wizard's review step
- `StackedNotificationManager.kt` — STACK registry + transactional re-post engine. Pure `planAbsorb()` → `AbsorbPlan`, the `StackPoster` side-effect seam, hex SHA-256 `groupKeyFor()`, eviction caps, privacy redaction, restart reconciliation
- `BlockerRule.kt` — Data models: `BlockerRule`, `AdvancedRuleConfig`, `MatchType`, `RuleType` (`DENYLIST`/`ALLOWLIST`/`STACK`/`THROTTLE`)
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
| `ThrottleStorage` | SharedPreferences | THROTTLE last-pass timestamps per rule (`rule:<id>`), so a restart can't reopen a burst |
| `NotificationActionRepository` | In-memory ConcurrentHashMap | Cached notification action intents |
| `StackedNotificationManager` | In-memory ConcurrentHashMap (volatile) | STACK group registry — entries, summary ids, cumulative counts; cleared/reconciled on listener (re)connect |
| `PrebuiltRulesRepository` | Assets JSON (`prebuilt_rules.json`) | Pre-configured rules for popular apps |

### UI Structure

Compose screens in `ui/screens/`, dialogs in `ui/components/`. Main UI is a `HorizontalPager` with three tabs. New rules are made in the full-screen `RuleWizardScreen`, opened from the Rules tab or from a History notification's details (then prefilled with its app, title and text, starting on the rule type step); existing rules are edited in `EditRuleDialog`. Prebuilt rules auto-install when corresponding apps are detected (package queries declared in manifest).
