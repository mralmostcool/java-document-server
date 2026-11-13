package pdfserver.storage;

import java.io.InputStream;

public interface ObjectStorage {

    /**
     * Put object into storage as a stream.
     * Key format: {ownerId}/{documentId}
     */
    void put(String ownerId, String documentId, InputStream data, long size, String contentType);

    /**
     * Stream object data from storage.
     */
    InputStream get(String ownerId, String documentId);

    /**
     * Check if object exists in storage.
     */
    boolean exists(String ownerId, String documentId);

    /**
     * Delete object from storage.
     */
    void delete(String ownerId, String documentId);
}

