import { definePlugin } from '@alinha/plugin-sdk'
import { RiskAssetsPage } from './pages/RiskAssetsPage'

/**
 * The plugin's frontend entry point. The shell reads this object and mounts
 * the routes and the menu — nothing to register by hand in the shell.
 *   area 'client'     → /org/:org/<path>
 *   area 'consultant' → /carteira/<path>
 * User-visible text (title, label) and paths are in Portuguese, plain language
 * (UI requirement in the technical report).
 */
export default definePlugin({
  id: 'risk-assets',
  name: 'Risk & Assets',
  routes: {
    client: [
      { path: 'ativos', handle: { title: 'Ativos e riscos' }, Component: RiskAssetsPage },
    ],
  },
  nav: [
    { label: 'Ativos e riscos', path: 'ativos', area: 'client' },
  ],
})
