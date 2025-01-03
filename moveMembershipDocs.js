// Define the query and destination folder
var query = "PATH:\"/app:company_home/cm:SourceFolder//*\""; // Replace with your query
var destinationFolderPath = "/Company Home/DestinationFolder"; // Replace with your destination folder path
var batchSize = 500; // Number of nodes to process in each batch

// Get the destination folder
var destinationFolder = search.xpathSearch(destinationFolderPath)[0];
if (!destinationFolder) {
    logger.log("Destination folder not found: " + destinationFolderPath);
    throw "Destination folder not found.";
}

// Process nodes in batches
var startIndex = 0;
var totalMoved = 0;

do {
    // Fetch nodes in the current batch
    var pagedQuery = query + " AND +@cm\\:created:[" + startIndex + " TO " + (startIndex + batchSize - 1) + "]";
    var nodes = search.luceneSearch({
        query: query,
        page: { maxItems: batchSize, skipCount: startIndex }
    });

    if (nodes.length === 0) {
        logger.log("No more nodes to process.");
        break;
    }

    logger.log("Processing batch of " + nodes.length + " nodes, starting at index " + startIndex);

    // Move nodes
    for each (var node in nodes) {
        if (node.isDocument || node.isContainer) {
            var nodeName = node.name;
            try {
                node.move(destinationFolder);
                logger.log("Moved node: " + nodeName + " to " + destinationFolder.displayPath);
                totalMoved++;
            } catch (e) {
                logger.log("Error moving node: " + nodeName + ". Error: " + e);
            }
        } else {
            logger.log("Skipping non-document/non-container node: " + node.name);
        }
    }

    // Increment the start index for the next batch
    startIndex += nodes.length;

} while (nodes.length === batchSize);

logger.log("Finished processing. Total nodes moved: " + totalMoved);
