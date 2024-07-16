package com.delta.custom.processors.pcxmigration;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Date;

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
import org.apache.nifi.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.CallableStatement;
import java.sql.Types;

import com.lrs.pcx.JavaPCX;

@Tags({ "example" })
@CapabilityDescription("Provide a description")
@SeeAlso({})
@ReadsAttributes({ @ReadsAttribute(attribute = "", description = "") })
@WritesAttributes({ @WritesAttribute(attribute = "", description = "") })
public class MyProcessor extends AbstractProcessor {

	private static final Logger logger = LoggerFactory.getLogger(MyProcessor.class);

	public static final PropertyDescriptor DOWNLOAD_DIR = new PropertyDescriptor.Builder().name("DOWNLOAD_DIR")
			.displayName("Download Directory").description("Document download directory").required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final PropertyDescriptor PCX_URL = new PropertyDescriptor.Builder().name("PCX_URL")
			.displayName("PCX URL").description("PCX URL (Add port eg : rc-hp28.ut.dentegra.lab:5805)").required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final PropertyDescriptor USERNAME = new PropertyDescriptor.Builder().name("PCX_USRENAME")
			.displayName("Username").description("PCX Username").required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final PropertyDescriptor START_DATE = new PropertyDescriptor.Builder().name("START_DATE")
			.displayName("STARTDATE").description("Start date").required(false)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final PropertyDescriptor END_DATE = new PropertyDescriptor.Builder().name("END_DATE")
			.displayName("ENDDATE").description("End date").required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final PropertyDescriptor PASSWORD = new PropertyDescriptor.Builder().name("PCX_PASSWORD")
			.displayName("Password").description("PCX Password").required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final PropertyDescriptor DATABASE_URL = new PropertyDescriptor.Builder().name("DATABASE_URL")
			.displayName("Database URL").description("JDBC Database URL").required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final PropertyDescriptor DATABASE_USER = new PropertyDescriptor.Builder().name("DATABASE_USER")
			.displayName("Database User").description("Database Username").required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final PropertyDescriptor DATABASE_PASSWORD = new PropertyDescriptor.Builder()
			.name("DATABASE_PASSWORD").displayName("Database Password").description("Database Password").required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final Relationship SUCCESS = new Relationship.Builder().name("SUCCESS")
			.description("Files Downloaded Successfully").build();

	private List<PropertyDescriptor> descriptors;

	private Set<Relationship> relationships;

	private Map<String, Integer> versionMap;

