package dev.pbrag.ingestion.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalFileObjectStorage implements ObjectStorage {

	private final Path root;

	public LocalFileObjectStorage(@Value("${pbrag.storage.local.root:./data/objects}") String root) {
		this.root = Path.of(root).toAbsolutePath().normalize();
	}

	@Override
	public StoredObject store(InputStream input, String originalFilename, long maximumBytes) throws IOException {
		String suffix = originalFilename.toLowerCase().endsWith(".md") ? ".md" : ".bin";
		String date = LocalDate.now().toString();
		String key = date + "/" + UUID.randomUUID() + suffix;
		Path target = resolveKey(key);
		Files.createDirectories(target.getParent());
		Path temporary = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
		MessageDigest digest = sha256();
		long total = 0;
		try (OutputStream output = new DigestOutputStream(Files.newOutputStream(temporary), digest)) {
			byte[] buffer = new byte[8192];
			int read;
			while ((read = input.read(buffer)) != -1) {
				total += read;
				if (total > maximumBytes) {
					throw new IOException("Uploaded file exceeds the size limit");
				}
				output.write(buffer, 0, read);
			}
			Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
			return new StoredObject(key, HexFormat.of().formatHex(digest.digest()), total);
		}
		finally {
			Files.deleteIfExists(temporary);
		}
	}

	@Override
	public void delete(String key) throws IOException {
		Files.deleteIfExists(resolveKey(key));
	}

	private Path resolveKey(String key) throws IOException {
		Path resolved = root.resolve(key).normalize();
		if (!resolved.startsWith(root)) {
			throw new IOException("Invalid storage key");
		}
		return resolved;
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}
}
