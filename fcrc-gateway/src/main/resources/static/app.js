/* global window, document, fetch, TextDecoder */

/**
 * FCRC 合规驾驶舱前端。
 *
 * 设计原则：
 * 1. 零构建工具：直接用浏览器原生 ES + fetch，降低演示门槛；
 * 2. 所有数据来自网关 /api/**，不硬编码；
 * 3. AI 对话优先走 SSE，失败时自动降级为同步接口；
 * 4. 人工复核只能由这里（真人）触发，AI 没有对应工具。
 */

const state = {
    view: 'overview',
    reviewRunNo: null,
    streaming: false,
    planMode: false,
    session: 'main',
    userId: 'demo-user',
};

const TITLES = {
    overview: ['合规驾驶舱', '实时掌握客户、风险、合规与工作流状态'],
    customers: ['客户与 KYC', '客户画像、PEP 标识与开户状态'],
    workflows: ['开户工作流', 'Spring AI Alibaba Graph 编排的联合审批流程'],
    cases: ['合规案件', '人工复核产生的案件与 SLA 跟踪'],
    reports: ['监管报告', '客户风险报告、SAR、CTR 与周期性报告'],
    copilot: ['AI Copilot', 'AgentScope 驱动的合规风控助手'],
};

/* ------------------------------------------------------------------ */
/* 基础工具                                                             */
/* ------------------------------------------------------------------ */

async function api(path, options = {}) {
    const res = await fetch(path, {
        headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
        ...options,
    });
    if (!res.ok) {
        throw new Error(`HTTP ${res.status} ${res.statusText}`);
    }
    const body = await res.json();
    if (body && body.success === false) {
        throw new Error(body.message || `业务失败 ${body.code || ''}`);
    }
    return body ? body.data : null;
}

function escapeHtml(value) {
    if (value === null || value === undefined) return '';
    return String(value)
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');
}

function tag(value, kind) {
    return `<span class="tag ${kind || ''}">${escapeHtml(value || '–')}</span>`;
}

function statusTag(status) {
    const map = {
        COMPLETED: 'ok', APPROVED: 'ok', PASS: 'ok', RESOLVED: 'ok', SUBMITTED: 'ok', CLOSED: '',
        MANUAL_REVIEW: 'warn', PENDING_INFO: 'warn', PENDING: 'warn', RUNNING: 'info',
        IN_PROGRESS: 'info', DRAFT: 'info', OPEN: 'info', NOT_STARTED: '',
        REJECTED: 'danger', FAILED: 'danger', HIGH: 'warn', CRITICAL: 'danger',
        MEDIUM: 'warn', LOW: 'ok', FINAL: 'info',
    };
    return tag(status, map[status] || '');
}

function riskTag(score, level) {
    if (score === null || score === undefined) return tag('–');
    const cls = score >= 70 ? 'danger' : score >= 40 ? 'warn' : 'ok';
    return tag(`${score} ${level || ''}`.trim(), cls);
}

function fmtTime(value) {
    if (!value) return '–';
    const d = new Date(value);
    return Number.isNaN(d.getTime()) ? value : d.toLocaleString('zh-CN', { hour12: false });
}

function showToast(message, kind = 'info') {
    const el = document.createElement('div');
    el.textContent = message;
    el.style.cssText = `
        position:fixed;right:24px;bottom:24px;z-index:99;padding:12px 18px;border-radius:10px;
        background:${kind === 'error' ? 'rgba(248,113,113,.18)' : 'rgba(45,212,191,.16)'};
        border:1px solid ${kind === 'error' ? 'rgba(248,113,113,.5)' : 'rgba(45,212,191,.5)'};
        color:#e6eefb;font-size:13px;box-shadow:0 12px 30px rgba(2,8,20,.5);`;
    document.body.appendChild(el);
    setTimeout(() => el.remove(), 3200);
}

/* ------------------------------------------------------------------ */
/* 导航                                                                 */
/* ------------------------------------------------------------------ */

