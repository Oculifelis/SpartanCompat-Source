# Material recolor review, 0.2.0

Credit: Oculifelis. Six materials, 31 sprites each, including all bow states. Staged only.

Catalogue searches: exact ingot/crystal names in Create 6.0.6, Ars Nouveau 5.13.2, Occultism 1.224.4 and AE2 19.2.17. Animated ingots/crystals use their first frame for palette sampling. Shapes come from the existing Spartan iron/gold difference masks, under Apache-2.0. No target mod texture is copied into release assets. The vanilla iron sword is an exact QA reference from this project's already built 1.21.1 Minecraft resource artifact.

First-pass criticism, ranked:
1. Enlarged 32-pixel polearms crowded the next cell. Sheet cells increased from 200 to 240 pixels; every sprite now fits without covering its neighbor.
2. Repeating sparse donor palette colors collapsed some shading. A continuous 16-stop interpolated ramp now preserves intermediate brightness and the original bevels.
3. Similar pale materials could become hard to distinguish. Final sheets retain Zinc's green tint, Iesnium's turquoise tint and Certus Quartz's blue tint from their native reference palettes. Fluix keeps violet shadows; Source Gem keeps its brighter magenta palette.

Final inspection: all six sheets read at 1x and 4x beside vanilla and Spartan iron on a dark background. Alpha masks and dimensions match the originals for all 186 sprites. The original top-left highlights, two-tone outlines, wood, bindings, grain and silhouettes are preserved. No filtering, upscaling of the actual assets, extra motif pixels or repainting. The shafts are long because the base mod uses larger source canvases for those weapon types.

Release hold: modules default to disabled. Opt-in uses the existing base iron textures. After the review verdict, the Main Coordinator can copy approved recolors, switch those model texture references and enable the modules by default. The staged textures and these sheets are outside src/main/resources and are absent from the jar.
