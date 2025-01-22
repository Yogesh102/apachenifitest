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

# Loop through .metadata.properties.xml.v* files in the source directory
find "$SOURCE_DIR" -type f -name "*.metadata.properties.xml.v*" | while read -r metadata_file; do
    # Extract the 'cm:created' field value
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
            # Extract the base name and version number
            base_name=$(basename "$metadata_file" .metadata.properties.xml.v*)
            version=$(echo "$metadata_file" | grep -oP '\.v\K[0-9]+')

            if [[ -n "$version" ]]; then
                # Find the corresponding actual file in any subdirectory
                actual_file=$(find "$SOURCE_DIR" -type f -name "$base_name.v$version" -print -quit)

                if [[ -n "$actual_file" ]]; then
                    # Move the actual file to the destination directory
                    mv "$actual_file" "$DEST_DIR/"
                    echo "Moved $actual_file to $DEST_DIR/"
                else
                    echo "Corresponding actual file not found for $metadata_file"
                fi
            else
                echo "Version number extraction failed for $metadata_file"
            fi
        else
            echo "File: $metadata_file has a 'cm:created' date earlier than $TARGET_DATE ($CREATED_DATE)"
        fi
    else
        echo "File: $metadata_file does not contain a 'cm:created' field."
    fi
done
