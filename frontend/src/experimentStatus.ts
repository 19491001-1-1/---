import type { ExperimentTask } from '@/types/api'

export type ExperimentStatus = ExperimentTask['status']

export const EXPERIMENT_STATUS_LABEL: Record<ExperimentStatus, string> = {
  QUEUED: '等待中',
  RUNNING: '运行中',
  COMPLETED: '已完成',
  FAILED: '失败',
  CANCELLED: '已取消',
}

export const experimentStatusLabel = (status: ExperimentStatus) =>
  EXPERIMENT_STATUS_LABEL[status]
