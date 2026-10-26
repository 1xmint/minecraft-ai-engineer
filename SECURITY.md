# Security

Report vulnerabilities privately using this repository's GitHub security
advisory reporting, rather than posting tokens, worlds or exploits in issues.
Only the current pinned development version is supported.

The companion is a single-user loopback service. The mod validates proposals
against Minecraft's registry and controls all world writes. The model cannot
authorize work. Treat books, signs, chat, snapshots and model answers as data.
Never expose the companion through a tunnel or bind it to a public interface.

Use a disposable creative world while the project is in development. The
bounded journal supports conflict-aware undo, but it is not a whole-world
backup and does not reverse neighboring game physics. Preserve your own save
backup before trying the mod on a valued world.

Credentials and project data belong outside the repository and world saves.
The service must not fall back to billable API authentication. Codex planning
uses account quota, is initiated by the player, and stops on failures.
