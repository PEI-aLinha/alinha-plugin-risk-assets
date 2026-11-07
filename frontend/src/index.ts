import { definePlugin } from '@alinha/plugin-sdk'
import { RiskAssetsPage } from './pages/RiskAssetsPage'

/**
 * Ponto de entrada do plugin no frontend. O shell lê este objeto e monta
 * as rotas e o menu — não há nada a registar à mão no shell.
 *   area 'client'     → /org/:org/<path>
 *   area 'consultant' → /carteira/<path>
 */
export default definePlugin({
  id: 'risk-assets',
  name: 'Risk & Assets',
  routes: {
    client: [
      { path: 'risk-assets', handle: { title: 'Risk & Assets' }, Component: RiskAssetsPage },
    ],
  },
  nav: [
    { label: 'Risk & Assets', path: 'risk-assets', area: 'client' },
  ],
})
