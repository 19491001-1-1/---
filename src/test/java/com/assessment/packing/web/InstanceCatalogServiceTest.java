package com.assessment.packing.web;

import com.assessment.packing.web.error.ApiException;
import com.assessment.packing.web.service.InstanceCatalogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class InstanceCatalogServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void reportsUnavailableCatalogForInvalidConfiguredDirectory() {
        var service = new InstanceCatalogService(temporaryDirectory.resolve("missing").toString());

        ApiException exception = assertThrows(ApiException.class, service::list);

        assertEquals("INSTANCE_CATALOG_UNAVAILABLE", exception.code());
        assertEquals(503, exception.status().value());
    }
}
