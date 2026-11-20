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

/** The logo at the top of every page but the home page. */
export function SiteHeader() {
  return (
    <header className="flex justify-center px-4 pt-8">
      <LogoLink />
    </header>
  )
}

/**
 * The site's pages: the logo at the top, then one block centred in the rest of the page, its
 * text centred. Buttons keep their natural width; the first one after the text gets extra space.
 */
export function PageShell({
  title,
  children,
  footer,
}: {
  title: string
  children: ReactNode
  /** A discreet secondary choice, centred at the bottom of the page. */
  footer?: ReactNode
}) {
  return (
    <div className="flex min-h-svh flex-col">
      <SiteHeader />
      <main className="flex flex-1 flex-col justify-center px-4 py-12">
        <div className="mx-auto flex w-full max-w-md flex-col items-center gap-6 text-center [&>:not([data-slot=button])]:w-full [&>:not([data-slot=button])+[data-slot=button]]:mt-4">
          <h1 className="text-4xl">{title}</h1>
          {children}
        </div>
      </main>
      {footer && <footer className="flex justify-center px-4 pb-8">{footer}</footer>}
    </div>
  )
}
