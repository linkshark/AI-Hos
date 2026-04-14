<template>
  <div
    class="page-shell"
    :class="{
      'mobile-viewport': isMobileViewport,
      'wechat-mobile': isWeChatMobile,
      'keyboard-open': keyboardOpen,
      'sidebar-collapsed': isDesktopSidebarCollapsed,
    }"
    :style="pageShellStyle"
  >
    <aside class="brand-panel" :class="{ collapsed: isDesktopSidebarCollapsed }">
      <div class="brand-card">
        <div class="brand-lockup">
          <img :src="shulanLogo" alt="杭州树兰医院" class="brand-logo" />
          <div v-if="!isDesktopSidebarCollapsed" class="brand-copy">
            <p class="brand-eyebrow">Hangzhou Shulan Hospital</p>
            <h1 class="brand-title">杭州树兰医院智能导诊台</h1>
            <p class="brand-subtitle">院内问答与导诊</p>
          </div>
        </div>
        <div v-if="!isDesktopSidebarCollapsed" class="brand-model-banner">
          <div :class="['model-logo-shell', `model-logo-shell-${currentModelOption.logoKind}`]" aria-hidden="true">
            <svg
              v-if="currentModelOption.logoKind === 'local'"
              class="model-logo-svg"
              viewBox="0 0 24 24"
            >
              <path d="M13 2 4 14h6l-1 8 9-12h-6z" />
            </svg>
            <svg
              v-else-if="currentModelOption.logoKind === 'fast'"
              class="model-logo-svg"
              viewBox="0 0 24 24"
            >
              <path d="M3 11.5 20.5 4l-4.8 16-3.8-5.7L3 11.5Z" />
              <path d="M11.9 14.1 20.5 4" />
            </svg>
            <svg
              v-else
              class="model-logo-svg"
              viewBox="0 0 24 24"
            >
              <path d="m12 5 7 4-7 4-7-4 7-4Z" />
              <path d="m5 12 7 4 7-4" />
              <path d="m5 15 7 4 7-4" />
            </svg>
          </div>
          <div class="brand-model-copy">
            <span>{{ currentModelOption.modeLabel }}</span>
            <strong>{{ currentModelOption.shortLabel }}</strong>
          </div>
        </div>
        <div class="brand-card-head">
          <button
            v-if="!isMobileViewport"
            class="sidebar-toggle-button"
            type="button"
            @click="toggleDesktopSidebar"
          >
            {{ isDesktopSidebarCollapsed ? '展开侧栏' : '收起侧栏' }}
          </button>
        </div>
        <div v-if="!isDesktopSidebarCollapsed" class="brand-status-grid">
          <div class="brand-status-item">
            <span>引擎</span>
            <strong>{{ currentModelOption.modeShortLabel }}</strong>
            <small>{{ currentModelShortLabel }}</small>
          </div>
          <div class="brand-status-item">
            <span>检索</span>
            <strong>RAG</strong>
          </div>
          <div class="brand-status-item">
            <span>知识</span>
            <strong>{{ knowledgeCount }}</strong>
          </div>
          <div class="brand-status-item">
            <span>状态</span>
            <strong>{{ isSending ? '处理中' : '在线' }}</strong>
          </div>
        </div>
      </div>

      <div class="control-card">
        <div class="control-header">
          <div v-if="!isDesktopSidebarCollapsed" class="control-header-copy">
            <span>会话与知识库</span>
          </div>
        </div>
        <div
          v-if="!isDesktopSidebarCollapsed"
          class="session-badge"
          :title="uuid ? `当前会话 ID：${uuid}` : '当前会话 ID 暂未生成'"
        >
          {{ currentSessionDisplayId }}
        </div>
        <div class="control-actions" :class="{ compact: isDesktopSidebarCollapsed }">
          <el-button class="primary-action" @click="newChat">
            {{ isDesktopSidebarCollapsed ? '新建' : '新建会话' }}
          </el-button>
          <el-button
            class="secondary-action"
            @click="openKnowledgeCenter"
          >
            {{ isDesktopSidebarCollapsed ? '知识库' : '知识库管理' }}
          </el-button>
        </div>
        <div v-if="!isDesktopSidebarCollapsed" class="knowledge-mini-grid">
          <button class="knowledge-mini-item knowledge-mini-item-button knowledge-mini-item-highlight" type="button" @click="openKnowledgeCenter">
            <span>文件数</span>
            <strong>{{ knowledgeCount }}</strong>
          </button>
          <div class="knowledge-mini-item">
            <span>格式</span>
            <strong>PDF/Office</strong>
          </div>
        </div>
        <p v-if="!isDesktopSidebarCollapsed" class="knowledge-summary">进入知识库页，管理文件并查看 RAG 切分。</p>
      </div>

      <div
        v-if="!isDesktopSidebarCollapsed"
        :class="['history-card', { 'history-card-expanded': !showDesktopQuickPrompts }]"
      >
        <div class="section-heading history-heading">
          <span>历史对话</span>
          <div class="history-heading-actions">
            <button
              v-if="isAdmin"
              class="history-debug-chip"
              type="button"
              :class="{ active: historyDebugMode }"
              @click="toggleHistoryDebugMode"
            >
              {{ historyDebugMode ? '原始视图' : '原始' }}
            </button>
            <button class="history-toggle-button" type="button" @click="toggleHistoryExpanded">
              {{ chatHistoryExpanded ? '收起' : '展开' }}
            </button>
          </div>
        </div>
        <div v-if="chatHistoryExpanded" class="history-list-shell">
          <div class="history-search-shell">
            <input
              v-model.trim="historyQuery"
              class="history-search-input"
              type="text"
              placeholder="搜索历史对话"
            />
          </div>
          <div v-if="historyTotal > 0" class="history-search-meta">
            共 {{ historyTotal }} 条
          </div>
          <div v-if="historyDebugMode" class="history-debug-banner">
            当前展示模型原始会话内容，仅供管理员排查使用。
          </div>
          <div v-if="historyLoading" class="history-empty-state">正在加载历史会话…</div>
          <div v-else-if="!filteredPinnedHistoryItems.length && !filteredRegularHistoryItems.length" class="history-empty-state">
            {{ historyQuery.trim() ? '没有匹配的历史对话' : '当前账号还没有历史对话' }}
          </div>
          <div v-else class="history-list">
            <div v-if="filteredPinnedHistoryItems.length" class="history-group">
              <div class="history-group-label">置顶对话</div>
              <div
                v-for="item in filteredPinnedHistoryItems"
                :key="`pinned-${item.memoryId}`"
                :class="['history-item', { active: String(item.memoryId) === String(uuid), loading: restoringHistoryMemoryId === item.memoryId }]"
                role="button"
                tabindex="0"
                @click="restoreChatHistory(item.memoryId)"
                @keydown.enter.prevent="restoreChatHistory(item.memoryId)"
                @keydown.space.prevent="restoreChatHistory(item.memoryId)"
              >
                <div class="history-item-head">
                  <strong v-if="renamingHistoryId !== item.memoryId" :title="item.title">{{ item.title }}</strong>
                  <input
                    v-else
                    v-model.trim="renamingHistoryTitle"
                    class="history-rename-input"
                    type="text"
                    maxlength="128"
                    @click.stop
                    @keydown.enter.prevent="submitRenameHistory(item)"
                    @keydown.esc.prevent="cancelRenameHistory"
                  />
                  <div class="history-item-actions">
                    <el-dropdown trigger="click" @command="(command) => handleHistoryCommand(command, item)">
                      <button class="history-item-menu" type="button" @click.stop>
                        ⋯
                      </button>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item command="unpin">取消置顶</el-dropdown-item>
                          <el-dropdown-item command="share">分享</el-dropdown-item>
                          <el-dropdown-item command="rename">重命名</el-dropdown-item>
                          <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                  </div>
                </div>
                <div v-if="renamingHistoryId === item.memoryId" class="history-rename-actions">
                  <button type="button" @click.stop="submitRenameHistory(item)">保存</button>
                  <button type="button" @click.stop="cancelRenameHistory">取消</button>
                </div>
              </div>
            </div>

            <div v-if="filteredRegularHistoryItems.length" class="history-group">
              <div class="history-group-label">历史对话</div>
              <div
                v-for="item in filteredRegularHistoryItems"
                :key="`history-${item.memoryId}`"
                :class="['history-item', { active: String(item.memoryId) === String(uuid), loading: restoringHistoryMemoryId === item.memoryId }]"
                role="button"
                tabindex="0"
                @click="restoreChatHistory(item.memoryId)"
                @keydown.enter.prevent="restoreChatHistory(item.memoryId)"
                @keydown.space.prevent="restoreChatHistory(item.memoryId)"
              >
                <div class="history-item-head">
                  <strong v-if="renamingHistoryId !== item.memoryId" :title="item.title">{{ item.title }}</strong>
                  <input
                    v-else
                    v-model.trim="renamingHistoryTitle"
                    class="history-rename-input"
                    type="text"
                    maxlength="128"
                    @click.stop
                    @keydown.enter.prevent="submitRenameHistory(item)"
                    @keydown.esc.prevent="cancelRenameHistory"
                  />
                  <div class="history-item-actions">
                    <el-dropdown trigger="click" @command="(command) => handleHistoryCommand(command, item)">
                      <button class="history-item-menu" type="button" @click.stop>
                        ⋯
                      </button>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item command="pin">置顶</el-dropdown-item>
                          <el-dropdown-item command="share">分享</el-dropdown-item>
                          <el-dropdown-item command="rename">重命名</el-dropdown-item>
                          <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                  </div>
                </div>
                <div v-if="renamingHistoryId === item.memoryId" class="history-rename-actions">
                  <button type="button" @click.stop="submitRenameHistory(item)">保存</button>
                  <button type="button" @click.stop="cancelRenameHistory">取消</button>
                </div>
              </div>
            </div>
          </div>
          <div v-if="historyTotal > historyPageSize" class="history-pagination">
            <button
              type="button"
              class="history-pagination-button"
              :disabled="historyPage <= 1 || historyLoading"
              @click="changeHistoryPage(historyPage - 1)"
            >
              上一页
            </button>
            <span class="history-pagination-text">{{ historyPage }} / {{ historyPageCount }}</span>
            <button
              type="button"
              class="history-pagination-button"
              :disabled="historyPage >= historyPageCount || historyLoading"
              @click="changeHistoryPage(historyPage + 1)"
            >
              下一页
            </button>
          </div>
        </div>
      </div>

      <div v-if="showDesktopQuickPrompts" class="suggestion-card">
        <div class="section-heading">
          <span>快捷指令</span>
        </div>
        <div class="prompt-grid">
          <button
            v-for="prompt in quickPrompts"
            :key="prompt.title"
            class="prompt-chip"
            type="button"
            @click="sendQuickPrompt(prompt.content)"
          >
            <span class="prompt-title">{{ prompt.title }}</span>
            <span class="prompt-description">{{ prompt.description }}</span>
          </button>
        </div>
      </div>
    </aside>

    <main class="workspace">
      <section v-if="!isMobileViewport" class="hero-panel">
        <div class="hero-bar">
          <div class="hero-identity">
            <strong>杭州树兰医院智能问答中心</strong>
          </div>
          <span class="hero-assistant-title">树兰 AI 陪诊助手</span>
          <div class="hero-inline-actions">
            <button
              class="header-export-button hero-export-button"
              type="button"
              :disabled="isSending"
              @click="exportConversation"
            >
              导出对话数据
            </button>
            <span class="status-pill">
              <span class="status-dot" :class="{ sending: isSending }"></span>
              {{ isSending ? '正在生成回复' : '可开始提问' }}
            </span>
            <AdminEntryActionButton v-if="isAdmin" @click="openAdminConsole" />
            <LogoutActionButton @click="handleLogout" />
          </div>
          <button class="mobile-panel-trigger" type="button" @click="mobilePanelVisible = true">
            服务面板
          </button>
        </div>
      </section>

      <section ref="chatPanelRef" class="chat-panel">
        <header v-if="isMobileViewport" class="chat-header chat-header-mobile">
          <div class="mobile-chat-toolbar">
            <button
              v-if="isAdmin"
              class="mobile-toolbar-button mobile-admin-button"
              type="button"
              @click="openAdminConsole"
            >
              后台
            </button>
            <button
              class="mobile-toolbar-button mobile-export-button"
              type="button"
              :disabled="isSending"
              @click="exportConversation"
            >
              导出
            </button>
            <LogoutActionButton
              class="mobile-chat-logout"
              label="退出"
              @click="handleLogout"
            />
          </div>
        </header>

        <div
          class="message-list"
          ref="messageListRef"
          @scroll="handleMessageListScroll"
        >
          <div
            v-for="(message, index) in messages"
            :key="index"
            :class="messageClass(message)"
          >
            <div class="message-avatar">
              <i :class="messageIcon(message)"></i>
            </div>
            <div class="message-body">
              <span class="message-role">{{ messageRole(message) }}</span>
              <div
                v-if="message.attachments?.length"
                class="message-attachment-gallery"
                :class="{ 'message-attachment-gallery-user': message.isUser }"
              >
                <article
                  v-for="attachment in message.attachments"
                  :key="attachment.id"
                  class="message-attachment-card"
                  :class="{ 'message-attachment-card-image': attachment.isImage }"
                >
                  <img
                    v-if="attachment.isImage && attachment.previewUrl"
                    :src="attachment.previewUrl"
                    :alt="attachment.name"
                    class="message-attachment-image"
                  />
                  <div v-else class="message-attachment-file">
                    <span class="message-attachment-badge">{{ attachment.badgeLabel || attachment.kindLabel }}</span>
                    <strong>{{ attachment.name }}</strong>
                    <span v-if="attachment.sizeLabel" class="message-attachment-size">{{ attachment.sizeLabel }}</span>
                  </div>
                  <div
                    v-if="attachment.isImage"
                    class="message-attachment-caption"
                  >
                    <strong>{{ attachment.name }}</strong>
                    <span v-if="attachment.sizeLabel">{{ attachment.sizeLabel }}</span>
                  </div>
                </article>
              </div>
              <div class="message-content" v-html="message.content"></div>
              <div v-if="groupedCitations(message).length" class="message-citation-inline">
                <span class="message-citation-inline-label">引用</span>
                <el-popover
                  v-for="citation in groupedCitations(message)"
                  :key="citation.key"
                  trigger="click"
                  placement="top"
                  :width="320"
                >
                  <template #reference>
                    <button class="message-citation-chip" type="button">
                      {{ compactCitationLabel(citation) }}
                    </button>
                  </template>
                  <div class="message-citation-popover">
                    <div class="message-citation-popover-head">
                      <strong>{{ citation.documentName }}</strong>
                      <span class="message-citation-type">{{ citation.retrievalTypeLabel }}</span>
                    </div>
                    <small>
                      <template v-if="citation.version">版本 {{ citation.version }}</template>
                      <template v-if="citation.updatedAt"> · 更新于 {{ citation.updatedAt }}</template>
                      <template v-else-if="citation.effectiveAt"> · 生效于 {{ citation.effectiveAt }}</template>
                    </small>
                    <ul>
                      <li v-for="snippet in citation.snippets" :key="snippet">{{ snippet }}</li>
                    </ul>
                    <button
                      v-if="citation.fileHash"
                      class="message-citation-link"
                      type="button"
                      @click="openCitationKnowledge(citation)"
                    >
                      查看知识详情
                    </button>
                  </div>
                </el-popover>
              </div>
              <div v-if="hasTraceDiagnostics(message)" class="message-trace-inline">
                <el-popover
                  trigger="click"
                  placement="top"
                  :width="360"
                >
                  <template #reference>
                    <button class="message-trace-chip" type="button">
                      链路 {{ formatTraceDuration(message.serverDurationMs) }}
                    </button>
                  </template>
                  <div class="message-trace-popover">
                    <div class="message-trace-head">
                      <strong>回答链路</strong>
                      <span>{{ traceModeLabel(message.toolMode) }}</span>
                    </div>
                    <small class="message-trace-meta">
                      <span v-if="message.provider">{{ message.provider }}</span>
                      <span v-if="message.firstTokenLatencyMs > 0">首字 {{ formatTraceDuration(message.firstTokenLatencyMs) }}</span>
                      <span v-if="message.traceId">traceId {{ shortenInlineTraceId(message.traceId) }}</span>
                    </small>
                    <ul class="message-trace-stage-list">
                      <li
                        v-for="stage in message.traceStages"
                        :key="stage.key"
                        :class="{ skipped: stage.status === 'SKIPPED' }"
                      >
                        <div class="message-trace-stage-head">
                          <strong>{{ stage.label }}</strong>
                          <span>{{ formatTraceDuration(stage.durationMs) }}</span>
                        </div>
                        <p v-if="stage.detail">{{ stage.detail }}</p>
                      </li>
                    </ul>
                  </div>
                </el-popover>
              </div>
              <span
                class="loading-dots"
                v-if="message.isThinking || message.isTyping"
              >
                <span class="dot"></span>
                <span class="dot"></span>
                <span class="dot"></span>
              </span>
            </div>
          </div>
          <div ref="messageBottomRef" class="message-bottom-anchor" aria-hidden="true"></div>
        </div>
        <button
          v-if="showScrollToBottom"
          class="scroll-bottom-button"
          type="button"
          @click="scrollToBottom(true)"
        >
          查看最新回复
        </button>

        <footer class="composer">
          <div v-if="chatAttachments.length" class="chat-attachment-list" :class="{ 'chat-attachment-list-mobile': isMobileViewport }">
            <article
              v-for="(attachment, index) in chatAttachments"
              :key="attachment.id"
              :class="chatAttachmentClass(attachment)"
            >
              <img
                v-if="attachment.isImage && attachment.previewUrl"
                :src="attachment.previewUrl"
                :alt="attachment.name"
                class="chat-attachment-preview"
              />
              <div v-else class="chat-attachment-file-icon">{{ attachment.kindLabel }}</div>
              <div class="chat-attachment-meta">
                <span class="chat-attachment-kind">{{ attachment.kindLabel }}</span>
                <strong class="chat-attachment-name">{{ attachment.name }}</strong>
                <span class="chat-attachment-size">{{ attachment.sizeLabel }}</span>
              </div>
              <button
                class="chat-attachment-remove"
                type="button"
                @click="removeChatAttachment(index)"
              >
                移除
              </button>
            </article>
          </div>
          <div
            class="composer-body"
            :class="{ 'composer-body-drag-active': composerDragActive }"
            @dragenter.prevent="handleComposerDragEnter"
            @dragover.prevent="handleComposerDragOver"
            @dragleave="handleComposerDragLeave"
            @drop.prevent="handleComposerDrop"
          >
            <div class="composer-row">
              <textarea
                v-if="isMobileViewport"
                ref="mobileComposerTextareaRef"
                v-model="inputMessage"
                class="mobile-composer-textarea"
                :placeholder="composerPlaceholder"
                rows="1"
                enterkeyhint="send"
                autocapitalize="off"
                autocomplete="off"
                autocorrect="off"
                spellcheck="false"
                @input="handleMobileTextareaInput"
                @focus="handleComposerFocus"
                @blur="handleComposerBlur"
                @keydown="handleComposerKeydown"
              ></textarea>
              <el-input
                v-else
                ref="composerInputRef"
                v-model="inputMessage"
                class="composer-input"
                type="textarea"
                :autosize="composerAutosize"
                :placeholder="composerPlaceholder"
                resize="none"
                inputmode="text"
                enterkeyhint="send"
                autocapitalize="off"
                autocomplete="off"
                autocorrect="off"
                spellcheck="false"
                @input="markChatActivity"
                @focus="handleComposerFocus"
                @blur="handleComposerBlur"
                @compositionstart="isComposing = true"
                @compositionend="isComposing = false"
                @keydown="handleComposerKeydown"
              />
            </div>
            <div class="composer-toolbar">
              <div class="composer-toolbar-left">
                <button
                  v-if="isMobileViewport"
                  class="mobile-toolbar-button mobile-attach-button composer-attach-button"
                  type="button"
                  :disabled="isSending"
                  @click="openChatAttachmentUpload"
                >
                  附件
                </button>
                <button
                  v-else
                  class="composer-icon-button composer-attach-button"
                  :disabled="isSending"
                  type="button"
                  title="添加附件"
                  aria-label="添加附件"
                  @click="openChatAttachmentUpload"
                >
                  <svg
                    class="composer-icon-svg"
                    viewBox="0 0 24 24"
                    aria-hidden="true"
                  >
                    <path
                      d="M21.44 11.05 12.25 20.24a6 6 0 0 1-8.49-8.49l9.2-9.19a4 4 0 1 1 5.65 5.66l-9.2 9.19a2 2 0 0 1-2.82-2.83l8.49-8.48"
                    />
                  </svg>
                </button>
                <el-popover
                  v-model:visible="modelPickerVisible"
                  placement="top-start"
                  trigger="click"
                  :width="292"
                  popper-class="model-mode-popover"
                >
                  <template #reference>
                    <button
                      :class="['model-mode-chip', `model-mode-chip-${currentModelOption.logoKind}`]"
                      type="button"
                      :aria-label="`当前模型 ${currentModelOption.chipLabel}`"
                    >
                      <span :class="['model-mode-chip-icon', 'model-logo-shell', `model-logo-shell-${currentModelOption.logoKind}`]" aria-hidden="true">
                        <svg
                          v-if="currentModelOption.logoKind === 'local'"
                          class="model-logo-svg"
                          viewBox="0 0 24 24"
                        >
                          <path d="M13 2 4 14h6l-1 8 9-12h-6z" />
                        </svg>
                        <svg
                          v-else-if="currentModelOption.logoKind === 'fast'"
                          class="model-logo-svg"
                          viewBox="0 0 24 24"
                        >
                          <path d="M3 11.5 20.5 4l-4.8 16-3.8-5.7L3 11.5Z" />
                          <path d="M11.9 14.1 20.5 4" />
                        </svg>
                        <svg
                          v-else
                          class="model-logo-svg"
                          viewBox="0 0 24 24"
                        >
                          <path d="m12 5 7 4-7 4-7-4 7-4Z" />
                          <path d="m5 12 7 4 7-4" />
                          <path d="m5 15 7 4 7-4" />
                        </svg>
                      </span>
                      <span class="model-mode-chip-label">模型</span>
                      <strong>{{ currentModelOption.chipLabel }}</strong>
                      <span class="model-mode-chip-caret">⌄</span>
                    </button>
                  </template>
                  <div class="model-mode-menu">
                    <button
                      v-for="option in modelProviderOptions"
                      :key="option.value"
                      :class="['model-mode-option', `model-mode-option-${option.logoKind}`, { active: selectedModelProvider === option.value }]"
                      type="button"
                      @click="setModelProvider(option.value)"
                    >
                      <div class="model-mode-option-main">
                        <div class="model-mode-option-title-group">
                          <span :class="['model-mode-option-icon', 'model-logo-shell', `model-logo-shell-${option.logoKind}`]" aria-hidden="true">
                            <svg
                              v-if="option.logoKind === 'local'"
                              class="model-logo-svg"
                              viewBox="0 0 24 24"
                            >
                              <path d="M13 2 4 14h6l-1 8 9-12h-6z" />
                            </svg>
                            <svg
                              v-else-if="option.logoKind === 'fast'"
                              class="model-logo-svg"
                              viewBox="0 0 24 24"
                            >
                              <path d="M3 11.5 20.5 4l-4.8 16-3.8-5.7L3 11.5Z" />
                              <path d="M11.9 14.1 20.5 4" />
                            </svg>
                            <svg
                              v-else
                              class="model-logo-svg"
                              viewBox="0 0 24 24"
                            >
                              <path d="m12 5 7 4-7 4-7-4 7-4Z" />
                              <path d="m5 12 7 4 7-4" />
                              <path d="m5 15 7 4 7-4" />
                            </svg>
                          </span>
                          <strong>{{ option.menuTitle }}</strong>
                        </div>
                        <span class="model-mode-option-badge">{{ option.modeLabel }}</span>
                      </div>
                      <span class="model-mode-option-subtitle">{{ option.menuSubtitle }}</span>
                    </button>
                  </div>
                </el-popover>
              </div>
              <div v-if="isMobileViewport" class="composer-toolbar-right">
                <el-button
                  class="send-action composer-send-button"
                  type="primary"
                  @click="sendMessage"
                  :disabled="isSending"
                >
                  发送
                </el-button>
              </div>
            </div>
          </div>
          <input
            ref="chatAttachmentInputRef"
            class="knowledge-input"
            type="file"
            multiple
            accept=".png,.jpg,.jpeg,.webp,.gif,.bmp,.pdf,.doc,.docx,.md,.markdown,.txt,.csv,.rtf,.html,.htm,.xml,.odt,.ods,.odp,.xls,.xlsx,.ppt,.pptx"
            @change="handleChatAttachmentChange"
          />
        </footer>
      </section>
    </main>

    <el-dialog
      v-model="sessionExpiredDialogVisible"
      class="session-expired-dialog"
      width="min(92vw, 520px)"
      :show-close="false"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :before-close="() => false"
      append-to-body
    >
      <div class="session-expired-content">
        <span class="session-expired-tag">登录状态已失效</span>
        <h3>已达超时时间 5 分钟，请重新登录！</h3>
        <p>当前登录状态已自动退出。你可以先导出当前对话记录，再确认返回登录页重新进入系统。</p>
      </div>
      <template #footer>
        <div class="session-expired-footer">
          <el-button class="session-expired-download" @click="exportTimedOutConversation">
            下载对话数据
          </el-button>
          <el-button class="session-expired-confirm" type="primary" @click="confirmSessionExpired">
            确认重新登录
          </el-button>
        </div>
      </template>
    </el-dialog>

    <el-drawer
      v-model="mobilePanelVisible"
      class="mobile-panel-drawer"
      direction="btt"
      size="78%"
      append-to-body
      :with-header="false"
    >
      <div class="mobile-panel-sheet">
        <div class="mobile-panel-grabber"></div>

        <section class="mobile-panel-card">
          <div class="mobile-panel-head">
            <div class="mobile-panel-head-copy">
              <span>会话与知识库</span>
            </div>
            <strong :title="uuid ? `当前会话 ID：${uuid}` : '当前会话 ID 暂未生成'">{{ currentSessionDisplayId }}</strong>
          </div>
          <div class="mobile-panel-model-banner">
            <div :class="['model-logo-shell', `model-logo-shell-${currentModelOption.logoKind}`]" aria-hidden="true">
              <svg
                v-if="currentModelOption.logoKind === 'local'"
                class="model-logo-svg"
                viewBox="0 0 24 24"
              >
                <path d="M13 2 4 14h6l-1 8 9-12h-6z" />
              </svg>
              <svg
                v-else-if="currentModelOption.logoKind === 'fast'"
                class="model-logo-svg"
                viewBox="0 0 24 24"
              >
                <path d="M3 11.5 20.5 4l-4.8 16-3.8-5.7L3 11.5Z" />
                <path d="M11.9 14.1 20.5 4" />
              </svg>
              <svg
                v-else
                class="model-logo-svg"
                viewBox="0 0 24 24"
              >
                <path d="m12 5 7 4-7 4-7-4 7-4Z" />
                <path d="m5 12 7 4 7-4" />
                <path d="m5 15 7 4 7-4" />
              </svg>
            </div>
            <div class="mobile-panel-model-copy">
              <span>{{ currentModelOption.modeLabel }}</span>
              <strong>{{ currentModelOption.shortLabel }}</strong>
            </div>
          </div>
          <div class="mobile-panel-grid">
            <div class="mobile-panel-metric">
              <span>模型</span>
              <strong>{{ currentModelOption.modeShortLabel }}</strong>
            </div>
            <div class="mobile-panel-metric">
              <span>知识</span>
              <strong>{{ knowledgeCount }}</strong>
            </div>
          </div>
          <div class="mobile-panel-actions">
            <button class="mobile-panel-primary" type="button" @click="handleNewChatFromPanel">
              新建会话
            </button>
            <button class="mobile-panel-secondary" type="button" @click="handleKnowledgeCenterFromPanel">
              知识库管理
            </button>
            <button v-if="isAdmin" class="mobile-panel-secondary" type="button" @click="handleAdminConsoleFromPanel">
              管理后台
            </button>
          </div>
        </section>

        <section class="mobile-panel-card">
          <div class="mobile-panel-head">
            <span>快捷指令</span>
            <small>点一下即发送</small>
          </div>
          <div class="mobile-panel-prompts">
            <button
              v-for="prompt in quickPrompts"
              :key="`mobile-${prompt.title}`"
              class="mobile-panel-prompt"
              type="button"
              @click="sendQuickPromptFromPanel(prompt.content)"
            >
              <strong>{{ prompt.title }}</strong>
              <span>{{ prompt.description }}</span>
            </button>
          </div>
        </section>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import MarkdownIt from 'markdown-it'
