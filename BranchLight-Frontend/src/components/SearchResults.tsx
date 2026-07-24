import type { CategorizedResult } from '../types/search'
import { SearchResultCard } from './SearchResultCard'

type SearchResultsProps = {
  query: string
  results: CategorizedResult[]
}

export function SearchResults({ query, results }: SearchResultsProps) {
  return (
    <section className="results" aria-labelledby="results-heading">
      <h2 id="results-heading" className="sr-only">
        Search results
      </h2>
      <p className="results-summary">
        Showing {results.length} results for <strong>“{query}”</strong>
      </p>
      <ol className="results-list" role="list">
        {results.map((result, index) => (
          <SearchResultCard
            key={`${result.role}:${result.url}`}
            position={index + 1}
            result={result}
          />
        ))}
      </ol>
    </section>
  )
}
