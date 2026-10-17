package com.assessment.packing.web.api;

import com.assessment.packing.web.dto.ExperimentDtos;
import com.assessment.packing.web.service.ExperimentArtifactService;
import com.assessment.packing.web.service.ExperimentTaskService;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/experiments")
public final class ExperimentController {
    private final ExperimentTaskService service;
    private final ExperimentArtifactService artifacts;

    public ExperimentController(ExperimentTaskService service, ExperimentArtifactService artifacts) {
        this.service = service;
        this.artifacts = artifacts;
    }

    @PostMapping("/preview")
    public ExperimentDtos.Preview preview(@Valid @RequestBody ExperimentDtos.Request request) {
        return service.preview(request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ExperimentDtos.Task create(@Valid @RequestBody ExperimentDtos.Request request) {
        return service.create(request);
    }

    @GetMapping
    public List<ExperimentDtos.Task> list() { return service.list(); }

    @GetMapping("/{id}")
    public ExperimentDtos.Task detail(@PathVariable("id") String id) { return service.require(id); }

    /**
     * 删除一条已结束的实验记录，同时清理其持久化结果和已生成产物。
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") String id) { service.delete(id); }

    /**
     * 取消正在运行或排队中的实验。已算出的结果全部保留，可用 {@code /retry} 接着补。
     */
    @PostMapping("/{id}/cancel")
    public ExperimentDtos.Task cancel(@PathVariable("id") String id) { return service.cancel(id); }

    /**
     * 重试尚未完成的实验：只补算还没有结果的格子，已完成的部分原样保留。
     */
    @PostMapping("/{id}/retry")
    public ExperimentDtos.Task retry(@PathVariable("id") String id) { return service.retry(id); }

    /**
     * 订阅任务事件流。浏览器 EventSource 断线重连时会自动带上 {@code Last-Event-ID}
     * 请求头，服务端据此补发漏掉的事件；带 {@code ?lastEventId=} 查询参数效果相同，
     * 供不便设置请求头的客户端使用。
     */
    @GetMapping(path = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(
            @PathVariable("id") String id,
            @RequestParam(name = "lastEventId", required = false) String lastEventId,
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventIdHeader) {
        String resumeFrom = lastEventIdHeader != null ? lastEventIdHeader : lastEventId;
        return service.events(id, resumeFrom);
    }

    /**
     * 分析视图数据：标量结果 + 单格收敛轨迹 + 实例几何，供热力图、排名、箱线图与收敛曲线对比使用。
     *
     * <p>不要求任务终态——分析视图本来就是随结果陆续到达而更新的，缺的格子为空。
     * 这一点与 {@code POST /artifacts} 的 409 是刻意区分的：产物是一次成型的快照，
     * 半途生成会给出误导性的报告。
     */
    @GetMapping("/{id}/analysis")
    public ExperimentDtos.Analysis analysis(@PathVariable("id") String id) {
        return artifacts.analysis(id);
    }

    /**
     * 列出实验任务的产物清单。任务不存在报 404，还没生成过产物则返回空数组——
     * 「尚未生成」与「任务不存在」必须能被前端区分开。
     */
    @GetMapping("/{id}/artifacts")
    public List<ExperimentDtos.Artifact> artifacts(@PathVariable("id") String id) {
        return artifacts.list(id);
    }

    /**
     * 为一个终态任务生成 CSV / HTML / SVG 产物，返回生成后的清单。
     *
     * <p>用 POST 而不是 GET：它<b>会写磁盘</b>，而 GET 必须保持幂等无副作用——
     * 浏览器、代理和预取都可能替用户发 GET，那会导致「看一眼清单就触发一次全量重写」。
     */
    @PostMapping("/{id}/artifacts")
    public List<ExperimentDtos.Artifact> generateArtifacts(@PathVariable("id") String id) {
        return artifacts.generate(id);
    }

    /**
     * 下载单个产物。
     *
     * <p>{@code name} 里可以带子目录（例如 {@code layouts/gcut1/ils.svg}），但它不是路径，
     * 只是一把查表用的钥匙：只有命中服务端产物清单的名字才会被放行，另有两层路径校验兜底。
     * 校验不通过一律 404，不区分「名字非法」与「文件不存在」，免得这个端点变成探测
     * 存储目录结构的工具。
     *
     * <p>用 {@code {*name}} 而不是 {@code {name:.+}}：后者是单段通配，遇到
     * {@code convergence/gcut1.svg} 这种带子目录的名字压根不匹配，请求会掉进静态资源
     * 处理器并变成 500。{@code {*name}} 捕获的是「剩余整段路径」，且会多带一个前导斜杠，
     * 因此这里先剥掉它再交给校验逻辑——剥掉之后 {@code resolveForDownload} 拿到的名字
     * 与产物清单里的相对路径形式完全一致。
     */
    @GetMapping("/{id}/artifacts/{*name}")
    public ResponseEntity<Resource> downloadArtifact(
            @PathVariable("id") String id, @PathVariable("name") String name) {
        String artifactName = name.startsWith("/") ? name.substring(1) : name;
        java.nio.file.Path file = artifacts.resolveForDownload(id, artifactName);
        Resource resource = new FileSystemResource(file);
        // 一律以附件下载，不让浏览器内联渲染：产物里既有 HTML 也有 SVG，
        // 内联打开等于把用户实验目录里的内容当成同源页面执行。
        String disposition = "attachment; filename=\""
                + ExperimentArtifactService.downloadName(artifactName) + "\"";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(MediaType.parseMediaType(ExperimentArtifactService.contentType(artifactName)))
                .contentLength(file.toFile().length())
                .body(resource);
    }
}
