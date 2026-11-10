# Import error notification channel (issue #24)

Status: draft, awaiting approval. No code until approved.

## Problem

Every import notification shares `ImportNotificationHelper.CHANNEL_ID = "novel_import_channel"` at
`IMPORTANCE_LOW` with the badge off. That is right for the foreground progress notification, but it
makes a hard failure (`postFailureNotification`) as quiet as progress: no sound, no badge. A channel's
importance cannot be raised after creation, so the only way out is a **new** channel, which stays in
the device's system settings for good. The decision is one-way, hence this spec.

## Decisions (agreed with the user, 2026-11-09)

1. **Scope.** Only hard failures (`postFailureNotification`, i.e. `Result.failure` on the final batch)
   go to the new channel. Completion with partial errors stays a completion on `novel_import_channel`,
   as fixed by issue #25.
2. **Importance.** `IMPORTANCE_DEFAULT` (sound, no heads-up). The user can lower it in system settings;
   the reverse is impossible on a channel born low.
3. **Badge and category.** `setShowBadge(true)` on the new channel; the notification keeps
   `CATEGORY_ERROR`. The progress channel keeps its badge off.
4. **Unchanged.** Foreground progress (`ForegroundInfo`, tied to the unique work name
   `chapter_import_active`) and the clean/partial completion stay on `novel_import_channel`.

## Design

- `ImportNotificationHelper.ERROR_CHANNEL_ID = "novel_import_error_channel"`.
- `ensureErrorChannel()` mirrors `ensureChannel()`: below SDK 26 nothing, otherwise create the channel
  only when absent, name/description from string resources.
- `postFailureNotification` calls `ensureErrorChannel()` and builds on `ERROR_CHANNEL_ID`. Its id
  (`COMPLETION_NOTIFICATION_ID + 1`), title, `ACTION_OPEN_FAILED_CHAPTERS` target and request code do
  not change.
- Strings in both `values/` (English "Import failures") and `values-pt/` (Portuguese
  "Falhas de importação"): channel name and description.
- The intent still goes through `novelPendingIntent`, so it carries `EXTRA_DEEP_LINK_TOKEN`; no new
  intent leaves the app (piolium F4).

## Out of scope

- Moving completion-with-errors to the error channel.
- Any change to when a failure is notified (the final-batch rule stays).
- Migrating or deleting notifications already posted on the old channel; a failure notification
  posted before the update simply stays where it was.
- `POST_NOTIFICATIONS` policy: unchanged (still asked only at a user-started import).

## Risks

- The channel is permanent once created on a device; a wrong name or description cannot be edited
  later by re-creating it with the same id. The strings above are final for the first release.
- No `docs/security-residual-risks.md` change: no egress, permission or intent posture changes.

## Exit criteria

- [ ] A channel `novel_import_error_channel` exists at `IMPORTANCE_DEFAULT` with badge on, created
      lazily by `postFailureNotification`, with EN and PT name/description.
- [ ] `postFailureNotification` posts on it; `createForegroundInfo` and `postCompletionNotification`
      (with and without errors) still post on `novel_import_channel`.
- [ ] The failure notification keeps `CATEGORY_ERROR`, its deep-link target and token.
- [ ] Unit tests cover: channel properties, failure on the error channel, progress and completion on
      the old one, no channel created below SDK 26.
- [ ] `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` passes.
- [ ] `docs/DECISIONS.md` records the channel decision; `AGENTS.md` notification section mentions it.
