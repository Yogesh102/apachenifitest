#!/bin/bash

# Define the base directory and destination directory
BASE_DIR="/path/to/Createform/EBCA/500k_folders/M"
DEST_DIR="/path/to/Createform/EBCA/restructured_folders"

# Ensure the destination directory exists
mkdir -p "$DEST_DIR"

# Process each subfolder
find "$BASE_DIR" -mindepth 1 -maxdepth 1 -type d | while read -r subfolder; do
    # Locate metadata file (assuming only one per subfolder)
    METADATA_FILE=$(find "$subfolder" -maxdepth 1 -type f -name "*.metadata.properties.xml" | head -n 1)

    # Check if metadata file exists
    if [[ -f "$METADATA_FILE" ]]; then
        # Extract cm:created property in format YYYY-MM-DDTHH:MM:SS
        CREATED_DATE=$(grep -oP '(?<=<property name="cm:created"><value>).*?(?=</value></property>)' "$METADATA_FILE")

        # Validate and parse date format (YYYY-MM-DDTHH:MM:SS)
        if [[ "$CREATED_DATE" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}$ ]]; then
            YEAR=$(echo "$CREATED_DATE" | cut -d'T' -f1 | cut -d'-' -f1)
            MONTH=$(echo "$CREATED_DATE" | cut -d'T' -f1 | cut -d'-' -f2)

            # Create year/month directory
            TARGET_DIR="$DEST_DIR/$YEAR/$MONTH"
            mkdir -p "$TARGET_DIR"

            # Move all files from the subfolder to the target directory
            mv "$subfolder"/* "$TARGET_DIR/"

            # Remove the now-empty subfolder
            rmdir "$subfolder"

            echo "Moved files from $(basename "$subfolder") to $TARGET_DIR/ and removed the folder."
        else
            echo "Skipping $(basename "$subfolder"): Invalid or missing cm:created date in metadata file"
        fi
    else
        echo "Skipping $(basename "$subfolder"): No metadata file found"
    fi
done

echo "Restructuring complete!"
