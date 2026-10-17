package com.assessment.packing.web;

import com.assessment.packing.experiment.AlgorithmCatalog;
import com.assessment.packing.solver.PackingDecoderType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "packing.experiment-store=${java.io.tmpdir}/packing-web-api-test-store")
@AutoConfigureMockMvc
final class WebApiIntegrationTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void exposesProjectAndProductCatalogMetadata() throws Exception {
        mvc.perform(get("/api/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productSearchAlgorithms").value(6))
                .andExpect(jsonPath("$.productDecoders").value(8))
                .andExpect(jsonPath("$.productCombinations").value(48))
                .andExpect(jsonPath("$.implementedAlgorithmEntries").value(46))
                .andExpect(jsonPath("$.version").value(not(blankOrNullString())));

        mvc.perform(get("/api/algorithms/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0].machineValue").value("TABU_MOVE"))
                .andExpect(jsonPath("$[0].guide").value(containsString("禁忌表")))
                .andExpect(jsonPath("$[5].machineValue").value("GA_TABU"));

        mvc.perform(get("/api/algorithms/decoders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[4].machineValue").value("SKYLINE"))
                .andExpect(jsonPath("$[4].guide").value(containsString("天际线")));

        mvc.perform(get("/api/algorithms/advanced"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(32)))
                .andExpect(jsonPath("$[0].machineValue").value("SHELF"))
                .andExpect(jsonPath("$[0].family").value("Shelf"))
                .andExpect(jsonPath("$[0].representative").value("SHELF_BAF_GLOBAL"))
                .andExpect(jsonPath("$[0].idea").value(containsString("Next-Fit")));

