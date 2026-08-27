# Rolodex v1.0 — first release

A rolodex for people you know, not just people you can dial.

## The deck

One full-screen card per person, and **the wheel is the knob**. Alphabetical, and it stops at
both ends rather than wrapping — the reason a rolodex works is that the same person is always in
the same place.

The card is the navigation too: tap the face to draw it, tap the name to edit. That keeps the
bottom bar free for the two verbs that matter, since LightOS caps a text action bar at three
items.

- **TEXT · CALL · FIND** for somebody with a number.
- **SEEN · EDIT · FIND** for somebody without one. For a person you only ever run into, "when
  did I last see them" is the only thing there is to record.

## Faces you draw, and can always redraw

A Mii-style builder: nine categories, sixty-eight parts — five heads, eleven hairstyles, eleven
sets of eyes, six brows, seven noses, nine mouths, seven kinds of facial hair, and ten extras
from round glasses to a mole.

- The **wheel cycles variants**, a **drag places** the part, and **two fingers scale and
  rotate** it.
- The **pencil** layer sits over the top for the detail that makes it them.
- **Zoom in to draw small things.** The wheel zooms up to 6x and two fingers pan. The pencil
  stays a fixed number of screen pixels wide, so at 4x it lays down a genuinely finer line
  rather than the same fat line magnified.
- **Undo goes back 40 steps** and covers parts and strokes alike.

Faces are stored as vectors, not images — about 300 bytes each. So a face renders crisply at
24px in a list row and full-screen in the editor from the same data, and **every face can always
be edited again**: every part still swappable, every stroke still removable, a year later.
Nothing in the app ever flattens a face into a picture that could only be redrawn from scratch.

## Find by anything you remember

Search covers the name, how you know them, where they are, their number, their tags, and **every
fact separately**. Typing `beagle` finds Marco. Typing `4B` finds whoever lives there. FIND
narrows the deck in place rather than navigating away, so you keep flipping.

Facts are separate lines rather than one note field precisely so that works — a single blob
makes it a substring match on a paragraph, which finds everything or nothing.

## You choose who is in it

Nothing is imported automatically. Six hundred contacts accumulated over fifteen years would
make the deck useless.

`CONTACTS` offers the people already on the phone, one row at a time:

- **ADD** puts them in the deck, keeping their name and numbers.
- **HIDE** says they are not a rolodex person and stops them being offered again. The contact
  itself is untouched and still in the phone, and the whole hidden set can be brought back from
  the bottom bar.

Separately, **ARCHIVE** takes somebody already in the deck out of the deck *and out of search*,
keeping everything you wrote about them.

## It writes back to the address book

A person you add becomes a real contact in the **local device account** — so LightOS's own
Contacts app and the stock dialer name them on an incoming call, and "the guy with the van"
becomes findable by everything on the phone rather than only by this app.

The local device account and not an account type of this app's own, which is the textbook
answer: raw contacts under an app's own account type are **deleted with the app**, so one
uninstall would take every neighbour and every drawn face with it.

The drawn face becomes their **contact photo**, marked as the primary one, which is how it shows
up in BrightChat's thread rows and in the dialer without either of them knowing this app exists.
`content://com.gios.brightrolodex.faces/<number>` serves the same face at 512px for callers that
want it bigger; it serves faces and nothing else — no facts, no notes, no tags.

Also wired: **TEXT** hands off to BrightChat (falling back to whatever the phone's messaging app
is), **NOTE** opens a per-person page in LightNotebook, and a birthday becomes a birthday event
on the contact.

## No network

This release asks for no `INTERNET` permission at all. The faces are drawn on the phone, the
people are in the address book, and there is no sync and no crash reporter. CI fails the release
if a dependency ever quietly adds it back.

The two things that costs, both from not depending on `light-common` yet: **shake-to-report** and
the **LightSync backup**. Both land in v1.1 once the repository has GitHub Packages secrets.

## Known gaps

- **Texting through BrightChat needs one line over there.** BrightChat declares `ACTION_SEND`
  for `image/*` only, so the direct-to-thread path resolves to nothing today and TEXT falls back
  to `smsto:`. Adding a `text/plain` filter to BrightChat turns it on with no change here.
- **Relationships** ("Denise's ex", "Pete's owner") are in the data model and have no UI yet.
- **Last seen** is manual. Reading it from the call log is a v1.1 job.
- One number per person in the editor. The model keeps the rest, so an adopted contact's extra
  numbers are not thrown away.

## Install

No adb grants, unlike most of this family. Install and open it.
