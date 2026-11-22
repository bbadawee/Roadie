# Compatibility & Known Limits

## Supported Targets

| Environment | Status | Notes |
| :--- | :--- | :--- |
| **Android Automotive OS (AAOS)** | Supported (Experimental) | Runs natively as a `distractionOptimized`-tagged activity with `MediaSessionService`. |
| **Android Car Head Units** (API 26+) | Supported | Adapts from `800×480` compact units to `1280×720+` widescreens using `1024×600` logical scaling. |
| **Android Tablets & Phones** | Supported | Useful for testing; portrait phones automatically use the stacked watch + queue layout. |
| **Phone-Projected Android Auto** | Not Supported for Video | Google's Android Auto projection protocol blocks third-party video surfaces on the car display. |

## Layout Thresholds

- **Browse grid:** `3` columns (`>= 780dp`), `2` columns (`500–779dp`), `1` column (`< 500dp`).
- **Split watch mode:** `width >= 820dp && height >= 300dp && width > height`.
