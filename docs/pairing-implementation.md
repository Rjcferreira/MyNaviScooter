# Initial pairing implementation

This implements the documented Encryption2 PRE_COMM -> SET_PWD -> AUTH flow.
It has not yet been validated on the owner's ZT3 Pro. Previous captures validate
only PRE_COMM. No claim of live pairing success is made by the automated tests.

References:
- https://codeberg.org/NootNooot/segway-ninebot-ble-cli (nb_crypto.py, handshake in segway_ble_client.py)
- https://gist.github.com/djensenius/48d6aef55a4ad403775cc5ff5fe92f53

The references disagree on password PRNG details and retry strategy. The app
uses SecureRandom for the new 16-byte password transmitted by SET_PWD, and sends
one SET_PWD per owner-approved attempt. It does not brute force credentials or
retry credential writes automatically. SET_PWD support with an existing binding
remains device/firmware dependent. A timeout is not evidence of rejection.

Initial pairing explicitly explains the possible effect on the official app's
Bluetooth binding. The owner selects Pair. A generated candidate is saved
encrypted and synchronously BEFORE sending SET_PWD, to survive interruption.
No raw SET_PWD frames or subsequent raw notifications are exported. A validated
SET_PWD reply with index 0 prompts for the physical button and starts a bounded
45-second wait. Index 1 advances automatically to AUTH. A phone tap cannot
authorize the session. Duplicate/replayed pairing replies do not advance it.

The first encrypted TX counter is 2. AUTH after SET_PWD uses TX counter 3;
reconnection using a stored credential starts AUTH at counter 2. The response
counter comes from the frame, with MAC validation. Responses may have zero
payload bytes; their status is in the index field. The old parser incorrectly
required at least one data byte.

On verified AUTH success, the candidate is promoted to the credential store.
An interrupted attempt retains the encrypted candidate for reconnection; it
does not overwrite the prior credential. Authentication rejection does not
automatically trigger another credential write. A minimum MTU of 32 is required
for the 29-byte SET_PWD; smaller negotiated MTUs stop before pairing transmission.

Synthetic vectors from the independent Python implementation cover request
bytes, zero-payload replies, MAC corruption, wrong keys, reflected requests,
fragmentation, duplicates and rejection states. No scooter secrets are fixtures.
The app ends after authentication; telemetry, profile writes and full backup
are not implemented by this change.
