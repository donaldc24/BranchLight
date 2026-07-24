import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import App from './App'

describe('BranchLight search', () => {
  it('does not show results before a search', () => {
    render(<App />)

    expect(
      screen.queryByRole('heading', { name: /search results/i }),
    ).not.toBeInTheDocument()
    expect(screen.queryAllByRole('listitem')).toHaveLength(0)
  })

  it('shows exactly five results after submitting a valid query', async () => {
    const user = userEvent.setup()
    render(<App />)

    const input = screen.getByRole('searchbox', { name: /search the web/i })
    await user.type(input, 'virtual threads{Enter}')

    expect(screen.getAllByRole('listitem')).toHaveLength(5)
    expect(input).toHaveValue('virtual threads')
  })

  it('does not show results for a whitespace-only query', async () => {
    const user = userEvent.setup()
    render(<App />)

    await user.type(
      screen.getByRole('searchbox', { name: /search the web/i }),
      '   ',
    )
    await user.click(screen.getByRole('button', { name: /search/i }))

    expect(screen.queryAllByRole('listitem')).toHaveLength(0)
    expect(
      screen.queryByText(/showing 5 results for/i),
    ).not.toBeInTheDocument()
  })

  it('shows the trimmed submitted query in the results summary', async () => {
    const user = userEvent.setup()
    render(<App />)

    await user.type(
      screen.getByRole('searchbox', { name: /search the web/i }),
      '  virtual threads  ',
    )
    await user.click(screen.getByRole('button', { name: /search/i }))

    const results = screen.getByRole('region', { name: /search results/i })
    expect(
      within(results).getByText(/showing 5 results for/i),
    ).toHaveTextContent('Showing 5 results for “virtual threads”')
  })

  it('renders every result title as a safe external link', async () => {
    const user = userEvent.setup()
    render(<App />)

    await user.type(
      screen.getByRole('searchbox', { name: /search the web/i }),
      'research tools',
    )
    await user.click(screen.getByRole('button', { name: /search/i }))

    const results = screen.getByRole('region', { name: /search results/i })
    const links = within(results).getAllByRole('link')

    expect(links).toHaveLength(5)
    links.forEach((link) => {
      expect(link).toHaveAttribute('href')
      expect(link).toHaveAttribute('target', '_blank')
      expect(link).toHaveAttribute('rel', 'noopener noreferrer')
    })
  })
})
