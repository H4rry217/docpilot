import { Send } from 'lucide-react'
import { useEffect, useRef } from 'react'
import { Button } from '@/components/ui/button'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Separator } from '@/components/ui/separator'
import { Textarea } from '@/components/ui/textarea'
import { cn } from '@/lib/utils'
import { useI18n } from '../../../shared/i18n'

type MockChatMessage = {
  id: string
  role: 'assistant' | 'user'
  textKey: 'ai.chat.assistantIntro' | 'ai.chat.userQuestion' | 'ai.chat.assistantReply'
}

const MOCK_CHAT_MESSAGES: MockChatMessage[] = [
  {
    id: 'assistant-intro',
    role: 'assistant',
    textKey: 'ai.chat.assistantIntro'
  },
  {
    id: 'user-question',
    role: 'user',
    textKey: 'ai.chat.userQuestion'
  },
  {
    id: 'assistant-reply',
    role: 'assistant',
    textKey: 'ai.chat.assistantReply'
  }
]

function ChatMessage({ message }: { message: MockChatMessage }) {
  const { t } = useI18n()
  const isAssistant = message.role === 'assistant'
  const text = t(message.textKey)

  if (isAssistant) {
    return (
      <article data-testid={`chat-message-${message.id}`} className="px-1 py-1 text-sm leading-6 text-foreground">
        <p className="whitespace-pre-wrap">{text}</p>
      </article>
    )
  }

  return (
    <article data-testid={`chat-message-${message.id}`} className="flex justify-end">
      <div
        className={cn(
          'max-w-[82%] rounded-lg bg-muted px-3 py-2 text-sm leading-6 text-foreground'
        )}
      >
        {text}
      </div>
    </article>
  )
}

export function AiChatPanelContent({ focusRequest = 0 }: { focusRequest?: number }) {
  const { t } = useI18n()
  const textareaRef = useRef<HTMLTextAreaElement | null>(null)

  useEffect(() => {
    if (focusRequest > 0) {
      textareaRef.current?.focus()
    }
  }, [focusRequest])

  return (
    <div className="flex min-h-0 flex-1 flex-col bg-background">
      <ScrollArea className="min-h-0 flex-1">
        <div className="flex flex-col gap-3 px-3 py-3">
          {MOCK_CHAT_MESSAGES.map((message) => (
            <ChatMessage key={message.id} message={message} />
          ))}
        </div>
      </ScrollArea>

      <Separator />

      <footer className="flex shrink-0 flex-col gap-2.5 p-3">
        <div
          data-testid="chat-composer"
          className="relative rounded-lg border bg-background transition-colors focus-within:border-ring/70"
        >
          <Textarea
            ref={textareaRef}
            className="min-h-20 resize-none border-0 bg-transparent pb-11 pr-12 shadow-none focus-visible:border-transparent focus-visible:ring-0"
            placeholder={t('ai.chat.inputPlaceholder')}
          />
          <Button
            data-testid="chat-send-button"
            type="button"
            size="icon-sm"
            className="absolute bottom-2 right-2"
            aria-label={t('ai.chat.sendMessage')}
            title={t('ai.chat.sendMessage')}
          >
            <Send />
          </Button>
        </div>
      </footer>
    </div>
  )
}