import shulanLogo from '@/assets/shulan-logo.png'
import { ElMessage } from 'element-plus'
import AdminEntryActionButton from '@/components/AdminEntryActionButton.vue'
import LogoutActionButton from '@/components/LogoutActionButton.vue'
import { downloadChatMarkdown } from '@/lib/chatExport'
import { compactCitationLabel, groupedCitations, normalizeCitation } from '@/lib/chatCitation'
import { apiClient, authState, authorizedFetch, isAdmin, logout, markAuthActivity, syncAuthIdleTimeoutPolicy } from '@/lib/auth'
import { confirmDanger } from '@/lib/confirmDialog'

const LOCAL_OLLAMA = 'LOCAL_OLLAMA'
const QWEN_ONLINE = 'QWEN_ONLINE'
const QWEN_ONLINE_FAST = 'QWEN_ONLINE_FAST'
const QWEN_ONLINE_DEEP = 'QWEN_ONLINE_DEEP'
const STREAM_METADATA_MARKER = '[[AIMED_STREAM_METADATA]]'
const MAX_CHAT_ATTACHMENT_BYTES = 1024 * 1024
const MAX_CHAT_ATTACHMENT_TOTAL_BYTES = 2 * 1024 * 1024
const MAX_CHAT_ATTACHMENT_COUNT = 3
const CHAT_MEMORY_STORAGE_KEY = 'chat_current_memory_id'
const CHAT_HISTORY_CACHE_KEY_PREFIX = 'aimed_chat_history_cache'
const CHAT_HISTORY_DETAIL_KEY_PREFIX = 'aimed_chat_history_detail'
const welcomeMessageMarkdown =
  '你好，我是**杭州树兰医院智能问答助手**。\n\n我可以帮你做这几类事情：\n- 查询号源及预约挂号\n- 结合医院知识库回答就医流程、科室和院内信息\n- 结合你上传的文档或图片做辅助解读\n- 提供风险提示、检查建议和就诊建议'

const router = useRouter()

const quickPrompts = [
  {
    title: '门诊',
    description: '介绍门诊服务能力',
    content: '我想了解杭州树兰医院的门诊服务能力',
  },
  {
    title: '预约',
    description: '总结当前预约规则',
    content: '帮我总结当前知识库里与预约相关的规则',
  },
  {
    title: '上传',
    description: '说明支持的资料格式',
    content: '我想上传一份院内 PDF 做知识库，请告诉我支持哪些格式',
  },
  {
    title: '常见问题',
    description: '了解院内服务内容',
    content: '我想了解当前知识库可以回答哪些院内服务问题',
  },
]

const messageListRef = ref()
const messageBottomRef = ref()
const chatAttachmentInputRef = ref()
const chatPanelRef = ref()
const composerInputRef = ref()
const mobileComposerTextareaRef = ref()
const isSending = ref(false)
const isComposing = ref(false)
const shouldStickToBottom = ref(true)
const uuid = ref()
const inputMessage = ref('')
const messages = ref([])
const knowledgeCount = ref(0)
const chatAttachments = ref([])
const chatHistoryItems = ref([])
const chatHistoryExpanded = ref(true)
const historyDebugMode = ref(false)
const historyLoading = ref(false)
const restoringHistoryMemoryId = ref(null)
const historyQuery = ref('')
const historyPage = ref(1)
const historyPageSize = 20
const historyTotal = ref(0)
const renamingHistoryId = ref(null)
const renamingHistoryTitle = ref('')
const selectedModelProvider = ref(LOCAL_OLLAMA)
const modelPickerVisible = ref(false)
const mobilePanelVisible = ref(false)
const desktopSidebarCollapsed = ref(false)
const sessionExpiredDialogVisible = ref(false)
const sessionExpired = ref(false)
const timedOutMessagesSnapshot = ref([])
const timedOutMemoryId = ref('')
const timedOutModelLabel = ref('')
const keyboardInset = ref(0)
const viewportHeight = ref(0)
const keyboardOpen = ref(false)
const isWeChatBrowser = ref(false)
const composerDragActive = ref(false)
const showScrollToBottom = computed(
  () => !shouldStickToBottom.value && messages.value.length > 1
)
let currentStreamController = null
let messageMutationObserver = null
let viewportMediaQuery = null
let visualViewportCleanup = null
let maxObservedViewportHeight = 0
let viewportSyncTimers = []
let historySearchTimer = null
const attachmentPreviewUrls = new Set()

const modelProviderOptions = [
  {
    value: LOCAL_OLLAMA,
    label: '本地 qwen3.5:27b',
    shortLabel: 'qwen3.5:27b',
    chipLabel: '本地 · qwen3.5:27b',
    menuTitle: 'qwen3.5:27b',
    menuSubtitle: '适合院内基础问答和隐私内容处理',
    modeLabel: '本地',
    modeShortLabel: '本地',
    logoKind: 'local',
    description: '适合院内基础问答和隐私内容处理',
  },
  {
    value: QWEN_ONLINE_FAST,
    label: '在线 qwen-plus',
    shortLabel: 'qwen-plus',
    chipLabel: '在线快答 · qwen-plus',
    menuTitle: 'qwen-plus',
    menuSubtitle: '更适合低延迟问答、普通症状咨询和院内信息查询',
    modeLabel: '在线快答',
    modeShortLabel: '快答',
    logoKind: 'fast',
    description: '更适合低延迟问答、普通症状咨询和院内信息查询',
  },
  {
    value: QWEN_ONLINE_DEEP,
    label: '在线 qwen3.6-plus',
    shortLabel: 'qwen3.6-plus',
    chipLabel: '在线深答 · qwen3.6-plus',
    menuTitle: 'qwen3.6-plus',
    menuSubtitle: '更适合复杂总结、多资料整合和长回答',
    modeLabel: '在线深答',
    modeShortLabel: '深答',
    logoKind: 'deep',
    description: '更适合复杂总结、多资料整合和长回答',
  },
]
const currentModelOption = computed(
  () => modelProviderOptions.find((option) => option.value === selectedModelProvider.value) || modelProviderOptions[0]
)
const currentModelShortLabel = computed(() => currentModelOption.value.shortLabel)
const currentUserDisplayName = computed(() => {
  const nickname = authState.user?.nickname?.trim?.()
  if (nickname) {
    return nickname
  }
  const email = authState.user?.email?.trim?.()
  if (email) {
    return email
  }
  return '用户'
})
const currentModelProviderTip = computed(() =>
  selectedModelProvider.value === LOCAL_OLLAMA
    ? '本地模型适合院内基础问答和隐私内容处理。'
    : selectedModelProvider.value === QWEN_ONLINE_FAST
      ? '在线快答适合普通问答和低延迟场景。'
      : '在线深答更适合复杂病情说明、长总结和多资料综合判断。'
)
const currentSessionDisplayId = computed(() => {
  if (!uuid.value) {
    return '会话 ID 待生成'
  }
  return String(uuid.value)
})
const currentHistoryCacheOwner = computed(() => authState.user?.id || 'anonymous')
const filteredHistoryItems = computed(() => {
  return chatHistoryItems.value.filter((item) => item?.title && item.title !== '未命名对话')
})
const filteredPinnedHistoryItems = computed(() =>
  filteredHistoryItems.value.filter((item) => item.pinned)
)
const filteredRegularHistoryItems = computed(() =>
  filteredHistoryItems.value.filter((item) => !item.pinned)
)
const historyPageCount = computed(() => Math.max(1, Math.ceil(historyTotal.value / historyPageSize)))
// 空会话时给一点引导，一旦用户已经开始正式对话，就把侧栏高度优先让给历史记录。
const showDesktopQuickPrompts = computed(() =>
  !isDesktopSidebarCollapsed.value && !messages.value.some((message) => message?.isUser)
)
const composerAutosize = computed(() =>
  isMobileViewport.value ? { minRows: 1, maxRows: 4 } : { minRows: 2, maxRows: 5 }
)
const composerPlaceholder = computed(() =>
  isMobileViewport.value
    ? (selectedModelProvider.value === LOCAL_OLLAMA
      ? '直接提问或短句确认，回车发送'
      : selectedModelProvider.value === QWEN_ONLINE_FAST
        ? '输入普通症状或院内问题，回车发送'
        : '输入复杂问题或多资料需求，回车发送')
    : (selectedModelProvider.value === LOCAL_OLLAMA
      ? '帮我概括这份资料的重点，结论尽量简短'
      : selectedModelProvider.value === QWEN_ONLINE_FAST
        ? '脑袋有点疼、鼻塞，应该先怎么处理'
        : '请综合最新上传资料，说明诊疗要点、风险和下一步建议')
)
const isWeChatMobile = computed(() => isMobileViewport.value && isWeChatBrowser.value)
const isDesktopSidebarCollapsed = computed(() => !isMobileViewport.value && desktopSidebarCollapsed.value)
const pageShellStyle = computed(() => ({
  '--chat-viewport-height': viewportHeight.value ? `${viewportHeight.value}px` : '100dvh',
  '--chat-keyboard-offset': keyboardInset.value ? `${keyboardInset.value}px` : '0px',
}))

