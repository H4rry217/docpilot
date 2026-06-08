import { Check, CheckCircle2, Copy, MessageCircle, Send, X } from 'lucide-react'
import { useAiReviewSessionViewModel, type AiReviewStatus } from '../model/aiReviewSession'
import { useI18n } from '../../../shared/i18n'
import { useResizableWidth } from '../../../shared/ui/useResizableWidth'
import '../../../shared/ui/ResizablePanel.css'
import './AiReviewPanel.css'

export const AI_REVIEW_PANEL_DEFAULT_WIDTH = 320
export const AI_REVIEW_PANEL_MIN_WIDTH = 280
export const AI_REVIEW_PANEL_MAX_WIDTH = 520

function reviewStatusKey(status: AiReviewStatus) {
  switch (status) {
    case 'accepted':
      return 'ai.accepted'
    case 'rejected':
      return 'ai.rejected'
    case 'pending':
    default:
      return 'ai.pending'
  }
}

export function AiReviewPanel({
  width,
  onWidthChange
}: {
  width: number
  onWidthChange: (width: number) => void
}) {
  const { t } = useI18n()
  const reviewSession = useAiReviewSessionViewModel()
  const startResize = useResizableWidth({
    width,
    min: AI_REVIEW_PANEL_MIN_WIDTH,
    max: AI_REVIEW_PANEL_MAX_WIDTH,
    direction: 'right-panel',
    onChange: onWidthChange
  })

  return (
    <aside className="ai-review-panel" aria-label={t('ai.title')}>
      <button
        className="resize-handle resize-handle-right"
        type="button"
        aria-label="Resize review panel"
        onPointerDown={startResize}
      />

      <header className="ai-review-header">
        <div>
          <span>{t('ai.title')}</span>
          <strong>{t('ai.subtitle')}</strong>
        </div>
        <small>{t('ai.runtime', { duration: reviewSession.runtimeDuration })}</small>
      </header>

      <section className="review-card">
        <div className="review-card-title">
          <strong>{t('ai.queueTitle')}</strong>
          <span>{reviewSession.reviewItems.length}</span>
        </div>
        <div className="review-list">
          {reviewSession.reviewItems.map((item) => (
            <article className={`review-item review-${item.status}`} key={item.id}>
              <div>
                <strong>{t(item.labelKey)}</strong>
                <span>{t('ai.modify')}</span>
              </div>
              <span className="review-status">{t(reviewStatusKey(item.status))}</span>
              <div className="review-item-actions" aria-hidden="true">
                <button type="button" tabIndex={-1}>
                  <Check size={13} />
                </button>
                <button type="button" tabIndex={-1}>
                  <X size={13} />
                </button>
                <button type="button" tabIndex={-1}>
                  <MessageCircle size={13} />
                </button>
                <button type="button" tabIndex={-1}>
                  <Copy size={13} />
                </button>
              </div>
            </article>
          ))}
        </div>
        <textarea className="review-feedback" placeholder={t('ai.feedbackPlaceholder')} />
        <div className="review-batch-actions">
          <button type="button">{t('ai.rejectAll')}</button>
          <button type="button">{t('ai.submitFeedback')}</button>
          <button type="button">{t('ai.approveAll')}</button>
        </div>
        <button className="apply-approved-button" type="button">
          <CheckCircle2 size={15} />
          <span>{t('ai.applyApproved')}</span>
        </button>
      </section>

      <section className="agent-chat-shell">
        <div className="agent-session-pill">
          <span>{t('ai.session')}</span>
          <strong>{reviewSession.sessionId}</strong>
        </div>
        <label className="agent-chat-input">
          <textarea placeholder={t('ai.chatPlaceholder')} />
          <button type="button" aria-label={t('ai.submitFeedback')} title={t('ai.submitFeedback')}>
            <Send size={15} />
          </button>
        </label>
      </section>
    </aside>
  )
}
