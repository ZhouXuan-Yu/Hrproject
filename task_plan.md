# Redis Performance Retrofit Plan

## Goal
Implement a commercial-grade Redis performance retrofit for the HR recruitment system, focused on faster page switching, fewer repeated MySQL reads, and faster interview scheduling.

## Phases
- [complete] Inspect current cache, API, and slow-path architecture
- [complete] Identify Redis insertion points and invalidation rules
- [complete] Draft phased implementation plan, risk controls, and acceptance criteria
- [complete] Build backend Redis cache foundation with safe fallback
- [complete] Connect first high-frequency read APIs and invalidation hooks
- [complete] Optimize interview scheduling modal first-row display
- [complete] Run tests after each slice and perform adversarial review

## Decisions
- User approved implementation on 2026-07-27.
- Prefer read-through caching and precise invalidation over hiding loading UI.
- Redis should be introduced as backend shared cache + distributed lock + async task backbone, not only as a front-end loading workaround.

## Errors Encountered
- session-catchup command returned no readable output in PowerShell; proceeding with a fresh plan file.
