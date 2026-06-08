import { createContext, useContext, useMemo, useState, type ReactNode } from 'react'

const STORAGE_KEY = 'docpilot.locale'

export type Locale = 'zh-CN' | 'en-US'

const zh = {
  'app.loading': '正在进入 DocPilot',
  'activity.files': '文件',
  'activity.outline': '大纲',
  'activity.settings': '设置',
  'activity.language': '语言',
  'auth.brandSubtitle': '默认用户体系',
  'auth.createAccount': '创建账号',
  'auth.displayName': '显示名',
  'auth.email': '邮箱',
  'auth.failed': '认证失败',
  'auth.login': '登录',
  'auth.mode': '认证模式',
  'auth.panel': 'DocPilot 用户认证',
  'auth.password': '密码',
  'auth.processing': '处理中',
  'auth.register': '注册',
  'auth.registerSuccess': '注册成功，请登录',
  'settings.general': '通用',
  'settings.login': '登录设置',
  'sidebar.workspace': '工作区',
  'sidebar.noDocument': '未选择文档',
  'sidebar.outlineTitle': '文档结构',
  'sidebar.outlineIntro': '选择文档后查看标题结构。',
  'sidebar.outlineEmpty': '当前文档暂无标题。',
  'sidebar.outlineUntitled': '未命名标题',
  'sidebar.outlineExpand': '展开 {title}',
  'sidebar.outlineCollapse': '折叠 {title}',
  'sidebar.outlineHeading': '标题',
  'sidebar.outlineParagraph': '段落',
  'sidebar.outlineHtml': 'HTML Block',
  'sidebar.settingsTitle': '设置',
  'sidebar.languageTitle': '界面语言',
  'workspace.newFolder': '新建文件夹',
  'workspace.newDocument': '新建文档',
  'workspace.rename': '重命名',
  'workspace.delete': '删除',
  'workspace.empty': '暂无文档',
  'workspace.listTitle': '工作区',
  'workspace.newWorkspace': '新建工作区',
  'workspace.renameWorkspace': '重命名工作区',
  'workspace.deleteWorkspace': '删除工作区',
  'workspace.emptyWorkspaces': '暂无工作区',
  'workspace.uploadMarkdownOnly': '请拖入 .md 或 .markdown 文件。',
  'workspace.uploadingMarkdown': '正在上传 {count} 个 Markdown 文件...',
  'workspace.uploadMarkdownDone': '已上传 {count} 个 Markdown 文件。',
  'workspace.uploadMarkdownFailed': 'Markdown 上传失败',
  'workspace.uploadMarkdownFailedWithReason': 'Markdown 上传失败：{reason}',
  'dialog.cancel': '取消',
  'dialog.confirm': '确定',
  'prompt.workspaceName': '工作区名称',
  'prompt.workspaceDefault': '新工作区',
  'prompt.folderName': '文件夹名称',
  'prompt.folderDefault': '新建文件夹',
  'prompt.documentTitle': '文档标题',
  'prompt.documentDefault': '未命名',
  'prompt.nodeName': '名称',
  'prompt.deleteNode': '删除 {name}？',
  'prompt.deleteWorkspace': '删除工作区 {name}？',
  'editor.noDocument': '未选择文档',
  'editor.notSaved': '尚未保存',
  'editor.updated': '最近修改：{time}',
  'editor.sideNote': '请在右侧修改下填写改进意见',
  'editor.save': '保存',
  'editor.share': '分享',
  'editor.blockMode': 'Block 模式',
  'editor.saved': '已保存',
  'editor.saving': '保存中',
  'editor.dirty': '有未保存修改',
  'editor.saveIdle': '等待编辑',
  'editor.saveFailed': '保存失败',
  'editor.opening': '正在打开文档',
  'editor.loadFailed': '文档加载失败',
  'editor.selectOrCreate': '选择或创建一个文档',
  'editor.pasteUnsaved': '粘贴内容尚未保存',
  'ai.title': 'AI 状态',
  'ai.subtitle': 'Document Agent 对话',
  'ai.runtime': '运行中 · {duration}',
  'ai.queueTitle': '修改列表',
  'ai.feedbackPlaceholder': '输入改进意见，例如：保留第一段，只修复乱码',
  'ai.rejectAll': '拒绝全部',
  'ai.submitFeedback': '提交改进意见',
  'ai.approveAll': '全部批准',
  'ai.applyApproved': '应用已批准',
  'ai.chatPlaceholder': '请先处理修改预览：确认、拒绝或提交改进意见',
  'ai.session': '会话 ID',
  'ai.modify': '修改',
  'ai.accepted': '已接受',
  'ai.rejected': '已拒绝',
  'ai.pending': '待审阅',
  'ai.listItem5': '列表项 5',
  'ai.paragraph8': '段落 8',
  'ai.paragraph10': '段落 10',
  'ai.html15': 'HTML Block 15',
  'ai.quote25': '引用 25',
  'ai.workspace.label': 'AI 对话',
  'ai.workspace.controlBar': 'AI 对话窗口控制栏',
  'ai.workspace.switchToFloating': '切换为浮窗',
  'ai.workspace.dockRight': '停靠到右侧',
  'ai.workspace.minimize': '最小化',
  'ai.workspace.restore': '恢复 AI 对话',
  'ai.workspace.resizeDock': '调整 AI 对话栏宽度',
  'ai.workspace.resizeFloating': '调整 AI 对话浮窗大小',
  'ai.chat.assistantIntro': '我可以帮你梳理文档、解释修改、生成改写建议。',
  'ai.chat.userQuestion': '帮我检查这份产品说明还有哪些地方需要补充。',
  'ai.chat.assistantReply': '可以。等接入真实模型后，我会基于当前文档上下文给出建议。',
  'ai.chat.inputPlaceholder': '输入你的问题或指令...',
  'ai.chat.sendMessage': '发送消息',
  'profile.currentUser': '当前用户',
  'profile.defaultUser': 'DocPilot 用户',
  'profile.logout': '退出登录',
  'profile.displayName': '显示名',
  'profile.currentPassword': '当前密码',
  'profile.newPassword': '新密码',
  'profile.save': '保存',
  'profile.changePassword': '修改密码',
  'profile.nameUpdated': '显示名已更新',
  'profile.passwordUpdated': '密码已更新',
  'profile.updateFailed': '更新失败',
  'html.preview': '预览',
  'html.source': '源码',
  'html.fixed': '固定',
  'html.auto': '自适应'
} as const

