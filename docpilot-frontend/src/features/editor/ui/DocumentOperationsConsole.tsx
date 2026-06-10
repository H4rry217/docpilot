import { Check, ChevronDown, ChevronUp, CircleHelp, Copy, Wrench } from 'lucide-react'
import {
  useMemo,
  useEffect,
  useRef,
  useState,
  type Dispatch,
  type FormEvent,
  type KeyboardEvent as ReactKeyboardEvent,
  type MutableRefObject,
  type SetStateAction
} from 'react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import {
  COMMAND_CATEGORY_KEYS,
  COMMAND_SUGGESTIONS,
  commandPrefixAtCaret,
  evaluateCommand,
  executableCommandFromInput,
  formatResultAnchor,
  isJsonLikeCommand,
  nextPlaceholderRange,
  previousPlaceholderRange,
  shouldOpenCommandSuggestions,
  type CommandEntry,
  type CommandSuggestion
} from './documentOperationsCommands'
import type { BlockDocument } from '../../../entities/block/types'
import { useI18n } from '../../../shared/i18n'
import './DocumentOperationsConsole.css'

export type DocumentOperationsConsoleProps = {
  className?: string
  controller: DocumentOperationsConsoleController
  getBlockDocument?: () => BlockDocument | null | undefined
  getDocumentVersion?: () => string | null | undefined
  onApplyBlockDocument?: (blockDocument: BlockDocument) => void
  onJumpToBlock?: (blockId: string) => void
}

export type DocumentOperationsConsoleController = {
  command: string
  setCommand: Dispatch<SetStateAction<string>>
  entries: CommandEntry[]
  setEntries: Dispatch<SetStateAction<CommandEntry[]>>
  suggestionsOpen: boolean
  setSuggestionsOpen: Dispatch<SetStateAction<boolean>>
  commandGuideOpen: boolean
  setCommandGuideOpen: Dispatch<SetStateAction<boolean>>
  composerOpen: boolean
  setComposerOpen: Dispatch<SetStateAction<boolean>>
  composerInitialBlockId: string | null
  expandedEntryIds: Set<number>
  setExpandedEntryIds: Dispatch<SetStateAction<Set<number>>>
  copiedBlockId: string | null
  setCopiedBlockId: Dispatch<SetStateAction<string | null>>
  activeSuggestionIndex: number
  setActiveSuggestionIndex: Dispatch<SetStateAction<number>>
  caretIndex: number
  setCaretIndex: Dispatch<SetStateAction<number>>
  nextEntryIdRef: MutableRefObject<number>
  copiedBlockTimeoutRef: MutableRefObject<number | undefined>
  openComposer: (blockId?: string | null) => void
}

const DEFAULT_EXPANDED_RESULT_LIMIT = 5

export function useDocumentOperationsConsoleState(): DocumentOperationsConsoleController {
  const [command, setCommand] = useState('')
  const [entries, setEntries] = useState<CommandEntry[]>([])
  const [suggestionsOpen, setSuggestionsOpen] = useState(false)
  const [commandGuideOpen, setCommandGuideOpen] = useState(false)
  const [composerOpen, setComposerOpen] = useState(false)
  const [composerInitialBlockId, setComposerInitialBlockId] = useState<string | null>(null)
  const [expandedEntryIds, setExpandedEntryIds] = useState<Set<number>>(() => new Set())
  const [copiedBlockId, setCopiedBlockId] = useState<string | null>(null)
  const [activeSuggestionIndex, setActiveSuggestionIndex] = useState(0)
  const [caretIndex, setCaretIndex] = useState(0)
  const nextEntryIdRef = useRef(1)
  const copiedBlockTimeoutRef = useRef<number | undefined>(undefined)

  function openComposer(blockId: string | null = null) {
    setComposerInitialBlockId(blockId)
    setComposerOpen(true)
  }

  useEffect(() => {
    return () => window.clearTimeout(copiedBlockTimeoutRef.current)
  }, [])

  return {
    command,
    setCommand,
    entries,
    setEntries,
    suggestionsOpen,
    setSuggestionsOpen,
    commandGuideOpen,
    setCommandGuideOpen,
    composerOpen,
    setComposerOpen,
    composerInitialBlockId,
    expandedEntryIds,
    setExpandedEntryIds,
    copiedBlockId,
    setCopiedBlockId,
    activeSuggestionIndex,
    setActiveSuggestionIndex,
    caretIndex,
    setCaretIndex,
    nextEntryIdRef,
    copiedBlockTimeoutRef,
    openComposer
  }
}

