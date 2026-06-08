import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue
} from '@/components/ui/select'

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
  return (
    <Select value={value} onValueChange={onChange}>
      <SelectTrigger aria-label={label} className="w-full min-w-36">
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        <SelectGroup>
          {options.map((option) => (
            <SelectItem key={option.value} value={option.value}>
              {option.label}
            </SelectItem>
          ))}
        </SelectGroup>
      </SelectContent>
    </Select>
  )
}