type TranslationKey = keyof typeof zh

const en: Record<TranslationKey, string> = {
  'app.loading': 'Entering DocPilot',
  'activity.files': 'Files',
  'activity.outline': 'Outline',
  'activity.settings': 'Settings',
  'activity.language': 'Language',
  'auth.brandSubtitle': 'Default user system',
  'auth.createAccount': 'Create account',
  'auth.displayName': 'Display name',
  'auth.email': 'Email',
  'auth.failed': 'Authentication failed',
  'auth.login': 'Log in',
  'auth.mode': 'Authentication mode',
  'auth.panel': 'DocPilot user authentication',
  'auth.password': 'Password',
  'auth.processing': 'Processing',
  'auth.register': 'Register',
  'auth.registerSuccess': 'Registration complete. Please log in.',
  'settings.general': 'General',
  'settings.login': 'Login settings',
  'sidebar.workspace': 'Workspace',
  'sidebar.noDocument': 'No document selected',
  'sidebar.outlineTitle': 'Document outline',
  'sidebar.outlineIntro': 'Select a document to inspect its heading structure.',
  'sidebar.outlineEmpty': 'This document has no headings.',
  'sidebar.outlineUntitled': 'Untitled heading',
  'sidebar.outlineExpand': 'Expand {title}',
  'sidebar.outlineCollapse': 'Collapse {title}',
  'sidebar.outlineHeading': 'Heading',
  'sidebar.outlineParagraph': 'Paragraph',
  'sidebar.outlineHtml': 'HTML Block',
  'sidebar.settingsTitle': 'Settings',
  'sidebar.languageTitle': 'Interface language',
  'workspace.newFolder': 'New folder',
  'workspace.newDocument': 'New document',
  'workspace.rename': 'Rename',
  'workspace.delete': 'Delete',
  'workspace.empty': 'No documents yet',
  'workspace.listTitle': 'Workspaces',
  'workspace.newWorkspace': 'New workspace',
  'workspace.renameWorkspace': 'Rename workspace',
  'workspace.deleteWorkspace': 'Delete workspace',
  'workspace.emptyWorkspaces': 'No workspaces yet',
  'workspace.uploadMarkdownOnly': 'Drop .md or .markdown files.',
  'workspace.uploadingMarkdown': 'Uploading {count} Markdown file(s)...',
  'workspace.uploadMarkdownDone': 'Uploaded {count} Markdown file(s).',
  'workspace.uploadMarkdownFailed': 'Markdown upload failed',
  'workspace.uploadMarkdownFailedWithReason': 'Markdown upload failed: {reason}',
  'dialog.cancel': 'Cancel',
  'dialog.confirm': 'Confirm',
  'prompt.workspaceName': 'Workspace name',
  'prompt.workspaceDefault': 'New workspace',
  'prompt.folderName': 'Folder name',
  'prompt.folderDefault': 'New Folder',
  'prompt.documentTitle': 'Document title',
  'prompt.documentDefault': 'Untitled',
  'prompt.nodeName': 'Name',
  'prompt.deleteNode': 'Delete {name}?',
  'prompt.deleteWorkspace': 'Delete workspace {name}?',
  'editor.noDocument': 'No document selected',
  'editor.notSaved': 'Not saved yet',
  'editor.updated': 'Updated {time}',
  'editor.sideNote': 'Add feedback under the change on the right',
  'editor.save': 'Save',
  'editor.share': 'Share',
  'editor.blockMode': 'Block mode',
  'editor.saved': 'Saved',
  'editor.saving': 'Saving',
  'editor.dirty': 'Unsaved changes',
  'editor.saveIdle': 'Waiting for edits',
  'editor.saveFailed': 'Save failed',
  'editor.opening': 'Opening document',
  'editor.loadFailed': 'Document failed to load',
  'editor.selectOrCreate': 'Select or create a document',
  'editor.pasteUnsaved': 'Pasted content has not been saved',
  'ai.title': 'AI Status',
  'ai.subtitle': 'Document Agent',
  'ai.runtime': 'Running · {duration}',
  'ai.queueTitle': 'Review queue',
  'ai.feedbackPlaceholder': 'Add feedback, e.g. keep the first paragraph and only fix mojibake',
  'ai.rejectAll': 'Reject all',
  'ai.submitFeedback': 'Submit feedback',
  'ai.approveAll': 'Approve all',
  'ai.applyApproved': 'Apply approved',
  'ai.chatPlaceholder': 'Review the preview first: accept, reject, or submit feedback',
  'ai.session': 'Session ID',
  'ai.modify': 'Modify',
  'ai.accepted': 'Accepted',
  'ai.rejected': 'Rejected',
  'ai.pending': 'Pending review',
  'ai.listItem5': 'List item 5',
  'ai.paragraph8': 'Paragraph 8',
  'ai.paragraph10': 'Paragraph 10',
  'ai.html15': 'HTML Block 15',
  'ai.quote25': 'Quote 25',
  'ai.workspace.label': 'AI chat',
  'ai.workspace.controlBar': 'AI chat window controls',
  'ai.workspace.switchToFloating': 'Switch to floating window',
  'ai.workspace.dockRight': 'Dock to right side',
  'ai.workspace.minimize': 'Minimize',
  'ai.workspace.restore': 'Restore AI chat',
  'ai.workspace.resizeDock': 'Resize AI chat sidebar',
  'ai.workspace.resizeFloating': 'Resize AI chat window',
  'ai.chat.assistantIntro': 'I can help organize documents, explain changes, and draft rewrite suggestions.',
  'ai.chat.userQuestion': 'Help me check what else this product spec needs.',
  'ai.chat.assistantReply': 'Sure. Once the real model is connected, I will make suggestions based on the current document context.',
  'ai.chat.inputPlaceholder': 'Ask a question or enter a command...',
  'ai.chat.sendMessage': 'Send message',
  'profile.currentUser': 'Current user',
  'profile.defaultUser': 'DocPilot User',
  'profile.logout': 'Log out',
  'profile.displayName': 'Display name',
  'profile.currentPassword': 'Current password',
  'profile.newPassword': 'New password',
  'profile.save': 'Save',
  'profile.changePassword': 'Change password',
  'profile.nameUpdated': 'Display name updated',
  'profile.passwordUpdated': 'Password updated',
  'profile.updateFailed': 'Update failed',
  'html.preview': 'Preview',
  'html.source': 'Source',
  'html.fixed': 'Fixed',
  'html.auto': 'Auto'
}

