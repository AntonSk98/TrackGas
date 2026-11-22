# Tasks

## 1. Period-scoped and paginated storage queries

- [x] 1.1 Add a global reading-date index and Room 7-to-8 migration; verify migration preserves existing readings and the generated Room schema matches.
- [x] 1.2 Add repository queries for selected-period readings with per-installation boundary neighbors and keyset History pages with estimate/starting-value context; verify boundary, ordering, and cursor behavior with database tests.

## 2. Independent Dashboard and History data state

- [x] 2.1 Replace the ViewModel's startup full-history observation with selected-period data loading; verify startup requests only current-period inputs and stale period results cannot replace the selected period.
- [x] 2.2 Add on-demand History page state, continuation, retry, and refresh after committed mutations; verify failures retain loaded pages and mutation results do not introduce duplicates.
- [x] 2.3 Verify filtered Dashboard projections match full-history results for intervals crossing period boundaries, meter replacements, gaps, temperatures, and all period types.

## 3. Paginated History presentation

- [x] 3.1 Request the first History page only when History is selected and append pages near the list end; verify Dashboard startup does not wait for History and scrolling reaches all readings in order.
- [x] 3.2 Render page-boundary estimates and starting-reading markers from neighbor context; verify these match full-history History projections, including first and last pages.

## 4. Integration verification

- [x] 4.1 Run the relevant unit and database tests plus OpenSpec validation; verify load and error states, period navigation, tab/scroll behavior, and existing chart/history semantics remain correct.

## 5. Period-scoped temperature work

- [x] 5.1 Add indexed temperature-cache queries and a non-destructive Room 8-to-9 migration; validate cache writes without materializing the full reading history.
- [x] 5.2 Refresh only the selected period's interval and daily temperature means, using row-count invalidations rather than full-cache emissions.
- [x] 5.3 Scope startup and connectivity backfills to the current Dashboard period and request historical temperatures when navigating to that period.
- [x] 5.4 Verify period boundaries, historical period navigation, and temperature refresh behavior with focused JVM tests.