const escapeHtml = (value = '') =>
  String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')

const truncateDialogTitle = (value = '', maxLength = 22) => {
  const normalized = String(value).trim()
  if (!normalized) {
    return '该历史对话'
  }
  return normalized.length > maxLength ? `${normalized.slice(0, maxLength)}…` : normalized
}

const buildHistoryDeleteDialogMessage = (title) => {
  const shortTitle = truncateDialogTitle(title, 28)
  return `确认删除「${shortTitle}」吗？删除后该会话会从左侧历史中隐藏。`
}

const medicalCalloutRules = [
  {
    pattern: /^(重点提示|重要提醒|特别提醒|温馨提示|注意|警示|风险提示|禁忌|危险信号|急诊指征)[：:]/,
    className: 'medical-callout-warning',
    label: '风险提示',
  },
  {
    pattern: /^(建议|就诊建议|处理建议|复诊建议|用药建议|护理建议)[：:]/,
    className: 'medical-callout-advice',
    label: '就诊建议',
  },
  {
    pattern: /^(检查建议|建议检查|推荐检查|检查项目|下一步检查)[：:]/,
    className: 'medical-callout-check',
    label: '检查建议',
  },
  {
    pattern: /^(结论|总结|核心结论|答复要点|要点|简要结论)[：:]/,
    className: 'medical-callout-info',
    label: '答复要点',
  },
]

const markdown = new MarkdownIt({
  html: false,
  breaks: true,
  linkify: true,
  typographer: false,
})

const defaultLinkRender =
  markdown.renderer.rules.link_open ||
  ((tokens, idx, options, _env, self) => self.renderToken(tokens, idx, options))

markdown.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  const token = tokens[idx]
  token.attrSet('target', '_blank')
  token.attrSet('rel', 'noopener noreferrer')
  return defaultLinkRender(tokens, idx, options, env, self)
}

onMounted(async () => {
  detectBrowserContext()
  setupViewportTracking()
  syncViewportMetrics()
  let hasRestorableSession = false
  try {
    hasRestorableSession = await initUUID()
  } catch (error) {
    console.error('初始化会话失败:', error)
    ElMessage.error(resolveUiErrorMessage(error, '初始化会话失败，请刷新页面重试。'))
  }
  initModelProvider()
  initDesktopSidebar()
  syncIdleTimeoutPolicy()

  if (uuid.value && hasRestorableSession) {
    await restoreChatHistory(uuid.value, { silent: true })
  } else if (uuid.value) {
    showWelcomeMessage()
  }

  await nextTick()
  attachMessageObserver()
  attachVisualViewportObserver()
  scrollToBottom(true)
  refreshKnowledgeFiles()
  fetchChatHistories()
})

onBeforeUnmount(() => {
  clearIdleTimer()
  detachIdleActivityListeners()
  abortActiveStream()
  detachMessageObserver()
  detachVisualViewportObserver()
  detachViewportTracking()
  clearViewportSyncTimers()
  clearHistorySearchTimer()
  releaseAllAttachmentPreviews()
})

watch(
  messages,
  async () => {
    persistCurrentConversationSnapshot()
    await scrollToBottom()
  },
  { deep: true }
)

watch(historyQuery, () => {
  historyPage.value = 1
  clearHistorySearchTimer()
  historySearchTimer = window.setTimeout(() => {
    fetchChatHistories()
  }, 220)
})

const messageClass = (message) => {
  if (message.kind === 'system') {
    return 'message message-system'
  }
  return message.isUser ? 'message message-user' : 'message message-assistant'
}

const messageIcon = (message) => {
  if (message.kind === 'system') {
    return 'fa-solid fa-folder-plus'
  }
  return message.isUser ? 'fa-solid fa-user' : 'fa-solid fa-stethoscope'
}

const messageRole = (message) => {
  if (message.kind === 'system') {
    return '知识库更新'
  }
  return message.isUser ? currentUserDisplayName.value : '树兰智能助手'
}

const normalizeTraceStage = (stage) => ({
  key: stage?.key || `stage-${Math.random().toString(36).slice(2, 8)}`,
  label: stage?.label || '未命名阶段',
  durationMs: Number(stage?.durationMs || 0),
  status: String(stage?.status || 'DONE').toUpperCase(),
  detail: stage?.detail || '',
})

const applyStreamMetadata = (message, metadata) => {
  message.citations = (metadata?.citations || []).map(normalizeCitation)
  message.traceId = metadata?.traceId || ''
  message.provider = metadata?.provider || ''
  message.toolMode = metadata?.toolMode || ''
  message.serverDurationMs = Number(metadata?.serverDurationMs || 0)
  message.firstTokenLatencyMs = Number(metadata?.firstTokenLatencyMs || 0)
  message.traceStages = Array.isArray(metadata?.traceStages) ? metadata.traceStages.map(normalizeTraceStage) : []
}

const hasTraceDiagnostics = (message) =>
  isAdmin.value && !message?.isUser && (Number(message?.serverDurationMs || 0) > 0 || (Array.isArray(message?.traceStages) && message.traceStages.length > 0))

const formatTraceDuration = (value) => {
  const duration = Number(value || 0)
  if (!Number.isFinite(duration) || duration <= 0) {
    return '0 ms'
  }
  if (duration < 1000) {
    return `${Math.round(duration)} ms`
  }
  return `${(duration / 1000).toFixed(duration >= 10_000 ? 0 : 1)} s`
}

const traceModeLabel = (toolMode) => {
  if (toolMode === 'APPOINTMENT') {
    return '挂号工具链'
  }
  if (toolMode === 'FAST') {
    return '在线快答链'
  }
  if (toolMode === 'DEEP') {
    return '在线深答链'
  }
  return '标准链路'
}

const shortenInlineTraceId = (traceId) => {
  if (!traceId || traceId.length <= 18) {
    return traceId
  }
  return `${traceId.slice(0, 10)}...${traceId.slice(-6)}`
}

const pushSystemMessage = (content) => {
  messages.value.push({
    kind: 'system',
    isUser: false,
    rawContent: content,
    content: convertStreamOutput(content),
    isTyping: false,
    isThinking: false,
  })
  shouldStickToBottom.value = true
}

const handleMessageListScroll = () => {
  const container = messageListRef.value
  if (!container) {
    return
  }

  const distanceToBottom =
    container.scrollHeight - container.scrollTop - container.clientHeight
  shouldStickToBottom.value = distanceToBottom < 48
}

const scrollToBottom = async (force = false) => {
  const container = messageListRef.value
  if (!container || (!force && !shouldStickToBottom.value)) {
    return
  }

  if (force) {
    shouldStickToBottom.value = true
  }

  await nextTick()
  requestAnimationFrame(() => {
    scrollBottomIntoView(force)
    requestAnimationFrame(() => {
      scrollBottomIntoView(force)
    })
  })
}

const scrollBottomIntoView = (force = false) => {
  const container = messageListRef.value
  const anchor = messageBottomRef.value
  if (!container || (!force && !shouldStickToBottom.value)) {
    return
  }

  if (anchor?.scrollIntoView) {
    anchor.scrollIntoView({ block: 'end', inline: 'nearest' })
  }
  container.scrollTop = container.scrollHeight

  if (isMobileViewport.value) {
    const scrollingElement = document.scrollingElement
    if (scrollingElement) {
      scrollingElement.scrollTop = scrollingElement.scrollHeight
    }
  }
}

const showWelcomeMessage = () => {
  messages.value = [
    {
      isUser: false,
      rawContent: welcomeMessageMarkdown,
      content: convertStreamOutput(welcomeMessageMarkdown),
      isTyping: false,
      isThinking: false,
    },
  ]
  shouldStickToBottom.value = true
}

const initModelProvider = () => {
  const storedProvider = localStorage.getItem('chat_model_provider')
  if (storedProvider === QWEN_ONLINE || storedProvider === QWEN_ONLINE_FAST) {
    selectedModelProvider.value = QWEN_ONLINE_FAST
    localStorage.setItem('chat_model_provider', QWEN_ONLINE_FAST)
    return
  }
  if (storedProvider === QWEN_ONLINE_DEEP) {
    selectedModelProvider.value = QWEN_ONLINE_DEEP
    return
  }
  selectedModelProvider.value = LOCAL_OLLAMA
}

const toggleHistoryExpanded = () => {
  chatHistoryExpanded.value = !chatHistoryExpanded.value
}

const clearHistorySearchTimer = () => {
  if (historySearchTimer) {
    window.clearTimeout(historySearchTimer)
    historySearchTimer = null
  }
}

const currentHistoryIndexStorageKey = () => `${CHAT_HISTORY_CACHE_KEY_PREFIX}_${currentHistoryCacheOwner.value}`
const historyDetailStorageKey = (memoryId) => `${CHAT_HISTORY_DETAIL_KEY_PREFIX}_${currentHistoryCacheOwner.value}_${memoryId}`
const historyDebugQuery = computed(() => (isAdmin.value && historyDebugMode.value ? { debug: true } : {}))

const parsePlainMessageContent = (message) => {
  if (typeof message?.rawContent === 'string' && message.rawContent.trim()) {
    return message.rawContent.trim()
  }
  if (typeof message?.content === 'string' && message.content.trim()) {
    return message.content.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim()
  }
  return ''
}

// 历史标题和摘要都基于“用户可见内容”生成，避免把给模型看的内部上下文变成左侧会话名称。
const summarizeHistoryTitle = (historyMessages) => {
  const firstUserMessage = historyMessages.find((message) => message.user && message.content)
  const content = (firstUserMessage?.content || '').replace(/\s+/g, ' ').trim()
  if (!content) {
    return '未命名对话'
  }
  return content.length > 22 ? `${content.slice(0, 21)}…` : content
}

const summarizeHistoryPreview = (historyMessages) => {
  const lastMessage = historyMessages.at(-1)
  const content = (lastMessage?.content || '').replace(/\s+/g, ' ').trim()
  if (!content) {
    return ''
  }
  return content.length > 40 ? `${content.slice(0, 39)}…` : content
}

const hasMeaningfulHistoryContent = (historyMessages) =>
  Array.isArray(historyMessages) &&
  historyMessages.some((message) => {
    if (!message?.user) {
      return false
    }
    return Boolean((message.content || '').trim()) || (Array.isArray(message.attachments) && message.attachments.length > 0)
  })

const readCachedHistoryIndex = () => {
  try {
    const raw = localStorage.getItem(currentHistoryIndexStorageKey())
    const items = raw ? JSON.parse(raw) : []
    return Array.isArray(items)
      ? items.filter((item) => item?.memoryId && item?.title && item.title !== '未命名对话')
      : []
  } catch {
    return []
  }
}

const writeCachedHistoryIndex = (items) => {
  localStorage.setItem(currentHistoryIndexStorageKey(), JSON.stringify(items))
}

