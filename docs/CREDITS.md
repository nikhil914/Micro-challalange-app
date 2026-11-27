# Credits and licences

MicroMind ships no third-party game code, images, sounds or fonts. All game logic is
original Kotlin, and all game art is drawn at runtime with Compose Canvas
(`app/src/main/java/com/microchallenge/app/games/GameArt.kt`).

Some game mechanics come from classic, public puzzle types or from open-source projects.
Game mechanics are not covered by copyright, but we credit the sources we drew on:

| Game | Mechanic and source | Licence | What we used |
|------|---------------------|---------|--------------|
| Slide Shift | "Sixteen" from [Simon Tatham's Portable Puzzle Collection](https://www.chiark.greenend.org.uk/~sgtatham/puzzles/) | [MIT](https://www.chiark.greenend.org.uk/~sgtatham/puzzles/doc/licence.html) | The idea only: slide whole rows and columns, tiles wrap around. No code was copied. |
| Lights Out | "Flip" from the same collection, and the 1995 Tiger Electronics toy | MIT (collection) | The idea only. No code was copied. |
| Pair Up | "Concentration" / "Memory", a traditional card game | Public domain | The idea only. |
| Star Catcher | Whack-a-target reflex game, with a Go/No-Go twist from cognitive psychology | Public domain | The idea only. |
| Bubble Sum | Subset-sum to a target, a common maths drill | Public domain | The idea only. |
| Weigh In | Balance-scale ordering puzzles (transitive inference) | Public domain | The idea only. |
| Rhythm Tap | Timing-to-a-beat tap game, a common rhythm-game mechanic | Public domain | The idea only. Rings, bursts and effects are original Canvas drawing. |
| Stack Tower | Tap-to-drop block stacker, a common arcade timing genre | Public domain | The idea only: slice off the overhang, perfect drops snap. All art is drawn in code. |

## Rules for adding third-party material
- Code: only MIT, BSD, Apache-2.0 or similarly permissive licences. Keep the original
  copyright notice in the file and add a row above.
- Art and sound: only CC0 (for example [Kenney](https://kenney.nl/assets) packs) or CC-BY
  with the attribution written here and in the app's About screen.
- Never copy a commercial game's code, art, names or look.
