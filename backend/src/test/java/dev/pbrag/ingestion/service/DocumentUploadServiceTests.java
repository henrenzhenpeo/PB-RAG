package dev.pbrag.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.pbrag.ingestion.service.impl.DocumentUploadServiceImpl;
import dev.pbrag.ingestion.storage.ObjectStorage;
import dev.pbrag.ingestion.storage.StoredObject;
import dev.pbrag.platform.api.InvalidRequestException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class DocumentUploadServiceTests {
	@Test
	void closesMultipartInputStreamAfterStorage() throws IOException {
		TrackingInputStream input = new TrackingInputStream("# Notes".getBytes());
		var file = org.mockito.Mockito.mock(org.springframework.web.multipart.MultipartFile.class);
		org.mockito.Mockito.when(file.isEmpty()).thenReturn(false);
		org.mockito.Mockito.when(file.getSize()).thenReturn(7L);
		org.mockito.Mockito.when(file.getOriginalFilename()).thenReturn("notes.md");
		org.mockito.Mockito.when(file.getContentType()).thenReturn("text/markdown");
		org.mockito.Mockito.when(file.getInputStream()).thenReturn(input);
		DocumentUploadService service = new DocumentUploadServiceImpl(
				new RecordingStorage(), failingCreation(), 1024);

		assertThatThrownBy(() -> service.upload(UUID.randomUUID(), UUID.randomUUID(), file))
				.isInstanceOf(IllegalStateException.class);

		assertThat(input.closed).isTrue();
	}

	@Test
	void rejectsUnsupportedFileBeforeStorage() {
		RecordingStorage storage = new RecordingStorage();
		DocumentUploadService service = new DocumentUploadServiceImpl(
				storage, failingCreation(), 1024);

		assertThatThrownBy(() -> service.upload(
				UUID.randomUUID(), UUID.randomUUID(),
				new MockMultipartFile("file", "notes.txt", "text/plain", "text".getBytes())))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessageContaining(".md and .pdf");
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
		return (workspaceId, knowledgeBaseId, displayName, mediaType, contentSha256, storageKey) -> {
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
		public InputStream open(String key) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void delete(String key) throws IOException {
			deleted = true;
		}
	}

	private static final class TrackingInputStream extends ByteArrayInputStream {
		private boolean closed;

		private TrackingInputStream(byte[] content) {
			super(content);
		}

		@Override
		public void close() throws IOException {
			closed = true;
			super.close();
		}
	}
}
