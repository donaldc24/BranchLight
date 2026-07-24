import type { SearchRequest, SearchResponse } from '../types/search'

export async function search(query: string): Promise<SearchResponse> {
  const request: SearchRequest = { query }
  const response = await fetch('/api/search', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    throw new Error(`Search request failed with status ${response.status}`)
  }

  return (await response.json()) as SearchResponse
}
