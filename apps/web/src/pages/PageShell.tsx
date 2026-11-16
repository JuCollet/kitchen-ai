import type { ReactNode } from 'react'

/** Narrow centered column shared by the account pages. */
export function PageShell({ title, children }: { title: string; children: ReactNode }) {
  return (
    <main className="mx-auto flex min-h-svh max-w-md flex-col gap-6 px-4 py-10">
      <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
      {children}
    </main>
  )
}
