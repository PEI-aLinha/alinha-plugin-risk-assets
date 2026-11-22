import { Boxes } from 'lucide-react'
import { EmptyState, PageHeader } from '@alinha/ui'

export function RiskAssetsPage() {
  return (
    <>
      <PageHeader title="Ativos" subtitle="Equipamentos, sistemas e serviços da organização." />
      <EmptyState icon={<Boxes className="size-5" aria-hidden />} title="Em construção">
        Aqui vai poder registar os equipamentos, sistemas e serviços da sua organização e indicar quais são mais importantes para o negócio.
      </EmptyState>
    </>
  )
}
