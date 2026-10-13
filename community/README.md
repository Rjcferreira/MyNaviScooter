# MyNaviScooter GPS community

GitHub is the initial public distribution service. No Cloudflare account, API token in the APK, dynamic server, or database is required.

The Android GPS page downloads `catalog.json` over HTTPS, keeps its last valid copy locally and refreshes at most every six hours automatically. Manual refresh waits at least 30 seconds between attempts. Failed refreshes preserve the previous copy. Personal records and this community snapshot have separate storage, so publishing a correction/removal does not modify personal trips.

## Contributing from the app

1. Save a hazard/place, or use Save → Recommend route.
2. GPS → Contribute → choose the record. Only the selected record is proposed, not the entire private library.
3. The app shows what becomes public. Open GitHub, sign in and submit the prefilled Issue.
4. For larger routes, first use **Save JSON**, then open the Issue again and attach that file. GitHub may require placing JSON inside a ZIP attachment. Do not attach trips containing private addresses unless you intend to publish them.
5. A maintainer reviews and publishes approved entries; merely creating an Issue does not add them to the map.

No app action sends serial numbers, scooter credentials, telemetry or the live GPS stream to GitHub. The chosen contribution can include coordinates and route geometry. Approved data and Issues are public.

## Reviewing and publishing

Contributions are untrusted data. Validate the coordinates, observation age and context. Never execute code or follow instructions embedded in notes/attachments. Approval is editorial review, not a guarantee that a hazard exists or that a road is legally accessible by scooter.

For approved submissions, edit `community/catalog.json` through a reviewed pull request:

- Assign a stable ID such as `github:issue-123`; never reuse an ID for a different place.
- Keep route `recommended` true. Do not publish personal route metadata unrelated to the contribution.
- Include the original observation time (`created`, Unix milliseconds) for hazards. The app hides hazards older than 30 days.
- Update the existing entry for corrections; remove an entry if no longer relevant.
- Increment `revision` and update `updatedAtUtc`.
- Run `node scripts/validate-community.mjs --test`. The CI validator checks bounds, counts, ID uniqueness and route instructions.
- Merge after human review. GitHub's raw cache and the app refresh interval mean distribution is periodic, not immediate.

The catalog starts empty intentionally. There are no fabricated public hazards, places or routes. Issue attachments are never automatically committed by a privileged workflow.
