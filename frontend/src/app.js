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
    foodDate: 'tHealthFoodDate',
    feedType: 'tHealthFeedType',
  };

  const POST_FILTER_TYPES = [
    ['TEXT', 'Текстовые'],
    ['WORKOUT', 'Тренировки'],
    ['RECIPE', 'Рецепты'],
    ['ACHIEVEMENT', 'Достижения'],
  ];

  const POST_CREATE_OPTIONS = [
    ['text', 'Написать текстовый пост', 'Короткая заметка без привязки к тренировке или рецепту.', '✍️'],
    ['workout', 'Создать тренировку и опубликовать', 'Новая тренировка сразу появится в публичной ленте.', '🏃'],
    ['recipe', 'Создать рецепт и опубликовать', 'Новый рецепт будет сохранен и опубликован в ленте.', '🍳'],
  ];

  const state = {
    user: null,
    workouts: null,
    foodEntries: null,
    dailyFood: null,
    foodDate: localStorage.getItem(STORAGE.foodDate) || todayISODate(),
    achievements: null,
    recipes: null,
    feedPosts: null,
    feedTypes: readInitialFeedTypes(),
    loading: false,
    feedLoading: false,
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
    if (state.loading) return;

    const hasData = state.user && state.workouts && state.foodEntries && state.dailyFood && state.achievements && state.recipes;
    if (hasData && !force) return;

    state.loading = true;
    state.error = null;
    renderRoute(false);

    try {
      state.user = await apiFetch('/api/users/me');
      const previousAchievementIds = options.previousAchievementIds || achievementIdSet(state.achievements || []);
      const [workouts, foodEntries, dailyFood, achievements, recipes] = await Promise.all([
        apiFetch('/api/workouts'),
        apiFetch('/api/food-entries'),
        fetchDailyFood(state.foodDate),
        fetchAchievements(),
        fetchRecipes(),
      ]);

      state.workouts = sortByDate(Array.isArray(workouts) ? workouts : [], 'workoutDate');
      state.foodEntries = sortByDate(Array.isArray(foodEntries) ? foodEntries : [], 'mealDate');
      state.dailyFood = dailyFood;
      state.achievements = normalizeAchievements(Array.isArray(achievements) ? achievements : []);
      state.recipes = sortByDate(Array.isArray(recipes) ? recipes : [], 'createdAt');

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

  async function loadDailyFood(date = state.foodDate, force = false) {
    if (state.dailyFood && state.dailyFood.date === date && !force) return;
    state.foodDate = date;
    localStorage.setItem(STORAGE.foodDate, date);
    try {
      state.dailyFood = await fetchDailyFood(date);
      const allEntries = await apiFetch('/api/food-entries');
      state.foodEntries = sortByDate(Array.isArray(allEntries) ? allEntries : [], 'mealDate');
    } catch (error) {
      state.error = friendlyError(error);
    }
    renderRoute(false);
  }

  async function fetchDailyFood(date) {
    try {
      const result = await apiFetch(`/api/food-entries/daily?date=${encodeURIComponent(date)}`);
      return normalizeDailyFood(result, date);
    } catch (error) {
      const entries = state.foodEntries || await apiFetch('/api/food-entries');
      return buildDailyFoodFromEntries(entries || [], date);
    }
  }

  async function fetchAchievements() {
    try {
      const achievements = await apiFetch('/api/users/me/achievements');
      return Array.isArray(achievements) ? achievements : [];
    } catch {
      return [];
    }
  }

  async function fetchRecipes() {
    try {
      const recipes = await apiFetch('/api/recipes');
      return Array.isArray(recipes) ? recipes : [];
    } catch {
      return [];
    }
  }

  async function loadFeed(force = false) {
    if (state.feedLoading) return;
    if (state.feedPosts && !force) return;

    state.feedLoading = true;
    state.error = null;
    renderRoute(false);

    try {
      const selectedTypes = normalizedFeedTypes(state.feedTypes);
      let posts = [];

      if (!selectedTypes.length) {
        posts = await apiFetch('/api/posts/feed');
      } else if (selectedTypes.length === 1) {
        posts = await apiFetch(`/api/posts/feed?type=${encodeURIComponent(selectedTypes[0])}`);
      } else {
        const groups = await Promise.all(
          selectedTypes.map((type) => apiFetch(`/api/posts/feed?type=${encodeURIComponent(type)}`))
        );
        posts = uniquePostsById(groups.flat());
      }

      state.feedPosts = sortByDate(Array.isArray(posts) ? posts : [], 'createdAt');
    } catch (error) {
      if (error instanceof AuthRequiredError) {
        navigate('/login');
        return;
      }
      state.error = friendlyError(error);
    } finally {
      state.feedLoading = false;
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
    state.dailyFood = null;
    state.achievements = null;
    state.recipes = null;
    state.feedPosts = null;
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
    if (scrollTop) window.scrollTo({ top: 0, behavior: 'smooth' });

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
      if (!state.user || !state.workouts || !state.foodEntries || !state.dailyFood || !state.achievements || !state.recipes) loadDashboard();
      return;
    }

    if (parts[0] === 'workouts') {
      renderProtected(renderWorkoutRoute(parts));
      if (!state.workouts) loadDashboard();
      return;
    }

    if (parts[0] === 'food') {
      renderProtected(renderFoodRoute(parts));
      if (!state.foodEntries || !state.dailyFood) loadDashboard();
      return;
    }

    if (parts[0] === 'recipes') {
      renderProtected(renderRecipesRoute(parts));
      if (!state.recipes) loadDashboard();
      return;
    }

    if (parts[0] === 'achievements') {
      renderProtected(renderAchievementsRoute(parts));
      if (!state.achievements) loadDashboard();
      return;
    }

    if (parts[0] === 'feed') {
      renderProtected(renderFeedRoute(parts));
      if (!state.feedPosts && !isPostFormRoute(parts)) loadFeed();
      if ((isPostFormRoute(parts) || parts[1] === 'new') && (!state.workouts || !state.recipes || !state.achievements)) loadDashboard();
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
              <h1>Дневник активности, питания и рецептов</h1>
              <p class="lead">Войдите через Keycloak, чтобы backend создал локальный профиль, а вы могли вести тренировки, КБЖУ, рецепты и делиться активностями в ленте.</p>
              <div class="hero-grid">
                <div class="mini-card"><span class="mini-icon">🏃</span><strong>Тренировки</strong><span class="muted">Личные записи и публикации</span></div>
                <div class="mini-card"><span class="mini-icon">🥗</span><strong>КБЖУ</strong><span class="muted">Food entries по дням</span></div>
                <div class="mini-card"><span class="mini-icon">📰</span><strong>Лента</strong><span class="muted">Посты, рецепты, достижения</span></div>
              </div>
            </div>
            <p class="footer-note">Frontend использует OAuth2 Authorization Code + PKCE и отправляет JWT в API как Bearer-токен.</p>
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
            ${navLink('/feed', 'Лента', route)}
            ${navLink('/workouts', 'Тренировки', route)}
            ${navLink('/food', 'КБЖУ', route)}
            ${navLink('/recipes', 'Рецепты', route)}
            ${navLink('/achievements', 'Достижения', route)}
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
    if (state.error) return pageHeader('Профиль', 'Не удалось получить данные.') + alertHtml('error', state.error);
    if (!state.user || !state.workouts || !state.foodEntries || !state.dailyFood || !state.recipes) {
      return pageHeader('Профиль', 'Подгружаем профиль, тренировки, КБЖУ, рецепты и достижения.') + skeletonGrid();
    }

    const user = state.user || {};
    const workouts = state.workouts || [];
    const foodEntries = state.foodEntries || [];
    const daily = state.dailyFood || emptyDailyFood(state.foodDate);
    const achievements = state.achievements || [];
    const recipes = state.recipes || [];
    const workoutMinutes = workouts.reduce((sum, item) => sum + Number(item.durationMinutes || 0), 0);
    const workoutCalories = workouts.reduce((sum, item) => sum + Number(item.caloriesBurned || 0), 0);

    return `
      ${flashHtml()}
      <section class="section-header profile-header">
        <div>
          <p class="eyebrow">Личный кабинет</p>
          <h1 style="font-size: clamp(38px, 5vw, 64px);">Профиль</h1>
          <p>Короткая сводка и быстрый переход к разделам.</p>
        </div>
        <span class="status-pill">${achievements.length} достижений</span>
      </section>
      <section class="dashboard profile-dashboard">
        <aside class="card profile-card profile-card-clean">
          <div class="avatar" aria-label="Фото профиля">${escapeHtml(getInitials(user))}</div>
          <div class="profile-main-info">
            <h2>${escapeHtml(user.username || 'Пользователь')}</h2>
            <p class="muted">${escapeHtml(user.email || 'email из Keycloak')}</p>
          </div>
          <div class="meta-list compact-meta">
            <div class="meta-item"><span class="meta-label">Имя</span><strong>${escapeHtml(user.firstName || '—')}</strong></div>
            <div class="meta-item"><span class="meta-label">Фамилия</span><strong>${escapeHtml(user.lastName || '—')}</strong></div>
            <div class="meta-item"><span class="meta-label">ID</span><strong>${escapeHtml(shortId(user.id))}</strong></div>
          </div>
        </aside>
        <div class="cards-grid profile-grid profile-cards-readable">
          <a class="card accent profile-action-card" href="#/workouts" aria-label="Все тренировки">
            <span class="badge">🏃 Тренировки</span>
            <h3>Личные тренировки</h3>
            <div class="metric-row"><span class="metric">${workouts.length}<small> шт.</small></span><span>${workoutMinutes} мин</span></div>
            <div class="stat-pills"><span>${workoutCalories} ккал</span><span>можно делиться</span></div>
          </a>
          <a class="card profile-action-card" href="#/food" aria-label="Food entries по дням">
            <span class="badge">🥗 КБЖУ</span>
            <h3>${formatDateShort(daily.date)}</h3>
            <div class="metric-row"><span class="metric">${daily.entries.length}<small> приемов</small></span></div>
            ${macroPills(daily)}
            <p class="muted microcopy">Личный дневник, без публикации в ленте.</p>
          </a>
          <a class="card profile-action-card" href="#/recipes" aria-label="Все рецепты">
            <span class="badge">🍳 Рецепты</span>
            <h3>Мои рецепты</h3>
            <div class="metric-row"><span class="metric">${recipes.length}<small> шт.</small></span></div>
            <div class="stat-pills"><span>публикация в ленте</span><span>отдельно от КБЖУ</span></div>
          </a>
          <a class="card achievement-card profile-action-card" href="#/achievements" aria-label="Все достижения">
            <span class="badge">🏅 Достижения</span>
            <h3>Витрина</h3>
            <div class="metric-row"><span class="metric">${achievements.length}<small> шт.</small></span></div>
            <div class="stat-pills"><span>${achievementProgressLabel(achievements)}</span><span>можно делиться</span></div>
          </a>
        </div>
      </section>
      ${renderRecentBlock(workouts, foodEntries, recipes, achievements)}
    `;
  }

  function renderRecentBlock(workouts, foodEntries, recipes, achievements) {
    const lastWorkout = workouts[0];
    const lastFood = foodEntries[0];
    const lastRecipe = recipes[0];
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
            <p class="muted">${workoutTypeLabel(lastWorkout.type)} · ${lastWorkout.durationMinutes || 0} мин</p>
          </a>` : emptyMini('Тренировок пока нет', 'Создайте первую тренировку из раздела «Тренировки».')}
        ${lastFood ? `
          <a class="card" href="#/food/${encodeURIComponent(lastFood.id)}">
            <span class="badge">Последний прием пищи</span>
            <h3>${escapeHtml(lastFood.mealName)}</h3>
            <p class="muted">${lastFood.calories || 0} ккал · ${formatDateTime(lastFood.mealDate)}</p>
          </a>` : emptyMini('Записей питания пока нет', 'Создайте первый прием пищи из раздела «КБЖУ».')}
        ${lastRecipe ? `
          <a class="card" href="#/recipes/${encodeURIComponent(lastRecipe.id)}">
            <span class="badge">Последний рецепт</span>
            <h3>${escapeHtml(lastRecipe.title)}</h3>
            <p class="muted">К=${lastRecipe.calories || 0}, Б=${formatNumber(lastRecipe.proteins)}, Ж=${formatNumber(lastRecipe.fats)}, У=${formatNumber(lastRecipe.carbohydrates)}</p>
          </a>` : emptyMini('Рецептов пока нет', 'Создайте рецепт и поделитесь им в ленте.')}
        ${lastAchievement ? `
          <a class="card accent" href="#/achievements/${encodeURIComponent(achievementId(lastAchievement))}">
            <span class="badge">${achievementIcon(lastAchievement)} Достижение</span>
            <h3>${escapeHtml(achievementTitle(lastAchievement))}</h3>
            <p class="muted">${escapeHtml(achievementDescription(lastAchievement))}</p>
          </a>` : emptyMini('Достижения появятся автоматически', 'Backend выдает их за действия пользователя.')}
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
    if (state.error) return pageHeader('Тренировки', 'Не удалось получить данные.') + alertHtml('error', state.error);
    if (!state.workouts) return pageHeader('Тренировки', 'Получаем список тренировок.') + skeletonGrid();

    if (parts[1] === 'new') return renderWorkoutForm();

    if (parts[1] && parts[2] === 'edit') {
      const item = findById(state.workouts, parts[1]);
      return item ? renderWorkoutForm(item) : pageHeader('Редактирование', 'Запись не найдена.') + emptyState('Запись не найдена', 'Вернитесь к списку тренировок.', '#/workouts', 'К списку');
    }

    if (parts[1]) return renderWorkoutDetails(findById(state.workouts, parts[1]));

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
      <div class="list-item">
        <a href="#/workouts/${encodeURIComponent(item.id)}">
          <p class="item-title"><span class="badge">${workoutTypeLabel(item.type)}</span>${escapeHtml(item.title)}</p>
          <div class="item-meta">
            <span>${item.durationMinutes || 0} мин</span><span>·</span><span>${item.caloriesBurned ?? 0} ккал</span>
          </div>
        </a>
        <div class="btn-row compact-actions">
          <button class="btn ghost" type="button" data-share-workout="${escapeHtml(item.id)}">Поделиться</button>
          <a class="btn ghost" href="#/workouts/${encodeURIComponent(item.id)}">Открыть</a>
        </div>
      </div>`;
  }

  function renderWorkoutDetails(item) {
    if (!item) return pageHeader('Тренировка', 'Запись не найдена.') + emptyState('Запись не найдена', 'Вернитесь к списку и выберите существующую тренировку.', '#/workouts', 'К списку');

    return `
      <section class="section-header">
        <div>
          <p class="eyebrow">Детали тренировки</p>
          <h1 style="font-size: clamp(34px, 5vw, 56px);">${escapeHtml(item.title)}</h1>
          <p>${workoutTypeLabel(item.type)} · личная запись, которой можно поделиться в ленте.</p>
        </div>
        <div class="btn-row">
          <button class="btn" type="button" data-share-workout="${escapeHtml(item.id)}">Поделиться</button>
          <a class="btn ghost" href="#/workouts">К списку</a>
        </div>
      </section>
      <article class="detail-card">
        <div class="detail-grid">
          <div class="detail-cell"><strong>Тип</strong>${workoutTypeLabel(item.type)}</div>
          <div class="detail-cell"><strong>Длительность</strong>${item.durationMinutes || 0} минут</div>
          <div class="detail-cell"><strong>Калории</strong>${item.caloriesBurned ?? 0} ккал</div>
          <div class="detail-cell"><strong>ID</strong>${escapeHtml(shortId(item.id))}</div>
          <div class="detail-cell" style="grid-column: 1 / -1;"><strong>Описание</strong>${escapeHtml(item.description || 'Описание не добавлено')}</div>
        </div>
        <div class="btn-row">
          <a class="btn ghost" href="#/workouts/${encodeURIComponent(item.id)}/edit">Редактировать</a>
          <button class="btn danger" type="button" data-delete-workout="${escapeHtml(item.id)}">Удалить</button>
          <a class="btn" href="#/workouts/new">Создать еще одну</a>
        </div>
      </article>
    `;
  }

  function renderWorkoutForm(item = null) {
    const editing = Boolean(item);
    return `
      <section class="section-header">
        <div>
          <p class="eyebrow">${editing ? 'Редактирование' : 'Новая запись'}</p>
          <h1 style="font-size: clamp(34px, 5vw, 56px);">${editing ? 'Изменить тренировку' : 'Создать тренировку'}</h1>
          <p>Тренировка остается личной, пока вы не нажмете «Поделиться».</p>
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
            <select id="workout-type" name="type" required>${workoutTypeOptions(item?.type)}</select>
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
    if (state.error) return pageHeader('КБЖУ', 'Не удалось получить данные.') + alertHtml('error', state.error);
    if (!state.foodEntries || !state.dailyFood) return pageHeader('КБЖУ', 'Получаем дневник питания.') + skeletonGrid();

    if (parts[1] === 'new') return renderFoodForm();

    if (parts[1] && parts[2] === 'edit') {
      const item = findById(state.foodEntries, parts[1]);
      return item ? renderFoodForm(item) : pageHeader('Редактирование', 'Запись не найдена.') + emptyState('Запись не найдена', 'Вернитесь к дневнику КБЖУ.', '#/food', 'К списку');
    }

    if (parts[1]) return renderFoodDetails(findById(state.foodEntries, parts[1]));

    const daily = state.dailyFood || emptyDailyFood(state.foodDate);

    return `
      ${flashHtml()}
      <section class="section-header">
        <div>
          <p class="eyebrow">Питание</p>
          <h1 style="font-size: clamp(38px, 5vw, 64px);">Дневник КБЖУ</h1>
          <p>Личные приемы пищи по датам. Food entries не публикуются в ленте.</p>
        </div>
        <a class="btn" href="#/food/new">Добавить прием пищи</a>
      </section>
      <section class="daily-panel daily-panel-readable">
        <div class="date-toolbar">
          <button class="btn ghost" type="button" data-food-date-action="prev">← Предыдущий день</button>
          <label class="date-field">
            <span>Выбранная дата</span>
            <input type="date" value="${escapeHtml(daily.date)}" data-food-date-input />
          </label>
          <button class="btn ghost" type="button" data-food-date-action="next">Следующий день →</button>
          <button class="btn" type="button" data-food-date-action="today">Сегодня</button>
        </div>
        <div class="daily-headline">
          <div>
            <strong>${formatDateShort(daily.date)}</strong>
            <span>${daily.entries.length} ${plural(daily.entries.length, 'прием пищи', 'приема пищи', 'приемов пищи')}</span>
          </div>
          <div class="daily-total-calories">${daily.totalCalories}<small> ккал</small></div>
        </div>
        ${nutritionSummary(daily)}
      </section>
      ${daily.entries.length ? `<div class="food-day-list">${daily.entries.map(foodListItem).join('')}</div>` : emptyState('На эту дату записей нет', 'Добавьте прием пищи или переключитесь на другую дату.', '#/food/new', 'Добавить прием пищи')}
    `;
  }

  function foodListItem(item) {
    return `
      <div class="food-entry-row">
        <a class="food-entry-main" href="#/food/${encodeURIComponent(item.id)}">
          <div>
            <p class="item-title">${escapeHtml(item.mealName)}</p>
            <p class="muted">${formatDateTime(item.mealDate)}</p>
          </div>
          <strong class="food-calories">${item.calories || 0}<small> ккал</small></strong>
        </a>
        ${macroPills(item, 'compact')}
        <a class="btn ghost" href="#/food/${encodeURIComponent(item.id)}">Открыть</a>
      </div>`;
  }

  function renderFoodDetails(item) {
    if (!item) return pageHeader('Прием пищи', 'Запись не найдена.') + emptyState('Запись не найдена', 'Вернитесь к списку и выберите существующий прием пищи.', '#/food', 'К списку');

    return `
      <section class="section-header">
        <div>
          <p class="eyebrow">Детали КБЖУ</p>
          <h1 style="font-size: clamp(34px, 5vw, 56px);">${escapeHtml(item.mealName)}</h1>
          <p>Личная запись за ${formatDateShort(dateOnly(item.mealDate))}. Ее нельзя публиковать в ленте.</p>
        </div>
        <div class="btn-row">
          <a class="btn ghost" href="#/food">К дневнику</a>
          <a class="btn" href="#/food/new">Добавить еще</a>
        </div>
      </section>
      <article class="detail-card nutrition-detail-card">
        <div class="daily-headline compact-headline">
          <div>
            <strong>${escapeHtml(item.mealName)}</strong>
            <span>${formatDateTime(item.mealDate)}</span>
          </div>
          <div class="daily-total-calories">${item.calories || 0}<small> ккал</small></div>
        </div>
        ${nutritionSummary(item)}
        <div class="btn-row">
          <a class="btn ghost" href="#/food/${encodeURIComponent(item.id)}/edit">Редактировать</a>
          <button class="btn danger" type="button" data-delete-food="${escapeHtml(item.id)}">Удалить</button>
        </div>
      </article>
    `;
  }

  function renderFoodForm(item = null) {
    const editing = Boolean(item);
    return `
      <section class="section-header">
        <div>
          <p class="eyebrow">${editing ? 'Редактирование' : 'Новая запись'}</p>
          <h1 style="font-size: clamp(34px, 5vw, 56px);">${editing ? 'Изменить прием пищи' : 'Создать прием пищи'}</h1>
          <p>Запись появится в дневнике КБЖУ на выбранную дату.</p>
        </div>
        <a class="btn ghost" href="#/food">К дневнику</a>
      </section>
      <form id="food-form" class="form-card" data-id="${editing ? escapeHtml(item.id) : ''}">
        <div class="forms-grid">
          <div class="form-field full-width">
            <label for="food-name">Название блюда</label>
            <input id="food-name" name="mealName" required maxlength="128" placeholder="Овсянка с ягодами" value="${escapeHtml(item?.mealName || '')}" />
          </div>
          <div class="form-field"><label for="food-calories">Калории</label><input id="food-calories" name="calories" type="number" required min="0" value="${escapeHtml(item?.calories ?? '')}" /></div>
          <div class="form-field"><label for="food-proteins">Белки, г</label><input id="food-proteins" name="proteins" type="number" required min="0" step="0.01" value="${escapeHtml(item?.proteins ?? '')}" /></div>
          <div class="form-field"><label for="food-fats">Жиры, г</label><input id="food-fats" name="fats" type="number" required min="0" step="0.01" value="${escapeHtml(item?.fats ?? '')}" /></div>
          <div class="form-field"><label for="food-carbs">Углеводы, г</label><input id="food-carbs" name="carbohydrates" type="number" required min="0" step="0.01" value="${escapeHtml(item?.carbohydrates ?? '')}" /></div>
          <div class="form-field full-width">
            <label for="food-date">Дата приема пищи</label>
            <input id="food-date" name="mealDate" type="datetime-local" value="${escapeHtml(toDatetimeLocalValue(item?.mealDate) || `${state.foodDate}T12:00`)}" />
          </div>
          <div class="form-actions"><a class="btn ghost" href="#/food">Отмена</a><button class="btn" type="submit">${editing ? 'Сохранить' : 'Создать'}</button></div>
        </div>
      </form>
    `;
  }

  function renderRecipesRoute(parts) {
    if (state.error) return pageHeader('Рецепты', 'Не удалось получить данные.') + alertHtml('error', state.error);
    if (!state.recipes) return pageHeader('Рецепты', 'Получаем рецепты пользователя.') + skeletonGrid();

    if (parts[1] === 'new') return renderRecipeForm();
    if (parts[1] && parts[2] === 'edit') {
      const item = findById(state.recipes, parts[1]);
      return item ? renderRecipeForm(item) : pageHeader('Редактирование', 'Рецепт не найден.') + emptyState('Рецепт не найден', 'Вернитесь к списку рецептов.', '#/recipes', 'К списку');
    }
    if (parts[1]) return renderRecipeDetails(findById(state.recipes, parts[1]));

    const recipes = state.recipes || [];
    return `
      ${flashHtml()}
      <section class="section-header">
        <div>
          <p class="eyebrow">Рецепты</p>
          <h1 style="font-size: clamp(38px, 5vw, 64px);">Рецепты</h1>
          <p>${recipes.length} рецептов. Рецепт можно создать отдельно или сразу опубликовать в ленте.</p>
        </div>
        <div class="btn-row">
          <a class="btn" href="#/recipes/new">Создать рецепт</a>
          <a class="btn ghost" href="#/feed/new/recipe">Создать пост-рецепт</a>
        </div>
      </section>
      ${recipes.length ? `<div class="cards-grid">${recipes.map(recipeCard).join('')}</div>` : emptyState('Рецептов пока нет', 'Создайте рецепт, чтобы им можно было поделиться в ленте.', '#/recipes/new', 'Создать рецепт')}
    `;
  }

  function recipeCard(item) {
    return `
      <article class="card recipe-card">
        <div>
          <span class="badge">🍳 Рецепт</span>
          <h3>${escapeHtml(item.title)}</h3>
          <p class="muted">${escapeHtml(item.description || 'Описание не добавлено')}</p>
        </div>
        <p>К=${item.calories || 0}, Б=${formatNumber(item.proteins)}, Ж=${formatNumber(item.fats)}, У=${formatNumber(item.carbohydrates)}</p>
        <div class="btn-row compact-actions">
          <button class="btn" type="button" data-share-recipe="${escapeHtml(item.id)}">Поделиться</button>
          <a class="btn ghost" href="#/recipes/${encodeURIComponent(item.id)}">Открыть</a>
        </div>
      </article>`;
  }

  function renderRecipeDetails(item) {
    if (!item) return pageHeader('Рецепт', 'Запись не найдена.') + emptyState('Рецепт не найден', 'Вернитесь к списку рецептов.', '#/recipes', 'К списку');

    return `
      <section class="section-header">
        <div>
          <p class="eyebrow">Детали рецепта</p>
          <h1 style="font-size: clamp(34px, 5vw, 56px);">${escapeHtml(item.title)}</h1>
          <p>${escapeHtml(item.description || 'Описание не добавлено')}</p>
        </div>
        <div class="btn-row">
          <button class="btn" type="button" data-share-recipe="${escapeHtml(item.id)}">Поделиться</button>
          <a class="btn ghost" href="#/recipes">К списку</a>
        </div>
      </section>
      <article class="detail-card">
        <div class="detail-grid">
          <div class="detail-cell"><strong>Калории</strong>${item.calories || 0} ккал</div>
          <div class="detail-cell"><strong>Белки</strong>${formatNumber(item.proteins)} г</div>
          <div class="detail-cell"><strong>Жиры</strong>${formatNumber(item.fats)} г</div>
          <div class="detail-cell"><strong>Углеводы</strong>${formatNumber(item.carbohydrates)} г</div>
          <div class="detail-cell" style="grid-column: 1 / -1;"><strong>Ингредиенты</strong>${escapeHtml(item.ingredients || 'Ингредиенты не указаны')}</div>
          <div class="detail-cell" style="grid-column: 1 / -1;"><strong>Создано</strong>${formatDateTime(item.createdAt)}</div>
        </div>
        <div class="btn-row">
          <a class="btn ghost" href="#/recipes/${encodeURIComponent(item.id)}/edit">Редактировать</a>
          <button class="btn danger" type="button" data-delete-recipe="${escapeHtml(item.id)}">Удалить</button>
          <a class="btn" href="#/recipes/new">Создать еще один</a>
        </div>
      </article>
    `;
  }

  function renderRecipeForm(item = null) {
    const editing = Boolean(item);
    return `
      <section class="section-header">
        <div>
          <p class="eyebrow">${editing ? 'Редактирование' : 'Новый рецепт'}</p>
          <h1 style="font-size: clamp(34px, 5vw, 56px);">${editing ? 'Изменить рецепт' : 'Создать рецепт'}</h1>
          <p>Рецепт хранится отдельно от food entries и может быть опубликован в ленте.</p>
        </div>
        <a class="btn ghost" href="#/recipes">К списку</a>
      </section>
      <form id="recipe-form" class="form-card" data-id="${editing ? escapeHtml(item.id) : ''}">
        ${recipeFields(item)}
        <div class="form-actions"><a class="btn ghost" href="#/recipes">Отмена</a><button class="btn" type="submit">${editing ? 'Сохранить' : 'Создать'}</button></div>
      </form>
    `;
  }

  function recipeFields(item = null) {
    return `
      <div class="forms-grid">
        <div class="form-field full-width"><label for="recipe-title">Название</label><input id="recipe-title" name="title" required maxlength="128" placeholder="Овсянка с ягодами" value="${escapeHtml(item?.title || '')}" /></div>
        <div class="form-field full-width"><label for="recipe-description">Описание</label><input id="recipe-description" name="description" required maxlength="128" placeholder="Полезный завтрак" value="${escapeHtml(item?.description || '')}" /></div>
        <div class="form-field full-width"><label for="recipe-ingredients">Ингредиенты</label><textarea id="recipe-ingredients" name="ingredients" required maxlength="128" placeholder="Овсяные хлопья, молоко, ягоды">${escapeHtml(item?.ingredients || '')}</textarea></div>
        <div class="form-field full-width"><label for="recipe-steps">Шаги приготовления</label><textarea id="recipe-steps" name="cookingSteps" required maxlength="128" placeholder="Сварить овсянку, добавить ягоды">${escapeHtml(item?.cookingSteps || '')}</textarea></div>
        <div class="form-field"><label for="recipe-calories">Калории</label><input id="recipe-calories" name="calories" type="number" min="0" value="${escapeHtml(item?.calories ?? '')}" /></div>
        <div class="form-field"><label for="recipe-proteins">Белки, г</label><input id="recipe-proteins" name="proteins" type="number" min="0" step="0.01" value="${escapeHtml(item?.proteins ?? '')}" /></div>
        <div class="form-field"><label for="recipe-fats">Жиры, г</label><input id="recipe-fats" name="fats" type="number" min="0" step="0.01" value="${escapeHtml(item?.fats ?? '')}" /></div>
        <div class="form-field"><label for="recipe-carbs">Углеводы, г</label><input id="recipe-carbs" name="carbohydrates" type="number" min="0" step="0.01" value="${escapeHtml(item?.carbohydrates ?? '')}" /></div>
        <div class="form-field full-width"><label for="recipe-image">Ссылка на изображение</label><input id="recipe-image" name="imageUrl" placeholder="https://example.com/image.jpg" value="${escapeHtml(item?.imageUrl || '')}" /></div>
      </div>`;
  }

  function renderAchievementsRoute(parts) {
    if (state.error) return pageHeader('Достижения', 'Не удалось получить данные.') + alertHtml('error', state.error);
    if (!state.achievements) return pageHeader('Достижения', 'Получаем список достижений пользователя.') + skeletonGrid();

    if (parts[1]) return renderAchievementDetails(findAchievementById(state.achievements, parts[1]));

    const achievements = state.achievements || [];
    const totalPoints = achievements.reduce((sum, item) => sum + Number(achievementPoints(item) || 0), 0);

    return `
      ${flashHtml()}
      <section class="section-header">
        <div>
          <p class="eyebrow">Прогресс</p>
          <h1 style="font-size: clamp(38px, 5vw, 64px);">Достижения</h1>
          <p>${achievements.length} достижений${totalPoints ? ` · ${totalPoints} очков` : ''}. Достижениями можно делиться в ленте.</p>
        </div>
        <a class="btn" href="#/profile">В профиль</a>
      </section>
      ${achievements.length ? `<div class="achievement-grid">${achievements.map(achievementListItem).join('')}</div>` : emptyState('Достижений пока нет', 'Frontend показывает только достижения, которые пришли от backend через /api/users/me/achievements.', '#/profile', 'В профиль')}
    `;
  }

  function achievementListItem(item) {
    return `
      <article class="card achievement-tile">
        <a href="#/achievements/${encodeURIComponent(achievementId(item))}">
          <div class="achievement-icon" aria-hidden="true">${achievementIcon(item)}</div>
          <div>
            <span class="badge">${escapeHtml(achievementCategory(item))}</span>
            <h3>${escapeHtml(achievementTitle(item))}</h3>
            <p class="muted">${escapeHtml(achievementDescription(item))}</p>
          </div>
          <div class="item-meta"><span>${formatDateTime(achievementEarnedAt(item))}</span></div>
        </a>
        <div class="btn-row compact-actions">
          <button class="btn" type="button" data-share-achievement-entry="${escapeHtml(achievementId(item))}">Поделиться</button>
          <a class="btn ghost" href="#/achievements/${encodeURIComponent(achievementId(item))}">Открыть</a>
        </div>
      </article>`;
  }

  function renderAchievementDetails(item) {
    if (!item) return pageHeader('Достижение', 'Запись не найдена.') + emptyState('Достижение не найдено', 'Вернитесь к списку достижений.', '#/achievements', 'К списку');

    return `
      <section class="section-header">
        <div>
          <p class="eyebrow">Детали достижения</p>
          <h1 style="font-size: clamp(34px, 5vw, 56px);">${escapeHtml(achievementTitle(item))}</h1>
          <p>${escapeHtml(achievementDescription(item))}</p>
        </div>
        <div class="btn-row">
          <button class="btn ghost" type="button" data-show-achievement="${escapeHtml(achievementId(item))}">Показать анимацию</button>
          <button class="btn" type="button" data-share-achievement-entry="${escapeHtml(achievementId(item))}">Поделиться</button>
          <a class="btn ghost" href="#/achievements">К списку</a>
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
        <div class="btn-row"><a class="btn" href="#/profile">В профиль</a></div>
      </article>
    `;
  }

  function renderFeedRoute(parts) {
    if (parts[1] === 'new') return renderPostForm(parts[2] || 'text');

    if (state.error) return pageHeader('Лента', 'Не удалось получить данные.') + alertHtml('error', state.error);
    if (!state.feedPosts) return pageHeader('Лента', 'Получаем публичные посты.') + skeletonGrid();

    const posts = state.feedPosts || [];
    return `
      ${flashHtml()}
      <section class="section-header feed-header">
        <div>
          <p class="eyebrow">Публичная активность</p>
          <h1 style="font-size: clamp(38px, 5vw, 64px);">Лента</h1>
          <p>Посты пользователей: текст, тренировки, рецепты и достижения. Food entries остаются только в профиле.</p>
        </div>
      </section>
      <section class="feed-create-grid" aria-label="Создание публикации">
        ${POST_CREATE_OPTIONS.map(([kind, title, text, icon]) => `
          <a class="create-post-card" href="#/feed/new/${kind}">
            <span class="create-post-icon">${icon}</span>
            <strong>${escapeHtml(title)}</strong>
            <span>${escapeHtml(text)}</span>
          </a>`).join('')}
      </section>
      <div class="feed-toolbar improved-toolbar">
        <details class="filter-menu">
          <summary>Фильтр: ${escapeHtml(feedFilterLabel())}</summary>
          <div class="filter-options">
            ${POST_FILTER_TYPES.map(([key, label]) => postFilterCheckbox(key, label)).join('')}
            <button class="btn ghost compact-btn" type="button" data-feed-filter-clear>Показать все типы</button>
          </div>
        </details>
        <span class="muted">${posts.length} ${plural(posts.length, 'пост', 'поста', 'постов')}</span>
      </div>
      ${posts.length ? `<div class="feed-list">${posts.map(postCard).join('')}</div>` : emptyState('Постов по выбранному фильтру нет', 'Измените фильтр или создайте новую публикацию.', '#/feed/new/text', 'Написать пост')}
    `;
  }

  function isPostFormRoute(parts) {
    return parts[0] === 'feed' && parts[1] === 'new';
  }

  function renderPostForm(kind) {
    const normalized = ['text', 'workout', 'recipe'].includes(kind) ? kind : 'text';
    const title = normalized === 'text' ? 'Написать текстовый пост' : normalized === 'workout' ? 'Создать тренировку и опубликовать' : 'Создать рецепт и опубликовать';
    const description = normalized === 'text'
      ? 'Обычный публичный пост без привязки к тренировке или рецепту.'
      : normalized === 'workout'
        ? 'Создается новая тренировка и сразу публикуется в ленте.'
        : 'Создается новый рецепт и сразу публикуется в ленте.';

    return `
      <section class="section-header">
        <div>
          <p class="eyebrow">Публикация</p>
          <h1 style="font-size: clamp(34px, 5vw, 56px);">${title}</h1>
          <p>${description}</p>
        </div>
        <a class="btn ghost" href="#/feed">К ленте</a>
      </section>
      <form id="post-form" class="form-card" data-kind="${normalized}">
        <div class="forms-grid">
          <div class="form-field full-width"><label for="post-title">Заголовок поста</label><input id="post-title" name="postTitle" required maxlength="128" placeholder="Сегодня сделал отличную тренировку" /></div>
          ${normalized === 'text' ? `<div class="form-field full-width"><label for="post-content">Текст поста</label><textarea id="post-content" name="content" required maxlength="2000" placeholder="Расскажите, что произошло"></textarea></div>` : ''}
          ${normalized === 'workout' ? workoutPostFields() : ''}
          ${normalized === 'recipe' ? recipeFields() : ''}
          <div class="form-actions"><a class="btn ghost" href="#/feed">Отмена</a><button class="btn" type="submit">Опубликовать</button></div>
        </div>
      </form>
    `;
  }

  function workoutPostFields() {
    return `
      <div class="form-field full-width"><label for="post-workout-title">Название тренировки</label><input id="post-workout-title" name="title" required maxlength="128" placeholder="Утренняя тренировка" /></div>
      <div class="form-field"><label for="post-workout-type">Тип</label><select id="post-workout-type" name="type" required>${workoutTypeOptions()}</select></div>
      <div class="form-field"><label for="post-workout-duration">Длительность, минут</label><input id="post-workout-duration" name="durationMinutes" type="number" required min="1" /></div>
      <div class="form-field"><label for="post-workout-calories">Сожженные калории</label><input id="post-workout-calories" name="caloriesBurned" type="number" min="0" /></div>
      <div class="form-field full-width"><label for="post-workout-description">Описание</label><textarea id="post-workout-description" name="description" maxlength="2000" placeholder="Как прошла тренировка"></textarea></div>`;
  }

  function postCard(post) {
    const type = post.type || post.postType || 'TEXT';
    return `
      <article class="post-card">
        <div class="post-header">
          <div>
            <span class="badge">${postTypeLabel(type)}</span>
            <h3>${escapeHtml(post.title || 'Пост без заголовка')}</h3>
            <p class="muted">${escapeHtml(post.username || 'Пользователь')} · ${formatDateTime(post.createdAt)}</p>
          </div>
        </div>
        ${post.content ? `<p>${escapeHtml(post.content)}</p>` : ''}
        ${post.workout ? postWorkoutPayload(post.workout) : ''}
        ${post.recipe ? postRecipePayload(post.recipe) : ''}
        ${post.userAchievement ? postAchievementPayload(post.userAchievement) : ''}
      </article>`;
  }

  function postWorkoutPayload(workout) {
    return `<div class="payload-card"><strong>${escapeHtml(workout.title || 'Тренировка')}</strong><span>${workoutTypeLabel(workout.type)} · ${workout.durationMinutes || 0} мин · ${workout.caloriesBurned || 0} ккал</span></div>`;
  }

  function postRecipePayload(recipe) {
    return `<div class="payload-card"><strong>${escapeHtml(recipe.title || 'Рецепт')}</strong><span>К=${recipe.calories || 0}, Б=${formatNumber(recipe.proteins)}, Ж=${formatNumber(recipe.fats)}, У=${formatNumber(recipe.carbohydrates)}</span></div>`;
  }

  function postAchievementPayload(item) {
    return `<div class="payload-card"><strong>${achievementIcon(item)} ${escapeHtml(achievementTitle(item))}</strong><span>${escapeHtml(achievementDescription(item))}</span></div>`;
  }

  function readInitialFeedTypes() {
    try {
      const raw = localStorage.getItem(STORAGE.feedType);
      if (!raw || raw === 'ALL') return [];
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) return normalizedFeedTypes(parsed);
      return normalizedFeedTypes([raw]);
    } catch {
      const raw = localStorage.getItem(STORAGE.feedType);
      return raw && raw !== 'ALL' ? normalizedFeedTypes([raw]) : [];
    }
  }

  function normalizedFeedTypes(types) {
    const allowed = new Set(POST_FILTER_TYPES.map(([key]) => key));
    return [...new Set((types || []).map(String).filter((type) => allowed.has(type)))];
  }

  function uniquePostsById(posts) {
    const map = new Map();
    (posts || []).forEach((post) => {
      const id = String(post?.id || `${post?.type || 'POST'}-${post?.createdAt || Math.random()}`);
      if (!map.has(id)) map.set(id, post);
    });
    return [...map.values()];
  }

  function feedFilterLabel() {
    const selected = normalizedFeedTypes(state.feedTypes);
    if (!selected.length) return 'все типы';
    if (selected.length === POST_FILTER_TYPES.length) return 'все типы';
    return selected.map(postTypeLabel).join(' + ');
  }

  function postFilterCheckbox(key, label) {
    const selected = normalizedFeedTypes(state.feedTypes);
    const checked = selected.includes(key) ? ' checked' : '';
    return `<label class="filter-option"><input type="checkbox" value="${escapeHtml(key)}" data-feed-filter${checked} /><span>${escapeHtml(label)}</span></label>`;
  }

  function nutritionSummary(source) {
    return `
      <div class="nutrition-summary readable-macros">
        <div class="macro-card calories"><span>Калории</span><strong>${Number(source?.totalCalories ?? source?.calories ?? 0)}</strong><small>ккал</small></div>
        <div class="macro-card"><span>Белки</span><strong>${formatNumber(source?.totalProteins ?? source?.proteins)}</strong><small>г</small></div>
        <div class="macro-card"><span>Жиры</span><strong>${formatNumber(source?.totalFats ?? source?.fats)}</strong><small>г</small></div>
        <div class="macro-card"><span>Углеводы</span><strong>${formatNumber(source?.totalCarbohydrates ?? source?.carbohydrates)}</strong><small>г</small></div>
      </div>`;
  }

  function macroPills(source, extraClass = '') {
    const className = `macro-pills${extraClass ? ` ${extraClass}` : ''}`;
    return `
      <div class="${className}">
        <span>К ${Number(source?.totalCalories ?? source?.calories ?? 0)}</span>
        <span>Б ${formatNumber(source?.totalProteins ?? source?.proteins)}</span>
        <span>Ж ${formatNumber(source?.totalFats ?? source?.fats)}</span>
        <span>У ${formatNumber(source?.totalCarbohydrates ?? source?.carbohydrates)}</span>
      </div>`;
  }

  function plural(count, one, few, many) {
    const n = Math.abs(Number(count)) % 100;
    const n1 = n % 10;
    if (n > 10 && n < 20) return many;
    if (n1 > 1 && n1 < 5) return few;
    if (n1 === 1) return one;
    return many;
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
    return `<div class="cards-grid"><div class="skeleton"></div><div class="skeleton"></div></div>`;
  }

  function emptyState(title, text, href, cta) {
    return `<div class="empty-state"><h2>${escapeHtml(title)}</h2><p class="muted">${escapeHtml(text)}</p><a class="btn" href="${escapeHtml(href)}">${escapeHtml(cta)}</a></div>`;
  }

  function alertHtml(type, message) {
    return `<div class="alert ${escapeHtml(type)}" role="alert"><strong>${type === 'error' ? 'Ошибка' : 'Готово'}</strong><span>${escapeHtml(message)}</span></div>`;
  }

  function flashHtml() {
    if (!state.flash) return '';
    const message = state.flash;
    state.flash = null;
    return alertHtml('success', message);
  }

  function logoHtml() {
    return `<span class="logo-mark" aria-hidden="true"><span class="logo-shield">T</span></span>`;
  }

  function bindGlobalActions() {
    app.querySelectorAll('[data-auth]').forEach((button) => button.addEventListener('click', () => startAuth(button.dataset.auth)));
    app.querySelectorAll('[data-logout]').forEach((button) => button.addEventListener('click', logout));
    app.querySelectorAll('[data-reload]').forEach((button) => button.addEventListener('click', () => reloadCurrentSection()));

    app.querySelectorAll('[data-accept-achievement]').forEach((button) => {
      button.addEventListener('click', () => {
        showNextAchievementOrClose();
        renderRoute(false);
      });
    });

    app.querySelectorAll('[data-share-achievement]').forEach((button) => {
      button.addEventListener('click', () => shareAchievement(state.achievementModal));
    });

    app.querySelectorAll('[data-show-achievement]').forEach((button) => {
      button.addEventListener('click', () => {
        const item = findAchievementById(state.achievements || [], button.dataset.showAchievement);
        if (!item) state.flash = 'Достижение не найдено в данных backend.';
        else showAchievementModal(item);
        renderRoute(false);
      });
    });

    app.querySelectorAll('[data-share-workout]').forEach((button) => button.addEventListener('click', () => shareEntity('workout', button.dataset.shareWorkout)));
    app.querySelectorAll('[data-share-recipe]').forEach((button) => button.addEventListener('click', () => shareEntity('recipe', button.dataset.shareRecipe)));
    app.querySelectorAll('[data-share-achievement-entry]').forEach((button) => button.addEventListener('click', () => shareAchievement(findAchievementById(state.achievements || [], button.dataset.shareAchievementEntry))));

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

    app.querySelectorAll('[data-delete-recipe]').forEach((button) => {
      button.addEventListener('click', async () => {
        if (!confirm('Удалить этот рецепт?')) return;
        await deleteResource('/api/recipes/' + encodeURIComponent(button.dataset.deleteRecipe), 'Рецепт удален.');
      });
    });

    app.querySelectorAll('[data-food-date-action]').forEach((button) => {
      button.addEventListener('click', () => {
        const current = parseISODate(state.foodDate);
        if (button.dataset.foodDateAction === 'prev') current.setDate(current.getDate() - 1);
        if (button.dataset.foodDateAction === 'next') current.setDate(current.getDate() + 1);
        const next = button.dataset.foodDateAction === 'today' ? todayISODate() : dateToISO(current);
        loadDailyFood(next, true);
      });
    });

    app.querySelectorAll('[data-food-date-input]').forEach((input) => {
      input.addEventListener('change', () => loadDailyFood(input.value || todayISODate(), true));
    });

    app.querySelectorAll('[data-feed-filter]').forEach((input) => {
      input.addEventListener('change', () => {
        state.feedTypes = Array.from(app.querySelectorAll('[data-feed-filter]:checked')).map((item) => item.value);
        localStorage.setItem(STORAGE.feedType, JSON.stringify(state.feedTypes));
        state.feedPosts = null;
        loadFeed(true);
      });
    });

    app.querySelectorAll('[data-feed-filter-clear]').forEach((button) => {
      button.addEventListener('click', () => {
        state.feedTypes = [];
        localStorage.setItem(STORAGE.feedType, JSON.stringify([]));
        state.feedPosts = null;
        loadFeed(true);
      });
    });
  }

  function bindForms() {
    const workoutForm = document.getElementById('workout-form');
    if (workoutForm) workoutForm.addEventListener('submit', submitWorkout);

    const foodForm = document.getElementById('food-form');
    if (foodForm) foodForm.addEventListener('submit', submitFood);

    const recipeForm = document.getElementById('recipe-form');
    if (recipeForm) recipeForm.addEventListener('submit', submitRecipe);

    const postForm = document.getElementById('post-form');
    if (postForm) postForm.addEventListener('submit', submitPost);
  }

  async function reloadCurrentSection() {
    state.error = null;
    const parts = routeParts();
    if (parts[0] === 'feed') {
      state.feedPosts = null;
      await loadFeed(true);
      return;
    }
    await loadDashboard(true);
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
    const payload = workoutPayload(formData);

    try {
      disableForm(form, true);
      const previousAchievementIds = achievementIdSet(state.achievements || []);
      await apiFetch(id ? `/api/workouts/${encodeURIComponent(id)}` : '/api/workouts', {
        method: id ? 'PATCH' : 'POST',
        body: JSON.stringify(payload),
      });
      state.flash = id ? 'Тренировка обновлена.' : 'Тренировка создана и добавлена в профиль.';
      await loadDashboard(true, { animateNewAchievements: true, previousAchievementIds });
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
      mealDate: stringValue(formData.get('mealDate')) || `${todayISODate()}T12:00`,
    };

    try {
      disableForm(form, true);
      const previousAchievementIds = achievementIdSet(state.achievements || []);
      await apiFetch(id ? `/api/food-entries/${encodeURIComponent(id)}` : '/api/food-entries', {
        method: id ? 'PATCH' : 'POST',
        body: JSON.stringify(payload),
      });
      state.foodDate = dateOnly(payload.mealDate) || state.foodDate;
      localStorage.setItem(STORAGE.foodDate, state.foodDate);
      state.flash = id ? 'Прием пищи обновлен.' : 'Прием пищи создан и добавлен в дневник КБЖУ.';
      await loadDashboard(true, { animateNewAchievements: true, previousAchievementIds });
      navigate('/food');
    } catch (error) {
      state.error = friendlyError(error);
      renderProtected(renderFoodForm(id ? findById(state.foodEntries, id) : null) + alertHtml('error', state.error));
    } finally {
      disableForm(form, false);
    }
  }

  async function submitRecipe(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const formData = new FormData(form);
    const id = form.dataset.id;
    const payload = recipePayload(formData);

    try {
      disableForm(form, true);
      await apiFetch(id ? `/api/recipes/${encodeURIComponent(id)}` : '/api/recipes', {
        method: id ? 'PATCH' : 'POST',
        body: JSON.stringify(payload),
      });
      state.flash = id ? 'Рецепт обновлен.' : 'Рецепт создан.';
      await loadDashboard(true);
      navigate('/recipes');
    } catch (error) {
      state.error = friendlyError(error);
      renderProtected(renderRecipeForm(id ? findById(state.recipes, id) : null) + alertHtml('error', state.error));
    } finally {
      disableForm(form, false);
    }
  }

  async function submitPost(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const formData = new FormData(form);
    const kind = form.dataset.kind;
    const post = { postTitle: stringValue(formData.get('postTitle')) };
    let path = '/api/posts/text';
    let payload = { post, content: stringValue(formData.get('content')) };

    if (kind === 'workout') {
      path = '/api/posts/workouts';
      payload = { post, workout: workoutPayload(formData) };
    }

    if (kind === 'recipe') {
      path = '/api/posts/recipes';
      payload = { post, recipe: recipePayload(formData) };
    }

    try {
      disableForm(form, true);
      const previousAchievementIds = achievementIdSet(state.achievements || []);
      await apiFetch(path, { method: 'POST', body: JSON.stringify(payload) });
      state.flash = 'Пост опубликован в ленте.';
      state.feedPosts = null;
      await Promise.all([
        loadDashboard(true, { animateNewAchievements: true, previousAchievementIds }),
        loadFeed(true),
      ]);
      navigate('/feed');
    } catch (error) {
      state.error = friendlyError(error);
      renderProtected(renderPostForm(kind) + alertHtml('error', state.error));
    } finally {
      disableForm(form, false);
    }
  }

  async function shareEntity(type, id) {
    const entity = type === 'workout' ? findById(state.workouts || [], id) : findById(state.recipes || [], id);
    const defaultTitle = type === 'workout'
      ? `Делюсь тренировкой: ${entity?.title || ''}`.trim()
      : `Делюсь рецептом: ${entity?.title || ''}`.trim();
    const postTitle = prompt('Введите заголовок поста', defaultTitle);
    if (!postTitle) return;

    const path = type === 'workout'
      ? `/api/posts/workouts/${encodeURIComponent(id)}/share`
      : `/api/posts/recipes/${encodeURIComponent(id)}/share`;

    try {
      await apiFetch(path, { method: 'POST', body: JSON.stringify({ postTitle }) });
      state.flash = type === 'workout' ? 'Тренировка опубликована в ленте.' : 'Рецепт опубликован в ленте.';
      state.feedPosts = null;
      await loadFeed(true);
      navigate('/feed');
    } catch (error) {
      state.error = friendlyError(error);
      renderRoute(false);
    }
  }

  async function shareAchievement(item) {
    if (!item) {
      state.achievementShareNotice = 'Достижение не найдено в данных backend.';
      renderRoute(false);
      return;
    }
    const postTitle = prompt('Введите заголовок поста', `Получил достижение: ${achievementTitle(item)}`);
    if (!postTitle) return;

    try {
      await apiFetch(`/api/posts/achievements/${encodeURIComponent(achievementId(item))}/share`, {
        method: 'POST',
        body: JSON.stringify({ postTitle }),
      });
      state.achievementShareNotice = 'Достижение опубликовано в ленте.';
      state.flash = 'Достижение опубликовано в ленте.';
      state.feedPosts = null;
      await loadFeed(true);
      renderRoute(false);
    } catch (error) {
      state.achievementShareNotice = friendlyError(error);
      renderRoute(false);
    }
  }

  function workoutPayload(formData) {
    const payload = {
      title: stringValue(formData.get('title')),
      type: stringValue(formData.get('type')),
      description: stringValue(formData.get('description')),
      durationMinutes: toInt(formData.get('durationMinutes')),
    };
    const calories = toIntOrNull(formData.get('caloriesBurned'));
    if (calories !== null) payload.caloriesBurned = calories;
    return payload;
  }

  function recipePayload(formData) {
    const payload = {
      title: stringValue(formData.get('title')),
      description: stringValue(formData.get('description')),
      ingredients: stringValue(formData.get('ingredients')),
      cookingSteps: stringValue(formData.get('cookingSteps')),
      calories: toIntOrNull(formData.get('calories')) ?? 0,
      proteins: toNumber(formData.get('proteins')),
      fats: toNumber(formData.get('fats')),
      carbohydrates: toNumber(formData.get('carbohydrates')),
    };
    const imageUrl = stringValue(formData.get('imageUrl'));
    if (imageUrl) payload.imageUrl = imageUrl;
    return payload;
  }

  function disableForm(form, disabled) {
    form.querySelectorAll('button, input, textarea, select, a').forEach((element) => {
      if ('disabled' in element) element.disabled = disabled;
    });
  }

  function createCodeVerifier() { return cryptoRandomString(64); }

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
    bytes.forEach((byte) => { binary += String.fromCharCode(byte); });
    return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  }

  function achievementModalHtml() {
    if (!state.achievementModal) return '';
    const item = state.achievementModal;
    return `
      <div class="achievement-overlay" role="dialog" aria-modal="true" aria-labelledby="achievement-title">
        <div class="confetti-layer" aria-hidden="true"><span></span><span></span><span></span><span></span><span></span><span></span><span></span><span></span><span></span><span></span><span></span><span></span></div>
        <section class="achievement-modal">
          <div class="achievement-burst" aria-hidden="true"><span>${achievementIcon(item)}</span></div>
          <p class="eyebrow">${state.achievementQueueTotal > 1 ? `Новое достижение · ${state.achievementQueueIndex} из ${state.achievementQueueTotal}` : 'Новое достижение'}</p>
          <h2 id="achievement-title">${escapeHtml(achievementTitle(item))}</h2>
          <p class="lead">${escapeHtml(achievementDescription(item))}</p>
          <div class="achievement-meta-row"><span class="badge">${escapeHtml(achievementCategory(item))}</span><span class="badge">${formatDateTime(achievementEarnedAt(item))}</span></div>
          ${state.achievementShareNotice ? alertHtml(state.achievementShareNotice.includes('опубликовано') ? 'success' : 'error', state.achievementShareNotice) : ''}
          <div class="btn-row achievement-actions">
            <button class="btn" type="button" data-accept-achievement>Принять</button>
            <button class="btn ghost" type="button" data-share-achievement>Поделиться</button>
          </div>
        </section>
      </div>`;
  }

  function normalizeDailyFood(result, fallbackDate) {
    const entries = sortByDate(Array.isArray(result?.entries) ? result.entries : [], 'mealDate').reverse();
    return {
      date: String(result?.date || fallbackDate || todayISODate()).slice(0, 10),
      totalCalories: Number(result?.totalCalories || 0),
      totalProteins: Number(result?.totalProteins || 0),
      totalFats: Number(result?.totalFats || 0),
      totalCarbohydrates: Number(result?.totalCarbohydrates || 0),
      entries,
    };
  }

  function buildDailyFoodFromEntries(entries, date) {
    const filtered = (entries || []).filter((entry) => dateOnly(entry.mealDate) === date).sort((a, b) => new Date(a.mealDate) - new Date(b.mealDate));
    return {
      date,
      totalCalories: filtered.reduce((sum, item) => sum + Number(item.calories || 0), 0),
      totalProteins: filtered.reduce((sum, item) => sum + Number(item.proteins || 0), 0),
      totalFats: filtered.reduce((sum, item) => sum + Number(item.fats || 0), 0),
      totalCarbohydrates: filtered.reduce((sum, item) => sum + Number(item.carbohydrates || 0), 0),
      entries: filtered,
    };
  }

  function emptyDailyFood(date) {
    return { date, totalCalories: 0, totalProteins: 0, totalFats: 0, totalCarbohydrates: 0, entries: [] };
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
    } catch { return new Set(); }
  }

  function markAchievementsSeen(collection) {
    const ids = achievementIdSet(collection);
    if (!ids.size) return;
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
    if (!list.length) return;
    state.achievementModal = list[0];
    state.achievementQueue = list.slice(1);
    state.achievementQueueTotal = list.length;
    state.achievementQueueIndex = 1;
    state.achievementShareNotice = null;
  }

  function showAchievementModal(item) { showAchievementQueue(item ? [item] : []); }

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
    return String(item?.id || item?.userAchievementId || item?.achievementId || source.id || source.code || source.title || 'achievement');
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
    return source.category || source.type || source.code || item?.category || item?.type || 'Достижение';
  }

  function achievementPoints(item) {
    const source = item?.achievement || item || {};
    return Number(source.points ?? item?.points ?? 0);
  }

  function achievementEarnedAt(item) {
    const source = item?.achievement || item || {};
    return item?.receivedAt || item?.earnedAt || item?.awardedAt || item?.createdAt || source.earnedAt || source.createdAt || new Date().toISOString();
  }

  function achievementIcon(item) {
    const source = item?.achievement || item || {};
    if (source.icon || item?.icon) return source.icon || item.icon;
    const text = `${achievementTitle(item)} ${achievementCategory(item)}`.toLowerCase();
    if (text.includes('трен') || text.includes('workout')) return '🏃';
    if (text.includes('питан') || text.includes('еда') || text.includes('калор') || text.includes('food')) return '🥗';
    if (text.includes('рецеп') || text.includes('recipe')) return '🍳';
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
    return [['CARDIO', 'Кардио'], ['STRENGTH', 'Силовая'], ['STRETCHING', 'Растяжка']]
      .map(([key, label]) => `<option value="${key}"${value === key ? ' selected' : ''}>${label}</option>`).join('');
  }

  function workoutTypeLabel(value) {
    const labels = { CARDIO: 'Кардио', STRENGTH: 'Силовая', STRETCHING: 'Растяжка' };
    return labels[value] || value || 'Тип не указан';
  }

  function postTypeLabel(value) {
    const labels = { TEXT: 'Текст', WORKOUT: 'Тренировка', RECIPE: 'Рецепт', ACHIEVEMENT: 'Достижение' };
    return labels[value] || value || 'Пост';
  }

  function findById(collection, id) {
    return (collection || []).find((item) => String(item.id) === String(id));
  }

  function sortByDate(items, field) {
    return [...(items || [])].sort((a, b) => new Date(b?.[field] || b?.createdAt || 0).getTime() - new Date(a?.[field] || a?.createdAt || 0).getTime());
  }

  function formatDateTime(value) {
    if (!value) return 'Дата не указана';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;
    return new Intl.DateTimeFormat('ru-RU', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }).format(date);
  }

  function formatDateShort(value) {
    if (!value) return 'Дата не указана';
    const date = parseISODate(String(value).slice(0, 10));
    if (Number.isNaN(date.getTime())) return value;
    return new Intl.DateTimeFormat('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric' }).format(date);
  }

  function toDatetimeLocalValue(value) {
    if (!value) return '';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value).slice(0, 16);
    const pad = (number) => String(number).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
  }

  function dateOnly(value) {
    if (!value) return '';
    if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}/.test(value)) return value.slice(0, 10);
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? '' : dateToISO(date);
  }

  function todayISODate() { return dateToISO(new Date()); }

  function parseISODate(value) {
    const [year, month, day] = String(value || todayISODate()).split('-').map(Number);
    return new Date(year, (month || 1) - 1, day || 1);
  }

  function dateToISO(date) {
    const pad = (number) => String(number).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
  }

  function getInitials(user) {
    const source = [user?.firstName, user?.lastName].filter(Boolean).join(' ') || user?.username || 'T';
    return source.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]).join('').toUpperCase();
  }

  function shortId(value) {
    if (!value) return '—';
    const text = String(value);
    return `${text.slice(0, 8)}…${text.slice(-4)}`;
  }

  function stringValue(value) { return String(value ?? '').trim(); }

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
    if (error instanceof AuthRequiredError) return 'Сессия завершилась. Войдите заново через Keycloak.';
    return error?.message || 'Что-то пошло не так.';
  }

  class AuthRequiredError extends Error {
    constructor() {
      super('Требуется авторизация.');
      this.name = 'AuthRequiredError';
    }
  }
})();
