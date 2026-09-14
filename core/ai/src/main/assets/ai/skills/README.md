# Bundled skills

These skills ship with AndroidIDE and are loaded read-only by `SkillRegistry`. They are
third-party work, vendored unmodified, and each directory carries the licence it arrived
under. Do not edit them here: a user who wants a changed version should install their own
copy, which takes precedence over the bundled one.

| Skill | Upstream | Licence |
| --- | --- | --- |
| `code-review-checklist` | github.com/curiositech/some_claude_skills | MIT |
| `git-workflow-expert` | github.com/curiositech/some_claude_skills | MIT |
| `refactoring-surgeon` | github.com/curiositech/some_claude_skills | MIT |
| `skill-creator` | github.com/anthropics/skills | Apache-2.0 |

Both licences are compatible with this project's GPLv3 and require that the original
copyright and licence text stay with the files, which is why every directory keeps its
`LICENSE.txt`.

Anthropic's `pdf`, `docx`, `pptx` and `xlsx` skills are deliberately **not** bundled: their
licence forbids redistribution outside Anthropic's own services. Users can still install them
themselves through the skill manager.

The upstream `skill-creator` also ships `scripts/`, `agents/`, `assets/` and an `eval-viewer`
that drive Anthropic's own evaluation harness. Those are omitted because nothing in this app
can run them and they more than doubled the bundle size.
