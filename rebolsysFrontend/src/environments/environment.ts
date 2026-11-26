declare global {
  interface Window {
    __REBOLSYS_CONFIG__?: { apiUrl?: string };
  }
}

export const environment = {
  apiUrl: typeof window !== 'undefined' && window.__REBOLSYS_CONFIG__?.apiUrl
    ? window.__REBOLSYS_CONFIG__.apiUrl
    : '/api/v1'
};
