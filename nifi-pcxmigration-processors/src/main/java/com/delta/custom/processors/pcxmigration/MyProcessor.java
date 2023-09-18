/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.delta.custom.processors.pcxmigration;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.nifi.annotation.behavior.ReadsAttribute;
import org.apache.nifi.annotation.behavior.ReadsAttributes;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.SeeAlso;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.io.InputStreamCallback;
import org.apache.nifi.processor.io.OutputStreamCallback;
import org.apache.nifi.processor.util.StandardValidators;

import com.lrs.pcx.JavaPCX;

@Tags({ "example" })
@CapabilityDescription("Provide a description")
@SeeAlso({})
@ReadsAttributes({ @ReadsAttribute(attribute = "", description = "") })
@WritesAttributes({ @WritesAttribute(attribute = "", description = "") })
public class MyProcessor extends AbstractProcessor {

	public static final PropertyDescriptor DOWNLOAD_DIR = new PropertyDescriptor.Builder().name("DOWNLOAD_DIR")
			.displayName("Download Directory").description("Document download directory").required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final PropertyDescriptor PCX_URL = new PropertyDescriptor.Builder().name("PCX_URL")
			.displayName("PCX URL").description("PCX URL (Add port eg : rc-hp28.ut.dentegra.lab:5805)").required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final PropertyDescriptor USERNAME = new PropertyDescriptor.Builder().name("PCX_USRENAME")
			.displayName("Username").description("PCX Username").required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final PropertyDescriptor PASSWORD = new PropertyDescriptor.Builder().name("PCX_PASSWORD")
			.displayName("Password").description("PCX Password").required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final Relationship SUCCESS = new Relationship.Builder().name("SUCCESS")
			.description("Files Downloaded Successfully").build();

	private List<PropertyDescriptor> descriptors;

	private Set<Relationship> relationships;

	/*
	 * static { System.loadLibrary("winjnix64");; }
	 */

	@Override
	protected void init(final ProcessorInitializationContext context) {
		descriptors = new ArrayList<>();
		descriptors.add(DOWNLOAD_DIR);
		descriptors.add(PCX_URL);
		descriptors.add(USERNAME);
		descriptors.add(PASSWORD);
		descriptors = Collections.unmodifiableList(descriptors);

		relationships = new HashSet<>();
		relationships.add(SUCCESS);
		relationships = Collections.unmodifiableSet(relationships);

	}

	@Override
	public Set<Relationship> getRelationships() {
		return this.relationships;
	}

	@Override
	public final List<PropertyDescriptor> getSupportedPropertyDescriptors() {
		return descriptors;
	}

	@OnScheduled
	public void onScheduled(final ProcessContext context) {

	}

	@Override
	public void onTrigger(final ProcessContext context, final ProcessSession session) {
		FlowFile flowFile = session.get();
		if (flowFile == null) {
			return;
		}

		session.read(flowFile, new InputStreamCallback() {
			@Override
			public void process(InputStream in) throws IOException {

				String[] HEADERS = { "folderpath" };
				String downloadDir = context.getProperty(DOWNLOAD_DIR).getValue();

				CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(HEADERS).setSkipHeaderRecord(true).build();

				Iterable<CSVRecord> records = csvFormat.parse(new InputStreamReader(in));

				JavaPCX pcx = new JavaPCX();
				pcx.Secure = true;
				pcx.RecvTimeout = 60;
				pcx.ConnTimeout = 2;

				// Get PCX connection values
				System.out.println("Connecting to PCX...");

				String pcx_url = context.getProperty(PCX_URL).getValue();
				String username = context.getProperty(USERNAME).getValue();
				String password = context.getProperty(PASSWORD).getValue();

				// Connect to PCX
				System.out.println("JavaPCX Version: \t" + JavaPCX.Version);
				pcx.Logon(pcx_url, username, password, "", "");

				for (CSVRecord record : records) {
					String path = record.get("folderpath");

					getLogger().info("Processing Record : " + path);

					for (String oneFileName : getDocuments(pcx, path)) {
						List<String> revisionDocumentIdList = getRevisionDocumentIDs(pcx, "/" + path + "/",
								oneFileName);
						int revIndex = revisionDocumentIdList.size();
						for (String oneRevisionDocumentID : revisionDocumentIdList) {
							downloadDocumentByID(pcx, "/" + path + "/", oneFileName, oneRevisionDocumentID, revIndex,
									downloadDir);
							revIndex--;
						}
					}
					getLogger().info("Completed Record : " + path);
				}

			}
		});

		flowFile = session.write(flowFile, new OutputStreamCallback() {
			public void process(OutputStream out) throws IOException {
				out.write("".getBytes());
			}
		});

		session.transfer(flowFile, SUCCESS); // TODO implement
	}

