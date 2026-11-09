package dev.pbrag.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.pbrag.ingestion.service.impl.DocumentUploadServiceImpl;
import dev.pbrag.ingestion.storage.ObjectStorage;
import dev.pbrag.ingestion.storage.StoredObject;
import dev.pbrag.platform.api.InvalidRequestException;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class DocumentUploadServiceTests {

	@Test
	void rejectsNonMarkdownFileBeforeStorage() {
		RecordingStorage storage = new RecordingStorage();
		DocumentUploadService service = new DocumentUploadServiceImpl(
				storage, failingCreation(), 1024);

		assertThatThrownBy(() -> service.upload(
				UUID.randomUUID(), UUID.randomUUID(),
				new MockMultipartFile("file", "notes.txt", "text/plain", "text".getBytes())))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessageContaining(".md");
		assertThat(storage.stored).isFalse();
	}

	@Test
	void deletesStoredObjectWhenDatabaseCreationFails() {
		RecordingStorage storage = new RecordingStorage();
		DocumentUploadService service = new DocumentUploadServiceImpl(
				storage, failingCreation(), 1024);

		assertThatThrownBy(() -> service.upload(
				UUID.randomUUID(), UUID.randomUUID(),
				new MockMultipartFile("file", "notes.md", "text/markdown", "# Notes".getBytes())))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("database failure");
		assertThat(storage.stored).isTrue();
		assertThat(storage.deleted).isTrue();
	}

	private static IngestionCreationService failingCreation() {
		return (workspaceId, knowledgeBaseId, displayName, contentSha256, storageKey) -> {
			throw new IllegalStateException("database failure");
		};
	}

	private static final class RecordingStorage implements ObjectStorage {
		private boolean stored;
		private boolean deleted;

		@Override
		public StoredObject store(InputStream input, String originalFilename, long maximumBytes) {
			stored = true;
			return new StoredObject("object.md", "hash", 7);
		}

		@Override
		public void delete(String key) throws IOException {
			deleted = true;
		}
	}
}
