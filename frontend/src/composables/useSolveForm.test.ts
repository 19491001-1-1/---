import { describe, expect, it } from 'vitest'
import { buildSolveRequest, defaultSolveForm, useSolveForm } from './useSolveForm'

describe('buildSolveRequest', () => {
  it('评价预算模式下只送评价次数，时间字段置空', () => {
    const request = buildSolveRequest({ ...defaultSolveForm(), instanceId: 'gcut1' })

    expect(request.maxEvaluations).toBe(20_000)
    // 预算模式是个二选一：两个都送会让后端的校验规则无法判断用户到底想要哪个。
    expect(request.maxMilliseconds).toBeNull()
  })

  it('时间预算模式下反过来，只送毫秒数', () => {
    const request = buildSolveRequest({
      ...defaultSolveForm(), instanceId: 'gcut1', budgetMode: 'TIME_MS', maxMilliseconds: 1_500,
    })

    expect(request.maxEvaluations).toBeNull()
    expect(request.maxMilliseconds).toBe(1_500)
  })
})

describe('useSolveForm', () => {
  it('默认表单缺少实例时不可提交', () => {
    const { valid } = useSolveForm()
    // 实例是唯一没有默认值的必填项：没有它求解无从谈起。
    expect(valid.value).toBe(false)
  })

  it('补齐实例后即可提交', () => {
    const { form, valid } = useSolveForm()
    form.instanceId = 'gcut1'
    expect(valid.value).toBe(true)
  })

  it('评价次数越界时拒绝提交', () => {
    const { form, valid } = useSolveForm()
    form.instanceId = 'gcut1'

    form.maxEvaluations = 0
    expect(valid.value, '0 次评价等于什么都不算').toBe(false)
    form.maxEvaluations = 5_000_001
    expect(valid.value, '超过上限应当被前端拦下，而不是让后端报 400').toBe(false)
    form.maxEvaluations = 5_000_000
    expect(valid.value).toBe(true)
  })

  it('时间预算同样卡上下界', () => {
    const { form, valid } = useSolveForm()
    form.instanceId = 'gcut1'
    form.budgetMode = 'TIME_MS'

    form.maxMilliseconds = 0
    expect(valid.value).toBe(false)
    form.maxMilliseconds = 300_001
    expect(valid.value).toBe(false)
    form.maxMilliseconds = 300_000
    expect(valid.value).toBe(true)
  })

  it('种子必须是安全整数', () => {
    const { form, valid } = useSolveForm()
    form.instanceId = 'gcut1'

    form.seed = 1.5
    expect(valid.value).toBe(false)
    form.seed = Number.MAX_SAFE_INTEGER + 1
    expect(valid.value).toBe(false)
    form.seed = 20261014
    expect(valid.value).toBe(true)
  })

  it('复位会把预算等字段收回默认值，只保留传入的实例', () => {
    const { form, reset } = useSolveForm()
    form.instanceId = 'gcut1'
    form.budgetMode = 'TIME_MS'
    form.maxMilliseconds = 999
    form.searchAlgorithm = 'TABU_MOVE'

    reset('gcut2')

    expect(form.instanceId).toBe('gcut2')
    expect(form.budgetMode).toBe('EVALUATIONS')
    expect(form.searchAlgorithm).toBe('ADAPTIVE_GA')
    expect(form.maxEvaluations).toBe(20_000)
  })

  it('buildRequest 读取的是当前表单，而不是构造时的快照', () => {
    const { form, buildRequest } = useSolveForm()
    form.instanceId = 'gcut3'
    form.decoder = 'MAXRECTS'

    expect(buildRequest()).toMatchObject({ instanceId: 'gcut3', decoder: 'MAXRECTS' })
  })
})
