# Keep the accessibility and tile services reachable by their manifest names.
-keep class com.gbhall.childlock.guard.GuardAccessibilityService { *; }
-keep class com.gbhall.childlock.tile.LockTileService { *; }
-keep class com.gbhall.childlock.lock.LockOverlayService { *; }
