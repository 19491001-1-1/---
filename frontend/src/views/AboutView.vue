<script setup lang="ts">
import { useCatalogStore } from '@/stores/catalog'
const catalog = useCatalogStore()
</script>

<template>
  <div class="about-page">
    <header class="archive-hero"><div><p class="section-kicker">工程说明</p><h2>从问题实例到可验证结果。</h2></div><p>项目用一条清晰的边界保护算法行为：Web 层只适配，求解、验证和实验框架仍只有一份实现。</p></header>
    <section class="architecture-flow">
      <article><span>INPUT</span><strong>Instance / Item</strong><p>12 组 GCUT 基准实例，后端白名单读取。</p></article>
      <i>→</i><article><span>SEARCH</span><strong>PackingAlgorithm</strong><p>6 种产品搜索器优化物品优先级。</p></article>
      <i>→</i><article><span>DECODE</span><strong>PackingDecoder</strong><p>8 种解码器把顺序转换成几何布局。</p></article>
      <i>→</i><article><span>VERIFY</span><strong>SolutionValidator</strong><p>独立检查边界、重叠、身份和旋转权限。</p></article>
    </section>
    <section class="engineering-facts">
      <div><strong>178</strong><span>自动化测试</span><small>当前完整验证</small></div>
      <div><strong>80 / 70</strong><span>覆盖率门禁</span><small>行 / 分支（%）</small></div>
      <div><strong>2.03–2.17×</strong><span>ACO 实测提升</span><small>多 fork 同口径复测</small></div>
      <div><strong>1.58×</strong><span>MaxRects 吞吐</span><small>分配量降低 63.6%</small></div>
    </section>
    <section class="version-line"><span>平台版本</span><strong>{{ catalog.meta?.version ?? '—' }}</strong><span>Java {{ catalog.meta?.javaVersion ?? '17+' }}</span></section>
  </div>
</template>
