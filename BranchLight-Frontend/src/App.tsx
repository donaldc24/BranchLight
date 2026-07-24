import { useState } from 'react'
import './App.css'
import { SearchForm } from './components/SearchForm'
import { SearchResults } from './components/SearchResults'
import { mockSearchResults } from './data/mockSearchResults'

function App() {
  const [query, setQuery] = useState('')
  const [submittedQuery, setSubmittedQuery] = useState('')

  function handleSearch() {
    const trimmedQuery = query.trim()

    if (!trimmedQuery) {
      return
    }

    setQuery(trimmedQuery)
    setSubmittedQuery(trimmedQuery)
  }

  const hasResults = submittedQuery.length > 0

  return (
    <main className={hasResults ? 'app app--results' : 'app'}>
      <div className="search-shell">
        <header className="brand">
          <h1 className="brand-name">
            Branch<span>Light</span>
          </h1>
          {!hasResults && (
            <p className="brand-subtitle">
              Search the web. Follow the thread.
            </p>
          )}
        </header>

        <SearchForm
          query={query}
          onQueryChange={setQuery}
          onSubmit={handleSearch}
        />

        <div
          className="results-announcement sr-only"
          role="status"
          aria-live="polite"
          aria-atomic="true"
        >
          {hasResults
            ? `Showing ${mockSearchResults.length} results for ${submittedQuery}`
            : ''}
        </div>

        {hasResults && (
          <SearchResults
            query={submittedQuery}
            results={mockSearchResults}
          />
        )}
      </div>
    </main>
  )
}

export default App
