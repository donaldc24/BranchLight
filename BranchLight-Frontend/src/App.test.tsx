import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import type { CategorizedResult, SearchResponse } from './types/search'

const searchResults: CategorizedResult[] = [
  {
    role: 'AUTHORITATIVE',
    title: 'Authoritative result',
    url: 'https://docs.example.com/authoritative',
    domain: 'docs.example.com',
    snippet: 'Primary documentation for the topic.',
    selectionReason: 'This is the primary source.',
    score: 0.99,
  },
  {
    role: 'EXPLANATORY',
    title: 'Explanatory result',
    url: 'https://learn.example.com/explanatory',
    domain: 'learn.example.com',
    snippet: 'A clear explanation of the topic.',
    selectionReason: 'This source explains the fundamentals.',
    score: 0.91,
  },
  {
    role: 'PRACTICAL',
    title: 'Practical result',
    url: 'https://build.example.com/practical',
    domain: 'build.example.com',
    snippet: 'A hands-on guide with examples.',
    selectionReason: 'This source demonstrates practical use.',
    score: 0.87,
  },
  {
    role: 'CRITICAL',
    title: 'Critical result',
    url: 'https://review.example.com/critical',
    domain: 'review.example.com',
    snippet: 'A critical assessment of tradeoffs.',
    selectionReason: 'This source challenges common assumptions.',
    score: 0.82,
  },
  {
    role: 'HUMAN_DISCUSSION',
    title: 'Human discussion',
    url: 'https://forum.example.com/discussion',
    domain: 'forum.example.com',
    snippet: 'A discussion grounded in real experience.',
    selectionReason: 'This source captures community perspectives.',
  },
]

const fetchMock = vi.fn()

function createResponse(
  body: unknown,
  ok = true,
  status = 200,
): Response {
  return {
    ok,
    status,
    json: vi.fn().mockResolvedValue(body),
  } as unknown as Response
}

function createSearchResponse(
  results: CategorizedResult[] = searchResults,
): SearchResponse {
  return {
    query: 'virtual threads',
    results,
  }
}

describe('BranchLight search', () => {
  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('does not show results before a search', () => {
    render(<App />)

    expect(
      screen.queryByRole('heading', { name: /search results/i }),
    ).not.toBeInTheDocument()
    expect(screen.queryAllByRole('listitem')).toHaveLength(0)
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('submits with Enter and shows loading until API results arrive', async () => {
    let resolveRequest: (response: Response) => void = () => undefined
    fetchMock.mockImplementation(
      () =>
        new Promise<Response>((resolve) => {
          resolveRequest = resolve
        }),
    )

    const user = userEvent.setup()
    render(<App />)

    const input = screen.getByRole('searchbox', { name: /search the web/i })
    const searchButton = screen.getByRole('button', { name: 'Search' })
    await user.type(input, '  virtual threads  {Enter}')

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock).toHaveBeenCalledWith('/api/search', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ query: 'virtual threads' }),
    })
    expect(input).toHaveValue('virtual threads')
    expect(searchButton).toBeDisabled()
    expect(searchButton).toHaveAccessibleName('Searching…')
    expect(
      screen.getByRole('status', { name: 'Searching…' }),
    ).toBeInTheDocument()

    await act(async () => {
      resolveRequest(createResponse(createSearchResponse()))
    })

    const results = await screen.findByRole('region', {
      name: /search results/i,
    })
    expect(within(results).getAllByRole('listitem')).toHaveLength(5)
    expect(searchButton).toBeEnabled()
    expect(searchButton).toHaveAccessibleName('Search')

    const links = within(results).getAllByRole('link')
    expect(links).toHaveLength(5)
    links.forEach((link) => {
      expect(link).toHaveAttribute('href')
      expect(link).toHaveAttribute('target', '_blank')
      expect(link).toHaveAttribute('rel', 'noopener noreferrer')
    })
  })

  it('does not call the API for a whitespace-only query', async () => {
    const user = userEvent.setup()
    render(<App />)

    await user.type(
      screen.getByRole('searchbox', { name: /search the web/i }),
      '   ',
    )
    await user.click(screen.getByRole('button', { name: /search/i }))

    expect(screen.queryAllByRole('listitem')).toHaveLength(0)
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('returns to an empty main screen when the BranchLight wordmark is clicked', async () => {
    fetchMock.mockResolvedValue(createResponse(createSearchResponse()))
    const user = userEvent.setup()
    render(<App />)

    const input = screen.getByRole('searchbox', { name: /search the web/i })
    await user.type(input, 'virtual threads{Enter}')
    await screen.findByRole('region', { name: /search results/i })

    await user.click(screen.getByRole('link', { name: 'Branch Light' }))

    expect(input).toHaveValue('')
    expect(
      screen.getByText('Search the web. Follow the thread.'),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('region', { name: /search results/i }),
    ).not.toBeInTheDocument()
  })

  it('shows a no-results state for an empty successful response', async () => {
    fetchMock.mockResolvedValue(createResponse(createSearchResponse([])))
    const user = userEvent.setup()
    render(<App />)

    await user.type(
      screen.getByRole('searchbox', { name: /search the web/i }),
      'nothing here{Enter}',
    )

    const noResults = await screen.findByRole('status', {
      name: 'No results found',
    })
    expect(
      within(noResults).getByRole('heading', { name: 'No results found' }),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('region', { name: /search results/i }),
    ).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Search' })).toBeEnabled()
  })

  it('shows an API error state and re-enables search', async () => {
    fetchMock.mockResolvedValue(createResponse(undefined, false, 503))
    const user = userEvent.setup()
    render(<App />)

    await user.type(
      screen.getByRole('searchbox', { name: /search the web/i }),
      'research tools{Enter}',
    )

    const alert = await screen.findByRole('alert')
    expect(
      within(alert).getByRole('heading', { name: 'Search unavailable' }),
    ).toBeInTheDocument()
    expect(alert).toHaveTextContent(
      'We couldn’t complete your search. Please try again.',
    )
    expect(screen.getByRole('button', { name: 'Search' })).toBeEnabled()
  })
})
