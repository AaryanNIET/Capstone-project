import { chromium } from 'playwright-core';
import { mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const BASE = process.env.BASE_URL ?? 'http://localhost:5173';
const OUT = fileURLToPath(new URL('./shots', import.meta.url));
mkdirSync(OUT, { recursive: true });
const SAMPLE = fileURLToPath(new URL('../docs/samples/orbital-cafe-handbook.md', import.meta.url));
const problems = [];
const results = [];
const check = (name, ok, detail = '') => results.push(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  — ' + detail : ''}`);

const browser = await chromium.launch({ channel: process.env.BROWSER_CHANNEL ?? 'chrome', headless: true });
const context = await browser.newContext({ viewport: { width: 1360, height: 860 } });
const page = await context.newPage();
page.on('console', (m) => { if (m.type() === 'error' || m.type() === 'warning') problems.push(`console.${m.type()}: ${m.text()}`); });
page.on('pageerror', (e) => problems.push(`pageerror: ${e.message}`));
page.on('response', (r) => { if (r.status() >= 500) problems.push(`HTTP ${r.status()} ${r.url()}`); });
const shot = (name) => page.screenshot({ path: `${OUT}/${name}.png`, fullPage: false });

try {
  // ---- auth
  await page.goto(BASE + '/');
  await page.waitForURL('**/login');
  check('unauthenticated visit redirects to /login', true);
  await shot('01-login');
  await page.getByRole('link', { name: 'Create one' }).click();
  await page.getByLabel('Password').fill('short');
  await page.getByLabel('Name').fill('Ada Lovelace');
  await page.getByLabel('Email').fill('admin@example.com');
  await page.getByRole('button', { name: 'Create account' }).click();
  check('weak password rejected client-side', await page.getByText('At least 8 characters, with a letter and a digit.').isVisible());
  await page.getByLabel('Password').fill('secret123');
  await page.getByRole('button', { name: 'Create account' }).click();
  await page.waitForURL('**/chat');
  await page.getByText('How can I help, Ada?').waitFor();
  check('register lands on chat with greeting', true);
  check('admin nav visible for ADMIN_EMAILS user', await page.getByRole('link', { name: 'Admin' }).first().isVisible());
  await shot('02-new-chat');

  // ---- documents
  await page.getByRole('link', { name: 'Documents' }).first().click();
  await page.getByText('No documents yet').waitFor();
  await page.setInputFiles('input[type=file]', SAMPLE);
  await page.getByText('orbital-cafe-handbook.md').waitFor();
  await page.getByText('Ready', { exact: true }).waitFor({ timeout: 20000 });
  check('upload processes to Ready', true);
  await shot('03-documents');

  // ---- documents-mode question with tool call + citations
  await page.getByRole('link', { name: 'Chat' }).first().click();
  await page.getByRole('radio', { name: 'Documents' }).click();
  await page.getByLabel('Message').fill('What are the opening hours, and who do I contact about allergies?');
  await page.getByLabel('Message').press('Enter');
  await page.waitForURL(/\/chat\/[0-9a-f-]+$/);
  const sawTool = await page.getByText('Searching your documents').first().waitFor({ timeout: 8000 }).then(() => true, () => false);
  check('tool progress shown while streaming', sawTool);
  await page.getByRole('button', { name: 'Source 1' }).first().waitFor({ timeout: 20000 });
  await page.getByText('Monday to Friday 07:30–18:00').waitFor();
  const answer = await page.locator('.prose').last().innerText();
  check('answer streamed and stored with citation chip', true);
  check('fabricated citation [9] removed', !answer.includes('[9]') && /made-up source\s+should/.test(answer));
  check('tool-call preamble discarded', !answer.includes('Let me look that up'));
  check('"Your documents" badge shown', await page.getByText('Your documents').isVisible());
  check('source title shown', await page.getByText('orbital-cafe-handbook.md').first().isVisible());
  await page.getByRole('button', { name: 'Source 1' }).first().click();
  await shot('04-documents-answer');
  const title = await page.locator('h1').first().innerText();
  check('conversation auto-titled from first message', title.startsWith('What are the opening hours'), title);

  // ---- feedback + regenerate
  await page.getByRole('button', { name: 'Good answer' }).click();
  check('thumbs up pressed', await page.locator('button[aria-label="Good answer"][aria-pressed="true"]').waitFor({ timeout: 3000 }).then(() => true, () => false));
  await page.getByRole('button', { name: 'Regenerate answer' }).click();
  await page.getByRole('button', { name: 'Source 1' }).first().waitFor({ timeout: 20000 });
  await page.waitForTimeout(500);
  const bubbles = await page.locator('.prose').count();
  check('regenerate replaces the reply (one assistant answer)', bubbles === 1, `answers=${bubbles}`);

  // ---- memory in a chat-mode conversation
  await page.getByRole('button', { name: 'New chat' }).click();
  await page.getByRole('radio', { name: 'Chat' }).click();
  await page.getByLabel('Message').fill('Hello! My name is Ada. Remember that I prefer concise answers.');
  await page.getByRole('button', { name: 'Send' }).click();
  await page.getByText('Hello Ada!').waitFor({ timeout: 20000 });
  check('saved memory reaches the prompt in the same turn', true);
  check('code block rendered', await page.locator('pre code').first().waitFor({ timeout: 10000 }).then(() => true, () => false));
  check('uncited memories collapsed instead of listed as sources', await page.getByText(/memories available to this answer/).waitFor({ timeout: 5000 }).then(() => true, () => false));
  const code = await page.locator('pre code').first().innerText();
  check('citation-like text inside code untouched', code.includes('arr[1]'));
  await shot('05-chat-memory');

  // ---- stop
  await page.getByLabel('Message').fill('Tell me something long please');
  await page.getByRole('button', { name: 'Send' }).click();
  await page.getByRole('button', { name: 'Stop generating' }).click({ timeout: 5000 });
  await page.getByText(/Stopped before/).first().waitFor({ timeout: 15000 }).then(
    () => check('stop produces a cancelled reply', true),
    () => check('stop produces a cancelled reply', false, 'no cancelled marker (reply may have finished first)'));

  // ---- memory page
  await page.getByRole('link', { name: 'Memory' }).first().click();
  await page.getByText("User's name is Ada").waitFor();
  check('memory page lists learned memories', await page.getByText('User prefers concise answers').isVisible());
  await shot('06-memory');

  // ---- sidebar search
  await page.getByRole('link', { name: 'Chat' }).first().click();
  await page.getByLabel('Search conversations').fill('opening hours');
  await page.waitForTimeout(800);
  const rows = await page.locator('aside li').count();
  check('conversation search filters the list', rows === 1, `rows=${rows}`);

  // ---- settings + admin
  await page.getByRole('link', { name: 'Settings' }).first().click();
  await page.getByText('Server capabilities').waitFor();
  check('settings shows model', await page.getByText('mock-gpt').isVisible());
  await shot('07-settings');
  await page.getByRole('link', { name: 'Admin' }).first().click();
  await page.getByText('Questions asked').waitFor();
  await shot('08-admin');
  await page.getByRole('tab', { name: 'executions' }).click();
  await page.getByText('completed').first().waitFor();
  check('admin executions list runs', true);
  await shot('09-admin-executions');

  // ---- phone width + dark mode
  const mobile = await browser.newContext({ viewport: { width: 390, height: 844 }, colorScheme: 'dark',
    storageState: await context.storageState() });
  const m = await mobile.newPage();
  m.on('pageerror', (e) => problems.push(`mobile pageerror: ${e.message}`));
  await m.goto(BASE + '/chat');
  await m.getByText('How can I help, Ada?').waitFor();
  const overflow = await m.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
  check('no horizontal scroll at 390px', !overflow);
  await m.screenshot({ path: `${OUT}/10-mobile-dark-new-chat.png` });
  await m.getByRole('button', { name: 'Conversations' }).click();
  await m.screenshot({ path: `${OUT}/11-mobile-dark-sidebar.png` });
  await m.locator('aside a').first().click();
  await m.locator('.prose').first().waitFor();
  const overflow2 = await m.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
  check('no horizontal scroll in a conversation at 390px', !overflow2);
  await m.screenshot({ path: `${OUT}/12-mobile-dark-conversation.png` });

  // ---- logout
  await page.getByRole('button', { name: 'Sign out' }).click();
  await page.waitForURL('**/login');
  check('sign out returns to login', true);
} catch (e) {
  results.push('ERROR  ' + e.message.split('\n')[0]);
  await shot('99-failure').catch(() => {});
} finally {
  await browser.close();
}
console.log(results.join('\n'));
console.log('\nBrowser problems:', problems.length ? '\n' + [...new Set(problems)].join('\n') : 'none');
process.exit(results.some((r) => !r.startsWith('PASS')) ? 1 : 0);
