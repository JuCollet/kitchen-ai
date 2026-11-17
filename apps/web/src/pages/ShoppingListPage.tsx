import {
  languageOf,
  messagesFor,
  ShoppingListView,
  withChecked,
  type ShoppingItem,
  type ShoppingList,
} from '@kitchen/shopping-list'
import '@kitchen/shopping-list/shopping-list.css'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation, useParams } from 'react-router'
import { api, ApiError } from '@/lib/api'
import { saveReturnPath } from '@/lib/returnAfterSignIn'
import { PageShell } from './PageShell'

/** Same rhythm as the widget: what other members check shows up within seconds. */
const REFRESH_MS = 10_000

/**
 * The shopping list for AI apps that cannot display the Kitchen widget: the same list component,
 * fed by the site API with the browser session instead of the AI host.
 */
export function ShoppingListPage() {
  const { t, i18n } = useTranslation()
  const { listId = '' } = useParams()
  const queryClient = useQueryClient()
  const queryKey = ['shopping-list', listId]
  const [notice, setNotice] = useState<string | null>(null)
  const listT = messagesFor(languageOf(i18n.language))

  const query = useQuery({
    queryKey,
    queryFn: () => api.shoppingList(listId),
    refetchInterval: REFRESH_MS,
    retry: (count, error) => !(error instanceof ApiError && error.status < 500) && count < 2,
  })

  // Optimistic: the box is ticked at once; on failure the previous list comes back.
  const toggle = useMutation({
    mutationFn: ({ item, checked }: { item: ShoppingItem; checked: boolean }) =>
      api.setChecked(listId, item.id, checked),
    onMutate: async ({ item, checked }) => {
      setNotice(null)
      await queryClient.cancelQueries({ queryKey })
      const before = queryClient.getQueryData<ShoppingList>(queryKey)
      if (before) queryClient.setQueryData(queryKey, withChecked(before, item.id, checked))
      return { before }
    },
    onError: (_error, _variables, context) => {
      if (context?.before) queryClient.setQueryData(queryKey, context.before)
      setNotice(listT.saveFailed)
    },
    onSuccess: (saved) => queryClient.setQueryData(queryKey, saved),
  })

  if (query.isPending)
    return <PageShell title={t('shoppingList.title')}>{t('common.loading')}</PageShell>
  if (query.isError) return <ShoppingListError error={query.error} />

  return (
    <main className="mx-auto min-h-svh max-w-md py-6 ksl-site">
      <ShoppingListView
        list={query.data}
        t={listT}
        locale={i18n.language}
        notice={notice}
        onToggle={(item, checked) => toggle.mutate({ item, checked })}
      />
    </main>
  )
}

function ShoppingListError({ error }: { error: Error }) {
  const { t } = useTranslation()
  const { pathname } = useLocation()
  const status = error instanceof ApiError ? error.status : 0
  // Signing in leaves the page (Google redirect): come back to this list afterwards.
  useEffect(() => {
    if (status === 401) saveReturnPath(pathname)
  }, [status, pathname])
  return (
    <PageShell title={t('shoppingList.title')}>
      {status === 401 ? (
        <p>
          {t('shoppingList.signInFirst')}{' '}
          <Link className="underline" to="/sign-in">
            {t('shoppingList.signIn')}
          </Link>
        </p>
      ) : (
        <p>{status === 404 ? t('shoppingList.notFound') : t('common.error')}</p>
      )}
    </PageShell>
  )
}
