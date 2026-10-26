# Loopback protocol v1

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
