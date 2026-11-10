package dev.pbrag.ingestion.storage;

/** 对象存储写入结果，内容摘要用于幂等去重，大小用于审计与校验。 */
public record StoredObject(String key, String sha256, long size) {
}
