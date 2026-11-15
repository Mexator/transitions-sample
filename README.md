# wordle-transitions

Demo apps for the **Transitions API** talk: a Wordle-style game «Слово» and a separate
dictionary app «Словарь», animated entirely with the Android Transitions API (Views only,
no Compose).

| Module | What it is |
|---|---|
| `:app` | The game: 6×5 board, on-screen keyboard, hint block, finale with a shared word chip |
| `:app-slovar` | «Словарь» — a standalone app that receives the word chip across the process boundary |
| `:core-dict` | Shared word list + articles + the cross-app contract |

**Status: M0** — fully playable, zero animations by design. That is the "before" state of
the talk. Each talk example lands as its own commit and tag:

| Tag | Example |
|---|---|
| `ex0` | App without animations (this state) |
| `ex1` | Letter colors — custom Transition + `beginDelayedTransition` |
| `ex2` | «Играть» → board — `Scene` + `go()`, ChangeBounds |
| `ex3` | Appear/disappear — Visibility + ViewOverlay |
| `ex4` | Cancellation — rapidly toggled hint card |
| `ex5` | Fragment shared element |
| `ex6` | Cross-app Activity shared element |
