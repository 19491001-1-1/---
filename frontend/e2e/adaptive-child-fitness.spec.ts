import { expect, test } from '@playwright/test'

test('自适应GA展示子代评分规则并完成真实求解', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await page.goto('/solve?search=ADAPTIVE_GA')
  await expect(page.getByTestId('adaptive-mutation-rule')).toContainText('子代自身')
  await expect(page.getByTestId('adaptive-mutation-rule')).toContainText('父代种群')
  const solve = page.waitForResponse(response => response.url().endsWith('/api/solve') && response.request().method() === 'POST')
  await page.getByRole('button', { name: /开始.*求解|开始运行|运行求解/ }).click()
  const response = await solve
  expect(response.ok()).toBe(true)
  const result = await response.json()
  expect(result.searchAlgorithm).toBe('ADAPTIVE_GA')
  expect(result.legal).toBe(true)
  expect(result.evaluations).toBeLessThanOrEqual(20000)
  await page.goto('/algorithms')
  const entry = page.locator('.algorithm-entry').filter({ hasText: 'Adaptive Genetic Algorithm' })
  await entry.locator('summary').click()
  await expect(entry.locator('.algorithm-guide')).toContainText('变异前子代自身')
  expect(errors).toEqual([])
  await page.screenshot({ path: '/private/tmp/adaptive-child-fitness-desktop.png', fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/solve?search=ADAPTIVE_GA')
  await expect(page.getByTestId('adaptive-mutation-rule')).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: '/private/tmp/adaptive-child-fitness-mobile.png', fullPage: true })
})

test('子代评分版可生成新的前端实验CSV与版本元数据', async ({ request }) => {
  const response = await request.post('/api/experiments', { data: {
    name: '自适应GA子代评分版复核', instanceIds: Array.from({ length: 12 }, (_, i) => `gcut${i + 1}`),
    searchAlgorithms: ['ADAPTIVE_GA'], decoders: ['SKYLINE'], seeds: [20261012, 20365741, 20470470],
    rotationAllowed: true, budgetMode: 'EVALUATIONS', maxEvaluations: 20000,
    maxMilliseconds: null, parallelism: 1,
  } })
  expect(response.ok()).toBe(true)
  const job = await response.json()
  await expect.poll(async () => (await (await request.get(`/api/experiments/${job.id}`)).json()).status,
    { timeout: 120000 }).toBe('COMPLETED')
  const generated = await request.post(`/api/experiments/${job.id}/artifacts`)
  expect(generated.ok()).toBe(true)
  const files = await generated.json()
  expect(files.some((file: { name: string }) => file.name.endsWith('runs.csv'))).toBe(true)
  const metadata = files.find((file: { name: string }) => file.name.endsWith('experiment-metadata.properties'))
  expect(metadata).toBeDefined()
  const downloaded = await request.get(`/api/experiments/${job.id}/artifacts/${metadata.name}`)
  expect(await downloaded.text()).toContain('adaptiveGaMutationFitness=PRE_MUTATION_CHILD')
  const removed = await request.delete(`/api/experiments/${job.id}`)
  expect(removed.status()).toBe(204)
  expect((await request.get(`/api/experiments/${job.id}`)).status()).toBe(404)
  console.log(`新前端实验：${job.id}`)
})

test('记录页可确认并删除已结束实验', async ({ page, request }) => {
  const name = `删除验收-${Date.now()}`
  const created = await request.post('/api/experiments', { data: {
    name, instanceIds: ['gcut1'], searchAlgorithms: ['ILS'], decoders: ['SKYLINE'], seeds: [20261017],
    rotationAllowed: true, budgetMode: 'EVALUATIONS', maxEvaluations: 20,
    maxMilliseconds: null, parallelism: 1,
  } })
  expect(created.ok()).toBe(true)
  const job = await created.json()
  await expect.poll(async () => (await (await request.get(`/api/experiments/${job.id}`)).json()).status,
    { timeout: 30_000 }).toBe('COMPLETED')

  await page.goto('/records')
  const row = page.locator('.records-table > button').filter({ hasText: name })
  await expect(row).toBeVisible()
  await row.click()
  await expect(page.getByRole('button', { name: '删除记录' })).toBeVisible()
  await page.screenshot({ path: '/private/tmp/experiment-delete-before.png', fullPage: false })
  page.once('dialog', dialog => dialog.accept())
  await page.getByRole('button', { name: '删除记录' }).click()
  await expect(row).toHaveCount(0)
  await expect(page.getByText('实验记录已删除')).toBeVisible()
  await page.screenshot({ path: '/private/tmp/experiment-delete-after.png', fullPage: false })
})
