package com.delta.custom.processors.pcxmigration;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.nifi.annotation.behavior.ReadsAttribute;
import org.apache.nifi.annotation.behavior.ReadsAttributes;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lrs.pcx.JavaPCX;

@Tags({ "example" })
@CapabilityDescription("Downloads failed documents and updates their status")
@ReadsAttributes({ @ReadsAttribute(attribute = "", description = "") })
@WritesAttributes({ @WritesAttribute(attribute = "", description = "") })
public class RetryFailedDocumentsProcessor extends AbstractProcessor {

	private static final Logger logger = LoggerFactory.getLogger(RetryFailedDocumentsProcessor.class);

	public static final PropertyDescriptor DOWNLOAD_DIR = new PropertyDescriptor.Builder().name("DOWNLOAD_DIR")
			.displayName("Download Directory").description("Document download directory").required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final PropertyDescriptor PCX_URL = new PropertyDescriptor.Builder().name("PCX_URL")
			.displayName("PCX URL").description("PCX URL (Add port eg : rc-hp28.ut.dentegra.lab:5805)").required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final PropertyDescriptor USERNAME = new PropertyDescriptor.Builder().name("PCX_USERNAME")
			.displayName("Username").description("PCX Username").required(true)
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

	private List<PropertyDescriptor> descriptors;
	private Set<Relationship> relationships;

	@Override
	protected void init(final ProcessorInitializationContext context) {
		List<PropertyDescriptor> descriptors = new ArrayList<>();
		descriptors.add(DOWNLOAD_DIR);
		descriptors.add(PCX_URL);
		descriptors.add(USERNAME);
		descriptors.add(PASSWORD);
		descriptors.add(DATABASE_URL);
		descriptors.add(DATABASE_USER);
		descriptors.add(DATABASE_PASSWORD);
		this.descriptors = Collections.unmodifiableList(descriptors);

		Set<Relationship> relationships = new HashSet<>();
		relationships
				.add(new Relationship.Builder().name("SUCCESS").description("Files Downloaded Successfully").build());
		relationships.add(new Relationship.Builder().name("FAILURE").description("Failed to Download Files").build());
		this.relationships = Collections.unmodifiableSet(relationships);
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
		// Any initialization code goes here
	}

	@Override
	public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException {
		try (Connection conn = createConnection(context)) {
			List<DocumentRecord> failedRecords = fetchFailedRecords(conn);

			JavaPCX pcx = initializePCX(context.getProperty(PCX_URL).getValue(),
					context.getProperty(USERNAME).getValue(), context.getProperty(PASSWORD).getValue());

			for (DocumentRecord record : failedRecords) {
				downloadDocumentByID(conn, pcx, record, context);
			}
		} catch (SQLException e) {
			logger.error("Database error during processing", e);
			throw new ProcessException(e);
		} catch (Exception e) {
			logger.error("Database error during processing", e);
		}
	}

	private Connection createConnection(final ProcessContext context) throws SQLException {
		return DriverManager.getConnection(context.getProperty(DATABASE_URL).getValue(),
				context.getProperty(DATABASE_USER).getValue(), context.getProperty(DATABASE_PASSWORD).getValue());
	}

	private List<DocumentRecord> fetchFailedRecords(Connection conn) throws SQLException {
		String sql = "SELECT id, folder_path, file_name, version, versionId FROM documents WHERE download_status = 'failure'";
		try (PreparedStatement pstmt = conn.prepareStatement(sql); ResultSet rs = pstmt.executeQuery()) {

			List<DocumentRecord> records = new ArrayList<>();
			while (rs.next()) {
				records.add(new DocumentRecord(rs.getInt("id"), rs.getString("folder_path"), rs.getString("file_name"),
						rs.getInt("version"), rs.getString("versionId")));
			}
			return records;
		}
	}

