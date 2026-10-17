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
  console.log(`新前端实验：${job.id}`)
})
