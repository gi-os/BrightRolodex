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

JDK 17, `compileSdk` 35, arm64 only. The signing key is committed on purpose — see
[keystore/README.md](keystore/README.md) — so a local release build is signed with the same
certificate as a published one and updates in place over it.

This app does **not** depend on `com.gios:light-common`, unlike the rest of the family. GitHub
Packages has no anonymous read even for a public package, and a new repository's own
`GITHUB_TOKEN` is not granted access to a package published from a different repository — so
the first CI run of a fresh repo fails on "Could not find com.gios:light-common", which reads
exactly like a credentials bug and is not one. The ~120 lines of wheel handling it needed are
vendored in `hw/` instead. The crash reporter and the LightSync backup provider are the two
things that costs, and they land once the repository has `GPR_USER` / `GPR_TOKEN` secrets.

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
