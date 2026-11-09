# MCEMS

The MineCraft Encrypted Messaging System uses elliptic curve Diffie-Hellman (ECDH) and HMAC-based Key Derivation Function (HKDF). Because of this, it is vulnerable to man-in-the-middle (MITM) attacks.

| Directory | Purpose |
| - | - |
| client | The Fabric mod users must install before joining the Minecraft server. |
| server | The Paper plugin handling the encrypted messaging system for the Minecraft server. |
| mitm | Time provided, stores the project(s) necessary to perform a MITM attack. |
