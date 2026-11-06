# Skills

| Skill | Source | State |
|---|---|---|
| `langchain-fundamentals`, `langchain-middleware`, `langgraph-persistence` | [`langchain-ai/langchain-skills`](https://github.com/langchain-ai/langchain-skills) at `126c384d8552dcb476b226c244172186cc3f4328`, MIT | Unmodified |
| `navigation-3`, `edge-to-edge`, `android-permissions-security`, `testing-setup` | [`android/skills`](https://github.com/android/skills) at `6daa53ef27fa2aa87827d183a85c06cedceca833`, license in `LICENSE-android-skills` | Unmodified |
| `jetpack-compose-performance`, `r8-analyzer` | [`android/skills`](https://github.com/android/skills) at `bf68e2d66a992a1e6aeb7e9263293cc23fcbf475`, license in `LICENSE-android-skills` | Unmodified |
| `break-ui` | [`emilkowalski/skills`](https://github.com/emilkowalski/skills) at `c8a79627b59d72d764d6ab6f2c87c09a48169833`, license in `LICENSE-emilkowalski-skills` | Unmodified. Written for the web: on Android its toggle becomes a dev-only fake backend and its 200% zoom becomes `font_scale 2.0` |
| `project-architecture`, `writing-for-agents` | The author's own | Unmodified |
| `commit-and-push` | The author's own | Adapted: no web version bump, plus the `ci` and `build` commit types this repo accepts |

Third-party skills are copied from a pinned commit after reading them, so an upstream change cannot alter what the agent obeys. To update one, read the diff and change the commit above.

`project-architecture` was written mostly around a TypeScript stack. Here its principles apply, while `AGENTS.md` names the Python mechanisms that replace its lint preset.