const readCachedHistoryDetail = (memoryId) => {
  try {
    const raw = localStorage.getItem(historyDetailStorageKey(memoryId))
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

const writeCachedHistoryDetail = (memoryId, payload) => {
  localStorage.setItem(historyDetailStorageKey(memoryId), JSON.stringify(payload))
}

const removeCachedHistoryDetail = (memoryId) => {
  localStorage.removeItem(historyDetailStorageKey(memoryId))
}

const purgeInvalidHistoryItem = (memoryId) => {
  chatHistoryItems.value = chatHistoryItems.value.filter((item) => String(item.memoryId) !== String(memoryId))
  writeCachedHistoryIndex(
    readCachedHistoryIndex().filter((item) => String(item.memoryId) !== String(memoryId))
  )
  removeCachedHistoryDetail(memoryId)
}

const mergeHistoryItems = (remoteItems, localItems) => {
  const merged = new Map()
  ;[...(Array.isArray(localItems) ? localItems : []), ...(Array.isArray(remoteItems) ? remoteItems : [])].forEach((item) => {
    if (!item?.memoryId || item.hidden || !item.title || item.title === '未命名对话') {
      return
    }
    merged.set(String(item.memoryId), { ...(merged.get(String(item.memoryId)) || {}), ...item })
  })
  return Array.from(merged.values()).sort((left, right) => {
    if (Boolean(left.pinned) !== Boolean(right.pinned)) {
      return left.pinned ? -1 : 1
    }
    return Number(right.updatedAtEpochMillis || 0) - Number(left.updatedAtEpochMillis || 0)
  })
}

// 本地快照只缓存“用户实际看到”的消息，避免刷新后把模型内部增强上下文重新喂回界面。
const persistCurrentConversationSnapshot = () => {
  if (!uuid.value) {
    return
  }
  const historyMessages = messages.value
    .filter((message) => message?.kind !== 'system')
    .map((message) => ({
      user: !!message.isUser,
      content: parsePlainMessageContent(message),
      attachments: Array.isArray(message.attachments)
        ? message.attachments.map((attachment, index) => ({
          id: attachment.id || `${uuid.value}-${index}-${attachment.name || 'attachment'}`,
          name: attachment.name || '未命名附件',
          size: attachment.size ?? 0,
          contentType: attachment.contentType || '',
          extension: attachment.extension || '',
          image: Boolean(attachment.isImage),
          kindLabel: attachment.kindLabel || (attachment.isImage ? '图片' : '文件'),
          previewUrl: attachment.previewUrl || '',
          previewWidth: attachment.previewWidth ?? null,
          previewHeight: attachment.previewHeight ?? null,
        }))
        : [],
      citations: Array.isArray(message.citations) ? message.citations.map((citation) => ({ ...citation })) : [],
    }))
    .filter((message) => message.content)

  // 只有欢迎语的空会话不进入历史，也不写本地恢复快照。
  if (!historyMessages.length || !hasMeaningfulHistoryContent(historyMessages)) {
    writeCachedHistoryIndex(
      readCachedHistoryIndex().filter((item) => String(item.memoryId) !== String(uuid.value))
    )
    removeCachedHistoryDetail(uuid.value)
    return
  }

  const indexItems = readCachedHistoryIndex()
  const existingItem = indexItems.find((item) => String(item.memoryId) === String(uuid.value))
  const displayTitle = existingItem?.customTitle || summarizeHistoryTitle(historyMessages)
  const nextItem = {
    memoryId: Number(uuid.value),
    title: displayTitle,
    customTitle: existingItem?.customTitle || null,
    preview: summarizeHistoryPreview(historyMessages),
    updatedAt: new Date().toISOString().slice(0, 16).replace('T', ' '),
    updatedAtEpochMillis: Date.now(),
    messageCount: historyMessages.length,
    pinned: Boolean(existingItem?.pinned),
    hidden: false,
  }
  writeCachedHistoryIndex(
    mergeHistoryItems([nextItem], indexItems.filter((item) => String(item.memoryId) !== String(uuid.value)))
  )
  writeCachedHistoryDetail(uuid.value, {
    memoryId: Number(uuid.value),
    title: nextItem.title,
    customTitle: nextItem.customTitle,
    firstQuestion: historyMessages.find((message) => message.user)?.content || '',
    lastPreview: nextItem.preview,
    messages: historyMessages,
  })
}

// 远端历史接口失败时，允许先用本地快照兜底恢复当前会话，降低用户刷新后的丢感。
const restoreCachedHistory = (memoryId) => {
  const cached = readCachedHistoryDetail(memoryId)
  if (!cached?.messages?.length || !hasMeaningfulHistoryContent(cached.messages)) {
    removeCachedHistoryDetail(memoryId)
    return false
  }
  uuid.value = String(cached.memoryId || memoryId)
  sessionStorage.setItem(CHAT_MEMORY_STORAGE_KEY, uuid.value)
  clearComposerAttachments()
  messages.value = cached.messages.map(mapHistoryMessageToViewMessage)
  shouldStickToBottom.value = true
  nextTick(() => scrollToBottom(true))
  return true
}

const updateHistoryItemLocal = (updatedItem) => {
  const nextItems = chatHistoryItems.value.filter((item) => item.memoryId !== updatedItem.memoryId)
  nextItems.push(updatedItem)
  chatHistoryItems.value = nextItems.sort((left, right) => {
    if (left.pinned !== right.pinned) {
      return left.pinned ? -1 : 1
    }
    return Number(right.updatedAtEpochMillis || 0) - Number(left.updatedAtEpochMillis || 0)
  })
}

// 后端 visible history 已经是“可展示语义”，这里只负责补前端渲染需要的派生字段。
const mapHistoryMessageToViewMessage = (message) => ({
  isUser: !!message.user,
  rawContent: message.content || '',
  content: convertStreamOutput(message.content || ''),
  attachments: Array.isArray(message.attachments)
    ? message.attachments.map((attachment, index) => ({
      id: attachment.id || `${message.user ? 'user' : 'assistant'}-${index}-${attachment.name || 'attachment'}`,
      name: attachment.name || '未命名附件',
      size: attachment.size ?? 0,
      contentType: attachment.contentType || '',
      extension: attachment.extension || '',
      isImage: Boolean(attachment.image ?? attachment.isImage),
      kindLabel: attachment.kindLabel || ((attachment.image ?? attachment.isImage) ? '图片' : '文件'),
      badgeLabel: attachmentBadgeLabel(attachment),
      sizeLabel: typeof attachment.size === 'number' ? formatAttachmentSize(attachment.size) : '',
      previewUrl: attachment.previewUrl || '',
      previewWidth: attachment.previewWidth ?? null,
      previewHeight: attachment.previewHeight ?? null,
    }))
    : [],
  citations: Array.isArray(message.citations) ? message.citations.map(normalizeCitation) : [],
  traceId: '',
  provider: '',
  toolMode: '',
  serverDurationMs: 0,
  firstTokenLatencyMs: 0,
  traceStages: [],
  isTyping: false,
  isThinking: false,
})

const fetchChatHistories = async () => {
  clearHistorySearchTimer()
  if (!authState.accessToken) {
    chatHistoryItems.value = []
    historyTotal.value = 0
    return
  }
  historyLoading.value = true
  try {
    const { data } = await apiClient.get('/api/aimed/chat/histories', {
      params: {
        page: historyPage.value,
        size: historyPageSize,
        keyword: historyQuery.value.trim() || undefined,
      },
    })
    chatHistoryItems.value = Array.isArray(data?.items)
      ? data.items.filter((item) => item?.title && item.title !== '未命名对话')
      : []
    historyTotal.value = Number(data?.total || 0)
    if (historyPage.value > 1 && historyTotal.value > 0 && chatHistoryItems.value.length === 0) {
      historyPage.value = Math.max(1, historyPage.value - 1)
      await fetchChatHistories()
      return
    }
  } catch (error) {
    console.error('加载历史会话失败:', error)
    const fallbackItems = readCachedHistoryIndex().filter((item) =>
      !item.hidden && hasMeaningfulHistoryContent(readCachedHistoryDetail(item.memoryId)?.messages)
    )
    const keyword = historyQuery.value.trim().toLowerCase()
    const filteredFallbackItems = keyword
      ? fallbackItems.filter((item) =>
        [item.title, item.customTitle, item.firstQuestion]
          .filter(Boolean)
          .some((value) => String(value).toLowerCase().includes(keyword))
      )
      : fallbackItems
    historyTotal.value = filteredFallbackItems.length
    const startIndex = (historyPage.value - 1) * historyPageSize
    chatHistoryItems.value = filteredFallbackItems.slice(startIndex, startIndex + historyPageSize)
    if (historyPage.value > 1 && historyTotal.value > 0 && chatHistoryItems.value.length === 0) {
      historyPage.value = Math.max(1, historyPage.value - 1)
      await fetchChatHistories()
      return
    }
    ElMessage[filteredFallbackItems.length ? 'warning' : 'error'](
      resolveUiErrorMessage(
        error,
        filteredFallbackItems.length
          ? '历史会话列表加载失败，已切换到本地缓存。'
          : '历史会话列表加载失败，请稍后重试。'
      )
    )
  } finally {
    historyLoading.value = false
  }
}

const restoreChatHistory = async (memoryId, { silent = false } = {}) => {
  if (!memoryId || isSending.value) {
    return
  }
  markChatActivity()
  cancelRenameHistory()
  restoringHistoryMemoryId.value = memoryId
  try {
    const { data } = await apiClient.get(`/api/aimed/chat/histories/${memoryId}`, { params: historyDebugQuery.value })
    uuid.value = String(data.memoryId || memoryId)
    sessionStorage.setItem(CHAT_MEMORY_STORAGE_KEY, uuid.value)
    clearComposerAttachments()
    messages.value = Array.isArray(data.messages) && data.messages.length
      ? data.messages.map(mapHistoryMessageToViewMessage)
      : [{
        isUser: false,
        rawContent: welcomeMessageMarkdown,
        content: convertStreamOutput(welcomeMessageMarkdown),
        isTyping: false,
        isThinking: false,
      }]
    shouldStickToBottom.value = true
    await nextTick()
    scrollToBottom(true)
  } catch (error) {
    console.error('恢复历史会话失败:', error)
    if (error?.response?.status === 404) {
      purgeInvalidHistoryItem(memoryId)
      if (!silent) {
        ElMessage.warning('这条历史对话已失效，已从列表中移除')
      }
      return
    }
    if (!historyDebugMode.value && restoreCachedHistory(memoryId)) {
      return
    }
    if (silent) {
      showWelcomeMessage()
      return
    }
    ElMessage.error('当前历史会话暂时无法恢复')
  } finally {
    restoringHistoryMemoryId.value = null
  }
}

const startRenameHistory = (item) => {
  renamingHistoryId.value = item.memoryId
  renamingHistoryTitle.value = item.customTitle || item.title || ''
}

const cancelRenameHistory = () => {
  renamingHistoryId.value = null
  renamingHistoryTitle.value = ''
}

const submitRenameHistory = async (item) => {
  const title = renamingHistoryTitle.value.trim()
  if (!title) {
    ElMessage.warning('会话标题不能为空')
    return
  }
  try {
    const { data } = await apiClient.put(`/api/aimed/chat/histories/${item.memoryId}/rename`, { title })
    updateHistoryItemLocal(data)
    writeCachedHistoryIndex(readCachedHistoryIndex().map((historyItem) =>
      String(historyItem.memoryId) === String(item.memoryId)
        ? { ...historyItem, title: data.title, customTitle: data.customTitle }
        : historyItem
    ))
    const cachedDetail = readCachedHistoryDetail(item.memoryId)
    if (cachedDetail) {
      writeCachedHistoryDetail(item.memoryId, { ...cachedDetail, title: data.title, customTitle: data.customTitle })
    }
    if (String(item.memoryId) === String(uuid.value)) {
      await fetchChatHistories()
    }
    ElMessage.success('历史会话已重命名')
    cancelRenameHistory()
  } catch (error) {
    console.error('重命名历史会话失败:', error)
    ElMessage.error('重命名失败，请稍后重试')
  }
}

const togglePinHistory = async (item, pinned) => {
  try {
    const { data } = await apiClient.put(`/api/aimed/chat/histories/${item.memoryId}/pin`, { pinned })
    updateHistoryItemLocal(data)
    writeCachedHistoryIndex(readCachedHistoryIndex().map((historyItem) =>
      String(historyItem.memoryId) === String(item.memoryId)
        ? { ...historyItem, pinned: data.pinned }
        : historyItem
    ))
    ElMessage.success(pinned ? '已置顶该会话' : '已取消置顶')
  } catch (error) {
    console.error('更新置顶状态失败:', error)
    ElMessage.error('操作失败，请稍后重试')
  }
}

const shareHistory = async (item) => {
  try {
    const { data } = await apiClient.get(`/api/aimed/chat/histories/${item.memoryId}`, { params: historyDebugQuery.value })
    const exportMessages = Array.isArray(data.messages) ? data.messages.map(mapHistoryMessageToViewMessage) : []
    downloadChatMarkdown({
      messages: exportMessages,
      memoryId: item.memoryId,
      modelLabel: currentModelOption.value.label,
      sessionTitle: data.title || item.title,
    })
  } catch (error) {
    console.error('导出历史会话失败:', error)
    ElMessage.error('当前历史会话暂时无法导出')
  }
}

const toggleHistoryDebugMode = async () => {
  historyDebugMode.value = !historyDebugMode.value
  historyPage.value = 1
  await fetchChatHistories()
  if (uuid.value) {
    await restoreChatHistory(uuid.value, { silent: true })
  }
}

const changeHistoryPage = async (nextPage) => {
  const normalizedPage = Math.min(Math.max(1, nextPage), historyPageCount.value)
  if (normalizedPage === historyPage.value) {
    return
  }
  historyPage.value = normalizedPage
  await fetchChatHistories()
}

const deleteHistory = async (item) => {
  try {
    await confirmDanger(buildHistoryDeleteDialogMessage(item.title), '删除历史对话')
  } catch {
    return
  }

  try {
    await apiClient.delete(`/api/aimed/chat/histories/${item.memoryId}`)
    chatHistoryItems.value = chatHistoryItems.value.filter((historyItem) => historyItem.memoryId !== item.memoryId)
    writeCachedHistoryIndex(readCachedHistoryIndex().map((historyItem) =>
      String(historyItem.memoryId) === String(item.memoryId)
        ? { ...historyItem, hidden: true }
        : historyItem
    ))
    removeCachedHistoryDetail(item.memoryId)
    if (String(item.memoryId) === String(uuid.value)) {
      await startNewChatSession()
    }
    ElMessage.success('历史对话已删除')
  } catch (error) {
    console.error('删除历史会话失败:', error)
    ElMessage.error('删除失败，请稍后重试')
  }
}

const handleHistoryCommand = (command, item) => {
  if (command === 'pin') {
    togglePinHistory(item, true)
    return
  }
  if (command === 'unpin') {
    togglePinHistory(item, false)
    return
  }
  if (command === 'share') {
    shareHistory(item)
    return
  }
  if (command === 'rename') {
    startRenameHistory(item)
    return
  }
  if (command === 'delete') {
    deleteHistory(item)
  }
}

const initDesktopSidebar = () => {
  desktopSidebarCollapsed.value = localStorage.getItem('chat_sidebar_collapsed') === 'true'
}

const toggleDesktopSidebar = () => {
  desktopSidebarCollapsed.value = !desktopSidebarCollapsed.value
  localStorage.setItem('chat_sidebar_collapsed', String(desktopSidebarCollapsed.value))
}

const setModelProvider = (provider) => {
  markChatActivity()
  selectedModelProvider.value = [QWEN_ONLINE_FAST, QWEN_ONLINE_DEEP].includes(provider) ? provider : LOCAL_OLLAMA
  modelPickerVisible.value = false
  localStorage.setItem('chat_model_provider', selectedModelProvider.value)
}

const sendQuickPrompt = (prompt) => {
  markChatActivity()
  inputMessage.value = prompt
  sendMessage()
}

const sendQuickPromptFromPanel = (prompt) => {
  mobilePanelVisible.value = false
  sendQuickPrompt(prompt)
}

const sendMessage = () => {
  if (sessionExpired.value || isSending.value || (!inputMessage.value.trim() && chatAttachments.value.length === 0)) {
    return
  }
  if (!uuid.value) {
    ElMessage.error('当前会话尚未准备完成，请刷新页面或重新新建会话。')
    return
  }
  markChatActivity()
  sendRequest(inputMessage.value.trim())
  inputMessage.value = ''
  resetMobileTextareaHeight()
}

const handleComposerKeydown = (event) => {
  if (event.key !== 'Enter' || event.shiftKey) {
    return
  }

  if (event.isComposing || isComposing.value) {
    return
  }

  event.preventDefault()
  sendMessage()
}

const openKnowledgeCenter = () => {
  if (!isAdmin.value) {
    ElMessage.warning('普通用户无此权限!')
    return
  }
  if (isMobileViewport.value) {
    router.push('/knowledge')
    return
  }
  window.open('/knowledge', '_blank', 'noopener')
}

const openAdminConsole = () => {
  if (!isAdmin.value) {
    return
  }
  if (isMobileViewport.value) {
    router.push('/admin')
    return
  }
  window.open('/admin', '_blank', 'noopener')
}

const handleKnowledgeCenterFromPanel = () => {
  mobilePanelVisible.value = false
  openKnowledgeCenter()
}

const handleAdminConsoleFromPanel = () => {
  mobilePanelVisible.value = false
  openAdminConsole()
}

const openChatAttachmentUpload = () => {
  markChatActivity()
  chatAttachmentInputRef.value?.click()
}

const buildAttachmentName = (fileName, contentType) => {
  const original = String(fileName || 'attachment').trim() || 'attachment'
  const extension = extractAttachmentExtension(original)
  if (contentType === 'image/jpeg' && extension !== 'jpg' && extension !== 'jpeg') {
    return original.replace(/\.[^.]+$/, '') + '.jpg'
  }
  if (contentType === 'image/webp' && extension !== 'webp') {
    return original.replace(/\.[^.]+$/, '') + '.webp'
  }
  return original
}

const buildAttachmentId = (file) => `${file.name}-${file.size}-${file.lastModified}`

const formatAttachmentSize = (bytes) => {
  if (!Number.isFinite(bytes) || bytes <= 0) {
    return '0 KB'
  }
  if (bytes < 1024) {
    return `${bytes} B`
  }
  return `${(bytes / 1024).toFixed(bytes >= 1024 * 100 ? 0 : 1)} KB`
}

const totalChatAttachmentBytes = (attachments) =>
  (Array.isArray(attachments) ? attachments : []).reduce((sum, attachment) => {
    const size = Number(attachment?.size || 0)
    return sum + (Number.isFinite(size) ? size : 0)
  }, 0)

const loadImageBitmap = async (file) => {
  if (typeof createImageBitmap === 'function') {
    return createImageBitmap(file)
  }

  const objectUrl = URL.createObjectURL(file)
  try {
    const image = await new Promise((resolve, reject) => {
      const element = new Image()
      element.onload = () => resolve(element)
      element.onerror = () => reject(new Error('图片加载失败'))
      element.src = objectUrl
    })
    return image
  } finally {
    URL.revokeObjectURL(objectUrl)
  }
}

const canvasToBlob = (canvas, type, quality) =>
  new Promise((resolve) => {
    canvas.toBlob((blob) => resolve(blob), type, quality)
  })

const compressImageAttachment = async (file) => {
  const mimeType = file?.type?.toLowerCase?.() || ''
  if (!mimeType.startsWith('image/') || mimeType === 'image/gif') {
    return null
  }

  const source = await loadImageBitmap(file)
  try {
    const targetType = mimeType === 'image/png' || mimeType === 'image/bmp' ? 'image/jpeg' : (mimeType || 'image/jpeg')
    let width = source.width
    let height = source.height

    const canvas = document.createElement('canvas')
    const context = canvas.getContext('2d', { alpha: false })
    if (!context) {
      return null
    }

    const qualities = [0.86, 0.74, 0.62, 0.5, 0.4]
    for (let step = 0; step < 6; step++) {
      canvas.width = Math.max(1, Math.round(width))
      canvas.height = Math.max(1, Math.round(height))
      context.clearRect(0, 0, canvas.width, canvas.height)
      context.fillStyle = '#ffffff'
      context.fillRect(0, 0, canvas.width, canvas.height)
      context.drawImage(source, 0, 0, canvas.width, canvas.height)

      for (const quality of qualities) {
        const blob = await canvasToBlob(canvas, targetType, quality)
        if (blob && blob.size <= MAX_CHAT_ATTACHMENT_BYTES) {
          return new File(
            [blob],
            buildAttachmentName(file.name, blob.type || targetType),
            { type: blob.type || targetType, lastModified: Date.now() }
          )
        }
      }

      width *= 0.82
      height *= 0.82
    }

    return null
  } finally {
    if (typeof source.close === 'function') {
      source.close()
    }
  }
}

const prepareChatAttachmentFiles = async (files) => {
  const acceptedFiles = []
  const oversizedNonImages = []
  const oversizedImagesCompressed = []
  const oversizedImagesFailed = []

  for (const file of files) {
    if (!file) {
      continue
    }
    if (file.size <= MAX_CHAT_ATTACHMENT_BYTES) {
      acceptedFiles.push(file)
      continue
    }
    if (!isImageFile(file)) {
      oversizedNonImages.push(file.name)
      continue
    }
    try {
      const compressedFile = await compressImageAttachment(file)
      if (compressedFile && compressedFile.size <= MAX_CHAT_ATTACHMENT_BYTES) {
        acceptedFiles.push(compressedFile)
        oversizedImagesCompressed.push(`${file.name} -> ${formatAttachmentSize(compressedFile.size)}`)
      } else {
        oversizedImagesFailed.push(file.name)
      }
    } catch (error) {
      console.error('前端压缩图片失败:', error)
      oversizedImagesFailed.push(file.name)
    }
  }

  return {
    acceptedFiles,
    oversizedNonImages,
    oversizedImagesCompressed,
    oversizedImagesFailed,
  }
}

const createPreviewUrl = (file) => {
  if (!isImageFile(file) || typeof URL === 'undefined' || typeof URL.createObjectURL !== 'function') {
    return ''
  }
  const previewUrl = URL.createObjectURL(file)
  attachmentPreviewUrls.add(previewUrl)
  return previewUrl
}

const normalizeChatAttachment = (file) => ({
  id: buildAttachmentId(file),
  file,
  name: file.name,
  size: file.size,
  contentType: file.type || '',
  extension: extractAttachmentExtension(file.name),
  lastModified: file.lastModified,
  isImage: isImageFile(file),
  kindLabel: isImageFile(file) ? '图片' : '文件',
  badgeLabel: attachmentBadgeLabel({ name: file.name, extension: extractAttachmentExtension(file.name), isImage: isImageFile(file) }),
  sizeLabel: formatAttachmentSize(file.size),
  previewUrl: createPreviewUrl(file),
})

const cloneMessageAttachment = (attachment) => ({
  id: attachment.id,
  name: attachment.name,
  size: attachment.size,
  contentType: attachment.contentType,
  extension: attachment.extension,
  isImage: attachment.isImage,
  kindLabel: attachment.kindLabel,
  badgeLabel: attachment.badgeLabel,
  sizeLabel: attachment.sizeLabel,
  previewUrl: attachment.previewUrl,
})

const extractAttachmentExtension = (name) => {
  if (!name || !String(name).includes('.')) {
    return ''
  }
  return String(name).split('.').pop().trim().toLowerCase()
}

const attachmentBadgeLabel = (attachment) => {
  const extension = String(attachment?.extension || extractAttachmentExtension(attachment?.name || '')).trim().toUpperCase()
  if (extension) {
    return extension.length > 6 ? extension.slice(0, 6) : extension
  }
  return (attachment?.image ?? attachment?.isImage) ? '图片' : '文件'
}

const openCitationKnowledge = (citation) => {
  if (!citation?.fileHash) {
    return
  }
  const targetUrl = router.resolve({ path: '/knowledge', query: { hash: citation.fileHash } }).href
  window.open(targetUrl, '_blank', 'noopener')
}

const createServerSession = async () => {
  const { data } = await apiClient.post('/api/aimed/chat/sessions')
  if (!data?.memoryId) {
    throw new Error('创建新会话失败')
  }
  return String(data.memoryId)
}

const startNewChatSession = async () => {
  persistCurrentConversationSnapshot()
  abortActiveStream()
  isSending.value = false
  mobilePanelVisible.value = false
  clearComposerAttachments()
  inputMessage.value = ''
  cancelRenameHistory()
  try {
    uuid.value = await createServerSession()
    sessionStorage.setItem(CHAT_MEMORY_STORAGE_KEY, uuid.value)
    showWelcomeMessage()
    shouldStickToBottom.value = true
    resetMobileTextareaHeight()
    await nextTick()
    scrollToBottom(true)
    fetchChatHistories()
  } catch (error) {
    console.error('创建新会话失败:', error)
    ElMessage.error(resolveUiErrorMessage(error, '创建新会话失败，请稍后重试。'))
  }
}

const releaseAttachmentPreview = (attachment) => {
  const previewUrl = attachment?.previewUrl
  if (!previewUrl || !attachmentPreviewUrls.has(previewUrl)) {
    return
  }
  URL.revokeObjectURL(previewUrl)
  attachmentPreviewUrls.delete(previewUrl)
}

const releaseAllAttachmentPreviews = () => {
  attachmentPreviewUrls.forEach((previewUrl) => {
    URL.revokeObjectURL(previewUrl)
  })
  attachmentPreviewUrls.clear()
}

const clearAttachmentInput = () => {
  if (chatAttachmentInputRef.value) {
    chatAttachmentInputRef.value.value = ''
  }
}

const clearComposerAttachments = ({ releasePreviews = true } = {}) => {
  if (releasePreviews) {
    chatAttachments.value.forEach(releaseAttachmentPreview)
  }
  chatAttachments.value = []
  composerDragActive.value = false
  clearAttachmentInput()
}

const ingestChatAttachments = async (files) => {
  if (files.length === 0) {
    return
  }

  const {
    acceptedFiles,
    oversizedNonImages,
    oversizedImagesCompressed,
    oversizedImagesFailed,
  } = await prepareChatAttachmentFiles(files)

  if (oversizedNonImages.length) {
    ElMessage.warning(`单个附件大小不能超过 1MB：${oversizedNonImages.join('、')}`)
  }
  if (oversizedImagesCompressed.length) {
    ElMessage.success(`已自动压缩图片：${oversizedImagesCompressed.join('；')}`)
  }
  if (oversizedImagesFailed.length) {
    ElMessage.warning(`图片超过 1MB 且无法压缩到限制内：${oversizedImagesFailed.join('、')}`)
  }
  const nextAttachments = [...chatAttachments.value]
  const seenKeys = new Set(nextAttachments.map((attachment) => attachment.id))
  const rejectedByCount = []
  const rejectedByTotal = []

  acceptedFiles.forEach((file) => {
    const normalized = normalizeChatAttachment(file)
    if (seenKeys.has(normalized.id)) {
      releaseAttachmentPreview(normalized)
      return
    }
    if (nextAttachments.length >= MAX_CHAT_ATTACHMENT_COUNT) {
      rejectedByCount.push(file.name)
      releaseAttachmentPreview(normalized)
      return
    }
    if (totalChatAttachmentBytes(nextAttachments) + normalized.size > MAX_CHAT_ATTACHMENT_TOTAL_BYTES) {
      rejectedByTotal.push(file.name)
      releaseAttachmentPreview(normalized)
      return
    }
    nextAttachments.push(normalized)
    seenKeys.add(normalized.id)
  })

  if (rejectedByCount.length) {
    ElMessage.warning(`最多可附加 ${MAX_CHAT_ATTACHMENT_COUNT} 个附件：${rejectedByCount.join('、')}`)
  }
  if (rejectedByTotal.length) {
    ElMessage.warning(`附件总大小不能超过 2MB：${rejectedByTotal.join('、')}`)
  }

  chatAttachments.value = nextAttachments
  markChatActivity()
}

const handleChatAttachmentChange = (event) => {
  if (sessionExpired.value) {
    event.target.value = ''
    return
  }
  void ingestChatAttachments(Array.from(event.target.files || []))
  clearAttachmentInput()
}

const handleComposerDragEnter = (event) => {
  if (sessionExpired.value || isSending.value) {
    return
  }
  if (event.dataTransfer?.types?.includes('Files')) {
    composerDragActive.value = true
  }
}

const handleComposerDragOver = (event) => {
  if (sessionExpired.value || isSending.value) {
    return
  }
  event.preventDefault()
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'copy'
  }
  composerDragActive.value = true
}

const handleComposerDragLeave = (event) => {
  const currentTarget = event.currentTarget
  const relatedTarget = event.relatedTarget
  if (currentTarget instanceof HTMLElement && relatedTarget instanceof Node && currentTarget.contains(relatedTarget)) {
    return
  }
  composerDragActive.value = false
}

const handleComposerDrop = (event) => {
  if (sessionExpired.value || isSending.value) {
    composerDragActive.value = false
    return
  }
  event.preventDefault()
  composerDragActive.value = false
  void ingestChatAttachments(Array.from(event.dataTransfer?.files || []))
}

const removeChatAttachment = (index) => {
  markChatActivity()
  const [removedAttachment] = chatAttachments.value.splice(index, 1)
  releaseAttachmentPreview(removedAttachment)
}

const isImageFile = (file) => {
  const mimeType = file?.type?.toLowerCase?.() || ''
  if (mimeType.startsWith('image/')) {
    return true
  }
  const filename = file?.name?.toLowerCase?.() || ''
  return ['.png', '.jpg', '.jpeg', '.webp', '.gif', '.bmp'].some((ext) =>
    filename.endsWith(ext)
  )
}

const chatAttachmentKind = (attachment) => (attachment?.isImage ? '图片' : '文件')

const chatAttachmentClass = (attachment) =>
  attachment?.isImage
    ? 'chat-attachment-chip chat-attachment-chip-image'
    : 'chat-attachment-chip'

const refreshKnowledgeFiles = async () => {
  if (!isAdmin.value) {
    knowledgeCount.value = 0
    return
  }

  try {
    const { data } = await apiClient.get('/api/aimed/knowledge/files')
    knowledgeCount.value = Array.isArray(data) ? data.length : 0
  } catch (error) {
    console.error('获取知识库列表失败:', error)
    ElMessage.error(resolveUiErrorMessage(error, '知识库摘要加载失败，请稍后重试。'))
  }
}

const buildChatRequest = (message, attachmentsSnapshot) => {
  if (attachmentsSnapshot.length) {
    const formData = new FormData()
    formData.append('memoryId', String(uuid.value))
    formData.append('message', message || '请结合我上传的文件回答。')
    formData.append('modelProvider', selectedModelProvider.value)
    attachmentsSnapshot.forEach((attachment) => formData.append('files', attachment.file))
    return { body: formData }
  }

  return {
    body: JSON.stringify({
      memoryId: uuid.value,
      message,
      modelProvider: selectedModelProvider.value,
    }),
    headers: {
      'Content-Type': 'application/json',
    },
  }
}

const streamChatResponse = async (message, attachmentsSnapshot, lastMsg) => {
  const { body, headers } = buildChatRequest(message, attachmentsSnapshot)
  abortActiveStream()
  currentStreamController = new AbortController()
  const response = await authorizedFetch('/api/aimed/chat', {
    method: 'POST',
    headers,
    body,
    signal: currentStreamController.signal,
  })

  if (!response.ok) {
    const errorText = await response.text()
    const traceId = response.headers.get('x-trace-id')
    throw new Error(buildTraceableMessage(errorText || `HTTP ${response.status}`, traceId))
  }

  if (!response.body) {
    throw new Error('响应流为空')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        const finalText = decoder.decode()
        if (finalText) {
          lastMsg.rawContent += finalText
        }
        break
      }

      const chunkText = decoder.decode(value, { stream: true })
      if (!chunkText) {
        continue
      }

      lastMsg.rawContent += chunkText
      const parsed = extractStreamMetadata(lastMsg.rawContent)
      lastMsg.content = convertStreamOutput(parsed.content)
      applyStreamMetadata(lastMsg, parsed.metadata)
      scrollToBottom()
    }
  } finally {
    currentStreamController = null
  }

  finalizeAssistantMessage(lastMsg)
}

