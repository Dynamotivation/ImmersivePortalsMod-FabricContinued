26.2.0-alpha.1 (unofficial port to Minecraft 26.2, Fabric)

- Rewrote portal rendering for the 26.2 renderer (Blaze3D): every visible portal is rendered into an offscreen target by the destination dimension's own LevelRenderer, with an oblique clipping projection, and composited into the main view. Works with Sodium 0.9 and Iris 1.11 loaded.
- Each dimension has its own level, level renderer, level extractor and particle engine; remote dimensions keep ticking (entities, block entities, particles, ambient effects).
- Loading placeholder: generating nether portals show the portal texture and fade into the live view. No more loading text.
- Breaking the frame of a portal from either side (also through the portal) removes both ends.
- Optional nether portal overlay (config) is drawn again.
- The online mod info check is disabled by default (it targeted the original mod's 1.21 info).
