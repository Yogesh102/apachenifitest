package com.delta.custom.processors.pcxmigration;

import com.lrs.pcx.JavaPCX;

import java.util.ArrayList;
import java.util.List;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class DuplicateFinder {

    private static String FOLDER_INFO_FILE;
    private static String HOST_URL = "rc-hp21.deltanet.net:5805";
    private static String username;
    private static String password;
    private static String rootFolder;
    private static BufferedWriter writer;

    public static void main(String args[]) {
        if (args.length != 1) {
            System.out.println("Specify folder to process as command line argument");
            System.exit(0);
        }

        rootFolder = args[0];
        FOLDER_INFO_FILE = "/delta/dars/" + "PCX-FOLDER-INFO.csv";

        JavaPCX pcx = new JavaPCX();
        pcx.Secure = true;
        pcx.RecvTimeout = 180;
        pcx.ConnTimeout = 2;

        System.out.println("Connecting to PCX...");

        username = "admin";
        password = "monkey";

        // Connect to PCX
        System.out.println("Username: \t\t" + username);
        System.out.println("JavaPCX Version: \t" + JavaPCX.Version);
        pcx.Logon(HOST_URL, username, password, "", "");

        if (!pcx.Error) {
            System.out.println("Connected");
            System.out.println("************************************\n");

            try {
                // Create and open mapping file for writing
                writer = new BufferedWriter(new FileWriter(FOLDER_INFO_FILE));
                writer.write("FolderName,DocumentName,TotalRevisions,DuplicateRevisions");
                writer.newLine();
                boolean moreFolders = true;
                String previousFolder = "";
                List<String> subFolderList = new ArrayList<>();

                while (moreFolders) {
                    moreFolders = false;
                    pcx.FolderListInitByPath(rootFolder);
                    pcx.FolderListReqAttr("folder");
                    pcx.FolderListSetAttr("array_max", "100");
                    pcx.FolderListSetAttr("previous_name", previousFolder);
                    pcx.FolderListComplete();

                    if (!pcx.Error) {
                        int folderListCount = pcx.FolderListGetCount();

                        String subFolder = "";
                        for (int i = 0; i < folderListCount; i++) {
                            moreFolders = true;
                            subFolder = pcx.FolderListGetAttr("folder", i);
                            System.out.println("Subfolder: " + subFolder);
                            subFolderList.add(subFolder);
                            previousFolder = subFolder;
                        }
                    }
                }

                for (String folder : subFolderList) {
                    boolean moreDocuments = true;
                    String previousDocument = "";

                    while (moreDocuments) {
                        moreDocuments = false;
                        pcx.DocumentListInitByPath(rootFolder + "/" + folder);
                        pcx.DocumentListReqAttr("file");
                        pcx.DocumentListSetAttr("array_max", "100");
                        pcx.DocumentListSetAttr("previous_name", previousDocument);
                        pcx.DocumentListComplete();
                        if (!pcx.Error) {
                            int documentListCount = pcx.DocumentListGetCount();
                            for (int i = 0; i < documentListCount; i++) {
                                moreDocuments = true;
                                String documentName = pcx.DocumentListGetAttr("file", i);
                                previousDocument = documentName;
                                
                                boolean moreRevisions = true;
                                String previousRevision = "";
                                int revisionListCount = 0;
                                int duplicateCount = 0;
                                String previousDate = "";
                                String previousSize = "";

                                while (moreRevisions) {
                                    moreRevisions = false;
                                    pcx.DocumentRevListInitByPath(rootFolder + "/" + folder + "/" + documentName);
                                    pcx.DocumentRevListSetAttr("array_max", "100");
                                    pcx.DocumentRevListSetAttr("previous_name", previousRevision);
                                    pcx.DocumentRevListComplete();
                                    if (!pcx.Error) {
                                        int currentRevisionCount = pcx.DocumentRevListGetCount();
                                        for (int j = 0; j < currentRevisionCount; j++) {
                                            moreRevisions = true;
                                            revisionListCount++;
                                            String importDate = pcx.DocumentRevListGetAttr("import_date", j);
                                            String size = pcx.DocumentRevListGetAttr("size", j);
                                            if (importDate.equals(previousDate) && size.equals(previousSize)) {
                                                duplicateCount++;
                                            }
                                            previousDate = importDate;
                                            previousSize = size;
                                            previousRevision = pcx.DocumentRevListGetAttr("rev_number", j);
                                        }
                                    }
                                }

                                writer.write(folder + "," + documentName + "," + revisionListCount + "," + duplicateCount);
                                writer.newLine();
                            }
                        }
                    }
                }
                writer.close();
            } catch (IOException e) {
                System.out.println(e);
            }
        } else {
            System.out.println("Connection error: " + pcx.ErrorDescription);
            return;
        }

        pcx.Logoff();
        if (!pcx.Error) {
            System.out.println("Disconnected");
        } else {
            System.out.println("Disconnection failed: " + pcx.ErrorDescription);
        }
    }
}