const dictionaries = {
  'zh-CN': zh,
  'en-US': en
}

type I18nContextValue = {
  locale: Locale
  setLocale: (locale: Locale) => void
  t: (key: TranslationKey, values?: Record<string, string>) => string
}

const I18nContext = createContext<I18nContextValue | null>(null)

function isLocale(value: string | null | undefined): value is Locale {
  return value === 'zh-CN' || value === 'en-US'
}

function initialLocale(): Locale {
  const stored = globalThis.localStorage?.getItem(STORAGE_KEY)
  return isLocale(stored) ? stored : 'zh-CN'
}

function interpolate(template: string, values?: Record<string, string>): string {
  if (!values) return template
  return Object.entries(values).reduce((text, [key, value]) => text.replaceAll(`{${key}}`, value), template)
}

export function I18nProvider({ children }: { children: ReactNode }) {
  const [locale, setLocaleState] = useState<Locale>(initialLocale)

  const value = useMemo<I18nContextValue>(() => {
    function setLocale(nextLocale: Locale) {
      setLocaleState(nextLocale)
      globalThis.localStorage?.setItem(STORAGE_KEY, nextLocale)
    }

    return {
      locale,
      setLocale,
      t: (key, values) => interpolate(dictionaries[locale][key], values)
    }
  }, [locale])

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>
}

export function useI18n(): I18nContextValue {
  const value = useContext(I18nContext)
  if (!value) {
    throw new Error('useI18n must be used inside I18nProvider')
  }
  return value
}
