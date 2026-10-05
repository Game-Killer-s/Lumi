export function routeLabel(url: string): string {
  return url
    .split('?')[0]
    .split('/')
    .map((segment) => (/^[0-9a-f-]{8,}$/i.test(segment) ? ':id' : segment))
    .join('/');
}
