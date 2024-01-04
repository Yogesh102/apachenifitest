#!/bin/bash

URL="http://{alfresco_host}/alfresco/service/bulkfsimport/status"
USERNAME=""
PASSWORD=""
BULK_IMPORT_URL="http://{alfresco_host}/alfresco/service/bulkfsimport"
DESTINATION_NODE=/Sites/xyz/documentLibrary
LOG_FILE=/opt/logs/bulk_import.log
BULK_INGEST_COMPLETED_DIR=/opt/BULK_INGEST_COMPLETED
SOURCE_DIR=/opt/BULK_IMPORT_FILES

log_message() {
    local message="$1"
    local timestamp=$(date "+%Y-%m-%d %H:%M:%S")
    echo "[$timestamp] $message" >> "${LOG_FILE}"
}

cd "$SOURCE_DIR"

while true; do
    
    folders=$(ls -t1r "$SOURCE_DIR")

		for file in $folders; 
		do
				
			log_message "Starting bulk ingestion script for $file"

			curl -s -u ${USERNAME}:${PASSWORD} --data "replaceExisting=replaceExisting&batchSize=100&numThreads=1&disableRules=false&targetPath=${DESTINATION_NODE}&sourceDirectory=$SOURCE_DIR/$file" ${BULK_IMPORT_URL}/initiate >/dev/null
			
			while true;
			do
				
				log_message " Checking bulk ingestion script status for $file "
				RESPONSE=$(curl -s -u $USERNAME:$PASSWORD $URL)

				
				if [[ $RESPONSE == *"Idle"* ]]; then
					log_message  "Bulk Ingestion is completed. See the response in the file ${BULK_INGEST_COMPLETED_DIR}/${file}.html"
					#echo "$RESPONSE" >> "${LOG_FILE}"
					echo "$RESPONSE" >> "${BULK_INGEST_COMPLETED_DIR}/${file}".html
					mv $file ${BULK_INGEST_COMPLETED_DIR}
					break
				fi
				
				log_message  "Ingestion in progress.." 
				
				sleep 60
     			done
			log_message "Searching for new bulk files to ingest in alfresco"
	 	   done	
	 sleep 60
     done