export function DocumentOperationsConsole({
  className,
  controller,
  getBlockDocument,
  getDocumentVersion,
  onApplyBlockDocument,
  onJumpToBlock
}: DocumentOperationsConsoleProps) {
  const { t } = useI18n()
  const inputRef = useRef<HTMLInputElement | null>(null)
  const outputRef = useRef<HTMLDivElement | null>(null)
  const suggestionRefs = useRef<Array<HTMLButtonElement | null>>([])
  const {
    command,
    setCommand,
    entries,
    setEntries,
    suggestionsOpen,
    setSuggestionsOpen,
    commandGuideOpen,
    setCommandGuideOpen,
    expandedEntryIds,
    setExpandedEntryIds,
    copiedBlockId,
    setCopiedBlockId,
    activeSuggestionIndex,
    setActiveSuggestionIndex,
    caretIndex,
    setCaretIndex,
    nextEntryIdRef,
    copiedBlockTimeoutRef,
    openComposer
  } = controller

  const commandIsJsonLike = isJsonLikeCommand(command)
  const suggestions = useMemo(() => {
    if (commandGuideOpen) return COMMAND_SUGGESTIONS
    if (commandIsJsonLike) return []
    if (!shouldOpenCommandSuggestions(command, caretIndex)) return []

    const needle = commandPrefixAtCaret(command, caretIndex).toLowerCase()
    if (!needle) return COMMAND_SUGGESTIONS

    return COMMAND_SUGGESTIONS.filter((suggestion) => {
      const category = t(COMMAND_CATEGORY_KEYS[suggestion.category]).toLowerCase()
      const description = t(suggestion.descriptionKey).toLowerCase()
      return suggestion.command.toLowerCase().includes(needle)
        || category.includes(needle)
        || description.includes(needle)
    })
  }, [caretIndex, command, commandGuideOpen, commandIsJsonLike, t])

  const suggestionsPanelOpen = commandGuideOpen || (suggestionsOpen && shouldOpenCommandSuggestions(command, caretIndex))
  const hasSuggestions = suggestions.length > 0
  const boundedActiveSuggestionIndex = hasSuggestions
    ? Math.min(activeSuggestionIndex, suggestions.length - 1)
    : 0

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const trimmedCommand = command.trim()
    const executableCommand = executableCommandFromInput(trimmedCommand)
    if (!executableCommand) return

    const feedback = evaluateCommand(executableCommand, {
      t,
      getBlockDocument,
      getDocumentVersion,
      onApplyBlockDocument
    })

    const entryId = nextEntryIdRef.current
    const shouldExpandResultsByDefault = Boolean(
      feedback.results?.length && feedback.results.length <= DEFAULT_EXPANDED_RESULT_LIMIT
    )

    setEntries((current) => [
      ...current,
      {
        id: entryId,
        command: trimmedCommand,
        feedback: feedback.message,
        results: feedback.results,
        status: feedback.status
      }
    ])
    setExpandedEntryIds(shouldExpandResultsByDefault ? new Set([entryId]) : new Set())
    nextEntryIdRef.current += 1
    setCommand('')
    setSuggestionsOpen(false)
    setCommandGuideOpen(false)
    setActiveSuggestionIndex(0)
    setCaretIndex(0)
  }

  function handleCommandChange(value: string, nextCaretIndex: number | null) {
    const resolvedCaretIndex = nextCaretIndex ?? value.length
    setCommand(value)
    setCommandGuideOpen(false)
    setSuggestionsOpen(shouldOpenCommandSuggestions(value, resolvedCaretIndex))
    setActiveSuggestionIndex(0)
    setCaretIndex(resolvedCaretIndex)
  }

  function applySuggestion(suggestion: CommandSuggestion) {
    setCommand(suggestion.usage)
    setSuggestionsOpen(false)
    setCommandGuideOpen(false)
    setActiveSuggestionIndex(0)
    selectPlaceholderInInput(suggestion.usage, 'first')
  }

  function toggleEntryResults(entryId: number) {
    setExpandedEntryIds((current) => {
      const next = new Set(current)
      if (next.has(entryId)) {
        next.delete(entryId)
      } else {
        next.add(entryId)
      }
      return next
    })
  }

  function copyBlockId(blockId: string) {
    void navigator.clipboard?.writeText(blockId)
    window.clearTimeout(copiedBlockTimeoutRef.current)
    setCopiedBlockId(blockId)
    copiedBlockTimeoutRef.current = window.setTimeout(() => {
      setCopiedBlockId(null)
    }, 1200)
  }

  function handleCommandKeyDown(event: ReactKeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Escape') {
      setSuggestionsOpen(false)
      setCommandGuideOpen(false)
      return
    }

    if (suggestionsPanelOpen) {
      if (event.key === 'ArrowDown' && hasSuggestions) {
        event.preventDefault()
        setActiveSuggestionIndex((index) => (index + 1) % suggestions.length)
      }

      if (event.key === 'ArrowUp' && hasSuggestions) {
        event.preventDefault()
        setActiveSuggestionIndex((index) => (index - 1 + suggestions.length) % suggestions.length)
      }

      if (event.key === 'Tab' && hasSuggestions) {
        event.preventDefault()
        applySuggestion(suggestions[boundedActiveSuggestionIndex])
      }

      if (event.key === 'Enter' && hasSuggestions && command.trim()) {
        event.preventDefault()
        applySuggestion(suggestions[boundedActiveSuggestionIndex])
      }

      return
    }

    if (event.key === 'Tab') {
      const selectionEdge = event.shiftKey
        ? event.currentTarget.selectionStart ?? 0
        : event.currentTarget.selectionEnd ?? command.length
      const range = event.shiftKey
        ? previousPlaceholderRange(command, selectionEdge)
        : nextPlaceholderRange(command, selectionEdge)

      if (range) {
        event.preventDefault()
        setSuggestionsOpen(false)
        setCommandGuideOpen(false)
        event.currentTarget.setSelectionRange(range.start, range.end)
        setCaretIndex(range.start)
      }
    }
  }

  function handleCommandSelection(input: HTMLInputElement) {
    setCaretIndex(input.selectionStart ?? input.value.length)
  }

  function selectPlaceholderInInput(value: string, direction: 'first' | 'next' | 'previous') {
    const range = direction === 'previous'
      ? previousPlaceholderRange(value, value.length + 1)
      : nextPlaceholderRange(value, 0)

    window.requestAnimationFrame(() => {
      const input = inputRef.current
      if (!input) return

      input.focus()
      setSuggestionsOpen(false)
      setCommandGuideOpen(false)

      if (range) {
        input.setSelectionRange(range.start, range.end)
        setCaretIndex(range.start)
      } else {
        input.setSelectionRange(value.length, value.length)
        setCaretIndex(value.length)
      }
    })
  }

  useEffect(() => {
    suggestionRefs.current.length = suggestions.length
  }, [suggestions.length])

  useEffect(() => {
    const output = outputRef.current
    if (!output) return
    output.scrollTop = output.scrollHeight
  }, [entries.length])

  useEffect(() => {
    if (!suggestionsPanelOpen || !hasSuggestions) return
    suggestionRefs.current[boundedActiveSuggestionIndex]?.scrollIntoView({
      block: 'nearest'
    })
  }, [boundedActiveSuggestionIndex, hasSuggestions, suggestionsPanelOpen])

  return (
    <div
      className={cn('document-operations-console-shell', className)}
      onClick={() => inputRef.current?.focus()}
    >
      <div className="document-operations-console">
            <div ref={outputRef} className="document-operations-console-output" role="log" aria-live="polite">
              {entries.map((entry) => {
                const resultsExpanded = expandedEntryIds.has(entry.id)

                return (
                  <div key={entry.id} className="document-operations-console-entry document-operations-entry">
                    <div className="document-operations-entry-header">
                      <span className="document-operations-entry-label">{t('developer.commandHistoryLabel')}</span>
                      <span className="document-operations-command-text">{entry.command}</span>
                      <span className={`document-operations-command-feedback document-operations-command-feedback--${entry.status}`}>
                        {entry.feedback}
                      </span>
                      {entry.results?.length ? (
                        <Button
                          type="button"
                          variant="ghost"
                          size="icon-xs"
                          className="document-operations-command-results-toggle"
                          aria-label={t(resultsExpanded ? 'developer.collapseResults' : 'developer.expandResults')}
                          aria-expanded={resultsExpanded}
                          title={t(resultsExpanded ? 'developer.collapseResults' : 'developer.expandResults')}
                          onClick={() => toggleEntryResults(entry.id)}
                        >
                          {resultsExpanded ? <ChevronUp aria-hidden="true" /> : <ChevronDown aria-hidden="true" />}
                        </Button>
                      ) : null}
                    </div>
                  {entry.results?.length && resultsExpanded ? (
                    <div className="document-operations-command-results">
                      {entry.results.map((result) => (
                        <div key={result.id} className="document-operations-command-result">
                          {result.blockId ? (
                            <span className="document-operations-command-result-anchor">
                              <button
                                type="button"
                                className="document-operations-command-result-label document-operations-command-result-block-link"
                                aria-label={t('developer.jumpToBlock', { blockId: result.blockId })}
                                title={t('developer.jumpToBlock', { blockId: result.blockId })}
                                onClick={() => {
                                  if (result.blockId) {
                                    onJumpToBlock?.(result.blockId)
                                  }
                                }}
                              >
                                {formatResultAnchor(result)}
                              </button>
                              <Button
                                type="button"
                                variant="ghost"
                                size="icon-xs"
                                className="document-operations-command-result-compose-button"
                                aria-label={t('developer.composer.openForBlock', { blockId: result.blockId })}
                                title={t('developer.composer.openForBlock', { blockId: result.blockId })}
                                onClick={() => {
                                  if (result.blockId) {
                                    openComposer(result.blockId)
                                  }
                                }}
                              >
                                <Wrench aria-hidden="true" />
                              </Button>
                              <Button
                                type="button"
                                variant="ghost"
                                size="icon-xs"
                                className="document-operations-command-result-copy-button"
                                aria-label={t(
                                  copiedBlockId === result.blockId ? 'developer.copiedBlockId' : 'developer.copyBlockId',
                                  { blockId: result.blockId }
                                )}
                                title={t(
                                  copiedBlockId === result.blockId ? 'developer.copiedBlockId' : 'developer.copyBlockId',
                                  { blockId: result.blockId }
                                )}
                                onClick={() => {
                                  if (result.blockId) {
                                    copyBlockId(result.blockId)
                                  }
                                }}
                              >
                                {copiedBlockId === result.blockId
                                  ? <Check aria-hidden="true" />
                                  : <Copy aria-hidden="true" />}
                              </Button>
                            </span>
                          ) : (
                            <span className="document-operations-command-result-label">{formatResultAnchor(result)}</span>
                          )}
                          {result.meta ? (
                            <span className="document-operations-command-result-meta">{result.meta}</span>
                          ) : null}
                          <span className="document-operations-command-result-text">{result.text}</span>
                        </div>
                      ))}
                    </div>
                  ) : null}
                </div>
                )
              })}
            </div>
            {suggestionsPanelOpen ? (
              <div className="document-operations-command-suggestions" aria-label={t('developer.commandSuggestions')}>
                <div className="document-operations-command-suggestions-header">{t('developer.commandSuggestions')}</div>
                {hasSuggestions ? (
                  suggestions.map((suggestion, index) => (
                    <button
                      ref={(element) => {
                        suggestionRefs.current[index] = element
                      }}
                      key={suggestion.command}
                      type="button"
                      className={cn(
                        'document-operations-command-suggestion',
                        index === boundedActiveSuggestionIndex && 'is-active'
                      )}
                      aria-label={t('developer.commandSuggestionLabel', { command: suggestion.command })}
                      onMouseEnter={() => setActiveSuggestionIndex(index)}
                      onMouseDown={(event) => event.preventDefault()}
                      onClick={() => applySuggestion(suggestion)}
                    >
                      <span className="document-operations-command-suggestion-main">
                        <span className="document-operations-command-suggestion-name">{suggestion.command}</span>
                        <span className="document-operations-command-suggestion-category">
                          {t(COMMAND_CATEGORY_KEYS[suggestion.category])}
                        </span>
                      </span>
                      <span className="document-operations-command-suggestion-description">{t(suggestion.descriptionKey)}</span>
                      <code className="document-operations-command-suggestion-usage">{suggestion.usage}</code>
                    </button>
                  ))
                ) : (
                  <div className="document-operations-command-suggestions-empty">
                    {t('developer.commandSuggestionsEmpty')}
                  </div>
                )}
              </div>
            ) : null}
            <form className="document-operations-command-line" onSubmit={handleSubmit}>
              <span className="document-operations-command-input-label">{t('developer.commandInputShort')}</span>
              <input
                ref={inputRef}
                className="document-operations-command-input"
                aria-label={t('developer.commandInput')}
                aria-autocomplete="list"
                aria-expanded={suggestionsPanelOpen}
                placeholder={t('developer.commandInputPlaceholder')}
                value={command}
                autoComplete="off"
                spellCheck={false}
                onBlur={() => {
                  setSuggestionsOpen(false)
                  setCommandGuideOpen(false)
                }}
                onChange={(event) => handleCommandChange(event.target.value, event.target.selectionStart)}
                onClick={(event) => handleCommandSelection(event.currentTarget)}
                onFocus={(event) => {
                  handleCommandSelection(event.currentTarget)
                  setSuggestionsOpen(shouldOpenCommandSuggestions(
                    event.currentTarget.value,
                    event.currentTarget.selectionStart ?? event.currentTarget.value.length
                  ))
                }}
                onKeyDown={handleCommandKeyDown}
                onKeyUp={(event) => handleCommandSelection(event.currentTarget)}
                onSelect={(event) => handleCommandSelection(event.currentTarget)}
              />
              <button
                type="button"
                className={cn('document-operations-command-guide-button', commandGuideOpen && 'is-active')}
                aria-label={t('developer.commandSuggestions')}
                title={t('developer.commandSuggestions')}
                aria-expanded={suggestionsPanelOpen}
                onMouseDown={(event) => event.preventDefault()}
                onClick={() => {
                  setCommandGuideOpen((open) => !open)
                  setSuggestionsOpen(false)
                  setActiveSuggestionIndex(0)
                  window.requestAnimationFrame(() => inputRef.current?.focus())
                }}
              >
                <CircleHelp aria-hidden="true" />
              </button>
            </form>
      </div>
    </div>
  )
}
