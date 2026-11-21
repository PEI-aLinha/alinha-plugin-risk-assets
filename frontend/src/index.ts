import { definePlugin } from '@alinha/plugin-sdk'
import { Boxes } from 'lucide-react'
import { RiskAssetsPage } from './pages/RiskAssetsPage'

/**
 * The plugin's frontend entry point. The shell reads this object and mounts
 * the routes and the menu — nothing to register by hand in the shell.
 *   area 'client'     → /org/:org/<path>
 *   area 'consultant' → /portfolio/:org/<path>
 * User-visible text (title, label) is in European Portuguese, plain language
 * (UI requirement in the technical report). Paths are in English.
 */
export default definePlugin({
  id: 'risk-assets',
  name: 'Risk & Assets',
  routes: {
    client: [
      { path: 'assets', handle: { title: 'Ativos' }, Component: RiskAssetsPage },
    ],
  },
  nav: [
    { label: 'Ativos', path: 'assets', area: 'client', icon: Boxes, order: 50 },
  ],
})
