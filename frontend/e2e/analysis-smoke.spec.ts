import { expect, test } from '@playwright/test'

/**
 * 结果分析页的端到端冒烟。
 *
 * <p>分析页的四张图（热力图、排名表、箱线图、收敛对比）都是拿真实求解跑出来的数据画的，
 * 而这正是单测替身覆盖不到的地方：单测里 `fetch` 被换成一份手写的 JSON，
 * 「后端返回的字段名 / 数值口径与图表实际要的对不对得上」这条接缝恰好被绕过。
 * 因此这里不模拟后端，直接打真接口。
 *
 * <p>前置条件与实验冒烟相同：后端已在 8080 端口运行（见 README「端到端冒烟」）。
 * 用例自带一个任务：它先提交并跑完一个小实验，再进分析页——不依赖别的用例留下的数据，
 * 因为记录页里本来就可能有任意历史任务，选中哪一个不该由用例的先后顺序决定。
 */
test('分析页用真实结果画出四张图', async ({ page }) => {
  await page.goto('/experiments')
  await expect(page.locator('.choice-grid.instances button').first()).toBeVisible({ timeout: 30_000 })

  // 1 实例 × 2 搜索器 × 2 解码器 × 1 种子，秒级跑完；与分析页的取数口径一致。
  const matrix = page.locator('.matrix-block')
  for (const index of [0, 1, 2]) {
    const selected = matrix.nth(index).locator('button.selected')
    for (let remaining = await selected.count(); remaining > 0; remaining = await selected.count()) {
      await selected.first().click()
    }
  }
  await matrix.nth(0).locator('button').first().click()
  await matrix.nth(1).locator('button').first().click()
  await matrix.nth(1).locator('button').nth(1).click()
  await matrix.nth(2).locator('button').first().click()
  await matrix.nth(2).locator('button').nth(1).click()
  await page.locator('.experiment-sidebar .plain-field input').first().fill('20261015')
  await page.locator('.experiment-sidebar input[type="number"]').first().fill('50')

  await page.getByRole('button', { name: '启动实验' }).click()
  await expect(page.locator('.live-task .task-status')).toHaveText('已完成', { timeout: 150_000 })

  // 按 id 精确选中刚跑完的这个任务。
  // 不能按名字选：默认实验名只带日期（「对比实验 2026/10/15」），同一天建的多条完全同名。
  const created = await page.request.get('/api/experiments')
  const tasks: { id: string; status: string; completedTasks: number }[] = await created.json()
  const mine = tasks.find((task) => task.status === 'COMPLETED' && task.completedTasks === 4)

  await page.goto('/analysis')
  const toolbar = page.locator('.analysis-toolbar')
  await expect(toolbar).toBeVisible({ timeout: 30_000 })
  await toolbar.locator('select').first().selectOption(mine!.id)

  // 真实结果到位后才谈得上画图；等热力图的格子出现即说明 /analysis 的字段与图表对上了。
  const heat = page.locator('.analysis-card', { hasText: '利用率热力图' })
  await expect(heat.locator('.heat-cell').first()).toBeVisible({ timeout: 30_000 })
  // 2 搜索器 × 2 解码器 = 4 格，且每格都得真的有数（空格子会是 .empty）。
  await expect(heat.locator('.heat-cell')).toHaveCount(4)
  await expect(heat.locator('.heat-cell.empty')).toHaveCount(0)

  // 排名表：两个算法各有两行结果，利用率一栏应当是百分比而不是「—」。
  const rank = page.locator('.analysis-card', { hasText: '算法排名' }).locator('.rank-table tbody tr')
  await expect(rank).toHaveCount(2)
  await expect(rank.first().locator('td').nth(3)).toContainText('%')

  // 箱线图每个算法一行，收敛对比拿到一条能画的曲线（SVG 由 echarts 渲染）。
  await expect(page.locator('.boxplot-row')).toHaveCount(2)
  await expect(page.locator('.convergence-canvas svg')).toBeVisible({ timeout: 30_000 })
})
