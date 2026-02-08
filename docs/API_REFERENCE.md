# DoNotNotify - API Reference

Complete method-level reference for every class in the codebase.

---

## Table of Contents

- [Data Models](#data-models)
- [Core Services](#core-services)
- [Storage Classes](#storage-classes)
- [UI - Screens](#ui---screens)
- [UI - Components](#ui---components)
- [UI - Theme](#ui---theme)
- [MainActivity](#mainactivity)

---

## Data Models

### `BlockerRule` (`BlockerRule.kt`)

Parcelable data class representing a notification filtering rule.

| Field | Type | Default | Description |
|---|---|---|---|
| `appName` | `String?` | `null` | Human-readable name of the target app |
| `packageName` | `String?` | `null` | Android package name to match against |
| `titleFilter` | `String?` | `null` | Pattern for matching notification title. Null/blank = match all. |
| `titleMatchType` | `MatchType` | `CONTAINS` | How to evaluate `titleFilter` |
| `textFilter` | `String?` | `null` | Pattern for matching notification text body. Null/blank = match all. |
| `textMatchType` | `MatchType` | `CONTAINS` | How to evaluate `textFilter` |
| `hitCount` | `Int` | `0` | Number of times this rule has matched a notification |
| `ruleType` | `RuleType` | `BLACKLIST` | Whether this rule blocks or whitelists |
| `isEnabled` | `Boolean` | `true` | Whether the rule is currently active |
| `advancedConfig` | `AdvancedRuleConfig?` | `null` | Optional time-window scheduling |

### `MatchType` (enum, `BlockerRule.kt`)

| Value | Behavior |
|---|---|
| `CONTAINS` | Case-insensitive substring match via `String.contains(filter, ignoreCase = true)` |
| `REGEX` | Full string regex match via `String.matches(filter.toRegex())` |

### `RuleType` (enum, `BlockerRule.kt`)

| Value | Behavior |
|---|---|
| `BLACKLIST` | Block notifications that match this rule |
| `WHITELIST` | Allow only matching notifications; implicitly block non-matching ones for this package |

### `AdvancedRuleConfig` (`BlockerRule.kt`)

Parcelable data class for time-based rule scheduling.

| Field | Type | Default | Description |
|---|---|---|---|
| `isTimeLimitEnabled` | `Boolean` | `false` | Whether time restriction is active |
| `startTimeHour` | `Int` | `9` | Start hour (0-23) |
| `startTimeMinute` | `Int` | `0` | Start minute (0-59) |
| `endTimeHour` | `Int` | `17` | End hour (0-23) |
| `endTimeMinute` | `Int` | `0` | End minute (0-59) |

### `SimpleNotification` (`SimpleNotification.kt`)

Parcelable data class representing a notification for storage and display.

| Field | Type | Default | Description |
|---|---|---|---|
| `appLabel` | `String?` | - | Display name of the source app |
| `packageName` | `String?` | - | Android package identifier |
| `title` | `String?` | - | Notification title text |
| `text` | `String?` | - | Notification body text |
| `timestamp` | `Long` | - | Unix timestamp in milliseconds |
| `wasOngoing` | `Boolean` | `false` | Whether the notification had `FLAG_ONGOING_EVENT` |
| `id` | `String?` | `UUID.randomUUID()` | Unique identifier for PendingIntent caching |

---

## Core Services

### `NotificationBlockerService` (`NotificationBlockerService.kt`)

**Extends:** `NotificationListenerService`

Android system service that receives all posted notifications.

#### Constants

| Constant | Value | Description |
|---|---|---|
| `ACTION_HISTORY_UPDATED` | `"com.donotnotify.donotnotify.HISTORY_UPDATED"` | Broadcast action sent after processing |
| `DEBOUNCE_PERIOD_MS` | `5000L` | Milliseconds to suppress duplicate recording |

#### Fields

| Field | Type | Description |
|---|---|---|
| `ruleStorage` | `RuleStorage` | Rule persistence |
| `notificationHistoryStorage` | `NotificationHistoryStorage` | History persistence |
| `blockedNotificationHistoryStorage` | `BlockedNotificationHistoryStorage` | Blocked history persistence |
| `statsStorage` | `StatsStorage` | Block count tracker |
| `unmonitoredAppsStorage` | `UnmonitoredAppsStorage` | Excluded apps tracker |
| `appInfoStorage` | `AppInfoStorage` | App icon/name cache |
| `recentlyBlocked` | `MutableMap<String, Long>` | Debounce map (key → timestamp) |

#### Methods

| Method | Parameters | Returns | Description |
|---|---|---|---|
| `onCreate()` | - | `Unit` | Initializes all storage instances |
| `onNotificationPosted(sbn)` | `StatusBarNotification?` | `Unit` | Main processing callback. Extracts notification data, evaluates rules, blocks/records, and broadcasts updates. |
| `resolveAppName(context, sbn)` | `Context`, `StatusBarNotification` | `CharSequence` | Resolves human-readable app name. Tries: (1) `android.substituteAppName` extra, (2) `PackageManager.getApplicationLabel()`, (3) raw package name. |

---

### `RuleMatcher` (`RuleMatcher.kt`)

**Type:** Singleton `object`

Stateless rule evaluation engine.

#### Methods

| Method | Parameters | Returns | Description |
|---|---|---|---|
| `matches(rule, packageName, title, text)` | `BlockerRule`, `String?`, `String?`, `String?` | `Boolean` | Evaluates a single rule against notification data. Checks time window, package match, title match, and text match. Returns `false` on regex errors. |
| `shouldBlock(packageName, title, text, rules)` | `String`, `String?`, `String?`, `List<BlockerRule>` | `Boolean` | Evaluates all rules for a package. Returns `true` if notification should be blocked. Logic: `(hasWhitelist && !matchesWhitelist) \|\| matchesBlacklist`. |

---

## Storage Classes

### `RuleStorage` (`RuleStorage.kt`)

JSON file-based storage for blocking rules.

| Property | Value |
|---|---|
| File path | `{filesDir}/rules.json` |

| Method | Parameters | Returns | Description |
|---|---|---|---|
| `getRules()` | - | `List<BlockerRule>` | Reads and deserializes rules. Returns empty list if file missing. |
| `saveRules(rules)` | `List<BlockerRule>` | `Unit` | Serializes and overwrites the entire rules file. |

---

### `NotificationHistoryStorage` (`NotificationHistoryStorage.kt`)

JSON file-based storage for non-blocked notification history.

| Property | Value |
|---|---|
| File path | `{filesDir}/notification_history.json` |
| Retention | `historyDays` from SharedPreferences (default: 5) |

| Method | Parameters | Returns | Description |
|---|---|---|---|
| `getHistory()` | - | `List<SimpleNotification>` | Returns all stored notifications. Empty list if file missing. |
| `saveNotification(notification)` | `SimpleNotification` | `Unit` | Deduplicates by content fields (appLabel, packageName, title, text), prepends to list, prunes entries older than retention period. |
| `deleteNotification(notification)` | `SimpleNotification` | `Unit` | Removes a specific notification by equality. |
| `deleteNotificationsFromPackage(packageName)` | `String` | `Unit` | Removes all notifications from a package. |
| `updateAppLabelForPackage(packageName, newAppLabel)` | `String`, `String` | `Unit` | Updates the `appLabel` on all notifications from a given package. |
| `clearHistory()` | - | `Unit` | Deletes the history file. |

---

### `BlockedNotificationHistoryStorage` (`BlockedNotificationHistoryStorage.kt`)

JSON file-based storage for blocked notification history.

| Property | Value |
|---|---|
| File path | `{filesDir}/blocked_notification_history.json` |
| Max entries | 100 |

| Method | Parameters | Returns | Description |
|---|---|---|---|
| `getHistory()` | - | `List<SimpleNotification>` | Returns blocked notification history. |
| `saveNotification(notification)` | `SimpleNotification` | `Boolean` | Deduplicates by content, prepends, trims to 100. Returns `true` if notification was new (not duplicate). |
| `deleteNotification(notification)` | `SimpleNotification` | `Unit` | Removes specific notification. |
| `clearHistory()` | - | `Unit` | Deletes the history file. |

---

### `AppInfoStorage` / `AppInfoDatabaseHelper` (`AppInfoStorage.kt`)

SQLite-based cache for app icons and display names.

#### `AppInfoDatabaseHelper`

| Property | Value |
|---|---|
| Database name | `app_info.db` |
| Version | 1 |
| Table | `app_info (package_name TEXT PK, app_name TEXT, app_icon BLOB)` |

#### `AppInfoStorage`

| Method | Parameters | Returns | Description |
|---|---|---|---|
| `isAppInfoSaved(packageName)` | `String` | `String?` | Returns cached app name, or `null` if not stored. |
| `saveAppInfo(packageName, appName, icon)` | `String`, `String`, `Drawable` | `Unit` | Stores app info. Converts Drawable to PNG BLOB. Uses `CONFLICT_REPLACE`. |
| `getAppIcon(packageName)` | `String` | `Bitmap?` | Returns decoded icon bitmap, or `null`. |
| `getAppName(packageName)` | `String` | `String?` | Returns cached display name. |
| `deleteAppInfo(packageName)` | `String` | `Unit` | Removes entry for package. |
| `clearAllAppInfo()` | - | `Unit` | Truncates the table. |

**Private helper:** `drawableToBitmap(drawable: Drawable): Bitmap` - Converts any Drawable to Bitmap. Handles BitmapDrawable directly; creates Canvas-rendered bitmap for others.

---

### `UnmonitoredAppsStorage` (`UnmonitoredAppsStorage.kt`)

SharedPreferences-based storage for packages excluded from monitoring.

| Property | Value |
|---|---|
| Preferences file | `unmonitored_apps_prefs` |
| Key | `unmonitored_apps` (Gson-serialized `Set<String>`) |

| Method | Parameters | Returns | Description |
|---|---|---|---|
| `getUnmonitoredApps()` | - | `Set<String>` | Returns set of excluded package names. |
| `addApp(packageName)` | `String` | `Unit` | Adds package to exclusion set. |
| `removeApp(packageName)` | `String` | `Unit` | Removes package from exclusion set. |
| `isAppUnmonitored(packageName)` | `String` | `Boolean` | Checks if package is excluded. |

---

### `StatsStorage` (`StatsStorage.kt`)

SharedPreferences-based counter for blocked notifications.

| Property | Value |
|---|---|
| Preferences file | `stats` |
| Key | `blocked_count` |

| Method | Parameters | Returns | Description |
|---|---|---|---|
| `getBlockedNotificationsCount()` | - | `Int` | Returns total blocked count. |
| `incrementBlockedNotificationsCount()` | - | `Unit` | Increments counter by 1. |

---

### `NotificationActionRepository` (`NotificationActionRepository.kt`)

In-memory cache for notification PendingIntents.

**Type:** Singleton `object` with `ConcurrentHashMap<String, PendingIntent>`

| Method | Parameters | Returns | Description |
|---|---|---|---|
| `saveAction(id, action)` | `String`, `PendingIntent` | `Unit` | Caches a PendingIntent by notification UUID. |
| `getAction(id)` | `String` | `PendingIntent?` | Retrieves cached PendingIntent. |
| `clear()` | - | `Unit` | Clears all cached intents. |

---

### `PrebuiltRulesRepository` (`PrebuiltRulesRepository.kt`)

Read-only repository loading rules from bundled assets.

| Method | Parameters | Returns | Description |
|---|---|---|---|
| `getPrebuiltRules()` | - | `List<BlockerRule>` | Reads and deserializes `assets/prebuilt_rules.json`. |

---

## UI - Screens

### `HistoryScreen` (`ui/screens/HistoryScreen.kt`)

```kotlin
@Composable
fun HistoryScreen(
    notifications: List<SimpleNotification>,
    unmonitoredApps: Set<String>,
    onNotificationClick: (SimpleNotification) -> Unit,
    onClearHistory: () -> Unit,
    onDeleteNotification: (SimpleNotification) -> Unit,
    onStopMonitoring: (String, String) -> Unit,    // (packageName, appName)
    onResumeMonitoring: (String) -> Unit            // (packageName)
)
```

Displays notification history grouped by app with expand/collapse, app icons, delete, stop monitoring, and clear history.

---

### `RulesScreen` (`ui/screens/RulesScreen.kt`)

```kotlin
@Composable
fun RulesScreen(
    rules: List<BlockerRule>,
    onRuleClick: (BlockerRule) -> Unit,
    onDeleteRuleClick: (BlockerRule) -> Unit,
    onBrowsePrebuiltRulesClick: () -> Unit
)
```

Lists all rules with type icons, hit counts, time indicators, and disabled state.

---

### `BlockedScreen` (`ui/screens/BlockedScreen.kt`)

```kotlin
@Composable
fun BlockedScreen(
    notifications: List<SimpleNotification>,
    onClearBlockedHistory: () -> Unit,
    onNotificationClick: (SimpleNotification) -> Unit,
    onDeleteNotificationClick: (SimpleNotification) -> Unit
)
```

Shows blocked notification list with details, delete, and clear history.

---

### `SettingsScreen` (`ui/screens/SettingsScreen.kt`)

```kotlin
@Composable
fun SettingsScreen(onClose: () -> Unit)
```

Settings page with history retention, export/import rules (using SAF file pickers), external links, and version display.

---

### `PrebuiltRulesScreen` (`ui/screens/PrebuiltRulesScreen.kt`)

```kotlin
@Composable
fun PrebuiltRulesScreen(
    userRules: List<BlockerRule>,
    onClose: () -> Unit,
    onAddRule: (BlockerRule) -> Unit
)
```

Shows prebuilt rules filtered to installed-but-uncovered apps with add buttons.

---

### `UnmonitoredAppsScreen` (`ui/screens/UnmonitoredAppsScreen.kt`)

```kotlin
@Composable
fun UnmonitoredAppsScreen(
    unmonitoredApps: Set<String>,
    onClose: () -> Unit,
    onResumeMonitoring: (String) -> Unit
)
```

Lists unmonitored apps with resume buttons.

---

### `EnableNotificationListenerScreen` (`ui/screens/EnableNotificationListenerScreen.kt`)

```kotlin
@Composable
fun EnableNotificationListenerScreen(onEnableClick: () -> Unit)
```

Permission request screen with informational card and settings button.

---

## UI - Components

### `AddRuleDialog` (`ui/components/Dialogs.kt`)

```kotlin
@Composable
fun AddRuleDialog(
    notification: SimpleNotification,
    onDismiss: () -> Unit,
    onAddRule: (BlockerRule) -> Unit
)
```

Pre-populates a rule dialog from a notification for creating new rules.

---

### `EditRuleDialog` (`ui/components/Dialogs.kt`)

```kotlin
@Composable
fun EditRuleDialog(
    rule: BlockerRule,
    onDismiss: () -> Unit,
    onUpdateRule: (BlockerRule, BlockerRule) -> Unit,  // (oldRule, newRule)
    onDeleteRule: (BlockerRule) -> Unit
)
```

Edits an existing rule with update and delete capabilities.

---

### `AdvancedRuleConfigDialog` (`ui/components/Dialogs.kt`)

```kotlin
@Composable
fun AdvancedRuleConfigDialog(
    initialConfig: AdvancedRuleConfig,
    initialIsEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (AdvancedRuleConfig, Boolean) -> Unit
)
```

Configures rule enable/disable state and time-window scheduling.

---

### `TimeSelector` (`ui/components/Dialogs.kt`)

```kotlin
@Composable
fun TimeSelector(
    label: String,
    hour: Int,
    minute: Int,
    onTimeSelected: (Int, Int) -> Unit
)
```

Reusable time picker row with label and native `TimePickerDialog`.

---

### `HistoryNotificationDetailsDialog` (`ui/components/HistoryNotificationDetailsDialog.kt`)

```kotlin
@Composable
fun HistoryNotificationDetailsDialog(
    notification: SimpleNotification,
    isActionAvailable: Boolean,
    onDismiss: () -> Unit,
    onTriggerAction: () -> Unit,
    onCreateRule: () -> Unit
)
```

Shows notification details with "Open" (trigger PendingIntent) and "Create Rule" actions. Long-press on values copies to clipboard.

---

### `NotificationDetailsDialog` (`ui/components/NotificationDetailsDialog.kt`)

```kotlin
@Composable
fun NotificationDetailsDialog(
    notification: SimpleNotification,
    onDismiss: () -> Unit,
    onViewRule: (() -> Unit)? = null
)
```

Shows blocked notification details with optional "View Rule" button.

---

### `AutoAddedRulesDialog` (`ui/components/AutoAddedRulesDialog.kt`)

```kotlin
@Composable
fun AutoAddedRulesDialog(
    addedApps: List<String>,
    onDismiss: () -> Unit,
    onDoNotShowAgain: () -> Unit
)
```

Informs user about auto-installed prebuilt rules.

---

### `DeleteConfirmationDialog` (`ui/components/DeleteConfirmationDialog.kt`)

```kotlin
@Composable
fun DeleteConfirmationDialog(
    itemName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
)
```

Generic deletion confirmation with Cancel/Delete buttons.

---

### `AboutDialog` (`ui/components/AboutDialog.kt`)

```kotlin
@Composable
fun AboutDialog(onDismiss: () -> Unit)
```

Shows app name, version, and developer contact.

---

## UI - Theme

### `DoNotNotifyTheme` (`ui/theme/Theme.kt`)

```kotlin
@Composable
fun DoNotNotifyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
)
```

Root theme composable. Dynamic color (Material You) is disabled by default. Uses custom light/dark color schemes with blue primary palette.

### Color Palette (`ui/theme/Color.kt`)

Defines 34 color values each for light and dark themes following Material 3 color roles (primary, secondary, tertiary, error, background, surface, etc.).

| Role | Light | Dark |
|---|---|---|
| Primary | `#00639A` | `#92CCFF` |
| Background | `#FDFCFF` | `#2B2D30` |
| Error | `#BA1A1A` | `#FFB4AB` |

### Typography (`ui/theme/Type.kt`)

Custom `bodyLarge` style: Default font family, normal weight, 16sp, 24sp line height, 0.5sp letter spacing. All other styles use Material 3 defaults.

---

## MainActivity

### `MainActivity` (`MainActivity.kt`)

**Extends:** `ComponentActivity`

Root activity and state coordinator.

#### Lifecycle Methods

| Method | Description |
|---|---|
| `onCreate(savedInstanceState)` | Enables edge-to-edge, initializes storage, checks for new prebuilt rules, sets Compose content with theme |
| `onResume()` | Refreshes service status, notification lists, rules, and unmonitored apps |

#### Private Methods

| Method | Parameters | Returns | Description |
|---|---|---|---|
| `checkForNewRules()` | - | `List<String>` | Scans installed apps against prebuilt rules, auto-installs new ones, returns list of newly added app names |
| `openNotificationListenerSettings()` | - | `Unit` | Launches `ACTION_NOTIFICATION_LISTENER_SETTINGS` intent |
| `isNotificationServiceEnabled()` | - | `Boolean` | Checks if app is in enabled notification listeners |

#### Composable Methods

| Method | Description |
|---|---|
| `MainScreen()` | Root composable managing navigation, broadcast receiver, and all dialog states |
| `TabbedScreen(...)` | Three-tab layout with History, Rules, and Blocked screens |
| `PagerScreenContent(...)` | Renders the appropriate screen for each pager page |

#### Extension Functions

| Function | Signature | Description |
|---|---|---|
| `Color.luminance()` | `fun Color.luminance(): Float` | Calculates relative luminance using standard coefficients (0.2126R + 0.7152G + 0.0722B) |
