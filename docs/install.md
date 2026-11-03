# Install the multiplayer development preview

[Download v0.1.0-alpha.2](https://github.com/1xmint/minecraft-ai-engineer/releases/tag/v0.1.0-alpha.2).

This preview uses one dedicated Fabric server and one private companion on the
server owner's computer, even when playing alone. The older `alpha.1` download
is a separate single-player reference. CI artifacts are build candidates; use a
published prerelease only when its exact source passed all five required checks.

## Requirements

- Minecraft Java 26.3, Java 25, Fabric Loader 0.20.1 and Fabric API 0.165.0+26.3.
- Node 24.14 or newer within Node 24; npm comes with Node.
- The owner's ChatGPT sign-in for optional Codex reasoning. No API key or paid
  API service is used. Planning consumes the owner's subscription quota.
- On Windows, the existing planner launcher uses the operating system's .NET
  Framework C# compiler. No Java compiler or TypeScript compiler is needed.

## Start the companion

1. Download the companion zip, mod jar, `BUILD.json` and `SHA256SUMS` from the
   same release. Verify the checksums before using the pair. `BUILD.json` names
   the exact checkout and CI run; do not mix versions from different runs.
2. Extract the companion zip into a new folder. In that folder, run
   `npm ci --omit=dev`. Internet access is required to fetch the pinned runtime
   dependencies, including the platform's optional Codex binary package.
3. Sign in to the official Codex CLI with ChatGPT (`npx --no-install codex login`) on the owner's
   computer if reasoning is wanted. Do not share the login with players.
4. Run `npm start` and leave it running. Startup itself makes no model request.
   The companion listens only on `127.0.0.1`. Its private data directory defaults
   to `%APPDATA%\minecraft-ai-engineer` on Windows and
   `~/.config/minecraft-ai-engineer` on Linux/macOS (or `XDG_CONFIG_HOME`).

## Start the dedicated server

1. Use [Fabric's server installer](https://fabricmc.net/use/server/) to install
   the matching Minecraft and Loader versions in a new server folder. Review
   Minecraft's EULA yourself. Use a disposable creative world for this preview.
2. Put the matching Fabric API and downloaded engineer jar in the server's
   `mods` folder. Run the server with Java 25. Keep `online-mode=true`.
3. Run the companion and server as the same owner, using the same private data
   directory. If setting `MCE_CONFIG_DIR`, set the same absolute path for both
   processes. Never expose that directory, token or companion port to players.
4. Configure `MCE_CODEX_OPERATOR_UUID` for the account-owning Minecraft operator
   before launching the server. Only that operator can request model reasoning.
   Other players can use authorized local engineering features; joining grants
   neither project access nor model access. General shared-subscription
   eligibility remains unresolved.
5. Join `localhost` with a matching Minecraft client. Client-side previews need
   Fabric, Fabric API and the matching engineer jar on that client too. A client
   without the overlay can use server-side text conversations and commands.
   Local play requires no public hosting or port forwarding.

## Build through conversation

Make yourself a server operator through the server console if needed. Say
`Engineer, start a new project`. Stand at the first workspace corner and say
`Engineer, mark the first corner here`, then mark the opposite upper corner with
`Engineer, mark the second corner here`. Corners use the block containing your feet.

Say `Engineer, show me a signal lamp`, then `Engineer, explain how this works`.
Ask the bot to build when ready and review its separate confirmation. Ask it to
test the circuit after construction. `Engineer, stop` interrupts work. A preview
does not place blocks and a geometry match does not prove the circuit works.

Use the included [mod guide](docs/mod-guide.md) for sharing, personal lessons,
permissions, interruption, undo and restart. See the
[companion guide](docs/companion-guide.md) for configuration. Creative execution
is supported; survival gathering and a visible NPC are still unfinished. Voice is
optional and fully local; see below.
This bundle is not proof of full human acceptance or production account login.

## Voice (optional, local)

Voice is off until you turn it on, and typing always works without it. Speech is
recognised and spoken by programs on your own computer. No audio is uploaded, no
account or API key is needed, and nothing is billed.

1. Install a local speech recogniser, for example
   [whisper.cpp](https://github.com/ggml-org/whisper.cpp) with a small English
   model such as `ggml-base.en.bin`. Optionally install a speech program for
   replies (Windows has one built in; on Linux use `espeak-ng`).
2. Create `config/engineer-voice.json` in your Minecraft folder:

```json
{
  "enabled": true,
  "recognizer": {
    "command": ["C:/whisper/whisper-cli.exe", "-m", "C:/whisper/ggml-base.en.bin", "-f", "{wav}", "-nt", "-np"],
    "timeoutSeconds": 60
  },
  "speaker": {
    "command": ["powershell", "-NoProfile", "-Command", "Add-Type -AssemblyName System.Speech; (New-Object System.Speech.Synthesis.SpeechSynthesizer).Speak([Console]::In.ReadToEnd())"],
    "timeoutSeconds": 120
  }
}
```

On Linux the speaker can be `["espeak-ng", "--stdin"]`. Leave `speaker` out to
keep replies silent. The recogniser command must contain `{wav}`; the mod puts the
path of the recording there and reads the words the program prints. Speech text
is passed to the speaker on standard input, never on a command line, and no
command runs through a shell. The file is read on each key press, so edits apply
without a restart.

**Using it.** Hold **V** and speak, then let go. The top-left line shows
`Listening...` and then `Transcribing on this computer...`. Your words appear in
the chat box as `Engineer, ...`. Nothing is sent until you press Enter, so you
can correct the words or press Esc to discard them. Esc (or switching away from
the game) while listening or transcribing cancels, and nothing is sent. Press
**B** to stop the engineer speaking; pressing V also stops it. Only the
engineer's own replies are read aloud, never other players' chat. Both keys can
be changed under Controls, in the "Engineer voice" category.

A spoken request goes through exactly the same permissions, delegation and
confirmations as a typed one. Saying "yes" only puts "Engineer, yes" in the chat
box; you still press Enter, and the engineer still needs its own pending
proposal. A spoken `/command` stays an ordinary message to the engineer and never
runs as a command.

**Privacy.** The recording is held in memory, written to a private temporary
folder only while your recogniser runs, and deleted straight after, whether it
succeeded, failed or was cancelled. Recording stops after 30 seconds. The
recogniser is stopped after `timeoutSeconds`.

**If something is missing.** With no config, an invalid config, no microphone or a
failing recogniser, pressing V shows one plain line explaining why and nothing is
sent. Typing still works.

### Microphone and speaker acceptance check

Automated CI has no microphone, speaker or model, so it proves the adapters with
fake audio devices and real local programs. A person still needs to run this
check once on a real computer:

1. Install whisper.cpp and a model, write the config above, and start the game.
   Open a creative world and select a project.
2. Without speaking, tap V. Expect `I did not hear any words` (or a short
   transcript of noise) and no message sent.
3. Hold V, say "what does this do", release. Expect the listening line while
   held, then `Engineer, what does this do` in the chat box. Wait ten seconds:
   nothing is sent. Press Enter: the engineer answers as if you had typed it.
4. Hold V, speak, and press Esc before releasing. Expect `Voice cancelled.
   Nothing was sent.` and no chat box.
5. Hold V, speak a long sentence, release, then press Esc while
   `Transcribing` shows. Expect the cancel notice and no chat box afterwards.
6. Say "yes" with no proposal waiting and press Enter. Expect the same reply as
   typing `Engineer, yes`; nothing is built.
7. With a speaker configured, ask a question and listen for the reply. Press B
   mid-sentence: speech stops at once. Ask again and press V mid-sentence:
   speech stops and listening starts.
8. Unplug or disable the microphone and press V. Expect `No microphone is
   available. Typing still works.`
9. Break the recogniser path in the config and press V, speak, release. Expect
   `Speech recognition could not start ...` and typing still works.
10. After each run, check your temporary folder has no `engineer-voice-*`
    folder left behind.

Still to be validated by a person: recognition quality and speed on real
hardware, the Windows and Linux speaker commands above, microphone permission
prompts on macOS, push-to-talk with a headset, and that recording never picks up
the engineer's own speech.
