#!/bin/bash

# Base directory containing the structured folders
BASE_DIR="/path/to/your/base/directory"

# Initialize counters
total_files=0
duplicate_files=0
moved_files=0

echo "Starting the script. Base directory: $BASE_DIR"

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

                # Move files from Level 2 to Level 1
                for file in "$level2_dir"/*; do
                    if [ -f "$file" ]; then
                        # Increment total file counter
                        ((total_files++))

                        # Extract the base filename
                        base_filename=$(basename "$file")
                        target_file="$level1_dir/$base_filename"

                        # Check for duplicates
                        if [ -e "$target_file" ]; then
                            # Append timestamp to the filename to avoid overwriting
                            timestamp=$(date +%Y%m%d%H%M%S)
                            new_filename="${base_filename%.*}_$timestamp.${base_filename##*.}"
                            target_file="$level1_dir/$new_filename"
                            echo "    Duplicate found: $base_filename. Renaming to $new_filename."
                            ((duplicate_files++))
                        fi

                        # Move the file
                        mv -v "$file" "$target_file"
                        ((moved_files++))
                    fi
                done

                # Check if Level 2 directory is empty and delete it
                if [ -z "$(ls -A "$level2_dir")" ]; then
                    echo "    Level 2 directory $level2_dir is empty. Deleting..."
                    rmdir "$level2_dir"
                else
                    echo "    Level 2 directory $level2_dir is not empty. Skipping deletion."
                fi
            else
                echo "  Skipping non-directory item in Level 2: $level2_dir"
            fi
        done
    else
        echo "Skipping non-directory item in Level 1: $level1_dir"
    fi
done

echo "------------------------------------------"
echo "Summary:"
echo "Total files processed: $total_files"
echo "Total files moved: $moved_files"
echo "Total duplicate files handled: $duplicate_files"
echo "Script completed."
