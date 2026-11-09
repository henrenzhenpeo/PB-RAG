package dev.pbrag.ingestion.storage;

public record StoredObject(String key, String sha256, long size) {
}
