package dev.pbrag.ingestion.storage;

import java.io.IOException;
import java.io.InputStream;

/** 文档原始文件存储抽象，使业务流程不依赖本地磁盘或未来的远程对象存储。 */
public interface ObjectStorage {

	/** 在大小限制内保存输入流，并返回存储键、摘要和实际大小。 */
	StoredObject store(InputStream input, String originalFilename, long maximumBytes) throws IOException;
	/** 根据存储键打开原始对象输入流，调用方负责关闭。 */
	InputStream open(String key) throws IOException;
	/** 删除指定存储键对应的对象。 */
	void delete(String key) throws IOException;
}
