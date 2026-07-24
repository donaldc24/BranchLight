import { useRef, useState } from 'react'
import type { MouseEvent } from 'react'
import './App.css'
import { search } from './api/search'
import { SearchForm } from './components/SearchForm'
import { SearchResults } from './components/SearchResults'
import type { CategorizedResult } from './types/search'

type SearchStatus = 'idle' | 'loading' | 'success' | 'error'

function App() {
  const [query, setQuery] = useState('')
  const [submittedQuery, setSubmittedQuery] = useState('')
  const [results, setResults] = useState<CategorizedResult[]>([])
  const [status, setStatus] = useState<SearchStatus>('idle')
  const searchRequestVersion = useRef(0)

  async function handleSearch() {
    const trimmedQuery = query.trim()

    if (!trimmedQuery || status === 'loading') {
      return
    }

    setQuery(trimmedQuery)
    setSubmittedQuery(trimmedQuery)
    setResults([])
    setStatus('loading')
    const requestVersion = ++searchRequestVersion.current

    try {
      const response = await search(trimmedQuery)

      if (requestVersion !== searchRequestVersion.current) {
        return
      }

      setResults(response.results)
      setStatus('success')
    } catch {
      if (requestVersion !== searchRequestVersion.current) {
        return
      }

      setResults([])
      setStatus('error')
    }
  }

  function handleHomeClick(event: MouseEvent<HTMLAnchorElement>) {
    event.preventDefault()
    searchRequestVersion.current += 1
    setQuery('')
    setSubmittedQuery('')
    setResults([])
    setStatus('idle')
  }

  const hasSearched = status !== 'idle'
  const isLoading = status === 'loading'

  return (
    <main className={hasSearched ? 'app app--results' : 'app'}>
      <div className="search-shell">
        <header className="brand">
          <h1 className="brand-name">
            <a className="brand-home" href="/" onClick={handleHomeClick}>
              Branch<span>Light</span>
            </a>
          </h1>
          {!hasSearched && (
            <p className="brand-subtitle">
              Search the web. Follow the thread.
            </p>
          )}
        </header>

        <SearchForm
          query={query}
          onQueryChange={setQuery}
          onSubmit={handleSearch}
          isLoading={isLoading}
        />

        {status === 'loading' && (
          <section
            className="search-state"
            role="status"
            aria-live="polite"
            aria-labelledby="loading-heading"
          >
            <h2 id="loading-heading">Searching…</h2>
            <p>Looking for results about “{submittedQuery}”.</p>
          </section>
        )}

        {status === 'error' && (
          <section
            className="search-state search-state--error"
            role="alert"
            aria-labelledby="error-heading"
          >
            <h2 id="error-heading">Search unavailable</h2>
            <p>We couldn’t complete your search. Please try again.</p>
          </section>
        )}

        {status === 'success' && results.length === 0 && (
          <section
            className="search-state"
            role="status"
            aria-live="polite"
            aria-labelledby="no-results-heading"
          >
            <h2 id="no-results-heading">No results found</h2>
            <p>Try a different search for “{submittedQuery}”.</p>
          </section>
        )}

        {status === 'success' && results.length > 0 && (
          <>
            <div
              className="results-announcement sr-only"
              role="status"
              aria-live="polite"
              aria-atomic="true"
            >
              Showing {results.length} results for {submittedQuery}
            </div>
            <SearchResults
              query={submittedQuery}
              results={results}
            />
          </>
        )}
      </div>
    </main>
  )
}

export default App
