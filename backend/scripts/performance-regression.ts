import { readFileSync } from 'fs';
import { resolve } from 'path';

const BASE_URL = process.env.BASE_URL ?? 'http://localhost:3000';
const ITERATIONS = Number.parseInt(
  process.env.REGRESSION_ITERATIONS ?? '20',
  10,
);
const TOLERANCE = Number.parseFloat(process.env.REGRESSION_TOLERANCE ?? '1.5');

interface BaselineEntry {
  endpoint: string;
  method: string;
  path: string;
  count: number;
  avgMs: number;
  p95Ms: number;
  maxMs: number;
}

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
  const file = resolve(__dirname, 'baseline.json');
  const baseline = JSON.parse(readFileSync(file, 'utf-8')) as {
    createdAt: string;
    entries: BaselineEntry[];
  };

  let failed = false;

  for (const entry of baseline.entries) {
    const samples = await measure(entry.path);
    const currentP95 = Math.round(p95(samples));
    const limit = entry.p95Ms * TOLERANCE + 50;

    const ok = currentP95 <= limit;

    if (!ok) {
      failed = true;
    }

    console.log(
      `${entry.endpoint}: p95=${currentP95}ms (baseline ${entry.p95Ms}ms, limit ${Math.round(limit)}ms) ${ok ? 'OK' : 'REGRESSION'}`,
    );
  }

  process.exit(failed ? 1 : 0);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
