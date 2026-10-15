import { computed, reactive } from 'vue'
import type { BudgetMode, SolveRequest } from '@/types/api'

export interface SolveFormState {
  instanceId: string
  searchAlgorithm: string
  decoder: string
  seed: number
  rotationAllowed: boolean
  budgetMode: BudgetMode
  maxEvaluations: number
  maxMilliseconds: number
}

export const defaultSolveForm = (): SolveFormState => ({
  instanceId: '',
  searchAlgorithm: 'ADAPTIVE_GA',
  decoder: 'SKYLINE',
  seed: 20261012,
  rotationAllowed: true,
  budgetMode: 'EVALUATIONS',
  maxEvaluations: 20_000,
  maxMilliseconds: 2_000,
})

export function buildSolveRequest(form: SolveFormState): SolveRequest {
  return {
    instanceId: form.instanceId,
    searchAlgorithm: form.searchAlgorithm,
    decoder: form.decoder,
    seed: form.seed,
    rotationAllowed: form.rotationAllowed,
    budgetMode: form.budgetMode,
    maxEvaluations: form.budgetMode === 'EVALUATIONS' ? form.maxEvaluations : null,
    maxMilliseconds: form.budgetMode === 'TIME_MS' ? form.maxMilliseconds : null,
  }
}

export function useSolveForm() {
  const form = reactive(defaultSolveForm())
  const valid = computed(() => {
    if (!form.instanceId || !form.searchAlgorithm || !form.decoder || !Number.isSafeInteger(form.seed)) return false
    return form.budgetMode === 'EVALUATIONS'
      ? form.maxEvaluations > 0 && form.maxEvaluations <= 5_000_000
      : form.maxMilliseconds > 0 && form.maxMilliseconds <= 300_000
  })
  const reset = (instanceId = '') => Object.assign(form, defaultSolveForm(), { instanceId })
  return { form, valid, reset, buildRequest: () => buildSolveRequest(form) }
}
