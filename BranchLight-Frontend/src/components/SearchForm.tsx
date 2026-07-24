import type { FormEvent } from 'react'

type SearchFormProps = {
  query: string
  onQueryChange: (query: string) => void
  onSubmit: () => void
  isLoading: boolean
}

export function SearchForm({
  query,
  onQueryChange,
  onSubmit,
  isLoading,
}: SearchFormProps) {
  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onSubmit()
  }

  return (
    <form
      className="search-form"
      role="search"
      aria-busy={isLoading}
      onSubmit={handleSubmit}
    >
      <label className="sr-only" htmlFor="search-query">
        Search the web
      </label>
      <div className="search-control">
        <svg
          className="search-icon"
          aria-hidden="true"
          viewBox="0 0 24 24"
          width="21"
          height="21"
        >
          <circle cx="11" cy="11" r="6.75" />
          <path d="m16 16 4 4" />
        </svg>
        <input
          id="search-query"
          name="query"
          type="search"
          value={query}
          onChange={(event) => onQueryChange(event.target.value)}
          placeholder="What are you looking for?"
          autoComplete="off"
          autoFocus
        />
        <button type="submit" disabled={isLoading}>
          {isLoading ? 'Searching…' : 'Search'}
        </button>
      </div>
    </form>
  )
}
