---
title: 'Fix Markdownlint'
allowed-tools: Bash(npx markdownlint-cli:*), Read, Edit, Glob
argument-hint: <file-or-directory>
description: Run markdownlint and fix issues. Use when the user asks to "fix markdown", "lint markdown", "fix markdownlint", or "markdownlint".
---

## Context

- Config file: `.markdownlint.yml` in the project root
- CI disables MD013 (line-length) and MD040 (fenced-code-language) — the skill must match this

## Your task

1. Determine the target: use the argument if provided, otherwise default to `"docs/**/*.md"`
2. Run `npx markdownlint-cli -c .markdownlint.yml --disable MD013 MD040 -- <target>` to find issues
3. If no issues are found, report that and stop
4. For each issue, read the affected file and fix the problem using Edit
5. Re-run the linter (same command) to verify all issues are resolved
6. Report what was fixed

## Common fixes

- **MD033/no-inline-html**: Only `<br>` and `<center>` are allowed inline HTML elements
- **MD060/table-column-style**: Align table pipes with header pipes

## Execution

Fix all issues in a single pass. Re-run the linter after fixing to confirm. Do not send any other text besides the tool calls.