function switchView(view) {
    state.view = view;
    document.querySelectorAll('.nav-item').forEach((btn) => {
        btn.classList.toggle('active', btn.dataset.view === view);
    });
    document.querySelectorAll('.view').forEach((section) => {
        section.classList.toggle('active', section.id === `view-${view}`);
    });
    const [title, subtitle] = TITLES[view] || ['FCRC', ''];
    document.getElementById('viewTitle').textContent = title;
    document.getElementById('viewSubtitle').textContent = subtitle;

    if (view === 'overview') loadOverview();
    if (view === 'customers') loadCustomers();
    if (view === 'workflows') loadWorkflows();
    if (view === 'cases') loadCases();
    if (view === 'reports') loadReports();
    if (view === 'copilot') applySession();
}

document.getElementById('nav').addEventListener('click', (event) => {
    const btn = event.target.closest('.nav-item');
    if (btn) switchView(btn.dataset.view);
});

document.addEventListener('click', (event) => {
    const jump = event.target.closest('[data-jump]');
    if (jump) switchView(jump.dataset.jump);
});

/* ------------------------------------------------------------------ */
/* 驾驶舱                                                               */
/* ------------------------------------------------------------------ */

async function loadOverview() {
    try {
        const [customers, workflows, cases, reports] = await Promise.allSettled([
            api('/api/customers/stats'),
            api('/api/workflows/stats'),
            api('/api/cases/stats'),
            api('/api/reports/stats'),
        ]);

        if (customers.status === 'fulfilled' && customers.value) {
            document.getElementById('statCustomers').textContent = customers.value.total ?? '–';
            document.getElementById('statPep').textContent = `PEP ${customers.value.pep ?? '–'}`;
        }
        if (workflows.status === 'fulfilled' && workflows.value) {
            document.getElementById('statReview').textContent = workflows.value.waitingReview ?? '–';
        }
        if (cases.status === 'fulfilled' && cases.value) {
            document.getElementById('statCases').textContent = cases.value.inProgress ?? '–';
            document.getElementById('statOverdue').textContent = `逾期 ${cases.value.overdue ?? '–'}`;
        }
        if (reports.status === 'fulfilled' && reports.value) {
            document.getElementById('statReports').textContent = reports.value.total ?? '–';
            document.getElementById('statSubmitted').textContent = `已报送 ${reports.value.submitted ?? '–'}`;
        }

        const page = await api('/api/workflows?page=0&size=6');
        renderRecentWorkflows(page.items || []);
    } catch (error) {
        showToast(`驾驶舱加载失败：${error.message}`, 'error');
    }

    loadCapabilities();
    checkHealth();
}

function renderRecentWorkflows(items) {
    const tbody = document.getElementById('recentWorkflows');
    if (!items.length) {
        tbody.innerHTML = '<tr><td colspan="5" class="empty">暂无工作流</td></tr>';
        return;
    }
    tbody.innerHTML = items.map((run) => `
        <tr>
            <td><code>${escapeHtml(run.runNo)}</code></td>
            <td>${escapeHtml(run.customerName || run.customerNo || '–')}</td>
            <td>${riskTag(run.riskScore, run.riskLevel)}</td>
            <td>${statusTag(run.complianceResult)}</td>
            <td>${statusTag(run.status)}</td>
        </tr>`).join('');
}

async function loadCapabilities() {
    try {
        const caps = await api('/api/agent/capabilities');
        document.getElementById('modelBadge').textContent = `${caps.modelMode} · ${caps.model}`;
        document.getElementById('agentMode').textContent = caps.modelMode;
        document.getElementById('capModel').textContent = caps.model;
        document.getElementById('capTools').textContent = caps.registeredToolCount;
        document.getElementById('capPlan').textContent = caps.planModeEnabled ? '开启' : '关闭';
        document.getElementById('capMemory').textContent = caps.memoryEnabled ? '开启' : '关闭';
        document.getElementById('capSandbox').textContent = caps.sandboxEnabled ? '开启' : '关闭（本地文件）';
        document.getElementById('capHitl').textContent = caps.humanInTheLoop;
        state.planMode = caps.planModeEnabled;
        updatePlanButton();
    } catch (error) {
        document.getElementById('modelBadge').textContent = 'Agent 不可用';
        document.getElementById('agentMode').textContent = 'offline';
    }
}

