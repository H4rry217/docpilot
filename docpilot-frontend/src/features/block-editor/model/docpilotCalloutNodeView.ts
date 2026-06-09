import type { NodeViewRendererProps } from '@tiptap/core'
import type { NodeView, ViewMutationRecord } from '@tiptap/pm/view'
import {
  booleanAttr,
  renderedCalloutAttrs,
  setDomAttributes,
  stringAttr,
  type HtmlAttrs
} from './docpilotMarkdownExtensionUtils'

function calloutTitle(attributes: HtmlAttrs): string {
  return stringAttr(attributes, 'title', 'Details') || 'Details'
}

export function calloutNodeView(props: NodeViewRendererProps): NodeView {
  const attrs = props.node.attrs as HtmlAttrs
  const collapsible = booleanAttr(attrs.collapsible)
  const dom = document.createElement(collapsible ? 'details' : 'aside')
  const contentDOM = document.createElement('div')

  let summary: HTMLElement | null = null

  if (collapsible) {
    const details = dom as HTMLDetailsElement
    const initialOpen = booleanAttr(attrs.open, true)
    details.open = initialOpen
    details.toggleAttribute('open', initialOpen)
    setDomAttributes(details, renderedCalloutAttrs(props.HTMLAttributes, {
      class: 'docpilot-block docpilot-callout docpilot-callout-collapsible'
    }))

    summary = document.createElement('summary')
    summary.className = 'docpilot-callout-summary'
    summary.contentEditable = 'false'
    summary.textContent = calloutTitle(attrs)
    summary.addEventListener('click', (event) => {
      event.preventDefault()
      details.open = !details.open
      details.toggleAttribute('open', details.open)
    })

    contentDOM.className = 'docpilot-callout-body'
    details.append(summary, contentDOM)
  } else {
    setDomAttributes(dom, renderedCalloutAttrs(props.HTMLAttributes, {
      class: 'docpilot-block docpilot-callout'
    }))
    dom.append(contentDOM)
  }

  return {
    dom,
    contentDOM,
    update(nextNode) {
      if (nextNode.type.name !== props.node.type.name) return false
      const nextAttrs = nextNode.attrs as HtmlAttrs
      if (booleanAttr(nextAttrs.collapsible) !== collapsible) return false

      if (collapsible) {
        setDomAttributes(dom, renderedCalloutAttrs(nextAttrs, {
          class: 'docpilot-block docpilot-callout docpilot-callout-collapsible'
        }))
        if (summary) {
          summary.textContent = calloutTitle(nextAttrs)
        }
      } else {
        setDomAttributes(dom, renderedCalloutAttrs(nextAttrs, {
          class: 'docpilot-block docpilot-callout'
        }))
      }
      return true
    },
    ignoreMutation(mutation: ViewMutationRecord) {
      return mutation.type === 'attributes'
        && mutation.target === dom
        && mutation.attributeName === 'open'
    }
  }
}
