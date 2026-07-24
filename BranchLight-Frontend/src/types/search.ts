export type SearchRole =
  | 'AUTHORITATIVE'
  | 'EXPLANATORY'
  | 'PRACTICAL'
  | 'CRITICAL'
  | 'HUMAN_DISCUSSION'

export type SearchRequest = {
  query: string
}

export type CategorizedResult = {
  role: SearchRole
  title: string
  url: string
  domain: string
  snippet: string
  selectionReason: string
  score?: number
}

export type SearchResponse = {
  query: string
  results: CategorizedResult[]
}
