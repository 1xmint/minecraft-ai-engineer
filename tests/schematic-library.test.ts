import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import {
  copyFileSync,
  mkdtempSync,
  readFileSync,
  readdirSync,
  rmSync,
  symlinkSync,
  writeFileSync,
} from "node:fs";
import type { AddressInfo } from "node:net";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test, type TestContext } from "node:test";
import { makeReferenceBlueprint } from "../companion/src/blueprint.js";
import type {
  GatewayContext,
  ProjectMember,
} from "../companion/src/gateway-types.js";
import { MemoryStore } from "../companion/src/memory.js";
import { createCompanionServer } from "../companion/src/server.js";
import { importSpongeSchematic } from "../companion/src/schematics/index.js";
import type { PlanningProvider } from "../companion/src/types.js";

// Real HTTP gateway, real SQLite and the pinned third-party fixtures. No model is called.
const OWNER = "11111111-1111-4111-8111-111111111111",
  EDITOR = "22222222-2222-4222-8222-222222222222",
  VIEWER = "44444444-4444-4444-8444-444444444444",
  EVE = "33333333-3333-4333-8333-333333333333",
  CONNECTION = "55555555-5555-4555-8555-555555555555";
const TOKEN = "schematic-library-test-token-0123456789";
const members: ProjectMember[] = [
  { actorId: OWNER, role: "owner", canPlan: true },
  { actorId: EDITOR, role: "editor", canPlan: false },
  { actorId: VIEWER, role: "viewer", canPlan: false },
];
const design = {
  request: "Hold imported designs",
  projectId: "project-a",
  worldId: "world-a",
  dimension: "minecraft:overworld",
  anchor: { x: 0, y: 100, z: 0 },
  rotation: 0 as const,
};
const noModel: PlanningProvider = {
  async plan() {
    throw new Error("Schematic routes must never call a model");
  },
};
const context = (changes: Partial<GatewayContext> = {}): GatewayContext => ({
  serverId: "server-a",
  serverEpoch: "epoch-one",
  actorId: OWNER,
  projectId: "project-a",
  projectRevision: 1,
  authorizationRevision: 1,
  requestId: randomUUID(),
  connectionId: CONNECTION,
  ...changes,
});
const FIXTURES = "../../tests/fixtures/schematics/";
const fixture = (name: string) =>
  new URL(`${FIXTURES}${name}`, import.meta.url);

function workspace() {
  const root = mkdtempSync(join(tmpdir(), "mce-schematic-library-"));
  const schematics = join(root, "schematics");
  return {
    root,
    schematics,
    library: join(schematics, "library"),
    exports: join(schematics, "exports"),
    db: join(root, "projects.sqlite"),
  };
}

async function start(
  t: TestContext,
  dbPath: string,
  schematicDirectory: string | undefined,
  epoch = "epoch-one",
) {
  const store = new MemoryStore(dbPath);
  const server = createCompanionServer({
    token: TOKEN,
    store,
    provider: noModel,
    ...(schematicDirectory === undefined ? {} : { schematicDirectory }),
  });
  let closed = false;
  const close = async () => {
    if (closed) return;
    closed = true;
    if (server.listening) {
      const done = new Promise<void>((resolve) =>
        server.close(() => resolve()),
      );
      server.closeAllConnections();
      await done;
    }
    store.close();
  };
  t.after(close);
  await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
  const base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
  const post = async (route: string, value: unknown) => {
    const response = await fetch(`${base}${route}`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${TOKEN}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(value),
    });
    return {
      status: response.status,
      body: (await response.json()) as Record<string, any>,
    };
  };
  const generation = epoch === "epoch-one" ? 1 : 2;
  assert.equal(
    (
      await post("/v2/server/open", {
        serverId: "server-a",
        serverEpoch: epoch,
        serverGeneration: generation,
      })
    ).status,
    200,
  );
  const sync = async (
    c: GatewayContext = context({ serverEpoch: epoch }),
    list = members,
  ) =>
    post("/v2/project", {
      context: { ...c, connectionId: undefined },
      worldId: design.worldId,
      dimension: design.dimension,
      name: "Imported designs",
      members: list,
      blueprint: makeReferenceBlueprint(design),
    });
  assert.equal((await sync()).status, 200);
  return { post, sync, close };
}

