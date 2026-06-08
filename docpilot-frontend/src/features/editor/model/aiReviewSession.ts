export type AiReviewStatus = 'accepted' | 'rejected' | 'pending'

export type AiReviewLabelKey =
  | 'ai.listItem5'
  | 'ai.paragraph8'
  | 'ai.paragraph10'
  | 'ai.html15'
  | 'ai.quote25'

export type AiReviewItemViewModel = {
  id: string
  labelKey: AiReviewLabelKey
  status: AiReviewStatus
}

export type AiReviewSessionViewModel = {
  sessionId: string
  runtimeDuration: string
  reviewItems: AiReviewItemViewModel[]
}

const MOCK_REVIEW_SESSION: AiReviewSessionViewModel = {
  sessionId: '4f5f426f-8...7243ff36',
  runtimeDuration: '28s',
  reviewItems: [
    { id: 'list-item-5', labelKey: 'ai.listItem5', status: 'rejected' },
    { id: 'paragraph-8', labelKey: 'ai.paragraph8', status: 'pending' },
    { id: 'paragraph-10', labelKey: 'ai.paragraph10', status: 'pending' },
    { id: 'html-15', labelKey: 'ai.html15', status: 'pending' },
    { id: 'quote-25', labelKey: 'ai.quote25', status: 'pending' }
  ]
}

export function useAiReviewSessionViewModel(): AiReviewSessionViewModel {
  return MOCK_REVIEW_SESSION
}
