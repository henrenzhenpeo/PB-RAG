package dev.pbrag.ingestion.parser;

import java.io.IOException;
import java.io.InputStream;

/** 将原始文件转换为带稳定来源位置的结构块，不负责分块、向量化或持久化。 */
public interface DocumentParser {

	/** 判断当前解析器是否支持给定对象存储键对应的文件类型。 */
	boolean supports(String storageKey);
	/** 读取原始文件流并生成带稳定来源位置的结构化解析结果。 */
	ParsedDocument parse(InputStream input) throws IOException;
}