const sendRequest = (message) => {
  isSending.value = true
  const attachmentsSnapshot = [...chatAttachments.value]
  const displayMessage = message || '请结合我上传的文件回答。'
  const userMsg = {
    isUser: true,
    rawContent: displayMessage,
    content: convertStreamOutput(displayMessage),
    attachments: attachmentsSnapshot.map(cloneMessageAttachment),
    isTyping: false,
    isThinking: false,
  }

  if (messages.value.length > 0) {
    messages.value.push(userMsg)
  }

  const botMsg = {
    isUser: false,
    content: '',
    rawContent: '',
    citations: [],
    traceId: '',
    provider: '',
    toolMode: '',
    serverDurationMs: 0,
    firstTokenLatencyMs: 0,
    traceStages: [],
    isTyping: true,
    isThinking: false,
  }
  messages.value.push(botMsg)
  clearComposerAttachments({ releasePreviews: false })
  const lastMsg = messages.value[messages.value.length - 1]
  shouldStickToBottom.value = true
  scrollToBottom(true)

  streamChatResponse(message, attachmentsSnapshot, lastMsg)
    .then(() => {
      if (messages.value.at(-1)) {
        messages.value.at(-1).isTyping = false
      }
      isSending.value = false
      fetchChatHistories()
    })
    .catch((error) => {
      if (isAbortError(error)) {
        if (messages.value.at(-1)) {
          messages.value.at(-1).isTyping = false
        }
        isSending.value = false
        return
      }
      console.error('流式错误:', error)
      const errorMessage = resolveRequestError(error)
      ElMessage.error(errorMessage)
      if (messages.value.at(-1)) {
        messages.value.at(-1).rawContent = errorMessage
        messages.value.at(-1).content = convertStreamOutput(errorMessage)
        messages.value.at(-1).isTyping = false
      }
      isSending.value = false
      shouldStickToBottom.value = true
      scrollToBottom(true)
      fetchChatHistories()
    })
}

const finalizeAssistantMessage = (message) => {
  const { content, metadata } = extractStreamMetadata(message.rawContent)
  message.rawContent = content
  message.content = convertStreamOutput(content)
  applyStreamMetadata(message, metadata)
}

const extractStreamMetadata = (rawContent) => {
  if (!rawContent || !rawContent.includes(STREAM_METADATA_MARKER)) {
    return { content: rawContent, metadata: null }
  }
  const markerIndex = rawContent.lastIndexOf(STREAM_METADATA_MARKER)
  const content = rawContent.slice(0, markerIndex)
  const metadataText = rawContent.slice(markerIndex + STREAM_METADATA_MARKER.length).trim()
  try {
    return {
      content,
      metadata: metadataText ? JSON.parse(metadataText) : null,
    }
  } catch (error) {
    console.error('解析回答引用元数据失败:', error)
    return { content: rawContent, metadata: null }
  }
}

const initUUID = async () => {
  let storedUUID = sessionStorage.getItem(CHAT_MEMORY_STORAGE_KEY)
  const hasRestorableSession = Boolean(storedUUID)
  if (!storedUUID) {
    storedUUID = await createServerSession()
    sessionStorage.setItem(CHAT_MEMORY_STORAGE_KEY, storedUUID)
  }
  uuid.value = storedUUID
  return hasRestorableSession
}

const convertStreamOutput = (output) => {
  if (!output) {
    return ''
  }

  const normalizedOutput = output.replace(/\t/g, '    ')
  return enhanceMedicalMarkup(markdown.render(normalizedOutput))
}

const resolveRequestError = (error) => {
  const serverMessage =
    error?.response?.data?.message || error?.response?.data?.error || error?.message
  if (serverMessage) {
    return `当前服务暂时不可用：${serverMessage}`
  }

  return '当前模型服务暂时不稳定，请稍后重试。若持续失败，请检查本机 Ollama 或在线模型连通性。'
}

const buildTraceableMessage = (message, traceId) => {
  if (!traceId) {
    return message
  }
  return `${message}（traceId: ${traceId}）`
}

const resolveUiErrorMessage = (error, fallback) => {
  const traceId = error?.response?.headers?.['x-trace-id'] || error?.response?.headers?.['X-Trace-Id']
  const serverMessage =
    error?.response?.data?.message || error?.response?.data?.error || error?.message
  if (serverMessage) {
    return buildTraceableMessage(serverMessage, traceId)
  }
  return fallback
}

const enhanceMedicalMarkup = (renderedHtml) => {
  if (typeof DOMParser === 'undefined') {
    return renderedHtml
  }

  const parser = new DOMParser()
  const documentNode = parser.parseFromString(
    `<div class="markdown-root">${renderedHtml}</div>`,
    'text/html'
  )
  const root = documentNode.body.firstElementChild

  if (!root) {
    return renderedHtml
  }

  root.querySelectorAll('table').forEach((table) => {
    if (table.parentElement?.classList.contains('table-scroll')) {
      return
    }

    const wrapper = documentNode.createElement('div')
    wrapper.className = 'table-scroll'
    table.parentNode?.insertBefore(wrapper, table)
    wrapper.appendChild(table)
  })

  root.querySelectorAll('p').forEach((paragraph) => {
    if (paragraph.closest('blockquote, li, td, th, pre, .medical-callout')) {
      return
    }

    const text = paragraph.textContent?.trim() || ''
    const matchedRule = medicalCalloutRules.find((rule) => rule.pattern.test(text))
    if (!matchedRule) {
      return
    }

    const wrapper = documentNode.createElement('div')
    wrapper.className = `medical-callout ${matchedRule.className}`
    const header = documentNode.createElement('div')
    header.className = 'medical-callout-header'

    const icon = documentNode.createElement('span')
    icon.className = 'medical-callout-icon'
    icon.setAttribute('aria-hidden', 'true')

    const label = documentNode.createElement('span')
    label.className = 'medical-callout-label'
    label.textContent = matchedRule.label

    const body = documentNode.createElement('div')
    body.className = 'medical-callout-body'

    paragraph.parentNode?.insertBefore(wrapper, paragraph)
    header.appendChild(icon)
    header.appendChild(label)
    wrapper.appendChild(header)
    wrapper.appendChild(body)
    body.appendChild(paragraph)
  })

  return root.innerHTML
}

const newChat = async () => {
  markChatActivity()
  await startNewChatSession()
}

const handleNewChatFromPanel = () => {
  mobilePanelVisible.value = false
  newChat()
}

const exportConversation = () => {
  if (isSending.value) {
    return
  }
  markChatActivity()
  downloadChatMarkdown({
    messages: messages.value,
    memoryId: uuid.value,
    modelLabel: currentModelOption.value.label,
    sessionTitle: chatHistoryItems.value.find((item) => String(item.memoryId) === String(uuid.value))?.title,
  })
}

const exportTimedOutConversation = () => {
  downloadChatMarkdown({
    messages: timedOutMessagesSnapshot.value,
    memoryId: timedOutMemoryId.value || uuid.value,
    modelLabel: timedOutModelLabel.value || currentModelOption.value.label,
  })
}

const handleLogout = async () => {
  abortActiveStream()
  mobilePanelVisible.value = false
  isSending.value = false
  clearComposerAttachments()
  sessionStorage.removeItem(CHAT_MEMORY_STORAGE_KEY)
  await logout()
  await router.replace('/login')
}

const confirmSessionExpired = async () => {
  sessionExpiredDialogVisible.value = false
  await router.replace('/login')
}

const clearIdleTimer = () => {}

const handleIdleActivity = () => {
  if (document.hidden) {
    return
  }
  markAuthActivity()
}

const attachIdleActivityListeners = () => {}

const detachIdleActivityListeners = () => {}

const syncIdleTimeoutPolicy = () => {
  syncAuthIdleTimeoutPolicy()
}

const restartIdleTimer = () => {
  markAuthActivity()
}

const markChatActivity = () => {
  markAuthActivity()
}

const handleSessionExpired = async () => {}

const isMobileViewport = ref(false)

const detectBrowserContext = () => {
  if (typeof navigator === 'undefined') {
    return
  }
  isWeChatBrowser.value = /MicroMessenger/i.test(navigator.userAgent || '')
}

const syncViewportMetrics = () => {
  if (typeof window === 'undefined') {
    return
  }

  const layoutHeight = Math.round(window.innerHeight || document.documentElement.clientHeight || 0)
  const visualViewport = window.visualViewport
  const visibleHeight = Math.round(visualViewport?.height || layoutHeight)
  const offsetTop = Math.round(visualViewport?.offsetTop || 0)

  if (!maxObservedViewportHeight) {
    maxObservedViewportHeight = Math.max(layoutHeight, visibleHeight)
  } else if (!keyboardOpen.value && layoutHeight > 0 && Math.abs(layoutHeight - maxObservedViewportHeight) > 120) {
    maxObservedViewportHeight = Math.max(layoutHeight, visibleHeight)
  } else {
    maxObservedViewportHeight = Math.max(maxObservedViewportHeight, layoutHeight, visibleHeight)
  }

  const estimatedKeyboardInset = visualViewport
    ? Math.max(0, maxObservedViewportHeight - visibleHeight - offsetTop)
    : 0

  keyboardOpen.value = isMobileViewport.value && estimatedKeyboardInset > 90
  keyboardInset.value = keyboardOpen.value ? estimatedKeyboardInset : 0
  viewportHeight.value = visibleHeight || layoutHeight || maxObservedViewportHeight || 0

  if (!keyboardOpen.value && layoutHeight > 0) {
    maxObservedViewportHeight = Math.max(layoutHeight, visibleHeight)
  }
}

const updateViewportState = () => {
  isMobileViewport.value = window.matchMedia('(max-width: 768px)').matches
  syncViewportMetrics()
}

const setupViewportTracking = () => {
  viewportMediaQuery = window.matchMedia('(max-width: 768px)')
  updateViewportState()
  if (typeof viewportMediaQuery.addEventListener === 'function') {
    viewportMediaQuery.addEventListener('change', updateViewportState)
  } else {
    viewportMediaQuery.addListener(updateViewportState)
  }
}

const detachViewportTracking = () => {
  if (viewportMediaQuery) {
    if (typeof viewportMediaQuery.removeEventListener === 'function') {
      viewportMediaQuery.removeEventListener('change', updateViewportState)
    } else {
      viewportMediaQuery.removeListener(updateViewportState)
    }
  }
  viewportMediaQuery = null
}

const clearViewportSyncTimers = () => {
  viewportSyncTimers.forEach((timer) => window.clearTimeout(timer))
  viewportSyncTimers = []
}

const scrollComposerIntoView = () => {
  const inputElement = mobileComposerTextareaRef.value || composerInputRef.value?.textarea
  const composerElement = chatPanelRef.value?.querySelector('.composer')
  inputElement?.scrollIntoView?.({ block: 'nearest', inline: 'nearest' })
  composerElement?.scrollIntoView?.({ block: 'nearest', inline: 'nearest' })
}

const scheduleViewportAlignment = (force = false) => {
  if (!isMobileViewport.value) {
    return
  }

  clearViewportSyncTimers()
  const delays = isWeChatMobile.value ? [180, 320, 520] : [80, 180, 320, 480]
  delays.forEach((delay) => {
    const timer = window.setTimeout(() => {
      syncViewportMetrics()
      scrollToBottom(force)
      scrollComposerIntoView()
    }, delay)
    viewportSyncTimers.push(timer)
  })
}

const handleComposerFocus = () => {
  markChatActivity()
  if (isWeChatMobile.value) {
    const timer = window.setTimeout(() => {
      syncViewportMetrics()
    }, 120)
    viewportSyncTimers.push(timer)
    return
  }
  scheduleViewportAlignment(true)
}

const handleComposerBlur = () => {
  const timer = window.setTimeout(() => {
    syncViewportMetrics()
    scrollToBottom()
  }, 120)
  viewportSyncTimers.push(timer)
}

const updateMobileTextareaHeight = (target = mobileComposerTextareaRef.value) => {
  if (!target) {
    return
  }

  target.style.height = 'auto'
  const nextHeight = Math.min(Math.max(target.scrollHeight, 46), 132)
  target.style.height = `${nextHeight}px`
}

const resetMobileTextareaHeight = () => {
  if (!mobileComposerTextareaRef.value) {
    return
  }

  mobileComposerTextareaRef.value.style.height = '46px'
}

const handleMobileTextareaInput = (event) => {
  markChatActivity()
  updateMobileTextareaHeight(event.target)
}

const attachMessageObserver = () => {
  const container = messageListRef.value
  if (!container) {
    return
  }
  detachMessageObserver()
  messageMutationObserver = new MutationObserver(() => {
    if (shouldStickToBottom.value) {
      scrollToBottom()
    }
  })
  messageMutationObserver.observe(container, {
    childList: true,
    subtree: true,
    characterData: true,
  })
}

