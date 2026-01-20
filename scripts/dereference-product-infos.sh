#!/bin/bash
# Script to dynamically dereference $ref in x-product-infos array
set -x
INPUT_FILE="${1:-full.yml}"
OUTPUT_FILE="${2:-full.yml}"

# Get the length of the x-product-infos array
ARRAY_LENGTH=$(yq eval '.info.x-product-infos | length' "$INPUT_FILE")

if [ "$ARRAY_LENGTH" = "null" ] || [ "$ARRAY_LENGTH" -eq 0 ]; then
    echo "No x-product-infos array found or array is empty"
    if [ "$INPUT_FILE" != "$OUTPUT_FILE" ]; then
        cp "$INPUT_FILE" "$OUTPUT_FILE"
    fi
    exit 0
fi

echo "Found x-product-infos array with $ARRAY_LENGTH element(s)"

# Copy input to output initially
if [ "$INPUT_FILE" != "$OUTPUT_FILE" ]; then
    cp "$INPUT_FILE" "$OUTPUT_FILE"
fi

REFS_FOUND=0

# Loop through all elements in the array
for ((i=0; i<ARRAY_LENGTH; i++)); do
    echo "Processing x-product-infos[$i]..."

    # Check if this element contains a $ref
    REF_VALUE=$(yq eval ".info.x-product-infos[$i].\$ref" "$OUTPUT_FILE")

    if [ "$REF_VALUE" != "null" ] && [ -n "$REF_VALUE" ]; then
        echo "Found \$ref in x-product-infos[$i]: $REF_VALUE"
        REFS_FOUND=$((REFS_FOUND + 1))

        # Parse the $ref value to extract file path and JSON pointer
        # Format: './path/to/file.yml#/JsonPointer'
        FILE_PATH=$(echo "$REF_VALUE" | sed 's/#.*//')
        JSON_POINTER=$(echo "$REF_VALUE" | sed 's/.*#//')

        echo "File path: $FILE_PATH"
        echo "JSON pointer: $JSON_POINTER"

        # Convert JSON pointer to yq path (e.g., /Product -> .Product)
        YQ_PATH=$(echo "$JSON_POINTER" | sed 's/^\//\./')

        echo "YQ path: $YQ_PATH"

        # Dereference using yq eval-all
        yq eval-all "select(fileIndex == 0).info.x-product-infos[$i] = select(fileIndex == 1)${YQ_PATH} | select(fileIndex == 0)" \
            "$OUTPUT_FILE" "$FILE_PATH" > "$OUTPUT_FILE.tmp"

        mv "$OUTPUT_FILE.tmp" "$OUTPUT_FILE"
        echo "Successfully dereferenced \$ref in x-product-infos[$i]"
    else
        echo "No \$ref found in x-product-infos[$i], skipping"
    fi
done

if [ $REFS_FOUND -eq 0 ]; then
    echo "No \$ref entries found in any x-product-infos elements"
else
    echo "Successfully processed $REFS_FOUND \$ref entries"
fi
