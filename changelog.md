26.2.0-alpha.2 (merge of the Minecraft 26.3 fork, still targeting 26.2)

Merged the history of Kurokekkon/ImmersivePortalsMod (26.3 branch, built on CaseBacon/ImmersivePortalsMod) into this fork. The 26.2 portal renderer of this fork was kept; the following was taken from the 26.3 fork:
- Validation of portal teleport requests (mounted players), TeleportRequestCheck.
- Chunk loading: client performance level based on free memory instead of frame rate, spurious chunk loading failure fixes, chunk sync changes.
- Packet redirection and networking fixes, global portal storage fixes.
- Optional compatibility mixins for Simple Voice Chat and Polymer.
- Camera angle helper for rolled portal views.
- Client and server tests (skipped without an accepted EULA).
Credits: qouteall and the iPortalTeam (original mod), CaseBacon (26.3 port), Kurokekkon (26.3 stabilization). Apache-2.0.

26.2.0-alpha.1 (unofficial port to Minecraft 26.2, Fabric)

- Rewrote portal rendering for the 26.2 renderer (Blaze3D): every visible portal is rendered into an offscreen target by the destination dimension's own LevelRenderer, with an oblique clipping projection, and composited into the main view. Works with Sodium 0.9 and Iris 1.11 loaded.
- Each dimension has its own level, level renderer, level extractor and particle engine; remote dimensions keep ticking (entities, block entities, particles, ambient effects).
- Loading placeholder: generating nether portals show the portal texture and fade into the live view. No more loading text.
- Breaking the frame of a portal from either side (also through the portal) removes both ends.
- Optional nether portal overlay (config) is drawn again.
- The online mod info check is disabled by default (it targeted the original mod's 1.21 info).
