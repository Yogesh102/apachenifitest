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


#!/bin/bash

# Base directory containing the structured folders
BASE_DIR="/path/to/your/base/directory"

# Loop through all level 1 directories
for level1_dir in "$BASE_DIR"/*; do
    if [ -d "$level1_dir" ]; then
        # Loop through all level 2 directories
        for level2_dir in "$level1_dir"/*; do
            if [ -d "$level2_dir" ]; then
                # Move all files from level 3 to level 2
                find "$level2_dir" -mindepth 2 -type f -exec mv {} "$level2_dir/" \;

                # Remove all empty level 3 directories
                find "$level2_dir" -mindepth 1 -type d -empty -delete

                # If the level 2 directory is now empty, remove it
                if [ -z "$(ls -A "$level2_dir")" ]; then
                    rmdir "$level2_dir"
                fi
            fi
        done
    fi
done

echo "Files have been moved, and empty directories removed."

#!/bin/bash

# Base directory containing the structured folders
BASE_DIR="/path/to/your/base/directory"

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

                # Move all files from Level 2 to Level 1
                echo "    Moving files from Level 2 ($level2_dir) to Level 1 ($level1_dir)..."
                find "$level2_dir" -type f -exec mv -v {} "$level1_dir/" \;

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

echo "Script completed."
