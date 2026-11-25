(() => {
  'use strict';

  const MAX_RECENT_PROJECTS = 5;
  const MAX_UPCOMING_DEADLINES = 6;
  const supportsAttrValue = typeof CSS !== 'undefined' && CSS.supports('width: attr(value type(<number>))');

  const dateFormatter = new Intl.DateTimeFormat(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric'
  });

  const dateTimeFormatter = new Intl.DateTimeFormat(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  });

  function redirectToLogin(reason) {
    const target = reason ? `login.html?reason=${encodeURIComponent(reason)}` : 'login.html';
    window.location.replace(target);
  }

  function formatDate(isoString, includeTime = false) {
    if (!isoString) return '—';
    const parsed = new Date(isoString);
    if (Number.isNaN(parsed.getTime())) return '—';
    return includeTime ? dateTimeFormatter.format(parsed) : dateFormatter.format(parsed);
  }

  function initSidebarDrawer() {
    const sidebar = document.getElementById('dashboard-sidebar');
    const backdrop = document.getElementById('sidebar-backdrop');
    const openBtn = document.getElementById('sidebar-open-btn');
    const closeBtn = document.getElementById('sidebar-close-btn');
    if (!sidebar || !openBtn) return;

    const desktopMedia = window.matchMedia('(min-width: 1024px)');

    const setOpen = (isOpen) => {
      sidebar.classList.toggle('is-open', isOpen);
      openBtn.setAttribute('aria-expanded', String(isOpen));
      if (backdrop) {
        backdrop.hidden = !isOpen;
      }
      if (!desktopMedia.matches && isOpen && closeBtn) {
        closeBtn.focus();
      }
    };

    const closeDrawer = ({ restoreFocus = false } = {}) => {
      if (!sidebar.classList.contains('is-open')) return;
      setOpen(false);
      if (restoreFocus && !desktopMedia.matches) {
        openBtn.focus();
      }
    };

    openBtn.addEventListener('click', () => setOpen(true));
    if (closeBtn) {
      closeBtn.addEventListener('click', () => closeDrawer({ restoreFocus: true }));
    }
    if (backdrop) {
      backdrop.addEventListener('click', () => closeDrawer({ restoreFocus: true }));
    }

    document.addEventListener('keydown', (event) => {
      if (event.key === 'Escape' && sidebar.classList.contains('is-open') && !desktopMedia.matches) {
        closeDrawer({ restoreFocus: true });
      }
    });

    const navLinks = Array.from(sidebar.querySelectorAll('.sidebar-link'));
    navLinks.forEach((link) => {
      link.addEventListener('click', () => {
        navLinks.forEach((item) => {
          item.classList.remove('is-active');
          item.removeAttribute('aria-current');
        });
        link.classList.add('is-active');
        link.setAttribute('aria-current', 'page');
        closeDrawer();
      });
    });
  }

  function renderUserIdentity(user) {
    const welcomeHeading = document.getElementById('welcome-heading');
    const sidebarName = document.getElementById('sidebar-user-name');
    const sidebarEmail = document.getElementById('sidebar-user-email');
    const profileName = document.getElementById('profile-name');
    const profileEmail = document.getElementById('profile-email');
    const profileId = document.getElementById('profile-id');
    const profileCreatedAt = document.getElementById('profile-created-at');

    const displayName = user.name || user.email || 'Workspace User';
    if (welcomeHeading) welcomeHeading.textContent = `Welcome back, ${displayName}`;
    if (sidebarName) sidebarName.textContent = displayName;
    if (sidebarEmail) sidebarEmail.textContent = user.email || '—';
    if (profileName) profileName.textContent = user.name || '—';
    if (profileEmail) profileEmail.textContent = user.email || '—';
    if (profileId) profileId.textContent = user.id || '—';
    if (profileCreatedAt) profileCreatedAt.textContent = formatDate(user.createdAt);
  }

  function renderSummaryStats(analytics) {
    const totalProjects = Number(analytics.totalProjects || 0);
    const totalTasks = Number(analytics.totalTasks || 0);
    const completedTasks = Number(analytics.completedTasks || 0);
    const pendingTasks = Number(analytics.pendingTasks || 0);
    const inProgressTasks = Number(analytics.inProgressTasks || 0);
    const overdueTasks = Number(analytics.overdueTasks || 0);

    document.getElementById('stat-total-projects').textContent = String(totalProjects);
    document.getElementById('stat-total-tasks').textContent = String(totalTasks);
    document.getElementById('stat-completed-tasks').textContent = String(completedTasks);

    const overdueEl = document.getElementById('stat-overdue-tasks');
    overdueEl.textContent = String(overdueTasks);
    overdueEl.classList.toggle('stat-value-danger', overdueTasks > 0);

    document.getElementById('stat-pending-footnote').textContent =
      `${pendingTasks} pending completion`;
    document.getElementById('stat-in-progress-footnote').textContent =
      `${inProgressTasks} currently in progress`;
  }

  function renderCompletionProgress(analytics) {
    const totalTasks = Number(analytics.totalTasks || 0);
    const completedTasks = Number(analytics.completedTasks || 0);
    const pendingTasks = Number(analytics.pendingTasks || 0);
    const rawPercent = Number(analytics.completionPercentage || 0);
    const clampedPercent = Math.max(0, Math.min(100, rawPercent));
    const roundedDisplay = Number.isInteger(clampedPercent)
      ? String(clampedPercent)
      : clampedPercent.toFixed(1);

    const progressEl = document.getElementById('completion-progress');
    progressEl.setAttribute('value', String(clampedPercent));
    progressEl.value = clampedPercent;
    progressEl.setAttribute(
      'aria-valuetext',
      `${roundedDisplay}% completed (${completedTasks} of ${totalTasks} tasks)`
    );
    if (!supportsAttrValue) {
      progressEl.style.setProperty('--value', String(clampedPercent));
    }

    document.getElementById('completion-percent-text').textContent = `${roundedDisplay}%`;
    document.getElementById('completion-done-count').textContent = String(completedTasks);
    document.getElementById('completion-pending-count').textContent = String(pendingTasks);
  }

  function renderDistributionChart({ totalTasks, entries, barsContainerId, emptyStateId }) {
    const barsContainer = document.getElementById(barsContainerId);
    const emptyState = document.getElementById(emptyStateId);

    if (totalTasks <= 0) {
      if (barsContainer) barsContainer.hidden = true;
      if (emptyState) emptyState.hidden = false;
      return;
    }

    if (barsContainer) barsContainer.hidden = false;
    if (emptyState) emptyState.hidden = true;

    entries.forEach(({ count, barId, countId }) => {
      const numericCount = Number(count || 0);
      const pct = Math.round((numericCount / totalTasks) * 100);
      const barEl = document.getElementById(barId);
      const countEl = document.getElementById(countId);

      if (barEl) {
        barEl.value = pct;
        barEl.setAttribute('value', String(pct));
      }
      if (countEl) {
        countEl.textContent = `${numericCount} (${pct}%)`;
      }
    });
  }

  function renderAnalytics(analytics) {
    renderSummaryStats(analytics);
    renderCompletionProgress(analytics);

    const totalTasks = Number(analytics.totalTasks || 0);
    const statusDist = analytics.statusDistribution || {};
    const priorityDist = analytics.priorityDistribution || {};

    renderDistributionChart({
      totalTasks,
      barsContainerId: 'status-chart-bars',
      emptyStateId: 'status-chart-empty',
      entries: [
        { count: statusDist.TODO, barId: 'bar-status-todo', countId: 'count-status-todo' },
        { count: statusDist.IN_PROGRESS, barId: 'bar-status-in-progress', countId: 'count-status-in-progress' },
        { count: statusDist.IN_REVIEW, barId: 'bar-status-in-review', countId: 'count-status-in-review' },
        { count: statusDist.DONE, barId: 'bar-status-done', countId: 'count-status-done' }
      ]
    });

    renderDistributionChart({
      totalTasks,
      barsContainerId: 'priority-chart-bars',
      emptyStateId: 'priority-chart-empty',
      entries: [
        { count: priorityDist.LOW, barId: 'bar-priority-low', countId: 'count-priority-low' },
        { count: priorityDist.MEDIUM, barId: 'bar-priority-medium', countId: 'count-priority-medium' },
        { count: priorityDist.HIGH, barId: 'bar-priority-high', countId: 'count-priority-high' },
        { count: priorityDist.URGENT, barId: 'bar-priority-urgent', countId: 'count-priority-urgent' }
      ]
    });
  }

  function createTag(label, modifierClass = '') {
    const span = document.createElement('span');
    span.className = modifierClass ? `tag ${modifierClass}` : 'tag';
    span.textContent = label;
    return span;
  }

  function renderRecentProjects(projects, taskCountByProjectId) {
    const listEl = document.getElementById('recent-projects-list');
    const emptyEl = document.getElementById('recent-projects-empty');
    const badgeEl = document.getElementById('recent-projects-badge');
    if (!listEl || !emptyEl || !badgeEl) return;

    listEl.replaceChildren();
    badgeEl.textContent = `${projects.length} ${projects.length === 1 ? 'project' : 'projects'}`;

    if (projects.length === 0) {
      emptyEl.hidden = false;
      return;
    }

    emptyEl.hidden = true;
    projects.slice(0, MAX_RECENT_PROJECTS).forEach((project) => {
      const li = document.createElement('li');
      li.className = 'item-row';

      const primary = document.createElement('div');
      primary.className = 'item-primary';

      const title = document.createElement('p');
      title.className = 'item-title';
      title.textContent = project.name || 'Untitled project';

      const meta = document.createElement('p');
      meta.className = 'item-meta';
      const desc = project.description ? project.description : 'No description';
      meta.textContent = `${desc} • Created ${formatDate(project.createdAt)}`;

      primary.append(title, meta);

      const badges = document.createElement('div');
      badges.className = 'item-badges';
      const count = taskCountByProjectId.get(project.id);
      const countLabel = typeof count === 'number'
        ? `${count} ${count === 1 ? 'task' : 'tasks'}`
        : 'Tasks unavailable';
      badges.append(createTag(countLabel, 'tag-indigo'));

      li.append(primary, badges);
      listEl.append(li);
    });
  }

  function priorityTagClass(priority) {
    if (priority === 'URGENT') return 'tag-danger';
    if (priority === 'HIGH') return 'tag-warning';
    return '';
  }

  function renderUpcomingDeadlines(tasks, projectNameById) {
    const listEl = document.getElementById('deadlines-list');
    const emptyEl = document.getElementById('deadlines-empty');
    const badgeEl = document.getElementById('deadlines-badge');
    if (!listEl || !emptyEl || !badgeEl) return;

    listEl.replaceChildren();

    const nowMs = Date.now();
    const scheduledTasks = tasks
      .filter((task) => task.dueDate && task.status !== 'DONE')
      .sort((a, b) => new Date(a.dueDate).getTime() - new Date(b.dueDate).getTime());

    badgeEl.textContent = `${scheduledTasks.length} scheduled`;

    if (scheduledTasks.length === 0) {
      emptyEl.hidden = false;
      return;
    }

    emptyEl.hidden = true;
    scheduledTasks.slice(0, MAX_UPCOMING_DEADLINES).forEach((task) => {
      const li = document.createElement('li');
      li.className = 'item-row';

      const primary = document.createElement('div');
      primary.className = 'item-primary';

      const title = document.createElement('p');
      title.className = 'item-title';
      title.textContent = task.title || 'Untitled task';

      const projectName = projectNameById.get(task.projectId) || 'Project';
      const dueMs = new Date(task.dueDate).getTime();
      const isOverdue = !Number.isNaN(dueMs) && dueMs < nowMs;

      const meta = document.createElement('p');
      meta.className = 'item-meta';
      meta.textContent = `${projectName} • Due ${formatDate(task.dueDate, true)}`;

      primary.append(title, meta);

      const badges = document.createElement('div');
      badges.className = 'item-badges';
      if (isOverdue) {
        badges.append(createTag('Overdue', 'tag-danger'));
      }
      if (task.status) {
        badges.append(createTag(task.status, 'tag-indigo'));
      }
      if (task.priority) {
        badges.append(createTag(task.priority, priorityTagClass(task.priority)));
      }

      li.append(primary, badges);
      listEl.append(li);
    });
  }

  function setLoadingState(isLoading) {
    const loadingBanner = document.getElementById('dashboard-loading-banner');
    const refreshBtn = document.getElementById('refresh-dashboard-btn');
    const statsGrid = document.getElementById('stats-grid');

    if (loadingBanner) loadingBanner.hidden = !isLoading;
    if (statsGrid) statsGrid.classList.toggle('is-loading', isLoading);
    if (refreshBtn) {
      refreshBtn.disabled = isLoading;
      refreshBtn.textContent = isLoading ? 'Refreshing…' : 'Refresh data';
    }
  }

  function showDashboardError(message) {
    const errorBanner = document.getElementById('dashboard-error-banner');
    const errorText = document.getElementById('dashboard-error-text');
    if (errorText) errorText.textContent = message;
    if (errorBanner) errorBanner.hidden = false;
  }

  function hideDashboardError() {
    const errorBanner = document.getElementById('dashboard-error-banner');
    if (errorBanner) errorBanner.hidden = true;
  }

  async function fetchAllProjectTasks(projects) {
    if (!Array.isArray(projects) || projects.length === 0) {
      return { tasks: [], taskCountByProjectId: new Map(), failedProjects: 0 };
    }

    const results = await Promise.allSettled(
      projects.map((project) =>
        window.TaskFlowApi.tasks.listByProject(project.id).then((tasks) => ({
          projectId: project.id,
          tasks: Array.isArray(tasks) ? tasks : []
        }))
      )
    );

    const tasks = [];
    const taskCountByProjectId = new Map();
    let failedProjects = 0;

    results.forEach((result) => {
      if (result.status === 'fulfilled') {
        const { projectId, tasks: projectTasks } = result.value;
        taskCountByProjectId.set(projectId, projectTasks.length);
        tasks.push(...projectTasks);
      } else {
        failedProjects += 1;
      }
    });

    return { tasks, taskCountByProjectId, failedProjects };
  }

  async function loadDashboardData() {
    hideDashboardError();
    setLoadingState(true);

    try {
      const [analyticsResult, projectsResult] = await Promise.allSettled([
        window.TaskFlowApi.dashboard.getAnalytics(),
        window.TaskFlowApi.projects.list()
      ]);

      const errors = [];

      if (analyticsResult.status === 'fulfilled' && analyticsResult.value) {
        renderAnalytics(analyticsResult.value);
      } else {
        errors.push(analyticsResult.reason?.message || 'Unable to load analytics.');
      }

      if (projectsResult.status === 'fulfilled') {
        const projects = Array.isArray(projectsResult.value) ? projectsResult.value : [];
        const projectNameById = new Map(projects.map((p) => [p.id, p.name]));
        const { tasks, taskCountByProjectId, failedProjects } = await fetchAllProjectTasks(projects);

        renderRecentProjects(projects, taskCountByProjectId);
        renderUpcomingDeadlines(tasks, projectNameById);

        if (failedProjects > 0) {
          errors.push(`Could not load tasks for ${failedProjects} project(s).`);
        }
      } else {
        errors.push(projectsResult.reason?.message || 'Unable to load projects.');
        renderRecentProjects([], new Map());
        renderUpcomingDeadlines([], new Map());
      }

      if (errors.length > 0) {
        showDashboardError(errors.join(' '));
      }
    } finally {
      setLoadingState(false);
    }
  }

  function initLogout() {
    const logoutBtn = document.getElementById('logout-btn');
    if (!logoutBtn) return;

    logoutBtn.addEventListener('click', async () => {
      logoutBtn.disabled = true;
      try {
        await window.TaskFlowApi.auth.logout();
      } catch {
        window.TaskFlowApi.clearSession();
      } finally {
        window.location.replace('login.html?logged_out=1');
      }
    });
  }

  function initRefreshActions() {
    const refreshBtn = document.getElementById('refresh-dashboard-btn');
    const retryBtn = document.getElementById('retry-dashboard-btn');

    if (refreshBtn) {
      refreshBtn.addEventListener('click', () => loadDashboardData());
    }
    if (retryBtn) {
      retryBtn.addEventListener('click', () => loadDashboardData());
    }
  }

  async function bootstrapDashboard() {
    const token = window.TaskFlowApi.getAccessToken();
    if (!token) {
      redirectToLogin('auth_required');
      return;
    }

    try {
      const user = await window.TaskFlowApi.auth.me();
      const guardStatus = document.getElementById('auth-guard-status');
      const dashboardContent = document.getElementById('dashboard-content');

      if (guardStatus) guardStatus.hidden = true;
      if (dashboardContent) dashboardContent.hidden = false;

      renderUserIdentity(user);
      await loadDashboardData();
    } catch (err) {
      if (err && err.status === 401) {
        window.TaskFlowApi.clearSession();
        redirectToLogin('session_expired');
        return;
      }
      const guardStatus = document.getElementById('auth-guard-status');
      if (guardStatus) {
        guardStatus.className = 'alert alert-error';
        guardStatus.textContent = err?.message || 'Unable to verify your session with the server.';
      }
    }
  }

  window.addEventListener('taskflow:unauthorized', () => {
    redirectToLogin('session_expired');
  });

  document.addEventListener('DOMContentLoaded', () => {
    initSidebarDrawer();
    initLogout();
    initRefreshActions();
    bootstrapDashboard();
  });
})();
