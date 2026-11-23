# Blockbench models

Provisional 3D bases for the three smithing hammers and the Commander's and Ruler's swords. They are **not wired into the game yet**: the items still use the flat 32x32 textures. Each model has:

- `<item>.bbmodel`: the Blockbench project (texture embedded).
- `<item>.json`: the same model exported as a Java block/item model.
- `<item>_3d.png`: its 64x64 texture (about 2 texels per model unit, like the 32x32 items).

How to use them:
1. Open the `.bbmodel` in Blockbench, adjust the cubes and paint the texture.
2. Export with File > Export > Export Block/Item Model (Java).
3. To use one in game, copy the JSON to `src/main/resources/assets/mitofus/models/item/<item>.json` (the item must be removed from the item model datagen so the two do not collide) and the PNG to `src/main/resources/assets/mitofus/textures/item/<item>_3d.png`.

Notes: the display values are the vanilla `item/handheld` ones, with an unrotated `gui` entry and `gui_light: front`; a 3D item in the inventory usually needs its own `gui` transform. Every cube is rotated 45 degrees around Z at (8, 8, 8) so the tool lies on the diagonal like the flat items.