const detachMessageObserver = () => {
  messageMutationObserver?.disconnect()
  messageMutationObserver = null
}

const attachVisualViewportObserver = () => {
  if (!window.visualViewport) {
    return
  }

  const handleViewportChange = () => {
    syncViewportMetrics()
    if (isWeChatMobile.value && !keyboardOpen.value) {
      return
    }
    if (shouldStickToBottom.value || keyboardOpen.value) {
      scrollToBottom(true)
      scrollComposerIntoView()
    }
  }

  window.visualViewport.addEventListener('resize', handleViewportChange)
  window.visualViewport.addEventListener('scroll', handleViewportChange)
  visualViewportCleanup = () => {
    window.visualViewport?.removeEventListener('resize', handleViewportChange)
    window.visualViewport?.removeEventListener('scroll', handleViewportChange)
  }
}

const detachVisualViewportObserver = () => {
  visualViewportCleanup?.()
  visualViewportCleanup = null
}

const abortActiveStream = () => {
  if (currentStreamController) {
    currentStreamController.abort()
    currentStreamController = null
  }
}

const isAbortError = (error) => {
  return error?.name === 'AbortError' || String(error?.message || '').includes('aborted')
}

watch(isAdmin, (value) => {
  if (value) {
    clearIdleTimer()
  }
  syncIdleTimeoutPolicy()
})
</script>

<style scoped>
.page-shell {
  display: grid;
  height: 100dvh;
  min-height: 100dvh;
  grid-template-columns: 224px minmax(0, 1fr);
  gap: 16px;
  padding: 16px;
  overflow: hidden;
  background:
    radial-gradient(circle at top left, rgba(111, 194, 190, 0.24), transparent 26%),
    radial-gradient(circle at bottom right, rgba(25, 83, 92, 0.18), transparent 30%),
    linear-gradient(135deg, #eef7f6 0%, #f6fbfb 55%, #eef3f8 100%);
}

.page-shell.sidebar-collapsed {
  grid-template-columns: 92px minmax(0, 1fr);
}

.page-shell.mobile-viewport {
  min-height: var(--chat-viewport-height, 100dvh);
}

.brand-panel,
.workspace {
  min-width: 0;
  min-height: 0;
}

.brand-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  padding-right: 4px;
}

@media (min-width: 961px) {
  .brand-panel {
    position: sticky;
    top: 0;
    max-height: calc(100dvh - 32px);
  }
}

.brand-panel.collapsed {
  gap: 8px;
}

.brand-card,
.control-card,
.suggestion-card,
.hero-panel,
.chat-panel {
  border: 1px solid rgba(60, 115, 122, 0.12);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.84);
  box-shadow: 0 18px 40px rgba(24, 73, 83, 0.08);
  backdrop-filter: blur(14px);
}

.brand-card,
.control-card,
.suggestion-card {
  padding: 14px;
}

.brand-card-head {
  display: flex;
  justify-content: flex-start;
  margin-top: 12px;
}

.sidebar-toggle-button {
  min-height: 28px;
  padding: 0 10px;
  border: 1px solid rgba(79, 160, 160, 0.16);
  border-radius: 999px;
  background: rgba(244, 250, 249, 0.92);
  color: #2b6770;
  font: inherit;
  font-size: 11px;
  font-weight: 700;
  cursor: pointer;
  transition: transform 0.18s ease, border-color 0.18s ease, background 0.18s ease;
}

.sidebar-toggle-button:hover {
  transform: translateY(-1px);
  border-color: rgba(46, 136, 140, 0.24);
  background: rgba(236, 247, 246, 0.96);
}

.brand-lockup {
  display: flex;
  gap: 12px;
  align-items: center;
}

.brand-copy {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.brand-logo {
  width: 60px;
  height: 60px;
  flex-shrink: 0;
  border-radius: 18px;
  background: linear-gradient(180deg, #ffffff 0%, #eef8f7 100%);
  padding: 8px;
  box-shadow: inset 0 0 0 1px rgba(79, 160, 160, 0.12), 0 12px 22px rgba(29, 83, 92, 0.06);
}

.brand-panel.collapsed .brand-card,
.brand-panel.collapsed .control-card {
  padding: 12px 10px;
}

.brand-panel.collapsed .brand-card-head {
  justify-content: center;
  margin-top: 12px;
}

.brand-panel.collapsed .brand-lockup {
  justify-content: center;
}

.brand-panel.collapsed .brand-logo {
  width: 52px;
  height: 52px;
}

.brand-eyebrow,
.hero-kicker {
  margin: 0 0 6px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #5c8d8f;
}

.brand-title {
  margin: 0;
  font-size: 16px;
  line-height: 1.22;
  color: #16363c;
}

.brand-subtitle {
  margin: 0;
  font-size: 11px;
  line-height: 1.5;
  color: #4e6f74;
}

.brand-model-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: 18px;
  background: linear-gradient(135deg, rgba(244, 250, 249, 0.96) 0%, rgba(230, 245, 243, 0.86) 100%);
  box-shadow: inset 0 0 0 1px rgba(79, 160, 160, 0.12);
}

.brand-model-copy {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.brand-model-copy span {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #6a898f;
}

.brand-model-copy strong {
  font-size: 13px;
  color: #18454d;
}

.brand-status-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-top: 12px;
}

.brand-status-item {
  padding: 9px 10px;
  border-radius: 12px;
  background: rgba(108, 189, 180, 0.12);
  box-shadow: inset 0 0 0 1px rgba(79, 160, 160, 0.1);
}

.brand-status-item span {
  display: block;
  margin-bottom: 4px;
  font-size: 10px;
  color: #5c8d8f;
}

.brand-status-item strong {
  font-size: 13px;
  color: #19535c;
}

.brand-status-item small {
  display: block;
  margin-top: 3px;
  font-size: 10px;
  color: #6d8a90;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-radius: 999px;
  padding: 7px 11px;
  font-size: 10px;
  font-weight: 700;
  color: #19535c;
  background: rgba(108, 189, 180, 0.14);
}

.control-header,
.section-heading,
.composer-toolbar,
.composer-toolbar-left,
.composer-toolbar-right {
  display: flex;
  align-items: center;
}

.control-header,
.section-heading {
  justify-content: space-between;
  margin-bottom: 10px;
  color: #183f45;
}

.control-header {
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}

.control-header-copy,
.mobile-panel-head-copy {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.control-header-copy span,
.mobile-panel-head-copy span {
  font-size: 12px;
  font-weight: 700;
  color: #183f45;
}

.control-header-copy small,
.mobile-panel-head-copy small {
  font-size: 10px;
  color: #759196;
}

.section-heading small {
  font-size: 11px;
  color: #6d8a90;
}

.session-badge {
  display: inline-flex;
  align-items: flex-start;
  justify-content: flex-start;
  border-radius: 16px;
  width: 100%;
  min-width: 0;
  padding: 8px 10px;
  font-size: 10px;
  font-weight: 700;
  line-height: 1.4;
  white-space: normal;
  overflow-wrap: anywhere;
  word-break: break-all;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  color: #1d5962;
  background: rgba(108, 189, 180, 0.14);
}

.control-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  align-items: stretch;
}

.control-actions.compact {
  grid-template-columns: 1fr;
  gap: 8px;
}

.primary-action,
.secondary-action,
.ghost-action,
.send-action {
  width: 100%;
  min-height: 38px;
  border-radius: 12px;
  font-weight: 700;
}

.control-actions :deep(.el-button) {
  margin: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding-inline: 12px;
  white-space: nowrap;
}

.control-actions.compact :deep(.el-button) {
  padding-inline: 0;
}

.primary-action,
.send-action {
  border: none;
  background: linear-gradient(135deg, #2e888c 0%, #57ada3 100%);
  box-shadow: 0 16px 28px rgba(56, 139, 140, 0.22);
}

.secondary-action,
.ghost-action {
  border-color: rgba(46, 136, 140, 0.22);
  color: #226b73;
  background: rgba(227, 244, 242, 0.72);
}

.knowledge-mini-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-top: 10px;
}

.knowledge-mini-item {
  padding: 9px 10px;
  border-radius: 14px;
  background: rgba(227, 244, 242, 0.72);
}

.knowledge-mini-item-button {
  border: none;
  text-align: left;
  cursor: pointer;
}

.knowledge-mini-item-highlight {
  background: linear-gradient(135deg, rgba(87, 173, 163, 0.2) 0%, rgba(46, 136, 140, 0.16) 100%);
  box-shadow: inset 0 0 0 1px rgba(46, 136, 140, 0.14);
}

.knowledge-mini-item span {
  display: block;
  margin-bottom: 4px;
  font-size: 10px;
  color: #6d8a90;
}

.knowledge-mini-item strong {
  font-size: 12px;
  color: #1f5e67;
}

.knowledge-summary {
  margin: 12px 0 0;
  font-size: 11px;
  line-height: 1.5;
  color: #4d6f74;
}

.history-card {
  padding: 12px 14px;
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(251, 254, 254, 0.97) 0%, rgba(243, 250, 250, 0.92) 100%);
  box-shadow: inset 0 0 0 1px rgba(91, 146, 150, 0.1), 0 18px 32px rgba(29, 83, 92, 0.06);
}

.history-card-expanded .history-list {
  max-height: 360px;
}

.history-heading {
  margin-bottom: 8px;
}

.history-heading-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.history-debug-chip {
  min-height: 24px;
  padding: 0 10px;
  border: 1px solid rgba(82, 152, 154, 0.16);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.82);
  color: #6b8a90;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.02em;
  cursor: pointer;
  transition: border-color 0.18s ease, background-color 0.18s ease, color 0.18s ease, box-shadow 0.18s ease;
}

.history-debug-chip:hover {
  border-color: rgba(61, 141, 143, 0.28);
  color: #2c646d;
}

.history-debug-chip.active {
  border-color: rgba(61, 141, 143, 0.28);
  background: rgba(111, 194, 190, 0.14);
  color: #195b64;
  box-shadow: 0 8px 18px rgba(29, 83, 92, 0.08);
}

.history-toggle-button {
  border: none;
  background: transparent;
  color: #5c7f84;
  font-size: 11px;
  font-weight: 700;
  cursor: pointer;
}

.history-list-shell {
  display: grid;
  gap: 10px;
  min-height: 72px;
}

.history-debug-banner {
  padding: 8px 10px;
  border-radius: 12px;
  background: rgba(255, 244, 215, 0.78);
  color: #7a5a12;
  font-size: 11px;
  line-height: 1.5;
}

.history-search-shell {
  position: sticky;
  top: 0;
  z-index: 1;
  padding-bottom: 2px;
  background: linear-gradient(180deg, rgba(243, 250, 250, 0.98) 0%, rgba(243, 250, 250, 0.92) 100%);
}

.history-search-meta {
  padding: 0 2px;
  color: #6a898f;
  font-size: 10px;
  font-weight: 700;
}

.history-search-input {
  width: 100%;
  min-height: 36px;
  padding: 0 12px;
  border: 1px solid rgba(79, 160, 160, 0.16);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.92);
  color: #17393f;
  font-size: 12px;
  outline: none;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.history-search-input:focus {
  border-color: rgba(46, 136, 140, 0.3);
  box-shadow: 0 0 0 4px rgba(111, 194, 190, 0.12);
}

.history-list {
  display: flex;
  max-height: 220px;
  flex-direction: column;
  gap: 8px;
  overflow-y: auto;
  padding-right: 2px;
}

.history-group {
  display: grid;
  gap: 8px;
}

.history-group-label {
  padding: 0 4px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: #5c7c82;
}

.history-empty-state {
  display: flex;
  min-height: 72px;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  background: rgba(229, 242, 241, 0.56);
  color: #678489;
  font-size: 12px;
  text-align: center;
}

.history-item {
  display: flex;
  flex-direction: column;
  width: 100%;
  padding: 10px 11px;
  border: 1px solid rgba(79, 160, 160, 0.14);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.9);
  text-align: left;
  color: #16363c;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease, opacity 0.2s ease;
}

.history-item:hover {
  transform: translateY(-1px);
  border-color: rgba(46, 136, 140, 0.24);
  box-shadow: 0 12px 22px rgba(29, 83, 92, 0.08);
}

.history-item.active {
  border-color: rgba(46, 136, 140, 0.26);
  background: rgba(238, 247, 246, 0.95);
}

.history-item.loading {
  opacity: 0.64;
}

.history-item-head {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.history-item-actions {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  flex-shrink: 0;
  min-width: 24px;
}

.history-item-head strong {
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  line-height: 1.45;
  color: #184047;
}

.history-item-menu {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  flex-shrink: 0;
  border: 0;
  border-radius: 999px;
  background: rgba(228, 241, 240, 0.92);
  color: #2b5961;
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
}

.history-item-menu:hover {
  background: rgba(213, 233, 231, 0.98);
}

.history-rename-input {
  flex: 1;
  min-width: 0;
  min-height: 30px;
  padding: 0 10px;
  border: 1px solid rgba(46, 136, 140, 0.24);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.95);
  color: #183f46;
  font-size: 12px;
  outline: none;
}

.history-rename-actions {
  display: flex;
  gap: 8px;
}

.history-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding-top: 4px;
}

.history-pagination-button {
  min-height: 30px;
  padding: 0 10px;
  border: 1px solid rgba(82, 152, 154, 0.16);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.88);
  color: #295860;
  font-size: 11px;
  font-weight: 700;
  cursor: pointer;
}

.history-pagination-button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.history-pagination-text {
  color: #67858a;
  font-size: 11px;
  font-weight: 700;
}

.history-rename-actions button {
  min-height: 28px;
  padding: 0 10px;
  border: 1px solid rgba(79, 160, 160, 0.16);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.92);
  color: #244b52;
  font-size: 11px;
  cursor: pointer;
}

.history-rename-actions button:hover {
  border-color: rgba(46, 136, 140, 0.28);
}

@media (max-width: 400px) {
  .control-actions {
    grid-template-columns: 1fr;
  }
}

.knowledge-input {
  display: none;
}

.prompt-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.prompt-chip {
  width: 100%;
  min-height: 86px;
  padding: 10px 10px 12px;
  border: 1px solid rgba(79, 160, 160, 0.16);
  border-radius: 14px;
  background: linear-gradient(180deg, rgba(247, 252, 251, 0.96) 0%, rgba(239, 248, 247, 0.9) 100%);
  text-align: left;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  font: inherit;
  color: #17393f;
  transition: transform 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}

.prompt-title {
  display: block;
  font-size: 12px;
  font-weight: 700;
  color: #184047;
}

.prompt-description {
  display: block;
  margin-top: 6px;
  font-size: 11px;
  line-height: 1.4;
  color: #5a7b80;
}

.prompt-chip:hover {
  transform: translateY(-1px);
  border-color: rgba(46, 136, 140, 0.32);
  box-shadow: 0 16px 28px rgba(39, 105, 111, 0.08);
}

.workspace {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
  overflow: hidden;
}

.hero-panel {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0;
  padding: 4px 10px;
}

.hero-bar {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  gap: 10px;
  position: relative;
}

.hero-identity {
  display: inline-flex;
  min-width: 0;
  align-items: center;
}

.hero-identity strong {
  font-size: 12px;
  color: #14373e;
  white-space: nowrap;
}

.hero-inline-actions {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  position: relative;
  z-index: 1;
}

.hero-export-button {
  min-height: 30px;
}

.hero-inline-actions :deep(.admin-entry-button),
.hero-inline-actions :deep(.logout-action-button) {
  min-height: 30px;
  padding-inline: 10px;
  box-shadow: 0 10px 18px rgba(33, 82, 89, 0.08);
}

.mobile-panel-trigger {
  display: none;
  align-items: center;
  justify-content: center;
  min-height: 32px;
  padding: 0 12px;
  border: 1px solid rgba(46, 136, 140, 0.16);
  border-radius: 999px;
  background: rgba(236, 247, 246, 0.92);
  color: #1f646c;
  font: inherit;
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
}

.chat-panel {
  position: relative;
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
}

.chat-header {
  justify-content: space-between;
  gap: 16px;
  padding: 14px 18px 10px;
  border-bottom: 1px solid rgba(70, 120, 126, 0.08);
}

.chat-header-mobile {
  padding: 8px 10px 0;
  border-bottom: none;
}

.mobile-chat-toolbar {
  display: flex;
  width: 100%;
  justify-content: flex-end;
  gap: 8px;
}

.mobile-toolbar-button {
  min-height: 30px;
  padding: 0 12px;
  border: 1px solid rgba(46, 136, 140, 0.16);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 10px 18px rgba(33, 82, 89, 0.08);
  color: #1c5c64;
  font: inherit;
  font-size: 11px;
  font-weight: 700;
  cursor: pointer;
  transition: opacity 0.2s ease, transform 0.2s ease, box-shadow 0.2s ease;
}

.mobile-toolbar-button:disabled {
  opacity: 0.52;
  cursor: not-allowed;
}

.mobile-toolbar-button:not(:disabled):active {
  transform: translateY(1px);
}

.mobile-export-button {
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96) 0%, rgba(236, 247, 246, 0.9) 100%);
}

.mobile-admin-button {
  background: linear-gradient(180deg, rgba(244, 251, 251, 0.96) 0%, rgba(228, 244, 243, 0.9) 100%);
}

.mobile-attach-button {
  min-width: 58px;
}

.mobile-chat-toolbar :deep(.logout-action-button) {
  min-height: 30px;
  padding: 0 10px;
  gap: 6px;
  font-size: 11px;
  box-shadow: 0 10px 18px rgba(33, 82, 89, 0.08);
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

.hero-assistant-title {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  font-size: 12px;
  font-weight: 800;
  color: #3e6b71;
  white-space: nowrap;
}

.mobile-chat-toolbar :deep(.logout-action-icon) {
  width: 20px;
  height: 20px;
  font-size: 12px;
}

.chat-header h3 {
  margin: 0;
  font-size: 20px;
  color: #173a40;
}

.chat-header-actions {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
}

.header-export-button {
  min-height: 32px;
  padding: 0 11px;
  border: 1px solid rgba(46, 136, 140, 0.14);
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96) 0%, rgba(236, 247, 246, 0.9) 100%);
  box-shadow: 0 14px 26px rgba(29, 83, 92, 0.08);
  color: #1d5e66;
  font: inherit;
  font-size: 10px;
  font-weight: 700;
  white-space: nowrap;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease, opacity 0.2s ease;
}

.header-export-button:hover:not(:disabled) {
  transform: translateY(-1px);
  border-color: rgba(46, 136, 140, 0.26);
  box-shadow: 0 18px 30px rgba(29, 83, 92, 0.12);
}

.header-export-button:disabled {
  opacity: 0.52;
  cursor: not-allowed;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #49b7a7;
  box-shadow: 0 0 0 6px rgba(73, 183, 167, 0.12);
}

