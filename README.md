# DoNotNotify

An Android app that filters and blocks unwanted notifications using customizable rules.

## Features

- **Notification Blocking** - Block notifications in real-time using Android's NotificationListenerService
- **Flexible Rules** - Create blacklist (block matching) or whitelist (allow only matching) rules
- **Pattern Matching** - Match notification title/text using simple contains or regex patterns
- **Time-Based Rules** - Schedule rules to activate only during specific time windows
- **Prebuilt Rules** - 40+ pre-configured rules for popular apps (e-commerce promos, social media, etc.)
- **Auto-Install Rules** - Automatically adds relevant prebuilt rules when you install supported apps
- **Notification History** - View all received notifications with configurable retention
- **Blocked History** - Track which notifications were blocked and by which rules
- **Import/Export** - Backup and restore your rules as JSON files
- **Fully Offline** - No network permissions, no data collection

## Requirements

- Android 7.0 (API 24) or higher
- Notification listener permission

## Building

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Install debug build on connected device
./gradlew installDebug
```

## How It Works

1. Grant notification listener permission when prompted
2. View incoming notifications in the **History** tab
3. Long-press a notification to create a blocking rule, or go to **Rules** tab to browse prebuilt rules
4. Blocked notifications appear in the **Blocked** tab
5. Tap any blocked notification to view details or edit the rule that blocked it

## License

MIT License - see [LICENSE](LICENSE) for details.
