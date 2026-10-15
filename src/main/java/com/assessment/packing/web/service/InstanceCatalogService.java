package com.assessment.packing.web.service;

import com.assessment.packing.io.InstanceFiles;
import com.assessment.packing.io.InstanceReader;
import com.assessment.packing.model.Instance;
import com.assessment.packing.web.dto.InstanceDtos;
import com.assessment.packing.web.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public final class InstanceCatalogService {
    private static final Logger LOGGER = LoggerFactory.getLogger(InstanceCatalogService.class);
    private final Path directory;
    private final InstanceReader reader = new InstanceReader();

    public InstanceCatalogService(@Value("${packing.instances-directory:gcut01}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    public List<InstanceDtos.Summary> list() {
        return loadAll().values().stream().map(this::summary).toList();
    }

    public InstanceDtos.Detail detail(String id) {
        return detail(id, require(id));
    }

    public Instance require(String id) {
        Instance instance = loadAll().get(id);
        if (instance == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "INSTANCE_NOT_FOUND",
                    "不存在的实例：" + id);
        }
        return instance;
    }

    private Map<String, Instance> loadAll() {
        try {
            Map<String, Instance> instances = new LinkedHashMap<>();
            for (Path path : InstanceFiles.listGcutFiles(directory)) {
                Instance instance = reader.read(path);
                instances.put(instance.name(), instance);
            }
            return instances;
        } catch (IOException | IllegalArgumentException exception) {
            LOGGER.error("读取排样实例目录失败：{}", directory, exception);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "INSTANCE_CATALOG_UNAVAILABLE",
                    "实例目录暂时无法读取，请检查后端配置");
        }
    }

    private InstanceDtos.Summary summary(Instance instance) {
        return new InstanceDtos.Summary(
                instance.name(), instance.name(), instance.items().size(),
                instance.binWidth(), instance.binHeight(), totalItemArea(instance), "GCUT");
    }

    private InstanceDtos.Detail detail(String id, Instance instance) {
        List<InstanceDtos.Item> items = instance.items().stream()
                .map(item -> new InstanceDtos.Item(item.id(), item.width(), item.height(), item.area()))
                .toList();
        return new InstanceDtos.Detail(
                id, instance.name(), instance.items().size(), instance.binWidth(), instance.binHeight(),
                instance.binArea(), totalItemArea(instance), "GCUT", items);
    }

    private static long totalItemArea(Instance instance) {
        return instance.items().stream().mapToLong(item -> item.area()).sum();
    }
}
