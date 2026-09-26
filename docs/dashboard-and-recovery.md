# Dashboard and pairing recovery

The Android dashboard follows the selected dark navy/orange visual direction.
The speed dial, battery and range are explicitly unavailable: no simulated
telemetry or claim of a factory backup is shown. Profile controls remain
unavailable. Diagnostics and credential import are under Settings.

Connection stages come from the BLE manager. At the end of a diagnostic session
the badge returns to Disconnected, even if AUTH succeeded earlier in the session.
Permission requests happen when scanning is requested. Export failures are
reported instead of showing a false success.

Saved credentials for the matching serial now take priority over pending
credentials. `credential_source` records only the source, never the key.
Following a failure, **Emparelhar novamente** opens a fresh connection and
explicitly selects initial pairing, regardless of the credential source.
The existing pairing consent explains the possible effect on official-app
Bluetooth binding. Previous saved/pending keys are archived encrypted locally
before a new candidate is persisted and transmitted. Archives are for recovery;
there is not yet a user-facing archive management screen.

This fixes the recovery dead end; it does not establish that the owner's ZT3
accepts this pairing protocol. A timeout is still not proof of invalid credentials.
No speed, profile, firmware, or region commands were added.

Validation includes independent crypto vectors, credential selection tests and
an Android emulator test that opens the native dashboard, scrolls to its controls
and opens Settings/About. Screenshots are produced as CI artifacts for inspection.
An emulator does not validate physical scooter communication.
