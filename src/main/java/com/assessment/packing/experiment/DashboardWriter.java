package com.assessment.packing.experiment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class DashboardWriter {
    private DashboardWriter() {}

    static void write(Path output) throws IOException {
        Files.createDirectories(output);
        String html = """
                <!doctype html><html lang="zh-CN"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>二维矩形排样完整实验</title><style>
                body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;background:#f1f5f9;color:#0f172a;margin:0}
                main{max-width:980px;margin:auto;padding:40px}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:18px}
                a{display:block;background:#fff;border-radius:14px;padding:24px;color:#1d4ed8;text-decoration:none;box-shadow:0 5px 20px #0f172a14}
                a:hover{transform:translateY(-2px)}h2{color:#0f172a}p{color:#475569;line-height:1.6}</style></head><body><main>
                <h1>二维矩形排样完整实验</h1><p>这里汇集默认参数、多随机种子、公平评价预算、公平时间预算和参数敏感性实验。每个子报告均包含原始CSV、统计汇总、排样图和收敛曲线。</p>
                <div class="grid">
                <a href="benchmark/report.html"><h2>默认参数总览</h2><p>全部构造算法与元启发式算法。</p></a>
                <a href="fair-evaluations/report.html"><h2>相同评价预算</h2><p>用候选解评价次数控制主要计算量。</p></a>
                <a href="fair-time/report.html"><h2>相同时间预算</h2><p>在同一台机器上控制墙钟搜索时间。</p></a>
                <a href="parameter-sweep/report.html"><h2>参数敏感性</h2><p>SA、Tabu、ACO、ILS、GA、自适应GA和混合算法的单因素扫描。</p></a>
                </div></main></body></html>
                """;
        Files.writeString(output.resolve("index.html"), html, StandardCharsets.UTF_8);
    }
}
