import { expect, test } from '@playwright/test'

test('家族配置默认隐藏兼容项，筛选与开关共同生效', async ({ page }) => {
  await page.goto('/algorithms')
  await page.getByRole('button', { name: /家族与策略配置/ }).click()
  await expect(page.locator('.family-catalog summary').first()).toBeVisible()
  await page.getByRole('button', { name: 'Shelf', exact: true }).click()
  await expect(page.locator('.family-catalog article')).toHaveCount(1)
  await expect(page.locator('.family-catalog')).not.toContainText('SHELF_BAF_WM')
  await page.getByRole('checkbox', { name: /显示兼容配置/ }).check()
  await expect(page.locator('.family-catalog')).toContainText('SHELF_BAF_WM')
  await page.getByRole('searchbox').fill('SHELF_BAF_WM')
  await expect(page.locator('.configuration-entry')).toHaveCount(1)
  await expect(page.locator('.family-catalog')).toContainText('兼容 / 消融')
  await page.locator('.configuration-guide > summary').click()
  await expect(page.locator('.configuration-guide-body')).toBeVisible()
  await expect(page.locator('.configuration-guide-body')).toContainText('层尾剩余空间是两回事')
  await page.getByRole('checkbox', { name: /显示兼容配置/ }).uncheck()
  await expect(page.getByText('没有匹配的策略配置', { exact: true })).toBeVisible()
})

test('移动端可展开家族并查看策略', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/algorithms')
  await page.getByRole('button', { name: /家族与策略配置/ }).click()
  await page.getByRole('button', { name: 'MaxRects', exact: true }).click()
  await expect(page.locator('.family-catalog')).toContainText('五次解码')
  await page.locator('[data-configuration="MAXRECTS_BEST"] .configuration-guide > summary').click()
  await expect(page.locator('[data-configuration="MAXRECTS_BEST"] .configuration-guide-body')).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})

test('全部46个配置均保留新手介绍，正文可搜索并可展开阅读', async ({ page }) => {
  await page.goto('/algorithms')
  await page.getByRole('button', { name: /家族与策略配置/ }).click()
  await page.getByRole('checkbox', { name: /显示兼容配置/ }).check()
  await expect(page.locator('.configuration-entry')).toHaveCount(46)
  const entries = page.locator('.configuration-entry')
  for (const entry of await entries.all()) {
    await expect(entry.locator('.configuration-overview')).not.toBeEmpty()
    await expect(entry.locator('.configuration-guide-body > section')).toHaveCount(4)
    for (const paragraph of await entry.locator('.configuration-guide-body p').all()) {
      await expect(paragraph).not.toBeEmpty()
    }
  }
  await page.getByRole('searchbox').fill('不是仅在开始时排序一次')
  await expect(page.locator('.configuration-entry')).toHaveCount(6)
  await page.locator('.configuration-guide > summary').first().click()
  await expect(page.locator('.configuration-guide-body').first()).toBeVisible()
})
