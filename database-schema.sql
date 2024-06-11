CREATE TABLE documents (
    id SERIAL PRIMARY KEY,
    folder_path VARCHAR(255),
    file_name VARCHAR(255),
    version INT,
    download_status VARCHAR(50),
    error_message TEXT,
    timestamp TIMESTAMP
);

CREATE TABLE reconciliation_reports (
    id SERIAL PRIMARY KEY,
    total_files INT,
    downloaded_files INT,
    failed_files INT,
    timestamp TIMESTAMP
);
