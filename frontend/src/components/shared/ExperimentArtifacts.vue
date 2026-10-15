<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { api, ApiClientError, saveArtifact } from '@/api/client'
import StatePanel from '@/components/shared/StatePanel.vue'
import type { ExperimentArtifact } from '@/types/api'

const props = defineProps<{
  /** 目标实验任务 id。为 null 时组件不请求任何东西。 */
  taskId: string | null
  /** 任务是否已经结束：只有终态任务才允许生成产物，后端也会再拒一次。 */
  terminal: boolean
  /** 任务结束后是否有结果可生成，用来决定提示文案。 */
  hasResults?: boolean
}>()

const artifacts = ref<ExperimentArtifact[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const downloading = ref<string | null>(null)

const sizeText = (bytes: number) => {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

/** 只列清单，不生成——GET 必须保持无副作用，否则挂载即触发一次全量重写。 */
async function load() {
  if (!props.taskId) { artifacts.value = []; return }
  loading.value = true; error.value = null
  try { artifacts.value = await api.experimentArtifacts(props.taskId) }
  catch (reason) { error.value = reason instanceof ApiClientError ? reason.message : '无法读取产物清单' }
  finally { loading.value = false }
}

async function generate() {
  if (!props.taskId) return
  loading.value = true; error.value = null
  try {
    artifacts.value = await api.generateExperimentArtifacts(props.taskId)
    ElMessage.success(`已生成 ${artifacts.value.length} 个产物`)
  } catch (reason) { error.value = reason instanceof ApiClientError ? reason.message : '无法生成产物' }
  finally { loading.value = false }
}

async function download(artifact: ExperimentArtifact) {
  if (!props.taskId) return
  downloading.value = artifact.name; error.value = null
  try {
    saveArtifact(await api.downloadExperimentArtifact(props.taskId, artifact.name))
  } catch (reason) { error.value = reason instanceof ApiClientError ? reason.message : '产物下载失败' }
  finally { downloading.value = null }
}

// 换任务就把上一个任务的清单清掉：否则用户点开 B 的第一眼看到的是 A 的产物，
// 而 B 可能压根还没生成过任何东西。
watch(() => props.taskId, (id) => { artifacts.value = []; downloading.value = null; error.value = null; if (id) void load() },
  { immediate: true })

const idleHint = computed(() => props.hasResults === false
  ? '这个实验没有产出任何结果，生成产物只会得到一份空报告。'
  : (props.terminal ? '还没生成过产物。' : '任务结束后即可生成 CSV / HTML / SVG 产物。'))
</script>

<template>
  <section class="experiment-artifacts">
    <header>
      <p class="section-kicker">产物</p>
      <span v-if="artifacts.length">{{ artifacts.length }} 个文件</span>
    </header>
    <StatePanel v-if="error" tone="error" title="产物操作失败" :description="error" />
    <p v-else-if="!artifacts.length" class="artifact-hint">{{ idleHint }}</p>
    <!-- 清单里的 name 可能带子目录，下载键就是它本身：服务端只放行清单内的名字。 -->
    <ul v-else class="artifact-list">
      <li v-for="artifact in artifacts" :key="artifact.name">
        <span><strong>{{ artifact.name }}</strong><small>{{ sizeText(artifact.sizeBytes) }}</small></span>
        <button type="button" :disabled="downloading === artifact.name" @click="download(artifact)">
          {{ downloading === artifact.name ? '下载中' : '下载' }}
        </button>
      </li>
    </ul>
    <div class="artifact-actions">
      <button type="button" :disabled="!terminal || loading || !taskId" @click="generate">
        {{ loading ? '处理中' : (artifacts.length ? '重新生成产物' : '生成产物') }}
      </button>
      <button type="button" :disabled="loading || !taskId" @click="load">刷新清单</button>
    </div>
  </section>
</template>