	@Override
	protected void init(final ProcessorInitializationContext context) {
		descriptors = new ArrayList<>();
		descriptors.add(DOWNLOAD_DIR);
		descriptors.add(PCX_URL);
		descriptors.add(USERNAME);
		descriptors.add(PASSWORD);
		descriptors.add(START_DATE);
		descriptors.add(END_DATE);
		descriptors.add(DATABASE_URL);
		descriptors.add(DATABASE_USER);
		descriptors.add(DATABASE_PASSWORD);
		descriptors = Collections.unmodifiableList(descriptors);

		relationships = new HashSet<>();
		relationships.add(SUCCESS);
		relationships = Collections.unmodifiableSet(relationships);

		versionMap = new HashMap<>();
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
		versionMap.clear(); // Clear the version map at the start of each schedule
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
				Connection conn = null;
				PreparedStatement pstmt = null;
				int migrationTrackerId = 0;
				String csvFileName = flowFile.getAttribute("filename");
				LocalDateTime startTime = LocalDateTime.now();
				try {
					String[] HEADERS = { "folderpath" };
					String downloadDir = context.getProperty(DOWNLOAD_DIR).getValue();

					CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(HEADERS).setSkipHeaderRecord(true)
							.build();
					Iterable<CSVRecord> records = csvFormat.parse(new InputStreamReader(in));

					JavaPCX pcx = new JavaPCX();
					pcx.Secure = true;
					pcx.RecvTimeout = 3600;
					pcx.ConnTimeout = 20;

					logger.info("Connecting to PCX...");
					String pcx_url = context.getProperty(PCX_URL).getValue();
					String username = context.getProperty(USERNAME).getValue();
					String password = context.getProperty(PASSWORD).getValue();
					String start_date = context.getProperty(START_DATE).getValue();
					String end_date = context.getProperty(END_DATE).getValue();
					LocalDate startDateFilter = StringUtils.isBlank(start_date) ? null : convertDate(start_date);
					LocalDate endDateFilter = convertDate(end_date);

					logger.info("JavaPCX Version: {}", JavaPCX.Version);
					pcx.Logon(pcx_url, username, password, "", "");

					// Database connection setup
					conn = DriverManager.getConnection(context.getProperty(DATABASE_URL).getValue(),
							context.getProperty(DATABASE_USER).getValue(),
							context.getProperty(DATABASE_PASSWORD).getValue());
					// conn.setAutoCommit(false);

					// Insert into migration_tracker table using CallableStatement
					String insertTrackerSQL = "BEGIN INSERT INTO migration_tracker (csv_file_name, from_date, to_date, start_time, status) VALUES (?, ?, ?, ?, ?) RETURNING id INTO ?; END;";
					CallableStatement cstmt = conn.prepareCall(insertTrackerSQL);
					cstmt.setString(1, csvFileName);
					cstmt.setTimestamp(2, Timestamp.valueOf(startDateFilter.atStartOfDay()));
					cstmt.setTimestamp(3, Timestamp.valueOf(endDateFilter.atStartOfDay()));
					cstmt.setTimestamp(4, Timestamp.valueOf(startTime));
					cstmt.setString(5, "In Progress");
					cstmt.registerOutParameter(6, Types.INTEGER);
					cstmt.execute();
					migrationTrackerId = cstmt.getInt(6);

					for (CSVRecord record : records) {
						String path = record.get("folderpath");
						logger.info("Processing Record: {}", path);

						for (String oneFileName : getDocuments(pcx, path)) {
							getRevisionDocumentIDs(pcx, "/" + path + "/", oneFileName, startDateFilter, endDateFilter,
									downloadDir, context, migrationTrackerId);
						}
						logger.info("Completed Record: {}", path);
					}

					// Update migration_tracker table with end time and status
					String updateTrackerSQL = "UPDATE migration_tracker SET end_time = ?, status = ? WHERE id = ?";
					pstmt = conn.prepareStatement(updateTrackerSQL);
					pstmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
					pstmt.setString(2, "Completed");
					pstmt.setInt(3, migrationTrackerId);
					pstmt.executeUpdate();

					// Commit transaction
					conn.commit();
					logger.info("Migration completed successfully for FlowFile: {}", csvFileName);
				} catch (Exception e) {
					logger.error("Error processing FlowFile", e);
					if (conn != null) {
						try {
							conn.rollback();
							logger.info("Transaction rolled back due to error");
						} catch (SQLException rollbackException) {
							logger.error("Error rolling back transaction", rollbackException);
						}
					}
				} finally {
					if (pstmt != null) {
						try {
							pstmt.close();
						} catch (SQLException e) {
							logger.error("Error closing PreparedStatement", e);
						}
					}
					if (conn != null) {
						try {
							conn.close();
						} catch (SQLException e) {
							logger.error("Error closing Connection", e);
						}
					}
				}
			}
		});

		session.write(flowFile, new OutputStreamCallback() {
			public void process(OutputStream out) throws IOException {
				out.write("".getBytes());
			}
		});

		session.transfer(flowFile, SUCCESS);
		logger.info("FlowFile transferred to SUCCESS relationship");
	}

	public List<String> getDocuments(JavaPCX pcx, String path) {
		List<String> docList = new ArrayList<>();
		boolean more = true;
		String previous = "";

		while (more) {
			more = false;
			pcx.DocumentListInitByPath(path);
			pcx.FolderListReqAttr("file");
			pcx.DocumentRevListSetAttr("array_max", "500");
			pcx.DocumentRevListSetAttr("previous_name", previous);
			pcx.DocumentListComplete();
			if (!pcx.Error) {
				for (int i = 0; i < pcx.DocumentListGetCount(); i++) {
					more = true;
					previous = pcx.DocumentListGetAttr("file", i);
					docList.add(pcx.DocumentListGetAttr("file", i));
				}
			} else {
				logger.error("Error getDocument: {}", pcx.ErrorDescription);
			}
		}
		logger.info("Total number of documents: {}", docList.size());
		return docList;
	}

	public void getRevisionDocumentIDs(JavaPCX pcx, String path, String fileName, LocalDate startDateFilter,
			LocalDate endDateFilter, String downloadDir, final ProcessContext context, int migrationTrackerId) {
		boolean more = true;
		String previousRevisionId = "";
		String previousImportDate = "";
		List<String> docRevisionList = new ArrayList<>();

		while (more) {
			more = false;
			pcx.DocumentRevListInitByPath(path + fileName);
			pcx.DocumentRevListSetAttr("array_max", "100");

			if (!previousRevisionId.equals("")) {
				pcx.DocumentRevListSetAttr("previous_revision_id", previousRevisionId);
				pcx.DocumentRevListSetAttr("previous_import_date", previousImportDate);
			}
			pcx.DocumentRevListComplete();

			if (!pcx.Error) {
				int docRevListCount = pcx.DocumentRevListGetCount();

				for (int i = 0; i < docRevListCount; i++) {
					more = true;
					String documentId = pcx.DocumentRevListGetAttr("document_id", i);
					String revisionId = pcx.DocumentRevListGetAttr("revision_id", i);
					String documentImportDate = pcx.DocumentRevListGetAttr("import_date", i);

					previousRevisionId = revisionId;
					previousImportDate = documentImportDate;

					DateTimeFormatter f = DateTimeFormatter.ofPattern("HH:mm:ss MMMM/dd/yyyy");
					LocalDate formattedDocumentImportDate = LocalDate.parse(documentImportDate, f);

					if (endDateFilter != null && startDateFilter == null
							&& formattedDocumentImportDate.isAfter(endDateFilter)) {
						continue;
					}
					if (endDateFilter != null && startDateFilter != null) {
						if (formattedDocumentImportDate.isBefore(startDateFilter)
								|| formattedDocumentImportDate.isAfter(endDateFilter)) {
							continue;
						}
					}

					docRevisionList.add(documentId);
				}
			} else {
				logger.error("Error: {}", pcx.ErrorDescription);
			}
		}

		if (docRevisionList != null && docRevisionList.size() > 0) {

			logger.info("Total Document Revisions to Process: {}", docRevisionList.size());

			// Download the current revision
			downloadDocumentByID(pcx, "/" + path + "/", fileName, docRevisionList.get(0), 0, downloadDir, path, context,
					migrationTrackerId);

			for (int i = 1; i < docRevisionList.size(); i++) {

				String oneRevisionDocumentID = docRevisionList.get(i);
				int versionIndex = docRevisionList.size() - i;
				downloadDocumentByID(pcx, "/" + path + "/", fileName, oneRevisionDocumentID, versionIndex, downloadDir,
						path, context, migrationTrackerId);

			}
		}

		logger.info("Total number of document revisions for {}: {}", fileName, docRevisionList.size());
	}

	public void downloadDocumentByID(JavaPCX pcx, String path, String fileName, String revisionDocumentID,
			int versionIndex, String downloadDir, String folderPath, final ProcessContext context,
			int migrationTrackerId) {

		try {
			String concatFileName, filePrefix, fileExtension, importDate;

			pcx.DocumentGetRecInitByID(revisionDocumentID);
			pcx.DocumentGetRecComplete();
			importDate = pcx.DocumentGetRecGetAttr("import_date");
			filePrefix = pcx.DocumentGetRecGetAttr("file_prefix");
			fileExtension = pcx.DocumentGetRecGetAttr("file_extension");

			DateTimeFormatter f = DateTimeFormatter.ofPattern("kk:mm:ss MMMM/dd/yyyy");
			LocalDateTime formattedImportDateTime = LocalDateTime.parse(importDate, f);

			concatFileName = versionIndex == 0 ? standardizeFileName(filePrefix + "." + fileExtension)
					: standardizeFileName(filePrefix + "." + fileExtension + ".v" + versionIndex);

			// Create folder if it doesn't exist
			String fullFolderPath = Paths.get(downloadDir, folderPath).toString();

			Files.createDirectories(Paths.get(fullFolderPath));

			logger.info("Full folderpath: {}", fullFolderPath);
			File file = new File(Paths.get(fullFolderPath, concatFileName).toString());
			if (file.exists()) {
				logger.info("File already exists - skipping download: {}", concatFileName);
				logDownloadStatus(context, folderPath, fileName, versionIndex, revisionDocumentID, "success", null,
						migrationTrackerId);
				logger.info("Entry added in the Documents table");
			} else {
				pcx.ReadFileInitByID(revisionDocumentID, file.getAbsolutePath());
				pcx.ReadFileComplete();

				if (!pcx.Error) {
					logger.info("Read file complete: {}", concatFileName);
					generateMetadataXMLFile(filePrefix + "." + fileExtension, path, formattedImportDateTime,
							fullFolderPath, versionIndex);
					logDownloadStatus(context, folderPath, fileName, versionIndex, revisionDocumentID, "success", null,
							migrationTrackerId);
				} else {
					logDownloadStatus(context, folderPath, fileName, versionIndex, revisionDocumentID, "failure",
							pcx.ErrorDescription, migrationTrackerId);
				}
			}
		} catch (Exception e) {
			logger.error("Error downloading document by ID: {}", revisionDocumentID, e);
			logDownloadStatus(context, folderPath, fileName, versionIndex, revisionDocumentID, "failure",
					e.getMessage(), migrationTrackerId);
		}

	}

	public String standardizeFileName(String fileName) {
		String trimmed = fileName.replaceAll("\\s+$", "");
		trimmed = trimmed.replaceAll("\\s+", " ");
		return trimmed;
	}

	public void generateMetadataXMLFile(String fileName, String folderPath, LocalDateTime importDate,
			String fullFolderPath, int versionIndex) {
		try (BufferedWriter xmlWriter = new BufferedWriter(new FileWriter(Paths.get(fullFolderPath, fileName
				+ (versionIndex == 0 ? ".metadata.properties.xml" : ".metadata.properties.xml.v" + versionIndex))
				.toString()))) {
			xmlWriter.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
			xmlWriter.write("<!DOCTYPE properties SYSTEM \"http://java.sun.com/dtd/properties.dtd\">\n");
			xmlWriter.write("<properties>\n");
			xmlWriter.write("  <entry key=\"cm:name\">" + fileName + "</entry>\n");
			// xmlWriter.write(" <entry key=\"cm:description\">" + folderPath +
			// "</entry>\n");
			xmlWriter.write("  <entry key=\"cm:author\">" + "hmadmin" + "</entry>\n");
			xmlWriter.write("  <entry key=\"cm:created\">" + importDate.toString() + "</entry>\n");
			xmlWriter.write("</properties>\n");
		} catch (IOException e) {
			logger.error("Error generating metadata XML file for: {}", fileName, e);
		}
	}

	public LocalDate convertDate(String dateArg) {
		DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd");
		LocalDate date = null;
		try {
			date = LocalDate.parse(dateArg, f);
		} catch (DateTimeParseException e) {
			logger.error("Please enter date in YYYY-MM-DD format", e);
		}
		return date;
	}

	public void logDownloadStatus(final ProcessContext context, String folderPath, String fileName, int version,
			String revisionDocumentID, String status, String errorMessage, int migrationTrackerId) {
		try (Connection conn = DriverManager.getConnection(context.getProperty(DATABASE_URL).getValue(),
				context.getProperty(DATABASE_USER).getValue(), context.getProperty(DATABASE_PASSWORD).getValue())) {
			String sql = "INSERT INTO documents (folder_path, file_name, version, download_status, error_message, timestamp, migration_tracker_id) VALUES (?, ?, ?, ?, ?, ?, ?)";
			try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
				pstmt.setString(1, folderPath);
				pstmt.setString(2, fileName);
				pstmt.setInt(3, version);
				pstmt.setString(4, status);
				pstmt.setString(5, errorMessage);
				pstmt.setTimestamp(6, new Timestamp(new Date().getTime()));
				pstmt.setInt(7, migrationTrackerId);
				pstmt.executeUpdate();
				logger.info("Entry added in the Documents table");
			}
		} catch (SQLException e) {
			logger.info("Failed to add entry in the Documents Table" + e.getMessage());
		}
	}

	public void generateReconciliationReport(final ProcessContext context) {
		try (Connection conn = DriverManager.getConnection(context.getProperty(DATABASE_URL).getValue(),
				context.getProperty(DATABASE_USER).getValue(), context.getProperty(DATABASE_PASSWORD).getValue())) {
			String sql = "SELECT COUNT(*) AS total_files, "
					+ "SUM(CASE WHEN download_status = 'success' THEN 1 ELSE 0 END) AS downloaded_files, "
					+ "SUM(CASE WHEN download_status = 'failure' THEN 1 ELSE 0 END) AS failed_files "
					+ "FROM documents";
			try (PreparedStatement pstmt = conn.prepareStatement(sql); ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) {
					int totalFiles = rs.getInt("total_files");
					int downloadedFiles = rs.getInt("downloaded_files");
					int failedFiles = rs.getInt("failed_files");

					String reportSql = "INSERT INTO reconciliation_reports (total_files, downloaded_files, failed_files, timestamp) VALUES (?, ?, ?, ?)";
					try (PreparedStatement reportPstmt = conn.prepareStatement(reportSql)) {
						reportPstmt.setInt(1, totalFiles);
						reportPstmt.setInt(2, downloadedFiles);
						reportPstmt.setInt(3, failedFiles);
						reportPstmt.setTimestamp(4, new Timestamp(new Date().getTime()));
						reportPstmt.executeUpdate();
						logger.info("Reconciliation report generated successfully");
					}
				}
			}
		} catch (SQLException e) {
			logger.error("Error generating reconciliation report", e);
		}
	}
}
