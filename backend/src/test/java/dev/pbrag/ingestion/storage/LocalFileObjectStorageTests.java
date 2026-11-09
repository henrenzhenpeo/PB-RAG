package dev.pbrag.ingestion.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileObjectStorageTests {

	@TempDir
	Path root;

	@Test
	void storesContentAndCalculatesSha256() throws IOException {
		LocalFileObjectStorage storage = new LocalFileObjectStorage(root.toString());
		byte[] content = "# PB-RAG\n".getBytes(StandardCharsets.UTF_8);

		StoredObject stored = storage.store(new ByteArrayInputStream(content), "readme.md", 1024);

		assertThat(stored.sha256()).isEqualTo("daf936790e04237f18be2a77a0c2d3bd4114a1737316cb42ea5fec721a5ad1a2");
		assertThat(stored.size()).isEqualTo(content.length);
		assertThat(Files.readAllBytes(root.resolve(stored.key()))).isEqualTo(content);
	}

	@Test
	void removesTemporaryFileWhenLimitIsExceeded() {
		LocalFileObjectStorage storage = new LocalFileObjectStorage(root.toString());

		assertThatThrownBy(() -> storage.store(
				new ByteArrayInputStream(new byte[16]), "large.md", 8))
				.isInstanceOf(IOException.class)
				.hasMessageContaining("size limit");

		assertThat(listRegularFiles(root)).isZero();
	}

	private static long listRegularFiles(Path root) {
		try (var paths = Files.walk(root)) {
			return paths.filter(Files::isRegularFile).count();
		}
		catch (IOException exception) {
			throw new AssertionError(exception);
		}
	}
}
