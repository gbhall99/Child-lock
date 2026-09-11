# Keep the accessibility and tile services reachable by their manifest names.
-keep class com.gbhall.childlock.guard.GuardAccessibilityService { *; }
-keep class com.gbhall.childlock.tile.LockTileService { *; }
-keep class com.gbhall.childlock.lock.LockOverlayService { *; }

# Settings are persisted by enum constant name, so obfuscating them would
# silently reset every preference on upgrade.
-keepclassmembers enum com.gbhall.childlock.** { *; }
-keep class com.gbhall.childlock.ChildLockApp
