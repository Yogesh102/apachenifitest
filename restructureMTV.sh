#!/bin/bash

# Base directory containing the 50k directories
BASE_DIR="/path/to/your/folder"

# Directory to store grouped folders
OUTPUT_DIR="/path/to/output/folder"

# Create output directory if it doesn't exist
mkdir -p "$OUTPUT_DIR"

# Loop through all directories in the base directory
for dir in "$BASE_DIR"/*; do
    if [ -d "$dir" ]; then
        # Extract the first 8 characters of the directory name
        folder_name=$(basename "$dir")
        prefix=${folder_name:0:8}
        
        # Create the parent directory if it doesn't exist
        target_dir="$OUTPUT_DIR/$prefix"
        mkdir -p "$target_dir"

        # Move the directory into the target folder
        mv "$dir" "$target_dir/"
    fi
done

echo "Folders have been grouped and moved successfully."
