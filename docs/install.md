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
is supported; survival gathering, a visible NPC and voice are still unfinished.
This bundle is not proof of full human acceptance or production account login.
