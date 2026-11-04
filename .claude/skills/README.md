# Skills

| Skill | Source | State |
|---|---|---|
| `langchain-fundamentals`, `langchain-middleware`, `langgraph-persistence` | [`langchain-ai/langchain-skills`](https://github.com/langchain-ai/langchain-skills) at `126c384d8552dcb476b226c244172186cc3f4328`, MIT | Unmodified |
| `navigation-3`, `edge-to-edge`, `android-permissions-security`, `testing-setup` | [`android/skills`](https://github.com/android/skills) at `6daa53ef27fa2aa87827d183a85c06cedceca833`, license in `LICENSE-android-skills` | Unmodified |
| `project-architecture`, `writing-for-agents` | The author's own | Unmodified |
| `commit-and-push` | The author's own | Adapted: no web version bump, plus the `ci` and `build` commit types this repo accepts |

Third-party skills are copied from a pinned commit after reading them, so an upstream change cannot alter what the agent obeys. To update one, read the diff and change the commit above.

`project-architecture` was written mostly around a TypeScript stack. Here its principles apply, while `AGENTS.md` names the Python mechanisms that replace its lint preset.
