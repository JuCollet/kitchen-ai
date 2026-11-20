// Latin subsets only: the Japanese glyphs of Kiwi Maru are never downloaded.
import '@fontsource/inter/latin-600.css'
import '@fontsource/inter/latin-700.css'
import '@fontsource/kiwi-maru/latin-400.css'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import logo from '@/assets/logo-kitchen-ai.svg'
import worksWithIcons from '@/assets/works-with-icons.svg'

/**
 * The landing page: the logo, what Kitchen does, one way in. The "works with" label is translated
 * text; the icons next to it are an image (Claude, ChatGPT, Gemini).
 */
export function HomePage() {
  const { t } = useTranslation()

  return (
    <main className="flex min-h-svh flex-col items-center justify-center bg-cream px-4 py-12 text-center text-ink">
      <h1 className="w-full max-w-[38rem]">
        <img src={logo} alt="Kitchen AI" className="h-auto w-full" width={619} height={198} />
      </h1>
      <p className="mt-10 max-w-[37rem] text-balance font-display text-lg leading-snug sm:text-xl">
        {t('home.tagline')}
      </p>
      <Link
        to="/sign-in"
        className="mt-10 inline-flex items-center justify-center rounded-xl border-[3px] border-brand bg-white px-11 py-2 font-ui text-xl font-bold uppercase tracking-[0.1em] text-brand transition-colors hover:bg-brand hover:text-white focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-brand/40"
      >
        {t('home.start')}
      </Link>
      <div className="mt-8 flex items-center gap-3 font-ui text-xs font-semibold">
        <span>{t('home.worksWith')}</span>
        <img src={worksWithIcons} alt="Claude, ChatGPT, Gemini" width={88} height={23} />
      </div>
    </main>
  )
}