.message-list {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  gap: 16px;
  overflow-y: auto;
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
  padding: 18px 20px;
  scroll-padding-bottom: 24px;
}

.scroll-bottom-button {
  position: absolute;
  right: 36px;
  bottom: 126px;
  z-index: 2;
  border: 1px solid rgba(46, 136, 140, 0.14);
  border-radius: 999px;
  padding: 10px 14px;
  font: inherit;
  font-size: 13px;
  font-weight: 700;
  color: #19535c;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 16px 30px rgba(28, 79, 88, 0.12);
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.scroll-bottom-button:hover {
  transform: translateY(-1px);
  box-shadow: 0 18px 34px rgba(28, 79, 88, 0.16);
}

.message-bottom-anchor {
  width: 100%;
  height: 1px;
  flex-shrink: 0;
}

.message {
  display: flex;
  gap: 14px;
  align-items: flex-start;
}

.message-user {
  flex-direction: row-reverse;
}

.message-system {
  align-self: center;
  width: min(760px, 100%);
}

.message-avatar {
  display: flex;
  width: 42px;
  height: 42px;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  border-radius: 16px;
  color: #ffffff;
  background: linear-gradient(135deg, #2e888c 0%, #5db6a7 100%);
  box-shadow: 0 12px 24px rgba(46, 136, 140, 0.18);
}

.message-user .message-avatar {
  background: linear-gradient(135deg, #17474f 0%, #2f6f78 100%);
}

.message-system .message-avatar {
  background: linear-gradient(135deg, #7faeb0 0%, #4e8d93 100%);
}

.message-body {
  max-width: min(76%, 760px);
  padding: 14px 16px;
  border-radius: 18px;
  background: #ffffff;
  box-shadow: 0 14px 30px rgba(33, 74, 83, 0.08);
}

.message-attachment-gallery {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 10px;
}

.message-attachment-gallery-user {
  justify-content: flex-end;
}

.message-attachment-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: min(220px, 100%);
}

.message-attachment-card-image {
  align-items: flex-start;
}

.message-attachment-image,
.chat-attachment-preview {
  display: block;
  width: 120px;
  height: 120px;
  border-radius: 16px;
  object-fit: cover;
  background: rgba(237, 245, 246, 0.92);
  box-shadow: 0 12px 26px rgba(28, 79, 88, 0.12);
}

.message-attachment-file {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 140px;
  padding: 12px;
  border: 1px solid rgba(79, 160, 160, 0.14);
  border-radius: 16px;
  background: rgba(239, 247, 246, 0.92);
}

.message-attachment-file strong {
  font-size: 13px;
  line-height: 1.45;
  color: #17474f;
  word-break: break-word;
}

.message-attachment-size {
  font-size: 11px;
  color: #6a878d;
}

.message-attachment-badge,
.chat-attachment-kind {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  align-self: flex-start;
  min-width: 34px;
  padding: 4px 8px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.78);
  color: #1d5962;
  font-size: 10px;
  font-weight: 700;
}

.message-attachment-caption {
  display: flex;
  flex-direction: column;
  gap: 3px;
  align-self: flex-start;
  max-width: 100%;
  padding: 8px 12px;
  border-radius: 14px;
  background: rgba(245, 248, 249, 0.96);
  color: #35575d;
  font-size: 12px;
  line-height: 1.45;
  word-break: break-word;
}

.message-attachment-caption strong {
  font-size: 12px;
  color: #214a53;
  font-weight: 700;
}

.message-attachment-caption span {
  font-size: 11px;
  color: #6a878d;
}

.message-user .message-attachment-caption {
  background: rgba(255, 255, 255, 0.16);
  color: rgba(255, 255, 255, 0.92);
}

.message-user .message-attachment-caption strong,
.message-user .message-attachment-caption span {
  color: rgba(255, 255, 255, 0.92);
}

.message-assistant .message-body {
  border-top-left-radius: 8px;
  background: linear-gradient(180deg, #ffffff 0%, #f6fbfb 100%);
}

.message-user .message-body {
  border-top-right-radius: 8px;
  background: linear-gradient(180deg, #1d5158 0%, #2f7580 100%);
  color: #ffffff;
}

.message-system .message-body {
  width: 100%;
  max-width: none;
  border-radius: 24px;
  background: linear-gradient(180deg, rgba(231, 245, 242, 0.92) 0%, rgba(246, 252, 251, 0.96) 100%);
  box-shadow: inset 0 0 0 1px rgba(79, 160, 160, 0.12);
}

:deep(.session-expired-dialog .el-dialog) {
  border: 1px solid rgba(60, 115, 122, 0.14);
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 30px 60px rgba(20, 68, 76, 0.18);
  overflow: hidden;
}

:deep(.session-expired-dialog .el-dialog__header) {
  display: none;
}

:deep(.session-expired-dialog .el-dialog__body) {
  padding: 28px 28px 8px;
}

:deep(.session-expired-dialog .el-dialog__footer) {
  padding: 0 28px 28px;
}

.session-expired-content h3 {
  margin: 0 0 12px;
  font-size: 28px;
  line-height: 1.2;
  color: #153e45;
}

.session-expired-content p {
  margin: 0;
  font-size: 14px;
  line-height: 1.8;
  color: #537278;
}

.session-expired-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(87, 173, 163, 0.14);
  color: #20727a;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.session-expired-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.session-expired-download,
.session-expired-confirm {
  min-height: 40px;
  border-radius: 14px;
  padding-inline: 16px;
  font-weight: 700;
}

.session-expired-download {
  border-color: rgba(46, 136, 140, 0.16);
  color: #1f656d;
  background: rgba(231, 245, 242, 0.74);
}

.session-expired-confirm {
  border: none;
  background: linear-gradient(135deg, #2e888c 0%, #57ada3 100%);
  box-shadow: 0 16px 28px rgba(56, 139, 140, 0.22);
}

:deep(.mobile-panel-drawer .el-drawer) {
  border-radius: 24px 24px 0 0;
  background: rgba(248, 252, 252, 0.98);
  box-shadow: 0 -24px 48px rgba(20, 68, 76, 0.18);
}

:deep(.mobile-panel-drawer .el-drawer__body) {
  padding: 12px 12px calc(12px + env(safe-area-inset-bottom, 0px));
}

.mobile-panel-sheet {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.mobile-panel-grabber {
  width: 44px;
  height: 5px;
  margin: 0 auto 4px;
  border-radius: 999px;
  background: rgba(92, 141, 143, 0.28);
}

.mobile-panel-card {
  padding: 14px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: inset 0 0 0 1px rgba(79, 160, 160, 0.12);
}

.mobile-panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
  color: #1b444b;
  font-size: 13px;
  font-weight: 700;
}

.mobile-panel-head small {
  color: #66868b;
  font-size: 11px;
  font-weight: 600;
}

.mobile-panel-head > strong {
  flex: 1 1 auto;
  min-width: 0;
  max-width: 100%;
  overflow-wrap: anywhere;
  word-break: break-all;
  font-size: 11px;
  line-height: 1.4;
  text-align: right;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  color: #19535c;
}

.mobile-panel-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 12px;
}

.mobile-panel-model-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 16px;
  background: rgba(236, 247, 246, 0.82);
}

.mobile-panel-model-copy {
  display: grid;
  gap: 2px;
}

.mobile-panel-model-copy span {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #66868b;
}

.mobile-panel-model-copy strong {
  font-size: 14px;
  color: #1a5760;
}

.mobile-panel-metric {
  padding: 10px 12px;
  border-radius: 16px;
  background: rgba(236, 247, 246, 0.78);
}

.mobile-panel-metric span {
  display: block;
  margin-bottom: 4px;
  font-size: 11px;
  color: #5d8085;
}

.mobile-panel-metric strong {
  font-size: 15px;
  color: #1a5760;
}

.mobile-panel-actions,
.mobile-panel-prompts {
  display: grid;
  gap: 8px;
}

.mobile-panel-actions {
  grid-template-columns: 1fr 1fr;
}

.mobile-panel-primary,
.mobile-panel-secondary,
.mobile-panel-prompt {
  min-height: 40px;
  border: none;
  border-radius: 14px;
  font: inherit;
}

.mobile-panel-primary {
  background: linear-gradient(135deg, #2e888c 0%, #57ada3 100%);
  color: #ffffff;
  font-weight: 700;
}

.mobile-panel-secondary {
  background: rgba(227, 244, 242, 0.82);
  color: #1f666e;
  box-shadow: inset 0 0 0 1px rgba(46, 136, 140, 0.14);
  font-weight: 700;
}

.mobile-panel-prompt {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  gap: 4px;
  padding: 10px 12px;
  background: rgba(246, 251, 251, 0.94);
  color: #1a4850;
  text-align: left;
  box-shadow: inset 0 0 0 1px rgba(79, 160, 160, 0.12);
}

.mobile-panel-prompt strong {
  font-size: 12px;
}

.mobile-panel-prompt span {
  font-size: 11px;
  color: #628086;
}

.message-role {
  display: inline-block;
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #6d8a90;
}

.message-user .message-role {
  color: rgba(255, 255, 255, 0.76);
}

.message-content {
  font-size: 15px;
  line-height: 1.8;
  color: #23474d;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.message-citation-inline {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
}

.message-citation-inline-label {
  font-size: 11px;
  font-weight: 700;
  color: #6b8c91;
}

.message-citation-chip {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 0 9px;
  border-radius: 999px;
  background: rgba(239, 248, 247, 0.9);
  border: 1px solid rgba(58, 132, 134, 0.12);
  color: #416870;
  font-size: 11px;
  font-weight: 700;
  line-height: 1.2;
  cursor: pointer;
  transition: transform 0.2s ease, border-color 0.2s ease, background 0.2s ease;
}

.message-citation-chip:hover {
  transform: translateY(-1px);
  border-color: rgba(58, 132, 134, 0.22);
  background: rgba(220, 241, 238, 0.96);
}

.message-citation-popover {
  display: grid;
  gap: 8px;
}

.message-citation-popover-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.message-citation-popover strong {
  display: block;
  color: #1d444b;
  font-size: 14px;
  line-height: 1.45;
}

.message-citation-type {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 9px;
  border-radius: 999px;
  background: rgba(226, 242, 239, 0.96);
  border: 1px solid rgba(58, 132, 134, 0.14);
  color: #2d6971;
  font-size: 11px;
  font-weight: 700;
}

.message-citation-popover small {
  display: block;
  color: #5d7d82;
  line-height: 1.5;
}

.message-citation-popover ul {
  margin: 0;
  padding-left: 18px;
  color: #355d63;
  font-size: 12px;
  line-height: 1.6;
}

.message-citation-popover li + li {
  margin-top: 6px;
}

.message-citation-link {
  margin-top: 10px;
  padding: 0;
  border: none;
  background: transparent;
  color: #2b7a84;
  font: inherit;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.message-trace-inline {
  margin-top: 10px;
}

.message-trace-chip {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid rgba(124, 158, 201, 0.22);
  background: rgba(241, 246, 255, 0.94);
  color: #466985;
  font: inherit;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition: transform 0.2s ease, border-color 0.2s ease, background 0.2s ease;
}

.message-trace-chip:hover {
  transform: translateY(-1px);
  border-color: rgba(124, 158, 201, 0.34);
  background: rgba(232, 241, 255, 0.98);
}

.message-trace-popover {
  display: grid;
  gap: 10px;
}

.message-trace-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.message-trace-head strong {
  color: #1d444b;
}

.message-trace-head span {
  color: #58727e;
  font-size: 12px;
}

.message-trace-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  color: #678189;
  line-height: 1.5;
}

.message-trace-stage-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 8px;
}

.message-trace-stage-list li {
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(246, 250, 253, 0.98);
}

.message-trace-stage-list li.skipped {
  background: rgba(245, 246, 247, 0.98);
}

.message-trace-stage-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #275762;
}

.message-trace-stage-list p {
  margin: 6px 0 0;
  color: #678089;
  font-size: 12px;
  line-height: 1.5;
}

.message-content :deep(*) {
  max-width: 100%;
}

.message-content :deep(p),
.message-content :deep(ul),
.message-content :deep(ol),
.message-content :deep(pre),
.message-content :deep(blockquote),
.message-content :deep(table),
.message-content :deep(.table-scroll),
.message-content :deep(h1),
.message-content :deep(h2),
.message-content :deep(h3),
.message-content :deep(h4),
.message-content :deep(.medical-callout) {
  margin: 0 0 12px;
}

.message-content :deep(p:last-child),
.message-content :deep(ul:last-child),
.message-content :deep(ol:last-child),
.message-content :deep(pre:last-child),
.message-content :deep(blockquote:last-child),
.message-content :deep(table:last-child),
.message-content :deep(.medical-callout:last-child),
.message-content :deep(.table-scroll:last-child) {
  margin-bottom: 0;
}

.message-content :deep(h1),
.message-content :deep(h2),
.message-content :deep(h3),
.message-content :deep(h4) {
  line-height: 1.35;
  color: #173a40;
  letter-spacing: 0.01em;
}

.message-content :deep(h1) {
  font-size: 22px;
}

.message-content :deep(h2) {
  font-size: 19px;
}

.message-content :deep(h3) {
  font-size: 17px;
}

.message-content :deep(ul),
.message-content :deep(ol) {
  padding-left: 20px;
}

.message-content :deep(li + li) {
  margin-top: 6px;
}

.message-content :deep(blockquote) {
  border-left: 3px solid rgba(46, 136, 140, 0.26);
  padding-left: 12px;
  color: #55757b;
}

.message-content :deep(pre) {
  overflow-x: auto;
  border-radius: 14px;
  padding: 12px 14px;
  background: #eff6f6;
  box-shadow: inset 0 0 0 1px rgba(73, 124, 128, 0.08);
}

.message-content :deep(code) {
  border-radius: 8px;
  padding: 2px 6px;
  font-size: 0.92em;
  background: rgba(37, 97, 105, 0.08);
}

.message-content :deep(pre code) {
  padding: 0;
  background: transparent;
}

.message-content :deep(a) {
  color: #1f7a81;
  text-decoration: underline;
}

.message-content :deep(.table-scroll) {
  overflow-x: auto;
}

.message-content :deep(table) {
  min-width: 100%;
  border-collapse: collapse;
  border-radius: 12px;
  box-shadow: inset 0 0 0 1px rgba(75, 125, 129, 0.12);
}

.message-content :deep(th),
.message-content :deep(td) {
  padding: 8px 10px;
  border: 1px solid rgba(75, 125, 129, 0.12);
  text-align: left;
}

.message-content :deep(th) {
  background: rgba(227, 244, 242, 0.72);
}

.message-content :deep(strong) {
  color: #15393f;
}

.message-content :deep(.medical-callout) {
  border-radius: 16px;
  padding: 12px 14px 14px;
  box-shadow: inset 0 0 0 1px rgba(80, 132, 136, 0.12);
}

.message-content :deep(.medical-callout-header) {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.message-content :deep(.medical-callout-icon) {
  position: relative;
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: inset 0 0 0 1px rgba(72, 120, 124, 0.12);
}

.message-content :deep(.medical-callout-icon::before) {
  content: '';
  position: absolute;
  inset: 6px;
  background-repeat: no-repeat;
  background-position: center;
  background-size: contain;
}

.message-content :deep(.medical-callout-label) {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.message-content :deep(.medical-callout-body) {
  font-size: inherit;
  line-height: inherit;
}

.message-content :deep(.medical-callout-body > *:last-child) {
  margin-bottom: 0;
}

.message-content :deep(.medical-callout p) {
  margin: 0;
}

.message-content :deep(.medical-callout-warning) {
  background: linear-gradient(180deg, rgba(255, 246, 231, 0.96) 0%, rgba(255, 251, 241, 0.92) 100%);
  box-shadow: inset 0 0 0 1px rgba(214, 157, 61, 0.18);
}

.message-content :deep(.medical-callout-warning .medical-callout-label) {
  color: #8c5d10;
}

.message-content :deep(.medical-callout-warning .medical-callout-icon::before) {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%23b97712' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M12 3 2.8 19a1.2 1.2 0 0 0 1 2h16.4a1.2 1.2 0 0 0 1-2L12 3Z'/%3E%3Cpath d='M12 9v5'/%3E%3Cpath d='M12 17h.01'/%3E%3C/svg%3E");
}

.message-content :deep(.medical-callout-advice) {
  background: linear-gradient(180deg, rgba(231, 247, 242, 0.96) 0%, rgba(242, 251, 248, 0.92) 100%);
}

.message-content :deep(.medical-callout-advice .medical-callout-label) {
  color: #1f6b5f;
}

.message-content :deep(.medical-callout-advice .medical-callout-icon::before) {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%23217367' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='m5 12 4 4L19 6'/%3E%3C/svg%3E");
}

.message-content :deep(.medical-callout-check) {
  background: linear-gradient(180deg, rgba(236, 244, 255, 0.96) 0%, rgba(245, 249, 255, 0.92) 100%);
  box-shadow: inset 0 0 0 1px rgba(86, 123, 191, 0.14);
}

.message-content :deep(.medical-callout-check .medical-callout-label) {
  color: #315d9a;
}

.message-content :deep(.medical-callout-check .medical-callout-icon::before) {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%23315d9a' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Crect x='4' y='3' width='16' height='18' rx='2'/%3E%3Cpath d='M8 7h8'/%3E%3Cpath d='M8 11h8'/%3E%3Cpath d='M8 15h5'/%3E%3C/svg%3E");
}

.message-content :deep(.medical-callout-info) {
  background: linear-gradient(180deg, rgba(233, 244, 249, 0.96) 0%, rgba(244, 250, 252, 0.92) 100%);
}

.message-content :deep(.medical-callout-info .medical-callout-label) {
  color: #2c6b78;
}

.message-content :deep(.medical-callout-info .medical-callout-icon::before) {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%232c6b78' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Ccircle cx='12' cy='12' r='9'/%3E%3Cpath d='M12 10v6'/%3E%3Cpath d='M12 7h.01'/%3E%3C/svg%3E");
}

.message-user .message-content {
  color: #ffffff;
}

.message-user .message-content :deep(h1),
.message-user .message-content :deep(h2),
.message-user .message-content :deep(h3),
.message-user .message-content :deep(h4),
.message-user .message-content :deep(a),
.message-user .message-content :deep(blockquote) {
  color: #ffffff;
}

.message-user .message-content :deep(pre) {
  background: rgba(255, 255, 255, 0.12);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.12);
}

.message-user .message-content :deep(code) {
  background: rgba(255, 255, 255, 0.14);
}

.message-user .message-content :deep(th) {
  background: rgba(255, 255, 255, 0.12);
}

.message-user .message-content :deep(.medical-callout) {
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.14);
}

.message-user .message-content :deep(.medical-callout-warning),
.message-user .message-content :deep(.medical-callout-advice),
.message-user .message-content :deep(.medical-callout-check),
.message-user .message-content :deep(.medical-callout-info) {
  background: rgba(255, 255, 255, 0.1);
}

.message-user .message-content :deep(.medical-callout-label) {
  color: #ffffff;
}

.message-user .message-content :deep(.medical-callout-icon) {
  background: rgba(255, 255, 255, 0.14);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.14);
}

.message-user .message-content :deep(.medical-callout-warning .medical-callout-icon::before),
.message-user .message-content :deep(.medical-callout-advice .medical-callout-icon::before),
.message-user .message-content :deep(.medical-callout-check .medical-callout-icon::before),
.message-user .message-content :deep(.medical-callout-info .medical-callout-icon::before) {
  filter: brightness(0) invert(1);
}

.loading-dots {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  margin-top: 10px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: currentColor;
  animation: pulse 1.2s infinite ease-in-out both;
}

.dot:nth-child(2) {
  animation-delay: -0.4s;
}

.dot:nth-child(3) {
  animation-delay: -0.8s;
}

.composer {
  flex-shrink: 0;
  padding: 9px 14px 10px;
  border-top: 1px solid rgba(70, 120, 126, 0.08);
  background: linear-gradient(180deg, rgba(248, 252, 252, 0.95) 0%, rgba(241, 248, 248, 0.85) 100%);
}

.composer-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.composer-body-drag-active {
  border-radius: 20px;
  background: rgba(230, 246, 244, 0.92);
  box-shadow: inset 0 0 0 2px rgba(46, 136, 140, 0.24);
}

.model-mode-chip {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-height: 36px;
  padding: 0 12px;
  border: 1px solid rgba(46, 136, 140, 0.16);
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96) 0%, rgba(236, 247, 246, 0.9) 100%);
  box-shadow: 0 12px 22px rgba(29, 83, 92, 0.08);
  color: #19454d;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.model-mode-chip-local {
  border-color: rgba(64, 133, 151, 0.22);
}

