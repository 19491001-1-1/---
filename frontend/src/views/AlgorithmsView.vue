<script setup lang="ts">
import { computed, ref } from 'vue'
import StatePanel from '@/components/shared/StatePanel.vue'
import { useCatalogStore } from '@/stores/catalog'

const catalog = useCatalogStore()
const tab = ref<'search' | 'decoder' | 'history'>('search')
const historyQuery = ref('')
const activeFamily = ref('全部')

const families = computed(() => ['全部', ...new Set(catalog.advancedAlgorithms.map(item => item.family))])
const visibleAdvancedAlgorithms = computed(() => {
  const query = historyQuery.value.trim().toLocaleLowerCase()
  return catalog.advancedAlgorithms.filter(item => {
    const inFamily = activeFamily.value === '全部' || item.family === activeFamily.value
    const searchable = `${item.displayName} ${item.machineValue} ${item.family} ${item.representativeName} ${item.idea}`.toLocaleLowerCase()
    return inFamily && (!query || searchable.includes(query))
  })
})
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
        <details class="algorithm-guide"><summary><span>详细解析</span><i>展开</i></summary><p>{{ item.guide }}</p></details>
        <footer><code>{{ item.machineValue }}</code><RouterLink :to="`/solve?search=${item.machineValue}`">用于求解 →</RouterLink></footer>
      </article>
    </div>
    <div v-else-if="tab === 'decoder'" class="algorithm-grid decoder-grid">
      <article v-for="(item, index) in catalog.decoders" :key="item.machineValue" class="algorithm-entry">
        <header><span>{{ String(index + 1).padStart(2, '0') }}</span><i>支持旋转</i></header>
        <h3>{{ item.chineseName }}</h3><small>{{ item.englishName }}</small><p>{{ item.description }}</p>
        <div class="structure-line"><span>空间结构</span><strong>{{ item.spaceStructure }}</strong></div>
        <details class="algorithm-guide"><summary><span>详细解析</span><i>展开</i></summary><p>{{ item.guide }}</p></details>
        <footer><code>{{ item.machineValue }}</code><RouterLink :to="`/solve?decoder=${item.machineValue}`">用于求解 →</RouterLink></footer>
      </article>
    </div>
    <section v-else class="history-archive">
      <header class="history-summary">
        <div><span>ADVANCED ARCHIVE</span><strong>32 个历史与高级变体</strong><p>按算法家族查看每个变体的核心思想、策略差异与当前代表入口。</p></div>
        <dl><div><dt>产品代表</dt><dd>14</dd></div><div><dt>高级变体</dt><dd>{{ catalog.advancedAlgorithms.length }}</dd></div><div><dt>参数验证</dt><dd>72</dd></div></dl>
      </header>
      <div class="history-toolbar">
        <label><span>搜索档案</span><input v-model="historyQuery" type="search" placeholder="名称、标识、家族或算法思想" /></label>
        <div class="family-filter" aria-label="算法家族筛选">
          <button v-for="family in families" :key="family" :class="{ active: activeFamily === family }" @click="activeFamily = family">{{ family }}</button>
        </div>
      </div>
      <p class="history-result-count">显示 {{ visibleAdvancedAlgorithms.length }} / {{ catalog.advancedAlgorithms.length }} 个变体</p>
      <div v-if="visibleAdvancedAlgorithms.length" class="advanced-grid">
        <article v-for="(item, index) in visibleAdvancedAlgorithms" :key="item.machineValue" class="advanced-entry">
          <header><span>{{ item.family }}</span><i :class="{ stochastic: item.stochastic }">{{ item.stochastic ? '随机搜索' : '确定性构造' }}</i></header>
          <div class="advanced-title"><b>{{ String(index + 1).padStart(2, '0') }}</b><h3>{{ item.displayName }}</h3></div>
          <p>{{ item.idea }}</p>
          <footer><span>当前代表</span><strong>{{ item.representativeName }}</strong></footer>
        </article>
      </div>
      <StatePanel v-else title="没有匹配的高级变体" description="请尝试更换算法家族或搜索关键词。" />
    </section>
  </div>
</template>