test("a player inspects, imports, transforms, compares and lists a real WorldEdit schematic, and it survives a restart", async (t) => {
  const w = workspace();
  try {
    let f = await start(t, w.db, w.schematics);
    copyFileSync(
      fixture("worldedit-fabric-7.3.schem"),
      join(w.library, "castle.schem"),
    );

    const library = await f.post("/v2/schematic/library", {
      context: context({ actorId: VIEWER }),
    });
    assert.equal(library.status, 200);
    assert.deepEqual(library.body.files, [{ name: "castle", bytes: 2525 }]);

    const inspected = await f.post("/v2/schematic/inspect", {
      context: context({ actorId: VIEWER }),
      name: "castle",
    });
    assert.equal(inspected.status, 200, JSON.stringify(inspected.body));
    assert.deepEqual(inspected.body.size, { x: 15, y: 16, z: 20 });
    assert.equal(inspected.body.nonAirBlocks, 889);
    assert.equal(inspected.body.discarded.blockEntities.length, 6);
    assert.equal(inspected.body.requiresDiscard, true);
    assert.equal(inspected.body.placeable, true);
    assert.ok(inspected.body.materials.length > 0);
    assert.ok(
      inspected.body.materials.every(
        (m: { key: string }) => m.key !== "minecraft:air",
      ),
    );
    assert.match(inspected.body.notice, /untrusted and untested/);

    // Block entities are listed, and nothing is stored until the player agrees to drop them.
    const refused = await f.post("/v2/schematic/import", {
      context: context({ actorId: EDITOR }),
      name: "castle",
      discard: false,
      transform: null,
    });
    assert.equal(refused.status, 200);
    assert.equal(refused.body.status, "needs-discard");
    assert.equal(refused.body.discarded.blockEntities.length, 6);
    assert.deepEqual(
      (await f.post("/v2/schematic/designs", { context: context() })).body
        .designs,
      [],
    );

    const imported = await f.post("/v2/schematic/import", {
      context: context({ actorId: EDITOR }),
      name: "castle.schem",
      discard: true,
      transform: null,
    });
    assert.equal(imported.status, 200, JSON.stringify(imported.body));
    assert.equal(imported.body.design.designId, 1);
    assert.equal(imported.body.design.trust, "untrusted-geometry");
    assert.equal(imported.body.design.verification, "untested");
    assert.equal(imported.body.design.createdBy, EDITOR);
    assert.equal(imported.body.design.discarded.blockEntities.length, 6);

    const turned = await f.post("/v2/schematic/transform", {
      context: context({ actorId: EDITOR }),
      designId: 1,
      transform: "rotate-90",
    });
    assert.equal(turned.status, 200, JSON.stringify(turned.body));
    assert.equal(turned.body.design.designId, 2);
    assert.equal(turned.body.design.parentId, 1);
    assert.deepEqual(turned.body.design.transforms, ["rotate-90"]);
    assert.deepEqual(turned.body.size, { x: 20, y: 16, z: 15 });
    assert.equal(turned.body.design.verification, "untested");

    const compared = await f.post("/v2/schematic/compare", {
      context: context({ actorId: VIEWER }),
      designId: 1,
      otherId: 2,
    });
    assert.equal(compared.status, 200, JSON.stringify(compared.body));
    assert.equal(compared.body.a.nonAirBlocks, 889);
    assert.equal(compared.body.b.nonAirBlocks, 889);
    assert.equal(compared.body.a.verification, "untested");
    assert.match(compared.body.notice, /Neither design is tested/);

    await f.close();
    // Restart: a new process, a new server epoch, the same SQLite file.
    f = await start(t, w.db, w.schematics, "epoch-two");
    const after = await f.post("/v2/schematic/designs", {
      context: context({ serverEpoch: "epoch-two", actorId: VIEWER }),
    });
    assert.equal(after.status, 200);
    assert.deepEqual(
      after.body.designs.map(
        (d: { designId: number; parentId: number | null }) => [
          d.designId,
          d.parentId,
        ],
      ),
      [
        [1, null],
        [2, 1],
      ],
    );
    assert.deepEqual(after.body.designs[1].size, { x: 20, y: 16, z: 15 });

    const shared = await f.post("/v2/schematic/design", {
      context: context({ serverEpoch: "epoch-two", actorId: VIEWER }),
      designId: 2,
    });
    assert.equal(shared.status, 200);
    assert.equal(shared.body.placement.available, true);
    assert.equal(shared.body.placement.blocks.length, 889);
    for (const block of shared.body.placement.blocks as {
      pos: number[];
      state: string;
    }[]) {
      assert.ok(!block.state.startsWith("minecraft:air"));
      assert.ok(block.pos[0]! >= 0 && block.pos[0]! < 20);
    }
    await f.close();
  } finally {
    rmSync(w.root, { recursive: true, force: true });
  }
});

