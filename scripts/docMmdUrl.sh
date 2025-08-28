#!/bin/bash

# Mermaid Live URL encoder + README updater
# Reads external-api.mmd and updates README.md with live link

encode_mermaid() {
    local code="$1"
    
    # Escape JSON control characters
    code="${code//\\/\\\\}"
    code="${code//\"/\\\"}"
    code="${code//$'\n'/\\n}"
    code="${code//$'\r'/\\r}"
    code="${code//$'\t'/\\t}"
    
    # Create JSON state
    local json="{\"code\":\"${code}\",\"mermaid\":{\"theme\":\"default\"}}"
    
    # Compress with gzip and base64 encode, make URL-safe
    local encoded
    encoded=$(echo -n "$json" | gzip -9 | base64 -w 0 | tr '+/' '-_' | tr -d '=')
    
    echo "https://mermaid.live/view#pako:$encoded"
}

update_readme() {
    local mermaid_url="$1"
    local readme_file="README.md"
    local current_hash=$(git rev-parse HEAD 2>/dev/null || echo "unknown")
    
    # Create README if it doesn't exist
    if [ ! -f "$readme_file" ]; then
        echo "Creating $readme_file..."
        echo "# Project" > "$readme_file"
    fi
    
    # Check if External API Schema Diagram link already exists
    if grep -q "\\[External API Schema Diagram\\]" "$readme_file"; then
        echo "Updating existing External API Schema Diagram link in $readme_file..."
        
        # Update the link
        if [ "$(uname)" = "Darwin" ]; then
            sed -i '' "s|\\[External API Schema Diagram\\]([^)]*)|\[External API Schema Diagram\]($mermaid_url)|g" "$readme_file"
        else
            sed -i "s|\\[External API Schema Diagram\\]([^)]*)|\[External API Schema Diagram\]($mermaid_url)|g" "$readme_file"
        fi
        
        # Update or add hash
        if grep -q "<!-- external-api-hash:" "$readme_file"; then
            echo "Updating existing hash to $current_hash"
            awk '{gsub(/<!-- external-api-hash: [^>]* -->/, "<!-- external-api-hash: '$current_hash' -->"); print}' "$readme_file" > "${readme_file}.tmp" && mv "${readme_file}.tmp" "$readme_file"
        else
            echo "Adding hash $current_hash after link"
            awk '/\[External API Schema Diagram\]/ {print; print "<!-- external-api-hash: '$current_hash' -->"; next} {print}' "$readme_file" > "${readme_file}.tmp" && mv "${readme_file}.tmp" "$readme_file"
        fi
    else
        # Add new section
        if grep -q "^## Development" "$readme_file"; then
            echo "Adding External API Schema section before Development section in $readme_file..."
            awk '/^## Development/ {print ""; print "---"; print ""; print "## External API Schema"; print ""; print "[External API Schema Diagram]('$mermaid_url')"; print "<!-- external-api-hash: '$current_hash' -->"; print ""} {print}' "$readme_file" > "${readme_file}.tmp" && mv "${readme_file}.tmp" "$readme_file"
        else
            echo "Adding External API Schema section to end of $readme_file..."
            echo "" >> "$readme_file"
            echo "---" >> "$readme_file"
            echo "" >> "$readme_file"
            echo "## External API Schema" >> "$readme_file"
            echo "" >> "$readme_file"
            echo "[External API Schema Diagram]($mermaid_url)" >> "$readme_file"
            echo "<!-- external-api-hash: $current_hash -->" >> "$readme_file"
        fi
    fi
    
    echo "✅ External API Schema Diagram link updated in $readme_file"
}

# Main execution
mermaid_file="external-api.mmd"

if [ ! -f "$mermaid_file" ]; then
    echo "No $mermaid_file found, skipping..."
    exit 0
fi

# Check if there are changes to external-api.mmd since last update
current_hash=$(git rev-parse HEAD 2>/dev/null || echo "")

if [ -z "$current_hash" ]; then
    echo "Not in a git repository, proceeding with update..."
elif grep -q "<!-- external-api-hash: " README.md 2>/dev/null; then
    # Extract last updated hash from README
    last_hash=$(grep "<!-- external-api-hash: " README.md | sed 's/.*external-api-hash: \([a-f0-9]*\).*/\1/')
    
    if [ -n "$last_hash" ] && git diff --quiet "$last_hash" HEAD -- "$mermaid_file" 2>/dev/null; then
        echo "No changes in $mermaid_file since last update ($last_hash), skipping..."
        exit 0
    else
        echo "Found changes in $mermaid_file since last update ($last_hash)"
    fi
else
    echo "No previous update hash found, proceeding with update..."
fi

echo "Found $mermaid_file, updating README..."
code=$(cat "$mermaid_file")
mermaid_url=$(encode_mermaid "$code")

if [ -z "$mermaid_url" ]; then
    echo "Error: Failed to generate mermaid URL"
    exit 1
fi

update_readme "$mermaid_url"
