import { writeFileSync } from 'fs';
import { resolve } from 'path';

interface Endpoint {
  name: string;
  method: string;
  path: string;
}

interface BaselineEntry {
  endpoint: string;
  method: string;
  path: string;
  count: number;
  avgMs: number;
  p95Ms: number;
  maxMs: number;
}

const BASE_URL = process.env.BASE_URL ?? 'http://localhost:3000';
const ITERATIONS = Number.parseInt(process.env.BASELINE_ITERATIONS ?? '20', 10);

const ENDPOINTS: Endpoint[] = [
  { name: 'health', method: 'GET', path: '/api/health' },
  { name: 'metrics', method: 'GET', path: '/api/metrics' },
  { name: 'catalog-cached', method: 'GET', path: '/api/catalog?page=1&limit=20' },
  {
    name: 'catalog-uncached',
    method: 'GET',
    path: '/api/catalog?page=1&limit=20&cache=false',
  },
  { name: 'discovery', method: 'GET', path: '/api/discovery' },
];

function p95(sorted: number[]): number {
  const index = Math.min(
    sorted.length - 1,
    Math.ceil(sorted.length * 0.95) - 1,
  );
  return sorted[index];
}

async function measure(path: string): Promise<number[]> {
  const samples: number[] = [];

  for (let i = 0; i < ITERATIONS; i += 1) {
    const start = Date.now();
    await fetch(`${BASE_URL}${path}`);
    samples.push(Date.now() - start);
  }

  return samples.sort((a, b) => a - b);
}

async function main(): Promise<void> {
  const entries: BaselineEntry[] = [];

  for (const endpoint of ENDPOINTS) {
    const samples = await measure(endpoint.path);

    const entry: BaselineEntry = {
      endpoint: endpoint.name,
      method: endpoint.method,
      path: endpoint.path,
      count: samples.length,
      avgMs: Math.round(
        samples.reduce((sum, s) => sum + s, 0) / samples.length,
      ),
      p95Ms: Math.round(p95(samples)),
      maxMs: samples[samples.length - 1],
    };

    entries.push(entry);

    console.log(
      `${endpoint.name}: avg=${entry.avgMs}ms p95=${entry.p95Ms}ms max=${entry.maxMs}ms`,
    );
  }

  const file = resolve(__dirname, 'baseline.json');

  writeFileSync(
    file,
    JSON.stringify({ createdAt: new Date().toISOString(), entries }, null, 2),
  );

  console.log(`Baseline saved to ${file}`);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
