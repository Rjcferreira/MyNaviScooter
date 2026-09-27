# Initial pairing implementation

This implements the ZT3 NinebotCrypto PRE_COMM (0x5B) -> app-random (0x5C) ->
challenge (0x5D) flow. Live owner captures validate PRE_COMM and a 13-byte
pairing response after the physical power-button press. Full authentication is
not claimed until a validated 0x5D/01 reply is captured.

References:
- https://codeberg.org/NootNooot/segway-ninebot-ble-cli (nb_crypto.py, handshake in segway_ble_client.py)
- https://gist.github.com/djensenius/48d6aef55a4ad403775cc5ff5fe92f53

The references disagree on password PRNG details and retry strategy. The app
uses SecureRandom for the new 16-byte app random. While the bounded physical-
confirmation screen is visible, the same random is sent once per second with a
strictly increasing shared wire counter. This matches the observed ZT3 behavior:
the scooter only emitted its reply after a later 0x5C following the button press.

Initial pairing explicitly explains the possible effect on the official app's
Bluetooth binding. The owner selects Pair. A generated candidate is saved
encrypted and synchronously BEFORE sending SET_PWD, to survive interruption.
No raw credential-bearing frames are exported. The physical-button instruction
is shown before the first 0x5C. A validated 0x5C reply with index 0 or 1 advances
to 0x5D. Firmware may encrypt that acknowledgement with either the pre-pair key
or the newly proposed key; both are attempted, but advancement still requires a
valid CCM tag and complete frame. A phone tap cannot authorize the session.

The first encrypted TX counter is 2. Counters are shared by TX and RX; after a
reply at counter N, the next transmitted 0x5D uses N+2. Reconnection with a
stored random starts 0x5D at counter 2. Responses may have zero payload bytes;
their status is in the index field.

On verified AUTH success, the candidate is promoted to the credential store.
An interrupted attempt retains the encrypted candidate for reconnection; it
does not overwrite the prior credential. Authentication rejection does not
automatically trigger another credential write. A minimum MTU of 32 is required
for the 29-byte SET_PWD; smaller negotiated MTUs stop before pairing transmission.

The tested ZT3 firmware did not react to the first fresh 0x5D. A second bounded
attempt caused it to close the BLE transport with Android status 19. The app
treats this once as a pairing-session restart, waits briefly, reconnects, repeats
PRE_COMM, and verifies the pending app random with 0x5D. A second transport error
is reported normally rather than entering a reconnect loop.

Synthetic vectors from the independent Python implementation cover request
bytes, zero-payload replies, MAC corruption, wrong keys, reflected requests,
fragmentation, duplicates and rejection states. No scooter secrets are fixtures.
The app ends after authentication; telemetry, profile writes and full backup
are not implemented by this change.