async function checkHealth() {
    const el = document.getElementById('health');
    try {
        await api('/api/agent/health');
        el.className = 'health up';
        el.innerHTML = '<i></i>Agent 在线';
    } catch (error) {
        el.className = 'health down';
        el.innerHTML = '<i></i>Agent 离线';
    }
}

/* ------------------------------------------------------------------ */
/* 客户                                                                 */
/* ------------------------------------------------------------------ */

async function loadCustomers() {
    const keyword = document.getElementById('customerKeyword').value.trim();
    const status = document.getElementById('customerStatus').value;
    const params = new URLSearchParams({ page: '0', size: '30' });
    if (keyword) params.set('keyword', keyword);
    if (status) params.set('status', status);

    const tbody = document.getElementById('customerRows');
    tbody.innerHTML = '<tr><td colspan="8" class="empty">加载中…</td></tr>';
    try {
        const page = await api(`/api/customers?${params}`);
        if (!page.items || !page.items.length) {
            tbody.innerHTML = '<tr><td colspan="8" class="empty">没有匹配的客户</td></tr>';
            return;
        }
        tbody.innerHTML = page.items.map((c) => `
            <tr>
                <td><code>${escapeHtml(c.customerNo)}</code></td>
                <td>${escapeHtml(c.name)}</td>
                <td>${escapeHtml(c.customerType)}</td>
                <td>${escapeHtml(c.country)}</td>
                <td>${c.pep ? tag('PEP', 'warn') : tag('否')}</td>
                <td>${statusTag(c.kycStatus)}</td>
                <td>${statusTag(c.onboardingStatus)}</td>
                <td>${statusTag(c.riskLevel)}</td>
            </tr>`).join('');
    } catch (error) {
        tbody.innerHTML = `<tr><td colspan="8" class="empty">加载失败：${escapeHtml(error.message)}</td></tr>`;
    }
}

/* ------------------------------------------------------------------ */
/* 工作流                                                               */
/* ------------------------------------------------------------------ */

async function loadWorkflows() {
    const status = document.getElementById('workflowStatus').value;
    const params = new URLSearchParams({ page: '0', size: '30' });
    if (status) params.set('status', status);

    const tbody = document.getElementById('workflowRows');
    tbody.innerHTML = '<tr><td colspan="7" class="empty">加载中…</td></tr>';
    try {
        const page = await api(`/api/workflows?${params}`);
        if (!page.items || !page.items.length) {
            tbody.innerHTML = '<tr><td colspan="7" class="empty">没有匹配的工作流</td></tr>';
            return;
        }
        tbody.innerHTML = page.items.map((run) => `
            <tr>
                <td><code>${escapeHtml(run.runNo)}</code></td>
                <td>${escapeHtml(run.customerName || run.customerNo || '–')}</td>
                <td>${riskTag(run.riskScore, run.riskLevel)}</td>
                <td>${statusTag(run.complianceResult)}</td>
                <td>${statusTag(run.preliminaryDecision)}</td>
                <td>${statusTag(run.status)}</td>
                <td>${run.status === 'MANUAL_REVIEW'
                    ? `<button class="btn small primary" data-review="${escapeHtml(run.runNo)}">人工复核</button>`
                    : '<span class="muted">—</span>'}</td>
            </tr>`).join('');
    } catch (error) {
        tbody.innerHTML = `<tr><td colspan="7" class="empty">加载失败：${escapeHtml(error.message)}</td></tr>`;
    }
}

document.getElementById('workflowRows').addEventListener('click', (event) => {
    const btn = event.target.closest('[data-review]');
    if (!btn) return;
    state.reviewRunNo = btn.dataset.review;
    document.getElementById('reviewRunLabel').textContent = `运行号：${state.reviewRunNo}`;
    document.getElementById('reviewComment').value = '';
    document.getElementById('reviewModal').classList.add('open');
});

function closeReviewModal() {
    document.getElementById('reviewModal').classList.remove('open');
    state.reviewRunNo = null;
}

document.getElementById('cancelReview').addEventListener('click', closeReviewModal);