.model-mode-chip-fast {
  border-color: rgba(41, 140, 145, 0.22);
}

.model-mode-chip-deep {
  border-color: rgba(58, 117, 162, 0.22);
}

.model-mode-chip:hover {
  transform: translateY(-1px);
  box-shadow: 0 14px 24px rgba(29, 83, 92, 0.12);
}

.model-mode-chip-label {
  font-size: 10px;
  font-weight: 700;
  color: #6b8a8f;
}

.model-mode-chip-icon,
.model-mode-option-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: #1d5e66;
}

.model-mode-chip-icon {
  width: 18px;
  height: 18px;
}

.model-logo-shell {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  flex-shrink: 0;
}

.model-logo-shell-local {
  color: #236474;
}

.model-logo-shell-fast {
  color: #1d7c70;
}

.model-logo-shell-deep {
  color: #2d6595;
}

.model-logo-svg {
  width: 17px;
  height: 17px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.9;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.brand-model-banner .model-logo-shell,
.mobile-panel-model-banner .model-logo-shell {
  width: 20px;
  height: 20px;
}

.brand-model-banner .model-logo-svg,
.mobile-panel-model-banner .model-logo-svg {
  width: 19px;
  height: 19px;
}

.model-mode-chip strong {
  font-size: 11px;
  font-weight: 700;
  color: #184047;
  white-space: nowrap;
}

.model-mode-chip-caret {
  font-size: 11px;
  color: #64848a;
}

.model-mode-menu {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.model-mode-option {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
  padding: 12px 14px;
  border: 1px solid rgba(46, 136, 140, 0.08);
  border-radius: 16px;
  background: rgba(249, 252, 252, 0.96);
  text-align: left;
  color: #16363c;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease, background 0.2s ease;
}

.model-mode-option:hover {
  transform: translateY(-1px);
  border-color: rgba(46, 136, 140, 0.18);
  box-shadow: 0 14px 24px rgba(29, 83, 92, 0.08);
}

.model-mode-option.active {
  border-color: rgba(46, 136, 140, 0.24);
  background: rgba(238, 247, 246, 0.96);
}

.model-mode-option-local.active {
  border-color: rgba(64, 133, 151, 0.2);
  background: linear-gradient(180deg, rgba(238, 247, 249, 0.98) 0%, rgba(244, 250, 250, 0.94) 100%);
}

.model-mode-option-fast.active {
  border-color: rgba(30, 133, 120, 0.2);
  background: linear-gradient(180deg, rgba(236, 248, 243, 0.98) 0%, rgba(244, 251, 248, 0.94) 100%);
}

.model-mode-option-deep.active {
  border-color: rgba(58, 117, 162, 0.2);
  background: linear-gradient(180deg, rgba(238, 244, 252, 0.98) 0%, rgba(246, 250, 255, 0.94) 100%);
}

.model-mode-option-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.model-mode-option-title-group {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.model-mode-option-main strong {
  font-size: 15px;
  font-weight: 800;
  color: #183f45;
}

.model-mode-option-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  min-height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(108, 189, 180, 0.16);
  color: #276a72;
  font-size: 11px;
  font-weight: 700;
}

.model-mode-option-subtitle {
  font-size: 12px;
  line-height: 1.5;
  color: #6a878d;
}

:deep(.model-mode-popover.el-popper) {
  border-radius: 20px;
  border: 1px solid rgba(46, 136, 140, 0.12);
  box-shadow: 0 18px 36px rgba(29, 83, 92, 0.12);
  padding: 10px;
}

.chat-attachment-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 10px 0 0;
}

.chat-attachment-chip {
  display: flex;
  align-items: center;
  gap: 12px;
  width: min(100%, 260px);
  border: 1px solid rgba(79, 160, 160, 0.16);
  border-radius: 18px;
  padding: 10px 12px;
  background: rgba(236, 246, 245, 0.92);
  color: #1d5962;
  box-shadow: 0 12px 24px rgba(27, 75, 84, 0.08);
}

.chat-attachment-chip-image {
  background: rgba(232, 240, 255, 0.92);
  border-color: rgba(89, 127, 194, 0.18);
}

.chat-attachment-file-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 56px;
  height: 56px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.78);
  color: #1d5962;
  font-size: 12px;
  font-weight: 700;
}

.chat-attachment-meta {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 4px;
}

.chat-attachment-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  font-weight: 700;
  color: #17474f;
}

.chat-attachment-size {
  font-size: 11px;
  color: #6a878d;
}

.chat-attachment-remove {
  flex-shrink: 0;
  padding: 0;
  border: none;
  background: transparent;
  font-size: 11px;
  color: #6d8a90;
  font-weight: 700;
  cursor: pointer;
}

.chat-attachment-remove:hover {
  color: #1d5962;
}

.chat-attachment-list-mobile .chat-attachment-chip {
  width: 100%;
}

.composer-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
}

.composer-input :deep(.el-textarea__inner) {
  border-radius: 18px;
  border-color: rgba(46, 136, 140, 0.16);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.97) 0%, rgba(248, 252, 252, 0.95) 100%);
  box-shadow: 0 12px 22px rgba(29, 83, 92, 0.08);
  padding: 12px 14px;
  font-size: 14px;
  line-height: 1.5;
  -webkit-appearance: none;
}

.mobile-composer-textarea {
  width: 100%;
  min-height: 42px;
  max-height: 120px;
  resize: none;
  border: 1px solid rgba(67, 126, 133, 0.12);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 16px 32px rgba(32, 80, 88, 0.06);
  padding: 10px 12px;
  color: #173a40;
  font: inherit;
  font-size: 14px;
  line-height: 1.45;
  outline: none;
  overflow-y: auto;
  -webkit-appearance: none;
  -webkit-user-select: text;
  user-select: text;
}

.mobile-composer-textarea::placeholder {
  color: #7b979b;
}

.composer-toolbar {
  justify-content: flex-start;
  gap: 10px;
}

.composer-toolbar-left {
  min-width: 0;
  gap: 8px;
}

.composer-toolbar-right {
  flex-shrink: 0;
}

.composer-attach-button,
.composer-send-button {
  min-height: 32px;
  border-radius: 999px;
  padding-inline: 12px;
  white-space: nowrap;
}

.composer-attach-button {
  flex-shrink: 0;
}

.composer-send-button {
  min-width: 66px;
}

.composer-icon-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  padding: 0;
  border: 1px solid rgba(46, 136, 140, 0.16);
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96) 0%, rgba(236, 247, 246, 0.9) 100%);
  box-shadow: 0 12px 22px rgba(29, 83, 92, 0.08);
  color: #1d5e66;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease, opacity 0.2s ease;
}

.composer-icon-button:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 14px 24px rgba(29, 83, 92, 0.12);
}

.composer-icon-button:disabled {
  opacity: 0.52;
  cursor: not-allowed;
}

.composer-icon-svg {
  width: 18px;
  height: 18px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.9;
  stroke-linecap: round;
  stroke-linejoin: round;
}

@keyframes pulse {
  0%,
  100% {
    transform: scale(0.68);
    opacity: 0.45;
  }

  50% {
    transform: scale(1);
    opacity: 1;
  }
}

@media (max-width: 1080px) {
  .page-shell {
    grid-template-columns: 1fr;
    padding: 16px;
    height: auto;
    min-height: 100dvh;
    overflow-x: hidden;
    overflow-y: auto;
    -webkit-overflow-scrolling: touch;
  }

  .hero-panel {
    align-items: stretch;
    flex-direction: column;
  }

  .hero-bar {
    width: 100%;
    flex-direction: column;
    align-items: flex-start;
  }

  .hero-identity {
    width: 100%;
    justify-content: space-between;
  }

  .hero-assistant-title {
    position: static;
    transform: none;
  }

  .hero-inline-actions {
    width: 100%;
    justify-content: flex-end;
  }

  .brand-panel,
  .workspace {
    overflow: visible;
  }

  .message-list {
    overscroll-behavior: auto;
  }
}

@media (max-width: 768px) {
  .page-shell {
    gap: 10px;
    padding: 10px;
  }

  .page-shell.mobile-viewport {
    height: auto;
    overflow-y: auto;
    overscroll-behavior-y: contain;
  }

  .brand-card,
  .control-card,
  .suggestion-card,
  .hero-panel,
  .chat-panel {
    border-radius: 22px;
    backdrop-filter: none;
  }

  .brand-lockup {
    align-items: flex-start;
  }

  .brand-logo {
    width: 68px;
    height: 68px;
    border-radius: 20px;
  }

  .brand-title {
    font-size: 22px;
  }

  .brand-panel {
    display: none;
  }

  .workspace {
    gap: 0;
    overflow: visible;
  }

  .suggestion-card {
    display: none;
  }

  .brand-card,
  .control-card,
  .suggestion-card {
    padding: 12px;
  }

  .hero-panel {
    padding: 5px 9px;
  }

  .hero-bar {
    gap: 4px;
    align-items: center;
  }

  .hero-identity strong {
    font-size: 11px;
    white-space: normal;
  }

  .hero-assistant-title {
    font-size: 10px;
  }

  .hero-kicker {
    margin-bottom: 1px;
    font-size: 9px;
  }

  .mobile-panel-trigger {
    display: inline-flex;
    margin-left: auto;
  }

  .prompt-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .chat-header {
    padding: 12px 12px 10px;
  }

  .chat-header-mobile {
    padding: 6px 8px 0;
  }

  .chat-header h3 {
    font-size: 17px;
  }

  .chat-header-actions {
    align-items: center;
    justify-content: flex-end;
    flex-wrap: nowrap;
  }

  .status-pill,
  .header-export-button {
    width: auto;
  }

  .message-list {
    min-height: 42dvh;
    max-height: none;
    padding: 12px;
    gap: 14px;
    padding-bottom: calc(14px + env(safe-area-inset-bottom, 0px));
  }

  .scroll-bottom-button {
    right: 14px;
    bottom: 116px;
  }

  .message-body {
    max-width: 100%;
    padding: 12px 14px;
  }

  .message-attachment-card {
    max-width: min(180px, 100%);
  }

  .message-attachment-image,
  .chat-attachment-preview {
    width: 88px;
    height: 88px;
    border-radius: 14px;
  }

  .chat-attachment-chip {
    width: 100%;
    border-radius: 16px;
    padding: 9px 10px;
  }

  .chat-attachment-file-icon {
    width: 48px;
    height: 48px;
    border-radius: 14px;
    font-size: 11px;
  }

  .message-avatar {
    width: 36px;
    height: 36px;
    border-radius: 14px;
  }

  .prompt-grid {
    grid-template-columns: 1fr 1fr;
  }

  .composer {
    position: sticky;
    bottom: 0;
    z-index: 3;
    margin: 0 -1px -1px;
    padding: 8px 8px calc(8px + env(safe-area-inset-bottom, 0px) + min(var(--chat-keyboard-offset, 0px), 14px));
    border-top: 1px solid rgba(70, 120, 126, 0.1);
    border-radius: 18px 18px 22px 22px;
    background: linear-gradient(180deg, rgba(248, 252, 252, 0.98) 0%, rgba(240, 248, 247, 0.96) 100%);
    box-shadow: 0 -14px 28px rgba(23, 71, 80, 0.08);
  }

  .page-shell.wechat-mobile .chat-panel,
  .page-shell.wechat-mobile .message-list {
    overflow: visible;
  }

  .page-shell.wechat-mobile .message-list {
    min-height: 36dvh;
    padding-bottom: 10px;
  }

  .page-shell.wechat-mobile.keyboard-open .composer {
    position: relative;
    bottom: auto;
    margin: 0;
    border-radius: 18px;
    box-shadow: none;
  }

  .session-expired-footer {
    flex-direction: column;
  }

  .session-expired-download,
  .session-expired-confirm {
    width: 100%;
  }

  .composer-row {
    grid-template-columns: 1fr;
  }

  .control-actions,
  .knowledge-mini-grid,
  .prompt-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 560px) {
  .page-shell {
    padding: 8px;
    gap: 8px;
  }

  .brand-card,
  .control-card,
  .suggestion-card,
  .hero-panel,
  .chat-panel {
    border-radius: 18px;
  }

  .brand-logo {
    width: 56px;
    height: 56px;
    border-radius: 16px;
  }

  .brand-title {
    font-size: 15px;
  }

  .brand-eyebrow {
    letter-spacing: 0.1em;
  }

  .brand-model-banner {
    padding: 9px 10px;
  }

  .brand-model-copy strong {
    font-size: 12px;
  }

  .session-badge {
    font-size: 9px;
    padding: 7px 9px;
  }

  .brand-subtitle,
  .knowledge-summary,
  .prompt-description {
    font-size: 12px;
  }

  .hero-panel {
    gap: 8px;
    padding: 5px 7px;
  }

  .mobile-panel-trigger {
    min-height: 30px;
    padding-inline: 10px;
    font-size: 10px;
  }

  .chat-header {
    padding: 10px 10px 8px;
  }

  .chat-header-mobile {
    padding: 6px 7px 0;
  }

  .chat-header h3 {
    font-size: 16px;
  }

  .header-export-button,
  .status-pill {
    min-height: 34px;
    font-size: 11px;
  }

  .model-mode-chip {
    min-height: 40px;
    padding: 6px 12px 6px 9px;
  }

  .model-mode-chip strong {
    font-size: 11px;
  }

  .message-list {
    min-height: 40dvh;
    padding: 10px;
    padding-bottom: calc(12px + env(safe-area-inset-bottom, 0px));
  }

  .message {
    gap: 10px;
  }

  .message-body {
    border-radius: 16px;
    padding: 11px 12px;
  }

  .message-attachment-gallery {
    gap: 8px;
    margin-bottom: 8px;
  }

  .message-attachment-card {
    max-width: min(150px, 100%);
  }

  .message-attachment-image,
  .chat-attachment-preview {
    width: 72px;
    height: 72px;
    border-radius: 12px;
  }

  .message-attachment-caption,
  .message-attachment-file {
    font-size: 11px;
  }

  .message-content {
    font-size: 14px;
    line-height: 1.7;
  }

  .message-content :deep(h1) {
    font-size: 20px;
  }

  .message-content :deep(h2) {
    font-size: 17px;
  }

  .message-content :deep(h3) {
    font-size: 15px;
  }

  .composer {
    padding: 8px 8px calc(8px + env(safe-area-inset-bottom, 0px) + min(var(--chat-keyboard-offset, 0px), 14px));
    border-radius: 16px 16px 18px 18px;
  }

  .model-switch-button {
    padding: 8px 10px;
  }

  .composer-row {
    margin: 8px 0 4px;
    gap: 8px;
  }

  .composer-input :deep(.el-textarea__inner) {
    min-height: 42px;
    padding: 10px 12px;
    font-size: 13px;
  }

  .mobile-composer-textarea {
    min-height: 40px;
    padding: 9px 11px;
    font-size: 13px;
  }

  .composer-toolbar {
    justify-content: space-between;
    gap: 6px;
  }

  .composer-toolbar-left {
    gap: 6px;
  }

  .composer-toolbar .mobile-attach-button,
  .composer-toolbar :deep(.composer-send-button) {
    min-width: 54px;
    min-height: 32px;
    padding-inline: 10px;
    font-size: 12px;
  }

  .chat-attachment-chip {
    gap: 10px;
    padding: 8px 9px;
  }

  .chat-attachment-name {
    font-size: 11px;
  }

  .chat-attachment-size,
  .chat-attachment-remove {
    font-size: 10px;
  }

  .composer {
    padding: 7px 7px calc(7px + env(safe-area-inset-bottom, 0px));
  }

  .composer-row {
    margin: 7px 0 4px;
    gap: 6px;
  }

  .composer-input :deep(.el-textarea__inner) {
    min-height: 40px;
    padding: 9px 11px;
  }

  .mobile-composer-textarea {
    min-height: 38px;
    padding: 8px 10px;
  }

  .composer-toolbar {
    gap: 6px;
  }

  .composer-toolbar-left {
    gap: 6px;
  }

  .ghost-action,
  .send-action,
  .primary-action,
  .secondary-action {
    min-height: 36px;
  }

  .header-export-button {
    padding-inline: 10px;
  }

  .chat-header-actions {
    gap: 6px;
  }

  .model-switch-button small {
    display: none;
  }

  .status-pill {
    padding-inline: 10px;
  }

  .scroll-bottom-button {
    right: 12px;
    bottom: 116px;
    padding: 9px 12px;
    font-size: 12px;
  }

  .prompt-chip {
    min-height: 78px;
    padding: 10px;
  }

  .mobile-panel-card {
    padding: 12px;
    border-radius: 18px;
  }

  .mobile-panel-actions,
  .mobile-panel-grid {
    grid-template-columns: 1fr;
  }

  .page-shell.wechat-mobile .message-list {
    min-height: 34dvh;
  }
}
</style>
