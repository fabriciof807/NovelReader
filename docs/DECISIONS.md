# Decisions

## 2026-11-05 — Immersive reader, bottom navigation and one-line counters

- **Reader:** opens with only the text; a centre tap shows the top bar and the options bar together, auto-hiding after 4s. This replaces the v2.9.2 decision to keep the top bar always visible. Reason: the text gets the whole screen, and the two-tier chrome (always-on bar plus tap-to-toggle bar) read as clutter. Search, the sleep timer and the optional battery indicator stay visible when they have something to say.
- **Navigation:** Favorites and Settings become destinations of a four-item bottom bar (Library, Collections, Favorites, Settings) instead of top-bar actions. Routes and deep links are unchanged.
- **Counters:** `LibraryStatsBar` is replaced by the one-line `LibraryStatsLine` above the nav bar.
- **Battery indicator:** optional (`reader_battery_always_on`, default off), included in backup v3; an invalid or missing value reads as off.
- **Motion:** press feedback (0.97), springs on interruptible animations and zero durations with the system's "remove animations" (`AppMotion`).
- **Known trade-offs:** nav bar is 46dp (2dp under the 48dp target) pending a device test; the reader's bar buttons stay tappable during the 150ms fade-out; the "Reading · time" badge was dropped from novel cards.
