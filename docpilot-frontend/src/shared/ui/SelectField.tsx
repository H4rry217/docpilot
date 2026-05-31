import { Check, ChevronDown } from 'lucide-react'
import { useEffect, useId, useRef, useState } from 'react'
import './SelectField.css'

export type SelectFieldOption = {
  label: string
  value: string
}

export function SelectField({
  label,
  value,
  options,
  onChange
}: {
  label: string
  value: string
  options: SelectFieldOption[]
  onChange: (value: string) => void
}) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement | null>(null)
  const listboxId = useId()
  const selectedOption = options.find((option) => option.value === value) ?? options[0]

  useEffect(() => {
    if (!open) return

    function closeOnOutsidePointer(event: MouseEvent) {
      if (!rootRef.current?.contains(event.target as Node)) {
        setOpen(false)
      }
    }

    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') setOpen(false)
    }

    document.addEventListener('mousedown', closeOnOutsidePointer)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.removeEventListener('mousedown', closeOnOutsidePointer)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [open])

  return (
    <div className="select-field" ref={rootRef}>
      <button
        className={`select-field-trigger ${open ? 'open' : ''}`}
        type="button"
        aria-label={label}
        aria-expanded={open}
        aria-haspopup="listbox"
        aria-controls={listboxId}
        onClick={() => setOpen((current) => !current)}
      >
        <span>{selectedOption?.label}</span>
        <ChevronDown size={15} />
      </button>
      {open ? (
        <div className="select-field-menu" id={listboxId} role="listbox" aria-label={label}>
          {options.map((option) => {
            const selected = option.value === value
            return (
              <button
                className={selected ? 'selected' : ''}
                type="button"
                role="option"
                aria-selected={selected}
                key={option.value}
                onClick={() => {
                  onChange(option.value)
                  setOpen(false)
                }}
              >
                <span>{option.label}</span>
                {selected ? <Check size={14} /> : null}
              </button>
            )
          })}
        </div>
      ) : null}
    </div>
  )
}
