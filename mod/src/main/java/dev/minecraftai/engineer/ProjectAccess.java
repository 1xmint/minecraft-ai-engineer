package dev.minecraftai.engineer;

import com.google.gson.*;
import java.util.*;

/** Server-owned project policy. Neither a client packet nor a model creates grants. */
final class ProjectAccess {
    enum Role { OWNER, EDITOR, VIEWER }
    enum Permission { VIEW, EDIT, BUILD, UNDO, PLAN }
    record Grant(Role role, Set<Permission> permissions) {
        Grant { permissions = Set.copyOf(permissions); }
    }
    final UUID owner;
    private long revision = 1;
    private final Map<UUID, Grant> members = new LinkedHashMap<>();

    ProjectAccess(UUID owner) {
        this.owner = Objects.requireNonNull(owner);
        members.put(owner, defaults(Role.OWNER));
    }
    long revision() { return revision; }
    Map<UUID, Grant> members() { return Map.copyOf(members); }
    boolean allows(UUID actor, Permission permission) {
        Grant grant = members.get(actor);
        return grant != null && grant.permissions().contains(permission);
    }
    void require(UUID actor, Permission permission) {
        if (!allows(actor, permission)) throw new IllegalStateException("This project does not grant you " + permission.name().toLowerCase(Locale.ROOT) + " permission");
    }
    void member(UUID actor, UUID target, Role role) {
        requireOwner(actor);
        if (target.equals(owner) || role == Role.OWNER) throw new IllegalStateException("Project ownership cannot be changed by a membership grant");
        if (!members.containsKey(target) && members.size() >= 128) throw new IllegalStateException("Project member limit reached");
        members.put(target, defaults(role));
        revision = Math.addExact(revision, 1);
    }
    void revoke(UUID actor, UUID target) {
        requireOwner(actor);
        if (target.equals(owner)) throw new IllegalStateException("The owner cannot revoke their own project");
        if (members.remove(target) != null) revision = Math.addExact(revision, 1);
    }
    void permission(UUID actor, UUID target, Permission permission, boolean allowed) {
        requireOwner(actor);
        Grant grant = members.get(target);
        if (grant == null || target.equals(owner)) throw new IllegalStateException("Choose a non-owner project member");
        if (permission == Permission.VIEW || permission == Permission.EDIT || permission == Permission.PLAN
                || grant.role() == Role.VIEWER) throw new IllegalStateException("Only an editor's build and undo rights can be adjusted; model access remains operator-owned");
        var permissions = EnumSet.copyOf(grant.permissions());
        if (allowed) permissions.add(permission); else permissions.remove(permission);
        members.put(target, new Grant(grant.role(), permissions));
        revision = Math.addExact(revision, 1);
    }
    private void requireOwner(UUID actor) {
        if (!owner.equals(actor)) throw new IllegalStateException("Only the project owner can change membership or grants");
    }
    private static Grant defaults(Role role) {
        return new Grant(role, switch (role) {
            case OWNER -> EnumSet.allOf(Permission.class);
            case EDITOR -> EnumSet.of(Permission.VIEW, Permission.EDIT, Permission.BUILD);
            case VIEWER -> EnumSet.of(Permission.VIEW);
        });
    }
    JsonObject json() {
        var result = new JsonObject();
        result.addProperty("revision", revision);
        var entries = new JsonArray();
        members.forEach((id, grant) -> {
            var entry = new JsonObject();
            entry.addProperty("actorId", id.toString());
            entry.addProperty("role", grant.role().name());
            var permissions = new JsonArray();
            grant.permissions().stream().sorted().forEach(p -> permissions.add(p.name()));
            entry.add("permissions", permissions);
            entries.add(entry);
        });
        result.add("members", entries);
        return result;
    }
    static ProjectAccess read(UUID owner, JsonObject value) {
        var policy = new ProjectAccess(owner);
        long revision = value.get("revision").getAsBigDecimal().longValueExact();
        if (revision < 1) throw new IllegalArgumentException("Invalid permission revision");
        var entries = value.getAsJsonArray("members");
        if (entries.isEmpty() || entries.size() > 128) throw new IllegalArgumentException("Invalid project membership");
        policy.members.clear();
        for (JsonElement element : entries) {
            var entry = element.getAsJsonObject();
            var id = UUID.fromString(entry.get("actorId").getAsString());
            var role = Role.valueOf(entry.get("role").getAsString());
            var permissions = EnumSet.noneOf(Permission.class);
            for (JsonElement p : entry.getAsJsonArray("permissions")) permissions.add(Permission.valueOf(p.getAsString()));
            if (!permissions.contains(Permission.VIEW) || (role == Role.OWNER) != id.equals(owner)
                    || (role == Role.OWNER && !permissions.equals(EnumSet.allOf(Permission.class)))
                    || (role == Role.VIEWER && !permissions.equals(EnumSet.of(Permission.VIEW)))
                    || (role != Role.OWNER && permissions.contains(Permission.PLAN))
                    || policy.members.putIfAbsent(id, new Grant(role, permissions)) != null)
                throw new IllegalArgumentException("Invalid or duplicate project grant");
        }
        if (!policy.members.containsKey(owner)) throw new IllegalArgumentException("Project owner missing");
        policy.revision = revision;
        return policy;
    }
}
