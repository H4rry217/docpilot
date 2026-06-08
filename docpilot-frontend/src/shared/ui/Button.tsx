import type { ButtonHTMLAttributes, ReactNode } from 'react'
import { Button as ShadcnButton } from '@/components/ui/button'
import { cn } from '@/lib/utils'

type ButtonVariant = 'ghost' | 'primary'

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  icon?: ReactNode
  variant?: ButtonVariant
}

export function Button({ children, icon, variant = 'primary', className, ...props }: ButtonProps) {
  return (
    <ShadcnButton
      className={cn(className)}
      type="button"
      variant={variant === 'primary' ? 'default' : 'outline'}
      {...props}
    >
      {icon ? <span data-icon="inline-start">{icon}</span> : null}
      {children}
    </ShadcnButton>
  )
}
