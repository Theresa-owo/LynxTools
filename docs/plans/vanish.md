# Vanish and command feedback

Baseline: da565dc on main; preserve the uncommitted 1.0.1 default-permission changes.
User now requires gm actor feedback (supersedes old silent-success requirement), and
permission-gated vanish invisible to viewers without the same permission.
No remote writes or production access. LynxTags remains unchanged.

Plan: inspect patched 26.1.2 sources; update feedback; implement native per-viewer
tracking and associated visibility/lifecycle rules; document constraints; compile,
run focused checks and package. No claim of full client/mod compatibility from build alone.
User clarified: keep attacks, drop/pickup, item/entity interaction;
silent containers, hide TAB/player-name completion. Creative mode only to enable.
Leaving creative, permission loss or module disable ends vanish. No interaction bans.
Later clarification: chat from vanished players visible only to self and authorised viewers.
Additional fix: full-label gradient via safe <label>, spacing prefix/name/suffix.
LynxTags 1.0.1 captures preview labels at extraction and restores at native submit.

Risks: unattributed mod sounds/particles and arbitrary mod packets cannot be universally
attributed to an actor; vanilla interactions can expose presence without an entity.
Rollback: replace JAR with previous version; missing-only config installation preserves files.

State: 18 server unit tests pass. Native vanish probe passed. Real isolated client passed
F5 front/back, first-person suppression, named mobs and creative inventory preview.
YSM 2.6.5 failed its native library in dev environment (err 54), so actual YSM custom
model rendering remains unverified. Owned tests stopped; isolated Config/server.properties
restored from .verification/pre-namecheck byte-identically. User client untouched.
Final server rebuild/load passed after adding native attack/chat-decorator attribution.
Owned server uses .verification/server, loopback 25579; no production access or user client control.
Earlier fixture failure was missing native world registration, fixed. Probe metadata updated to 1.0.2.
Original 1.0.0 test JARs retained under .verification/pre-vanish-mods.
Coordination authorised with thread 01a1267f-4dc1-7ae2-b319-239a218d2bb9 (Minecraft Server 代理).
It confirmed production still 1.0.0 and nametags disabled, then enabled only nametags and reloaded.
User screenshots confirm F5 protocol now works; full label still requires server 1.0.2 deployment.
No commit/push. Send final paths/hashes to coordinator when validated; do not deploy from this thread.

1.0.3 follow-up: coordinator requested current LP meta name-gradient-start/end, strict #RRGGBB,
whole-label override and fallback to config, legacy affix color-code stripping before MiniMessage.
Implemented without changing LP data or LynxTags. Build + verification compile and all 21 tests passed.
No game/server launch for this follow-up; runtime context/group acceptance belongs to coordinator.

1.0.4 follow-up: native join/quit takeover, configurable modules.presence and presence.join/quit,
language markers exactly &8&l[&a&l+&8&l] / &8&l[&c&l-&8&l], full current-LP label.
Replace native broadcast calls; delayed join until login registration, quit before LP/vanish cleanup.
Client LynxTags1.0.2 inventory_follow_rotation=true: native submit uses body yaw and retains preview tilt.
Builds/21 server tests pass; server probe marker styles/LP label/vanish quit passed.
Real client + loopback server verification passed, logs presence-rotation-client / presence-client-server.
One formatted join, no vanilla duplicate; native quit hook confirmed. Inventory left/right rotation screenshots checked.
Owned sessions exited normally; isolated Config/server.properties restored byte-identically from .verification/pre-presence-client.
Final 1.0.4 server / 1.0.2 client paths and hashes sent to Minecraft Server 代理 for its user-authorised installation.
