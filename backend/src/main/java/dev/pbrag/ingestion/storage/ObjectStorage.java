package dev.pbrag.ingestion.storage;

import java.io.IOException;
import java.io.InputStream;

public interface ObjectStorage {

	StoredObject store(InputStream input, String originalFilename, long maximumBytes) throws IOException;

	void delete(String key) throws IOException;
}
