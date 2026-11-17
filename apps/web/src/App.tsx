import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Route, Routes } from 'react-router'
import { AccountPage } from '@/pages/AccountPage'
import { ConsentPage } from '@/pages/ConsentPage'
import { HomePage } from '@/pages/HomePage'
import { JoinPage } from '@/pages/JoinPage'
import { ShoppingListPage } from '@/pages/ShoppingListPage'
import { SignInPage } from '@/pages/SignInPage'

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/sign-in" element={<SignInPage />} />
      <Route path="/account" element={<AccountPage />} />
      <Route path="/join" element={<JoinPage />} />
      <Route path="/consent" element={<ConsentPage />} />
      <Route path="/list/:listId" element={<ShoppingListPage />} />
      <Route path="*" element={<HomePage />} />
    </Routes>
  )
}

const queryClient = new QueryClient()

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </QueryClientProvider>
  )
}
