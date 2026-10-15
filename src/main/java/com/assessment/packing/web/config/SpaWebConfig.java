package com.assessment.packing.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * 单 jar 部署时把前端产物当作静态资源托管，并为前端路由提供回退。
 *
 * <p>Vue Router 使用 history 模式，用户直接打开或刷新 {@code /records} 这类前端路由时，
 * 服务端收到的是一个磁盘上并不存在的路径。若不管它，请求会落到静态资源处理器并抛
 * {@code NoResourceFoundException}，再被兜底的 {@code GlobalExceptionHandler} 变成 500——
 * 单 jar 交付物一刷新就碎。这里把「找不到的静态路径」回退到 {@code index.html}，交给前端路由。
 *
 * <p><b>回退必须限定在非 /api 路径。</b>否则 {@code /api/nonsense} 也会被吐成 index.html，
 * 既掩盖了真实的错误，也会破坏既有对未知 API 路径的断言。带扩展名（末段含点）的缺失资源
 * 同样不回退：那通常是打错的 js/css 地址，返回 HTML 只会让浏览器报出更难懂的错误。
 *
 * <p>没有打包前端时（默认构建）classpath 下不存在 {@code static/index.html}，本配置退化为无操作。
 */
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 先于 Spring Boot 的静态资源自动配置注册 /**：Boot 的注册器发现已有 /** 映射后
        // 会跳过自己那一条，因此行为是确定的，不依赖两个 configurer 谁先执行。
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new SpaIndexResourceResolver());
    }

    /**
     * 继承 {@link PathResourceResolver} 而不是直接实现 {@code ResourceResolver}：
     * 链尾是否会自动追加一个 {@code PathResourceResolver} 取决于 webjars 等开关，
     * 继承之后「按真实路径解析」这一步始终由本类完成，不依赖链尾兜底。
     */
    static final class SpaIndexResourceResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            if (!resourcePath.isEmpty()) {
                Resource requested = super.getResource(resourcePath, location);
                if (requested != null) {
                    return requested;
                }
            }
            if (!shouldFallBack(resourcePath)) {
                return null;
            }
            Resource index = location.createRelative("index.html");
            return index.exists() ? index : null;
        }

        /** 只有「看起来像前端路由」的路径才回退，其余保持原样交给异常链路。 */
        private static boolean shouldFallBack(String resourcePath) {
            if (resourcePath.equals("api") || resourcePath.startsWith("api/")) {
                return false;
            }
            int lastSlash = resourcePath.lastIndexOf('/');
            String tail = lastSlash < 0 ? resourcePath : resourcePath.substring(lastSlash + 1);
            return !tail.contains(".");
        }
    }
}
