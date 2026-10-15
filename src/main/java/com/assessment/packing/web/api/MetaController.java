package com.assessment.packing.web.api;

import com.assessment.packing.web.dto.MetaDto;
import com.assessment.packing.web.service.CatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/meta")
public final class MetaController {
    private final CatalogService catalog;

    public MetaController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public MetaDto get() {
        return catalog.meta();
    }
}
