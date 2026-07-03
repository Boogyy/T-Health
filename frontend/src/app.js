import './styles.css';

(() => {
  'use strict';

  const viteEnv = typeof import.meta !== 'undefined' ? import.meta.env : {};

  const CONFIG = {
    keycloakBaseUrl: window.T_HEALTH_CONFIG?.keycloakBaseUrl || localStorage.getItem('tHealthKeycloakUrl') || viteEnv.VITE_KEYCLOAK_BASE_URL || 'http://localhost:8180',
    realm: window.T_HEALTH_CONFIG?.realm || localStorage.getItem('tHealthRealm') || viteEnv.VITE_KEYCLOAK_REALM || 't-health',
    clientId: window.T_HEALTH_CONFIG?.clientId || localStorage.getItem('tHealthClientId') || viteEnv.VITE_KEYCLOAK_CLIENT_ID || 't-health-frontend',
    apiBaseUrl: normalizeBaseUrl(window.T_HEALTH_CONFIG?.apiBaseUrl || localStorage.getItem('tHealthApiBase') || viteEnv.VITE_API_BASE_URL || defaultApiBaseUrl()),
  };

  const STORAGE = {
    token: 'tHealthTokens',
    pkce: 'tHealthPkce',
    returnTo: 'tHealthReturnTo',
    authMode: 'tHealthAuthMode',
    seenAchievements: 'tHealthSeenAchievementIds',
  };

  const state = {
    user: null,
    workouts: null,
    foodEntries: null,
    achievements: null,
    loading: false,
    pageLoading: false,
    error: null,
    flash: null,
    achievementModal: null,
    achievementQueue: [],
    achievementQueueTotal: 0,
    achievementQueueIndex: 0,
    achievementShareNotice: null,
  };

  const app = document.getElementById('app');

  window.addEventListener('hashchange', renderRoute);
  window.addEventListener('DOMContentLoaded', init);

  async function init() {
    const query = new URLSearchParams(window.location.search);
    if (query.has('error')) {
      const reason = query.get('error_description') || query.get('error') || 'Keycloak вернул ошибку авторизации.';
      history.replaceState(null, document.title, window.location.pathname + window.location.hash);
      state.error = reason;
      renderLogin();
      return;
    }

    if (query.has('code')) {
      await handleAuthCallback(query);
      return;
    }

    renderRoute();
  }

  function defaultApiBaseUrl() {
    // В dev-режиме Vite обращаемся к текущему origin, а /api проксируется на backend из vite.config.js.
    // В production это также работает, если nginx/сервер проксирует /api на backend.
    return window.location.origin;
  }

  function normalizeBaseUrl(value) {
    return String(value || '').replace(/\/$/, '');
  }

  function currentRedirectUri() {
    return window.location.origin + window.location.pathname;
  }

  function authEndpoint(mode) {
    const action = mode === 'register' ? 'registrations' : 'auth';
    return `${CONFIG.keycloakBaseUrl}/realms/${encodeURIComponent(CONFIG.realm)}/protocol/openid-connect/${action}`;
  }

  function tokenEndpoint() {
    return `${CONFIG.keycloakBaseUrl}/realms/${encodeURIComponent(CONFIG.realm)}/protocol/openid-connect/token`;
  }

  function logoutEndpoint() {
    return `${CONFIG.keycloakBaseUrl}/realms/${encodeURIComponent(CONFIG.realm)}/protocol/openid-connect/logout`;
  }

  async function startAuth(mode) {
    try {
      const verifier = createCodeVerifier();
      const challenge = await createCodeChallenge(verifier);
      const stateValue = cryptoRandomString(24);
      const returnTo = normalizedRoute().startsWith('/login') || normalizedRoute() === '/' ? '/profile' : normalizedRoute();

      localStorage.setItem(STORAGE.pkce, JSON.stringify({ verifier, state: stateValue, createdAt: Date.now() }));
      localStorage.setItem(STORAGE.returnTo, returnTo);
      localStorage.setItem(STORAGE.authMode, mode);

      const params = new URLSearchParams({
        client_id: CONFIG.clientId,
        response_type: 'code',
        scope: 'openid profile email',
        redirect_uri: currentRedirectUri(),
        state: stateValue,
        code_challenge: challenge,
        code_challenge_method: 'S256',
      });

      if (mode === 'login') {
        params.set('prompt', 'login');
      }

      window.location.assign(`${authEndpoint(mode)}?${params.toString()}`);
    } catch (error) {
      state.error = friendlyError(error);
      renderLogin();
    }
  }

  async function handleAuthCallback(query) {
    renderLoading('Завершаем вход через Keycloak', 'Получаем токен и создаем локальный профиль в backend.');

    try {
      const pkce = JSON.parse(localStorage.getItem(STORAGE.pkce) || '{}');
      if (!pkce.verifier || !pkce.state || pkce.state !== query.get('state')) {
        throw new Error('Состояние авторизации не совпало. Попробуйте войти еще раз.');
      }

      const body = new URLSearchParams({
        grant_type: 'authorization_code',
        client_id: CONFIG.clientId,
        code: query.get('code'),
        redirect_uri: currentRedirectUri(),
        code_verifier: pkce.verifier,
      });

      const response = await fetch(tokenEndpoint(), {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body,
      });

      if (!response.ok) {
        throw new Error(await readError(response));
      }

      const tokens = await response.json();
      saveTokens(tokens);
      localStorage.removeItem(STORAGE.pkce);

      history.replaceState(null, document.title, window.location.pathname);

      await getCurrentUser(true);
      const authMode = localStorage.getItem(STORAGE.authMode);
      localStorage.removeItem(STORAGE.authMode);

      if (authMode === 'register') {
        await loadDashboard(true, {
          animateNewAchievements: true,
          previousAchievementIds: new Set(),
        });
      }

      const returnTo = authMode === 'register' ? '/profile' : localStorage.getItem(STORAGE.returnTo) || '/profile';
      localStorage.removeItem(STORAGE.returnTo);
      navigate(returnTo);
    } catch (error) {
      clearAuthData();
      history.replaceState(null, document.title, window.location.pathname);
      state.error = friendlyError(error);
      renderLogin();
    }
  }

  function saveTokens(tokens) {
    const now = Math.floor(Date.now() / 1000);
    const payload = {
      accessToken: tokens.access_token,
      refreshToken: tokens.refresh_token,
      idToken: tokens.id_token,
      tokenType: tokens.token_type || 'Bearer',
      expiresAt: now + Number(tokens.expires_in || 0),
      refreshExpiresAt: now + Number(tokens.refresh_expires_in || 0),
    };
    localStorage.setItem(STORAGE.token, JSON.stringify(payload));
  }

  function readTokens() {
    try {
      return JSON.parse(localStorage.getItem(STORAGE.token) || 'null');
    } catch {
      return null;
    }
  }

  function isAuthenticated() {
    const tokens = readTokens();
    return Boolean(tokens?.accessToken);
  }

  async function getAccessToken() {
    const tokens = readTokens();
    if (!tokens?.accessToken) {
      throw new AuthRequiredError();
    }

    const now = Math.floor(Date.now() / 1000);
    if (tokens.expiresAt && tokens.expiresAt - 25 > now) {
      return tokens.accessToken;
    }

    if (!tokens.refreshToken || (tokens.refreshExpiresAt && tokens.refreshExpiresAt <= now)) {
      clearAuthData();
      throw new AuthRequiredError();
    }

    const body = new URLSearchParams({
      grant_type: 'refresh_token',
      client_id: CONFIG.clientId,
      refresh_token: tokens.refreshToken,
    });

    const response = await fetch(tokenEndpoint(), {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });

    if (!response.ok) {
      clearAuthData();
      throw new AuthRequiredError();
    }

    const nextTokens = await response.json();
    saveTokens(nextTokens);
    return nextTokens.access_token;
  }

  async function apiFetch(path, options = {}) {
    const token = await getAccessToken();
    const headers = new Headers(options.headers || {});
    headers.set('Authorization', `Bearer ${token}`);

    const hasBody = options.body !== undefined && options.body !== null;
    if (hasBody && !headers.has('Content-Type')) {
      headers.set('Content-Type', 'application/json');
    }

    const response = await fetch(`${CONFIG.apiBaseUrl}${path}`, {
      ...options,
      headers,
    });

    if (response.status === 401) {
      clearAuthData();
      throw new AuthRequiredError();
    }

    if (!response.ok) {
      throw new Error(await readError(response));
    }

    if (response.status === 204) {
      return null;
    }

    return response.json();
  }

  async function readError(response) {
    const text = await response.text();
    if (!text) {
      return `${response.status} ${response.statusText}`;
    }

    try {
      const data = JSON.parse(text);
      if (data.validationErrors) {
        const details = Object.entries(data.validationErrors)
          .map(([key, value]) => `${key}: ${value}`)
          .join('; ');
        return details || data.message || text;
      }
      return data.message || text;
    } catch {
      return text;
    }
  }

  async function getCurrentUser(force = false) {
    if (state.user && !force) {
      return state.user;
    }
    state.user = await apiFetch('/api/users/me');
    return state.user;
  }

  async function loadDashboard(force = false, options = {}) {
    if (state.loading) {
      return;
    }

    const hasData = state.user && state.workouts && state.foodEntries && state.achievements;
    if (hasData && !force) {
      return;
    }

    state.loading = true;
    state.error = null;
    renderRoute(false);

    try {
      state.user = await apiFetch('/api/users/me');
      const previousAchievementIds = options.previousAchievementIds || achievementIdSet(state.achievements || []);
      const [workouts, foodEntries, achievements] = await Promise.all([
        apiFetch('/api/workouts'),
        apiFetch('/api/food-entries'),
        fetchAchievements(),
      ]);
      state.workouts = Array.isArray(workouts) ? workouts : [];
      state.foodEntries = Array.isArray(foodEntries) ? foodEntries : [];
      state.achievements = normalizeAchievements(Array.isArray(achievements) ? achievements : []);

      if (options.animateNewAchievements) {
        const newAchievements = findNewAchievements(state.achievements, previousAchievementIds);
        showAchievementQueue(newAchievements);
      }
      markAchievementsSeen(state.achievements);
    } catch (error) {
      if (error instanceof AuthRequiredError) {
        navigate('/login');
        return;
      }
      state.error = friendlyError(error);
    } finally {
      state.loading = false;
      renderRoute(false);
    }
  }

  function clearAuthData() {
    localStorage.removeItem(STORAGE.token);
    localStorage.removeItem(STORAGE.pkce);
    localStorage.removeItem(STORAGE.authMode);
    state.user = null;
    state.workouts = null;
    state.foodEntries = null;
    state.achievements = null;
  }

  function logout() {
    const tokens = readTokens();
    clearAuthData();
    const params = new URLSearchParams({
      client_id: CONFIG.clientId,
      post_logout_redirect_uri: currentRedirectUri(),
    });
    if (tokens?.idToken) {
      params.set('id_token_hint', tokens.idToken);
    }
    window.location.assign(`${logoutEndpoint()}?${params.toString()}`);
  }

  function normalizedRoute() {
    const hash = window.location.hash.replace(/^#/, '');
    return hash || '/';
  }

  function navigate(path) {
    window.location.hash = path.startsWith('/') ? path : `/${path}`;
  }

  function routeParts() {
    return normalizedRoute().split('/').filter(Boolean).map(decodeURIComponent);
  }

  function renderRoute(scrollTop = true) {
    const route = normalizedRoute();
    if (scrollTop) {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }

    if (route === '/' || route === '/login') {
      renderLogin();
      return;
    }

    if (!isAuthenticated()) {
      localStorage.setItem(STORAGE.returnTo, route);
      renderLogin('Для продолжения войдите через Keycloak.');
      return;
    }

    const parts = routeParts();
    if (parts[0] === 'profile') {
      renderProtected(renderProfile());
      if (!state.user || !state.workouts || !state.foodEntries || !state.achievements) {
        loadDashboard();
      }
      return;
    }

    if (parts[0] === 'achievements') {
      renderProtected(renderAchievementsRoute(parts));
      if (!state.achievements) {
        loadDashboard();
      }
      return;
    }

    if (parts[0] === 'workouts') {
      renderProtected(renderWorkoutRoute(parts));
      if (!state.workouts) {
        loadDashboard();
      }
      return;
    }

    if (parts[0] === 'food') {
      renderProtected(renderFoodRoute(parts));
      if (!state.foodEntries) {
        loadDashboard();
      }
      return;
    }

    if (parts[0] === 'feed') {
      renderProtected(renderFeedPlaceholder());
      return;
    }

    renderProtected(renderNotFound());
  }

  function renderLogin(message = '') {
    const alert = state.error
      ? alertHtml('error', state.error)
      : message
        ? alertHtml('success', message)
        : '';

    app.innerHTML = `
      <main class="page">
        <section class="hero">
          <div class="hero-card">
            <div>
              <div class="brand" aria-label="Т-Здоровье">
                ${logoHtml()}
                <span>Т-Здоровье</span>
              </div>
              <p class="eyebrow">Контроль здоровья в одном окне</p>
              <h1>Дневник тренировок и питания для сотрудников</h1>
              <p class="lead">Войдите через Keycloak, чтобы backend сразу создал локальный профиль, а вы могли вести тренировки и приемы пищи.</p>
              <div class="hero-grid">
                <div class="mini-card"><span class="mini-icon">🏃</span><strong>Тренировки</strong><span class="muted">Создание, список и детали</span></div>
                <div class="mini-card"><span class="mini-icon">🥗</span><strong>Питание</strong><span class="muted">Калории, БЖУ, даты</span></div>
                <div class="mini-card"><span class="mini-icon">🏅</span><strong>Достижения</strong><span class="muted">Выдаются backend-сервисом</span></div>
              </div>
            </div>
            <p class="footer-note">Фронтенд использует OAuth2 Authorization Code + PKCE и отправляет JWT в API как Bearer-токен.</p>
          </div>
          <aside class="auth-panel">
            ${logoHtml()}
            <div>
              <h2>Начните здесь</h2>
              <p class="muted">Регистрация и вход открываются на стороне Keycloak. После callback приложение вызывает <strong>/api/users/me</strong> для создания записи в локальной БД.</p>
            </div>
            ${alert}
            <button class="btn full" type="button" data-auth="register">Register</button>
            <button class="btn secondary full" type="button" data-auth="login">Login</button>
            <p class="footer-note">Keycloak: ${escapeHtml(CONFIG.keycloakBaseUrl)} · API: ${escapeHtml(CONFIG.apiBaseUrl)}</p>
          </aside>
        </section>
      </main>
    `;
    bindGlobalActions();
  }

  function renderLoading(title, text) {
    app.innerHTML = `
      <main class="page">
        <section class="auth-panel" style="max-width: 560px; margin: 80px auto;">
          ${logoHtml()}
          <div>
            <h2>${escapeHtml(title)}</h2>
            <p class="muted">${escapeHtml(text)}</p>
          </div>
          <div class="skeleton" aria-hidden="true"></div>
        </section>
      </main>
    `;
  }

  function renderProtected(content) {
    const route = normalizedRoute();
    app.innerHTML = `
      <header class="topbar">
        <div class="topbar-inner">
          <a class="brand" href="#/profile" aria-label="Т-Здоровье — профиль">
            ${logoHtml()}
            <span>Т-Здоровье</span>
          </a>
          <nav class="nav" aria-label="Основная навигация">
            ${navLink('/profile', 'Профиль', route)}
            ${navLink('/workouts', 'Тренировки', route)}
            ${navLink('/food', 'Питание', route)}
            ${navLink('/achievements', 'Достижения', route)}
            ${navLink('/feed', 'Лента постов', route)}
            <button class="nav-link" type="button" data-reload>Обновить</button>
            <button class="nav-link" type="button" data-logout>Выйти</button>
          </nav>
        </div>
      </header>
      <main class="page">${content}</main>
      ${achievementModalHtml()}
    `;
    bindGlobalActions();
    bindForms();
  }

  function navLink(path, label, route) {
    const active = route === path || route.startsWith(`${path}/`);
    return `<a class="nav-link${active ? ' active' : ''}" href="#${path}">${escapeHtml(label)}</a>`;
  }

  function renderProfile() {
    if (state.error) {
      return pageHeader('Профиль', 'Не удалось получить данные.') + alertHtml('error', state.error);
    }

    if (!state.user || !state.workouts || !state.foodEntries) {
      return pageHeader('Профиль', 'Подгружаем профиль, тренировки и приемы пищи.') + skeletonGrid();
    }

    const user = state.user || {};
    const workouts = state.workouts || [];
    const foodEntries = state.foodEntries || [];
    const achievements = state.achievements || [];
    const workoutMinutes = workouts.reduce((sum, item) => sum + Number(item.durationMinutes || 0), 0);
    const workoutCalories = workouts.reduce((sum, item) => sum + Number(item.caloriesBurned || 0), 0);
    const mealCalories = foodEntries.reduce((sum, item) => sum + Number(item.calories || 0), 0);

    return `
      ${flashHtml()}
      <section class="section-header">
        <div>
          <p class="eyebrow">Личный кабинет</p>
          <h1 style="font-size: clamp(38px, 5vw, 64px);">Профиль</h1>
          <p>Сводка пользователя и быстрый переход к текущему функционалу.</p>
        </div>
        <span class="status-pill">${achievements.length} достижений</span>
      </section>
      <section class="dashboard">
        <aside class="card profile-card">
          <div>
            <div class="avatar" aria-label="Фото профиля">${escapeHtml(getInitials(user))}</div>
            <h2>${escapeHtml(user.username || 'Пользователь')}</h2>
            <p class="muted">${escapeHtml(user.email || 'email из Keycloak')}</p>
            <div class="meta-list">
              <div class="meta-item"><span class="meta-label">Имя</span><strong>${escapeHtml(user.firstName || '—')}</strong></div>
              <div class="meta-item"><span class="meta-label">Фамилия</span><strong>${escapeHtml(user.lastName || '—')}</strong></div>
              <div class="meta-item"><span class="meta-label">ID</span><strong>${escapeHtml(shortId(user.id))}</strong></div>
            </div>
          </div>
        </aside>
        <div class="cards-grid">
          <a class="card accent" href="#/workouts" aria-label="Все тренировки">
            <div>
              <span class="badge">🏃 Все тренировки</span>
              <h3>Тренировки пользователя</h3>
              <p class="muted">Список, детали и создание новой записи.</p>
            </div>
            <div class="metric">${workouts.length}<small> шт.</small></div>
            <p>${workoutMinutes} мин · ${workoutCalories} ккал</p>
          </a>
          <a class="card" href="#/food" aria-label="Все приемы пищи">
            <div>
              <span class="badge">🥗 Все приемы пищи</span>
              <h3>Дневник питания</h3>
              <p class="muted">БЖУ, калории и время приема пищи.</p>
            </div>
            <div class="metric">${foodEntries.length}<small> шт.</small></div>
            <p>${mealCalories} ккал суммарно</p>
          </a>
          <a class="card achievement-card" href="#/achievements" aria-label="Все достижения">
            <div>
              <span class="badge">🏅 Все достижения</span>
              <h3>Витрина достижений</h3>
              <p class="muted">Полученные бейджи, даты и будущая возможность поделиться.</p>
            </div>
            <div class="metric">${achievements.length}<small> шт.</small></div>
            <p>${achievementProgressLabel(achievements)}</p>
          </a>
        </div>
      </section>
      ${renderRecentBlock(workouts, foodEntries, achievements)}
    `;
  }

  function renderRecentBlock(workouts, foodEntries, achievements) {
    const lastWorkout = workouts[0];
    const lastFood = foodEntries[0];
    const lastAchievement = achievements[0];

    return `
      <section class="section-header">
        <div>
          <h2>Последние события</h2>
          <p>Короткая проверка, что данные после создания появились в списках.</p>
        </div>
      </section>
      <div class="cards-grid">
        ${lastWorkout ? `
          <a class="card" href="#/workouts/${encodeURIComponent(lastWorkout.id)}">
            <span class="badge">Последняя тренировка</span>
            <h3>${escapeHtml(lastWorkout.title)}</h3>
            <p class="muted">${workoutTypeLabel(lastWorkout.type)} · ${lastWorkout.durationMinutes || 0} мин · ${formatDateTime(lastWorkout.workoutDate)}</p>
          </a>` : emptyMini('Тренировок пока нет', 'Создайте первую тренировку из раздела «Тренировки».')}
        ${lastFood ? `
          <a class="card" href="#/food/${encodeURIComponent(lastFood.id)}">
            <span class="badge">Последний прием пищи</span>
            <h3>${escapeHtml(lastFood.mealName)}</h3>
            <p class="muted">${lastFood.calories || 0} ккал · ${formatDateTime(lastFood.mealDate)}</p>
          </a>` : emptyMini('Записей питания пока нет', 'Создайте первый прием пищи из раздела «Питание».')}
        ${lastAchievement ? `
          <a class="card accent" href="#/achievements/${encodeURIComponent(achievementId(lastAchievement))}">
            <span class="badge">${achievementIcon(lastAchievement)} Достижение</span>
            <h3>${escapeHtml(achievementTitle(lastAchievement))}</h3>
            <p class="muted">${escapeHtml(achievementDescription(lastAchievement))}</p>
          </a>` : emptyMini('Достижения появятся автоматически', 'Backend выдает их за тренировки и записи питания.')}
      </div>
    `;
  }

  function emptyMini(title, text) {
    return `
      <div class="card">
        <span class="badge">Пока пусто</span>
        <h3>${escapeHtml(title)}</h3>
        <p class="muted">${escapeHtml(text)}</p>
      </div>`;
  }

  function renderWorkoutRoute(parts) {
    if (state.error) {
      return pageHeader('Тренировки', 'Не удалось получить данные.') + alertHtml('error', state.error);
    }
    if (!state.workouts) {
      return pageHeader('Тренировки', 'Получаем список тренировок.') + skeletonGrid();
    }

    if (parts[1] === 'new') {
      return renderWorkoutForm();
    }

    if (parts[1] && parts[2] === 'edit') {
      const item = findById(state.workouts, parts[1]);
      return item
        ? renderWorkoutForm(item)
        : pageHeader('Редактирование', 'Запись не найдена.') + emptyState('Запись не найдена', 'Вернитесь к списку тренировок.', '#/workouts', 'К списку');
    }

    if (parts[1]) {
      return renderWorkoutDetails(findById(state.workouts, parts[1]));
    }

    const workouts = state.workouts || [];
    const totalMinutes = workouts.reduce((sum, item) => sum + Number(item.durationMinutes || 0), 0);

    return `
      ${flashHtml()}
      <section class="section-header">
        <div>
          <p class="eyebrow">Активность</p>
          <h1 style="font-size: clamp(38px, 5vw, 64px);">Тренировки</h1>
          <p>${workouts.length} записей · ${totalMinutes} минут суммарно.</p>
        </div>
        <a class="btn" href="#/workouts/new">Создать тренировку</a>
      </section>
      ${workouts.length ? `<div class="list">${workouts.map(workoutListItem).join('')}</div>` : emptyState('Тренировок пока нет', 'Добавьте первую тренировку, чтобы она появилась в профиле.', '#/workouts/new', 'Создать тренировку')}
    `;
  }

  function workoutListItem(item) {
    return `
      <a class="list-item" href="#/workouts/${encodeURIComponent(item.id)}">
        <div>
          <p class="item-title"><span class="badge">${workoutTypeLabel(item.type)}</span>${escapeHtml(item.title)}</p>
          <div class="item-meta">
            <span>${item.durationMinutes || 0} мин</span>
            <span>·</span>
            <span>${item.caloriesBurned ?? 0} ккал</span>
            <span>·</span>
            <span>${formatDateTime(item.workoutDate)}</span>
          </div>
        </div>
        <span class="btn ghost" aria-hidden="true">Открыть</span>
      </a>`;
  }

  function renderWorkoutDetails(item) {
    if (!item) {
      return pageHeader('Тренировка', 'Запись не найдена.') + emptyState('Запись не найдена', 'Вернитесь к списку и выберите существующую тренировку.', '#/workouts', 'К списку');
    }

    return `
      <section class="section-header">
        <div>
          <p class="eyebrow">Детали тренировки</p>
          <h1 style="font-size: clamp(34px, 5vw, 56px);">${escapeHtml(item.title)}</h1>
          <p>${workoutTypeLabel(item.type)} · ${formatDateTime(item.workoutDate)}</p>
        </div>
        <div class="btn-row">
          <a class="btn ghost" href="#/workouts">К списку</a>
          <a class="btn" href="#/workouts/new">Создать еще одну</a>
        </div>
      </section>
      <article class="detail-card">
        <div class="detail-grid">
          <div class="detail-cell"><strong>Тип</strong>${workoutTypeLabel(item.type)}</div>
          <div class="detail-cell"><strong>Длительность</strong>${item.durationMinutes || 0} минут</div>
          <div class="detail-cell"><strong>Калории</strong>${item.caloriesBurned ?? 0} ккал</div>
          <div class="detail-cell"><strong>Дата</strong>${formatDateTime(item.workoutDate)}</div>
          <div class="detail-cell" style="grid-column: 1 / -1;"><strong>Описание</strong>${escapeHtml(item.description || 'Описание не добавлено')}</div>
        </div>
        <div class="btn-row">
          <a class="btn ghost" href="#/workouts/${encodeURIComponent(item.id)}/edit">Редактировать</a>
          <button class="btn danger" type="button" data-delete-workout="${escapeHtml(item.id)}">Удалить</button>
        </div>
      </article>
    `;
  }

  function renderWorkoutForm(item = null) {
    const editing = Boolean(item);
    if (editing && !item) {
      return pageHeader('Редактирование', 'Запись не найдена.') + emptyState('Запись не найдена', 'Вернитесь к списку тренировок.', '#/workouts', 'К списку');
    }

    return `
      <section class="section-header">
        <div>
          <p class="eyebrow">${editing ? 'Редактирование' : 'Новая запись'}</p>
          <h1 style="font-size: clamp(34px, 5vw, 56px);">${editing ? 'Изменить тренировку' : 'Создать тренировку'}</h1>
          <p>После создания приложение вернет вас в профиль, а карточка тренировок обновится.</p>
        </div>
        <a class="btn ghost" href="#/workouts">К списку</a>
      </section>
      <form id="workout-form" class="form-card" data-id="${editing ? escapeHtml(item.id) : ''}">
        <div class="forms-grid">
          <div class="form-field full-width">
            <label for="workout-title">Название</label>
            <input id="workout-title" name="title" required maxlength="128" placeholder="Силовая тренировка" value="${escapeHtml(item?.title || '')}" />
          </div>
          <div class="form-field">
            <label for="workout-type">Тип</label>
            <select id="workout-type" name="type" required>
              ${workoutTypeOptions(item?.type)}
            </select>
          </div>
          <div class="form-field">
            <label for="workout-duration">Длительность, минут</label>
            <input id="workout-duration" name="durationMinutes" type="number" required min="1" value="${escapeHtml(item?.durationMinutes || '')}" />
          </div>
          <div class="form-field">
            <label for="workout-calories">Сожженные калории</label>
            <input id="workout-calories" name="caloriesBurned" type="number" min="0" value="${escapeHtml(item?.caloriesBurned ?? '')}" />
          </div>
          <div class="form-field full-width">
            <label for="workout-description">Описание</label>
            <textarea id="workout-description" name="description" maxlength="2000" placeholder="Жим, приседания, тяга">${escapeHtml(item?.description || '')}</textarea>
          </div>
          <div class="form-actions">
            <a class="btn ghost" href="#/workouts">Отмена</a>
            <button class="btn" type="submit">${editing ? 'Сохранить' : 'Создать'}</button>
          </div>
        </div>
      </form>
    `;
  }

  function renderFoodRoute(parts) {
    if (state.error) {
      return pageHeader('Питание', 'Не удалось получить данные.') + alertHtml('error', state.error);
    }
    if (!state.foodEntries) {
      return pageHeader('Питание', 'Получаем дневник питания.') + skeletonGrid();
    }

    if (parts[1] === 'new') {
      return renderFoodForm();
    }

    if (parts[1] && parts[2] === 'edit') {
      const item = findById(state.foodEntries, parts[1]);
      return item
        ? renderFoodForm(item)
        : pageHeader('Редактирование', 'Запись не найдена.') + emptyState('Запись не найдена', 'Вернитесь к дневнику питания.', '#/food', 'К списку');
    }

    if (parts[1]) {
      return renderFoodDetails(findById(state.foodEntries, parts[1]));
    }

    const entries = state.foodEntries || [];
    const calories = entries.reduce((sum, item) => sum + Number(item.calories || 0), 0);

    return `
      ${flashHtml()}
      <section class="section-header">
        <div>
          <p class="eyebrow">Питание</p>
          <h1 style="font-size: clamp(38px, 5vw, 64px);">Приемы пищи</h1>
          <p>${entries.length} записей · ${calories} ккал суммарно.</p>
        </div>
        <a class="btn" href="#/food/new">Создать прием пищи</a>
      </section>
      ${entries.length ? `<div class="list">${entries.map(foodListItem).join('')}</div>` : emptyState('Приемов пищи пока нет', 'Добавьте первую запись, чтобы увидеть ее в профиле.', '#/food/new', 'Создать прием пищи')}
    `;
  }

  function foodListItem(item) {
    return `
      <a class="list-item" href="#/food/${encodeURIComponent(item.id)}">
        <div>
          <p class="item-title"><span class="badge">${item.calories || 0} ккал</span>${escapeHtml(item.mealName)}</p>
          <div class="item-meta">
            <span>Б ${formatNumber(item.proteins)} г</span>
            <span>·</span>
            <span>Ж ${formatNumber(item.fats)} г</span>
            <span>·</span>
            <span>У ${formatNumber(item.carbohydrates)} г</span>
            <span>·</span>
            <span>${formatDateTime(item.mealDate)}</span>
          </div>
        </div>
        <span class="btn ghost" aria-hidden="true">Открыть</span>
      </a>`;
  }

  function renderFoodDetails(item) {
    if (!item) {
      return pageHeader('Прием пищи', 'Запись не найдена.') + emptyState('Запись не найдена', 'Вернитесь к списку и выберите существующий прием пищи.', '#/food', 'К списку');
    }

    return `
      <section class="section-header">
        <div>
          <p class="eyebrow">Детали питания</p>
          <h1 style="font-size: clamp(34px, 5vw, 56px);">${escapeHtml(item.mealName)}</h1>
          <p>${item.calories || 0} ккал · ${formatDateTime(item.mealDate)}</p>
        </div>
        <div class="btn-row">
          <a class="btn ghost" href="#/food">К списку</a>
          <a class="btn" href="#/food/new">Создать еще один</a>
        </div>
      </section>
      <article class="detail-card">
        <div class="detail-grid">
          <div class="detail-cell"><strong>Калории</strong>${item.calories || 0} ккал</div>
          <div class="detail-cell"><strong>Белки</strong>${formatNumber(item.proteins)} г</div>
          <div class="detail-cell"><strong>Жиры</strong>${formatNumber(item.fats)} г</div>
          <div class="detail-cell"><strong>Углеводы</strong>${formatNumber(item.carbohydrates)} г</div>
          <div class="detail-cell" style="grid-column: 1 / -1;"><strong>Дата приема пищи</strong>${formatDateTime(item.mealDate)}</div>
        </div>
        <div class="btn-row">
          <a class="btn ghost" href="#/food/${encodeURIComponent(item.id)}/edit">Редактировать</a>
          <button class="btn danger" type="button" data-delete-food="${escapeHtml(item.id)}">Удалить</button>
        </div>
      </article>
    `;
  }

  function renderFoodForm(item = null) {
    const editing = Boolean(item);
    if (editing && !item) {
      return pageHeader('Редактирование', 'Запись не найдена.') + emptyState('Запись не найдена', 'Вернитесь к дневнику питания.', '#/food', 'К списку');
    }

    return `
      <section class="section-header">
        <div>
          <p class="eyebrow">${editing ? 'Редактирование' : 'Новая запись'}</p>
          <h1 style="font-size: clamp(34px, 5vw, 56px);">${editing ? 'Изменить прием пищи' : 'Создать прием пищи'}</h1>
          <p>После создания приложение вернет вас в профиль, а карточка питания обновится.</p>
        </div>
        <a class="btn ghost" href="#/food">К списку</a>
      </section>
      <form id="food-form" class="form-card" data-id="${editing ? escapeHtml(item.id) : ''}">
        <div class="forms-grid">
          <div class="form-field full-width">
            <label for="food-name">Название блюда</label>
            <input id="food-name" name="mealName" required maxlength="128" placeholder="Овсянка с ягодами" value="${escapeHtml(item?.mealName || '')}" />
          </div>
          <div class="form-field">
            <label for="food-calories">Калории</label>
            <input id="food-calories" name="calories" type="number" required min="0" value="${escapeHtml(item?.calories ?? '')}" />
          </div>
          <div class="form-field">
            <label for="food-proteins">Белки, г</label>
            <input id="food-proteins" name="proteins" type="number" required min="0" step="0.01" value="${escapeHtml(item?.proteins ?? '')}" />
          </div>
          <div class="form-field">
            <label for="food-fats">Жиры, г</label>
            <input id="food-fats" name="fats" type="number" required min="0" step="0.01" value="${escapeHtml(item?.fats ?? '')}" />
          </div>
          <div class="form-field">
            <label for="food-carbs">Углеводы, г</label>
            <input id="food-carbs" name="carbohydrates" type="number" required min="0" step="0.01" value="${escapeHtml(item?.carbohydrates ?? '')}" />
          </div>
          <div class="form-field full-width">
            <label for="food-date">Дата приема пищи</label>
            <input id="food-date" name="mealDate" type="datetime-local" value="${escapeHtml(toDatetimeLocalValue(item?.mealDate) || toDatetimeLocalValue(new Date().toISOString()))}" />
          </div>
          <div class="form-actions">
            <a class="btn ghost" href="#/food">Отмена</a>
            <button class="btn" type="submit">${editing ? 'Сохранить' : 'Создать'}</button>
          </div>
        </div>
      </form>
    `;
  }


  function renderAchievementsRoute(parts) {
    if (state.error) {
      return pageHeader('Достижения', 'Не удалось получить данные.') + alertHtml('error', state.error);
    }
    if (!state.achievements) {
      return pageHeader('Достижения', 'Получаем список достижений пользователя.') + skeletonGrid();
    }

    if (parts[1]) {
      const item = findAchievementById(state.achievements, parts[1]);
      return renderAchievementDetails(item);
    }

    const achievements = state.achievements || [];
    const totalPoints = achievements.reduce((sum, item) => sum + Number(achievementPoints(item) || 0), 0);

    return `
      ${flashHtml()}
      <section class="section-header">
        <div>
          <p class="eyebrow">Прогресс</p>
          <h1 style="font-size: clamp(38px, 5vw, 64px);">Достижения</h1>
          <p>${achievements.length} достижений${totalPoints ? ` · ${totalPoints} очков` : ''}. Здесь можно демонстрировать бейджи пользователя.</p>
        </div>
        <div class="btn-row">
          <a class="btn" href="#/profile">В профиль</a>
        </div>
      </section>
      ${achievements.length ? `<div class="achievement-grid">${achievements.map(achievementListItem).join('')}</div>` : emptyState('Достижений пока нет', 'Frontend показывает только достижения, которые пришли от backend через /api/users/me/achievements.', '#/profile', 'В профиль')}
    `;
  }

  function achievementListItem(item) {
    return `
      <a class="card achievement-tile" href="#/achievements/${encodeURIComponent(achievementId(item))}">
        <div class="achievement-icon" aria-hidden="true">${achievementIcon(item)}</div>
        <div>
          <span class="badge">${escapeHtml(achievementCategory(item))}</span>
          <h3>${escapeHtml(achievementTitle(item))}</h3>
          <p class="muted">${escapeHtml(achievementDescription(item))}</p>
        </div>
        <div class="item-meta">
          <span>${formatDateTime(achievementEarnedAt(item))}</span>
          ${achievementPoints(item) ? `<span>·</span><span>${achievementPoints(item)} очков</span>` : ''}
        </div>
      </a>`;
  }

  function renderAchievementDetails(item) {
    if (!item) {
      return pageHeader('Достижение', 'Запись не найдена.') + emptyState('Достижение не найдено', 'Вернитесь к списку достижений.', '#/achievements', 'К списку');
    }

    return `
      <section class="section-header">
        <div>
          <p class="eyebrow">Детали достижения</p>
          <h1 style="font-size: clamp(34px, 5vw, 56px);">${escapeHtml(achievementTitle(item))}</h1>
          <p>${escapeHtml(achievementDescription(item))}</p>
        </div>
        <div class="btn-row">
          <button class="btn ghost" type="button" data-show-achievement="${escapeHtml(achievementId(item))}">Показать анимацию</button>
          <a class="btn" href="#/achievements">К списку</a>
        </div>
      </section>
      <article class="detail-card achievement-detail">
        <div class="achievement-icon large" aria-hidden="true">${achievementIcon(item)}</div>
        <div class="detail-grid">
          <div class="detail-cell"><strong>Категория</strong>${escapeHtml(achievementCategory(item))}</div>
          <div class="detail-cell"><strong>Получено</strong>${formatDateTime(achievementEarnedAt(item))}</div>
          <div class="detail-cell"><strong>Очки</strong>${achievementPoints(item) || '—'}</div>
          <div class="detail-cell"><strong>ID</strong>${escapeHtml(shortId(achievementId(item)))}</div>
        </div>
        <div class="btn-row">
          <button class="btn ghost" type="button" data-share-stub>Поделиться позже</button>
          <a class="btn" href="#/profile">В профиль</a>
        </div>
      </article>
    `;
  }

  function renderFeedPlaceholder() {
    return `
      <section class="section-header">
        <div>
          <p class="eyebrow">Заглушка навигации</p>
          <h1 style="font-size: clamp(38px, 5vw, 64px);">Лента постов</h1>
          <p>Раздел подготовлен как точка входа. Сейчас backend проекта содержит текущие CRUD-сценарии для профиля, тренировок, питания и достижений.</p>
        </div>
        <a class="btn" href="#/profile">Вернуться в профиль</a>
      </section>
      <section class="feed-placeholder">
        <div class="placeholder-block">
          <h2>Будущая лента активностей</h2>
          <p class="muted">Здесь можно будет показывать посты по интересам, рецепты, тренировки и комментарии, когда соответствующие endpoint'ы будут реализованы.</p>
          <div class="btn-row">
            <a class="btn ghost" href="#/workouts">Поделиться тренировкой позже</a>
            <a class="btn ghost" href="#/food">Поделиться рецептом позже</a>
          </div>
        </div>
        <div class="placeholder-block" style="background: var(--yellow); border-style: solid;">
          <h3>Меню уже готово</h3>
          <p>Кнопка в навигации ведет сюда и может быть подключена к реальной ленте без изменения структуры приложения.</p>
        </div>
      </section>
    `;
  }

  function renderNotFound() {
    return pageHeader('Страница не найдена', 'Такого раздела пока нет.') + emptyState('404', 'Вернитесь в профиль или выберите раздел в меню.', '#/profile', 'В профиль');
  }

  function pageHeader(title, description) {
    return `
      <section class="section-header">
        <div>
          <p class="eyebrow">Т-Здоровье</p>
          <h1 style="font-size: clamp(38px, 5vw, 64px);">${escapeHtml(title)}</h1>
          <p>${escapeHtml(description)}</p>
        </div>
      </section>`;
  }

  function skeletonGrid() {
    return `
      <div class="cards-grid">
        <div class="skeleton"></div>
        <div class="skeleton"></div>
      </div>`;
  }

  function emptyState(title, text, href, cta) {
    return `
      <div class="empty-state">
        <h2>${escapeHtml(title)}</h2>
        <p class="muted">${escapeHtml(text)}</p>
        <a class="btn" href="${escapeHtml(href)}">${escapeHtml(cta)}</a>
      </div>`;
  }

  function alertHtml(type, message) {
    return `<div class="alert ${escapeHtml(type)}" role="alert"><strong>${type === 'error' ? 'Ошибка' : 'Готово'}</strong><span>${escapeHtml(message)}</span></div>`;
  }

  function flashHtml() {
    if (!state.flash) {
      return '';
    }
    const message = state.flash;
    state.flash = null;
    return alertHtml('success', message);
  }

  function logoHtml() {
    return `<span class="logo-mark" aria-hidden="true"><span class="logo-shield">T</span></span>`;
  }

  function bindGlobalActions() {
    app.querySelectorAll('[data-auth]').forEach((button) => {
      button.addEventListener('click', () => startAuth(button.dataset.auth));
    });

    app.querySelectorAll('[data-logout]').forEach((button) => {
      button.addEventListener('click', logout);
    });

    app.querySelectorAll('[data-reload]').forEach((button) => {
      button.addEventListener('click', () => loadDashboard(true));
    });

    app.querySelectorAll('[data-accept-achievement]').forEach((button) => {
      button.addEventListener('click', () => {
        showNextAchievementOrClose();
        renderRoute(false);
      });
    });

    app.querySelectorAll('[data-share-achievement]').forEach((button) => {
      button.addEventListener('click', () => {
        state.achievementShareNotice = 'Скоро здесь будет публикация достижения в ленту. Пока это демонстрационная заглушка.';
        renderRoute(false);
      });
    });

    app.querySelectorAll('[data-share-stub]').forEach((button) => {
      button.addEventListener('click', () => {
        state.flash = 'Поделиться достижением можно будет после подключения ленты постов.';
        renderRoute(false);
      });
    });


    app.querySelectorAll('[data-show-achievement]').forEach((button) => {
      button.addEventListener('click', () => {
        const item = findAchievementById(state.achievements || [], button.dataset.showAchievement);
        if (!item) {
          state.flash = 'Достижение не найдено в данных backend.';
        } else {
          showAchievementModal(item);
        }
        renderRoute(false);
      });
    });

    app.querySelectorAll('[data-delete-workout]').forEach((button) => {
      button.addEventListener('click', async () => {
        if (!confirm('Удалить эту тренировку?')) return;
        await deleteResource('/api/workouts/' + encodeURIComponent(button.dataset.deleteWorkout), 'Тренировка удалена.');
      });
    });

    app.querySelectorAll('[data-delete-food]').forEach((button) => {
      button.addEventListener('click', async () => {
        if (!confirm('Удалить этот прием пищи?')) return;
        await deleteResource('/api/food-entries/' + encodeURIComponent(button.dataset.deleteFood), 'Прием пищи удален.');
      });
    });
  }

  function bindForms() {
    const workoutForm = document.getElementById('workout-form');
    if (workoutForm) {
      workoutForm.addEventListener('submit', submitWorkout);
    }

    const foodForm = document.getElementById('food-form');
    if (foodForm) {
      foodForm.addEventListener('submit', submitFood);
    }
  }

  async function deleteResource(path, message) {
    try {
      await apiFetch(path, { method: 'DELETE' });
      state.flash = message;
      await loadDashboard(true);
      navigate('/profile');
    } catch (error) {
      state.error = friendlyError(error);
      renderRoute(false);
    }
  }

  async function submitWorkout(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const formData = new FormData(form);
    const id = form.dataset.id;
    const payload = {
      title: stringValue(formData.get('title')),
      type: stringValue(formData.get('type')),
      description: stringValue(formData.get('description')),
      durationMinutes: toInt(formData.get('durationMinutes')),
    };
    const calories = toIntOrNull(formData.get('caloriesBurned'));
    if (calories !== null) {
      payload.caloriesBurned = calories;
    }

    try {
      disableForm(form, true);
      const previousAchievementIds = achievementIdSet(state.achievements || []);
      await apiFetch(id ? `/api/workouts/${encodeURIComponent(id)}` : '/api/workouts', {
        method: id ? 'PATCH' : 'POST',
        body: JSON.stringify(payload),
      });
      state.flash = id ? 'Тренировка обновлена.' : 'Тренировка создана и добавлена в профиль.';
      await loadDashboard(true, {
        animateNewAchievements: true,
        previousAchievementIds,
      });
      navigate('/profile');
    } catch (error) {
      state.error = friendlyError(error);
      renderProtected(renderWorkoutForm(id ? findById(state.workouts, id) : null) + alertHtml('error', state.error));
    } finally {
      disableForm(form, false);
    }
  }

  async function submitFood(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const formData = new FormData(form);
    const id = form.dataset.id;
    const payload = {
      mealName: stringValue(formData.get('mealName')),
      calories: toInt(formData.get('calories')),
      proteins: toNumber(formData.get('proteins')),
      fats: toNumber(formData.get('fats')),
      carbohydrates: toNumber(formData.get('carbohydrates')),
      mealDate: stringValue(formData.get('mealDate')) || toDatetimeLocalValue(new Date().toISOString()),
    };

    try {
      disableForm(form, true);
      const previousAchievementIds = achievementIdSet(state.achievements || []);
      await apiFetch(id ? `/api/food-entries/${encodeURIComponent(id)}` : '/api/food-entries', {
        method: id ? 'PATCH' : 'POST',
        body: JSON.stringify(payload),
      });
      state.flash = id ? 'Прием пищи обновлен.' : 'Прием пищи создан и добавлен в профиль.';
      await loadDashboard(true, {
        animateNewAchievements: true,
        previousAchievementIds,
      });
      navigate('/profile');
    } catch (error) {
      state.error = friendlyError(error);
      renderProtected(renderFoodForm(id ? findById(state.foodEntries, id) : null) + alertHtml('error', state.error));
    } finally {
      disableForm(form, false);
    }
  }

  function disableForm(form, disabled) {
    form.querySelectorAll('button, input, textarea, select, a').forEach((element) => {
      if ('disabled' in element) {
        element.disabled = disabled;
      }
    });
  }

  function createCodeVerifier() {
    return cryptoRandomString(64);
  }

  async function createCodeChallenge(verifier) {
    const bytes = new TextEncoder().encode(verifier);
    const digest = await crypto.subtle.digest('SHA-256', bytes);
    return base64UrlEncode(new Uint8Array(digest));
  }

  function cryptoRandomString(length) {
    const charset = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~';
    const array = new Uint8Array(length);
    crypto.getRandomValues(array);
    return Array.from(array, (byte) => charset[byte % charset.length]).join('');
  }

  function base64UrlEncode(bytes) {
    let binary = '';
    bytes.forEach((byte) => {
      binary += String.fromCharCode(byte);
    });
    return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  }


  function achievementModalHtml() {
    if (!state.achievementModal) {
      return '';
    }

    const item = state.achievementModal;
    return `
      <div class="achievement-overlay" role="dialog" aria-modal="true" aria-labelledby="achievement-title">
        <div class="confetti-layer" aria-hidden="true">
          <span></span><span></span><span></span><span></span><span></span><span></span>
          <span></span><span></span><span></span><span></span><span></span><span></span>
        </div>
        <section class="achievement-modal">
          <div class="achievement-burst" aria-hidden="true">
            <span>${achievementIcon(item)}</span>
          </div>
          <p class="eyebrow">${state.achievementQueueTotal > 1 ? `Новое достижение · ${state.achievementQueueIndex} из ${state.achievementQueueTotal}` : 'Новое достижение'}</p>
          <h2 id="achievement-title">${escapeHtml(achievementTitle(item))}</h2>
          <p class="lead">${escapeHtml(achievementDescription(item))}</p>
          <div class="achievement-meta-row">
            <span class="badge">${escapeHtml(achievementCategory(item))}</span>
            <span class="badge">${formatDateTime(achievementEarnedAt(item))}</span>
          </div>
          ${state.achievementShareNotice ? alertHtml('success', state.achievementShareNotice) : ''}
          <div class="btn-row achievement-actions">
            <button class="btn" type="button" data-accept-achievement>Принять</button>
            <button class="btn ghost" type="button" data-share-achievement>Поделиться</button>
          </div>
        </section>
      </div>`;
  }

  async function fetchAchievements() {
    try {
      const achievements = await apiFetch('/api/users/me/achievements');
      return Array.isArray(achievements) ? achievements : [];
    } catch {
      return [];
    }
  }

  function normalizeAchievements(items) {
    return [...(items || [])].sort((a, b) => new Date(achievementEarnedAt(b)).getTime() - new Date(achievementEarnedAt(a)).getTime());
  }

  function achievementIdSet(collection) {
    return new Set((collection || []).map(achievementId).filter(Boolean));
  }

  function seenAchievementsStorageKey() {
    const userKey = state.user?.id || state.user?.userId || state.user?.keycloakId || state.user?.username || state.user?.email || 'anonymous';
    return `${STORAGE.seenAchievements}:${userKey}`;
  }

  function readSeenAchievementIds() {
    try {
      const data = JSON.parse(localStorage.getItem(seenAchievementsStorageKey()) || '[]');
      return new Set(Array.isArray(data) ? data.map(String) : []);
    } catch {
      return new Set();
    }
  }

  function markAchievementsSeen(collection) {
    const ids = achievementIdSet(collection);
    if (!ids.size) {
      return;
    }

    const seen = readSeenAchievementIds();
    ids.forEach((id) => seen.add(id));
    localStorage.setItem(seenAchievementsStorageKey(), JSON.stringify([...seen]));
  }

  function findNewAchievements(collection, previousIds = new Set()) {
    const seen = readSeenAchievementIds();
    return (collection || []).filter((item) => {
      const id = achievementId(item);
      return id && !previousIds.has(id) && !seen.has(id);
    });
  }

  function showAchievementQueue(items) {
    const list = (Array.isArray(items) ? items : [items]).filter(Boolean);
    if (!list.length) {
      return;
    }

    state.achievementModal = list[0];
    state.achievementQueue = list.slice(1);
    state.achievementQueueTotal = list.length;
    state.achievementQueueIndex = 1;
    state.achievementShareNotice = null;
  }

  function showAchievementModal(item) {
    showAchievementQueue(item ? [item] : []);
  }

  function showNextAchievementOrClose() {
    state.achievementShareNotice = null;

    const next = state.achievementQueue.shift();
    if (next) {
      state.achievementModal = next;
      state.achievementQueueIndex += 1;
      return;
    }

    state.achievementModal = null;
    state.achievementQueue = [];
    state.achievementQueueTotal = 0;
    state.achievementQueueIndex = 0;
    navigate('/profile');
  }

  function achievementId(item) {
    const source = item?.achievement || item || {};
    return String(item?.id || item?.achievementId || source.id || source.code || source.title || 'achievement');
  }

  function achievementTitle(item) {
    const source = item?.achievement || item || {};
    return source.title || source.name || item?.title || item?.name || 'Новое достижение';
  }

  function achievementDescription(item) {
    const source = item?.achievement || item || {};
    return source.description || item?.description || 'Описание достижения появится здесь.';
  }

  function achievementCategory(item) {
    const source = item?.achievement || item || {};
    return source.category || source.type || item?.category || item?.type || 'Достижение';
  }

  function achievementPoints(item) {
    const source = item?.achievement || item || {};
    return Number(source.points ?? item?.points ?? 0);
  }

  function achievementEarnedAt(item) {
    const source = item?.achievement || item || {};
    return item?.earnedAt || item?.awardedAt || item?.createdAt || source.earnedAt || source.createdAt || new Date().toISOString();
  }

  function achievementIcon(item) {
    const source = item?.achievement || item || {};
    if (source.icon || item?.icon) {
      return source.icon || item.icon;
    }
    const text = `${achievementTitle(item)} ${achievementCategory(item)}`.toLowerCase();
    if (text.includes('трен')) return '🏃';
    if (text.includes('питан') || text.includes('еда') || text.includes('калор')) return '🥗';
    if (text.includes('рег') || text.includes('перв') || text.includes('добро')) return '🏅';
    return '⭐';
  }

  function achievementProgressLabel(achievements) {
    const count = achievements.length;
    if (!count) return 'бейджи появятся после действий';
    const points = achievements.reduce((sum, item) => sum + achievementPoints(item), 0);
    return points ? `${points} очков прогресса` : 'полученные бейджи';
  }

  function findAchievementById(collection, id) {
    return (collection || []).find((item) => String(achievementId(item)) === String(id));
  }

  function workoutTypeOptions(value) {
    return [
      ['CARDIO', 'Кардио'],
      ['STRENGTH', 'Силовая'],
      ['STRETCHING', 'Растяжка'],
    ].map(([key, label]) => `<option value="${key}"${value === key ? ' selected' : ''}>${label}</option>`).join('');
  }

  function workoutTypeLabel(value) {
    const labels = {
      CARDIO: 'Кардио',
      STRENGTH: 'Силовая',
      STRETCHING: 'Растяжка',
    };
    return labels[value] || value || 'Тип не указан';
  }

  function findById(collection, id) {
    return (collection || []).find((item) => String(item.id) === String(id));
  }

  function formatDateTime(value) {
    if (!value) return 'Дата не указана';
    const normalized = typeof value === 'string' && !value.endsWith('Z') ? value : String(value);
    const date = new Date(normalized);
    if (Number.isNaN(date.getTime())) return value;
    return new Intl.DateTimeFormat('ru-RU', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(date);
  }

  function toDatetimeLocalValue(value) {
    if (!value) return '';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return String(value).slice(0, 16);
    }
    const pad = (number) => String(number).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
  }

  function getInitials(user) {
    const source = [user?.firstName, user?.lastName].filter(Boolean).join(' ') || user?.username || 'T';
    return source
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0])
      .join('')
      .toUpperCase();
  }

  function shortId(value) {
    if (!value) return '—';
    const text = String(value);
    return `${text.slice(0, 8)}…${text.slice(-4)}`;
  }

  function stringValue(value) {
    return String(value ?? '').trim();
  }

  function toInt(value) {
    const number = Number.parseInt(String(value), 10);
    return Number.isFinite(number) ? number : 0;
  }

  function toIntOrNull(value) {
    const raw = stringValue(value);
    if (!raw) return null;
    const number = Number.parseInt(raw, 10);
    return Number.isFinite(number) ? number : null;
  }

  function toNumber(value) {
    const number = Number.parseFloat(String(value).replace(',', '.'));
    return Number.isFinite(number) ? number : 0;
  }

  function formatNumber(value) {
    const number = Number(value || 0);
    return new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 2 }).format(number);
  }

  function escapeHtml(value) {
    return String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  function friendlyError(error) {
    if (error instanceof AuthRequiredError) {
      return 'Сессия завершилась. Войдите заново через Keycloak.';
    }
    return error?.message || 'Что-то пошло не так.';
  }

  class AuthRequiredError extends Error {
    constructor() {
      super('Требуется авторизация.');
      this.name = 'AuthRequiredError';
    }
  }
})();
