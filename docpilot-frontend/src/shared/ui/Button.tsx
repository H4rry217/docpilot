import type { ButtonHTMLAttributes, ReactNode } from 'react'

type ButtonVariant = 'ghost' | 'primary'

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  icon?: ReactNode
  variant?: ButtonVariant
}

export function Button({ children, icon, variant = 'primary', className, ...props }: ButtonProps) {
  const classNames = ['button', `button-${variant}`, className].filter(Boolean).join(' ')
  return (
    <button className={classNames} type="button" {...props}>
      {icon}
      <span>{children}</span>
    </button>
  )
}
