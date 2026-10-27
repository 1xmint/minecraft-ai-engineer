# Loopback protocol v1

This describes the shipped single-player reference protocol. It is not a
multiplayer authorization boundary. The planned server gateway, authenticated
actor context, revision fencing and recipient filtering are specified in
[STATE.md](../STATE.md). Minecraft clients must never receive this service bearer token.

Companion binds only 127.0.0.1:43127. Requests require Authorization: Bearer TOKEN;
the shared token is stored in a user-only config file outside world saves/repo.
Reject browser Origin, unknown routes, excessive bodies, and non-loopback hosts.
JSON errors: {error:string}. Response protocol version is 1. No service route can
mutate a world. The mod owns authorization and server-thread operations.

POST /v1/design
Request: {request:string,worldId:string,dimension:string,anchor:{x,y,z},
rotation:0|90|180|270,projectId?:string,maxSize?:{x,y,z}}
Response: {blueprint:Blueprint,message:string,projectId:string}
POST /v1/observe — {worldId,dimension,region:{min,max},blocks:[{pos,state}],tick}
GET /v1/projects?worldId=... — {projects:[...]}
POST /v1/checkpoint — {worldId,dimension,projectId,taskId,status,detail,blueprintRevision?,observed?,verified?}
Functional evidence requires a positive blueprintRevision matching the current
stored blueprint. Stale revision reports are rejected. New revisions, changed
world observations, and fresh checkpoints invalidate previous functional evidence;
historical reports remain in the audit log.
GET /v1/health — {protocol:1,status:'ok'} (also authenticated).

Blueprint format (coordinates relative unless stated):
{schemaVersion:1,id:string,revision:integer,projectId:string,worldId:string,
dimension:string,name:string,explanation:string,anchor:{x,y,z},rotation:0|90|180|270,
blocks:[{pos:{x,y,z},state:'minecraft:block[property=value,...]',stage:string,
component:string,prerequisites:string[]}],
stages:[{id:string,title:string,explanation:string}],
bounds:{min:{x,y,z},max:{x,y,z}},resources:{'minecraft:block':integer},
ports:[{name:string,pos:{x,y,z},kind:'input'|'output'}],
tests:[{id:string,description:string}],
provenance:{kind:'curated',template:string,minecraft:'26.3'},verification:'untested'}

Rotation: (x,z)->(-z,x) per clockwise 90 degrees viewed from above, followed by
anchor translation. Rotate facing and horizontal property keys with coordinates.
Runtime-changing powered/lit/power properties are observations, not construction
mistakes. Registry validates complete block-state strings before preview/build.

