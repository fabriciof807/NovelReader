# Decisions

## 2026-11-10 — The destination owns the system bar tone; the tab request goes to the current entry

- **Decision:** each screen applies its own status-bar icon tone on `ON_RESUME` (`SystemBarAppearance`), instead of the reader capturing the tone it found and restoring it on dispose; `WallpaperVariantTheme` restores only while its entry is resumed. With the reader's top bar visible the tone follows the bar composited over the reader background (`statusBarBehindIsDark`). A Settings -> Library/Collections move writes the tab request to the entry that is current after `navigate`, and the library route applies it (`LibraryTabRequests`).
- **Reason:** a tone captured per reader instance was wrong after reader -> search result -> back -> back, and a tone chosen from the reader background alone left dark icons on a dark translucent bar over a light reader. The back stack entry's `savedStateHandle` is not the handle the Hilt ViewModel is built with, so the old write never arrived and a `runCatching` hid it.
- **Trade-off:** compositing only over the reader background ignores the wallpaper behind a translucent bar, so a weak veil over an opposite-tone wallpaper can misjudge the tone near the 0.5 luminance threshold. The observer registered by `NovelReaderTheme` still re-applies the app tone on every Activity resume and only loses to the NavHost entries because of registration order (no test covers an Activity resume). Known and accepted for v2.13.0.
- **Known polish, not fixed:** at 360dp the library's one-line top bar truncates "Library" to "Li…" (the title disappears at font scale 2.0), and the bottom bar labels truncate at font scale 2.0.

## 2026-11-09 — Import failures get their own notification channel

- **Decision:** a hard import failure (`postFailureNotification`, i.e. `Result.failure` on the final batch) posts on the new `novel_import_error_channel` at `IMPORTANCE_DEFAULT` with the badge on, keeping `CATEGORY_ERROR` and its `ACTION_OPEN_FAILED_CHAPTERS` deep link. Progress and completion — including completion with partial errors — stay on `novel_import_channel` (`IMPORTANCE_LOW`, no badge).
- **Reason:** a channel's importance can never be raised after creation, so a failure on the progress channel would stay as quiet as progress for good; a new channel was the only way to make a failure audible. Only hard failures move: issue #25 fixed that a partial import reads as a completion, and moving that one would undo it.
- **Trade-off:** the channel is permanent on a device and its name/description cannot be edited afterwards, hence the strings in both locales are final for the first release. Notifications posted before the update stay on the old channel.

## 2026-11-05 — Immersive reader, bottom navigation and one-line counters

- **Reader:** opens with only the text; a centre tap shows the top bar and the options bar together, auto-hiding after 4s. This replaces the v2.9.2 decision to keep the top bar always visible. Reason: the text gets the whole screen, and the two-tier chrome (always-on bar plus tap-to-toggle bar) read as clutter. Search, the sleep timer and the optional battery indicator stay visible when they have something to say.
- **Navigation:** Favorites and Settings become destinations of a four-item bottom bar (Library, Collections, Favorites, Settings) instead of top-bar actions. Routes and deep links are unchanged.
- **Counters:** `LibraryStatsBar` is replaced by the one-line `LibraryStatsLine` above the nav bar.
- **Battery indicator:** optional (`reader_battery_always_on`, default off), included in backup v3; an invalid or missing value reads as off.
- **Motion:** press feedback (0.97), springs on interruptible animations and zero durations with the system's "remove animations" (`AppMotion`).
- **Known trade-offs:** nav bar is 46dp (2dp under the 48dp target) pending a device test; the reader's bar buttons stay tappable during the 150ms fade-out; the "Reading · time" badge was dropped from novel cards.
