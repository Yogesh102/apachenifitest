package org.delta.pcx.main;

import com.lrs.pcx.JavaPCX;
//import org.apache.commons.lang3.time.StopWatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class ExportPCXDocuments {
    private static String BASE_DIRECTORY;
    private static String PROGRAM_DIRECTORY;
    private static String DOCUMENTS_DESTINATION;
    private static String MAPPING_FILE_DESTINATION;
    private static String MAPPING_FILE;
    private static String HOST_URL;
    private static String username;
    private static String password;
    private static String rootFolder;
    private static String environment;
    private static String startDateArg;
    private static String endDateArg;
    private static LocalDate startDateFilter;
    private static LocalDate endDateFilter;
    private static BufferedWriter writer;
    private static int duplicateFileCount=0;

    public static void main(String args[]){
    //public static void main(String args[]) throws InterruptedException {
      // Get root folder to process from command line
      //if(args.length != 2){
      if(args.length < 2 || args.length > 4){
        System.out.println("Specify environment and folder to process as command line arguments");
        System.exit(0);
      }
      environment = args[0];
      rootFolder = args[1];
      if(args.length == 3){
        endDateArg = args[2];
        endDateFilter = convertDate(endDateArg);
      }
      if(args.length == 4){
        startDateArg = args[2];
        endDateArg = args[3];
        startDateFilter = convertDate(startDateArg);
        endDateFilter = convertDate(endDateArg);
      }

      // Configure file system locations based on environment
      if(environment.equals("dev")){
        BASE_DIRECTORY= "/opt/delta/dars/";
      }
      else{
        BASE_DIRECTORY= "/delta/dars/";
      }
      PROGRAM_DIRECTORY = BASE_DIRECTORY + "pcx-export-app/";
      DOCUMENTS_DESTINATION = BASE_DIRECTORY + "historical/dropzone/";
      MAPPING_FILE_DESTINATION = BASE_DIRECTORY + "historical/config/";
      MAPPING_FILE = MAPPING_FILE_DESTINATION + "PCX-EXPORT-METADATA.csv";

      // Configure PCX Java client
      //System.loadLibrary("nlrxapi1x64");
      //System.loadLibrary("jpjnix64");
      //System.loadLibrary("winjnix64");
      JavaPCX pcx = new JavaPCX();
      pcx.Secure = true;
      pcx.RecvTimeout = 60;
      pcx.ConnTimeout = 2;

      // Get values from property file, including PCX connection information
      System.out.println("Connecting to PCX...");
      try (InputStream input = new FileInputStream(PROGRAM_DIRECTORY + "config/" + "pcx-properties.properties")) {
        Properties prop = new Properties();
        prop.load(input);
        username = prop.getProperty("INF.u");
        password = prop.getProperty("INF.p");

        if(environment.equals("dev") || environment.equals("non-prod")){
          HOST_URL = prop.getProperty("PCX_URL_NON_PROD"); 
        }
        else if(environment.equals("prod")){
          HOST_URL = prop.getProperty("PCX_URL_PROD");
        }
        else{
          System.out.println("Invalid environment entered");
        }
      }
      catch (IOException e){ System.out.println(e); }
      finally{}

      // Connect to PCX
      System.out.println("Username: \t\t" + username);
      System.out.println("JavaPCX Version: \t" + JavaPCX.Version);
      pcx.Logon(HOST_URL, username, password, "", "");

      if(!pcx.Error){
          System.out.println("Connected");
          System.out.println("************************************\n");

          // Capture/display sub-folder and document count for the folder to be processed
          pcx.FolderCountInitByPath(rootFolder);
          pcx.FolderCountComplete();

          System.out.println("TOTAL FOLDER count for " + rootFolder + " : " + pcx.FolderCountGetAttr("subfolder_cnt"));
          System.out.println("TOTAL DOCUMENT count for " + rootFolder + " : " + pcx.FolderCountGetAttr("document_cnt"));

          try{
            // Create and open mapping file for writing
            writer = new BufferedWriter(new FileWriter(MAPPING_FILE));
            writer.write("Document Name,Folders,Import Date,Author");
            writer.newLine();

            List<String> folderList = getFoldersRecursive(pcx, rootFolder, "");

            // CLose mapping file for writing
            writer.close();
          }
          catch(IOException e){ System.out.println(e); }
        } else {
            System.out.println("connection error: " + pcx.ErrorDescription);
            return;
        }

        pcx.Logoff();
        if (!pcx.Error){
          System.out.println("Disconnected");
          printReportSummary();
        }
        else{
          System.out.println("Disconnection failed: " + pcx.ErrorDescription);
        }
    }

    public static List<String> getFoldersRecursive(JavaPCX pcx, String path, String indent) {
        System.out.println(indent + "Folder Path: \t" + path);
        List<String> subFolderList = new ArrayList<String>();

        boolean more = true;
        String previousFolder = "";
        while(more){
          more = false;
          // Make folder list request
          pcx.FolderListInitByPath(path);
          pcx.FolderListReqAttr("folder");
          // override default limit of 500 folders returned. more than 500 not possible.
          pcx.FolderListSetAttr("array_max", "100");
          pcx.FolderListSetAttr("previous_name", previousFolder);
          pcx.FolderListComplete();

          // Process folder list response
          if (!pcx.Error){
            int folderListCount = pcx.FolderListGetCount();
            System.out.println(indent + "Folder List Count: \t" + folderListCount);

            for (int i = 0; i < folderListCount; i++){
              more = true;
              String subFolder = pcx.FolderListGetAttr("folder", i);
              String subFolderWithSlash = subFolder;
              // Add trailing slash if missing
              if(subFolderWithSlash.charAt(subFolderWithSlash.length() - 1) != '/'){
                subFolderWithSlash += "/";
              }
              String subPath = path + subFolderWithSlash;
              subFolderList.add(subPath);
              System.out.println(indent + "sub-folder: \t" + subPath + "\titeration: " + i);
              previousFolder = subFolder;
            }
          }
          else{
            System.out.println("Error: " + pcx.ErrorDescription);
          }
        }

        // Process folder list response
        if (!pcx.Error){

          // Iterate through subfolders, recursing down when applicable
          indent += "  ";
          for (String subFolder : subFolderList){
            System.out.println(indent + "recurse through: \t" + subFolder);
            getFoldersRecursive(pcx,subFolder, indent);
            System.out.println(indent + "recursion complete for: \t" + subFolder);
          }

          // No more folders to recurse through, get document list for current folder
          for(String oneFileName : getDocuments(pcx, path)){
            System.out.println(indent + "File: " + oneFileName);
            // Get file revision list for each file

            List<String> revisionDocumentIdList = getRevisionDocumentIDs(pcx, "/" + path + "/", oneFileName);
            System.out.println(indent + "Number of revisions: " + revisionDocumentIdList.size());
            int revIndex=revisionDocumentIdList.size();
            // for (String oneRevisionDocumentID : getRevisionDocumentIDs(pcx, "/" + path + "/", oneFileName)){
            for (String oneRevisionDocumentID : revisionDocumentIdList){
              System.out.println(indent + "Revision Document ID: " + oneRevisionDocumentID);
              downloadDocumentByID(pcx, "/" + path + "/", oneFileName, oneRevisionDocumentID, revIndex);
              revIndex--;
            }
          }
        }
        else{
            System.out.println("Error getSubFolder: " + pcx.ErrorDescription);
        }
        return subFolderList;
    }

    public static List<String> getDocuments(JavaPCX pcx, String path) {
        System.out.println("Document Path: " + path);
        List<String> docList = new ArrayList<String>();

        int res = pcx.DocumentListInitByPath(path);
        System.out.println("path result (zero for success): " + res);
        //pcx.FolderListReqAttr("folder_path"); // optionally set to return limited data
        pcx.FolderListReqAttr("file");
        pcx.DocumentListComplete();
        pcx.DocumentListSetAttr("array_max", "2");
        if (!pcx.Error){
            System.out.println("Num Docs: " + pcx.DocumentListGetCount());
            for (int i = 0; i < pcx.DocumentListGetCount(); i++)
            {
                docList.add(pcx.DocumentListGetAttr("file", i));
                System.out.println("-- Document: " + pcx.DocumentListGetAttr("file", i));
            }
        }
        else{
            System.out.println("Error getDocument: " + pcx.ErrorDescription);
        }
        return docList;
    }

    public static List<String> getRevisionDocumentIDs(JavaPCX pcx, String path, String fileName) {
        System.out.println("Document Revision Path: " + path + fileName);
        List<String> docRevisionList = new ArrayList<String>();

        boolean more = true;
        String previousRevisionId = "";
        String previousImportDate = "";
        while(more){
          more = false;
          //System.out.println("\tpreviousRevisionId: \t" + previousRevisionId);
          // Make document revision list request. Will fetch 500 revisions per loop
          pcx.DocumentRevListInitByPath(path + fileName);
          pcx.DocumentRevListSetAttr("array_max","500");

          // Both previous_revision_id and previous_import_date need to be set for successive iterations
          if(!previousRevisionId.equals("")){
            pcx.DocumentRevListSetAttr("previous_revision_id", previousRevisionId);
            pcx.DocumentRevListSetAttr("previous_import_date", previousImportDate);
          }
          pcx.DocumentRevListComplete();

          // Process document revision list response
          if (!pcx.Error){
            int docRevListCount = pcx.DocumentRevListGetCount();
            System.out.println("Num Document Revisions: " + docRevListCount);

            for (int i = 0; i < docRevListCount; i++){
              more = true;
              String documentId = pcx.DocumentRevListGetAttr("document_id", i);
              String revisionId = pcx.DocumentRevListGetAttr("revision_id", i);
              String documentImportDate = pcx.DocumentRevListGetAttr("import_date", i);

              previousRevisionId = revisionId;
              previousImportDate = documentImportDate;

              // Format PCX doc import date for comparison to date range filters
              DateTimeFormatter f = DateTimeFormatter.ofPattern("HH:mm:ss MMMM/dd/yyyy");
              LocalDate formattedDocumentImportDate = LocalDate.parse(documentImportDate,f);
              // Filter document export on end-date only
              if(endDateFilter != null && startDateFilter == null && formattedDocumentImportDate.isAfter(endDateFilter)){
                continue;
              }
              // Filter document export on start-date and end-date
              if(endDateFilter != null && startDateFilter != null){
                if(formattedDocumentImportDate.isBefore(startDateFilter) || formattedDocumentImportDate.isAfter(endDateFilter)){
                  continue;
                }
              }

              docRevisionList.add(documentId);
              System.out.println("\tdocumentId: \t" + documentId + "\titeration: " + i);
            }
          }
          else{
            System.out.println("Error: " + pcx.ErrorDescription);
          }
        }

        return docRevisionList;
    }

    // Created so that downloaded document considers Document ID of desired revision. HY 1/31/2022
    public static void downloadDocumentByID(JavaPCX pcx, String path, String fileName, String revisionDocumentID, int revIndex) {
      String concatFileName, filePrefix, fileExtension, folderPath, importDate;
      String author="";
      System.out.println("Path Download: " + path + fileName + ".  Revision Document ID: " + revisionDocumentID);

      pcx.DocumentGetRecInitByID(revisionDocumentID);
      pcx.DocumentGetRecComplete();
      importDate = pcx.DocumentGetRecGetAttr("import_date"); // Format: 15:03:20 March/08/2022
      folderPath = pcx.DocumentGetRecGetAttr("folder_path");
      filePrefix = pcx.DocumentGetRecGetAttr("file_prefix");
      fileExtension = pcx.DocumentGetRecGetAttr("file_extension");

      // Name file uniquely so that it ingests into Alfresco.

      // Format import date for YYYY-MM-DD using Java 8, for mapping file
      DateTimeFormatter f = DateTimeFormatter.ofPattern("kk:mm:ss MMMM/dd/yyyy");
      LocalDateTime formattedImportDateTime = LocalDateTime.parse(importDate,f);

      // Format import date for YYYYMMDDHHMMSS, for individual filename suffixes
      DateTimeFormatter f2 = DateTimeFormatter.ofPattern("yyyyMMddkkmmss");
      String filenameTimestamp = f2.format(formattedImportDateTime);

      // Use creation timestamp from PCX to ensure uniqueness.
      concatFileName = filePrefix + "-" + filenameTimestamp + "." + fileExtension;

      // Check that file with same filename has not already been processed, as duplicates dissallowed.
      // (added to mapping file or written to file created in output location)
      boolean duplicateFileExists = new File(DOCUMENTS_DESTINATION, concatFileName).exists();
      if(duplicateFileExists){
        duplicateFileCount++;
        System.out.println("-- -- -- -- DUPLICATE FILE : " + concatFileName + " -- -- --");
        pcx.ReadFileComplete();
        return;
      }

      System.out.println("-- -- Import Date for file: " + importDate);
      System.out.println("-- -- Formatted Import DateTime for file: " + formattedImportDateTime);
      System.out.println("-- -- Filename Timestamp for file: " + filenameTimestamp);
      System.out.println("-- -- Rev index for file: " + revIndex);
      System.out.println("-- -- File prefix: " + filePrefix);
      System.out.println("-- -- File extenstion: " + fileExtension);
      System.out.println("-- -- Folder path: " + folderPath);

      // Write metadata for document to mapping file
      try{
        writer.write(concatFileName + "," + folderPath + "," + formattedImportDateTime + "," + author);
        writer.newLine();
      }
      catch(IOException e){ System.out.println(e); }

      // Write document file to destination location
      pcx.ReadFileInitByID(revisionDocumentID, DOCUMENTS_DESTINATION + concatFileName);
      pcx.ReadFileComplete();

      if (!pcx.Error){
          System.out.println("Read file complete");
      }
      else{
          System.out.println("Error Download: " + pcx.ErrorDescription);
      }
  }

  // Report on various counters from the export
  public static void printReportSummary(){
      System.out.println("TOTAL duplicate files in PCX not exported: " + duplicateFileCount);
  }

  public static LocalDate convertDate(String dateArg){
    DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    LocalDate date = null;
    try{
      date = LocalDate.parse(dateArg,f);
    }
    catch(DateTimeParseException e){
      System.out.println("Please enter date in YYYY-MM-DD format");
      System.exit(0);
    }
    return date;
  }

}
