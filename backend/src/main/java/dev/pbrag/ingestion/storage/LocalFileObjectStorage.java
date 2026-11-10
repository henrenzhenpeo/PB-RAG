package dev.pbrag.ingestion.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
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

/**
 * 本地文件系统对象存储实现。上传时流式计算 SHA-256，并通过临时文件原子移动到内容寻址路径。
 */
@Component
public class LocalFileObjectStorage implements ObjectStorage {

	private final Path root;

	/** 将配置的存储根目录转换为绝对、规范化路径。 */
	public LocalFileObjectStorage(@Value("${pbrag.storage.local.root:./data/objects}") String root) {
		this.root = Path.of(root).toAbsolutePath().normalize();
	}

	/** 流式保存文件、限制最大字节数并同步计算内容摘要。 */
	@Override
	public StoredObject store(InputStream input, String originalFilename, long maximumBytes) throws IOException {
		String lowerName = originalFilename.toLowerCase();
		String suffix = lowerName.endsWith(".md") ? ".md" : lowerName.endsWith(".pdf") ? ".pdf" : ".bin";
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
			moveIntoPlace(temporary, target);
			return new StoredObject(key, HexFormat.of().formatHex(digest.digest()), total);
		}
		finally {
			Files.deleteIfExists(temporary);
		}
	}

	/** 优先使用原子移动；不支持该能力的文件系统退化为同目录普通移动。 */
	private static void moveIntoPlace(Path temporary, Path target) throws IOException {
		try {
			Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (AtomicMoveNotSupportedException exception) {
			Files.move(temporary, target);
		}
	}

	/** 按存储键打开文件输入流，调用方负责关闭该流。 */
	@Override
	public InputStream open(String key) throws IOException {
		return Files.newInputStream(resolveKey(key));
	}

	/** 删除指定存储对象；文件不存在时保持幂等。 */
	@Override
	public void delete(String key) throws IOException {
		Files.deleteIfExists(resolveKey(key));
	}

	/** 解析并校验存储键，阻止路径穿越到存储根目录之外。 */
	private Path resolveKey(String key) throws IOException {
		Path resolved = root.resolve(key).normalize();
		if (!resolved.startsWith(root)) {
			throw new IOException("Invalid storage key");
		}
		return resolved;
	}

	/** 创建 SHA-256 摘要器；JVM 缺少标准算法时转换为非法状态。 */
	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}
}
