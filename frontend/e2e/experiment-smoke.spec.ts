import { expect, test, type Locator } from '@playwright/test'

/**
 * 端到端冒烟：起一个真实后端 + 真实前端，跑完一个 2×2 的对比实验，等进度到 100%。
 *
 * <p>这条用例存在的意义不是覆盖业务分支，而是钉住那些只在「真浏览器 + 真后端」下
 * 才会暴露的接缝：SSE 事件流的建立与收尾、前端表单与后端校验的对齐、
 * 跨模块的字段名一致性。单测把 fetch 换成替身时，这些接缝恰好全部被绕过了。
 *
 * <p>前置条件：后端已在 8080 端口运行（见 README「端到端冒烟」）。后端没起时
 * 用例会直接失败而不是被跳过——静默跳过等于这条接缝没人守。
 */

/** 用最小的矩阵：1 实例 × 2 搜索器 × 2 解码器 × 1 种子 = 4 个格子，秒级跑完。 */
test('2×2 实验能跑到 100% 并给出合法结果', async ({ page }) => {
  await page.goto('/experiments')

  // 目录数据要先到位，否则下面按名字点选会点到空列表上。
  await expect(page.locator('.choice-grid.instances button').first()).toBeVisible({ timeout: 30_000 })

  // 表单默认会预选若干项，先全部取消，再精确挑出这 2×2，避免默认值变化让用例漂移。
  const matrix = page.locator('.matrix-block')
  await deselectAll(matrix.nth(0).locator('button.selected'))
  await deselectAll(matrix.nth(1).locator('button.selected'))
  await deselectAll(matrix.nth(2).locator('button.selected'))

  await matrix.nth(0).locator('button').first().click()
  await matrix.nth(1).locator('button').first().click()
  await matrix.nth(1).locator('button').nth(1).click()
  await matrix.nth(2).locator('button').first().click()
  await matrix.nth(2).locator('button').nth(1).click()

  // 只留一个种子，且预算压到极小——冒烟用例不该在 CI 里跑几十秒。
  await page.locator('.experiment-sidebar .plain-field input').first().fill('20261012')
  await page.locator('.experiment-sidebar input[type="number"]').first().fill('50')

  await expect(page.locator('.task-equation strong')).toHaveText('4 个任务')

  // 预览必须通过后端校验：这里把「前端算出来的规模」和「后端认的规模」对一次。
  await page.getByRole('button', { name: '预览规模' }).click()
  await expect(page.locator('.preview-result')).toBeVisible()

  await page.getByRole('button', { name: '启动实验' }).click()

  const live = page.locator('.live-task')
  await expect(live).toBeVisible()
  await expect(live.locator('.task-status')).toHaveText('已完成', { timeout: 150_000 })

  // 进度条走满 + 计数一致：SSE 断线补发如果丢事件，这里就对不上。
  await expect(live.locator('.progress-facts strong')).toHaveText('4 / 4')
  await expect(live.locator('.live-metrics strong').first()).toHaveText('4')

  // 终态任务可以生成产物，并能在页面上看到清单与下载入口。
  const artifacts = live.locator('.experiment-artifacts')
  await artifacts.locator('.artifact-actions button').first().click()
  await expect(artifacts.locator('.artifact-list li').first()).toBeVisible({ timeout: 60_000 })
  await expect(artifacts.locator('.artifact-list')).toContainText('runs.csv')
  await expect(artifacts.locator('.artifact-list')).toContainText('report.html')

  // 当前任务 id 写进路由，刷新后从后端重新读取，而不是丢回一张空白实验表单。
  await expect(page).toHaveURL(/\/experiments\?task=/)
  await page.reload()
  await expect(page.locator('.live-task .task-status')).toHaveText('已完成', { timeout: 30_000 })
  await expect(page.locator('.live-task .progress-facts strong')).toHaveText('4 / 4')
})

test('移动端七个导航入口完整可用', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/')

  const navigation = page.locator('.nav-list .nav-item')
  await expect(navigation).toHaveCount(7)
  await expect(page.getByRole('link', { name: '项目说明' })).toBeVisible()
  await page.getByRole('link', { name: '项目说明' }).click()
  await expect(page).toHaveURL(/\/about$/)
  await expect(page.getByRole('heading', { name: '项目说明' })).toBeVisible()
})

/** 逐个点掉已选项：`.selected` 的集合每点一次都会变，所以要反复取第一个。 */
async function deselectAll(selected: Locator) {
  for (let remaining = await selected.count(); remaining > 0; remaining = await selected.count()) {
    await selected.first().click()
  }
}
