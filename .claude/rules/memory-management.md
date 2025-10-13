# Memory Management Rules

## Local Memory Storage

- **Location**: `.claude/.localmemory/`
- **Purpose**: Task progress and intermediate results
- **Scope**: Local only - NEVER commit to git

## Folder Structure

Pattern: `.claude/.localmemory/{action}-{module-identifier}/`

Where module-identifier is:
- `{vendor}-{module}` for simple modules
- `{vendor}-{suite}-{module}` when suite exists

Examples:
- `create-github-github/`
- `update-amazon-aws-s3/`
- `fix-gitlab-gitlab/`

## Phase Output Files

Standard naming in memory folders:
```
phase-01-discovery.json
phase-02-scaffolding.json
phase-03-api-spec.json
phase-04-type-generation.json
phase-05-implementation.json
phase-06-testing.json
phase-07-documentation.json
phase-08-build.json
phase-09-validation.json
_work/                    # Working files
```

## Context Passing

1. Each phase reads previous phase output
2. Each phase writes standardized output
3. Use JSON format for structured data
4. Include status and metadata

## Important Notes

- Memory folders created by first running task
- No folder = work not started yet
- Clean up after workflow completion
- Never reference in production code