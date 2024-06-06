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

public class FolderListAndCounts {

	private static String FOLDER_INFO_FILE;
	private static String HOST_URL = "rc-hp21.deltanet.net:5805";
	private static String username;
	private static String password;
	private static String rootFolder;
	private static String environment;
	private static String startDateArg;
	private static String endDateArg;
	private static LocalDate startDateFilter;
	private static LocalDate endDateFilter;
	private static BufferedWriter writer;
	private static int duplicateFileCount = 0;

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

			pcx.FolderCountInitByPath(rootFolder);
			pcx.FolderCountComplete();

			System.out.println("TOTAL FOLDER count for " + rootFolder + " : " + pcx.FolderCountGetAttr("subfolder_cnt"));
			System.out.println("TOTAL DOCUMENT count for " + rootFolder + " : " + pcx.FolderCountGetAttr("document_cnt"));

			try {
				// Create and open mapping file for writing
				writer = new BufferedWriter(new FileWriter(FOLDER_INFO_FILE));
				writer.write("FolderName,Folders,Documents");
				writer.newLine();
				boolean more = true;
				String previousFolder = "";
				List<String> subFolderList = new ArrayList<String>();

				while (more) {
					more = false;
					pcx.FolderListInitByPath(rootFolder);
					pcx.FolderListReqAttr("folder");
					pcx.FolderListSetAttr("array_max", "100");
					pcx.FolderListSetAttr("previous_name", previousFolder);
					pcx.FolderListComplete();

					if (!pcx.Error) {
						int folderListCount = pcx.FolderListGetCount();

						String subFolder = "";
						for (int i = 0; i < folderListCount; i++) {
							more = true;
							subFolder = pcx.FolderListGetAttr("folder", i);
							System.out.println("Subfolder: " + subFolder);
							subFolderList.add(subFolder);
							previousFolder = subFolder;
						}
					}

				}

				for (String folder : subFolderList) {
					pcx.FolderCountInitByPath(rootFolder + "/" + folder);
					pcx.FolderCountComplete();
					System.out.println("Folder: " + folder + " Folders: " + pcx.FolderCountGetAttr("subfolder_cnt") + " Documents: " + pcx.FolderCountGetAttr("document_cnt"));
					writer.write(folder + "," + pcx.FolderCountGetAttr("subfolder_cnt") + "," + pcx.FolderCountGetAttr("document_cnt"));
					writer.newLine();
				}
				writer.close();
			} catch (IOException e) {
				System.out.println(e);
			}
		} else {
			System.out.println("connection error: " + pcx.ErrorDescription);
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
