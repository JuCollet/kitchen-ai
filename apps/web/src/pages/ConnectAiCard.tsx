import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ChatGptIcon, ClaudeIcon, GeminiIcon } from '@/components/AiIcons'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { CHATGPT_PLUGINS_URL, claudeConnectorLink, GEMINI_URL, mcpUrl } from '@/lib/connectors'

/**
 * "Connect your AI": one button per AI app. Claude gets a link that prefills its connector
 * dialog; ChatGPT and Gemini have none, so their button copies Kitchen's URL and opens the right
 * page. The AI app then asks the person to sign in to Kitchen and to allow it (/consent).
 */
export function ConnectAiCard() {
  const { t } = useTranslation()
  const url = mcpUrl()
  const [copied, setCopied] = useState(false)

  function copy() {
    // Not awaited: the new tab must open within the click, or browsers block it.
    navigator.clipboard.writeText(url).then(
      () => setCopied(true),
      () => setCopied(false),
    )
  }

  function copyAndOpen(page: string) {
    copy()
    window.open(page, '_blank', 'noopener,noreferrer')
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('connect.title')}</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-5">
        <p className="text-sm text-muted-foreground">{t('connect.intro')}</p>
        <div className="flex flex-col items-center gap-2.5">
          <Button asChild variant="claude" size="sm">
            <a href={claudeConnectorLink(url)} target="_blank" rel="noopener noreferrer">
              <ClaudeIcon className="size-4" />
              {t('connect.claude')}
            </a>
          </Button>
          <p className="text-xs text-muted-foreground">{t('connect.claudeHint')}</p>
        </div>
        <div className="flex flex-col items-center gap-2.5">
          <Button variant="chatgpt" size="sm" onClick={() => copyAndOpen(CHATGPT_PLUGINS_URL)}>
            <ChatGptIcon className="size-4" />
            {t('connect.chatgpt')}
          </Button>
          <p className="text-xs text-muted-foreground">{t('connect.chatgptHint')}</p>
        </div>
        <div className="flex flex-col items-center gap-2.5">
          <Button variant="gemini" size="sm" onClick={() => copyAndOpen(GEMINI_URL)}>
            <GeminiIcon className="size-4" />
            {t('connect.gemini')}
          </Button>
          <p className="text-xs text-muted-foreground">{t('connect.geminiHint')}</p>
        </div>
        <Button variant="outline" size="sm" onClick={copy} className="self-center">
          {copied ? t('account.copied') : t('connect.copyUrl')}
        </Button>
      </CardContent>
    </Card>
  )
}
