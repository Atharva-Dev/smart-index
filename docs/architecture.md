# Architecture

A short tour of how Smart Index is built. For build instructions see the [README](../README.md).

## Overview

Smart Index keeps its own searchable copy of metadata from the phone's built-in providers. It never copies file
contents, and the copy lives in app-private storage.

```text
 MediaStore ─┐
 CallLog ────┤            ┌──────────────┐        ┌────────────────┐
 Telephony ──┼─ sources ─▶│ :sync        │─ write▶│ :core:index    │◀─ read ─ feature:home
 Contacts ───┘ (per type) │ orchestration│        │ Room + FTS5    │
 Recordings ─ matcher ───▶│ (WorkManager)│        └────────────────┘
                          └──────────────┘
```

| Module | Responsibility |
|---|---|
| `:core:model` | Shared contracts (items, permissions, index and sync interfaces, config). No Android dependencies beyond basics. |
| `:core:index` | Room database, FTS5 trigram table, search planner, paging, counts, reconcile and purge. |
| `:source:media` | Reads MediaStore files, classifies them (photo, video, document, other), loads thumbnails, resolves open-file URIs. |
| `:source:telephony` | Indexes calls, SMS and MMS and resolves contact names. |
| `:source:recordings` | Parses Samsung recording file names and matches each recording to a call. |
| `:sync` | Runs the indexers: first full pass, incremental passes, permission gating, scheduling, progress. |
| `:feature:*` | Home (timeline and search), player, onboarding, index status. |
| `:core:permissions`, `:core:designsystem` | Permission groups and the Compose theme. |

## Search

- Every item has one normalised search key built from its name, folder, extension, phone numbers, contact
  names and message text. Text is folded (Unicode NFC, lower-cased with the root locale) before it is stored and
  before the query is compared, with an offset map so highlights point at the original text.
- Queries of three or more characters use an FTS5 trigram table, which gives exact contiguous-substring
  matching. Queries of one or two characters cannot use trigrams and use a bounded scan over the key column,
  with an early stop once a page is full.
- Result pages and per-type counts are separate queries, so the first page is never held up by counting. For
  one- and two-character queries counts start after the first page arrives.
- Text search is newest first. Date-only browsing is oldest to newest, grouped by part of the day.
- Group MMS keep every participant, so each known participant's saved name matches.

## Indexing

- Each source has one indexer with a full mode (first run, re-index) and an incremental mode (cursor-based).
  Full runs reconcile: rows whose source item no longer exists are removed.
- Triggers: content-URI triggers, a 15-minute periodic pass, and a catch-up when the app comes to the
  foreground. Runs are serialised through a two-slot WorkManager queue, and one failing source never stops the
  others.
- A type is indexed only while its permission group is granted. Revoking a group purges that type's rows.
  Re-indexing resets cursors and rewrites rows; it never empties the index first.

## Call recordings

Recordings are audio files under `Recordings/Call/` or `Call/` on any volume. The parser reads the vendor file
name pattern (contact or number plus a timestamp), and candidates are scored against calls by time window and
duration. Assignment is one-to-one and greedy, with a score threshold; below it a recording stays unlinked.
Only Samsung naming is supported today.

## Privacy by construction

The build fails if any variant requests a network permission, enables backup, or exports a component other than
the launcher activity. Module boundaries are checked too, so a feature cannot read a source directly. See the
README for the exact tasks.

## Performance notes

- Budget: first page of results under 1,000 ms at p95 on a real device with about 283,000 items.
- Rare one- and two-character queries are a full scan by design. If a device measurement shows the first page
  above 1 s warm or 3 s cold, the planned next step is a bigram lookup table for short queries.
- The index is roughly 115–140 MB at that scale. Measurement procedures are in
  [`perf-procedures.md`](perf-procedures.md).
