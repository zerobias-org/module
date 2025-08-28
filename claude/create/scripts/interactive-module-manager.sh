#!/bin/bash

# Smart Interactive Module Manager
# Manages module creation workflows with task isolation and progress tracking

set -e

# Colors for human-friendly output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Function to print colored output with timestamps
print_status() {
    echo -e "${BLUE}[$(date +'%H:%M:%S')] INFO${NC} $1"
}

print_success() {
    echo -e "${GREEN}[$(date +'%H:%M:%S')] SUCCESS${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[$(date +'%H:%M:%S')] WARNING${NC} $1"
}

print_error() {
    echo -e "${RED}[$(date +'%H:%M:%S')] ERROR${NC} $1"
}

print_task_start() {
    echo -e "${PURPLE}[$(date +'%H:%M:%S')] TASK START${NC} $1"
}

print_task_complete() {
    echo -e "${CYAN}[$(date +'%H:%M:%S')] TASK COMPLETE${NC} $1"
}

print_separator() {
    echo "════════════════════════════════════════════════════════════════"
}

# Function to process and display Claude stream JSON
process_claude_stream() {
    local claude_command="$1"
    local original_prompt="$2"
    local messages=()
    local first_display=true
    
    # Function to clear and show current messages
    show_messages() {
        # Clear screen and show command
        clear
        echo -e "${BLUE}[INFO]${NC} Executing: $claude_command"
        
        # Show original prompt if available
        if [ -n "$original_prompt" ] && [ "$original_prompt" != "No original prompt found" ]; then
            echo -e "${CYAN}[ORIGINAL PROMPT]${NC} $original_prompt"
        fi
        echo
        
        # Show last N lines of messages (adaptive to terminal height)
        local terminal_height=$(tput lines 2>/dev/null || echo 50)
        local max_display_lines=$((terminal_height - 8))  # Reserve space for header/footer
        if [ $max_display_lines -lt 20 ]; then
            max_display_lines=40  # Fallback for very small terminals
        fi
        local all_lines=()
        
        # Convert all messages to individual lines
        for ((i=0; i<${#messages[@]}; i++)); do
            while IFS= read -r line; do
                all_lines+=("  $line")
            done <<< "${messages[i]}"
        done
        
        # Show only the last max_display_lines
        local total_lines=${#all_lines[@]}
        local start_line=$((total_lines > max_display_lines ? total_lines - max_display_lines : 0))
        
        for ((i=start_line; i<total_lines; i++)); do
            echo "${all_lines[i]}"
        done
        
        echo
    }
    
    # Add a message to the buffer and refresh display
    add_message() {
        local msg="$1"
        messages+=("$msg")
        show_messages
    }
    
    # Process stream line by line
    local json_buffer=""
    while IFS= read -r line; do
        # Skip empty lines
        [ -z "$line" ] && continue
        
        # Build JSON buffer
        json_buffer+="$line"
        
        # Try to parse as complete JSON
        if echo "$json_buffer" | jq -e . >/dev/null 2>&1; then
            local msg_type=$(echo "$json_buffer" | jq -r '.type // "unknown"')
            local timestamp="$(date '+%H:%M:%S')"
            
            case "$msg_type" in
                "system")
                    local subtype=$(echo "$json_buffer" | jq -r '.subtype // ""')
                    if [ "$subtype" = "init" ]; then
                        local model=$(echo "$json_buffer" | jq -r '.model // "unknown"' | cut -d'-' -f3-)
                        add_message "[$timestamp] Started with model: $model"
                    fi
                    ;;
                "user")
                    # Show both user messages and tool results
                    local has_tool_results=$(echo "$json_buffer" | jq -r '.message.content[0].type // "text"')
                    if [ "$has_tool_results" = "tool_result" ]; then
                        # Show tool result summary
                        local tool_use_id=$(echo "$json_buffer" | jq -r '.message.content[0].tool_use_id // ""' 2>/dev/null)
                        local is_error=$(echo "$json_buffer" | jq -r '.message.content[0].is_error // false' 2>/dev/null)
                        local content=$(echo "$json_buffer" | jq -r '.message.content[0].content // ""' 2>/dev/null)
                        
                        if [ "$is_error" = "true" ]; then
                            add_message "[$timestamp] Tool Error: $content"
                        else
                            # Show meaningful tool result with better formatting
                            local clean_content=$(printf '%s' "$content" | head -c 3000 | sed 's/[[:space:]]\+/ /g' | sed 's/ \. /.\n/g' | sed 's/ - /\n- /g')
                            
                            # Try to extract meaningful info based on content type
                            if [[ "$content" =~ ^[[:space:]]*\{.*\}[[:space:]]*$ ]]; then
                                # JSON content - show status or key fields, but also show more content
                                local status=$(echo "$content" | jq -r '.status // empty' 2>/dev/null)
                                local message=$(echo "$content" | jq -r '.message // .description // empty' 2>/dev/null)
                                if [ -n "$status" ]; then
                                    add_message "[$timestamp] Tool Result: Status: $status${message:+ - $message}"
                                fi
                                # Also show the actual JSON content (truncated)
                                local json_preview=$(printf '%s' "$content" | head -c 2000)
                                if [ ${#content} -gt 2000 ]; then
                                    json_preview="$json_preview..."
                                fi
                                # Indent multi-line JSON content
                                local indented_json=$(echo "$json_preview" | sed '2,$s/^/    /')
                                add_message "[$timestamp] Tool Result: $indented_json"
                            elif [[ "$content" =~ ^[[:space:]]*[0-9]+→ ]]; then
                                # File content with line numbers - show more lines
                                local file_preview=$(echo "$content" | head -n20 | sed 's/^[[:space:]]*[0-9]*→//')
                                # Indent multi-line file content
                                local indented_file=$(echo "$file_preview" | sed '2,$s/^/    /')
                                add_message "[$timestamp] Tool Result: File content: $indented_file"
                            elif [ ${#content} -lt 500 ]; then
                                # Short content - show it all
                                local indented_content=$(echo "$clean_content" | sed '2,$s/^/    /')
                                add_message "[$timestamp] Tool Result: $indented_content"
                            else
                                # Long content - show more than before
                                if [ ${#clean_content} -gt 2500 ]; then
                                    clean_content="${clean_content:0:2500}..."
                                fi
                                # Indent multi-line content
                                local indented_content=$(echo "$clean_content" | sed '2,$s/^/    /')
                                add_message "[$timestamp] Tool Result: $indented_content"
                            fi
                        fi
                    else
                        # Regular user message
                        local content=$(echo "$json_buffer" | jq -r '.message.content // .message // ""' 2>/dev/null)
                        if [ -n "$content" ] && [ "$content" != "null" ] && [ ${#content} -gt 10 ]; then
                            local clean_content=$(echo "$content" | tr '\n\r' ' ' | sed 's/  */ /g')
                            add_message "[$timestamp] User: $clean_content"
                        fi
                    fi
                    ;;
                "assistant")
                    # Show both text content and tool usage
                    local has_text_content=false
                    local has_tool_use=false
                    
                    # Check for text content
                    local text_content=$(echo "$json_buffer" | jq -r '[.message.content[] | select(.type == "text") | .text] | join(" ")' 2>/dev/null)
                    if [ -n "$text_content" ] && [ "$text_content" != "null" ] && [ ${#text_content} -gt 10 ]; then
                        has_text_content=true
                        local clean_content
                        clean_content=$(printf '%s' "$text_content" | sed 's/[[:space:]]\+/ /g' | sed 's/ \. /.\n/g' | sed 's/ - /\n- /g')
                        if [ ${#clean_content} -gt 2000 ]; then
                            clean_content="${clean_content:0:2000}..."
                        fi
                        # Indent multi-line Claude content
                        local indented_claude=$(echo "$clean_content" | sed '2,$s/^/    /')
                        add_message "[$timestamp] Claude: $indented_claude"
                    fi
                    
                    # Check for tool usage
                    local tool_uses=$(echo "$json_buffer" | jq -r '.message.content[] | select(.type == "tool_use") | .name' 2>/dev/null)
                    if [ -n "$tool_uses" ]; then
                        has_tool_use=true
                        local tool_list=$(echo "$tool_uses" | tr '\n' ',' | sed 's/,$//')
                        add_message "[$timestamp] Claude: Using tools: $tool_list"
                    fi
                    ;;
                "result")
                    local cost=$(echo "$json_buffer" | jq -r '.total_cost_usd // "0"')
                    local duration=$(echo "$json_buffer" | jq -r '.duration_ms // "0"')
                    local duration_sec=$((duration / 1000))
                    add_message "[$timestamp] Completed - Cost: \$$cost, Duration: ${duration_sec}s"
                    ;;
            esac
            
            # Reset buffer
            json_buffer=""
        elif [[ "$json_buffer" =~ ^\{.*\}$ ]]; then
            # Complete JSON but failed to parse - reset
            json_buffer=""
        fi
        
        # Handle non-JSON lines
        if [[ "$line" != "{"* ]] && [ -z "$json_buffer" ]; then
            # If it's a meaningful non-JSON line, show it
            if [[ "$line" =~ [a-zA-Z] ]] && [ ${#line} -gt 5 ]; then
                # Show full line content
                local clean_line=$(echo "$line" | tr '\n\r' ' ' | sed 's/  */ /g')
                add_message "[$timestamp] Output: $clean_line"
            fi
        fi
    done
    
    # Final display
    show_messages
}

# Get absolute repository root
REPO_ROOT="/Users/ctamas/code/zborg/module"
MEMORY_BASE_DIR="$REPO_ROOT/.claude/.localmemory"
TASKS_DIR="$REPO_ROOT/claude/create/tasks"

# Function to discover existing module works
discover_existing_works() {
    local works=()
    if [ -d "$MEMORY_BASE_DIR" ]; then
        for dir in "$MEMORY_BASE_DIR"/*; do
            if [ -d "$dir" ]; then
                local work_name=$(basename "$dir")
                # Extract action and module identifier
                if [[ "$work_name" =~ ^([^-]+)-(.+)$ ]]; then
                    local action="${BASH_REMATCH[1]}"
                    local module_id="${BASH_REMATCH[2]}"
                    works+=("$work_name:$action:$module_id")
                fi
            fi
        done
    fi
    printf '%s\n' "${works[@]}"
}

# Function to get task progress for a module work
get_task_progress() {
    local work_name="$1"
    local work_dir="$MEMORY_BASE_DIR/$work_name"
    
    if [ ! -d "$work_dir" ]; then
        echo "0:0:no_tasks"
        return
    fi
    
    # Count total available tasks
    local total_tasks=$(find "$TASKS_DIR" -name "task-*.md" | grep -E 'task-[0-9]+-.*\.md' | wc -l | tr -d ' ')
    
    # Count completed tasks by checking output files and their status
    local completed_tasks=0
    local failed_tasks=0
    local in_progress_tasks=0
    
    for output_file in "$work_dir"/task-*-output.json; do
        if [ -f "$output_file" ]; then
            # Check if the output contains a status field
            local status=""
            if command -v jq >/dev/null 2>&1; then
                # Check top-level status first
                status=$(jq -r '.status // empty' "$output_file" 2>/dev/null)
                # If no top-level status, check nested status patterns
                if [ -z "$status" ]; then
                    status=$(jq -r '.prerequisitesCheck.status // .scaffolding.status // .apiDefinition.status // .moduleImplementation.status // .implementationSummary.status // empty' "$output_file" 2>/dev/null)
                fi
            else
                # Fallback without jq
                if grep -q '"status".*"completed"' "$output_file" 2>/dev/null; then
                    status="completed"
                elif grep -q '"status".*"failed"' "$output_file" 2>/dev/null; then
                    status="failed"
                elif grep -q '"status".*"error"' "$output_file" 2>/dev/null; then
                    status="error"
                elif grep -q '"status".*"in_progress"' "$output_file" 2>/dev/null; then
                    status="in_progress"
                fi
            fi
            
            case "$status" in
                "completed"|"passed")
                    completed_tasks=$((completed_tasks + 1))
                    ;;
                "failed"|"error")
                    failed_tasks=$((failed_tasks + 1))
                    ;;
                "in_progress")
                    in_progress_tasks=$((in_progress_tasks + 1))
                    ;;
                *)
                    # If status is present but unrecognized, count as completed
                    if [ -n "$status" ]; then
                        completed_tasks=$((completed_tasks + 1))
                    fi
                    ;;
            esac
        fi
    done
    
    echo "$completed_tasks:$total_tasks:$failed_tasks:$in_progress_tasks"
}

# Function to find next unfinished task
find_next_task() {
    local work_name="$1"
    local work_dir="$MEMORY_BASE_DIR/$work_name"
    
    # Get list of available task numbers from task definition files
    local available_tasks=()
    for task_file in "$TASKS_DIR"/task-*.md; do
        if [[ $(basename "$task_file") =~ ^task-([0-9]+)- ]]; then
            available_tasks+=(${BASH_REMATCH[1]})
        fi
    done
    
    # Sort task numbers
    IFS=$'\n' available_tasks=($(sort -n <<<"${available_tasks[*]}"))
    unset IFS
    
    # Find first task without completed output
    for task_num in "${available_tasks[@]}"; do
        local task_output="$work_dir/task-$(printf "%02d" $((10#$task_num)))-output.json"
        
        if [ ! -f "$task_output" ]; then
            echo "$task_num"
            return
        fi
        
        # Check if task failed or is in progress
        local status=""
        if command -v jq >/dev/null 2>&1; then
            status=$(jq -r '.status // .prerequisitesCheck.status // .scaffolding.status // .apiDefinition.status // .moduleImplementation.status // .implementationSummary.status // empty' "$task_output" 2>/dev/null)
        else
            if grep -q '"status".*"failed\|error"' "$task_output" 2>/dev/null; then
                status="failed"
            elif grep -q '"status".*"in_progress"' "$task_output" 2>/dev/null; then
                status="in_progress"
            fi
        fi
        
        if [[ "$status" == "failed" || "$status" == "error" || "$status" == "in_progress" ]]; then
            echo "$task_num"
            return
        fi
    done
    
    echo "all_complete"
}

# Function to get human-readable task name
get_task_name() {
    local task_num="$1"
    local task_file="$TASKS_DIR/task-$(printf "%02d" $((10#$task_num)))-*.md"
    local task_files=($task_file)
    
    if [ -f "${task_files[0]}" ]; then
        # Extract task name from filename
        local filename=$(basename "${task_files[0]}")
        local task_name=$(echo "$filename" | sed 's/^task-[0-9]*-//' | sed 's/\.md$//' | tr '-' ' ')
        echo "$task_name"
    else
        echo "Task $task_num"
    fi
}

# Function to detect claude permission requirements
needs_skip_permissions() {
    if timeout 10 claude -p "echo test" >/dev/null 2>&1; then
        echo "false"
    else
        echo "true"
    fi
}

# Function to show interactive menu
show_menu() {
    clear
    print_separator
    echo -e "${BLUE}  Smart Interactive Module Manager${NC}"
    print_separator
    echo
    
    local existing_works=($(discover_existing_works))
    
    if [ ${#existing_works[@]} -eq 0 ]; then
        print_status "No existing module works found in $MEMORY_BASE_DIR"
    else
        print_status "Existing module works:"
        echo
        local index=1
        for work_entry in "${existing_works[@]}"; do
            IFS=':' read -r work_name action module_id <<< "$work_entry"
            local progress=$(get_task_progress "$work_name")
            IFS=':' read -r completed total failed in_progress <<< "$progress"
            
            local status_text=""
            if [ "$total" = "no_tasks" ]; then
                status_text="${YELLOW}No tasks found${NC}"
            elif [ "$completed" -eq "$total" ] && [ "$failed" -eq 0 ]; then
                status_text="${GREEN}Complete ($completed/$total)${NC}"
            elif [ "$failed" -gt 0 ]; then
                status_text="${RED}Failed tasks: $failed, Completed: $completed/$total${NC}"
            elif [ "$in_progress" -gt 0 ]; then
                status_text="${YELLOW}In progress: $in_progress, Completed: $completed/$total${NC}"
            else
                status_text="${CYAN}Progress: $completed/$total${NC}"
            fi
            
            echo -e "  ${PURPLE}$index)${NC} $(echo ${action:0:1} | tr '[:lower:]' '[:upper:]')${action:1} ${CYAN}$module_id${NC} - $status_text"
            index=$((index + 1))
        done
        echo
    fi
    
    echo -e "  ${PURPLE}n)${NC} ${GREEN}Create new module work${NC}"
    echo -e "  ${PURPLE}q)${NC} Quit"
    echo
    print_separator
    echo -n "Select an option: "
}

# Function to get original prompt from task 01 output
get_original_prompt() {
    local work_name="$1"
    local work_dir="$MEMORY_BASE_DIR/$work_name"
    local task01_output="$work_dir/task-01-output.json"
    
    if [ -f "$task01_output" ] && command -v jq >/dev/null 2>&1; then
        local original_prompt=$(jq -r '.initialUserPrompt // empty' "$task01_output" 2>/dev/null)
        if [ -n "$original_prompt" ] && [ "$original_prompt" != "null" ]; then
            echo "$original_prompt"
            return 0
        fi
    fi
    echo "No original prompt found"
    return 1
}

# Function to execute task with human-friendly logging
execute_task() {
    local work_name="$1"
    local task_num="$2"
    local prompt="$3"
    
    local task_name=$(get_task_name "$task_num")
    local start_time=$(date +%s)
    local original_prompt=""
    
    # Get original prompt if work_name is available
    if [ -n "$work_name" ]; then
        original_prompt=$(get_original_prompt "$work_name")
    fi
    
    print_separator
    print_task_start "Task $(printf "%02d" $((10#$task_num))): $task_name"
    print_status "Starting task execution..."
    echo
    
    # Check permission requirements and add streaming flags
    local skip_permissions=$(needs_skip_permissions)
    local claude_flags="--output-format stream-json --verbose"
    if [ "$skip_permissions" = "true" ]; then
        print_warning "Using --dangerously-skip-permissions to avoid hanging"
        claude_flags="--dangerously-skip-permissions --output-format stream-json --verbose"
    fi
    
    print_status "Using Claude flags: $claude_flags"
    
    # Create isolated task execution prompt
    local task_prompt="$prompt"
    local claude_command="claude $claude_flags -p \"$task_prompt\""
    
    print_status "Starting Claude execution with streaming output..."
    echo "========================================="
    
    # Execute claude with isolated context and stream processing
    set +e
    eval "$claude_command" 2>&1 | process_claude_stream "$claude_command" "$original_prompt"
    local exit_code=${PIPESTATUS[0]}
    set -e
    
    echo "----------------------------------------"
    
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    local duration_text=""
    if [ $duration -lt 60 ]; then
        duration_text="${duration}s"
    else
        local minutes=$((duration / 60))
        local seconds=$((duration % 60))
        duration_text="${minutes}m ${seconds}s"
    fi
    
    if [ $exit_code -eq 0 ]; then
        # Clear the streaming display and show persistent summary
        clear
        
        # Show persistent task completion summary
        print_separator
        print_task_complete "Task $(printf "%02d" $((10#$task_num))): $task_name - Completed in $duration_text"
        
        # Show original prompt for context
        if [ -n "$original_prompt" ] && [ "$original_prompt" != "No original prompt found" ]; then
            print_status "Original request: $original_prompt"
        fi
        
        # Verify task output was created and extract summary
        local work_dir="$MEMORY_BASE_DIR/$work_name"
        local task_output="$work_dir/task-$(printf "%02d" $((10#$task_num)))-output.json"
        
        if [ -f "$task_output" ]; then
            print_success "Task output: $(basename "$task_output")"
            
            # Extract and show meaningful task summary
            if command -v jq >/dev/null 2>&1; then
                local status=$(jq -r '.status // .prerequisitesCheck.status // .scaffolding.status // .apiDefinition.status // .moduleImplementation.status // .implementationSummary.status // "completed"' "$task_output" 2>/dev/null)
                print_status "Status: $status"
                
                # Try to extract task-specific summary information
                case "$task_num" in
                    "01")
                        local product_package=$(jq -r '.productPackage // "N/A"' "$task_output" 2>/dev/null)
                        local module_package=$(jq -r '.modulePackage // "N/A"' "$task_output" 2>/dev/null)
                        print_status "Product: $product_package → Module: $module_package"
                        ;;
                    "02")
                        local operations_count=$(jq -r '.operations | keys | length' "$task_output" 2>/dev/null)
                        local auth_method=$(jq -r '.authenticationMethod // "N/A"' "$task_output" 2>/dev/null)
                        print_status "Operations discovered: $operations_count, Auth: $auth_method"
                        ;;
                    "05")
                        local endpoint_count=$(jq -r '.apiDefinition.files.apiSpec.endpointCount // "N/A"' "$task_output" 2>/dev/null)
                        local schema_count=$(jq -r '.apiDefinition.files.apiSpec.schemaCount // "N/A"' "$task_output" 2>/dev/null)
                        print_status "API endpoints: $endpoint_count, Schemas: $schema_count"
                        ;;
                    "06")
                        local files_created=$(jq -r '.moduleImplementation.files | keys | length' "$task_output" 2>/dev/null)
                        print_status "Implementation files created: $files_created"
                        ;;
                    "07")
                        local test_files=$(jq -r '.integrationTests.files | keys | length' "$task_output" 2>/dev/null)
                        print_status "Integration test files: $test_files"
                        ;;
                    *)
                        # For other tasks, try to extract any summary info
                        local summary=$(jq -r '.summary // .description // empty' "$task_output" 2>/dev/null | head -c 100)
                        if [ -n "$summary" ]; then
                            print_status "Summary: $summary"
                        fi
                        ;;
                esac
            fi
        else
            print_warning "Task output file not found - task may have failed"
        fi
        
        print_separator
        echo
        
        # Small pause to let user read the summary
        sleep 3
    elif [ $exit_code -eq 130 ]; then
        # Exit code 130 = Ctrl+C (SIGINT), don't show completion message
        return $exit_code
    else
        print_error "Task $(printf "%02d" $((10#$task_num))) failed with exit code $exit_code after $duration_text"
        return $exit_code
    fi
    
    return 0
}

# Function to run workflow for existing work
run_existing_workflow() {
    local work_entry="$1"
    IFS=':' read -r work_name action module_id <<< "$work_entry"
    
    print_status "Selected work: $(echo ${action:0:1} | tr '[:lower:]' '[:upper:]')${action:1} $module_id"
    
    local work_dir="$MEMORY_BASE_DIR/$work_name"
    
    # Main execution loop
    local max_iterations=15
    local iteration=0
    
    while [ $iteration -lt $max_iterations ]; do
        iteration=$((iteration + 1))
        
        local next_task=$(find_next_task "$work_name")
        
        if [ "$next_task" = "all_complete" ]; then
            local progress=$(get_task_progress "$work_name")
            IFS=':' read -r completed total failed in_progress <<< "$progress"
            
            print_success "All tasks completed! 🎉"
            print_success "Final status: $completed/$total tasks completed"
            if [ "$failed" -gt 0 ]; then
                print_warning "Tasks with failures: $failed"
            fi
            break
        fi
        
        local task_name=$(get_task_name "$next_task")
        print_status "Iteration $iteration: Next task is $(printf "%02d" $((10#$next_task))) - $task_name"
        
        # Execute the task with module identifier
        local simple_prompt="execute task $(printf "%02d" $((10#$next_task))) on work $module_id"
        
        if ! execute_task "$work_name" "$next_task" "$simple_prompt"; then
            print_error "Task execution failed. Stopping workflow."
            return 1
        fi
        
        # Small delay between tasks
        sleep 2
    done
    
    if [ $iteration -ge $max_iterations ]; then
        print_warning "Reached maximum iterations ($max_iterations). Check for infinite loop or increase limit."
        return 1
    fi
    
    print_success "Workflow completed successfully for $work_name"
}

# Function to create new module work
create_new_work() {
    echo
    print_status "Creating new module work"
    echo -n "Enter your module creation request: "
    read -r user_prompt
    
    if [ -z "$user_prompt" ]; then
        print_error "Module request cannot be empty"
        return 1
    fi
    
    print_status "Starting new module creation workflow"
    print_status "Request: \"$user_prompt\""
    echo
    
    # Start with task 01 which will create the memory directory
    local start_time=$(date +%s)
    
    if ! execute_task "" "01" "execute task 01: $user_prompt"; then
        print_error "Failed to start module creation workflow"
        return 1
    fi
    
    # Try to detect the created work directory
    local new_work=""
    sleep 1  # Give filesystem time to update
    
    local existing_works=($(discover_existing_works))
    if [ ${#existing_works[@]} -gt 0 ]; then
        # Find the most recently created directory
        local newest_work=""
        local newest_time=0
        
        for work_entry in "${existing_works[@]}"; do
            IFS=':' read -r work_name action module_id <<< "$work_entry"
            local work_dir="$MEMORY_BASE_DIR/$work_name"
            if [ -d "$work_dir" ]; then
                local dir_time=$(stat -f %m "$work_dir" 2>/dev/null || stat -c %Y "$work_dir" 2>/dev/null || echo 0)
                if [ "$dir_time" -gt "$newest_time" ]; then
                    newest_time="$dir_time"
                    newest_work="$work_entry"
                fi
            fi
        done
        
        if [ -n "$newest_work" ]; then
            print_success "Module work created successfully"
            echo
            print_status "Continuing with remaining tasks..."
            
            # Continue with the rest of the workflow
            run_existing_workflow "$newest_work"
        else
            print_warning "Could not automatically detect created module work"
            print_status "You can run this script again to continue with the workflow"
        fi
    else
        print_warning "No module work directory was created - task 01 may have failed"
    fi
}

# Main execution
main() {
    while true; do
        show_menu
        read -r choice
        
        case "$choice" in
            [qQ])
                print_status "Goodbye!"
                exit 0
                ;;
            [nN])
                create_new_work
                ;;
            [0-9]*)
                local existing_works=($(discover_existing_works))
                local index=$((choice - 1))
                
                if [ $index -ge 0 ] && [ $index -lt ${#existing_works[@]} ]; then
                    run_existing_workflow "${existing_works[$index]}"
                else
                    print_error "Invalid selection"
                    sleep 2
                fi
                ;;
            *)
                print_error "Invalid option"
                sleep 2
                ;;
        esac
    done
}

# Check prerequisites
if ! command -v claude >/dev/null 2>&1; then
    print_error "Claude CLI is required but not found in PATH"
    print_error "Please install and configure Claude CLI first"
    exit 1
fi

if [ ! -d "$TASKS_DIR" ]; then
    print_error "Tasks directory not found: $TASKS_DIR"
    print_error "Please ensure you're running this script from the correct repository"
    exit 1
fi

# Create memory directory if it doesn't exist
mkdir -p "$MEMORY_BASE_DIR"

# Start main execution
main