async function submitReview(decision) {
    if (!state.reviewRunNo) return;
    const reviewer = document.getElementById('reviewer').value.trim();
    const comment = document.getElementById('reviewComment').value.trim();
    if (!reviewer) {
        showToast('请填写复核人', 'error');
        return;
    }
    try {
        await api(`/api/workflows/${encodeURIComponent(state.reviewRunNo)}/review`, {
            method: 'POST',
            body: JSON.stringify({ decision, reviewer, comment }),
        });
        showToast(decision === 'APPROVED' ? '复核通过，工作流继续执行' : '已拒绝，工作流结束');
        closeReviewModal();
        loadWorkflows();
    } catch (error) {
        showToast(`复核失败：${error.message}`, 'error');
    }
}

document.getElementById('approveBtn').addEventListener('click', () => submitReview('APPROVED'));
document.getElementById('rejectBtn').addEventListener('click', () => submitReview('REJECTED'));

/* ------------------------------------------------------------------ */
/* 案件 / 报告                                                          */
/* ------------------------------------------------------------------ */

async function loadCases() {
    const status = document.getElementById('caseStatus').value;
    const params = new URLSearchParams({ page: '0', size: '30' });
    if (status) params.set('status', status);

    const tbody = document.getElementById('caseRows');
    tbody.innerHTML = '<tr><td colspan="7" class="empty">加载中…</td></tr>';
    try {
        const page = await api(`/api/cases?${params}`);
        if (!page.items || !page.items.length) {
            tbody.innerHTML = '<tr><td colspan="7" class="empty">暂无案件</td></tr>';
            return;
        }
        tbody.innerHTML = page.items.map((c) => `
            <tr>
                <td><code>${escapeHtml(c.caseNo)}</code></td>
                <td>${escapeHtml(c.customerName || c.customerNo)}</td>
                <td>${escapeHtml(c.type)}</td>
                <td>${statusTag(c.priority)}</td>
                <td>${statusTag(c.status)}</td>
                <td>${escapeHtml(c.assignee || '未分配')}</td>
                <td>${c.overdue ? tag('已逾期', 'danger') : fmtTime(c.dueAt)}</td>
            </tr>`).join('');
    } catch (error) {
        tbody.innerHTML = `<tr><td colspan="7" class="empty">加载失败：${escapeHtml(error.message)}</td></tr>`;
    }
}

async function loadReports() {
    const status = document.getElementById('reportStatus').value;
    const params = new URLSearchParams({ page: '0', size: '30' });
    if (status) params.set('status', status);

    const tbody = document.getElementById('reportRows');
    tbody.innerHTML = '<tr><td colspan="7" class="empty">加载中…</td></tr>';
    try {
        const page = await api(`/api/reports?${params}`);
        if (!page.items || !page.items.length) {
            tbody.innerHTML = '<tr><td colspan="7" class="empty">暂无报告</td></tr>';
            return;
        }
        tbody.innerHTML = page.items.map((r) => `
            <tr>
                <td><code>${escapeHtml(r.reportNo)}</code></td>
                <td>${escapeHtml(r.customerNo)}</td>
                <td>${escapeHtml(r.reportType)}</td>
                <td>${escapeHtml(r.period || '–')}</td>
                <td>${statusTag(r.decision)}</td>
                <td>${statusTag(r.status)}</td>
                <td><a class="link" href="/api/reports/${encodeURIComponent(r.reportNo)}/csv">下载 CSV</a></td>
            </tr>`).join('');
    } catch (error) {
        tbody.innerHTML = `<tr><td colspan="7" class="empty">加载失败：${escapeHtml(error.message)}</td></tr>`;
    }
}

/* ------------------------------------------------------------------ */
/* AI Copilot                                                           */
/* ------------------------------------------------------------------ */

const messagesEl = document.getElementById('messages');
const eventLogEl = document.getElementById('eventLog');

function applySession() {
    state.userId = document.getElementById('userId').value.trim() || 'demo-user';
    state.session = document.getElementById('sessionId').value.trim() || 'main';
    document.getElementById('sessionLabel').textContent = `session: ${state.session}`;
}

