#!/bin/bash

# Define the target date
TARGET_DATE="2023-08-01T00:00:00" # Format: YYYY-MM-DDTHH:MM:SS

# Define the directory containing the XML files
SOURCE_DIR="/path/to/xml/files"

# Loop through .metadata.properties.xml files in the directory
find "$SOURCE_DIR" -type f -name "*.metadata.properties.xml" | while read -r file; do
    # Extract the 'created' field value using grep and sed (or xmllint if available)
    CREATED_DATE=$(grep -oP '<created>\K[^<]+' "$file")

    if [[ -n "$CREATED_DATE" ]]; then
        # Compare the dates
        if [[ "$CREATED_DATE" > "$TARGET_DATE" ]]; then
            echo "File: $file has created date greater than $TARGET_DATE ($CREATED_DATE)"
        else
            echo "File: $file has created date not greater than $TARGET_DATE ($CREATED_DATE)"
        fi
    else
        echo "File: $file does not contain a 'created' field."
    fi
done
