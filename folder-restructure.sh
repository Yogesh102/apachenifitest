#!/bin/bash

# Define source and destination directories
SOURCE_DIR="/path/to/source_directory"
DEST_DIR="/path/to/destination_directory"

# Define folder names and patterns
declare -A FOLDERS
FOLDERS["On Demand-A"]="IF144R_RXXA"
FOLDERS["On Demand-C"]="IF144R_RXXC"
FOLDERS["Scheduled"]="IF144_"
FOLDERS["Medicaid Reclamation"]="IF144H IF144I"

# Create destination folders if they don't exist
for folder in "${!FOLDERS[@]}"; do
    mkdir -p "$DEST_DIR/$folder"
done

# Process files in the source directory
for file in "$SOURCE_DIR"/*; do
    if [ -f "$file" ]; then
        FILE_NAME=$(basename "$file")
        moved=false
        for folder in "${!FOLDERS[@]}"; do
            for pattern in ${FOLDERS[$folder]}; do
                if [[ "$FILE_NAME" == $pattern* ]]; then
                    # Move the main file
                    mv "$file" "$DEST_DIR/$folder/"
                    echo "Moved $FILE_NAME to $folder"
                    moved=true
                    break
                fi
            done
            if [ "$moved" = true ]; then
                break
            fi
        done
        if [ "$moved" = false ]; then
            echo "File $FILE_NAME does not match any pattern and was not moved."
        fi
    fi
done
