import type { SearchResult } from '../types/search'

type SearchResultCardProps = {
  position: number
  result: SearchResult
}

export function SearchResultCard({
  position,
  result,
}: SearchResultCardProps) {
  return (
    <li className="result-item">
      <article className="result-card">
        <span className="result-number" aria-hidden="true">
          {position}
        </span>
        <div className="result-content">
          <p className="result-url">{result.displayUrl}</p>
          <h3 className="result-title">
            <a
              href={result.url}
              target="_blank"
              rel="noopener noreferrer"
            >
              {result.title}
              <span className="new-tab-indicator" aria-hidden="true">
                ↗
              </span>
            </a>
          </h3>
          <p className="result-description">{result.description}</p>
        </div>
      </article>
    </li>
  )
}
