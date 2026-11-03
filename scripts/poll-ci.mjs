/**
 * 轮询 GitHub Actions 运行状态，直到完成或超时。
 * 用法：node poll-ci.mjs <owner/repo> [timeoutMinutes]
 */
const repo = process.argv[2] ?? 'zoominu/eason-blog'
const timeoutMin = Number(process.argv[3] ?? 10)
const deadline = Date.now() + timeoutMin * 60 * 1000

const headers = { 'User-Agent': 'dsh-agent', Accept: 'application/vnd.github+json' }

async function get(path) {
  const r = await fetch(`https://api.github.com/repos/${repo}${path}`, { headers, signal: AbortSignal.timeout(15000) })
  if (!r.ok) throw new Error(`HTTP ${r.status} ${path}`)
  return r.json()
}

async function waitRun() {
  for (;;) {
    const runs = await get('/actions/runs?per_page=1')
    if (runs.workflow_runs?.length) return runs.workflow_runs[0]
    if (Date.now() > deadline) throw new Error('no run found')
    await new Promise((r) => setTimeout(r, 5000))
  }
}

let run = await waitRun()
console.log(`run #${run.run_number} ${run.status} ${run.conclusion ?? ''} ${run.html_url}`)

while (run.status !== 'completed') {
  if (Date.now() > deadline) {
    console.log('TIMEOUT waiting for run')
    process.exit(2)
  }
  await new Promise((r) => setTimeout(r, 15000))
  run = await get(`/actions/runs/${run.id}`)
  console.log(`  ... ${run.status} ${run.conclusion ?? ''}`)
}

console.log(`\nFINAL: ${run.status} / ${run.conclusion}`)
const jobs = await get(`/actions/runs/${run.id}/jobs`)
for (const job of jobs.jobs) {
  console.log(`- ${job.name}: ${job.status} / ${job.conclusion}`)
  for (const step of job.steps ?? []) {
    console.log(`    ${step.conclusion === 'success' ? '✓' : step.conclusion === 'failure' ? '✗' : '·'} ${step.name} (${step.conclusion ?? step.status})`)
  }
}
process.exit(run.conclusion === 'success' ? 0 : 1)
