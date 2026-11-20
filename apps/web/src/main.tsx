import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
// Kitchen's fonts, self-hosted (no request to Google Fonts), latin subsets only.
import '@fontsource/inter/latin-400.css'
import '@fontsource/inter/latin-500.css'
import '@fontsource/inter/latin-600.css'
import '@fontsource/inter/latin-700.css'
import '@fontsource/kiwi-maru/latin-400.css'
import '@fontsource/kiwi-maru/latin-500.css'
import './i18n'
import './index.css'
import { App } from './App'

const root = document.getElementById('root')
if (!root) throw new Error('Missing #root element')

createRoot(root).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