function appendMessage(role, text) {
    const wrap = document.createElement('div');
    wrap.className = `msg ${role}`;
    wrap.innerHTML = `
        <div class="avatar">${role === 'user' ? 'ME' : 'AI'}</div>
        <div class="bubble"></div>`;
    const bubble = wrap.querySelector('.bubble');
    if (text) bubble.textContent = text;
    messagesEl.appendChild(wrap);
    messagesEl.scrollTop = messagesEl.scrollHeight;
    return bubble;
}

function appendText(bubble, text) {
    bubble.textContent += text;
    messagesEl.scrollTop = messagesEl.scrollHeight;
}

function appendToolChip(bubble, name) {
    const chip = document.createElement('span');
    chip.className = 'tool-chip';
    chip.textContent = `⚙ ${name}`;
    bubble.appendChild(chip);
    messagesEl.scrollTop = messagesEl.scrollHeight;
}

function logEvent(title, detail) {
    if (eventLogEl.querySelector('.muted')) eventLogEl.innerHTML = '';
    const item = document.createElement('div');
    item.className = 'ev';
    item.textContent = detail ? `${title} · ${detail}` : title;
    eventLogEl.prepend(item);
    while (eventLogEl.children.length > 40) eventLogEl.lastChild.remove();
}

function setStreaming(streaming) {
    state.streaming = streaming;
    const btn = document.getElementById('sendBtn');
    btn.disabled = streaming;
    btn.textContent = streaming ? '生成中…' : '发送';
}

async function sendMessage(text) {
    if (!text.trim() || state.streaming) return;
    applySession();

    appendMessage('user', text);
    const bubble = appendMessage('assistant', '');
    bubble.innerHTML = '<span class="typing">正在思考…</span>';
    setStreaming(true);
    logEvent('发送消息', text.slice(0, 40));

    try {
        const ok = await streamChat(text, bubble);
        if (!ok) await syncChat(text, bubble);
    } catch (error) {
        bubble.textContent = `对话失败：${error.message}`;
        logEvent('对话失败', error.message);
    } finally {
        setStreaming(false);
    }
}

async function streamChat(text, bubble) {
    let res;
    try {
        res = await fetch('/api/agent/chat/stream', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ message: text, userId: state.userId, sessionId: state.session }),
        });
    } catch (error) {
        return false;
    }
    if (!res.ok || !res.body) return false;

    bubble.textContent = '';
    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let gotText = false;

    const handleFrame = (frame) => {
        let eventName = 'message';
        const dataLines = [];
        frame.split(/\r?\n/).forEach((line) => {
            if (line.startsWith('event:')) eventName = line.slice(6).trim();
            else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim());
        });
        if (!dataLines.length) return;
        const raw = dataLines.join('\n');

        if (eventName === 'done') {
            logEvent('本轮完成');
            return;
        }
        if (eventName === 'error') {
            const parsed = safeJson(raw);
            logEvent('错误', parsed && parsed.message ? parsed.message : raw);
            appendText(bubble, `\n[错误] ${parsed && parsed.message ? parsed.message : raw}`);
            gotText = true;
            return;
        }

        const ev = safeJson(raw);
        if (!ev || !ev.type) return;
        switch (ev.type) {
            case 'TEXT_BLOCK_DELTA':
                if (ev.delta) {
                    appendText(bubble, ev.delta);
                    gotText = true;
                }
                break;
            case 'TOOL_CALL_START':
                appendToolChip(bubble, ev.toolCallName || 'tool');
                logEvent('工具调用', ev.toolCallName);
                break;
            case 'TOOL_RESULT_END':
                logEvent('工具完成', ev.toolCallName);
                break;
            case 'AGENT_RESULT':
                if (!gotText && ev.result && Array.isArray(ev.result.content)) {
                    const joined = ev.result.content
                        .filter((b) => b && b.type === 'text')
                        .map((b) => b.text || '')
                        .join('');
                    if (joined) {
                        appendText(bubble, joined);
                        gotText = true;
                    }
                }
                break;
            case 'ALL_TOOLS_DENIED':
                logEvent('权限拒绝', '所有工具调用被拒绝');
                break;
            case 'REQUIRE_USER_CONFIRM':
                logEvent('等待确认', '工具请求需要人工确认');
                break;
            case 'EXCEED_MAX_ITERS':
                logEvent('达到最大轮数', '');
                break;
            default:
                break;
        }
    };

    while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        let index;
        while ((index = buffer.indexOf('\n\n')) >= 0) {
            handleFrame(buffer.slice(0, index));
            buffer = buffer.slice(index + 2);
        }
    }
    if (buffer.trim()) handleFrame(buffer);
    if (!gotText) bubble.textContent = '（没有收到文本输出，请查看右侧事件日志）';
    return true;
}