test("non-members, viewers, revoked editors and disconnected requests get nothing and change nothing", async (t) => {
  const w = workspace();
  try {
    const f = await start(t, w.db, w.schematics);
    copyFileSync(
      fixture("worldedit-fabric-7.3.schem"),
      join(w.library, "castle.schem"),
    );
    const importAs = (c: GatewayContext) =>
      f.post("/v2/schematic/import", {
        context: c,
        name: "castle",
        discard: true,
        transform: null,
      });

    for (const route of ["/v2/schematic/library", "/v2/schematic/designs"])
      assert.equal(
        (await f.post(route, { context: context({ actorId: EVE }) })).status,
        403,
      );
    assert.equal(
      (
        await f.post("/v2/schematic/inspect", {
          context: context({ actorId: EVE }),
          name: "castle",
        })
      ).status,
      403,
    );
    assert.equal(
      (
        await f.post("/v2/schematic/library", {
          context: { ...context(), connectionId: undefined },
        })
      ).status,
      403,
    );
    assert.equal((await importAs(context({ actorId: VIEWER }))).status, 403);
    assert.equal((await importAs(context({ actorId: EVE }))).status, 403);

    // Exact fields only: an extra flag cannot smuggle in trust.
    assert.equal(
      (
        await f.post("/v2/schematic/import", {
          context: context(),
          name: "castle",
          discard: true,
          transform: null,
          verified: true,
        })
      ).status,
      400,
    );

    const owner = await importAs(context());
    assert.equal(owner.status, 200);
    // A replayed request id is refused rather than imported twice.
    assert.equal(
      (
        await importAs({
          ...context(),
          requestId: owner.body.context.requestId,
        })
      ).status,
      409,
    );

    // Revoke the editor: authorization revision 2 without them.
    assert.equal(
      (
        await f.sync(
          context({ authorizationRevision: 2 }),
          members.filter((m) => m.actorId !== EDITOR),
        )
      ).status,
      200,
    );
    assert.equal(
      (
        await f.post("/v2/schematic/designs", {
          context: context({ actorId: EDITOR, authorizationRevision: 2 }),
        })
      ).status,
      403,
    );
    assert.equal(
      (
        await f.post("/v2/schematic/design", {
          context: context({ actorId: EDITOR, authorizationRevision: 2 }),
          designId: 1,
        })
      ).status,
      403,
    );
    // A stale authorization revision is refused even for the owner.
    assert.equal(
      (await f.post("/v2/schematic/designs", { context: context() })).status,
      409,
    );
    const owned = await f.post("/v2/schematic/designs", {
      context: context({ authorizationRevision: 2 }),
    });
    assert.equal(owned.body.designs.length, 1);
    await f.close();
  } finally {
    rmSync(w.root, { recursive: true, force: true });
  }
});