        mvc.perform(get("/api/algorithms/families"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='Shelf')].configurations[?(@.id=='SHELF_BAF_WM')].compatibilityOnly").value(hasItem(true)))
                .andExpect(jsonPath("$[?(@.name=='MaxRects')].configurations[?(@.id=='MAXRECTS_BEST')].settings").value(hasItem(containsString("五次解码"))))
                .andExpect(jsonPath("$[?(@.name=='Shelf')].configurations[?(@.id=='SHELF_BAF_WM')].guide.workflow").value(hasItem(containsString("先尝试 WM"))));
    }

    @Test
    void listsAndReadsWhitelistedInstances() throws Exception {
        mvc.perform(get("/api/instances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(12)))
                .andExpect(jsonPath("$[0].id").value("gcut1"))
                .andExpect(jsonPath("$[9].id").value("gcut10"));

        mvc.perform(get("/api/instances/gcut1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(10))
                .andExpect(jsonPath("$.binWidth").value(250))
                .andExpect(jsonPath("$.items", hasSize(10)));
    }

    @Test
    void returnsUniformErrorForMissingInstanceWithoutTreatingIdAsPath() throws Exception {
        mvc.perform(get("/api/instances/..%2Fpom.xml").header("X-Request-Id", "test-request"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Request-Id", "test-request"))
                .andExpect(jsonPath("$.code").value("INSTANCE_NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").value("test-request"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void solvesWithIndependentSearchAndDecoderSelection() throws Exception {
        mvc.perform(post("/api/solve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("TABU_MOVE", "MAXRECTS", "EVALUATIONS", 40, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.legal").value(true))
                .andExpect(jsonPath("$.searchAlgorithm").value("TABU_MOVE"))
                .andExpect(jsonPath("$.decoder").value("MAXRECTS"))
                .andExpect(jsonPath("$.packedArea").isNumber())
                .andExpect(jsonPath("$.placements").isArray())
                .andExpect(jsonPath("$.convergence").isArray())
                .andExpect(jsonPath("$.cacheQueries").isNumber())
                .andExpect(jsonPath("$.algorithmStatistics.completedIterations").isNumber());
    }

    @Test
    void everyProductSearchAlgorithmCanUseTheSolveApi() throws Exception {
        for (var algorithm : AlgorithmCatalog.optimizerAlgorithms()) {
            mvc.perform(post("/api/solve")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request(algorithm.name(), PackingDecoderType.SKYLINE.name(),
                                    "EVALUATIONS", 12, null)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.legal").value(true))
                    .andExpect(jsonPath("$.searchAlgorithm").value(algorithm.name()));
        }
    }

    @Test
    void supportsTimeBudgetWithAnExplicitProtocol() throws Exception {
        mvc.perform(post("/api/solve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("ILS", "SKYLINE", "TIME_MS", null, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.terminationReason").value("TIME_BUDGET_REACHED"));
    }

    @Test
    void validatesBudgetAndRejectsHistoricalAlgorithmAsSearchChoice() throws Exception {
        mvc.perform(post("/api/solve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("TABU_MOVE", "SKYLINE", "EVALUATIONS", 10, 10)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.budgetValid").exists());

        mvc.perform(post("/api/solve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("SKYLINE", "SKYLINE", "EVALUATIONS", 10, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_SEARCH_ALGORITHM"));

        mvc.perform(post("/api/solve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("TABU_MOVE", "SKYLINE", "EVALUATIONS", 5_000_001, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.maxEvaluations").exists());
    }

    @Test
    void reportsMalformedEnumsAndDoesNotExposeAStackTrace() throws Exception {
        String malformed = request("NOT_AN_ALGORITHM", "SKYLINE", "EVALUATIONS", 10, null);
        mvc.perform(post("/api/solve").contentType(MediaType.APPLICATION_JSON).content(malformed))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void previewsStartsAndListsAnExperimentTask() throws Exception {
        mvc.perform(post("/api/experiments/preview")
                        .contentType(MediaType.APPLICATION_JSON).content(experimentRequest(2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskCount").value(8))
                .andExpect(jsonPath("$.parallelism").value(2));

        String body = mvc.perform(post("/api/experiments")
                        .contentType(MediaType.APPLICATION_JSON).content(experimentRequest(2)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.totalTasks").value(8))
                .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(body);

        mvc.perform(get("/api/experiments/{id}", created.get("id").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("API 集成实验"));
        mvc.perform(get("/api/experiments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists());
    }

    /**
     * 取消与重试端点的契约：任务还在跑时都能成功受理；任务已结束时两者都必须被明确拒绝
     * （409 加错误码），而不是 500 或静默成功。小预算实验在测试里几乎总是瞬间跑完，
     * 因此这里先制造一个「确定已完成」的任务，再验证终态下的拒绝语义。
     */
    @Test
    void cancelsAndRetriesAnExperimentTask() throws Exception {
        String id = createExperiment(1);
        awaitExperimentTerminal(id);

        // 已完成的实验：取消会被拒（不是「运行中」），重试会被拒（没有缺口可补）。
        mvc.perform(post("/api/experiments/{id}/cancel", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXPERIMENT_NOT_RUNNING"));

        mvc.perform(post("/api/experiments/{id}/retry", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXPERIMENT_NOTHING_TO_RETRY"));

        // 不存在的任务：取消与重试都应报同一个 NOT_FOUND。
        mvc.perform(post("/api/experiments/{id}/cancel", "no-such-task"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EXPERIMENT_NOT_FOUND"));
        mvc.perform(post("/api/experiments/{id}/retry", "no-such-task"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EXPERIMENT_NOT_FOUND"));
    }

    /** 建一个实验并返回它的 id。 */
    private String createExperiment(int parallelism) throws Exception {
        String body = mvc.perform(post("/api/experiments")
                        .contentType(MediaType.APPLICATION_JSON).content(experimentRequest(parallelism)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    /** 轮询直到任务进入终态（COMPLETED / FAILED / CANCELLED）。 */
    private void awaitExperimentTerminal(String id) throws Exception {
        for (int attempt = 0; attempt < 200; attempt++) {
            String body = mvc.perform(get("/api/experiments/{id}", id))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            String status = objectMapper.readTree(body).get("status").asText();
            if (status.equals("COMPLETED") || status.equals("FAILED") || status.equals("CANCELLED")) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("实验未在预期时间内结束：" + id);
    }

    /**
     * 事件流的断线重连协议：连接必须带 {@code event:} 名与 {@code id:} 序号，
     * 且任务结束后连接会关闭。前端据此把「按序号补发」和「关闭即终态」两条逻辑接上。
     */
    @Test
    void streamsEventsWithNamesAndSequenceIds() throws Exception {
        String id = createExperiment(1);
        awaitExperimentTerminal(id);

        // 任务已经结束，因此订阅会立刻收到一次 progress 快照 + 一个名为 complete 的终止事件，
        // 随后服务端关闭流（异步请求随即完成）。这正好覆盖「快到来不及订阅」这条边界。
        MvcResult result = mvc.perform(get("/api/experiments/{id}/events", id)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andReturn();

        String stream = result.getResponse().getContentAsString();
        assertTrue(stream.contains("event:progress"), "应当先补发一份进度快照，实际=" + stream);
        assertTrue(stream.contains("event:complete"), "任务结束时必须补发终止事件，实际=" + stream);
        assertTrue(stream.contains("id:"), "事件必须带序号，重连补发才有依据，实际=" + stream);

        // 带 Last-Event-ID 的重连请求同样要能正常建立并拿到终止事件。
        MvcResult resumed = mvc.perform(get("/api/experiments/{id}/events", id)
                        .header("Last-Event-ID", "0")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(resumed.getResponse().getContentAsString().contains("event:complete"),
                "重连也必须以终止事件收尾");
    }

    @Test
    void timeBudgetExperimentRejectsParallelExecution() throws Exception {
        mvc.perform(post("/api/experiments/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(experimentRequest(2)
                                .replace("\"budgetMode\": \"EVALUATIONS\"", "\"budgetMode\": \"TIME_MS\"")
                                .replace("\"maxEvaluations\": 2", "\"maxEvaluations\": null")
                                .replace("\"maxMilliseconds\": null", "\"maxMilliseconds\": 2")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    /**
     * 产物端点与下载的安全边界走一遍完整的 HTTP 栈。
     *
     * <p>这里用的是真实求解链路，因此单格结果里带的是**真的**放置列表与收敛轨迹——
     * 适配器（Web 的扁平放置 → 核心的 {@code Placement}、算法名 → {@code AlgorithmId}）
     * 是不是真的能把一份实验转成可生成的产物，只有这条路能验证。
     */
    @Test
    void generatesAndDownloadsExperimentArtifactsWithoutLeakingTheTaskDirectory() throws Exception {
        String id = createExperiment(1);
        awaitExperimentTerminal(id);

        // 尚未生成时清单是空的，而不是 404：「还没生成」与「任务不存在」必须能分开。
        mvc.perform(get("/api/experiments/{id}/artifacts", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // 生成：用 POST 是因为它确实会写磁盘，GET 必须保持无副作用。
        String manifest = mvc.perform(post("/api/experiments/{id}/artifacts", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='report.html')]").exists())
                .andExpect(jsonPath("$[?(@.name=='runs.csv')]").exists())
                // report.html 里有一条指向元数据的链接；不补写这个文件就是死链。
                .andExpect(jsonPath("$[?(@.name=='experiment-metadata.properties')]").exists())
                .andReturn().getResponse().getContentAsString();
        assertTrue(manifest.contains("layouts/") || manifest.contains("convergence/"),
                "真实实验应当产出排样图或收敛图，实际清单=" + manifest);

        // 下载一个白名单内的产物：必须以附件形式下发，且内容非空。
        mvc.perform(get("/api/experiments/{id}/artifacts/{name}", id, "runs.csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(result -> assertTrue(
                        result.getResponse().getContentAsByteArray().length > 0, "产物内容不能为空"));

        // 越界尝试一律 404：任务目录里躺着 meta.bin、cells/ 与布局文件，它们必须不可下载。
        // 与「文件不存在」报同一个错误码，免得这个端点变成探测存储目录结构的工具。
        for (String name : new String[] {
                "meta.bin", "cells/0.bin", "cells/0.layout.bin", "../pom.xml", "artifacts/runs.csv"}) {
            mvc.perform(get("/api/experiments/{id}/artifacts/{name}", id, name))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("EXPERIMENT_ARTIFACT_NOT_FOUND"));
        }

        // 名字里带 `..` 却不构成越界（前导斜杠会先被剥掉）时，走的仍是「查不到这个名字」，
        // 而不是别的错误码——越界与不存在必须不可区分。
        mvc.perform(get("/api/experiments/{id}/artifacts/{name}", id, "/../pom.xml"))
                .andExpect(status().isNotFound());
    }

    /**
     * 嵌套名字在真实 HTTP 栈下也能下到位。
     *
     * <p>这条用例钉的是一个真实踩过的坑：端点最初写成 {@code {name:.+}}（单段通配），
     * {@code convergence/gcut1.svg} 这类名字压根不匹配，请求会掉进静态资源处理器，
     * 报出来的是一个莫名其妙的 404/500，而不是我们的错误码。控制器改用
     * {@code {*name}} 捕获剩余整段路径。
     */
    @Test
    void downloadsNestedArtifactNamesThroughTheRealHttpStack() throws Exception {
        String id = createExperiment(1);
        awaitExperimentTerminal(id);
        mvc.perform(post("/api/experiments/{id}/artifacts", id)).andExpect(status().isOk());

        mvc.perform(get("/api/experiments/{id}/artifacts/{name}", id, "convergence/gcut1.svg"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Type", containsString("image/svg+xml")))
                // 二级子目录（真实产物里确实会出现）也要能下到位。
                .andExpect(result -> assertTrue(
                        result.getResponse().getContentAsByteArray().length > 0, "产物内容不能为空"));

        mvc.perform(get("/api/experiments/{id}/artifacts/{name}", id, "layouts/gcut1/ils.svg"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("ils.svg")));
    }

    /**
     * 分析视图走一遍真实的 HTTP 栈。
     *
     * <p>前端的热力图 / 排名 / 箱线图 / 收敛对比四张图都只依赖这一个响应，因此这里钉的是
     * 「一个请求就能拿到画图所需的全部东西」——results 与 cells 逐位对齐、每格带轨迹、
     * 实例元数据齐备。任何一项缺失都会表现成图上的空白或张冠李戴，而不是一个报错。
     */
    @Test
    void servesAnalysisDataForChartsInOneRequest() throws Exception {
        String id = createExperiment(1);
        awaitExperimentTerminal(id);

        mvc.perform(get("/api/experiments/{id}/analysis", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                // 1 实例 × 2 搜索器 × 2 解码器 × 2 种子 = 8 格。
                .andExpect(jsonPath("$.totalTasks").value(8))
                .andExpect(jsonPath("$.completedTasks").value(8))
                .andExpect(jsonPath("$.results", hasSize(8)))
                .andExpect(jsonPath("$.cells", hasSize(8)))
                // 逐位对齐是图表联动的先决条件：cells[i] 必须描述 results[i]。
                .andExpect(jsonPath("$.cells[3].index").value(3))
                .andExpect(jsonPath("$.cells[3].instanceId").value("gcut1"))
                // 实例元数据是热力图的轴标签来源。
                .andExpect(jsonPath("$.instances.gcut1.id").value("gcut1"))
                .andExpect(jsonPath("$.instances.gcut1.itemCount").value(10));

        // 每格都要带上那条收敛轨迹，否则「收敛曲线对比」画不出来。
        String body = mvc.perform(get("/api/experiments/{id}/analysis", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode analysis = objectMapper.readTree(body);
        for (JsonNode cell : analysis.get("cells")) {
            assertTrue(cell.get("convergence").size() > 0,
                    "真实求解的每格都该带回收敛轨迹：" + cell);
            assertTrue(cell.get("convergence").get(0).has("step"), "轨迹点要带上评价步数");
        }

        // 未知任务仍要 404：分析不做终态校验，但存在性校验不能跟着一起放松。
        mvc.perform(get("/api/experiments/{id}/analysis", "no-such-task"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EXPERIMENT_NOT_FOUND"));
    }

    /** 任务还在跑时生成产物必须被明确拒绝，而不是给出一份注定残缺的报告。 */
    @Test
    void refusesArtifactGenerationForAnUnknownOrRunningExperiment() throws Exception {
        mvc.perform(post("/api/experiments/{id}/artifacts", "no-such-task"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EXPERIMENT_NOT_FOUND"));

        // 一个规模足够大、短时间跑不完的实验，用来稳定地拿到「运行中」这个状态。
        String id = createLargeTimeBudgetExperiment();
        mvc.perform(post("/api/experiments/{id}/artifacts", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXPERIMENT_NOT_COMPLETED"));
        mvc.perform(post("/api/experiments/{id}/cancel", id)).andExpect(status().isOk());
    }

    /**
     * 一个跑得足够久的实验：gcut11 有 1000 个零件，配合 30 秒墙钟预算，
     * 足以让「运行中」这个状态稳定存在，而不必和调度时序赛跑。
     */
    private String createLargeTimeBudgetExperiment() throws Exception {
        String body = mvc.perform(post("/api/experiments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "运行中的实验",
                                  "instanceIds": ["gcut11"],
                                  "searchAlgorithms": ["ILS"],
                                  "decoders": ["SKYLINE"],
                                  "seeds": [20261012],
                                  "rotationAllowed": true,
                                  "budgetMode": "TIME_MS",
                                  "maxEvaluations": null,
                                  "maxMilliseconds": 30000,
                                  "parallelism": 1
                                }
                                """))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private static String request(
            String algorithm, String decoder, String mode, Integer evaluations, Integer milliseconds) {
        String evaluationField = evaluations == null ? "null" : evaluations.toString();
        String timeField = milliseconds == null ? "null" : milliseconds.toString();
        return """
                {
                  "instanceId": "gcut1",
                  "searchAlgorithm": "%s",
                  "decoder": "%s",
                  "seed": 20261014,
                  "rotationAllowed": true,
                  "budgetMode": "%s",
                  "maxEvaluations": %s,
                  "maxMilliseconds": %s
                }
                """.formatted(algorithm, decoder, mode, evaluationField, timeField);
    }

    private static String experimentRequest(int parallelism) {
        return """
                {
                  "name": "API 集成实验",
                  "instanceIds": ["gcut1"],
                  "searchAlgorithms": ["TABU_MOVE", "ILS"],
                  "decoders": ["SKYLINE", "MAXRECTS"],
                  "seeds": [20261012, 20261013],
                  "rotationAllowed": true,
                  "budgetMode": "EVALUATIONS",
                  "maxEvaluations": 2,
                  "maxMilliseconds": null,
                  "parallelism": %d
                }
                """.formatted(parallelism);
    }
}
