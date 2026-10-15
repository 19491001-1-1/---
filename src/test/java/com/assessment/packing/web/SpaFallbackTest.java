package com.assessment.packing.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 单 jar 部署下的前端回退。夹具 {@code src/test/resources/static/index.html} 只在测试
 * classpath 上，因此默认构建不会真的打包前端，但回退逻辑本身能被完整验证。
 *
 * <p>这个类顺带钉住一条边界：回退**不能**吞掉 /api 路径，也不能把打错的静态资源地址
 * 变成 HTML。
 */
@SpringBootTest(properties = "packing.experiment-store=${java.io.tmpdir}/packing-spa-fallback-test-store")
@AutoConfigureMockMvc
final class SpaFallbackTest {

    private static final String INDEX_MARKER = "spa-fallback-fixture";

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("前端深链直接打开时回退到 index.html，而不是 500")
    void deepFrontendRouteServesIndex() throws Exception {
        mvc.perform(get("/records"))
                .andExpect(status().isOk());
        assertTrue(body("/records").contains(INDEX_MARKER),
                "history 模式的前端路由在单 jar 下必须由 index.html 承接，否则刷新即 500");
    }

    @Test
    @DisplayName("根路径由 Spring Boot 的欢迎页接管并转发到 index.html")
    void rootPathIsForwardedToIndexByWelcomePage() throws Exception {
        // classpath:/static/index.html 存在时，Boot 的 WelcomePageHandlerMapping 会接管 `/`，
        // 优先级高于本类的资源处理器。它返回 200 并「转发」到 index.html——MockMvc 不执行
        // 转发（转发是容器的职责），所以这里断言转发目标而不是响应体。真实容器里转发会执行。
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));
    }

    @Test
    @DisplayName("嵌套前端路由回退到 SPA 外壳")
    void nestedFrontendRouteServesIndex() throws Exception {
        assertTrue(body("/experiments/42").contains(INDEX_MARKER));
    }

    @Test
    @DisplayName("真实存在的静态资源按原样下发，不走回退")
    void existingStaticResourceIsServedAsIs() throws Exception {
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    @DisplayName("未知的 /api 路径不被回退吞掉——回退不得改变既有 API 行为")
    void unknownApiPathIsNotSwallowedBySpaFallback() throws Exception {
        MvcResult result = mvc.perform(get("/api/nonsense"))
                .andExpect(status().isNotFound())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("RESOURCE_NOT_FOUND"),
                "未知 API 路径应返回稳定的 404 错误码");
        assertFalse(body.contains(INDEX_MARKER),
                "未知 API 路径不能拿到 index.html，否则真实错误被伪装成前端页面");
    }

    @Test
    @DisplayName("带扩展名的缺失静态资源不吐 HTML，避免把打错的 js/css 地址变成 200 页面")
    void missingAssetWithExtensionIsNotTurnedIntoHtml() throws Exception {
        MvcResult result = mvc.perform(get("/assets/app.js"))
                .andExpect(status().isNotFound())
                .andReturn();
        assertFalse(result.getResponse().getContentAsString().contains(INDEX_MARKER),
                "缺失的 js 地址不应被回退成 index.html");
    }

    private String body(String path) throws Exception {
        return mvc.perform(get(path)).andReturn().getResponse().getContentAsString();
    }
}
