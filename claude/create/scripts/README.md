# Module Creation Automation Scripts

This directory contains scripts to automate the module creation workflow, with improved module identifier detection and permission handling.

## Scripts

### 1. `create-module.sh` (Interactive)
Semi-automated script that prompts for continuation between tasks.

**Usage:**
```bash
./claude/create/scripts/create-module.sh "create a new module for GitHub with operations list repos, create repo, delete repo"
```

**Features:**
- Runs tasks sequentially
- Prompts for continuation after each task
- Supports auto-continue mode
- Colored output for better readability
- Error handling with exit codes
- **Intelligent module identifier detection** from Task 01 output
- **Auto-detects permission requirements** and uses `--dangerously-skip-permissions` when needed

### 2. `create-module-auto.sh` (Fully Automated)
Fully automated script that runs all tasks without user interaction.

**Usage:**
```bash
./claude/create/scripts/create-module-auto.sh "create a new module for GitHub with operations list repos, create repo, delete repo"
```

**Features:**
- Fully automated execution
- Smart completion detection
- Progress tracking via memory files
- **Real module identifier extraction** from Task 01 JSON output
- Safety limits to prevent infinite loops
- **Permission handling** to prevent hanging on permission prompts

## Key Improvements

### 1. **Module Identifier Detection**
Instead of guessing module identifiers, scripts now:
- Read Task 01 output JSON: `.claude/.localmemory/{module-id}/task-01-output.json`
- Extract `modulePackage` field (e.g., `@zerobias-org/module-atlassian-cloud-bitbucket`)
- Convert to proper module identifier (e.g., `create-atlassian-cloud-bitbucket`)
- Fall back to directory name if JSON doesn't exist yet

### 2. **Permission Management**
Scripts automatically detect if Claude needs `--dangerously-skip-permissions`:
- Test Claude with a simple command with timeout
- Add the flag automatically if hanging is detected
- Prevents workflow interruption due to permission prompts

## How It Works

Both scripts work by repeatedly calling:
```bash
claude [--dangerously-skip-permissions] -p "run next unfinished task for the following prompt: {your original prompt}"
```

The automation continues until:
1. Claude indicates all tasks are completed
2. All task output files are generated (more reliable than status files)
3. Maximum iteration limit is reached (safety measure)

## Task Completion Detection

The scripts use multiple methods to detect when all tasks are complete:

1. **Output Analysis**: Looks for completion keywords in Claude's output
2. **Task Output Files**: Counts `task-*-output.json` files vs total task definitions
3. **Memory Files**: Checks task status files in `.claude/.localmemory/{module-id}/`
4. **Smart Progress Tracking**: Uses actual task definition count from `claude/create/tasks/`

## Module Identifier Resolution

The improved identifier detection works as follows:

```bash
# 1. Look for existing memory directories
for dir in .claude/.localmemory/create-*; do
    # 2. Try to read task-01-output.json
    if [ -f "$dir/task-01-output.json" ]; then
        # 3. Extract modulePackage and convert
        # @zerobias-org/module-vendor-service → create-vendor-service
        module_id=$(jq -r '.modulePackage' | sed 's/@[^/]*\/module-/create-/')
    fi
done
```

This ensures accurate module identification throughout the workflow.

## Error Handling

- Exit immediately on Claude execution failure
- Colored output for different message types (info, success, warning, error)
- Safety iteration limits to prevent infinite loops
- Proper exit codes for integration with other tools
- **Timeout protection** for permission detection

## Examples

### Interactive Mode
```bash
# Run with user prompts - permission handling automatic
./claude/create/scripts/create-module.sh "create a new module for Atlassian Bitbucket with operations list repos, get repo, list pull requests"
```

### Automated Mode  
```bash
# Run completely automated - no hanging on permissions
./claude/create/scripts/create-module-auto.sh "create a new module for AWS S3 with operations upload file, download file, list objects"
```

## Configuration

You can modify the following variables in the scripts:
- `MAX_ITERATIONS`: Maximum number of task iterations (default: 15-20)
- Color codes for different output types
- **Module identifier extraction logic** (now reads from Task 01 JSON)
- **Permission timeout** for auto-detection (default: 10 seconds)

## Requirements

- Bash shell
- `claude` CLI tool installed and configured
- `jq` for JSON processing (used for module identifier extraction)
- Proper permissions to execute scripts (`chmod +x` applied)

## Migration from Old Scripts

If you were using scripts from `./scripts/`, the new location is:
```bash
# Old location
./scripts/create-module.sh

# New location
./claude/create/scripts/create-module.sh
```

The new scripts are backward compatible but with much more reliable module identifier detection.