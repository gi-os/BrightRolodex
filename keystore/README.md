# Release signing keys stay outside the repository

The original `brightrolodex.jks` and `password.txt` were publicly committed. Treat that
private key as compromised; removing the files does not remove them from git history.
A public certificate is safe to publish, but its private signing key is not.

Release CI restores a replacement key and signing lineage from GitHub Actions secrets,
then signs with APK Signature Scheme v3. The lineage must connect the original certificate
pinned in `signing-lineage-root-fingerprint.txt` to the replacement certificate pinned in
`signing-fingerprint.txt`, preserving installed data without granting signer rollback.

Without all four signing environment variables, local release builds are unsigned, never
debug-signed. Do not commit private keys, passwords, or the replacement keystore.
See [Release signing](../README.md#release-signing) for Giovanni's migration and secret setup
steps. The original key is used offline only to create the rotation proof; it never enters CI.
