# DoNotNotify - Developer Guide

A practical guide for developers working on the DoNotNotify codebase.

---

## Table of Contents

- [Getting Started](#getting-started)
- [Build & Run](#build--run)
- [How to Add a New Feature](#how-to-add-a-new-feature)
- [How to Add a New Prebuilt Rule](#how-to-add-a-new-prebuilt-rule)
- [How to Add a New Storage Mechanism](#how-to-add-a-new-storage-mechanism)
- [How to Add a New Screen](#how-to-add-a-new-screen)
- [How to Add a New Dialog](#how-to-add-a-new-dialog)
- [Understanding the Rule System](#understanding-the-rule-system)
- [Key Design Decisions](#key-design-decisions)
- [Common Patterns](#common-patterns)
- [Testing](#testing)
- [Release Process](#release-process)

---

## Getting Started

### Prerequisites

- Android Studio (latest stable)
- JDK 11+
- Android SDK with API level 36 installed
- An Android device or emulator running API 24+

### Project Setup

1. Clone the repository
2. Open in Android Studio (project auto-syncs Gradle)
3. Build: `./gradlew assembleDebug`
4. Run on device: `./gradlew installDebug`

### Important: Notification Listener Permission

The app requires `BIND_NOTIFICATION_LISTENER_SERVICE` permission, which can only be granted via system settings. On first launch, the app shows an `EnableNotificationListenerScreen` that directs users to the system settings.

For development/testing, navigate to **Settings > Apps > Special app access > Notification access** and enable DoNotNotify.

---

## Build & Run

```bash
# Debug APK
./gradlew assembleDebug

# Release APK (R8 minification enabled)
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug

# Run unit tests
./gradlew test

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Clean build artifacts
./gradlew clean
```

---

## How to Add a New Feature

### Example: Adding a "Snooze Rule" Feature

1. **Update the data model** - Add fields to `BlockerRule` in `BlockerRule.kt`:
   ```kotlin
   val snoozeUntil: Long? = null  // Timestamp when snooze expires
   ```
   Since `BlockerRule` uses `@Keep` and Gson, new nullable fields with defaults are backward-compatible with existing stored JSON.

2. **Update rule evaluation** - Modify `RuleMatcher.matches()` in `RuleMatcher.kt` to check the snooze timestamp.

3. **Update the UI** - Add snooze controls to `RuleDialog` in `ui/components/Dialogs.kt` or create a new dialog component.

4. **Update the service** - If the feature affects notification processing, modify `NotificationBlockerService.onNotificationPosted()`.

5. **Add tests** - Add test cases to `RuleMatcherTest.kt`.

### File modification order

For most features, the modification order is:
1. Data models (`BlockerRule.kt`, `SimpleNotification.kt`)
2. Business logic (`RuleMatcher.kt`)
3. Storage (if new storage needed)
4. Service (`NotificationBlockerService.kt`)
5. UI components (`ui/components/`)
6. UI screens (`ui/screens/`)
7. Activity wiring (`MainActivity.kt`)
8. Tests

---

## How to Add a New Prebuilt Rule

1. **Edit `app/src/main/assets/prebuilt_rules.json`** - Add a new entry:
   ```json
   {
     "appName": "AppName",
     "packageName": "com.example.app",
     "titleFilter": null,
     "titleMatchType": "CONTAINS",
     "textFilter": "(?i).*(spam|promo|offer).*",
     "textMatchType": "REGEX",
     "hitCount": 0,
     "ruleType": "BLACKLIST",
     "isEnabled": true
   }
   ```

2. **Add package query to `AndroidManifest.xml`** - Required for Android 11+ package visibility:
   ```xml
   <queries>
       <package android:name="com.example.app" />
   </queries>
   ```

3. **Test** - Install the target app on a test device, then launch DoNotNotify. The auto-install logic in `MainActivity.checkForNewRules()` should detect and install the rule. Check via the "Browse Pre-built Rules" screen.

### Tips for writing regex patterns

- Use `(?i)` prefix for case-insensitive matching
- Wrap with `.*` for partial matching: `(?i).*(keyword1|keyword2).*`
- `MatchType.REGEX` uses `String.matches()` which requires the pattern to match the **entire string**
- Test patterns against real notification text from the History tab

---

## How to Add a New Storage Mechanism

The app uses four storage patterns. Choose based on your needs:

### JSON File (for structured lists)
Follow `RuleStorage` pattern:
```kotlin
class MyStorage(private val context: Context) {
    private val gson = Gson()
    private val file = File(context.filesDir, "my_data.json")

    fun getData(): List<MyData> {
        if (!file.exists()) return emptyList()
        val type = object : TypeToken<List<MyData>>() {}.type
        return gson.fromJson(file.readText(), type) ?: emptyList()
    }

    fun saveData(data: List<MyData>) {
        file.writeText(gson.toJson(data))
    }
}
```

### SharedPreferences (for simple key-value data)
Follow `StatsStorage` pattern for primitives, or `UnmonitoredAppsStorage` pattern for Gson-serialized sets.

### SQLite (for large or queryable data)
Follow `AppInfoStorage` / `AppInfoDatabaseHelper` pattern. Use `SQLiteOpenHelper` subclass for schema management.

### In-Memory (for transient session data)
Follow `NotificationActionRepository` pattern using a singleton `object` with `ConcurrentHashMap`.

### Integration

After creating a storage class:
1. Add a `lateinit var` field in `NotificationBlockerService` and/or `MainActivity`
2. Initialize in `onCreate()`
3. Use from the appropriate callback or composable

---

## How to Add a New Screen

1. **Create the screen composable** in `ui/screens/`:
   ```kotlin
   @Composable
   fun MyNewScreen(
       data: List<MyData>,
       onClose: () -> Unit,
       onAction: (MyData) -> Unit
   ) {
       Scaffold(
           topBar = {
               TopAppBar(
                   title = { Text("My Screen") },
                   navigationIcon = {
                       IconButton(onClick = onClose) {
                           Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                       }
                   }
               )
           }
       ) { paddingValues ->
           // Screen content
       }
   }
   ```

2. **Add navigation state** in `MainActivity`:
   ```kotlin
   private var showMyScreen by mutableStateOf(false)
   ```

3. **Wire into `MainScreen()`** - Add a condition in the screen switching logic:
   ```kotlin
   if (showMyScreen) {
       BackHandler { showMyScreen = false }
       MyNewScreen(
           data = myData,
           onClose = { showMyScreen = false },
           onAction = { /* handle */ }
       )
   }
   ```

The app uses manual boolean-based navigation rather than Jetpack Navigation. Each screen is a full-screen composable shown conditionally.

---

## How to Add a New Dialog

1. **Create the dialog composable** in `ui/components/`:
   ```kotlin
   @Composable
   fun MyDialog(
       data: MyData,
       onDismiss: () -> Unit,
       onConfirm: (MyData) -> Unit
   ) {
       Dialog(onDismissRequest = onDismiss) {
           Card {
               Column(modifier = Modifier.padding(16.dp)) {
                   // Dialog content
                   Row(
                       modifier = Modifier.fillMaxWidth(),
                       horizontalArrangement = Arrangement.End
                   ) {
                       Button(onClick = onDismiss) { Text("Cancel") }
                       Spacer(modifier = Modifier.width(8.dp))
                       Button(onClick = { onConfirm(data) }) { Text("Confirm") }
                   }
               }
           }
       }
   }
   ```

2. **Add trigger state** in `MainActivity.MainScreen()`:
   ```kotlin
   var itemToShowDialog by remember { mutableStateOf<MyData?>(null) }
   ```

3. **Show conditionally**:
   ```kotlin
   itemToShowDialog?.let { item ->
       MyDialog(
           data = item,
           onDismiss = { itemToShowDialog = null },
           onConfirm = { /* handle */ }
       )
   }
   ```

The pattern uses nullable state variables: setting to non-null shows the dialog, setting to null dismisses it.

---

## Understanding the Rule System

### Evaluation Priority

1. Rules are scoped per package (`packageName`)
2. Only enabled rules are evaluated
3. Within a package, whitelist rules are checked first
4. Then blacklist rules are checked
5. A notification is blocked if:
   - There are whitelist rules for the package AND none matched, OR
   - Any blacklist rule matched
6. **Blacklist wins over whitelist** - if both match, the notification is blocked

### Filter Behavior

- Null or blank filter = matches everything (wildcard)
- Both title and text filters must match for a rule to match (AND logic)
- `CONTAINS` is case-insensitive
- `REGEX` uses full-string matching (`String.matches()`), so patterns like `.*keyword.*` are needed for partial matches

### Time Windows

- When `isTimeLimitEnabled` is true, the rule only activates during the configured time window
- Supports overnight ranges (e.g., 22:00 to 06:00) - handles midnight crossing
- Time is evaluated in the device's current timezone

### Hit Counting

- Each time a rule matches, its `hitCount` is incremented
- The updated rule list is saved back to storage
- Hit counts are visible in the Rules screen UI

---

## Key Design Decisions

### No Architecture Framework
The app uses direct state management with Compose's `mutableStateOf` rather than ViewModel/LiveData/StateFlow. State lives in `MainActivity` and is passed down as parameters. This works for the app's scale but would need refactoring for significantly more complex features.

### Full List Replacement
All JSON storage classes replace the entire file on every write. This is simple and correct for the app's data sizes (typically <100 rules, <1000 notifications) but wouldn't scale to very large datasets.

### Boolean Navigation
Screen navigation uses boolean flags (`showSettingsScreen`, `showPrebuiltRulesScreen`) rather than Jetpack Navigation or a router. This keeps the dependency set minimal but limits deep linking and transition animations.

### Single Module
Everything lives in the `:app` module. For a project of this size (~3,200 lines), this is appropriate and avoids unnecessary build complexity.

### No Network
The app declares zero network permissions and makes no HTTP requests. All data stays on-device.

### Gson over Kotlin Serialization
The project uses Gson for JSON serialization. Data classes use `@Keep` annotations to survive R8 minification (Gson uses reflection).

---

## Common Patterns

### Storage Access in Composables
Storage classes are instantiated via `remember { Storage(context) }` within composables when needed locally (e.g., `AppInfoStorage` in `HistoryScreen`).

### Async Image Loading
App icons are loaded from SQLite using `produceState` with `Dispatchers.IO`:
```kotlin
val appIcon by produceState<Bitmap?>(initialValue = null, key1 = packageName) {
    if (packageName != null) {
        value = withContext(Dispatchers.IO) {
            appInfoStorage.getAppIcon(packageName)
        }
    }
}
```

### Debouncing
`NotificationBlockerService` uses a time-based debounce map to avoid recording duplicate notifications within 5 seconds. The map is cleaned on each notification processing cycle.

### Deduplication in Storage
Both history storage classes deduplicate by content (appLabel, packageName, title, text) - if a notification with the same content arrives, the old entry is removed and the new one is prepended (updating the timestamp).

---

## Testing

### Unit Tests

Located in `app/src/test/java/com/donotnotify/donotnotify/`.

Run: `./gradlew test`

The primary test class is `RuleMatcherTest` which covers:
- Empty rule sets
- Blacklist matching and non-matching
- Whitelist matching and implicit blocking
- Blacklist priority over whitelist
- Regex matching
- Disabled rule handling
- Real-world regex patterns (Mygate)

### Writing New Tests

Add tests to `RuleMatcherTest` or create new test classes. Use the existing pattern:
```kotlin
@Test
fun `descriptive test name`() {
    val rule = BlockerRule(
        packageName = "com.example.app",
        titleFilter = "pattern",
        ruleType = RuleType.BLACKLIST
    )
    val result = RuleMatcher.shouldBlock("com.example.app", "title", "text", listOf(rule))
    assertTrue(result)  // or assertFalse
}
```

### Instrumented Tests

Located in `app/src/androidTest/`. Currently contains a basic context test. Run with:
```bash
./gradlew connectedAndroidTest
```

---

## Release Process

### Version Bumping

Update in `app/build.gradle.kts`:
```kotlin
defaultConfig {
    versionCode = 28          // Increment for each release
    versionName = "2.62"      // Human-readable version
}
```

### Building Release

```bash
./gradlew assembleRelease
```

Release builds use R8 minification (`isMinifyEnabled = true`). Data classes are preserved via `@Keep` annotations.

### Store Metadata

Fastlane metadata is in `fastlane/metadata/android/en-US/`:
- `title.txt` - App title
- `short_description.txt` - Short description
- `full_description.txt` - Full store description
- `changelogs/{versionCode}.txt` - Version-specific changelog
- `images/` - Screenshots and icon