	private JavaPCX initializePCX(String url, String username, String password) {
		JavaPCX pcx = new JavaPCX();
		pcx.Secure = true;
		pcx.RecvTimeout = 3600;
		pcx.ConnTimeout = 20;

		logger.info("Connecting to PCX...");
		logger.info("JavaPCX Version: {}", JavaPCX.Version);
		pcx.Logon(url, username, password, "", "");
		return pcx;
	}

	private void downloadDocumentByID(Connection conn, JavaPCX pcx, DocumentRecord record, final ProcessContext context)
			throws Exception {
		String downloadDir = context.getProperty(DOWNLOAD_DIR).getValue();
		String path = record.folderPath;
		String fileName = record.fileName;
		int versionIndex = record.version;
		String revisionDocumentID = record.versionId;

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
		String fullFolderPath = Paths.get(downloadDir, path).toString();
		Files.createDirectories(Paths.get(fullFolderPath));

		logger.info("Full folderpath: {}", fullFolderPath);
		File file = new File(Paths.get(fullFolderPath, concatFileName).toString());
		if (file.exists()) {
			logger.info("File already exists - skipping download: {}", concatFileName);
		} else {
			pcx.ReadFileInitByID(revisionDocumentID, file.getAbsolutePath());
			pcx.ReadFileComplete();

			if (!pcx.Error) {
				logger.info("Read file complete: {}", concatFileName);
				generateMetadataXMLFile(filePrefix + "." + fileExtension, path, formattedImportDateTime, fullFolderPath,
						versionIndex, revisionDocumentID);

				updateDownloadStatus(conn, record.id, "success", null);
			} else {
				updateDownloadStatus(conn, record.id, "failure", pcx.ErrorDescription);
			}
		}
	}

	private void updateDownloadStatus(Connection conn, int id, String status, String errorMessage) throws SQLException {
		String sql = "UPDATE documents SET download_status = ?, error_message = ?, timestamp = ? WHERE id = ?";
		try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setString(1, status);
			pstmt.setString(2, errorMessage);
			pstmt.setTimestamp(3, new Timestamp(new Date().getTime()));
			pstmt.setInt(4, id);
			pstmt.executeUpdate();
		}
	}

	private String standardizeFileName(String fileName) {
		String trimmed = fileName.replaceAll("\\s+$", "");
		trimmed = trimmed.replaceAll("\\s+", " ");
		return trimmed;
	}

	private void generateMetadataXMLFile(String fileName, String folderPath, LocalDateTime importDate,
			String fullFolderPath, int versionIndex, String revisionId) {
		try (BufferedWriter xmlWriter = new BufferedWriter(new FileWriter(Paths.get(fullFolderPath, fileName
				+ (versionIndex == 0 ? ".metadata.properties.xml" : ".metadata.properties.xml.v" + versionIndex))
				.toString()))) {
			xmlWriter.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
			xmlWriter.write("<!DOCTYPE properties SYSTEM \"http://java.sun.com/dtd/properties.dtd\">\n");
			xmlWriter.write("<properties>\n");
			xmlWriter.write("  <entry key=\"cm:name\">" + fileName + "</entry>\n");
			xmlWriter.write("  <entry key=\"cm:author\">" + "hmadmin" + "</entry>\n");
			xmlWriter.write("  <entry key=\"dars:pcxId\">" + revisionId + "</entry>\n");
			xmlWriter.write("  <entry key=\"cm:created\">" + importDate.toString() + "</entry>\n");
			xmlWriter.write("</properties>\n");
			logger.info("Metadata XML file generated for: {}", fileName);
		} catch (IOException e) {
			logger.error("Error generating metadata XML file for: {}", fileName, e);
		}
	}

	private static class DocumentRecord {
		int id;
		String folderPath;
		String fileName;
		int version;
		String versionId;

		DocumentRecord(int id, String folderPath, String fileName, int version, String versionId) {
			this.id = id;
			this.folderPath = folderPath;
			this.fileName = fileName;
			this.version = version;
			this.versionId = versionId;
		}
	}
}
