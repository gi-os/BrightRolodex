<a href="https://github.com/sponsors/gi-os"><img src="https://raw.githubusercontent.com/gi-os/gi-os/main/images/bright-sponsors.svg" width="100%" alt="Thank you to my sponsors"></a>

# Rolodex

[**⬇ Download the latest APK**](https://github.com/gi-os/BrightRolodex/releases/latest) · free, open source.

A rolodex for people you know, not just people you can dial. For the Light Phone III.

Your address book holds everyone you have ever had a number for. A rolodex holds the people you
actually need to remember — the super, the woman who works nights at the bodega, whoever lives in
4B with the beagle — and for most of them you do not have a number and never will.

So: one card per person, a phone number is optional, and the face on the card is one you drew.

## What it does

**A deck you flip.** One full-screen card per person and the wheel is the knob. Alphabetical
and it does not wrap, because the reason a rolodex works is that the same person is always in
the same place.

**Faces you draw.** A parts builder — heads, hair, eyes, brows, noses, mouths, beards, glasses,
hats — where the wheel cycles variants and a drag places them, plus a pencil layer over the top
for the thing that makes it them. Zoom in with the wheel to draw something small: the pencil
stays a fixed number of screen pixels wide, so at 4x it lays down a genuinely finer line.

Faces are stored as vectors, never as images, which means **every face can always be edited
again** — every part still swappable, every stroke still removable, a year later.

**Find by anything you remember.** Search covers the name, how you know them, where they are,
and every fact separately. Typing `beagle` finds Marco. Typing `4B` finds whoever lives there.
This is the feature: the situation the app exists for is somebody saying hello on the stairs and
you having no idea who they are.

**You choose who is in it.** Nothing is imported automatically. `CONTACTS` offers the people
already on the phone one at a time: `ADD` puts them in the deck, `HIDE` says they are not a
rolodex person and stops them being offered again. The friend from third grade stays in your
address book and out of your rolodex.

**It writes back.** A person you add becomes a real contact in the local device account, so
LightOS's own Contacts app and the stock dialer know who is calling — and the drawn face becomes
their contact photo, which is how it turns up in BrightChat and the dialer without either of
them knowing this app exists.

## The controls

| | |
|---|---|
| Wheel, on the deck | flip through the cards |
| Wheel, in PARTS | cycle variants of the selected category |
| Wheel, in PENCIL | zoom |
| Two fingers, in PENCIL | pan |
| Two fingers, in PARTS | scale and rotate the placed part |
| Camera button, on the deck | draw this person's face |
| Tap the face | draw it |
| Tap the name | edit details and facts |

## Permissions

`READ_CONTACTS` and `WRITE_CONTACTS`, for the two halves above. `CALL_PHONE`, so a card places
a call rather than typing the number into the keypad. `VIBRATE`, for the 45 ms tick every
LightOS control has.

**No `INTERNET`.** Nothing here talks to a network — the faces are drawn on the phone, the
people are in the address book, and there is no sync and no crash reporter. `build.yml` fails
the release if a dependency ever quietly adds it back.

## Install

Grab the APK from [the latest release](https://github.com/gi-os/BrightRolodex/releases/latest),
or add the repository to Obtainium. It is also in BrightMarket.

```
adb install -r BrightRolodex-v1.0.x.apk
```

Nothing else to do — no adb grants, unlike most of this family.

## Building

```
./gradlew :app:assembleRelease :app:testDebugUnitTest
```

JDK 17, `compileSdk` 35, arm64 only. Without all four signing environment variables
(`KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`), release builds are
unsigned; debug builds use the standard Android debug key. Neither can update a
production install. See [keystore/README.md](keystore/README.md).

This app does **not** depend on `com.gios:light-common`, unlike the rest of the family. GitHub
Packages has no anonymous read even for a public package, and a new repository's own
`GITHUB_TOKEN` is not granted access to a package published from a different repository — so
the first CI run of a fresh repo fails on "Could not find com.gios:light-common", which reads
exactly like a credentials bug and is not one. The ~120 lines of wheel handling it needed are
vendored in `hw/` instead. The crash reporter and the LightSync backup provider are the two
things that costs, and they land once the repository has `GPR_USER` / `GPR_TOKEN` secrets.

### Release signing

Before merging the signing change, disable **Build & Release APK** so the merge does
not publish. In a private directory outside the checkout, use Android Build Tools
35.0.0 to generate the replacement key and create a proof of rotation from the original
release key. The original alias is `brightrolodex`; its keystore and key password are the
same value from the old `keystore/password.txt` in git history or Giovanni’s local copy.
Use that old password only for the old signer, and fresh passwords for the replacement key.

```sh
keytool -genkeypair -storetype JKS -keystore brightrolodex-new.jks -alias brightrolodex-release \
  -keyalg RSA -keysize 3072 -validity 10000

apksigner rotate --out brightrolodex.lineage \
  --old-signer --ks /path/to/original/brightrolodex.jks --ks-key-alias brightrolodex \
  --set-installed-data true --set-rollback false \
  --new-signer --ks brightrolodex-new.jks --ks-key-alias brightrolodex-release

apksigner lineage --in brightrolodex.lineage --print-certs
```

Verify that signer #1's SHA-256 digest matches `signing-lineage-root-fingerprint.txt`,
then replace `signing-fingerprint.txt` with signer #2's SHA-256 digest. Do not change
the original fingerprint. Keep the original private key out of CI; it is only needed
offline to create the lineage.

In **Settings → Secrets and variables → Actions**, set these five secrets:

| Secret | Value |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded `brightrolodex-new.jks` |
| `SIGNING_LINEAGE_BASE64` | Base64-encoded `brightrolodex.lineage` |
| `KEYSTORE_PASSWORD` | New keystore password |
| `KEY_ALIAS` | `brightrolodex-release` |
| `KEY_PASSWORD` | New key password |

On Linux, upload the files without printing their contents and enter the other values when prompted:

```sh
base64 -w 0 brightrolodex-new.jks | gh secret set RELEASE_KEYSTORE_BASE64 --repo gi-os/BrightRolodex
base64 -w 0 brightrolodex.lineage | gh secret set SIGNING_LINEAGE_BASE64 --repo gi-os/BrightRolodex
gh secret set KEYSTORE_PASSWORD --repo gi-os/BrightRolodex
gh secret set KEY_ALIAS --repo gi-os/BrightRolodex
gh secret set KEY_PASSWORD --repo gi-os/BrightRolodex
```

CI signs with the new key using APK Signature Scheme v3 and verifies the proof of rotation
from the original pin to the new pin. BrightRolodex requires Android API 33, so no supported
device needs a v1/v2 signature or the old private key. Android can accept an in-place update,
but a catalog that compares only the current certificate can still hide it from users on
the old signer. Coordinate the BrightMarket catalog/updater transition before re-enabling
publishing, and only re-enable it for an authorized release.

A push to `main` (other than Markdown-only changes) or a manual run of `build.yml` builds,
signs, tags, and publishes. Tag pushes do not trigger it. Branch and PR checks
in `check.yml` publish nothing.

### Layout

```
face/     the face: a small path language, the parts table, two renderers
data/     Person, the JSON store, search, phone numbers, deck ordering
contacts/ reading the address book and writing raw contacts to the local account
ui/       theme ported from light-sdk, the deck, the editor, the lists
share/    the face ContentProvider, and handoff to BrightChat / LightNotebook
hw/       the wheel and the camera button
```

`face/`, `data/` and the path language have no Android imports, so they are covered by JVM unit
tests rather than by tapping on the phone. Sixty-eight parts is a lot of hand-written curves,
and a typo in one of them should be a test failure on a laptop and not a blank face discovered
a week later.

## Design

The Light Phone III design language, ported by hand from
[`lightphone/light-sdk`](https://github.com/lightphone/light-sdk) (MIT, see
[LICENSE-light-sdk](LICENSE-light-sdk)): a 27×31 grid, a named type scale over the panel's own
height, three colours, no ripples, and a 45 ms tick on finger-down rather than on click.

It is a plain sideloaded APK and not an SDK tool because it could not be one: the SDK's Gradle
plugin blocks `contentResolver`, `Context` and `Intent` at configure time, and `READ_CONTACTS`
is not on its allowlist. A contacts app needs all four.

## Support

These apps are free, open, and built on my own time. Sponsorship pays the bills that don't go away: build servers, test hardware, and the crash reporter that keeps them shipping. Donation or not my code is always free for the world to use.

[Sponsor on GitHub](https://github.com/sponsors/gi-os)
