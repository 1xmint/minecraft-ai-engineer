# Release notes: next preview after v0.1.0-alpha.3 (preparation)

This describes the current source. Nothing listed under "New since alpha.3" is
in the published v0.1.0-alpha.3 download, which predates it. It is a development
preview for one dedicated local server. Use a disposable world and the paired files from
one release. Nothing here is proof of the human checks listed at the end.

## Supported versions

| Part           | Version                       |
| -------------- | ----------------------------- |
| Minecraft Java | 26.3                          |
| Fabric Loader  | 0.20.1                        |
| Fabric API     | 0.165.0+26.3                  |
| Java           | 25                            |
| Node           | 24.14 or newer within Node 24 |

The mod jar and companion zip are a pair. `BUILD.json` and `SHA256SUMS` in the
release identify them. Do not mix files from different releases.

## New since alpha.3

Each item was checked by automated runs against a real Minecraft server. Some
items have the limits named under "Not supported yet".

- **A visible companion builds a working machine.** It takes real items from a
  chest, places a NOT gate with normal survival item use, then pulls the real
  lever and reads the real lamp to test it.
- **Two companions can work at once** on separate parts of one project. Each
  has its own inventory, task and permissions. Conflicting parts, chests and
  routes are refused. No helper or permission returns after a restart.
- **Imported designs can be physically built.** Choose, turn and position a
  design by talking, see materials, collisions and anything it cannot place,
  then have the companion build it from a chest.
- **Older notes are recalled.** A question or plan can draw on any current
  project note that shares its words, not only the newest 32.
- **Diagnosis says what is known.** It separates intended, observed, unknown,
  stale and verified states. The optional overlay tints the differing cells.
  Players without the overlay get the same guidance as chat text.
- **Four stored and clocked circuits can be previewed, built and live tested
  through chat:** data latch, edge register, up counter and pulse tally. A pass
  needs every step on live ticks, and every lever and output back where it
  started. Testing needs creative mode.
- **Comparisons count real lever tests** as passed, failed, interrupted,
  refused, older revision and unreadable. They never become a rate.
- **Delayed replies stay private.** A note reply that is out of date because of
  a project switch or lost access is neither shown nor spoken.

## Supported

- Owner, collaborator and denied players on one dedicated server, including
  clients without the overlay.
- Conversation for projects, preview, approval, building, testing, lessons and
  notes. Commands remain a fallback.
- Creative-mode construction of curated designs, with undo and conflict checks.
- Survival companion work described in the
  [physical companion guide](physical-companion.md).
- Optional local voice. See "Voice" in `START-HERE.md`.

## Not supported yet

- **Machine building covers the NOT gate only.** Larger circuits have no
  physical build path.
- **There is no small calculator or seven-segment display.** If a stored-circuit
  test is interrupted, a player must put the levers down by hand before it can
  be cleared. It is never replayed automatically.
- **Imported designs:** up to 32 blocks, 2 tall, six block kinds. They are
  never tested and never become verified.
- **Two companions:** only the first companion gathers. A route that crosses
  the other worker's reservation stops that worker rather than rerouting.
  A machine test will not start if redstone or any other block that could affect
  the result is within 2 blocks of the component, including a neighbouring part.
- **Machine tests record no timing.** Comparisons say timing from them is
  unavailable.
- **Note recall matches literal words,** so a synonym will not find a note.
- **Tools and workbench recipes for machine building** are not included.
- Model-interpreted free conversation is available only to the operator whose
  account is signed in. Other members use the fixed phrases.

## Human checks (not yet done)

Automated runs use scripted offline players and have no microphone, speaker or
signed-in account. Nobody has completed the checks below for this release. A
person ticks each one after doing it.

Microphone and speaker: follow "Microphone and speaker acceptance check" in
`START-HERE.md`.

- [ ] Every step of that check passed on Windows with a real microphone.
- [ ] Every step of that check passed on Linux with a real microphone.
- [ ] A person heard a spoken reply, and heard nothing after switching project
      or losing access while a reply was outstanding.

Online login, with `online-mode=true` and real Minecraft accounts:

- [ ] Owner joins the dedicated server with a real account.
- [ ] A second real account joins as collaborator and receives only its granted
      permissions.
- [ ] A third real account without access is refused and sees no private notes.
- [ ] The operator signs in to ChatGPT. Another member gets neither the sign-in
      nor direct model access.
- [ ] A companion is summoned, builds the NOT gate from a chest and passes its
      test on that server.
- [ ] The server restarts. No companion, permission or job returns by itself.