test("oversized, entity-bearing, corrupt, linked and badly named files are refused with a reason and leave everything unchanged", async (t) => {
  const w = workspace();
  try {
    const f = await start(t, w.db, w.schematics);
    copyFileSync(
      fixture("hollowcube-2x2-chunk-vert.schem"),
      join(w.library, "tower.schem"),
    );
    copyFileSync(
      fixture("schematic4j-sponge-v3.schem"),
      join(w.library, "zoo.schem"),
    );
    writeFileSync(join(w.library, "junk.schem"), "not a schematic");
    const outside = join(w.root, "outside.schem");
    copyFileSync(fixture("worldedit-fabric-7.3.schem"), outside);
    let linked = true;
    try {
      symlinkSync(outside, join(w.library, "link.schem"));
    } catch {
      linked = false; // Windows without symlink rights; CI runs on Linux.
    }
    const before = readdirSync(w.library).sort();
    const inspect = (name: string) =>
      f.post("/v2/schematic/inspect", { context: context(), name });
    const importing = (name: string) =>
      f.post("/v2/schematic/import", {
        context: context(),
        name,
        discard: true,
        transform: null,
      });

    const tower = await importing("tower");
    assert.equal(tower.status, 422);
    assert.match(tower.body.error, /too large for in-game use/);
    const zoo = await inspect("zoo");
    assert.equal(zoo.status, 422);
    assert.match(zoo.body.error, /contains entities/);
    const junk = await importing("junk");
    assert.equal(junk.status, 422);
    assert.match(junk.body.error, /could not be used as a Sponge schematic/);
    if (linked) {
      const link = await inspect("link");
      assert.equal(link.status, 400);
      assert.match(link.body.error, /link or not a regular file/);
    }
    for (const name of ["../outside", "a/b", "..", "castle.nbt", ""])
      assert.equal((await inspect(name)).status, 400, name);
    assert.equal((await inspect("missing")).status, 404);

    assert.deepEqual(readdirSync(w.library).sort(), before);
    assert.deepEqual(
      (await f.post("/v2/schematic/designs", { context: context() })).body
        .designs,
      [],
    );
    await f.close();
  } finally {
    rmSync(w.root, { recursive: true, force: true });
  }
});

test("export writes a new Sponge file once and never replaces an existing one", async (t) => {
  const w = workspace();
  try {
    const f = await start(t, w.db, w.schematics);
    const geometry = {
      format: "minecraft-ai-engineer.schematic-geometry",
      formatVersion: 1,
      dataVersion: 3953,
      size: { x: 2, y: 1, z: 1 },
      offset: { x: 0, y: 0, z: 0 },
      palette: [
        { name: "minecraft:stone", properties: {} },
        { name: "minecraft:air", properties: {} },
      ],
      blocks: [0, 1],
    };
    const exportAs = (c: GatewayContext, value: unknown = geometry) =>
      f.post("/v2/schematic/export", {
        context: c,
        name: "selection",
        geometry: value,
      });
    assert.equal((await exportAs(context({ actorId: VIEWER }))).status, 403);
    assert.equal(
      (await exportAs(context(), { ...geometry, verified: true })).status,
      422,
    );
    assert.deepEqual(readdirSync(w.exports), []);

    const first = await exportAs(context({ actorId: EDITOR }));
    assert.equal(first.status, 200, JSON.stringify(first.body));
    assert.equal(first.body.file, "selection.schem");
    const written = readFileSync(join(w.exports, "selection.schem"));
    const back = importSpongeSchematic(written).geometry;
    assert.deepEqual(back.size, geometry.size);
    assert.deepEqual(
      back.blocks.map((i) => back.palette[i]!.name),
      ["minecraft:stone", "minecraft:air"],
    );

    const again = await exportAs(context(), {
      ...geometry,
      blocks: [1, 0],
    });
    assert.equal(again.status, 409);
    assert.match(again.body.error, /already exists and was left unchanged/);
    assert.deepEqual(readFileSync(join(w.exports, "selection.schem")), written);
    await f.close();
  } finally {
    rmSync(w.root, { recursive: true, force: true });
  }
});

test("schematic routes report that they are off when no library folder is configured", async (t) => {
  const w = workspace();
  try {
    const f = await start(t, w.db, undefined);
    const off = await f.post("/v2/schematic/library", { context: context() });
    assert.equal(off.status, 404);
    assert.match(off.body.error, /not configured/);
    await f.close();
  } finally {
    rmSync(w.root, { recursive: true, force: true });
  }
});
