#!/bin/bash

# Define the target date
TARGET_DATE="2024-08-01T00:00:00" # ISO 8601 format

# Define the directory containing the XML files
SOURCE_DIR="/path/to/xml/files"

# Convert TARGET_DATE to Unix timestamp
TARGET_TIMESTAMP=$(date -d "$TARGET_DATE" +"%s")

# Loop through .metadata.properties.xml files in the directory
find "$SOURCE_DIR" -type f -name "*.metadata.properties.xml" | while read -r file; do
    # Extract the 'created' field value
    CREATED_DATE=$(grep -oP '<entry key="cm:created">\K[^<]+' "$file")

    if [[ -n "$CREATED_DATE" ]]; then
        # Convert CREATED_DATE to Unix timestamp
        CREATED_TIMESTAMP=$(date -d "$CREATED_DATE" +"%s" 2>/dev/null)

        if [[ $? -ne 0 ]]; then
            echo "Error: Invalid date format in file $file"
            continue
        fi

        # Compare the timestamps
        if [[ "$CREATED_TIMESTAMP" -gt "$TARGET_TIMESTAMP" ]]; then
            echo "File: $file has a 'created' date greater than $TARGET_DATE ($CREATED_DATE)"
        else
            echo "File: $file has a 'created' date not greater than $TARGET_DATE ($CREATED_DATE)"
        fi
    else
        echo "File: $file does not contain a 'cm:created' field."
    fi
done
