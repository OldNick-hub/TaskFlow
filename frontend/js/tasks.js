(() => {
  'use strict';

  const STATUSES = ['TODO', 'IN_PROGRESS', 'IN_REVIEW', 'DONE'];
  const STATUS_RANK = { TODO: 1, IN_PROGRESS: 2, IN_REVIEW: 3, DONE: 4 };
  const PRIORITY_RANK = { LOW: 1, MEDIUM: 2, HIGH: 3, URGENT: 4 };

  const supportsUserInvalid = typeof CSS !== 'undefined' && CSS.supports('selector(:user-invalid)');
  const supportsClosedBy = typeof HTMLDialogElement !== 'undefined' && 'closedBy' in HTMLDialogElement.prototype;
  const supportsCommands = typeof HTMLButtonElement !== 'undefined' && 'commandForElement' in HTMLButtonElement.prototype;

  const dateTimeFormatter = new Intl.DateTimeFormat(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  });

  let projectsState = [];
  let projectNameById = new Map();
  let tasksState = [];
  let currentView = 'kanban';
  let activeSort = { column: null, direction: 'ascending' };
  let editingTask = null;
  let deletingTask = null;
  let draggedTaskId = null;

  function redirectToLogin(reason) {
    const target = reason ? `login.html?reason=${encodeURIComponent(reason)}` : 'login.html';
    window.location.replace(target);
  }

  function formatDateTime(isoString) {
    if (!isoString) return 'No deadline';
    const parsed = new Date(isoString);
    if (Number.isNaN(parsed.getTime())) return 'No deadline';
    return dateTimeFormatter.format(parsed);
  }

  function toLocalDateTimeInputValue(isoString) {
    if (!isoString) return '';
    const date = new Date(isoString);
    if (Number.isNaN(date.getTime())) return '';
    const pad = (n) => String(n).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
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
      if (backdrop) backdrop.hidden = !isOpen;
      if (!desktopMedia.matches && isOpen && closeBtn) closeBtn.focus();
    };

    const closeDrawer = ({ restoreFocus = false } = {}) => {
      if (!sidebar.classList.contains('is-open')) return;
      setOpen(false);
      if (restoreFocus && !desktopMedia.matches) openBtn.focus();
    };

    openBtn.addEventListener('click', () => setOpen(true));
    if (closeBtn) closeBtn.addEventListener('click', () => closeDrawer({ restoreFocus: true }));
    if (backdrop) backdrop.addEventListener('click', () => closeDrawer({ restoreFocus: true }));

    document.addEventListener('keydown', (event) => {
      if (event.key === 'Escape' && sidebar.classList.contains('is-open') && !desktopMedia.matches) {
        closeDrawer({ restoreFocus: true });
      }
    });
  }

  function initLightDismissFallback() {
    if (supportsClosedBy) return;

    const isOutside = (event, dialog) => {
      const rect = dialog.getBoundingClientRect();
      return (
        event.clientX < rect.left ||
        event.clientX > rect.right ||
        event.clientY < rect.top ||
        event.clientY > rect.bottom
      );
    };

    document.querySelectorAll('dialog[closedby="any"]').forEach((dialog) => {
      let pressedOutside = false;
      dialog.addEventListener('pointerdown', (event) => {
        pressedOutside = isOutside(event, dialog);
      });
      dialog.addEventListener('pointerup', (event) => {
        if (pressedOutside && isOutside(event, dialog)) {
          dialog.close();
        }
      });
    });
  }

  function renderSidebarUser(user) {
    const nameEl = document.getElementById('sidebar-user-name');
    const emailEl = document.getElementById('sidebar-user-email');
    if (nameEl) nameEl.textContent = user.name || user.email || 'Workspace User';
    if (emailEl) emailEl.textContent = user.email || '—';
  }

  function showPageFeedback(type, message) {
    const banner = document.getElementById('tasks-feedback');
    if (!banner) return;
    banner.className = `alert alert-${type}`;
    banner.textContent = message;
    banner.hidden = false;
  }

  function hidePageFeedback() {
    const banner = document.getElementById('tasks-feedback');
    if (!banner) return;
    banner.hidden = true;
    banner.textContent = '';
  }

  function setLoadingState(isLoading) {
    const loadingBanner = document.getElementById('tasks-loading-banner');
    const refreshBtn = document.getElementById('refresh-tasks-btn');
    if (loadingBanner) loadingBanner.hidden = !isLoading;
    if (refreshBtn) {
      refreshBtn.disabled = isLoading;
      refreshBtn.textContent = isLoading ? 'Refreshing…' : 'Refresh';
    }
  }

  function showErrorBanner(message) {
    const banner = document.getElementById('tasks-error-banner');
    const textEl = document.getElementById('tasks-error-text');
    if (textEl) textEl.textContent = message;
    if (banner) banner.hidden = false;
  }

  function hideErrorBanner() {
    const banner = document.getElementById('tasks-error-banner');
    if (banner) banner.hidden = true;
  }

  function createTag(label, modifierClass = '') {
    const span = document.createElement('span');
    span.className = modifierClass ? `tag ${modifierClass}` : 'tag';
    span.textContent = label;
    return span;
  }

  function priorityTagClass(priority) {
    if (priority === 'LOW') return 'tag-low';
    if (priority === 'MEDIUM') return 'tag-medium';
    if (priority === 'HIGH') return 'tag-high';
    if (priority === 'URGENT') return 'tag-urgent';
    return '';
  }

  function isTaskOverdue(task) {
    if (!task.dueDate || task.status === 'DONE') return false;
    const dueMs = new Date(task.dueDate).getTime();
    return !Number.isNaN(dueMs) && dueMs < Date.now();
  }

  function getActiveFilters() {
    const projectSelect = document.getElementById('filter-project');
    const statusSelect = document.getElementById('filter-status');
    const prioritySelect = document.getElementById('filter-priority');
    return {
      projectId: projectSelect ? projectSelect.value : 'ALL',
      status: statusSelect ? statusSelect.value : '',
      priority: prioritySelect ? prioritySelect.value : ''
    };
  }

  function populateProjectSelectors() {
    const filterSelect = document.getElementById('filter-project');
    const formSelect = document.getElementById('task-project-input');
    const prevFilter = filterSelect ? filterSelect.value : 'ALL';

    if (filterSelect) {
      filterSelect.replaceChildren();
      const allOpt = document.createElement('option');
      allOpt.value = 'ALL';
      allOpt.textContent = 'All Projects';
      filterSelect.append(allOpt);

      projectsState.forEach((project) => {
        const opt = document.createElement('option');
        opt.value = project.id;
        opt.textContent = project.name || 'Untitled project';
        filterSelect.append(opt);
      });

      const params = new URLSearchParams(window.location.search);
      const urlProjectId = params.get('projectId');
      if (urlProjectId && projectNameById.has(urlProjectId)) {
        filterSelect.value = urlProjectId;
      } else if (prevFilter === 'ALL' || projectNameById.has(prevFilter)) {
        filterSelect.value = prevFilter;
      } else {
        filterSelect.value = 'ALL';
      }
    }

    if (formSelect) {
      formSelect.replaceChildren();
      const placeholder = document.createElement('option');
      placeholder.value = '';
      placeholder.textContent = 'Select a project…';
      formSelect.append(placeholder);

      projectsState.forEach((project) => {
        const opt = document.createElement('option');
        opt.value = project.id;
        opt.textContent = project.name || 'Untitled project';
        formSelect.append(opt);
      });
    }

    const createBtn = document.getElementById('create-task-btn');
    if (createBtn) {
      createBtn.disabled = projectsState.length === 0;
    }
  }

  function createStatusSelect(task) {
    const select = document.createElement('select');
    select.className = 'task-status-select';
    select.setAttribute('aria-label', `Change status for ${task.title || 'task'}`);

    STATUSES.forEach((status) => {
      const opt = document.createElement('option');
      opt.value = status;
      opt.textContent = status;
      if (task.status === status) {
        opt.selected = true;
      }
      select.append(opt);
    });

    select.addEventListener('change', async () => {
      const nextStatus = select.value;
      if (nextStatus === task.status) return;
      await handleQuickStatusChange(task, nextStatus, select);
    });

    return select;
  }

  async function handleQuickStatusChange(task, nextStatus, selectEl) {
    hidePageFeedback();
    if (selectEl) selectEl.disabled = true;

    try {
      await window.TaskFlowApi.tasks.updateStatus(task.id, nextStatus);
      showPageFeedback('success', `Moved "${task.title}" to ${nextStatus}.`);
      await fetchTasksForCurrentFilters();
    } catch (err) {
      if (err && err.status === 401) return;
      if (selectEl) selectEl.value = task.status;
      showErrorBanner(err?.message || 'Unable to update task status.');
    } finally {
      if (selectEl) selectEl.disabled = false;
    }
  }

  function renderKanbanBoard() {
    STATUSES.forEach((status) => {
      const cardsContainer = document.getElementById(`col-cards-${status}`);
      const countBadge = document.getElementById(`col-count-${status}`);
      if (!cardsContainer || !countBadge) return;

      cardsContainer.replaceChildren();
      const matching = tasksState.filter((task) => task.status === status);
      countBadge.textContent = String(matching.length);

      if (matching.length === 0) {
        const empty = document.createElement('div');
        empty.className = 'kanban-empty';
        empty.textContent = 'No tasks in this stage';
        cardsContainer.append(empty);
        return;
      }

      matching.forEach((task) => {
        const card = document.createElement('article');
        card.className = 'task-card';
        card.setAttribute('draggable', 'true');
        card.dataset.taskId = task.id;

        card.addEventListener('dragstart', (event) => {
          draggedTaskId = task.id;
          card.classList.add('is-dragging');
          if (event.dataTransfer) {
            event.dataTransfer.effectAllowed = 'move';
            event.dataTransfer.setData('text/plain', task.id);
          }
        });

        card.addEventListener('dragend', () => {
          draggedTaskId = null;
          card.classList.remove('is-dragging');
          document.querySelectorAll('.kanban-column.is-drop-target').forEach((col) => {
            col.classList.remove('is-drop-target');
          });
        });

        const top = document.createElement('div');
        top.className = 'task-card-top';

        const title = document.createElement('h3');
        title.className = 'task-card-title';
        title.textContent = task.title || 'Untitled task';

        const priorityTag = createTag(task.priority || 'MEDIUM', priorityTagClass(task.priority));
        top.append(title, priorityTag);

        card.append(top);

        if (task.description && task.description.trim()) {
          const desc = document.createElement('p');
          desc.className = 'task-card-desc';
          desc.textContent = task.description;
          card.append(desc);
        }

        const badges = document.createElement('div');
        badges.className = 'task-card-badges';
        const projectName = projectNameById.get(task.projectId) || 'Project';
        badges.append(createTag(projectName, 'tag-indigo'));
        if (isTaskOverdue(task)) {
          badges.append(createTag('Overdue', 'tag-danger'));
        }
        card.append(badges);

        const dueEl = document.createElement('div');
        dueEl.className = isTaskOverdue(task) ? 'task-card-due is-overdue' : 'task-card-due';
        dueEl.textContent = task.dueDate ? `Due ${formatDateTime(task.dueDate)}` : 'No deadline';
        card.append(dueEl);

        const statusWrap = document.createElement('div');
        statusWrap.className = 'task-status-control';
        statusWrap.append(createStatusSelect(task));
        card.append(statusWrap);

        const actions = document.createElement('div');
        actions.className = 'task-card-actions';

        const editBtn = document.createElement('button');
        editBtn.type = 'button';
        editBtn.className = 'btn btn-ghost btn-sm';
        editBtn.textContent = 'Edit';
        editBtn.addEventListener('click', () => openTaskFormModal(task));

        const deleteBtn = document.createElement('button');
        deleteBtn.type = 'button';
        deleteBtn.className = 'btn btn-danger-outline btn-sm';
        deleteBtn.textContent = 'Delete';
        deleteBtn.addEventListener('click', () => openDeleteModal(task));

        actions.append(editBtn, deleteBtn);
        card.append(actions);

        cardsContainer.append(card);
      });
    });
  }

  function initKanbanDropTargets() {
    document.querySelectorAll('.kanban-column[data-status-column]').forEach((column) => {
      const targetStatus = column.dataset.statusColumn;

      column.addEventListener('dragover', (event) => {
        if (!draggedTaskId) return;
        event.preventDefault();
        column.classList.add('is-drop-target');
      });

      column.addEventListener('dragleave', (event) => {
        if (!column.contains(event.relatedTarget)) {
          column.classList.remove('is-drop-target');
        }
      });

      column.addEventListener('drop', async (event) => {
        event.preventDefault();
        column.classList.remove('is-drop-target');
        const droppedId = draggedTaskId || event.dataTransfer?.getData('text/plain');
        if (!droppedId) return;
        const task = tasksState.find((t) => t.id === droppedId);
        if (!task || task.status === targetStatus) return;
        await handleQuickStatusChange(task, targetStatus, null);
      });
    });
  }

  function renderListViewTable() {
    const tbody = document.getElementById('tasks-table-body');
    if (!tbody) return;
    tbody.replaceChildren();

    tasksState.forEach((task) => {
      const tr = document.createElement('tr');
      tr.dataset.taskId = task.id;

      // Task Title + Description cell
      const titleTd = document.createElement('td');
      const titleDiv = document.createElement('div');
      titleDiv.className = 'table-task-title';
      titleDiv.textContent = task.title || 'Untitled task';
      titleTd.append(titleDiv);

      if (task.description && task.description.trim()) {
        const descDiv = document.createElement('div');
        descDiv.className = 'table-task-desc';
        descDiv.textContent = task.description;
        titleTd.append(descDiv);
      }

      // Project cell
      const projectTd = document.createElement('td');
      projectTd.append(createTag(projectNameById.get(task.projectId) || 'Project', 'tag-indigo'));

      // Status cell (accessible inline status selector + numeric sort rank)
      const statusTd = document.createElement('td');
      statusTd.dataset.value = String(STATUS_RANK[task.status] || 99);
      statusTd.append(createStatusSelect(task));

      // Priority cell
      const priorityTd = document.createElement('td');
      priorityTd.dataset.value = String(PRIORITY_RANK[task.priority] || 0);
      priorityTd.append(createTag(task.priority || 'MEDIUM', priorityTagClass(task.priority)));

      // Due Date cell
      const dueTd = document.createElement('td');
      const dueMs = task.dueDate ? new Date(task.dueDate).getTime() : Number.MAX_SAFE_INTEGER;
      dueTd.dataset.value = String(Number.isNaN(dueMs) ? Number.MAX_SAFE_INTEGER : dueMs);
      const dueSpan = document.createElement('span');
      if (isTaskOverdue(task)) {
        dueSpan.className = 'task-card-due is-overdue';
        dueSpan.textContent = `${formatDateTime(task.dueDate)} (Overdue)`;
      } else {
        dueSpan.textContent = formatDateTime(task.dueDate);
      }
      dueTd.append(dueSpan);

      // Actions cell
      const actionsTd = document.createElement('td');
      const actionsWrap = document.createElement('div');
      actionsWrap.className = 'table-actions-cell';

      const editBtn = document.createElement('button');
      editBtn.type = 'button';
      editBtn.className = 'btn btn-ghost btn-sm';
      editBtn.textContent = 'Edit';
      editBtn.addEventListener('click', () => openTaskFormModal(task));

      const deleteBtn = document.createElement('button');
      deleteBtn.type = 'button';
      deleteBtn.className = 'btn btn-danger-outline btn-sm';
      deleteBtn.textContent = 'Delete';
      deleteBtn.addEventListener('click', () => openDeleteModal(task));

      actionsWrap.append(editBtn, deleteBtn);
      actionsTd.append(actionsWrap);

      tr.append(titleTd, projectTd, statusTd, priorityTd, dueTd, actionsTd);
      tbody.append(tr);
    });

    if (activeSort.column !== null) {
      applyCurrentSortToTable({ announce: false });
    }
  }

  function applyCurrentSortToTable({ announce = true } = {}) {
    const table = document.getElementById('tasks-table');
    const tbody = document.getElementById('tasks-table-body');
    const announcer = document.getElementById('sort-status-announcer');
    if (!table || !tbody || activeSort.column === null) return;

    const headers = Array.from(table.tHead.rows[0].cells);
    const targetTh = headers[activeSort.column];
    if (!targetTh) return;

    headers.forEach((th) => th.removeAttribute('aria-sort'));
    targetTh.setAttribute('aria-sort', activeSort.direction);

    const factor = activeSort.direction === 'ascending' ? 1 : -1;
    const colIdx = targetTh.cellIndex;
    const rows = Array.from(tbody.rows);

    rows.sort((a, b) => {
      const aVal = Number(a.cells[colIdx].dataset.value || 0);
      const bVal = Number(b.cells[colIdx].dataset.value || 0);
      return factor * (aVal - bVal);
    });

    rows.forEach((row) => {
      if (typeof tbody.moveBefore === 'function') {
        tbody.moveBefore(row, null);
      } else {
        tbody.appendChild(row);
      }
    });

    if (announce && announcer) {
      const colLabel = targetTh.querySelector('button')?.textContent?.trim() || 'column';
      announcer.textContent = `Sorted by ${colLabel}, ${activeSort.direction}, ${rows.length} tasks`;
    }
  }

  function initSortableTable() {
    const table = document.getElementById('tasks-table');
    if (!table) return;

    const handleSortButton = (button) => {
      const th = button.closest('th');
      if (!th) return;
      const nextDir = th.getAttribute('aria-sort') === 'ascending' ? 'descending' : 'ascending';
      activeSort = { column: th.cellIndex, direction: nextDir };
      applyCurrentSortToTable({ announce: true });
    };

    table.addEventListener('command', (event) => {
      if (event.command === '--sort' && event.source) {
        handleSortButton(event.source);
      }
    });

    if (!supportsCommands) {
      table.tHead.querySelectorAll('button[command="--sort"]').forEach((button) => {
        button.addEventListener('click', () => handleSortButton(button));
      });
    }
  }

  function updateViewVisibility() {
    const noProjectsState = document.getElementById('tasks-no-projects-state');
    const emptyState = document.getElementById('tasks-empty-state');
    const kanbanView = document.getElementById('kanban-view');
    const listView = document.getElementById('list-view');
    const countLabel = document.getElementById('tasks-count-label');

    if (countLabel) {
      countLabel.textContent = `${tasksState.length} ${tasksState.length === 1 ? 'task' : 'tasks'}`;
    }

    if (projectsState.length === 0) {
      if (noProjectsState) noProjectsState.hidden = false;
      if (emptyState) emptyState.hidden = true;
      if (kanbanView) kanbanView.hidden = true;
      if (listView) listView.hidden = true;
      return;
    }

    if (noProjectsState) noProjectsState.hidden = true;

    if (tasksState.length === 0) {
      if (emptyState) emptyState.hidden = false;
      if (kanbanView) kanbanView.hidden = true;
      if (listView) listView.hidden = true;
      return;
    }

    if (emptyState) emptyState.hidden = true;
    if (kanbanView) kanbanView.hidden = currentView !== 'kanban';
    if (listView) listView.hidden = currentView !== 'list';
  }

  function renderWorkspace() {
    renderKanbanBoard();
    renderListViewTable();
    updateViewVisibility();
  }

  async function fetchTasksForCurrentFilters() {
    hideErrorBanner();
    setLoadingState(true);

    try {
      if (projectsState.length === 0) {
        tasksState = [];
        renderWorkspace();
        return;
      }

      const { projectId, status, priority } = getActiveFilters();
      const queryFilters = {};
      if (status) queryFilters.status = status;
      if (priority) queryFilters.priority = priority;

      if (projectId && projectId !== 'ALL') {
        const list = await window.TaskFlowApi.tasks.listByProject(projectId, queryFilters);
        tasksState = Array.isArray(list) ? list : [];
      } else {
        const results = await Promise.allSettled(
          projectsState.map((project) =>
            window.TaskFlowApi.tasks.listByProject(project.id, queryFilters)
          )
        );
        const aggregated = [];
        let failures = 0;
        results.forEach((res) => {
          if (res.status === 'fulfilled' && Array.isArray(res.value)) {
            aggregated.push(...res.value);
          } else if (res.status === 'rejected') {
            if (res.reason?.status === 401) return;
            failures += 1;
          }
        });
        tasksState = aggregated;
        if (failures > 0) {
          showErrorBanner(`Unable to load tasks for ${failures} project(s).`);
        }
      }

      renderWorkspace();
    } catch (err) {
      if (err && err.status === 401) return;
      showErrorBanner(err?.message || 'Unable to load tasks.');
      tasksState = [];
      renderWorkspace();
    } finally {
      setLoadingState(false);
    }
  }

  async function refreshProjectsAndTasks() {
    hideErrorBanner();
    setLoadingState(true);

    try {
      const projects = await window.TaskFlowApi.projects.list();
      projectsState = Array.isArray(projects) ? projects : [];
      projectNameById = new Map(projectsState.map((p) => [p.id, p.name]));
      populateProjectSelectors();
      await fetchTasksForCurrentFilters();
    } catch (err) {
      if (err && err.status === 401) return;
      showErrorBanner(err?.message || 'Unable to load projects.');
      setLoadingState(false);
    }
  }

  /* ---- Create / Edit Task Modal ---- */
  function linkError(el, shown) {
    const errorId = el.dataset.error;
    if (!errorId) return;
    const ids = (el.getAttribute('aria-describedby') || '')
      .split(/\s+/)
      .filter((id) => id && id !== errorId);
    if (shown) ids.push(errorId);
    if (ids.length) {
      el.setAttribute('aria-describedby', ids.join(' '));
    } else {
      el.removeAttribute('aria-describedby');
    }
  }

  function applyTaskFieldRules(el) {
    el.setCustomValidity('');
    if (el.name === 'projectId' && !el.disabled) {
      if (!el.value) {
        el.setCustomValidity('Please select a project for this task.');
      }
    } else if (el.name === 'title') {
      const trimmed = el.value.trim();
      if (trimmed.length === 0) {
        el.setCustomValidity('Task title is required.');
      } else if (trimmed.length > 200) {
        el.setCustomValidity('Task title must not exceed 200 characters.');
      }
    } else if (el.name === 'description') {
      if (el.value.length > 2000) {
        el.setCustomValidity('Description must not exceed 2000 characters.');
      }
    }

    const errorEl = document.getElementById(el.dataset.error || '');
    if (errorEl && el.validationMessage) {
      errorEl.textContent = el.validationMessage;
    }
  }

  function setFormFieldError(el, message) {
    const errorEl = document.getElementById(el.dataset.error || '');
    if (errorEl && message) errorEl.textContent = message;
    el.classList.add('user-invalid-fallback');
    el.setAttribute('aria-invalid', 'true');
    linkError(el, true);
  }

  function clearFormFieldError(el) {
    el.classList.remove('user-invalid-fallback');
    el.setAttribute('aria-invalid', 'false');
    linkError(el, false);
  }

  function resetTaskFormValidation(form) {
    const formError = document.getElementById('task-form-error');
    if (formError) {
      formError.hidden = true;
      formError.textContent = '';
    }
    form.querySelectorAll('input, select, textarea').forEach((el) => {
      el.setCustomValidity('');
      clearFormFieldError(el);
    });
  }

  function openTaskFormModal(task = null) {
    const dialog = document.getElementById('task-form-dialog');
    const form = document.getElementById('task-form');
    const titleHeader = document.getElementById('task-form-title');
    const submitBtn = document.getElementById('task-form-submit-btn');
    const projectInput = document.getElementById('task-project-input');
    const titleInput = document.getElementById('task-title-input');
    const descInput = document.getElementById('task-description-input');
    const statusInput = document.getElementById('task-status-input');
    const priorityInput = document.getElementById('task-priority-input');
    const dueInput = document.getElementById('task-due-input');
    if (!dialog || !form) return;

    editingTask = task;
    resetTaskFormValidation(form);

    if (task) {
      titleHeader.textContent = 'Edit Task';
      submitBtn.textContent = 'Save Changes';
      projectInput.value = task.projectId || '';
      projectInput.disabled = true;
      titleInput.value = task.title || '';
      descInput.value = task.description || '';
      statusInput.value = task.status || 'TODO';
      priorityInput.value = task.priority || 'MEDIUM';
      dueInput.value = toLocalDateTimeInputValue(task.dueDate);
    } else {
      titleHeader.textContent = 'Create Task';
      submitBtn.textContent = 'Create Task';
      form.reset();
      resetTaskFormValidation(form);
      projectInput.disabled = false;

      const { projectId, status, priority } = getActiveFilters();
      if (projectId && projectId !== 'ALL') {
        projectInput.value = projectId;
      } else if (projectsState.length > 0) {
        projectInput.value = projectsState[0].id;
      }
      statusInput.value = status || 'TODO';
      priorityInput.value = priority || 'MEDIUM';
    }

    dialog.showModal();
  }

  function initTaskFormModal() {
    const dialog = document.getElementById('task-form-dialog');
    const form = document.getElementById('task-form');
    const closeBtn = document.getElementById('task-form-close-btn');
    const cancelBtn = document.getElementById('task-form-cancel-btn');
    const submitBtn = document.getElementById('task-form-submit-btn');
    const formError = document.getElementById('task-form-error');
    if (!dialog || !form) return;

    const closeModal = () => {
      dialog.close();
      editingTask = null;
    };

    if (closeBtn) closeBtn.addEventListener('click', closeModal);
    if (cancelBtn) cancelBtn.addEventListener('click', closeModal);

    form.addEventListener(
      'blur',
      (event) => {
        const el = event.target;
        if (!el.matches?.('input, select, textarea')) return;
        applyTaskFieldRules(el);
        const invalid = supportsUserInvalid
          ? el.matches(':user-invalid') || !el.validity.valid
          : !el.validity.valid;
        if (invalid && (el.value.length > 0 || el.tagName === 'SELECT')) {
          setFormFieldError(el, el.validationMessage);
        } else if (el.validity.valid) {
          clearFormFieldError(el);
        }
      },
      true
    );

    form.addEventListener('input', (event) => {
      const el = event.target;
      if (!el.matches?.('input, select, textarea')) return;
      el.setCustomValidity('');
      if (el.getAttribute('aria-invalid') === 'true') {
        applyTaskFieldRules(el);
        if (el.validity.valid) {
          clearFormFieldError(el);
        } else {
          setFormFieldError(el, el.validationMessage);
        }
      }
    });

    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      if (formError) {
        formError.hidden = true;
        formError.textContent = '';
      }

      const controls = Array.from(form.querySelectorAll('input, select, textarea'));
      let firstInvalid = null;
      controls.forEach((el) => {
        applyTaskFieldRules(el);
        if (!el.validity.valid) {
          setFormFieldError(el, el.validationMessage);
          if (!firstInvalid) firstInvalid = el;
        } else {
          clearFormFieldError(el);
        }
      });

      if (firstInvalid) {
        firstInvalid.focus();
        return;
      }

      const titleVal = form.elements.namedItem('title').value.trim();
      const rawDesc = form.elements.namedItem('description').value.trim();
      const statusVal = form.elements.namedItem('status').value;
      const priorityVal = form.elements.namedItem('priority').value;
      const rawDue = form.elements.namedItem('dueDate').value;
      const dueDateIso = rawDue ? new Date(rawDue).toISOString() : null;

      const payload = {
        title: titleVal,
        description: rawDesc.length > 0 ? rawDesc : null,
        status: statusVal,
        priority: priorityVal,
        dueDate: dueDateIso
      };

      const isEdit = Boolean(editingTask);
      const idleLabel = isEdit ? 'Save Changes' : 'Create Task';
      const busyLabel = isEdit ? 'Saving…' : 'Creating…';

      submitBtn.disabled = true;
      submitBtn.textContent = busyLabel;

      try {
        if (isEdit) {
          await window.TaskFlowApi.tasks.update(editingTask.id, payload);
          showPageFeedback('success', `Task "${titleVal}" updated.`);
        } else {
          const targetProjectId = form.elements.namedItem('projectId').value;
          await window.TaskFlowApi.tasks.create(targetProjectId, payload);
          showPageFeedback('success', `Task "${titleVal}" created.`);
        }
        closeModal();
        await fetchTasksForCurrentFilters();
      } catch (err) {
        if (err && err.status === 401) return;
        if (err?.validationErrors) {
          Object.entries(err.validationErrors).forEach(([field, msg]) => {
            const input = form.elements.namedItem(field);
            if (input && input instanceof HTMLElement) {
              setFormFieldError(input, String(msg));
            }
          });
        }
        if (formError) {
          formError.textContent = err?.message || 'Unable to save task.';
          formError.hidden = false;
        }
      } finally {
        submitBtn.disabled = false;
        submitBtn.textContent = idleLabel;
      }
    });
  }

  /* ---- Delete Confirmation Modal ---- */
  function openDeleteModal(task) {
    const dialog = document.getElementById('delete-task-dialog');
    const messageEl = document.getElementById('delete-task-message');
    const errorEl = document.getElementById('delete-task-error');
    if (!dialog || !messageEl) return;

    deletingTask = task;
    if (errorEl) {
      errorEl.hidden = true;
      errorEl.textContent = '';
    }
    messageEl.textContent = `Are you sure you want to delete "${task.title}"? This action cannot be undone.`;
    dialog.showModal();
  }

  function initDeleteModal() {
    const dialog = document.getElementById('delete-task-dialog');
    const closeBtn = document.getElementById('delete-task-close-btn');
    const cancelBtn = document.getElementById('delete-task-cancel-btn');
    const confirmBtn = document.getElementById('delete-task-confirm-btn');
    const errorEl = document.getElementById('delete-task-error');
    if (!dialog || !confirmBtn) return;

    const closeModal = () => {
      dialog.close();
      deletingTask = null;
    };

    if (closeBtn) closeBtn.addEventListener('click', closeModal);
    if (cancelBtn) cancelBtn.addEventListener('click', closeModal);

    confirmBtn.addEventListener('click', async () => {
      if (!deletingTask) return;
      if (errorEl) {
        errorEl.hidden = true;
        errorEl.textContent = '';
      }

      confirmBtn.disabled = true;
      confirmBtn.textContent = 'Deleting…';
      const deletedTitle = deletingTask.title;

      try {
        await window.TaskFlowApi.tasks.remove(deletingTask.id);
        closeModal();
        showPageFeedback('success', `Task "${deletedTitle}" deleted.`);
        await fetchTasksForCurrentFilters();
      } catch (err) {
        if (err && err.status === 401) return;
        if (errorEl) {
          errorEl.textContent = err?.message || 'Unable to delete task.';
          errorEl.hidden = false;
        }
      } finally {
        confirmBtn.disabled = false;
        confirmBtn.textContent = 'Delete Task';
      }
    });
  }

  function initFiltersAndViewControls() {
    const projectSelect = document.getElementById('filter-project');
    const statusSelect = document.getElementById('filter-status');
    const prioritySelect = document.getElementById('filter-priority');
    const clearBtn = document.getElementById('clear-filters-btn');
    const kanbanBtn = document.getElementById('view-kanban-btn');
    const listBtn = document.getElementById('view-list-btn');
    const createBtn = document.getElementById('create-task-btn');
    const emptyCreateBtn = document.getElementById('empty-create-task-btn');
    const refreshBtn = document.getElementById('refresh-tasks-btn');
    const retryBtn = document.getElementById('retry-tasks-btn');

    [projectSelect, statusSelect, prioritySelect].forEach((select) => {
      if (select) {
        select.addEventListener('change', () => {
          hidePageFeedback();
          fetchTasksForCurrentFilters();
        });
      }
    });

    if (clearBtn) {
      clearBtn.addEventListener('click', () => {
        hidePageFeedback();
        if (projectSelect) projectSelect.value = 'ALL';
        if (statusSelect) statusSelect.value = '';
        if (prioritySelect) prioritySelect.value = '';
        fetchTasksForCurrentFilters();
      });
    }

    const setView = (view) => {
      currentView = view;
      if (kanbanBtn) kanbanBtn.setAttribute('aria-pressed', String(view === 'kanban'));
      if (listBtn) listBtn.setAttribute('aria-pressed', String(view === 'list'));
      updateViewVisibility();
    };

    if (kanbanBtn) kanbanBtn.addEventListener('click', () => setView('kanban'));
    if (listBtn) listBtn.addEventListener('click', () => setView('list'));

    if (createBtn) {
      createBtn.addEventListener('click', () => {
        hidePageFeedback();
        openTaskFormModal(null);
      });
    }
    if (emptyCreateBtn) {
      emptyCreateBtn.addEventListener('click', () => {
        hidePageFeedback();
        openTaskFormModal(null);
      });
    }
    if (refreshBtn) {
      refreshBtn.addEventListener('click', () => {
        hidePageFeedback();
        refreshProjectsAndTasks();
      });
    }
    if (retryBtn) {
      retryBtn.addEventListener('click', () => {
        hidePageFeedback();
        refreshProjectsAndTasks();
      });
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

  async function bootstrapTasksPage() {
    const token = window.TaskFlowApi.getAccessToken();
    if (!token) {
      redirectToLogin('auth_required');
      return;
    }

    try {
      const user = await window.TaskFlowApi.auth.me();
      const guardStatus = document.getElementById('auth-guard-status');
      const content = document.getElementById('tasks-content');

      if (guardStatus) guardStatus.hidden = true;
      if (content) content.hidden = false;

      renderSidebarUser(user);
      await refreshProjectsAndTasks();
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
    initLightDismissFallback();
    initLogout();
    initFiltersAndViewControls();
    initKanbanDropTargets();
    initSortableTable();
    initTaskFormModal();
    initDeleteModal();
    bootstrapTasksPage();
  });
})();
