import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { Button } from '@/components/ui/button'

export function HomePage() {
  const { t } = useTranslation()

  return (
    <main className="mx-auto flex min-h-svh max-w-xl flex-col items-center justify-center gap-6 p-6 text-center">
      <h1 className="text-4xl font-semibold tracking-tight">Kitchen</h1>
      <p className="text-muted-foreground">{t('home.tagline')}</p>
      <Button asChild>
        <Link to="/sign-in">{t('home.start')}</Link>
      </Button>
    </main>
  )
}
