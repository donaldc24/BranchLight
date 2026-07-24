import type { SearchResult } from '../types/search'

export const mockSearchResults: SearchResult[] = [
  {
    id: 'mdn-web-docs',
    title: 'MDN Web Docs',
    url: 'https://developer.mozilla.org/',
    displayUrl: 'developer.mozilla.org',
    description:
      'Resources for developers, by developers. Explore reliable guides and references for the open web.',
  },
  {
    id: 'internet-archive',
    title: 'Internet Archive: Digital Library of Free & Borrowable Materials',
    url: 'https://archive.org/',
    displayUrl: 'archive.org',
    description:
      'A nonprofit library offering millions of free books, websites, recordings, software, and other cultural artifacts.',
  },
  {
    id: 'our-world-in-data',
    title: 'Our World in Data',
    url: 'https://ourworldindata.org/',
    displayUrl: 'ourworldindata.org',
    description:
      'Research and data that make progress against the world’s largest problems understandable and accessible.',
  },
  {
    id: 'project-gutenberg',
    title: 'Project Gutenberg',
    url: 'https://www.gutenberg.org/',
    displayUrl: 'gutenberg.org',
    description:
      'A library of more than 70,000 free eBooks, with a focus on older works whose copyright has expired.',
  },
  {
    id: 'smithsonian-open-access',
    title: 'Smithsonian Open Access',
    url: 'https://www.si.edu/openaccess',
    displayUrl: 'si.edu › openaccess',
    description:
      'Discover millions of digital items from across the Smithsonian’s museums, research centers, libraries, and archives.',
  },
]
