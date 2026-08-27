# The signing key is in this directory, on purpose

`brightrolodex.jks` and its password are committed. That is a deliberate decision, not an
accident, and it is the same conclusion the rest of this family arrived at the hard way.

Android identifies an app by the pair (package name, signing certificate). Every APK here is a
public GitHub release, so the certificate is already public and permanently so — it can be read
out of any release with `apksigner verify --print-certs`. There is nothing left to protect.

What pretending otherwise costs is real. A repository with no keystore secret builds a release
signed with the throwaway debug key. That APK installs perfectly well and can never update a
real release: Obtainium reports `Failure: Invalid`, and the only cure is an uninstall, which
wipes the user's data. In this app that data is hand-drawn faces and things you wrote down about
your neighbours, none of which can be re-derived from anything.

A signing key rotation was attempted once across this family and reverted for exactly that
reason. So: the key is committed, the very first release is the app's permanent identity, and
`signing-fingerprint.txt` in the repository root pins it. `build.yml` fails the release if the
certificate ever drifts from that fingerprint.

`KEYSTORE_PASSWORD` in the environment still overrides `password.txt`, so a fork can sign with
its own key without editing the build file.
