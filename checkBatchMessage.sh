#!/bin/bash

# Define the target date
TARGET_DATE="2024-08-01T00:00:00" # ISO 8601 format

# Define the source and destination directories
SOURCE_DIR="/path/to/source_directory"
DEST_DIR="/path/to/destination_directory"

# Ensure the destination directory exists
mkdir -p "$DEST_DIR"

# Convert TARGET_DATE to Unix timestamp
TARGET_TIMESTAMP=$(date -d "$TARGET_DATE" +"%s")
if [[ $? -ne 0 ]]; then
    echo "Error: Invalid TARGET_DATE format."
    exit 1
fi

# Loop through .metadata.properties.xml files in the source directory
find "$SOURCE_DIR" -maxdepth 1 -type f -name "*.metadata.properties.xml" | while read -r metadata_file; do
    # Extract the 'created' field value
    CREATED_DATE=$(grep -oP '<entry key="cm:created">\K[^<]+' "$metadata_file")

    if [[ -n "$CREATED_DATE" ]]; then
        # Convert CREATED_DATE to Unix timestamp
        CREATED_TIMESTAMP=$(date -d "$CREATED_DATE" +"%s" 2>/dev/null)
        if [[ $? -ne 0 ]]; then
            echo "Error: Invalid date format in file $metadata_file"
            continue
        fi

        # Compare the timestamps
        if [[ "$CREATED_TIMESTAMP" -ge "$TARGET_TIMESTAMP" ]]; then
            # Extract the base name without the .metadata.properties.xml extension
            base_name=$(basename "$metadata_file" .metadata.properties.xml)

            # Move the .metadata.properties.xml file
            mv "$metadata_file" "$DEST_DIR/"

            # Find and move files with the same base name but different extensions
            find "$SOURCE_DIR" -maxdepth 1 -type f -name "$base_name.*" ! -name "*.metadata.properties.xml*" -exec mv {} "$DEST_DIR/" \;

            echo "Moved $metadata_file and its associated files to $DEST_DIR/"
        else
            echo "File: $metadata_file has a 'created' date earlier than $TARGET_DATE ($CREATED_DATE)"
        fi
    else
        echo "File: $metadata_file does not contain a 'cm:created' field."
    fi
done
