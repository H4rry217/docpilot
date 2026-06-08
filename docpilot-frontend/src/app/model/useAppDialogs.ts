import { useState, type FormEvent } from 'react'

type PromptDialogState = {
  type: 'prompt'
  title: string
  label: string
  value: string
  confirmLabel: string
  resolve: (value?: string) => void
}

type ConfirmDialogState = {
  type: 'confirm'
  title: string
  message: string
  confirmLabel: string
  danger?: boolean
  resolve: (confirmed: boolean) => void
}

export type AppDialogState = PromptDialogState | ConfirmDialogState

export type RequestTextInput = {
  title: string
  label: string
  defaultValue: string
  confirmLabel: string
}

export type RequestConfirmInput = {
  title: string
  message: string
  confirmLabel: string
  danger?: boolean
}

export function useAppDialogs() {
  const [dialog, setDialog] = useState<AppDialogState | undefined>()

  function requestText(input: RequestTextInput): Promise<string | undefined> {
    return new Promise((resolve) => {
      setDialog({
        type: 'prompt',
        title: input.title,
        label: input.label,
        value: input.defaultValue,
        confirmLabel: input.confirmLabel,
        resolve
      })
    })
  }

  function requestConfirm(input: RequestConfirmInput): Promise<boolean> {
    return new Promise((resolve) => {
      setDialog({
        type: 'confirm',
        title: input.title,
        message: input.message,
        confirmLabel: input.confirmLabel,
        danger: input.danger,
        resolve
      })
    })
  }

  function handleDialogCancel() {
    if (!dialog) return
    if (dialog.type === 'prompt') {
      dialog.resolve(undefined)
    } else {
      dialog.resolve(false)
    }
    setDialog(undefined)
  }

  function handleDialogValueChange(value: string) {
    setDialog((current) => (current?.type === 'prompt' ? { ...current, value } : current))
  }

  function handleDialogSubmit(event: FormEvent) {
    event.preventDefault()
    if (!dialog) return

    if (dialog.type === 'prompt') {
      const value = dialog.value.trim()
      if (!value) return
      dialog.resolve(value)
    } else {
      dialog.resolve(true)
    }

    setDialog(undefined)
  }

  return {
    dialog,
    requestText,
    requestConfirm,
    handleDialogCancel,
    handleDialogValueChange,
    handleDialogSubmit
  }
}
