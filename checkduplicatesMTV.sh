#!/bin/bash

# Base directory containing the structured folders
BASE_DIR="/path/to/your/base/directory"

# Initialize counters
total_files=0
duplicate_files=0

echo "Starting preview mode. Base directory: $BASE_DIR"

# List Level 1 directories
echo "Listing Level 1 directories in $BASE_DIR:"
find "$BASE_DIR" -mindepth 1 -maxdepth 1 -type d
echo "------------------------------------------"

# Loop through all Level 1 directories
for level1_dir in "$BASE_DIR"/*; do
    if [ -d "$level1_dir" ]; then
        echo "Processing Level 1 directory: $level1_dir"

        # List Level 2 directories inside the current Level 1 directory
        echo "  Listing Level 2 directories in $level1_dir:"
        find "$level1_dir" -mindepth 1 -maxdepth 1 -type d
        echo "  ------------------------------------------"

        # Loop through all Level 2 directories
        for level2_dir in "$level1_dir"/*; do
            if [ -d "$level2_dir" ]; then
                echo "  Processing Level 2 directory: $level2_dir"

                # Check files in Level 2
                for file in "$level2_dir"/*; do
                    if [ -f "$file" ]; then
                        # Increment total file counter
                        ((total_files++))

                        # Extract the base filename
                        base_filename=$(basename "$file")
                        target_file="$level1_dir/$base_filename"

                        # Check if a duplicate exists
                        if [ -e "$target_file" ]; then
                            echo "    Duplicate found: $base_filename in $level1_dir"
                            ((duplicate_files++))
                        else
                            echo "    File would be moved: $base_filename to $level1_dir"
                        fi
                    fi
                done
            else
                echo "  Skipping non-directory item in Level 2: $level2_dir"
            fi
        done
    else
        echo "Skipping non-directory item in Level 1: $level1_dir"
    fi
done

echo "------------------------------------------"
echo "Preview Summary:"
echo "Total files analyzed: $total_files"
echo "Total duplicate files: $duplicate_files"
echo "No files were moved during this preview."
