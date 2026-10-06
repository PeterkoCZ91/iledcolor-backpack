# Roadmap

GifPack already covers the main path — find or make a GIF, fit it to 64 × 64 and get it
onto the backpack with a confirmed result. The remaining work is mostly **verification on
real hardware** and a few features that the protocol allows but the app does not use yet.
Items are capability-driven: a feature the backpack does not advertise stays hidden rather
than guessed.

Status words follow the [feature-maturity table](README.md#feature-maturity).

## Next steps

| Order | Work | Status and definition of done |
| --- | --- | --- |
| 1 | **Import robustness on Android.** Import after the URI grant is revoked, and after the process is killed mid-import. | Copy-with-cancellation and the share queue are unit-tested; valid and malformed `ACTION_SEND` and cleanup are verified on a phone. Done when both Android cases are tested on a device. |
| 2 | **First-connection resilience.** One transient GATT 133 was seen on a first connection; the retry succeeded. | No confirmed cause. Record each occurrence; add a bounded automatic retry only if it recurs. |
| 3 | **Verify remaining backpack answers and visuals.** The "not enough space" status; physical image of edited GIFs. | Clock, status query, brightness, screen on/off and the "already on backpack" status (3) are verified. Rotation is hidden because the tested firmware does not advertise it (`funCode 0x0044`). Done when the statuses are observed and the panel output is checked by eye. |
| 4 | **Verify the new features on the panel.** Programme speed / brightness bytes and built-in programmes on a device that reports them. | The scrolling-text banner is verified (upload confirmed and it scrolls on the panel). Speed / brightness remain experimental; the tested unit reports 0 built-in programmes. |
| 5 | **Error states and accessibility.** Each screen's error state, TalkBack, large fonts and touch targets. | Code pass done on all screens (descriptions, headings, 48 dp targets, toggle/slider semantics, announced errors with a recovery action). A manual TalkBack and large-font pass on a phone is still to do. |
| 6 | **Measure capacity safely.** Find the practical upload limit step by step, watching the panel, without using "clear all" as cleanup. | Largest confirmed upload: 453 KB / 96 frames. |

## Protocol features not yet used

- **Clock sync decision.** The tested backpack does not advertise time support, and the
  app now skips the command there. Confirm the behaviour on a device that does.
- **Backpack password** (`funCode` bit `0x0040`). Query, verify and set are known in outline.
  Must be designed carefully — a mistake can lock the user out of their own device.
- **Effects** for uploaded programmes (an effect byte exists next to speed and brightness).

## Library and UX ideas

Not known bugs or blockers:

- Search, rename, sort and an overview of edited copies in the collection.
- Category filtering, if pinned / trending / recent prove insufficient in daily use.
- Continue uploads while the phone is locked — needs a foreground service with a visible
  notification; only if users need it.

## Before a public release

Not required for local development or debug testing:

- **Privacy notes** describing which requests and files go to third parties (Giphy, Klipy,
  video URLs) and how API keys are stored; a privacy policy and data-safety answers for the
  chosen distribution channel.
- **Release build**: a separate release variant, a safe signing setup and key custody, a
  target-API review and the channel's requirements.

## Done recently

- Scrolling-text banner, verified on the panel.
- Library items whose file is broken show the reason and offer *Remove*.
- Screenshots in the README; the repository was published with a clean history and CI.
- First connection without a remembered backpack finds the first one advertising nearby.
- "Already on backpack" status verified; the home tile shows the backpack's connection state and name.
- Upload failures show a typed reason with a hint, in the upload card and in the history.

## What is still unknown about the backpack

- Total storage capacity and the largest safe programme size. The app's 20 MiB / 600-frame
  limits are decoder limits, not device capacity.
- The status query reports display settings only — not free memory.
- Behaviour of other models and firmware versions; only one unit has been tested.
