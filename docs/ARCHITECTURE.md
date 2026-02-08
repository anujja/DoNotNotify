# DoNotNotify - Architecture & Codebase Documentation

## Table of Contents

- [1. Project Overview](#1-project-overview)
- [2. Project Structure](#2-project-structure)
- [3. Build System & Configuration](#3-build-system--configuration)
- [4. Data Models](#4-data-models)
- [5. Core Services & Business Logic](#5-core-services--business-logic)
- [6. Storage Layer](#6-storage-layer)
- [7. UI Layer](#7-ui-layer)
- [8. Theme System](#8-theme-system)
- [9. Prebuilt Rules](#9-prebuilt-rules)
- [10. Testing](#10-testing)
- [11. Data Flow Diagrams](#11-data-flow-diagrams)
- [12. Android Manifest & Permissions](#12-android-manifest--permissions)

---

## 1. Project Overview

DoNotNotify is a single-module Android application that intercepts, evaluates, and optionally blocks system notifications based on user-defined rules. It operates entirely offline with zero network permissions.

| Property | Value |
|---|---|
| Package | `com.donotnotify.donotnotify` |
| Language | Kotlin (100%) |
| UI Framework | Jetpack Compose + Material 3 |
| Min SDK | 24 (Android 7.0 Nougat) |
| Target/Compile SDK | 36 |
| Java Target | 11 |
| Current Version | 2.61 (versionCode 27) |
| License | MIT |
| Gradle Version | 8.13 |
| AGP Version | 8.13.2 |

### Key Dependencies

| Dependency | Version | Purpose |
|---|---|---|
| Jetpack Compose BOM | 2024.09.00 | UI framework |
| Material 3 | 1.4.0 | Design system |
| Material Icons Extended | 1.7.8 | Icon library |
| Gson | 2.13.2 | JSON serialization |
| Accompanist System UI Controller | 0.36.0 | Status bar styling |
| Kotlin | 2.0.21 | Language |

---

## 2. Project Structure

```
DoNotNotify/
├── app/
│   ├── build.gradle.kts                 # App-level build config
│   ├── proguard-rules.pro               # R8/ProGuard rules
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/
│       │   │   └── prebuilt_rules.json  # 32 pre-configured rules
│       │   ├── java/com/donotnotify/donotnotify/
│       │   │   ├── MainActivity.kt                    # App entry point, navigation, state
│       │   │   ├── NotificationBlockerService.kt      # Notification listener service
│       │   │   ├── RuleMatcher.kt                     # Rule evaluation engine
│       │   │   ├── BlockerRule.kt                     # Data models (rule, enums, config)
│       │   │   ├── SimpleNotification.kt              # Notification data model
│       │   │   ├── RuleStorage.kt                     # Rule persistence (JSON file)
│       │   │   ├── NotificationHistoryStorage.kt      # Notification history (JSON file)
│       │   │   ├── BlockedNotificationHistoryStorage.kt # Blocked history (JSON file)
│       │   │   ├── AppInfoStorage.kt                  # App icons & names (SQLite)
│       │   │   ├── UnmonitoredAppsStorage.kt          # Excluded apps (SharedPreferences)
│       │   │   ├── StatsStorage.kt                    # Blocked counts (SharedPreferences)
│       │   │   ├── NotificationActionRepository.kt    # In-memory PendingIntent cache
│       │   │   ├── PrebuiltRulesRepository.kt         # Asset-based rule loader
│       │   │   └── ui/
│       │   │       ├── components/
│       │   │       │   ├── Dialogs.kt                 # Add/Edit rule dialogs, advanced config
│       │   │       │   ├── AboutDialog.kt             # About dialog
│       │   │       │   ├── AutoAddedRulesDialog.kt    # Auto-added rules notification
│       │   │       │   ├── DeleteConfirmationDialog.kt # Deletion confirmation
│       │   │       │   ├── HistoryNotificationDetailsDialog.kt  # History item details
│       │   │       │   └── NotificationDetailsDialog.kt         # Blocked item details
│       │   │       ├── screens/
│       │   │       │   ├── HistoryScreen.kt           # Tab 0: Notification history
│       │   │       │   ├── RulesScreen.kt             # Tab 1: Rule list
│       │   │       │   ├── BlockedScreen.kt           # Tab 2: Blocked notifications
│       │   │       │   ├── SettingsScreen.kt          # Settings page
│       │   │       │   ├── PrebuiltRulesScreen.kt     # Prebuilt rule browser
│       │   │       │   ├── UnmonitoredAppsScreen.kt   # Unmonitored apps manager
│       │   │       │   └── EnableNotificationListenerScreen.kt # Permission request
│       │   │       └── theme/
│       │   │           ├── Color.kt                   # Light/dark color definitions
│       │   │           ├── Theme.kt                   # Material theme composition
│       │   │           └── Type.kt                    # Typography definitions
│       │   └── res/                                   # Android resources
│       ├── test/                                      # Unit tests
│       └── androidTest/                               # Instrumented tests
├── build.gradle.kts                     # Root build config
├── settings.gradle.kts                  # Gradle settings
├── gradle/
│   └── libs.versions.toml               # Version catalog
├── fastlane/                            # Store metadata & screenshots
├── CLAUDE.md                            # AI assistant instructions
├── CONTRIBUTING.md                      # Contribution guidelines
├── README.md                            # Project readme
└── LICENSE                              # MIT license
```

---

## 3. Build System & Configuration

### Root Build (`build.gradle.kts`)

Declares plugin aliases without applying them:

- `com.android.application`
- `org.jetbrains.kotlin.android`
- `org.jetbrains.kotlin.plugin.compose`

### App Build (`app/build.gradle.kts`)

**Plugins applied:**
- `android.application` - Android application plugin
- `kotlin.android` - Kotlin for Android
- `kotlin.compose` - Compose compiler plugin
- `kotlin-parcelize` - Parcelable code generation

**Build types:**
- **debug** - Default debug configuration
- **release** - Minification enabled (`isMinifyEnabled = true`) via R8 with `proguard-android-optimize.txt` and custom `proguard-rules.pro`

**Version Catalog (`gradle/libs.versions.toml`):**

All dependency versions are centralized in the TOML version catalog. Key entries include the Compose BOM for coordinated Compose library versions, Gson for JSON, and Accompanist for system UI control.

### ProGuard (`proguard-rules.pro`)

Minimal custom rules. The `BlockerRule` keep rule is commented out because `@Keep` annotations on the data classes handle R8 retention directly.

---

## 4. Data Models

### `BlockerRule` (`BlockerRule.kt`)

The central data model representing a notification filtering rule.

```kotlin
@Keep
@Parcelize
data class BlockerRule(
    val appName: String? = null,          // Human-readable app name
    val packageName: String? = null,       // Android package identifier
    val titleFilter: String? = null,       // Pattern to match notification title
    val titleMatchType: MatchType = MatchType.CONTAINS,
    val textFilter: String? = null,        // Pattern to match notification text
    val textMatchType: MatchType = MatchType.CONTAINS,
    val hitCount: Int = 0,                 // Number of times this rule matched
    val ruleType: RuleType = RuleType.BLACKLIST,
    val isEnabled: Boolean = true,         // Whether the rule is active
    val advancedConfig: AdvancedRuleConfig? = null  // Optional time scheduling
) : Parcelable
```

### `MatchType` (enum)

- `CONTAINS` - Case-insensitive substring match
- `REGEX` - Full regex match via `String.matches()`

### `RuleType` (enum)

- `BLACKLIST` - Block notifications that match this rule
- `WHITELIST` - Allow only notifications that match; block everything else for this package

### `AdvancedRuleConfig`

Optional time-windowed scheduling for rules:

```kotlin
@Keep
@Parcelize
data class AdvancedRuleConfig(
    val isTimeLimitEnabled: Boolean = false,
    val startTimeHour: Int = 9,            // Default: 09:00
    val startTimeMinute: Int = 0,
    val endTimeHour: Int = 17,             // Default: 17:00
    val endTimeMinute: Int = 0
) : Parcelable
```

Supports spans across midnight (e.g., 22:00 to 06:00).

### `SimpleNotification` (`SimpleNotification.kt`)

Lightweight notification representation for storage and display:

```kotlin
@Keep
@Parcelize
data class SimpleNotification(
    val appLabel: String?,         // Display name of the source app
    val packageName: String?,      // Android package name
    val title: String?,            // Notification title
    val text: String?,             // Notification body text
    val timestamp: Long,           // Unix timestamp in milliseconds
    val wasOngoing: Boolean = false,  // Whether it was FLAG_ONGOING_EVENT
    val id: String? = UUID.randomUUID().toString()  // Unique identifier
) : Parcelable
```

All data classes use `@Keep` to survive R8 minification (required for Gson reflection) and `@Parcelize` for efficient inter-component transfer.

---

## 5. Core Services & Business Logic

### `NotificationBlockerService` (`NotificationBlockerService.kt`)

**Extends:** `NotificationListenerService` (Android system service)

This is the heart of the application. Android delivers every posted notification to this service via the `onNotificationPosted()` callback.

**Lifecycle:**
1. `onCreate()` - Initializes all storage instances
2. `onNotificationPosted(sbn)` - Processes each notification (see flow below)

**Notification Processing Flow (`onNotificationPosted`):**

```
Notification received (StatusBarNotification)
    │
    ├── Extract: packageName, title, text, timestamp
    │
    ├── Skip if both title and text are null/blank
    │
    ├── Resolve app name:
    │   1. android.substituteAppName extra (system label)
    │   2. PackageManager.getApplicationLabel()
    │   3. Raw package name (fallback)
    │
    ├── Save/update app info (icon + name) in SQLite if not cached
    │
    ├── Load all rules for this package
    │   ├── Evaluate WHITELIST rules (first match wins)
    │   └── Evaluate BLACKLIST rules (first match wins)
    │
    ├── Update hitCount on matched rules
    │
    ├── Determine block decision:
    │   blocked = (hasWhitelist && !matchesWhitelist) || matchesBlacklist
    │
    ├── If blocked: cancelNotification(sbn.key)
    │   └── Logs warning if notification has FLAG_ONGOING_EVENT
    │
    ├── Debounce check (5-second window per notification key)
    │   └── key = "$packageName:$title:$text"
    │
    ├── If not duplicate:
    │   ├── Cache PendingIntent (contentIntent) in NotificationActionRepository
    │   ├── If blocked: save to BlockedNotificationHistoryStorage, increment stats
    │   └── If allowed && not unmonitored: save to NotificationHistoryStorage
    │
    ├── Broadcast ACTION_HISTORY_UPDATED
    │
    └── Clean up expired debounce entries
```

**Constants:**
- `ACTION_HISTORY_UPDATED = "com.donotnotify.donotnotify.HISTORY_UPDATED"` - Broadcast action
- `DEBOUNCE_PERIOD_MS = 5000L` - 5-second duplicate suppression window

**Thread safety note:** The `recentlyBlocked` map is a plain `mutableMapOf`. This is safe because `onNotificationPosted` is called on a single binder thread per the `NotificationListenerService` contract.

### `RuleMatcher` (`RuleMatcher.kt`)

**Type:** Singleton object

Stateless rule evaluation engine with two public methods:

#### `matches(rule, packageName, title, text): Boolean`

Evaluates a single rule against notification data:

1. **Time check** - If `advancedConfig.isTimeLimitEnabled`, verify current time falls within the configured window. Handles midnight-spanning ranges.
2. **Package check** - Rule's packageName must match (safety check; callers usually pre-filter).
3. **Title match** - Based on `titleMatchType`:
   - `CONTAINS`: Case-insensitive `String.contains()`
   - `REGEX`: `String.matches()` (full match, not find)
   - Blank/null filter = automatic match (wildcard)
4. **Text match** - Same logic as title match.
5. **Result**: `titleMatch && textMatch`

Invalid regex patterns are caught and treated as non-matches.

#### `shouldBlock(packageName, title, text, rules): Boolean`

Higher-level method that evaluates all rules for a package:

1. Filters to enabled rules for the given package
2. Separates into whitelist and blacklist groups
3. Checks whitelist rules (first match = whitelisted)
4. Checks blacklist rules (first match = blacklisted)
5. **Blocking logic:** `(hasWhitelistRules && !matchesWhitelist) || matchesBlacklist`

This means blacklist rules take priority over whitelist rules when both match.

---

## 6. Storage Layer

The app uses four distinct storage mechanisms:

### 6.1 JSON File Storage

Three classes share a common pattern: Gson serialization to/from JSON files in the app's internal storage directory (`context.filesDir`).

#### `RuleStorage` (`RuleStorage.kt`)

| Property | Value |
|---|---|
| File | `rules.json` |
| Serializer | Gson |
| Type | `List<BlockerRule>` |

**Methods:**
- `getRules(): List<BlockerRule>` - Reads and deserializes the rules file
- `saveRules(rules: List<BlockerRule>)` - Serializes and writes the full list

The entire rule list is replaced on every save (no incremental updates).

#### `NotificationHistoryStorage` (`NotificationHistoryStorage.kt`)

| Property | Value |
|---|---|
| File | `notification_history.json` |
| Serializer | Gson |
| Type | `List<SimpleNotification>` |
| Retention | Configurable via SharedPreferences `historyDays` (default: 5 days) |

**Methods:**
- `getHistory()` - Returns all stored notifications
- `saveNotification(notification)` - Deduplicates by content (appLabel + packageName + title + text), adds to front, prunes entries older than retention period
- `deleteNotification(notification)` - Removes a specific entry
- `deleteNotificationsFromPackage(packageName)` - Removes all entries from a package
- `updateAppLabelForPackage(packageName, newAppLabel)` - Updates display names when a better label is resolved
- `clearHistory()` - Deletes the file entirely

#### `BlockedNotificationHistoryStorage` (`BlockedNotificationHistoryStorage.kt`)

| Property | Value |
|---|---|
| File | `blocked_notification_history.json` |
| Serializer | Gson |
| Type | `List<SimpleNotification>` |
| Max Size | 100 entries (hard cap) |

**Methods:**
- `getHistory()` - Returns blocked notification history
- `saveNotification(notification): Boolean` - Returns `true` if the notification was new (not a duplicate). Deduplicates by content, trims to 100 entries.
- `deleteNotification(notification)` - Removes a specific entry
- `clearHistory()` - Deletes the file entirely

### 6.2 SQLite Storage

#### `AppInfoStorage` / `AppInfoDatabaseHelper` (`AppInfoStorage.kt`)

| Property | Value |
|---|---|
| Database | `app_info.db` |
| Table | `app_info` |
| Schema Version | 1 |

**Schema:**
```sql
CREATE TABLE app_info (
    package_name TEXT PRIMARY KEY,
    app_name     TEXT,
    app_icon     BLOB
)
```

Stores app icons as PNG-compressed BLOBs and human-readable app names.

**Methods:**
- `isAppInfoSaved(packageName): String?` - Returns app name if cached, null otherwise
- `saveAppInfo(packageName, appName, icon)` - Stores/replaces via `CONFLICT_REPLACE`
- `getAppIcon(packageName): Bitmap?` - Decodes stored PNG blob
- `getAppName(packageName): String?` - Returns cached display name
- `deleteAppInfo(packageName)` - Removes entry for a package
- `clearAllAppInfo()` - Truncates the table

**Helper:** `drawableToBitmap(Drawable): Bitmap` - Converts any Drawable to a Bitmap. Handles BitmapDrawable directly, creates a Canvas-drawn bitmap for other types.

### 6.3 SharedPreferences Storage

#### `UnmonitoredAppsStorage` (`UnmonitoredAppsStorage.kt`)

| Property | Value |
|---|---|
| Preferences File | `unmonitored_apps_prefs` |
| Key | `unmonitored_apps` |
| Format | Gson-serialized `Set<String>` |

Manages the set of package names excluded from notification monitoring. Notifications from these apps are not recorded in history.

**Methods:** `getUnmonitoredApps()`, `addApp(packageName)`, `removeApp(packageName)`, `isAppUnmonitored(packageName)`

#### `StatsStorage` (`StatsStorage.kt`)

| Property | Value |
|---|---|
| Preferences File | `stats` |
| Key | `blocked_count` |

Simple counter tracking total blocked notifications.

**Methods:** `getBlockedNotificationsCount(): Int`, `incrementBlockedNotificationsCount()`

#### Settings SharedPreferences

Used directly in `MainActivity` and `NotificationHistoryStorage`:

| File | Key | Type | Default | Purpose |
|---|---|---|---|---|
| `settings` | `historyDays` | Int | 5 | Notification history retention in days |
| `settings` | `processed_packages` | StringSet | empty | Packages already checked for prebuilt rules |
| `settings` | `show_auto_add_dialog` | Boolean | true | Whether to show auto-add notification |

### 6.4 In-Memory Storage

#### `NotificationActionRepository` (`NotificationActionRepository.kt`)

**Type:** Singleton object with `ConcurrentHashMap<String, PendingIntent>`

Caches the `contentIntent` (`PendingIntent`) from each notification, keyed by the notification's UUID. This allows users to "Open" a notification from the history screen, triggering the original app's intent.

**Lifecycle:** Data persists only while the service process is alive. Cleared on process death.

**Methods:** `saveAction(id, action)`, `getAction(id): PendingIntent?`, `clear()`

---

## 7. UI Layer

The entire UI is built with Jetpack Compose and Material 3. There are no XML layouts.

### 7.1 `MainActivity` (`MainActivity.kt`)

**Extends:** `ComponentActivity`

Entry point and root state holder for the application. Manages:

- **Storage initialization** - Creates instances of all storage classes
- **State variables** (using `mutableStateOf`):
  - `isServiceEnabled` - Whether the notification listener is active
  - `pastNotifications` - Current notification history list
  - `blockedNotifications` - Blocked notification history list
  - `rules` - Current rule set
  - `unmonitoredApps` - Set of excluded packages
  - `showSettingsScreen` / `showPrebuiltRulesScreen` - Navigation state
  - `autoAddedApps` / `showAutoAddedDialog` - Prebuilt rule auto-add state

**Key behaviors:**

1. **Edge-to-edge** - Calls `enableEdgeToEdge()` and uses Accompanist's `rememberSystemUiController()` to set transparent status/navigation bars with appropriate icon colors.

2. **Prebuilt rule auto-installation** (`checkForNewRules()`):
   - On launch, scans installed packages against `prebuilt_rules.json`
   - Adds rules for newly-detected apps that aren't already covered
   - Tracks processed packages in SharedPreferences to avoid re-processing
   - Shows `AutoAddedRulesDialog` to inform the user

3. **BroadcastReceiver** - Listens for `ACTION_HISTORY_UPDATED` broadcasts from the service to refresh notification lists in real-time.

4. **Navigation model** - Simple boolean-based screen switching (no Navigation component):
   - If service not enabled: `EnableNotificationListenerScreen`
   - If settings open: `SettingsScreen`
   - If prebuilt rules open: `PrebuiltRulesScreen`
   - Default: `TabbedScreen` with three tabs

5. **Dialog management** - Uses nullable state variables to control dialog visibility:
   - `notificationToShowAddDialog` → `AddRuleDialog`
   - `notificationToShowDetailsDialog` → `NotificationDetailsDialog`
   - `notificationToShowHistoryDetailsDialog` → `HistoryNotificationDetailsDialog`
   - `ruleToEdit` → `EditRuleDialog`
   - `ruleToDelete` → `DeleteConfirmationDialog`
   - `notificationToDelete` → `DeleteConfirmationDialog`

**Extension function:** `Color.luminance(): Float` - Calculates relative luminance for determining dark/light icon colors.

### 7.2 Screens

#### `HistoryScreen` (`ui/screens/HistoryScreen.kt`)

**Tab 0** - Displays all received (non-blocked) notifications grouped by app.

**Features:**
- Notifications grouped by `appLabel`, sorted by most recent timestamp
- Expandable/collapsible app groups with notification count
- App icons loaded asynchronously from `AppInfoStorage` via `produceState`
- Each notification card shows title, text, and relative timestamp
- Warning icon for ongoing notifications (may not be blockable)
- Per-notification delete button
- "Stop monitoring" button per app group (adds to unmonitored list)
- "Clear History" button with confirmation dialog
- Collapsible "Unmonitored Apps" section at bottom with "Resume" buttons
- Auto-scrolls to unmonitored section when expanded

#### `RulesScreen` (`ui/screens/RulesScreen.kt`)

**Tab 1** - Lists all configured blocking rules.

**Features:**
- Each rule card shows: app name, title filter, text filter
- Rule type icon: Block icon for BLACKLIST, checkmark for WHITELIST
- Clock icon if time-limited via `AdvancedRuleConfig`
- Hit count display
- Disabled rules shown with strikethrough and reduced opacity
- Tap to edit (opens `EditRuleDialog`)
- "Browse Pre-built Rules" button at bottom
- Empty state message directing users to History tab

#### `BlockedScreen` (`ui/screens/BlockedScreen.kt`)

**Tab 2** - Shows recently blocked notifications.

**Features:**
- Flat list (not grouped) of blocked notifications
- Each card shows: app name, title, text
- Warning icon for ongoing notifications
- Per-item delete button
- Tap to view details (opens `NotificationDetailsDialog`)
- "Clear Blocked History" button with confirmation dialog

#### `SettingsScreen` (`ui/screens/SettingsScreen.kt`)

**Full-screen overlay** with back navigation.

**Settings:**
- **History Retention (Days)** - Numeric input, saved to SharedPreferences
- **Export/Import Rules** - Opens dialog with two options:
  - Export: Uses `ActivityResultContracts.CreateDocument` to save rules as `donotnotify_rules.json`
  - Import: Uses `ActivityResultContracts.OpenDocument` to load rules. Deduplicates against existing rules on import. Uses `GsonBuilder` with exclusion strategy to skip `hitCount` during export.
- **Buy me a coffee** - Opens external link
- **Visit Website** - Opens external link
- **Version display** - Shows current version at bottom

#### `PrebuiltRulesScreen` (`ui/screens/PrebuiltRulesScreen.kt`)

**Full-screen overlay** showing available prebuilt rules.

**Behavior:**
- Loads rules from `PrebuiltRulesRepository`
- Filters to only show rules for installed apps that aren't already added
- Each card shows app name, title filter, text filter
- "Add" button per rule

#### `UnmonitoredAppsScreen` (`ui/screens/UnmonitoredAppsScreen.kt`)

**Full-screen overlay** listing apps excluded from monitoring.

Shows each unmonitored package with its resolved app label and a "Resume" button.

#### `EnableNotificationListenerScreen` (`ui/screens/EnableNotificationListenerScreen.kt`)

**Initial screen** shown when notification listener permission is not granted.

Displays an informational card with a button that opens Android's `ACTION_NOTIFICATION_LISTENER_SETTINGS`.

### 7.3 Dialog Components

#### `AddRuleDialog` / `EditRuleDialog` / `RuleDialog` (`ui/components/Dialogs.kt`)

`RuleDialog` is a shared private composable used by both `AddRuleDialog` and `EditRuleDialog`:

**Fields:**
- Rule type selector: `BLACKLIST` / `WHITELIST` (segmented button)
- Title filter text field with match type selector (`CONTAINS` / `REGEX`)
- Text filter text field with match type selector (`CONTAINS` / `REGEX`)
- "Advanced Configuration" button → opens `AdvancedRuleConfigDialog`
- Delete button (edit mode only, with confirmation)
- Cancel / Save buttons

**`AddRuleDialog`** pre-populates from a `SimpleNotification` (title, text, package, app name).

**`EditRuleDialog`** pre-populates from an existing `BlockerRule` and provides update/delete callbacks.

#### `AdvancedRuleConfigDialog` (`ui/components/Dialogs.kt`)

Configures optional settings:
- Enable/disable rule checkbox
- Enable/disable time limit checkbox
- Start time picker (24-hour `TimePickerDialog`)
- End time picker (24-hour `TimePickerDialog`)

#### `TimeSelector` (`ui/components/Dialogs.kt`)

Reusable composable that displays a label and formatted time (HH:mm), opening a native `TimePickerDialog` on tap.

#### `HistoryNotificationDetailsDialog` (`ui/components/HistoryNotificationDetailsDialog.kt`)

Shows full details of a history notification:
- App name, title, text, formatted timestamp
- "Open" button - triggers the cached `PendingIntent` (if available) with background activity start mode on API 34+
- "Create Rule" button - navigates to `AddRuleDialog`
- Long-press on detail values copies to clipboard

#### `NotificationDetailsDialog` (`ui/components/NotificationDetailsDialog.kt`)

Shows full details of a blocked notification:
- App name, title, text, formatted timestamp
- "View Rule" button (if the blocking rule can be identified)
- "Close" button

#### `AutoAddedRulesDialog` (`ui/components/AutoAddedRulesDialog.kt`)

Informs the user about automatically added prebuilt rules:
- Lists up to 5 app names, then "+ X more"
- "Do Not Show Again" button (persists preference)
- "Ok" dismiss button

#### `DeleteConfirmationDialog` (`ui/components/DeleteConfirmationDialog.kt`)

Generic confirmation dialog for delete actions. Shows item name and Cancel/Delete buttons.

#### `AboutDialog` (`ui/components/AboutDialog.kt`)

Shows app name, version, and developer email (`aj@donotnotify.com`).

---

## 8. Theme System

### Color Palette (`ui/theme/Color.kt`)

Defines a complete Material 3 color scheme for both light and dark modes with a blue primary color (`#00639A` light / `#92CCFF` dark). The dark theme uses a lighter dark grey (`#2B2D30`) for background and surface.

### Theme Composition (`ui/theme/Theme.kt`)

`DoNotNotifyTheme` composable:
- Supports system dark mode detection via `isSystemInDarkTheme()`
- Dynamic color (Material You) is available but **disabled by default** (`dynamicColor = false`)
- Falls back to custom light/dark color schemes
- Applies custom typography

### Typography (`ui/theme/Type.kt`)

Defines `bodyLarge` style (16sp, normal weight, 24sp line height). Other Material styles use defaults.

---

## 9. Prebuilt Rules

### `PrebuiltRulesRepository` (`PrebuiltRulesRepository.kt`)

Loads rules from `assets/prebuilt_rules.json` using Gson.

### `prebuilt_rules.json`

Contains 32 pre-configured rules for popular apps:

| Category | Apps | Rule Type |
|---|---|---|
| E-commerce | Flipkart, Amazon, Myntra, eBay, AliExpress, Etsy | BLACKLIST (promotional) |
| Food Delivery | Swiggy, Zomato, Uber Eats, DoorDash, Grubhub | BLACKLIST (promotional) |
| Social Media | Instagram, Facebook, YouTube, TikTok, Snapchat, Reddit, Pinterest, X (Twitter), LinkedIn | BLACKLIST (engagement bait) |
| Entertainment | Netflix, Prime Video, Disney+, Hulu, Twitch, Spotify, Candy Crush | BLACKLIST (engagement) |
| Dating | Tinder | BLACKLIST (engagement) |
| Utility | Truecaller, GPay | BLACKLIST (promotional/rewards) |
| Security | Mygate | WHITELIST (allow check-in/approval only) |

Most rules use `REGEX` matching on text content with case-insensitive patterns like:
```regex
(?i).*(offer|discount|sale|deal|coupon|cashback).*
```

The Mygate rule is notable as the only WHITELIST rule - it allows only check-in and approval notifications, blocking all others from that app.

### Auto-Installation Logic (`MainActivity.checkForNewRules()`)

On each app launch:
1. Gets set of already-processed packages from SharedPreferences
2. Queries `PackageManager` for installed packages
3. Loads prebuilt rules
4. Finds rules where:
   - The target package is installed
   - The package hasn't been processed before
   - No existing rule covers this package
5. Adds matching rules to storage
6. Records processed packages
7. Shows dialog listing newly added apps

---

## 10. Testing

### Unit Tests (`app/src/test/`)

#### `RuleMatcherTest` (`RuleMatcherTest.kt`)

9 test cases covering `RuleMatcher.shouldBlock()`:

| Test | Scenario | Expected |
|---|---|---|
| No rules exist | Empty rule list | Not blocked |
| Blacklist title match | "Promo" matches "This is a Promo" | Blocked |
| Blacklist no match | "Promo" vs "Important Update" | Not blocked |
| Whitelist match | "OTP" matches "Your OTP is 1234" | Not blocked |
| Whitelist exists, no match | "OTP" vs "Promotional Content" | Blocked (implicit) |
| Both match (priority) | Whitelist "Offer" + Blacklist "Expired" | Blocked (blacklist wins) |
| Regex matching | `^[0-9]+$` matches "123456" but not "123abc456" | Correct |
| Disabled rules | Disabled blacklist rule | Not blocked |
| Mygate regex | WHITELIST `.*(checked\|approval).*` | Not blocked for check-in |

Uses Mockito inline mock maker (configured via `test/resources/mockito-extensions/org.mockito.plugins.MockMaker`).

### Instrumented Tests (`app/src/androidTest/`)

`ExampleInstrumentedTest` - Basic context test (boilerplate, verifies package name).

---

## 11. Data Flow Diagrams

### Notification Processing

```
┌─────────────┐     ┌──────────────────────────┐     ┌──────────────┐
│   Android    │────>│ NotificationBlockerService│────>│  RuleMatcher │
│   System     │     │   onNotificationPosted()  │     │  .matches()  │
└─────────────┘     └──────────────────────────┘     └──────────────┘
                              │                              │
                              │ blocked?                     │ match result
                              ▼                              │
                    ┌──────────────────┐                     │
                    │ cancelNotification│◄────────────────────┘
                    └──────────────────┘
                              │
                    ┌─────────┴──────────┐
                    ▼                    ▼
          ┌─────────────────┐  ┌──────────────────────┐
          │ BlockedHistory  │  │ NotificationHistory   │
          │   Storage       │  │   Storage             │
          │ (if blocked)    │  │ (if allowed &         │
          │                 │  │  not unmonitored)     │
          └─────────────────┘  └──────────────────────┘
                    │                    │
                    └────────┬───────────┘
                             ▼
                  ┌──────────────────────┐
                  │ Broadcast:           │
                  │ ACTION_HISTORY_UPDATED│
                  └──────────────────────┘
                             │
                             ▼
                  ┌──────────────────────┐
                  │ MainActivity         │
                  │ (BroadcastReceiver)  │
                  │ refreshes UI state   │
                  └──────────────────────┘
```

### Rule Evaluation Logic

```
For a notification from package P:

1. Get enabled rules where packageName == P
2. Split into WHITELIST and BLACKLIST groups

                    ┌───────────────────┐
                    │ Any WHITELIST      │
                    │ rules exist?       │
                    └─────┬─────────────┘
                     yes  │          no
                          ▼           │
                ┌────────────────┐    │
                │ Matches any    │    │
                │ WHITELIST rule?│    │
                └──┬──────────┬──┘   │
                yes│          │no    │
                   │          ▼      │
                   │   IMPLICIT     │
                   │   BLOCK        │
                   ▼                ▼
            ┌────────────────────────┐
            │ Matches any BLACKLIST  │
            │ rule?                  │
            └───┬──────────────┬────┘
             yes│              │no
                ▼              ▼
             BLOCK          ALLOW
```

### Storage Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Internal Storage                      │
│                                                         │
│  ┌─────────────┐  ┌──────────────────────┐             │
│  │ rules.json  │  │ notification_history │             │
│  │ (RuleStorage)│  │ .json               │             │
│  └─────────────┘  │ (NotificationHistory │             │
│                    │  Storage)            │             │
│  ┌─────────────┐  └──────────────────────┘             │
│  │ blocked_    │                                        │
│  │ notification│  ┌──────────────────────┐             │
│  │ _history    │  │ app_info.db          │             │
│  │ .json       │  │ (SQLite)             │             │
│  └─────────────┘  └──────────────────────┘             │
│                                                         │
├─────────────────────────────────────────────────────────┤
│                 SharedPreferences                        │
│                                                         │
│  ┌─────────────────────┐  ┌────────────────────┐       │
│  │ settings            │  │ unmonitored_apps   │       │
│  │ - historyDays       │  │ _prefs             │       │
│  │ - processed_packages│  │ - unmonitored_apps │       │
│  │ - show_auto_add_... │  └────────────────────┘       │
│  └─────────────────────┘                                │
│  ┌─────────────────────┐                                │
│  │ stats               │                                │
│  │ - blocked_count     │                                │
│  └─────────────────────┘                                │
│                                                         │
├─────────────────────────────────────────────────────────┤
│                    In-Memory                             │
│                                                         │
│  ┌──────────────────────────────────┐                   │
│  │ NotificationActionRepository    │                   │
│  │ ConcurrentHashMap<String,       │                   │
│  │                   PendingIntent> │                   │
│  └──────────────────────────────────┘                   │
│                                                         │
├─────────────────────────────────────────────────────────┤
│                    Assets (Read-Only)                    │
│                                                         │
│  ┌──────────────────────────────────┐                   │
│  │ prebuilt_rules.json             │                   │
│  │ (PrebuiltRulesRepository)       │                   │
│  └──────────────────────────────────┘                   │
└─────────────────────────────────────────────────────────┘
```

---

## 12. Android Manifest & Permissions

### Permission

```xml
<uses-permission android:name="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" />
```

This is the only permission. No internet, storage, camera, or location permissions.

### Components

**Activity:**
- `MainActivity` - Launcher activity with `MAIN`/`LAUNCHER` intent filter

**Service:**
- `NotificationBlockerService` - Exported with `BIND_NOTIFICATION_LISTENER_SERVICE` permission. Intent filter for `android.service.notification.NotificationListenerService`.

### Package Queries

The manifest declares `<queries>` for 30 app packages. This is required by Android 11+'s package visibility restrictions so the app can:
1. Detect which supported apps are installed
2. Auto-install corresponding prebuilt rules

Queried packages span e-commerce (Amazon, Flipkart, eBay), social media (Instagram, Facebook, TikTok), food delivery (Swiggy, Zomato, DoorDash), entertainment (Netflix, Disney+, Spotify), and utilities (Truecaller, Mygate).

### Backup Configuration

- `fullBackupContent` → `@xml/backup_rules`
- `dataExtractionRules` → `@xml/data_extraction_rules`

These control which app data is included in Android's auto-backup feature.
