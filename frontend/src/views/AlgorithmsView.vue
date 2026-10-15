<script setup lang="ts">
import { ref } from 'vue'
import StatePanel from '@/components/shared/StatePanel.vue'
import { useCatalogStore } from '@/stores/catalog'

const catalog = useCatalogStore()
const tab = ref<'search' | 'decoder' | 'history'>('search')
</script>

<template>
  <div class="archive-page">
    <header class="archive-hero">
      <div><p class="section-kicker">能力档案 · 入口收敛</p><h2>算法深度，不等于按钮数量。</h2></div>
      <p>项目完成系统推演后，依据差异度、维护成本和产品价值收敛展示入口；实现和验证痕迹仍然保留。</p>
    </header>
    <StatePanel v-if="catalog.error" tone="error" title="算法目录加载失败" :description="catalog.error" action-label="重试" @action="catalog.load(true)" />
    <div class="archive-tabs">
      <button :class="{ active: tab === 'search' }" @click="tab = 'search'">搜索优化器 <span>6</span></button>
      <button :class="{ active: tab === 'decoder' }" @click="tab = 'decoder'">排样解码器 <span>8</span></button>
      <button :class="{ active: tab === 'history' }" @click="tab = 'history'">历史与高级变体 <span>46</span></button>
    </div>
    <div v-if="tab === 'search'" class="algorithm-grid">
      <article v-for="(item, index) in catalog.searchAlgorithms" :key="item.machineValue" class="algorithm-entry">
        <header><span>{{ String(index + 1).padStart(2, '0') }}</span><i :class="{ stochastic: item.stochastic }">{{ item.stochastic ? '随机' : '确定' }}</i></header>
        <h3>{{ item.chineseName }}</h3><small>{{ item.englishName }}</small><p>{{ item.description }}</p>
        <footer><code>{{ item.machineValue }}</code><RouterLink :to="`/solve?search=${item.machineValue}`">用于求解 →</RouterLink></footer>
      </article>
    </div>
    <div v-else-if="tab === 'decoder'" class="algorithm-grid decoder-grid">
      <article v-for="(item, index) in catalog.decoders" :key="item.machineValue" class="algorithm-entry">
        <header><span>{{ String(index + 1).padStart(2, '0') }}</span><i>支持旋转</i></header>
        <h3>{{ item.chineseName }}</h3><small>{{ item.englishName }}</small><p>{{ item.description }}</p>
        <div class="structure-line"><span>空间结构</span><strong>{{ item.spaceStructure }}</strong></div>
        <footer><code>{{ item.machineValue }}</code><RouterLink :to="`/solve?decoder=${item.machineValue}`">用于求解 →</RouterLink></footer>
      </article>
    </div>
    <section v-else class="history-map">
      <div class="family-root"><span>核心模型</span><strong>算法家族</strong><small>46 个稳定入口</small></div>
      <div class="family-branches">
        <article><span>PRIMARY</span><strong>产品代表</strong><b>6 + 8</b><p>差异清晰、适合公平实验和主界面选择。</p></article>
        <article><span>ADVANCED</span><strong>高级变体</strong><b>32</b><p>Shelf、Skyline、MaxRects、Tabu 和 GA 的消融对照。</p></article>
        <article><span>ARCHIVE</span><strong>参数推演</strong><b>72</b><p>Guillotine 的选择、切割与合并策略组合，仅作验证留痕。</p></article>
      </div>
    </section>
  </div>
</template>
