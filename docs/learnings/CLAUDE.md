# Learnings Directory

## Format Requirements

All learning files in this directory must conform to the format produced by the `eng:workflows:compound` plugin (the `/eng:workflows:compound` skill).

Before adding or modifying any learning file, verify that:

1. **YAML frontmatter** includes all required fields: `title`, `date`, `category`, `component`, `module`, `problem_type`, `severity`, `symptoms`, `root_cause`, `tags`
2. **Category subdirectory** matches the `category` field in the frontmatter (e.g. `build-errors/`, `logic-errors/`, `performance/`)
3. **Body sections** follow the standard structure: `## Problem`, `## Root Cause`, `## Solution`, `## Prevention`, `## References`

If you are unsure whether the format is correct, prompt the user to run the `eng:workflows:compound` skill to verify or generate the learning in the correct format.
