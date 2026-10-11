# 1.0.5 teleport terrain correction

Baseline main 3b4379a, clean checkout at start. Only LynxTools; no client changes,
production access, commit or push. Deployment coordinated separately by Minecraft Server 代理.

Warp must preserve stored dimension/xyz/yaw/pitch, skipping terrain only. Other checks stay.
Back/home/request safety uses native VoxelShape surfaces, complete body clearance and support;
no solid-face assumption or integer-only feet. Keep existing async chunk and per-tick budgets.

Implementation: saved point first; each search lattice anchor sweeps a foot box down through a
one-block vertical interval, then validates body and support at the computed height. Native
BlockCollisions handles extended fence/wall shapes using target-height CollisionContext.

Validation: build/21 existing unit tests; targeted isolated LifecycleProbe terrain branch using
actual enderman_farm fractional coordinates, carpet/fence/pane/wall/slabs/stairs/snow/stone,
void/lava/blocked headroom, warp terrain bypass and home safety. No broad suite or real client claim.
Initial probe compile error was namespace shadowing by its existing `net` field; fixed imports.

Isolated config backed up at .verification/pre-terrain/Config.toml; temporary TerrainTest.db
keeps previous test database unchanged. Restore config after the owned server exits.
Old test JARs also retained in pre-terrain. Final build and 21 unit tests passed.
Native terrain run passed all113 stages, including invalid height/removed dimension/body border.
Owned server exited0; isolated Config restored to hash FEAA4164FC698715D9AA03A2241A28D9C9A3DB2EA96161BE177C9A6B90D887F2.
Evidence docs/validation/1.0.5-terrain.log. Final JAR build/libs/LynxTools-1.0.5.jar,
SHA256 9D0BD1CAF278B175D7F71BB6C8A8B72FE66A0C326ECF73B94070A321B90A2A21.
No production changes or git publication. Coordinator will retrieve final reply for deployment.
