# Reader: configurable swipe direction (Webnovel-style)

**Date:** 2026-07-10
**Project:** NovelReader

## Problem

The reader's swipe handler (`ReaderHtmlBuilder.kt:178-193`) only detects
horizontal swipes (swipe-left = next chapter, swipe-right = previous). The
user wants the Webnovel pattern: swipe-up = next chapter, swipe-down = previous,
with the direction configurable in Settings.

## Goals

1. Default: swipe-up/down navigates chapters (Webnovel-style).
2. Settings → "Swipe direction" allows switching between 4 options:
   **Vertical** / **Horizontal** / **Both** / **Off**.
3. Preference change reflects immediately in the reader (no WebView restart).
4. Slow accidental text scrolling does NOT trigger chapter navigation.

## Non-goals

- Tap-gesture page turn (tap continues toggling controls).
- Transition animation between chapters.
- Advanced sensitivity settings (threshold, debounce).
- Visual "swipe to next chapter" indicator.
- i18n beyond PT + EN.

## Design

### Data model

`ReaderConfig` gains a new field:

```kotlin
data class ReaderConfig(
    val fontSize: Int = 20,
    val fontFamily: String = "serif",
    val lineHeight: Float = 1.8f,
    val theme: String = "light",
    val autoScrollSpeed: Float = 0f,
    val keepScreenOn: Boolean = true,
    val swipeDirection: String = "vertical"  // NEW
)
```

Valid values: `"vertical"`, `"horizontal"`, `"both"`, `"none"`.

`ReaderPreferences` gains:

```kotlin
private object Keys {
    val SWIPE_DIRECTION = stringPreferencesKey("swipe_direction")
}

suspend fun updateSwipeDirection(direction: String) {
    context.dataStore.edit { it[Keys.SWIPE_DIRECTION] = direction }
}
```

### JS swipe handler

The horizontal-only handler in `ReaderHtmlBuilder.kt` (lines 178-193) is
replaced with a direction-configurable handler. A global `_swipeDir` variable
holds the current setting; `applyConfig(cfg)` updates it at runtime.

```javascript
var _swipeDir = '${config.swipeDirection}';
(function() {
    var _ts = {x:0, y:0, t:0};
    document.addEventListener('touchstart', function(e) {
        var t = e.touches[0]; _ts = {x: t.clientX, y: t.clientY, t: Date.now()};
    });
    document.addEventListener('touchend', function(e) {
        var dx = e.changedTouches[0].clientX - _ts.x;
        var dy = e.changedTouches[0].clientY - _ts.y;
        var dt = Date.now() - _ts.t;
        if (Math.abs(dx) < 15 && Math.abs(dy) < 15 && dt < 300) {
            try { Android.onTap(); } catch(e) {}
            return;
        }
        if (dt > 500) return;  // slow drag = scroll, not swipe
        var dir = null;
        if ((_swipeDir === 'vertical' || _swipeDir === 'both') &&
            Math.abs(dy) > 60 && Math.abs(dy) > Math.abs(dx) * 1.5) {
            dir = dy < 0 ? 'next' : 'prev';
        }
        if (dir === null &&
            (_swipeDir === 'horizontal' || _swipeDir === 'both') &&
            Math.abs(dx) > 60 && Math.abs(dx) > Math.abs(dy) * 1.5) {
            dir = dx < 0 ? 'next' : 'prev';
        }
        if (dir !== null) {
            try { Android.onSwipe(dir); } catch(e) {}
        }
    });
})();
```

`applyConfig` gains one line:

```javascript
function applyConfig(cfg) {
    _swipeDir = cfg.swipeDirection;
    // ... rest unchanged
}
```

`applyConfigJs` passes `swipeDirection` in its payload:

```kotlin
fun applyConfigJs(config: ReaderConfig): String {
    val map = themeVars(config) + mapOf(
        "fontFamily" to config.fontFamily,
        "fontSize" to config.fontSize,
        "lineHeight" to config.lineHeight,
        "autoScrollSpeed" to config.autoScrollSpeed,
        "swipeDirection" to config.swipeDirection   // NEW
    )
    return buildJs(code = "applyConfig(args.args);", params = mapOf("args" to payload))
}
```

### Settings UI

`ReaderSettingsSheet.kt` gains a new section before the auto-scroll section:

```
--- divider ---
"Swipe direction" (titleSmall)

4 chips in a Row (reusing ThemeOption composable):
  [ArrowUpward]  [ArrowBack]   [Both]         [X]   
  Vertical       Horizontal    Both           Off
  (selected)     (selected)    (selected)     (selected)
--- divider ---
```

New parameter:

```kotlin
fun SettingsSheet(
    config: ReaderConfig,
    // ... existing params ...
    onSwipeDirectionChange: (String) -> Unit = {},
    onDismiss: () -> Unit
)
```

### Wiring

- `ReaderViewModel.updateSwipeDirection(direction: String)` calls
  `readerPreferences.updateSwipeDirection(direction)`.
- `ReaderScreen.SettingsSheet(...)` passes
  `onSwipeDirectionChange = viewModel::updateSwipeDirection`.
- The existing `LaunchedEffect(state.config, webView)` (screen lines 145-148)
  re-evaluates `applyConfigJs(state.config)`, which includes the new
  `swipeDirection` field. The JS `applyConfig` updates `_swipeDir` in-place.

### i18n

| Key | PT | EN |
|-----|----|----|
| `reader_swipe_direction` | Direção do swipe | Swipe direction |
| `reader_swipe_vertical` | Vertical | Vertical |
| `reader_swipe_horizontal` | Horizontal | Horizontal |
| `reader_swipe_both` | Ambos | Both |
| `reader_swipe_none` | Desligado | Off |

### Testing

1. **`ReaderPreferencesTest`** (+2 tests):
   - `swipeDirection defaults to vertical`
   - `updateSwipeDirection persists and reads back for all 4 values`

2. **`ReaderHtmlBuilderTest`** (+4 tests):
   - `buildReaderHtml with swipeDirection=vertical contains vertical-only swipe JS`
   - `buildReaderHtml with swipeDirection=horizontal contains horizontal-only swipe JS`
   - `buildReaderHtml with swipeDirection=both contains both branches in swipe JS`
   - `buildReaderHtml with swipeDirection=none contains swipe handler that never fires`
   - `applyConfigJs passes swipeDirection to applyConfig`

3. **`ReaderViewModelTest`** (+1 test):
   - `updateSwipeDirection forwards value to readerPreferences`

### Error handling

| Condition | Behavior |
|-----------|----------|
| Invalid value in DataStore | `ReaderConfig.swipeDirection` defaults to `"vertical"` via `?.takeIf { it in setOf("vertical","horizontal","both","none") } ?: "vertical"` |
| No prev/next chapter | `state.nextChapterId == null` → `goToNextChapter()` no-ops (existing behavior) |
| WebView destroyed | Handler freed with WebView; no effect |
| Auto-scroll + swipe | `touchstart` pauses auto-scroll; new chapter may restart it (existing behavior) |

### Manual verification

```
./gradlew :app:installDebug
```

1. Open reader, slow drag vertically: no chapter change.
2. Fast swipe up: next chapter.
3. Settings → Horizontal → swipe left: next chapter.
4. Settings → Off → any swipe: nothing.
5. Settings → Both → vertical and horizontal swipes both work.
