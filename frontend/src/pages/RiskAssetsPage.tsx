import { useQuery } from '@tanstack/react-query'
import { useHost } from '@alinha/plugin-sdk'
import { PageHeader } from '@alinha/ui'

export function RiskAssetsPage() {
  const { api } = useHost()
  const ping = useQuery({
    queryKey: ['risk-assets', 'ping'],
    queryFn: () => api<{ plugin: string; status: string }>('/risk-assets/ping'),
  })

  return (
    <>
      <PageHeader title="Ativos e riscos" />
      <p>Backend: {ping.isPending ? 'a verificar…' : ping.isError ? 'indisponível' : ping.data.status}</p>
    </>
  )
}