	public static List<String> getDocuments(JavaPCX pcx, String path) {
		List<String> docList = new ArrayList<String>();

		int res = pcx.DocumentListInitByPath(path);
		// pcx.FolderListReqAttr("folder_path"); // optionally set to return limited
		// data
		pcx.FolderListReqAttr("file");
		pcx.DocumentListComplete();
		//pcx.yes ("array_max", "2");
		if (!pcx.Error) {
			for (int i = 0; i < pcx.DocumentListGetCount(); i++) {
				docList.add(pcx.DocumentListGetAttr("file", i));
			}
		} else {
			System.out.println("Error getDocument: " + pcx.ErrorDescription);
		}
		return docList;
	}

	public static List<String> getRevisionDocumentIDs(JavaPCX pcx, String path, String fileName) {
		List<String> docRevisionList = new ArrayList<String>();
		pcx.DocumentRevListInitByPath(path + fileName);
		pcx.DocumentRevListComplete();

		if (!pcx.Error) {
			System.out.println("Num Docs Revisions: " + pcx.DocumentRevListGetCount());
			for (int i = 0; i < pcx.DocumentRevListGetCount(); i++) {
				docRevisionList.add(pcx.DocumentRevListGetAttr("document_id", i));
			}
		} else {
			System.out.println("Error getDocumentRevisions: " + pcx.ErrorDescription);
		}
		return docRevisionList;
	}

	// Created so that downloaded document considers Document ID of desired
	// revision. HY 1/31/2022
	public static void downloadDocumentByID(JavaPCX pcx, String path, String fileName, String revisionDocumentID,
			int revIndex, String downloadDir) {
		String concatFileName, filePrefix, fileExtension, importDate;

		pcx.DocumentGetRecInitByID(revisionDocumentID);
		pcx.DocumentGetRecComplete();
		importDate = pcx.DocumentGetRecGetAttr("import_date"); // Format: 15:03:20 March/08/2022
		filePrefix = pcx.DocumentGetRecGetAttr("file_prefix");
		fileExtension = pcx.DocumentGetRecGetAttr("file_extension");

		// Name file uniquely so that it ingests into Alfresco.

		// Format import date for YYYY-MM-DD using Java 8, for mapping file
		DateTimeFormatter f = DateTimeFormatter.ofPattern("kk:mm:ss MMMM/dd/yyyy");
		// LocalDate formattedImportDate = LocalDate.parse(importDate,f);
		LocalDateTime formattedImportDateTime = LocalDateTime.parse(importDate, f);

		// Format import date for YYYYMMDDHHMMSS, for individual filename suffixes
		DateTimeFormatter f2 = DateTimeFormatter.ofPattern("yyyyMMddkkmmss");
		String filenameTimestamp = f2.format(formattedImportDateTime);

		concatFileName = filePrefix + "-" + filenameTimestamp + "." + fileExtension;

		pcx.ReadFileInitByID(revisionDocumentID, downloadDir + concatFileName);
		pcx.ReadFileComplete();

		if (!pcx.Error) {
			System.out.println("Read file complete");
			generateMetadataXMLFile(concatFileName, path, formattedImportDateTime, downloadDir);

		} else {
			System.out.println("Error Download: " + pcx.ErrorDescription);
		}
	}

	public static void generateMetadataXMLFile(String fileName, String folderPath, LocalDateTime importDate,
			String downloadDir) {

		try (BufferedWriter xmlWriter = new BufferedWriter(
				new FileWriter(downloadDir + fileName + ".metadata.properties.xml"))) {
			xmlWriter.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
			xmlWriter.write("<!DOCTYPE properties SYSTEM \"http://java.sun.com/dtd/properties.dtd\">\n");
			xmlWriter.write("<properties>\n");
			xmlWriter.write("  <entry key=\"cm:name\">" + fileName + "</entry>\n");
			xmlWriter.write("  <entry key=\"cm:description\">" + folderPath + "</entry>\n");
			xmlWriter.write("  <entry key=\"cm:created\">" + importDate.toString() + "</entry>\n");
			xmlWriter.write("</properties>\n");

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
