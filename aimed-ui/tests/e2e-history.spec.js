import { test, expect } from '@playwright/test';

const baseURL = 'http://127.0.0.1:5173';

test('history and restore flow', async ({ page }) => {
  await page.goto(`${baseURL}/login`);
  await page.getByPlaceholder('请输入邮箱或用户名').fill('654735670@qq.com');
  await page.getByPlaceholder('请输入登录密码').fill('13588035530');
  await page.getByRole('button', { name: '登录进入系统' }).click();
  await page.waitForURL(`${baseURL}/`);
  await page.waitForTimeout(1500);

  const before = await page.locator('.history-item').count();
  console.log('before history count', before);

  const input = page.locator('.composer-input textarea');
  await input.fill('请用一句话介绍树兰医院');
  await input.press('Enter');

  await expect(page.locator('.message-item.assistant-message').last()).toBeVisible({ timeout: 30000 });
  await page.waitForTimeout(4000);

  await page.getByRole('button', { name: '新建会话' }).click();
  await page.waitForTimeout(1500);

  const after = await page.locator('.history-item').count();
  console.log('after history count', after);
  expect(after).toBeGreaterThanOrEqual(before + 1);

  const firstItem = page.locator('.history-item').first();
  await firstItem.click();
  await page.waitForTimeout(1200);

  await expect(page.locator('.message-item.user-message').last()).toContainText('请用一句话介绍树兰医院');

  await page.reload();
  await page.waitForTimeout(1500);
  await expect(page.locator('.message-item.user-message').last()).toContainText('请用一句话介绍树兰医院');
});
