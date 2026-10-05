const BASE_URL = process.env.BASE_URL ?? 'http://localhost:3000';
const CONCURRENCY = Number.parseInt(process.env.CONCURRENCY ?? '20', 10);
const REQUESTS = Number.parseInt(process.env.REQUESTS ?? '200', 10);

// Ті самі endpoints у двох варіантах:
// - без ?cache=false — відповідь із кешу
// - з ?cache=false — завжди читаємо з бази (для порівняння)
const ENDPOINTS = [
  { name: 'health', path: '/api/health' },
  { name: 'catalog (cached)', path: '/api/catalog?page=1&limit=20' },
  { name: 'catalog (uncached)', path: '/api/catalog?page=1&limit=20&cache=false' },
  { name: 'popular', path: '/api/catalog/popular?limit=10' },
  { name: 'new-releases', path: '/api/catalog/new-releases?limit=10' },
  { name: 'discovery', path: '/api/discovery' },
];

interface Sample {
  endpoint: string;
  latencyMs: number;
  status: number;
}

function percentile(sorted: number[], p: number): number {
  const index = Math.min(
    sorted.length - 1,
    Math.ceil(sorted.length * (p / 100)) - 1,
  );
  return sorted[index];
}

// Прогріваємо кеш для кешованих endpoints,
// щоб у тесті вимірювати відповідь саме з кешу.
async function warmup(): Promise<void> {
  for (const endpoint of ENDPOINTS) {
    if (endpoint.name.includes('uncached')) {
      continue;
    }

    await fetch(`${BASE_URL}${endpoint.path}`);
  }

  console.log('Cache warmed up for cached endpoints');
}

async function main(): Promise<void> {
  await warmup();

  const samples: Sample[] = [];
  let next = 0;

  const worker = async (): Promise<void> => {
    while (true) {
      const index = next;
      next += 1;

      if (index >= REQUESTS) {
        return;
      }

      const endpoint = ENDPOINTS[index % ENDPOINTS.length];
      const start = Date.now();
      const response = await fetch(`${BASE_URL}${endpoint.path}`);

      samples.push({
        endpoint: endpoint.name,
        latencyMs: Date.now() - start,
        status: response.status,
      });
    }
  };

  const startedAt = Date.now();
  await Promise.all(Array.from({ length: CONCURRENCY }, () => worker()));
  const durationSec = (Date.now() - startedAt) / 1000;

  console.log(
    `Requests: ${samples.length} | Concurrency: ${CONCURRENCY} | Time: ${durationSec.toFixed(2)}s | Throughput: ${Math.round(samples.length / durationSec)} req/s`,
  );

  for (const endpoint of ENDPOINTS) {
    const latencies = samples
      .filter((s) => s.endpoint === endpoint.name)
      .map((s) => s.latencyMs)
      .sort((a, b) => a - b);

    if (latencies.length === 0) {
      continue;
    }

    const errors = samples.filter(
      (s) => s.endpoint === endpoint.name && s.status >= 400,
    ).length;

    console.log(
      `${endpoint.name}: n=${latencies.length} avg=${Math.round(latencies.reduce((a, b) => a + b, 0) / latencies.length)}ms p50=${percentile(latencies, 50)}ms p95=${percentile(latencies, 95)}ms p99=${percentile(latencies, 99)}ms errors=${errors}`,
    );
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});

