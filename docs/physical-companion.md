# Optional physical companion

This development feature has passed native lifecycle and dedicated two-client
chat proof; full integration checks remain pending. It is not included in alpha.2.
It currently creates an idle visible companion;
physical construction, gathering and navigation are not enabled.

![The physical companion received and rendered by the actual Minecraft client](images/physical-companion.png)

Screenshot from the dedicated offline CI fixture on source `053f358`,
[run 41576664814](https://github.com/1xmint/minecraft-ai-engineer/actions/runs/41576664814).
The run proves addressed-chat summon/dismiss, denied shared-editor actions and
owner-disconnect cleanup. It does not establish production account login or the
full human acceptance workflow.

The server operator needs the free
[Carpet 26.3 release](https://github.com/gnembon/fabric-carpet/releases/tag/v26.3),
specifically `fabric-carpet-26.3+v260915.jar`. Its SHA256 is
`7c40b93e807ed914064176898037117097446c6cc76e6a80a856c8bf7f7659f9`.
Install it in the dedicated Fabric server's mods folder. No extra account, API key
or client mod is needed. The ordinary Engineer works without this optional mod.

As project owner:

1. Select your project and mark a workspace containing a 3×3 stone floor with two
   clear air blocks above it, at the workspace's minimum corner.
2. Move players and other entities out of that space.
3. Say **“Engineer, summon my companion.”**
4. Say **“Engineer, where is my companion?”** for its position, or
   **“Engineer, dismiss my companion.”** to remove it.

The companion is an empty survival actor with a generated `Eng_…` name. It has no
project permissions, cannot use items or mine without a separately checked action
ticket, and cannot pick up dropped materials or experience orbs while idle. Authorized project
viewers can ask for its location; only the owner can summon or dismiss it.

Changing the owner's connection, dimension, selected project, project revision or
permissions removes the actor. Moving it out of its idle position also removes
it. Server shutdown stops and removes it; restart does not automatically summon
it or grant any action authority. These lifecycle cases still require the stated
CI and multiplayer proofs before release.
