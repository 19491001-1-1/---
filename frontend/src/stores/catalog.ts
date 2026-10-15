import { defineStore } from 'pinia'
import { api, ApiClientError } from '@/api/client'
import type { Meta, PackingDecoder, PackingInstanceSummary, SearchAlgorithm } from '@/types/api'

interface CatalogState {
  meta: Meta | null
  searchAlgorithms: SearchAlgorithm[]
  decoders: PackingDecoder[]
  instances: PackingInstanceSummary[]
  loading: boolean
  loaded: boolean
  error: string | null
}

export const useCatalogStore = defineStore('catalog', {
  state: (): CatalogState => ({
    meta: null,
    searchAlgorithms: [],
    decoders: [],
    instances: [],
    loading: false,
    loaded: false,
    error: null,
  }),
  actions: {
    async load(force = false) {
      if ((this.loaded && !force) || this.loading) return
      this.loading = true
      this.error = null
      try {
        const [meta, searchAlgorithms, decoders, instances] = await Promise.all([
          api.meta(), api.searchAlgorithms(), api.decoders(), api.instances(),
        ])
        this.meta = meta
        this.searchAlgorithms = searchAlgorithms
        this.decoders = decoders
        this.instances = instances
        this.loaded = true
      } catch (error: unknown) {
        this.error = error instanceof ApiClientError ? error.message : '加载平台数据时发生未知错误'
      } finally {
        this.loading = false
      }
    },
  },
})
