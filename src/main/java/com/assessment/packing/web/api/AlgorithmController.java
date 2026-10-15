package com.assessment.packing.web.api;

import com.assessment.packing.web.dto.AlgorithmDto;
import com.assessment.packing.web.dto.DecoderDto;
import com.assessment.packing.web.service.CatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/algorithms")
public final class AlgorithmController {
    private final CatalogService catalog;

    public AlgorithmController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/search")
    public List<AlgorithmDto> search() {
        return catalog.searchAlgorithms();
    }

    @GetMapping("/decoders")
    public List<DecoderDto> decoders() {
        return catalog.decoders();
    }
}
