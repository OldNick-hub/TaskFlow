(() => {
  'use strict';

  const supportsUserInvalid = typeof CSS !== 'undefined' && CSS.supports('selector(:user-invalid)');
  const supportsClosedBy = typeof HTMLDialogElement !== 'undefined' && 'closedBy' in HTMLDialogElement.prototype;

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

  let projectsState = [];
  let taskCountsByProjectId = new Map();
  let editingProjectId = null;
  let deletingProject = null;
  let activeDetailProject = null;

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
    const banner = document.getElementById('projects-feedback');
    if (!banner) return;
    banner.className = `alert alert-${type}`;
    banner.textContent = message;
    banner.hidden = false;
  }

  function hidePageFeedback() {
    const banner = document.getElementById('projects-feedback');
    if (!banner) return;
    banner.hidden = true;
    banner.textContent = '';
  }

  function setLoadingState(isLoading) {
    const loadingBanner = document.getElementById('projects-loading-banner');
    const refreshBtn = document.getElementById('refresh-projects-btn');
    if (loadingBanner) loadingBanner.hidden = !isLoading;
    if (refreshBtn) {
      refreshBtn.disabled = isLoading;
      refreshBtn.textContent = isLoading ? 'Refreshing…' : 'Refresh';
    }
  }

  function showErrorBanner(message) {
    const banner = document.getElementById('projects-error-banner');
    const textEl = document.getElementById('projects-error-text');
    if (textEl) textEl.textContent = message;
    if (banner) banner.hidden = false;
  }

  function hideErrorBanner() {
    const banner = document.getElementById('projects-error-banner');
    if (banner) banner.hidden = true;
  }

  function createButton(label, className, onClick) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = className;
    button.textContent = label;
    button.addEventListener('click', onClick);
    return button;
  }

  function createTag(label, modifierClass = '') {
    const span = document.createElement('span');
    span.className = modifierClass ? `tag ${modifierClass}` : 'tag';
    span.textContent = label;
    return span;
  }

  function renderProjectsList() {
    const grid = document.getElementById('projects-grid');
    const emptyState = document.getElementById('projects-empty-state');
    const countLabel = document.getElementById('projects-count-label');
    if (!grid || !emptyState || !countLabel) return;

    grid.replaceChildren();
    countLabel.textContent = `${projectsState.length} ${projectsState.length === 1 ? 'project' : 'projects'}`;

    if (projectsState.length === 0) {
      emptyState.hidden = false;
      return;
    }

    emptyState.hidden = true;

    projectsState.forEach((project) => {
      const card = document.createElement('article');
      card.className = 'card project-card';

      const body = document.createElement('div');
      body.className = 'project-card-body';

      const header = document.createElement('div');
      header.className = 'project-card-header';

      const titleBtn = document.createElement('button');
      titleBtn.type = 'button';
      titleBtn.className = 'project-title-btn';
      titleBtn.textContent = project.name || 'Untitled project';
      titleBtn.addEventListener('click', () => openDetailModal(project));

      const count = taskCountsByProjectId.get(project.id);
      const countText = typeof count === 'number'
        ? `${count} ${count === 1 ? 'task' : 'tasks'}`
        : 'Tasks N/A';
      const countTag = createTag(countText, 'tag-indigo');

      header.append(titleBtn, countTag);

      const desc = document.createElement('p');
      desc.className = 'project-description';
      desc.textContent = project.description && project.description.trim()
        ? project.description
        : 'No description provided.';

      const meta = document.createElement('div');
      meta.className = 'project-meta';
      const createdSpan = document.createElement('span');
      createdSpan.textContent = `Created ${formatDate(project.createdAt)}`;
      meta.append(createdSpan);

      body.append(header, desc, meta);

      const actions = document.createElement('div');
      actions.className = 'project-card-actions';

      actions.append(
        createButton('View', 'btn btn-secondary btn-sm', () => openDetailModal(project)),
        createButton('Edit', 'btn btn-ghost btn-sm', () => openProjectFormModal(project)),
        createButton('Delete', 'btn btn-danger-outline btn-sm', () => openDeleteModal(project))
      );

      card.append(body, actions);
      grid.append(card);
    });
  }

  async function fetchTaskCounts(projects) {
    const counts = new Map();
    if (!Array.isArray(projects) || projects.length === 0) {
      return counts;
    }

    const results = await Promise.allSettled(
      projects.map((project) =>
        window.TaskFlowApi.tasks.listByProject(project.id).then((tasks) => ({
          projectId: project.id,
          count: Array.isArray(tasks) ? tasks.length : 0
        }))
      )
    );

    results.forEach((result) => {
      if (result.status === 'fulfilled') {
        counts.set(result.value.projectId, result.value.count);
      }
    });

    return counts;
  }

  async function loadProjects() {
    hideErrorBanner();
    setLoadingState(true);

    try {
      const projects = await window.TaskFlowApi.projects.list();
      projectsState = Array.isArray(projects) ? projects : [];
      taskCountsByProjectId = await fetchTaskCounts(projectsState);
      renderProjectsList();
    } catch (err) {
      if (err && err.status === 401) {
        return;
      }
      showErrorBanner(err?.message || 'Unable to load projects from the server.');
      projectsState = [];
      taskCountsByProjectId = new Map();
      renderProjectsList();
    } finally {
      setLoadingState(false);
    }
  }

  /* ---- Create / Edit Project Modal ---- */
  function linkError(el, shown) {
    const errorId = el.dataset.error;
    if (!errorId) return;
    const ids = (el.getAttribute('aria-describedby') || '')
      .split(/\s+/)
      .filter((id) => id && id !== errorId);
    if (shown) {
      ids.push(errorId);
    }
    if (ids.length) {
      el.setAttribute('aria-describedby', ids.join(' '));
    } else {
      el.removeAttribute('aria-describedby');
    }
  }

  function applyProjectFieldRules(el) {
    el.setCustomValidity('');
    if (el.name === 'name') {
      const trimmed = el.value.trim();
      if (trimmed.length === 0) {
        el.setCustomValidity('Project name is required.');
      } else if (trimmed.length > 150) {
        el.setCustomValidity('Project name must not exceed 150 characters.');
      }
    } else if (el.name === 'description') {
      if (el.value.length > 1000) {
        el.setCustomValidity('Description must not exceed 1000 characters.');
      }
    }
    const errorEl = document.getElementById(el.dataset.error || '');
    if (errorEl && el.validationMessage) {
      errorEl.textContent = el.validationMessage;
    }
  }

  function setFormFieldError(el, message) {
    const errorEl = document.getElementById(el.dataset.error || '');
    if (errorEl && message) {
      errorEl.textContent = message;
    }
    el.classList.add('user-invalid-fallback');
    el.setAttribute('aria-invalid', 'true');
    linkError(el, true);
  }

  function clearFormFieldError(el) {
    el.classList.remove('user-invalid-fallback');
    el.setAttribute('aria-invalid', 'false');
    linkError(el, false);
  }

  function resetProjectFormValidation(form) {
    const formError = document.getElementById('project-form-error');
    if (formError) {
      formError.hidden = true;
      formError.textContent = '';
    }
    form.querySelectorAll('input, textarea').forEach((el) => {
      el.setCustomValidity('');
      clearFormFieldError(el);
    });
  }

  function openProjectFormModal(project = null) {
    const dialog = document.getElementById('project-form-dialog');
    const form = document.getElementById('project-form');
    const titleEl = document.getElementById('project-form-title');
    const submitBtn = document.getElementById('project-form-submit-btn');
    const nameInput = document.getElementById('project-name-input');
    const descInput = document.getElementById('project-description-input');
    if (!dialog || !form || !nameInput || !descInput) return;

    editingProjectId = project ? project.id : null;
    resetProjectFormValidation(form);

    if (project) {
      titleEl.textContent = 'Edit Project';
      submitBtn.textContent = 'Save Changes';
      nameInput.value = project.name || '';
      descInput.value = project.description || '';
    } else {
      titleEl.textContent = 'Create Project';
      submitBtn.textContent = 'Create Project';
      form.reset();
      resetProjectFormValidation(form);
    }

    dialog.showModal();
  }

  function initProjectFormModal() {
    const dialog = document.getElementById('project-form-dialog');
    const form = document.getElementById('project-form');
    const closeBtn = document.getElementById('project-form-close-btn');
    const cancelBtn = document.getElementById('project-form-cancel-btn');
    const submitBtn = document.getElementById('project-form-submit-btn');
    const formError = document.getElementById('project-form-error');
    if (!dialog || !form) return;

    const closeModal = () => {
      dialog.close();
      editingProjectId = null;
    };

    if (closeBtn) closeBtn.addEventListener('click', closeModal);
    if (cancelBtn) cancelBtn.addEventListener('click', closeModal);

    form.addEventListener(
      'blur',
      (event) => {
        const el = event.target;
        if (!el.matches?.('input, textarea')) return;
        applyProjectFieldRules(el);
        const invalid = supportsUserInvalid
          ? el.matches(':user-invalid') || !el.validity.valid
          : !el.validity.valid;
        if (invalid && el.value.length > 0) {
          setFormFieldError(el, el.validationMessage);
        } else if (el.validity.valid) {
          clearFormFieldError(el);
        }
      },
      true
    );

    form.addEventListener('input', (event) => {
      const el = event.target;
      if (!el.matches?.('input, textarea')) return;
      el.setCustomValidity('');
      if (el.getAttribute('aria-invalid') === 'true') {
        applyProjectFieldRules(el);
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

      const controls = Array.from(form.querySelectorAll('input, textarea'));
      let firstInvalid = null;
      controls.forEach((el) => {
        applyProjectFieldRules(el);
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

      const nameVal = form.elements.namedItem('name').value.trim();
      const rawDesc = form.elements.namedItem('description').value.trim();
      const payload = {
        name: nameVal,
        description: rawDesc.length > 0 ? rawDesc : null
      };

      const isEdit = Boolean(editingProjectId);
      const idleLabel = isEdit ? 'Save Changes' : 'Create Project';
      const busyLabel = isEdit ? 'Saving…' : 'Creating…';

      submitBtn.disabled = true;
      submitBtn.textContent = busyLabel;

      try {
        if (isEdit) {
          await window.TaskFlowApi.projects.update(editingProjectId, payload);
          showPageFeedback('success', `Project "${nameVal}" updated.`);
        } else {
          await window.TaskFlowApi.projects.create(payload);
          showPageFeedback('success', `Project "${nameVal}" created.`);
        }
        closeModal();
        await loadProjects();
      } catch (err) {
        if (err && err.status === 401) {
          return;
        }
        if (err?.validationErrors) {
          Object.entries(err.validationErrors).forEach(([field, msg]) => {
            const input = form.elements.namedItem(field);
            if (input && input instanceof HTMLElement) {
              setFormFieldError(input, String(msg));
            }
          });
        }
        if (formError) {
          formError.textContent = err?.message || 'Unable to save project.';
          formError.hidden = false;
        }
      } finally {
        submitBtn.disabled = false;
        submitBtn.textContent = idleLabel;
      }
    });
  }

  /* ---- View Project Details Modal ---- */
  function priorityTagClass(priority) {
    if (priority === 'URGENT') return 'tag-danger';
    if (priority === 'HIGH') return 'tag-warning';
    return '';
  }

  function renderDetailTasks(tasks) {
    const listEl = document.getElementById('detail-tasks-list');
    const emptyEl = document.getElementById('detail-tasks-empty');
    const badgeEl = document.getElementById('detail-tasks-badge');
    const countEl = document.getElementById('detail-project-task-count');
    if (!listEl || !emptyEl || !badgeEl || !countEl) return;

    listEl.replaceChildren();
    const count = tasks.length;
    const countText = `${count} ${count === 1 ? 'task' : 'tasks'}`;
    badgeEl.textContent = countText;
    countEl.textContent = String(count);

    if (count === 0) {
      emptyEl.hidden = false;
      return;
    }

    emptyEl.hidden = true;
    tasks.forEach((task) => {
      const li = document.createElement('li');
      li.className = 'item-row';

      const primary = document.createElement('div');
      primary.className = 'item-primary';

      const title = document.createElement('p');
      title.className = 'item-title';
      title.textContent = task.title || 'Untitled task';

      const meta = document.createElement('p');
      meta.className = 'item-meta';
      const dueText = task.dueDate ? `Due ${formatDate(task.dueDate, true)}` : 'No deadline';
      const descText = task.description ? `${task.description} • ` : '';
      meta.textContent = `${descText}${dueText}`;

      primary.append(title, meta);

      const badges = document.createElement('div');
      badges.className = 'item-badges';
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

  async function openDetailModal(project) {
    const dialog = document.getElementById('project-detail-dialog');
    const titleEl = document.getElementById('detail-project-title');
    const descEl = document.getElementById('detail-project-description');
    const createdEl = document.getElementById('detail-project-created');
    const updatedEl = document.getElementById('detail-project-updated');
    const loadingEl = document.getElementById('detail-tasks-loading');
    const errorEl = document.getElementById('detail-tasks-error');
    const errorTextEl = document.getElementById('detail-tasks-error-text');
    const emptyEl = document.getElementById('detail-tasks-empty');
    const listEl = document.getElementById('detail-tasks-list');
    if (!dialog) return;

    activeDetailProject = project;
    titleEl.textContent = project.name || 'Untitled project';
    descEl.textContent = project.description && project.description.trim()
      ? project.description
      : 'No description provided.';
    createdEl.textContent = formatDate(project.createdAt);
    updatedEl.textContent = formatDate(project.updatedAt);

    if (listEl) listEl.replaceChildren();
    if (emptyEl) emptyEl.hidden = true;
    if (errorEl) errorEl.hidden = true;
    if (loadingEl) loadingEl.hidden = false;

    dialog.showModal();

    try {
      const [freshProject, tasks] = await Promise.all([
        window.TaskFlowApi.projects.get(project.id),
        window.TaskFlowApi.tasks.listByProject(project.id)
      ]);

      if (freshProject) {
        activeDetailProject = freshProject;
        titleEl.textContent = freshProject.name || 'Untitled project';
        descEl.textContent = freshProject.description && freshProject.description.trim()
          ? freshProject.description
          : 'No description provided.';
        createdEl.textContent = formatDate(freshProject.createdAt);
        updatedEl.textContent = formatDate(freshProject.updatedAt);
      }

      renderDetailTasks(Array.isArray(tasks) ? tasks : []);
    } catch (err) {
      if (err && err.status === 401) {
        return;
      }
      if (errorTextEl) {
        errorTextEl.textContent = err?.message || 'Unable to load project details.';
      }
      if (errorEl) errorEl.hidden = false;
    } finally {
      if (loadingEl) loadingEl.hidden = true;
    }
  }

  function initDetailModal() {
    const dialog = document.getElementById('project-detail-dialog');
    const closeBtn = document.getElementById('detail-close-btn');
    const doneBtn = document.getElementById('detail-done-btn');
    const editBtn = document.getElementById('detail-edit-btn');
    const deleteBtn = document.getElementById('detail-delete-btn');
    if (!dialog) return;

    const closeModal = () => {
      dialog.close();
      activeDetailProject = null;
    };

    if (closeBtn) closeBtn.addEventListener('click', closeModal);
    if (doneBtn) doneBtn.addEventListener('click', closeModal);

    if (editBtn) {
      editBtn.addEventListener('click', () => {
        const target = activeDetailProject;
        closeModal();
        if (target) openProjectFormModal(target);
      });
    }

    if (deleteBtn) {
      deleteBtn.addEventListener('click', () => {
        const target = activeDetailProject;
        closeModal();
        if (target) openDeleteModal(target);
      });
    }
  }

  /* ---- Delete Confirmation Modal ---- */
  function openDeleteModal(project) {
    const dialog = document.getElementById('delete-project-dialog');
    const messageEl = document.getElementById('delete-dialog-message');
    const errorEl = document.getElementById('delete-dialog-error');
    if (!dialog || !messageEl) return;

    deletingProject = project;
    if (errorEl) {
      errorEl.hidden = true;
      errorEl.textContent = '';
    }
    messageEl.textContent = `Are you sure you want to delete "${project.name}"? This action cannot be undone.`;
    dialog.showModal();
  }

  function initDeleteModal() {
    const dialog = document.getElementById('delete-project-dialog');
    const closeBtn = document.getElementById('delete-close-btn');
    const cancelBtn = document.getElementById('delete-cancel-btn');
    const confirmBtn = document.getElementById('delete-confirm-btn');
    const errorEl = document.getElementById('delete-dialog-error');
    if (!dialog || !confirmBtn) return;

    const closeModal = () => {
      dialog.close();
      deletingProject = null;
    };

    if (closeBtn) closeBtn.addEventListener('click', closeModal);
    if (cancelBtn) cancelBtn.addEventListener('click', closeModal);

    confirmBtn.addEventListener('click', async () => {
      if (!deletingProject) return;
      if (errorEl) {
        errorEl.hidden = true;
        errorEl.textContent = '';
      }

      confirmBtn.disabled = true;
      confirmBtn.textContent = 'Deleting…';
      const deletedName = deletingProject.name;

      try {
        await window.TaskFlowApi.projects.remove(deletingProject.id);
        closeModal();
        showPageFeedback('success', `Project "${deletedName}" deleted.`);
        await loadProjects();
      } catch (err) {
        if (err && err.status === 401) {
          return;
        }
        if (errorEl) {
          errorEl.textContent = err?.message || 'Unable to delete project.';
          errorEl.hidden = false;
        }
      } finally {
        confirmBtn.disabled = false;
        confirmBtn.textContent = 'Delete Project';
      }
    });
  }

  function initPageActions() {
    const createBtn = document.getElementById('create-project-btn');
    const emptyCreateBtn = document.getElementById('empty-create-project-btn');
    const refreshBtn = document.getElementById('refresh-projects-btn');
    const retryBtn = document.getElementById('retry-projects-btn');

    if (createBtn) {
      createBtn.addEventListener('click', () => {
        hidePageFeedback();
        openProjectFormModal(null);
      });
    }
    if (emptyCreateBtn) {
      emptyCreateBtn.addEventListener('click', () => {
        hidePageFeedback();
        openProjectFormModal(null);
      });
    }
    if (refreshBtn) {
      refreshBtn.addEventListener('click', () => {
        hidePageFeedback();
        loadProjects();
      });
    }
    if (retryBtn) {
      retryBtn.addEventListener('click', () => {
        hidePageFeedback();
        loadProjects();
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

  async function bootstrapProjectsPage() {
    const token = window.TaskFlowApi.getAccessToken();
    if (!token) {
      redirectToLogin('auth_required');
      return;
    }

    try {
      const user = await window.TaskFlowApi.auth.me();
      const guardStatus = document.getElementById('auth-guard-status');
      const content = document.getElementById('projects-content');

      if (guardStatus) guardStatus.hidden = true;
      if (content) content.hidden = false;

      renderSidebarUser(user);
      await loadProjects();
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
    initPageActions();
    initProjectFormModal();
    initDetailModal();
    initDeleteModal();
    bootstrapProjectsPage();
  });
})();