Reference template `signal-lamp`: solid supports at (0,0,0), (1,0,0), (2,0,0);
floor lever at (0,1,0) facing east; repeater at (1,1,0) facing west delay=1;
redstone lamp at (2,1,0). Stages `foundation`, `input`, `output`. The repeater
outputs east into the lamp (the repeater's facing state points toward its input).
Test off->on->off using scheduled ticks. Test only
this curated circuit and restore input; never infer arbitrary circuit validity.
Blueprints can be structurally valid but remain functionally untested until
the actual mod records game test evidence for that project and revision.

## Server gateway v2 — multiplayer foundation

V2 uses the same native loopback/Host/Origin/body limits and server-private
bearer token. It is a Fabric-to-companion gateway, not a player HTTP API.
Fabric derives the actor from its authenticated Minecraft connection and remains
the only permission/execution authority. Node mirrors server assertions for
privacy and stale-result rejection; it never grants a player world access.
V1 remains for the historical single-player release and is not used by the
multiplayer gateway.

Every project operation carries:

```json
{
  "context": {
    "serverId": "server-namespace",
    "serverEpoch": "current-boot-uuid",
    "actorId": "11111111-1111-4111-8111-111111111111",
    "projectId": "project-uuid",
    "projectRevision": 1,
    "authorizationRevision": 1,
    "requestId": "unique-operation-uuid"
  }
}
```

POST `/v2/server/open` accepts `{serverId,serverEpoch,serverGeneration}` and responds with
`{protocol:2,status:'ready',serverId,serverEpoch,serverGeneration}`. Fabric increments
the positive safe-integer generation durably under a private file lock before
each new Minecraft boot. The companion durably rejects lower generations, even
for epochs it has never seen. An equal generation must retain the same epoch;
a higher generation requires a new epoch. Reopening the same boot after a
companion restart is allowed. Registration is live-process
state: after a companion restart, Fabric must reopen and synchronize projects.
Previously retired epochs cannot be reopened, including after service restart.
A new registration pauses old task mirrors, invalidates evidence and aborts
queued/running old planning requests. Ownership/history remain durable.

POST `/v2/project` accepts the context plus `worldId`, `dimension`, optional
`name`/`blueprint`, and `members:[{actorId,role,canPlan}]`. Role is lowercase
`owner`, `editor` or `viewer`, with exactly one owner and explicit `canPlan`.
The first synchronization is owner-authored; editor/owner updates must match
the current membership. Only an existing owner can mirror changed membership;
the trusted Fabric server may assert `isOperator:true` for an administrator.
Changed membership requires a higher authorization revision. Blueprint records
are immutable, and a geometry change requires a higher project revision.
An optional minimal Java blueprint is normalized to the typed format above;
actual block-registry validation still belongs to Minecraft.

POST `/v2/checkpoint` and `/v2/observe` accept the context plus their v1 body
fields and a required integer `reportSequence` from 1 to `Number.MAX_SAFE_INTEGER`.
Fabric assigns one increasing sequence per project/server epoch across both
report routes on the server thread. The companion durably rejects duplicate or
older sequences, including after a service restart. A new Minecraft server epoch
starts a new sequence; a fresh random request ID alone cannot make an old report
current. This prevents late progress from undoing a newer observation or test.
A checkpoint must match the current world/project and revision, and
functional evidence requires its synchronized intended blueprint. Changes,
revocation and fresh checkpoints clear stale functional evidence while audit
records remain. Request IDs for mutating operations are one-use, even after
restart. A retry must be a new explicit operation, not replayed authority.

Composite checkpoint task IDs are `projectUUID:component`. Their observed payload
includes the component, basis/version, assignment generation and descriptive
assignments. `testScope` identifies captured vectors by project, exact blueprint
id/revision, component and basis/version. `lastPassedScope` may point to an older
immutable revision when an unrelated component changed; empty current vectors
are never a new test. Historical audit records keep their original identity.
These fields describe work; none grants a Minecraft execution lease.

On intent sync, the companion derives leaf geometry and transitive prerequisites
from canonical stored blueprints. It preserves unchanged historical task rows,
pauses changed/removed/unscoped tasks and never carries live execution across a
project revision. Authorization changes and service/server registration pause
all tasks. World observations compare coordinate history across the retained 20
snapshots and invalidate only intersecting component/read closures; each closure
includes a two-block margin. Newly covered coordinates that contradict canonical
geometry also invalidate affected results. Legacy unscoped rows fail closed.
The comparison fingerprint is local to companion invalidation and is not the
Fabric assignment basis or an execution credential.

POST `/v2/project/read` accepts context and returns the authorized project and
current blueprint. GET `/v2/projects?serverId=...&serverEpoch=...&actorId=...&worldId=...`
returns only that actor's current project memberships. Equal project UUIDs in
different server namespaces do not share data. These read routes still require
the private server bearer token; player UUID query fields are not authentication.

POST `/v2/design` accepts context plus the v1 design fields, `modelAuthorized:true`
and `isOperator:true`. Initially only a mirrored owner with `canPlan` may request
operator-owned planning. This technical boundary does **not** establish that
one subscription can serve arbitrary multiplayer users. General shared account
access stays disabled pending the eligibility decision in STATE.md.

One fair queue serializes v1/v2 inference. At most eight requests wait, with
one waiting request per actor. Defaults cap v2 planning to four starts per actor
per minute and 24 per server per hour; counters survive service restart. Queued
work rechecks epoch, membership and both revisions before invoking the provider,
and again before returning. Cancellation/revocation discards late replies.

A design result returns the unchanged request context, a proposal at
`projectRevision+1`, message and projectId. It records an audit proposal only.
Fabric must validate and accept the proposal, then synchronize the new revision
through `/v2/project`; the model response cannot advance canonical authority.
Every response is protocol 2. Invalid/stale authority returns 403/409, exhausted
budgets 429, and cancelled/unavailable planning 503. No route mutates Minecraft.
