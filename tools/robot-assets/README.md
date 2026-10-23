# FORGE AI robot assets

Robot artwork and renderer: [robot-heads](https://github.com/fayazara/robot-heads), copyright 2026 Fayaz Ahmed, MIT licensed. The original renderer files and license are retained in `robot-heads-main/`; the packaged license is `/licenses/robot-heads-MIT.txt`.

FORGE renders its orange square robot into transparent sprite atlases at build-authoring time. The JavaFX application needs no Node.js, React, browser, network, or API request to display the robot. It plays cached frames only while visible. The avatar follows Reduce motion and window visibility.

The generator keeps the upstream appearance and uses deterministic, seamless head movement. Error/search beacon pulses are softened. The six states are idle, thinking, searching, working, happy, and error. A displayed state describes actual application activity; it never reports a result before the corresponding task completes.

To regenerate (optional developer tooling, not needed to run FORGE), use Node.js 24, run `npm install --prefix tools/robot-assets`, then `npm run generate --prefix tools/robot-assets`. Copy `tools/robot-assets/generated/` into `src/main/resources/images/forge-ai/`. The generator records frame geometry in `manifest.json`. JavaFX geometry constants must match that manifest.