async function syncChat(text, bubble) {
    const reply = await api('/api/agent/chat', {
        method: 'POST',
        body: JSON.stringify({ message: text, userId: state.userId, sessionId: state.session }),
    });
    bubble.textContent = reply.reply || '（空回复）';
    logEvent('同步回复', `${reply.modelMode}`);
}

function safeJson(raw) {
    try {
        return JSON.parse(raw);
    } catch (error) {
        return null;
    }
}

document.getElementById('chatForm').addEventListener('submit', (event) => {
    event.preventDefault();
    const input = document.getElementById('chatInput');
    const text = input.value;
    input.value = '';
    sendMessage(text);
});

document.getElementById('clearChat').addEventListener('click', () => {
    messagesEl.innerHTML = '';
    eventLogEl.innerHTML = '<span class="muted">等待对话…</span>';
    showToast('本地对话已清空；服务端会话仍保留，可调用 clear API');
});

async function updatePlanButton() {
    const btn = document.getElementById('planToggle');
    btn.textContent = state.planMode ? 'Plan Mode: 开' : 'Plan Mode: 关';
    btn.style.borderColor = state.planMode ? 'rgba(45,212,191,.6)' : '';
    btn.style.color = state.planMode ? 'var(--accent)' : '';
}

document.getElementById('planToggle').addEventListener('click', async () => {
    applySession();
    try {
        if (state.planMode) {
            await api(`/api/agent/plan/exit?userId=${encodeURIComponent(state.userId)}&sessionId=${encodeURIComponent(state.session)}`, { method: 'POST' });
            state.planMode = false;
        } else {
            await api(`/api/agent/plan/enter?userId=${encodeURIComponent(state.userId)}&sessionId=${encodeURIComponent(state.session)}`, { method: 'POST' });
            state.planMode = true;
        }
        updatePlanButton();
        logEvent('Plan Mode', state.planMode ? '进入只读调研' : '退出');
    } catch (error) {
        showToast(`切换 Plan Mode 失败：${error.message}`, 'error');
    }
});

document.getElementById('applyPermission').addEventListener('click', async () => {
    applySession();
    const mode = document.getElementById('permissionMode').value;
    try {
        const result = await api(`/api/agent/permission-mode?userId=${encodeURIComponent(state.userId)}&sessionId=${encodeURIComponent(state.session)}&mode=${encodeURIComponent(mode)}`, { method: 'PUT' });
        showToast(`权限模式已切换为 ${result.mode}`);
        logEvent('权限模式', result.mode);
    } catch (error) {
        showToast(`切换失败：${error.message}`, 'error');
    }
});

document.getElementById('userId').addEventListener('change', applySession);
document.getElementById('sessionId').addEventListener('change', applySession);

/* ------------------------------------------------------------------ */
/* 顶部操作                                                             */
/* ------------------------------------------------------------------ */

document.getElementById('refreshBtn').addEventListener('click', () => switchView(state.view));
document.getElementById('searchCustomers').addEventListener('click', loadCustomers);
document.getElementById('searchWorkflows').addEventListener('click', loadWorkflows);
document.getElementById('searchCases').addEventListener('click', loadCases);
document.getElementById('searchReports').addEventListener('click', loadReports);

document.getElementById('customerKeyword').addEventListener('keydown', (event) => {
    if (event.key === 'Enter') loadCustomers();
});

/* 初始化 */
applySession();
loadOverview();
