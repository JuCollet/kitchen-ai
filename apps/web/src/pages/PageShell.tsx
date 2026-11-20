import type { ReactNode } from 'react'
import { Link } from 'react-router'
import logo from '@/assets/logo-kitchen-ai.svg'

/** The Kitchen logo, linking back to the home page. */
function LogoLink() {
  return (
    <Link
      to="/"
      className="self-center rounded-lg focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-ring/40"
    >
      <img src={logo} alt="Kitchen AI" className="h-12 w-auto" width={150} height={48} />
    </Link>
  )
}

/** The logo at the top of a page whose content is long (the shopping list). */
export function SiteHeader() {
  return (
    <header className="flex justify-center px-4 pt-8">
      <LogoLink />
    </header>
  )
}

/**
 * The site's pages: one block centred in the page, everything centred. The same space separates
 * the logo from the text and the text from the first button (gap-6 plus the extra margins).
 */
export function PageShell({ title, children }: { title: string; children: ReactNode }) {
  return (
    <main className="flex min-h-svh flex-col justify-center px-4 py-12">
      <div className="mx-auto flex w-full max-w-md flex-col gap-6 text-center [&>:not([data-slot=button])+[data-slot=button]]:mt-4">
        <div className="mb-4 flex justify-center">
          <LogoLink />
        </div>
        <h1 className="text-4xl">{title}</h1>
        {children}
      </div>
    </main>
  )
}
