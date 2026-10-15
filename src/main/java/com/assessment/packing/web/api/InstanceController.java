package com.assessment.packing.web.api;

import com.assessment.packing.web.dto.InstanceDtos;
import com.assessment.packing.web.service.InstanceCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/instances")
public final class InstanceController {
    private final InstanceCatalogService instances;

    public InstanceController(InstanceCatalogService instances) {
        this.instances = instances;
    }

    @GetMapping
    public List<InstanceDtos.Summary> list() {
        return instances.list();
    }

    @GetMapping("/{id}")
    public InstanceDtos.Detail detail(@PathVariable("id") String id) {
        return instances.detail(id);
    }
}
