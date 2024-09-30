#!/bin/bash


if [ -z "$1" ]; then
    echo "Usage: $0 <directory_path>"
    exit 1
fi


directory=$1


output_file="file_count_report.txt"

if [ ! -d "$directory" ]; then
    echo "Directory does not exist: $directory"
    exit 1
fi


for dir in "$directory"/*/ ; do
    if [ -d "$dir" ]; then
        
        file_count=$(find "$dir" -type f | wc -l)
        
        
        echo "$dir: $file_count files" >> "$output_file"
    fi
done

echo "File count report generated: $output_file"
