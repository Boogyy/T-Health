import './styles.css';
import './public-profile.css';

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

    communities: null,
    myCommunities: null,
    communityDetails: {},
    communityMembers: {},
    communityPosts: {},
    postComments: {},

    publicPostDetails: {},
    publicPostLoadingIds: new Set(),
    publicPostReturnRoute: null,

    // Username используется как ключ. Объекты без прототипа не конфликтуют
    // со специальными именами вроде "__proto__" и "constructor".
    publicUserProfiles: Object.create(null),
    publicUserProfileErrors: Object.create(null),
    publicUserProfileLoadingNames: new Set(),
    publicUserCommunityDialogUsername: null,

    chats: null,
    chatMessages: {},

    loading: false,
    feedLoading: false,

    communitiesLoading: false,
    communityLoadingIds: new Set(),
    commentLoadingIds: new Set(),

    chatsLoading: false,
    chatLoadingIds: new Set(),

    error: null,
    flash: null,
    transientNotice: null,
    achievementModal: null,
    achievementQueue: [],
    achievementQueueTotal: 0,
    achievementQueueIndex: 0,
    achievementShareNotice: null,
  };

  const app = document.getElementById('app');
  let transientNoticeTimer = null;

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

  async function loadCommunities(force = false) {
    if (state.communitiesLoading) return;
    if (state.communities && state.myCommunities && !force) return;

    state.communitiesLoading = true;
    state.error = null;
    renderRoute(false);

    try {
      const [user, communities, myCommunities] = await Promise.all([
        getCurrentUser(),
        apiFetch('/api/communities'),
        apiFetch('/api/communities/me'),
      ]);

      state.user = user;

      state.communities = sortByDate(
        Array.isArray(communities) ? communities : [],
        'createdAt'
      );

      state.myCommunities = sortByDate(
        Array.isArray(myCommunities) ? myCommunities : [],
        'createdAt'
      );

      state.communities.forEach((community) => {
        state.communityDetails[String(community.id)] = community;
      });

      state.myCommunities.forEach((community) => {
        state.communityDetails[String(community.id)] = community;
      });
    } catch (error) {
      if (error instanceof AuthRequiredError) {
        navigate('/login');
        return;
      }

      state.error = friendlyError(error);
    } finally {
      state.communitiesLoading = false;
      renderRoute(false);
    }
  }

  async function loadCommunity(communityId, force = false) {
    const id = String(communityId);

    const hasData =
      state.communityDetails[id] &&
      state.communityMembers[id] &&
      state.communityPosts[id];

    if (hasData && !force) return;
    if (state.communityLoadingIds.has(id)) return;

    state.communityLoadingIds.add(id);
    state.error = null;
    renderRoute(false);

    try {
      const [user, community] = await Promise.all([
        getCurrentUser(),
        apiFetch(`/api/communities/${encodeURIComponent(id)}`),
      ]);

      let members = [];
      let posts = [];

      if (community.currentUserMember) {
        [members, posts] = await Promise.all([
          apiFetch(`/api/communities/${encodeURIComponent(id)}/members`),
          apiFetch(`/api/communities/${encodeURIComponent(id)}/posts`),
        ]);
      }

      state.user = user;
      state.communityDetails[id] = community;
      state.communityMembers[id] = Array.isArray(members) ? members : [];

      state.communityPosts[id] = sortByDate(
        Array.isArray(posts) ? posts : [],
        'createdAt'
      );

      upsertCommunityCollections(community);
    } catch (error) {
      if (error instanceof AuthRequiredError) {
        navigate('/login');
        return;
      }

      state.error = friendlyError(error);
    } finally {
      state.communityLoadingIds.delete(id);
      renderRoute(false);
    }
  }

  async function loadComments(postId, force = false) {
    const id = String(postId);

    if (state.postComments[id] && !force) return;
    if (state.commentLoadingIds.has(id)) return;

    state.commentLoadingIds.add(id);
    state.error = null;
    renderRoute(false);

    try {
      const comments = await apiFetch(
        `/api/posts/${encodeURIComponent(id)}/comments`
      );

      state.postComments[id] = Array.isArray(comments) ? comments : [];
    } catch (error) {
      state.error = friendlyError(error);
    } finally {
      state.commentLoadingIds.delete(id);
      renderRoute(false);
    }
  }

  async function loadPublicPost(postId, force = false) {
    const id = String(postId);

    const hasPost = Boolean(state.publicPostDetails[id]);
    const hasComments = Object.prototype.hasOwnProperty.call(
      state.postComments,
      id
    );

    if (hasPost && hasComments && !force) return;
    if (state.publicPostLoadingIds.has(id)) return;

    state.publicPostLoadingIds.add(id);
    state.error = null;
    renderRoute(false);

    try {
      const [user, post, comments] = await Promise.all([
        getCurrentUser(),
        apiFetch(`/api/posts/${encodeURIComponent(id)}`),
        apiFetch(`/api/posts/${encodeURIComponent(id)}/comments`),
      ]);

      state.user = user;
      state.publicPostDetails[id] = post;
      state.postComments[id] = Array.isArray(comments) ? comments : [];
      updatePostCommentsCount(id, state.postComments[id].length);
    } catch (error) {
      if (error instanceof AuthRequiredError) {
        navigate('/login');
        return;
      }

      state.error = friendlyError(error);
    } finally {
      state.publicPostLoadingIds.delete(id);
      renderRoute(false);
    }
  }

  async function loadChats(force = false) {
    if (state.chatsLoading) return;
    if (state.chats && !force) return;

    state.chatsLoading = true;
    state.error = null;
    renderRoute(false);

    try {
      const [user, chats] = await Promise.all([
        getCurrentUser(),
        apiFetch('/api/direct-chats'),
      ]);

      state.user = user;
      state.chats = sortChats(Array.isArray(chats) ? chats : []);
    } catch (error) {
      if (error instanceof AuthRequiredError) {
        navigate('/login');
        return;
      }

      state.error = friendlyError(error);
    } finally {
      state.chatsLoading = false;
      renderRoute(false);
    }
  }

  async function loadChatMessages(chatId, force = false) {
    const id = String(chatId);

    if (state.chatMessages[id] && !force) return;
    if (state.chatLoadingIds.has(id)) return;

    state.chatLoadingIds.add(id);
    state.error = null;
    renderRoute(false);

    try {
      const messages = await apiFetch(
        `/api/direct-chats/${encodeURIComponent(id)}/messages`
      );

      state.chatMessages[id] = sortByDateAscending(
        Array.isArray(messages) ? messages : [],
        'sentAt'
      );
    } catch (error) {
      state.error = friendlyError(error);
    } finally {
      state.chatLoadingIds.delete(id);
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

    state.communities = null;
    state.myCommunities = null;
    state.communityDetails = {};
    state.communityMembers = {};
    state.communityPosts = {};
    state.postComments = {};

    state.publicPostDetails = {};
    state.publicPostLoadingIds.clear();
    state.publicPostReturnRoute = null;

    state.chats = null;
    state.chatMessages = {};

    state.publicUserProfiles = Object.create(null);
    state.publicUserProfileErrors = Object.create(null);
    state.publicUserProfileLoadingNames.clear();
    state.publicUserCommunityDialogUsername = null;

    if (transientNoticeTimer) {
      clearTimeout(transientNoticeTimer);
      transientNoticeTimer = null;
    }
    state.transientNotice = null;
  }

  async function loadPublicUserProfile(
      username,
      force = false
  ) {
    const exactUsername = String(username || '');

    const profileLoaded =
        Object.prototype.hasOwnProperty.call(
            state.publicUserProfiles,
            exactUsername
        );

    const errorLoaded =
        Object.prototype.hasOwnProperty.call(
            state.publicUserProfileErrors,
            exactUsername
        );

    if (
        (profileLoaded || errorLoaded) &&
        !force
    ) {
      return;
    }

    if (
        state.publicUserProfileLoadingNames.has(
            exactUsername
        )
    ) {
      return;
    }

    // Не переносим глобальную ошибку с предыдущего экрана на профиль.
    state.error = null;

    state.publicUserProfileLoadingNames.add(
        exactUsername
    );

    delete state.publicUserProfileErrors[
        exactUsername
        ];

    if (force) {
      delete state.publicUserProfiles[
          exactUsername
          ];
    }

    renderRoute(false);

    try {
      const [currentUser, profile] =
          await Promise.all([
            getCurrentUser(),
            apiFetch(
                `/api/users/public/${encodeURIComponent(
                    exactUsername
                )}`
            ),
          ]);

      state.user = currentUser;

      if (
        String(profile?.userId) ===
        String(currentUser?.id)
      ) {
        navigate('/profile');
        return;
      }

      state.publicUserProfiles[
          exactUsername
          ] = profile;
    } catch (error) {
      if (error instanceof AuthRequiredError) {
        navigate('/login');
        return;
      }

      state.publicUserProfileErrors[
          exactUsername
          ] = friendlyError(error);
    } finally {
      state.publicUserProfileLoadingNames.delete(
          exactUsername
      );

      renderRoute(false);
    }
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

    if (
        parts[0] === 'users' &&
        parts[1]
    ) {
      const username = parts[1];

      if (
        state.user?.username &&
        state.user.username === username
      ) {
        navigate('/profile');
        return;
      }

      renderProtected(
          renderPublicUserProfile(username)
      );

      const profileLoaded =
          Object.prototype.hasOwnProperty.call(
              state.publicUserProfiles,
              username
          );

      const errorLoaded =
          Object.prototype.hasOwnProperty.call(
              state.publicUserProfileErrors,
              username
          );

      if (!profileLoaded && !errorLoaded) {
        loadPublicUserProfile(username);
      }

      return;
    }

    if (parts[0] === 'feed') {
      renderProtected(renderFeedRoute(parts));

      if (parts[1] && parts[1] !== 'new') {
        const postId = String(parts[1]);
        const commentsLoaded = Object.prototype.hasOwnProperty.call(
          state.postComments,
          postId
        );

        if (!state.publicPostDetails[postId] || !commentsLoaded) {
          loadPublicPost(postId);
        }

        return;
      }

      if (!state.feedPosts && !isPostFormRoute(parts)) loadFeed();
      if ((isPostFormRoute(parts) || parts[1] === 'new') && (!state.workouts || !state.recipes || !state.achievements)) loadDashboard();
      return;
    }

    if (parts[0] === 'communities') {
      renderProtected(renderCommunitiesRoute(parts));

      const catalogRoute =
        !parts[1] ||
        parts[1] === 'all' ||
        parts[1] === 'mine';

      if (
        catalogRoute &&
        (!state.communities || !state.myCommunities)
      ) {
        loadCommunities();
      }

      if (!catalogRoute && parts[1] && parts[1] !== 'new') {
        const communityId = parts[1];

        const communityNotLoaded =
          !state.communityDetails[communityId] ||
          !state.communityMembers[communityId] ||
          !state.communityPosts[communityId];

        if (communityNotLoaded) {
          loadCommunity(communityId);
        } else if (
          parts[2] === 'posts' &&
          parts[3] &&
          parts[3] !== 'new' &&
          state.communityDetails[communityId]?.currentUserMember &&
          !state.postComments[parts[3]]
        ) {
          loadComments(parts[3]);
        }
      }
      return;
    }

    if (parts[0] === 'chats') {
      renderProtected(renderChatsRoute(parts));

      if (!state.chats && parts[1] !== 'new') {
        loadChats();
      }

      if (
        parts[1] &&
        parts[1] !== 'new' &&
        !state.chatMessages[parts[1]]
      ) {
        loadChatMessages(parts[1]);
      }
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
            ${navLink('/communities', 'Сообщества', route)}
            ${navLink('/chats', 'Чаты', route)}
            ${navLink('/workouts', 'Тренировки', route)}
            ${navLink('/food', 'КБЖУ', route)}
            ${navLink('/recipes', 'Рецепты', route)}
            ${navLink('/achievements', 'Достижения', route)}
            <button class="nav-link" type="button" data-reload>Обновить</button>
            <button class="nav-link" type="button" data-logout>Выйти</button>
          </nav>
        </div>
      </header>
      ${transientNoticeHtml()}
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
    if (parts[1]) return renderPublicPostDetails(parts[1]);

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

  function postAuthorMeta(post) {
    const username = String(post?.username || '');
    const initial = username
      ? username.charAt(0).toUpperCase()
      : 'П';

    return `
      <div class="post-author-meta">
        <span class="post-author-avatar" aria-hidden="true">
          ${escapeHtml(initial)}
        </span>

        <span class="post-author-copy">
          <span class="post-author-name">
            ${publicUserLink(username)}
          </span>

          <time datetime="${escapeHtml(post?.createdAt || '')}">
            ${formatDateTime(post?.createdAt)}
          </time>
        </span>
      </div>
    `;
  }

  function postCommentsCount(post) {
    const value = Number(
      post?.commentsCount ??
      post?.commentCount ??
      0
    );

    return Number.isFinite(value) && value > 0
      ? Math.trunc(value)
      : 0;
  }

  function updatePostCommentsCount(postId, count) {
    const id = String(postId);
    const safeCount = Math.max(0, Number(count) || 0);

    const updatePost = (post) => {
      if (!post || String(post.id) !== id) {
        return post;
      }

      return {
        ...post,
        commentsCount: safeCount,
      };
    };

    if (state.publicPostDetails[id]) {
      state.publicPostDetails[id] = updatePost(
        state.publicPostDetails[id]
      );
    }

    if (Array.isArray(state.feedPosts)) {
      state.feedPosts = state.feedPosts.map(updatePost);
    }

    Object.values(state.publicUserProfiles).forEach((profile) => {
      if (!Array.isArray(profile?.publications)) {
        return;
      }

      profile.publications = profile.publications.map(updatePost);
    });
  }

  function postCard(post) {
    const type = post.type || post.postType || 'TEXT';
    const title = post.title || 'Пост без заголовка';
    const commentsCount = postCommentsCount(post);

    return `
      <article
        class="post-card public-post-card"
        data-open-public-post="${escapeHtml(post.id)}"
        tabindex="0"
        role="link"
        aria-label="Открыть публикацию: ${escapeHtml(title)}"
      >
        <header class="post-card-head">
          ${postAuthorMeta(post)}

          <span class="badge post-type-badge">
            ${postTypeLabel(type)}
          </span>
        </header>

        <div class="post-card-body">
          <h3 class="post-card-title">
            ${escapeHtml(title)}
          </h3>

          ${post.content
            ? `<p class="post-content">${escapeHtml(post.content)}</p>`
            : ''}

          ${post.workout ? postWorkoutPayload(post.workout) : ''}
          ${post.recipe ? postRecipePayload(post.recipe) : ''}
          ${post.userAchievement ? postAchievementPayload(post.userAchievement) : ''}
        </div>

        <div class="public-post-card-footer">
          <span class="public-post-card-action">
            Открыть публикацию
            <span aria-hidden="true">→</span>
          </span>

          <span
            class="post-comments-count"
            aria-label="Комментариев: ${commentsCount}"
            title="Комментариев: ${commentsCount}"
          >
            <span aria-hidden="true">💬</span>
            <strong>${commentsCount}</strong>
          </span>
        </div>
      </article>`;
  }

  function renderPublicPostDetails(postId) {
    const id = String(postId);
    const post = state.publicPostDetails[id];
    const comments = state.postComments[id];
    const loading = state.publicPostLoadingIds.has(id);
    const closeRoute = publicPostCloseRoute();

    if (state.error && !post) {
      return `
        <div class="public-post-overlay" data-close-public-post>
          <section
            class="public-post-dialog public-post-dialog-error"
            role="dialog"
            aria-modal="true"
            aria-labelledby="public-post-title"
          >
            <a
              class="public-post-close"
              href="#${escapeHtml(closeRoute)}"
              data-close-public-post
              aria-label="Закрыть"
            >×</a>
            <h1 id="public-post-title">Не удалось открыть публикацию</h1>
            ${alertHtml('error', state.error)}
            <a class="btn" href="#${escapeHtml(closeRoute)}" data-close-public-post>
              Вернуться назад
            </a>
          </section>
        </div>
      `;
    }

    if (!post || loading || !comments) {
      return `
        <div class="public-post-overlay" data-close-public-post>
          <section
            class="public-post-dialog"
            role="dialog"
            aria-modal="true"
            aria-label="Загрузка публикации"
          >
            <a
              class="public-post-close"
              href="#${escapeHtml(closeRoute)}"
              data-close-public-post
              aria-label="Закрыть"
            >×</a>
            <div class="skeleton public-post-dialog-skeleton"></div>
          </section>
        </div>
      `;
    }

    const type = post.type || post.postType || 'TEXT';

    return `
      <div class="public-post-overlay" data-close-public-post>
        <section
          class="public-post-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="public-post-title"
          data-public-post-dialog
        >
          <a
            class="public-post-close"
            href="#${escapeHtml(closeRoute)}"
            data-close-public-post
            aria-label="Закрыть публикацию"
          >×</a>

          ${flashHtml()}
          ${state.error ? alertHtml('error', state.error) : ''}

          <article class="post-card post-detail-card public-post-detail-card">
            <header class="post-card-head">
              ${postAuthorMeta(post)}

              <span class="badge post-type-badge">
                ${postTypeLabel(type)}
              </span>
            </header>

            <h1 id="public-post-title" class="post-detail-title">
              ${escapeHtml(post.title || 'Публикация')}
            </h1>

            ${post.content ? `<p class="post-content">${escapeHtml(post.content)}</p>` : ''}
            ${post.workout ? postWorkoutPayload(post.workout) : ''}
            ${post.recipe ? postRecipePayload(post.recipe) : ''}
            ${post.userAchievement ? postAchievementPayload(post.userAchievement) : ''}
          </article>

          <section class="comments-section public-post-comments">
            <div class="subsection-heading public-post-comments-heading">
              <div>
                <h2>Комментарии</h2>
                <p class="muted">Обсуждение публичной публикации.</p>
              </div>

              <span class="status-pill" aria-label="Количество комментариев">
                ${comments.length}
              </span>
            </div>

            ${comments.length
              ? `
                <div class="comment-list public-post-comment-list" data-public-comment-list>
                  ${comments
                    .map((comment) => commentCard(null, post, comment))
                    .join('')}
                </div>
              `
              : `
                <div class="empty-comments">
                  <strong>Комментариев пока нет</strong>
                  <span>Начните обсуждение первым.</span>
                </div>
              `}

            <form
              id="comment-form"
              class="comment-form public-post-comment-composer"
              data-post-id="${escapeHtml(post.id)}"
            >
              <label class="sr-only" for="public-comment-content">
                Комментарий
              </label>

              <textarea
                id="public-comment-content"
                name="content"
                required
                maxlength="2000"
                placeholder="Напишите комментарий"
              ></textarea>

              <button class="btn" type="submit">Отправить</button>
            </form>
          </section>
        </section>
      </div>
    `;
  }

  function publicPostCloseRoute() {
    return state.publicPostReturnRoute || '/feed';
  }

  function closePublicPost() {
    const route = publicPostCloseRoute();
    state.publicPostReturnRoute = null;
    navigate(route);
  }

  function scrollPublicCommentsToBottom() {
    const scroll = () => {
      const list = app.querySelector(
        '[data-public-comment-list]'
      );

      if (!list) {
        return;
      }

      /*
       * Прокручиваем только список комментариев. Само модальное
       * окно больше не имеет отдельной вертикальной прокрутки,
       * поэтому новый комментарий всегда оказывается полностью
       * виден над закреплённой формой отправки.
       */
      list.scrollTop = Math.max(
        0,
        list.scrollHeight - list.clientHeight
      );
    };

    requestAnimationFrame(() => {
      requestAnimationFrame(scroll);
    });

    /*
     * Повторяем после перерасчёта шрифтов и размеров карточек.
     * Это защищает от недокрутки при другом масштабе браузера.
     */
    window.setTimeout(scroll, 120);
    window.setTimeout(scroll, 320);

    if (document.fonts?.ready) {
      document.fonts.ready.then(scroll).catch(() => {});
    }
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

  function renderCommunitiesRoute(parts) {
    if (parts[1] === 'new') {
      return renderCommunityForm();
    }

    const catalogView =
      !parts[1] || parts[1] === 'all'
        ? 'all'
        : parts[1] === 'mine'
          ? 'mine'
          : null;

    if (catalogView) {
      if (state.error) {
        return (
          pageHeader('Сообщества', 'Не удалось получить данные.') +
          alertHtml('error', state.error)
        );
      }

      if (!state.communities || !state.myCommunities) {
        return (
          pageHeader('Сообщества', 'Загружаем список сообществ.') +
          skeletonGrid()
        );
      }

      return renderCommunitiesList(catalogView);
    }

    const communityId = String(parts[1]);
    const community = state.communityDetails[communityId];

    if (state.error && !community) {
      return (
        pageHeader('Сообщество', 'Не удалось получить данные.') +
        alertHtml('error', state.error)
      );
    }

    if (
      !community ||
      !state.communityMembers[communityId] ||
      !state.communityPosts[communityId]
    ) {
      return (
        pageHeader(
          'Сообщество',
          'Загружаем участников и публикации.'
        ) + skeletonGrid()
      );
    }

    if (parts[2] === 'edit') {
      return renderCommunityForm(community);
    }

    if (parts[2] === 'posts' && parts[3] === 'new') {
      return renderCommunityPostForm(community);
    }

    if (parts[2] === 'posts' && parts[3]) {
      return renderCommunityPostDetails(community, parts[3]);
    }

    return renderCommunityDetails(community);
  }

  function renderCommunitiesList(activeView = 'all') {
    const communities = state.communities || [];
    const myCommunities = state.myCommunities || [];
    const ownedCommunities = myCommunities.filter(isCommunityOwner);
    const mineActive = activeView === 'mine';
    const visibleCommunities = mineActive ? ownedCommunities : communities;

    return `
      ${flashHtml()}

      <section class="section-header community-catalog-header">
        <div>
          <p class="eyebrow">Общение по интересам</p>

          <h1 style="font-size: clamp(38px, 5vw, 64px);">
            Сообщества
          </h1>

          <p>
            Находите единомышленников, публикуйте записи
            и обсуждайте их в комментариях.
          </p>
        </div>

        <a class="btn" href="#/communities/new">
          Создать сообщество
        </a>
      </section>

      <nav class="community-catalog-tabs" aria-label="Разделы сообществ">
        ${communityCatalogTab(
          '/communities',
          'Все сообщества',
          communities.length,
          !mineActive
        )}
        ${communityCatalogTab(
          '/communities/mine',
          'Мои сообщества',
          ownedCommunities.length,
          mineActive
        )}
      </nav>

      <section class="community-section community-catalog-section">
        <div class="subsection-heading">
          <div>
            <h2>${mineActive ? 'Мои сообщества' : 'Все сообщества'}</h2>

            <p class="muted">
              ${mineActive
                ? 'Сообщества, которые вы создали.'
                : 'Открытый каталог сообществ Т-Здоровья.'}
            </p>
          </div>

          <span class="status-pill">
            ${visibleCommunities.length}
          </span>
        </div>

        ${visibleCommunities.length
          ? `
              <div class="community-grid">
                ${visibleCommunities.map(communityCard).join('')}
              </div>
            `
          : emptyState(
              mineActive
                ? 'Вы пока не создали ни одного сообщества'
                : 'Сообществ пока нет',
              mineActive
                ? 'Создайте сообщество и пригласите единомышленников.'
                : 'Станьте первым автором сообщества.',
              '#/communities/new',
              'Создать сообщество'
            )}
      </section>
    `;
  }

  function communityCatalogTab(path, label, count, active) {
    return `
      <a
        class="community-catalog-tab${active ? ' active' : ''}"
        href="#${path}"
        ${active ? 'aria-current="page"' : ''}
      >
        <span>${escapeHtml(label)}</span>
        <strong>${Number(count || 0)}</strong>
      </a>
    `;
  }

  function communityCard(community) {
    const owner = isCommunityOwner(community);

    const membership = owner
      ? 'Владелец'
      : community.currentUserMember
        ? 'Участник'
        : 'Можно вступить';

    return `
      <a
        class="card community-card${
          community.currentUserMember ? ' joined' : ''
        }"
        href="#/communities/${encodeURIComponent(community.id)}"
      >
        <div class="community-card-top">
          <span class="community-icon">👥</span>

          <span class="badge">
            ${escapeHtml(membership)}
          </span>
        </div>

        <div>
          <h3>
            ${escapeHtml(
              community.communityName || 'Сообщество'
            )}
          </h3>

          <p class="muted community-description">
            ${escapeHtml(
              community.description ||
                'Описание пока не добавлено.'
            )}
          </p>
        </div>

        <div class="item-meta">
          <span>
            ${Number(community.membersCount || 0)}
            ${plural(
              community.membersCount || 0,
              'участник',
              'участника',
              'участников'
            )}
          </span>

          <span>
            ${formatDateShort(community.createdAt)}
          </span>
        </div>
      </a>
    `;
  }

  function isCommunityOwner(community) {
    return (
      String(community?.ownerId || '') ===
      String(state.user?.id || '')
    );
  }

  function renderCommunityDetails(community) {
    const id = String(community.id);

    const members = state.communityMembers[id] || [];
    const posts = state.communityPosts[id] || [];

    const owner = isCommunityOwner(community);

    return `
      ${flashHtml()}

      ${state.error ? alertHtml('error', state.error) : ''}

      <section class="section-header community-detail-header">
        <div>
          <p class="eyebrow">
            ${
              owner
                ? 'Ваше сообщество'
                : community.currentUserMember
                  ? 'Вы участник'
                  : 'Открытое сообщество'
            }
          </p>

          <h1 style="font-size: clamp(36px, 5vw, 60px);">
            ${escapeHtml(community.communityName)}
          </h1>

          <p>
            ${escapeHtml(
              community.description ||
                'Описание пока не добавлено.'
            )}
          </p>
        </div>

        <div class="btn-row">
          ${
            community.currentUserMember
              ? `
                <a
                  class="btn"
                  href="#/communities/${encodeURIComponent(id)}/posts/new"
                >
                  Новый пост
                </a>
              `
              : `
                <button
                  class="btn"
                  type="button"
                  data-join-community="${escapeHtml(id)}"
                >
                  Вступить
                </button>
              `
          }

          ${
            owner
              ? `
                <a
                  class="btn ghost"
                  href="#/communities/${encodeURIComponent(id)}/edit"
                >
                  Редактировать
                </a>

                <button
                  class="btn danger"
                  type="button"
                  data-delete-community="${escapeHtml(id)}"
                >
                  Удалить
                </button>
              `
              : community.currentUserMember
                ? `
                  <button
                    class="btn ghost"
                    type="button"
                    data-leave-community="${escapeHtml(id)}"
                  >
                    Выйти
                  </button>
                `
                : ''
          }

          <a class="btn ghost" href="#/communities">
            К списку
          </a>
        </div>
      </section>

      <section class="community-summary-grid">
        <article class="card community-stat-card">
          <span class="badge">Участники</span>

          <span class="metric">
            ${Number(community.membersCount || members.length)}
          </span>

          <p class="muted">
            Людей в сообществе
          </p>
        </article>

        <article class="card community-stat-card">
          <span class="badge">Публикации</span>

          <span class="metric">
            ${posts.length}
          </span>

          <p class="muted">
            Текстовых постов
          </p>
        </article>

        <article class="card community-stat-card">
          <span class="badge">Создано</span>

          <h3>
            ${formatDateShort(community.createdAt)}
          </h3>

          <p class="muted">
            Последнее обновление:
            ${formatDateShort(
              community.updatedAt || community.createdAt
            )}
          </p>
        </article>
      </section>

      <section class="community-content-grid">
        <div>
          <div class="subsection-heading">
            <div>
              <h2>Публикации</h2>

              <p class="muted">
                Обсуждения участников сообщества.
              </p>
            </div>

            ${
              community.currentUserMember
                ? `
                  <a
                    class="btn ghost"
                    href="#/communities/${encodeURIComponent(id)}/posts/new"
                  >
                    Написать
                  </a>
                `
                : ''
            }
          </div>

          ${
            posts.length
              ? `
                <div class="community-post-list">
                  ${posts
                    .map((post) =>
                      communityPostCard(community, post)
                    )
                    .join('')}
                </div>
              `
              : emptyState(
                  'Публикаций пока нет',
                  community.currentUserMember
                    ? 'Создайте первый пост сообщества.'
                    : 'После вступления вы сможете создать публикацию.',
                  community.currentUserMember
                    ? `#/communities/${id}/posts/new`
                    : '#/communities',
                  community.currentUserMember
                    ? 'Создать пост'
                    : 'К каталогу'
                )
          }
        </div>

        <aside class="members-panel">
          <div class="subsection-heading">
            <div>
              <h2>Участники</h2>

              <p class="muted">
                Владелец и участники сообщества.
              </p>
            </div>
          </div>

          <div class="member-list">
            ${members
              .map((member) =>
                communityMemberRow(community, member)
              )
              .join('')}
          </div>
        </aside>
      </section>
    `;
  }

  function communityPostCard(community, post) {
    const canDiscuss = community.currentUserMember;

    return `
      <article class="post-card community-post-card">
        <header class="post-card-head">
          ${postAuthorMeta(post)}

          <span class="badge post-type-badge">
            Пост сообщества
          </span>
        </header>

        <div class="post-card-body">
          <h3 class="post-card-title">
            ${escapeHtml(
              post.title || 'Пост без заголовка'
            )}
          </h3>

          ${
            post.content
              ? `
                <p class="post-content">
                  ${escapeHtml(post.content)}
                </p>
              `
              : ''
          }
        </div>

        ${
          canDiscuss
            ? `
              <a
                class="btn ghost discussion-link"
                href="#/communities/${encodeURIComponent(
                  community.id
                )}/posts/${encodeURIComponent(post.id)}"
              >
                Открыть обсуждение
              </a>
            `
            : ''
        }
      </article>
    `;
  }

  function communityMemberRow(community, member) {
    const isCurrent =
      String(member.userId) === String(state.user?.id);

    const username = String(member.username || '');
    const displayUsername = username || 'Пользователь';

    const memberIdentity = `
      <div class="member-avatar">
        ${escapeHtml(getMemberInitials(member))}
      </div>

      <div class="member-info">
        <strong>${escapeHtml(displayUsername)}</strong>
        <small>
          ${communityRoleLabel(member.role)}
          · с ${formatDateShort(member.joinedAt)}
        </small>
      </div>
    `;

    const memberProfile = username
      ? `
          <a
            class="member-profile-link"
            href="#/users/${encodeURIComponent(username)}"
            aria-label="Открыть профиль ${escapeHtml(username)}"
          >
            ${memberIdentity}
          </a>
        `
      : `
          <div class="member-profile-link">
            ${memberIdentity}
          </div>
        `;

    return `
      <div class="member-row">
        ${memberProfile}

        ${
          !isCurrent
            ? `
                <button
                  class="icon-btn"
                  type="button"
                  data-start-chat="${escapeHtml(member.userId)}"
                  title="Начать личный чат"
                >
                  💬
                </button>
              `
            : `
                <span class="badge">Вы</span>
              `
        }
      </div>
    `;
  }

  function renderCommunityForm(community = null) {
    const editing = Boolean(community);

    const cancelHref = editing
      ? `#/communities/${encodeURIComponent(community.id)}`
      : '#/communities';

    return `
      <section class="section-header">
        <div>
          <p class="eyebrow">
            ${
              editing
                ? 'Настройки сообщества'
                : 'Новое сообщество'
            }
          </p>

          <h1 style="font-size: clamp(34px, 5vw, 56px);">
            ${
              editing
                ? 'Редактировать сообщество'
                : 'Создать сообщество'
            }
          </h1>

          <p>
            ${
              editing
                ? 'Измените название или описание.'
                : 'Соберите людей вокруг общей цели, спорта или полезной привычки.'
            }
          </p>
        </div>

        <a class="btn ghost" href="${cancelHref}">
          Отмена
        </a>
      </section>

      ${state.error ? alertHtml('error', state.error) : ''}

      <form
        id="community-form"
        class="form-card"
        ${editing
          ? `data-id="${escapeHtml(community.id)}"`
          : ''}
      >
        <div class="forms-grid">
          <div class="form-field full-width">
            <label for="community-name">
              Название
            </label>

            <input
              id="community-name"
              name="communityName"
              required
              maxlength="64"
              value="${escapeHtml(
                community?.communityName || ''
              )}"
              placeholder="Бег по утрам"
            />
          </div>

          <div class="form-field full-width">
            <label for="community-description">
              Описание
            </label>

            <textarea
              id="community-description"
              name="description"
              maxlength="1024"
              placeholder="Расскажите, кому подойдет сообщество и чем вы будете заниматься"
            >${escapeHtml(
              community?.description || ''
            )}</textarea>
          </div>

          <div class="form-actions">
            <a class="btn ghost" href="${cancelHref}">
              Отмена
            </a>

            <button class="btn" type="submit">
              ${editing ? 'Сохранить' : 'Создать'}
            </button>
          </div>
        </div>
      </form>
    `;
  }

  function renderCommunityPostForm(community) {
    if (!community.currentUserMember) {
      return (
        pageHeader(
          'Новый пост',
          'Сначала вступите в сообщество.'
        ) +
        emptyState(
          'Требуется участие',
          'Публиковать записи могут только участники сообщества.',
          `#/communities/${community.id}`,
          'Открыть сообщество'
        )
      );
    }

    return `
      <section class="section-header">
        <div>
          <p class="eyebrow">
            ${escapeHtml(community.communityName)}
          </p>

          <h1 style="font-size: clamp(34px, 5vw, 56px);">
            Новый пост
          </h1>

          <p>
            Публикация будет видна участникам сообщества.
          </p>
        </div>

        <a
          class="btn ghost"
          href="#/communities/${encodeURIComponent(community.id)}"
        >
          Отмена
        </a>
      </section>

      ${state.error ? alertHtml('error', state.error) : ''}

      <form
        id="community-post-form"
        class="form-card"
        data-community-id="${escapeHtml(community.id)}"
      >
        <div class="forms-grid">
          <div class="form-field full-width">
            <label for="community-post-title">
              Заголовок
            </label>

            <input
              id="community-post-title"
              name="title"
              required
              maxlength="128"
              placeholder="Кто завтра идет на пробежку?"
            />
          </div>

          <div class="form-field full-width">
            <label for="community-post-content">
              Текст
            </label>

            <textarea
              id="community-post-content"
              name="content"
              required
              maxlength="2000"
              placeholder="Расскажите подробнее"
            ></textarea>
          </div>

          <div class="form-actions">
            <a
              class="btn ghost"
              href="#/communities/${encodeURIComponent(community.id)}"
            >
              Отмена
            </a>

            <button class="btn" type="submit">
              Опубликовать
            </button>
          </div>
        </div>
      </form>
    `;
  }

  function renderCommunityPostDetails(community, postId) {
    const communityId = String(community.id);

    const posts = state.communityPosts[communityId] || [];
    const post = findById(posts, postId);

    if (!post) {
      return (
        pageHeader(
          'Публикация',
          'Пост не найден.'
        ) +
        emptyState(
          'Пост не найден',
          'Вернитесь к публикациям сообщества.',
          `#/communities/${community.id}`,
          'К сообществу'
        )
      );
    }

    if (!community.currentUserMember) {
      return (
        pageHeader(
          post.title || 'Публикация',
          'Комментарии доступны участникам.'
        ) +
        emptyState(
          'Вступите в сообщество',
          'После вступления вы сможете читать обсуждение и оставлять комментарии.',
          `#/communities/${community.id}`,
          'Открыть сообщество'
        )
      );
    }

    const comments = state.postComments[String(post.id)];

    return `
      ${flashHtml()}

      ${state.error ? alertHtml('error', state.error) : ''}

      <section class="section-header">
        <div>
          <p class="eyebrow">
            ${escapeHtml(community.communityName)}
          </p>

          <h1 style="font-size: clamp(34px, 5vw, 56px);">
            ${escapeHtml(post.title || 'Публикация')}
          </h1>

          <p>
            ${publicUserLink(post.username)}
            · ${formatDateTime(post.createdAt)}
          </p>
        </div>

        <a
          class="btn ghost"
          href="#/communities/${encodeURIComponent(community.id)}"
        >
          К сообществу
        </a>
      </section>

      <article class="post-card post-detail-card">
        <p class="post-content">
          ${escapeHtml(post.content || '')}
        </p>
      </article>

      <section class="comments-section">
        <div class="subsection-heading">
          <div>
            <h2>Комментарии</h2>

            <p class="muted">
              Обсуждение публикации.
            </p>
          </div>

          <span class="status-pill">
            ${comments?.length || 0}
          </span>
        </div>

        ${
          comments
            ? comments.length
              ? `
                <div class="comment-list">
                  ${comments
                    .map((comment) =>
                      commentCard(
                        community,
                        post,
                        comment
                      )
                    )
                    .join('')}
                </div>
              `
              : `
                <div class="empty-comments">
                  <strong>
                    Комментариев пока нет
                  </strong>

                  <span>
                    Начните обсуждение первым.
                  </span>
                </div>
              `
            : `
              <div class="skeleton comment-skeleton"></div>
            `
        }

        <form
          id="comment-form"
          class="comment-form"
          data-post-id="${escapeHtml(post.id)}"
        >
          <label
            class="sr-only"
            for="comment-content"
          >
            Комментарий
          </label>

          <textarea
            id="comment-content"
            name="content"
            required
            maxlength="2000"
            placeholder="Напишите комментарий"
          ></textarea>

          <button class="btn" type="submit">
            Отправить
          </button>
        </form>
      </section>
    `;
  }

  function commentCard(community, post, comment) {
    const currentUserId = String(state.user?.id || '');

    const commentAuthorId = String(
      comment.authorId || ''
    );

    const postAuthorId = String(
      post.authorId || ''
    );

    const canDelete =
      commentAuthorId === currentUserId ||
      postAuthorId === currentUserId ||
      isCommunityOwner(community);

    const username =
      comment.username || 'Пользователь';

    const firstLetter =
      username.trim().charAt(0).toUpperCase() || 'П';

    return `
      <article class="comment-card">
        <div class="comment-avatar">
          ${escapeHtml(firstLetter)}
        </div>

        <div class="comment-body">
          <div class="comment-heading">
            <strong>
              ${escapeHtml(username)}
            </strong>

            <span>
              ${formatDateTime(comment.createdAt)}
            </span>
          </div>

          <p>
            ${escapeHtml(comment.content || '')}
          </p>
        </div>

        ${
          canDelete
            ? `
              <button
                class="icon-btn danger-icon"
                type="button"
                data-delete-comment="${escapeHtml(comment.id)}"
                data-post-id="${escapeHtml(post.id)}"
                aria-label="Удалить комментарий"
                title="Удалить комментарий"
              >
                ×
              </button>
            `
            : ''
        }
      </article>
    `;
  }

  function renderChatsRoute(parts) {
    if (parts[1] === 'new') {
      return renderDirectChatForm();
    }

    if (!parts[1]) {
      if (state.error) {
        return (
          pageHeader(
            'Чаты',
            'Не удалось получить список чатов.'
          ) +
          alertHtml('error', state.error)
        );
      }

      if (!state.chats) {
        return (
          pageHeader(
            'Чаты',
            'Загружаем личные переписки.'
          ) +
          skeletonGrid()
        );
      }

      return renderChatsList();
    }

    const chatId = String(parts[1]);

    if (state.error && !state.chatMessages[chatId]) {
      return (
        pageHeader(
          'Чат',
          'Не удалось загрузить переписку.'
        ) +
        alertHtml('error', state.error) +
        emptyState(
          'Сообщения недоступны',
          'Попробуйте обновить страницу или вернитесь к списку чатов.',
          '#/chats',
          'К списку чатов'
        )
      );
    }

    if (!state.chats || !state.chatMessages[chatId]) {
      return (
        pageHeader(
          'Чат',
          'Загружаем сообщения.'
        ) +
        `
          <div class="chat-layout">
            <div class="skeleton"></div>
            <div class="skeleton"></div>
          </div>
        `
      );
    }

    const chat = findById(state.chats, chatId);

    if (!chat) {
      return (
        pageHeader(
          'Чат',
          'Переписка не найдена.'
        ) +
        emptyState(
          'Чат не найден',
          'Вернитесь к списку доступных переписок.',
          '#/chats',
          'К списку чатов'
        )
      );
    }

    return renderChatDetails(chatId, chat);
  }

  function renderChatsList() {
    const chats = state.chats || [];

    return `
      ${flashHtml()}

      <section class="section-header">
        <div>
          <p class="eyebrow">
            Личные сообщения
          </p>

          <h1 style="font-size: clamp(38px, 5vw, 64px);">
            Чаты
          </h1>

          <p>
            Переписки один на один с пользователями
            Т-Здоровья.
          </p>
        </div>

        <a class="btn" href="#/chats/new">
          Новый чат
        </a>
      </section>

      ${
        chats.length
          ? `
            <div class="chat-list">
              ${chats.map(chatListItem).join('')}
            </div>
          `
          : emptyState(
              'Личных чатов пока нет',
              'Начните переписку по UUID пользователя или напишите участнику сообщества.',
              '#/chats/new',
              'Начать чат'
            )
      }
    `;
  }

  function chatListItem(chat) {
    const lastMessage = chat.lastMessage;

    const companionName =
      chat.companionUsername ||
      chat.companionEmail ||
      'Собеседник';

    return `
      <a
        class="chat-list-item"
        href="#/chats/${encodeURIComponent(chat.id)}"
      >
        <div class="chat-avatar">
          ${escapeHtml(getChatInitials(chat))}
        </div>

        <div class="chat-preview">
          <div class="chat-preview-heading">
            <strong>
              ${escapeHtml(companionName)}
            </strong>

            <span>
              ${formatChatTime(
                lastMessage?.sentAt || chat.createdAt
              )}
            </span>
          </div>

          <p>
            ${escapeHtml(
              lastMessage?.content ||
                'Сообщений пока нет'
            )}
          </p>

          <small>
            ${escapeHtml(chat.companionEmail || '')}
          </small>
        </div>

        <span class="chat-arrow" aria-hidden="true">
          ›
        </span>
      </a>
    `;
  }

  function renderDirectChatForm() {
    return `
      <section class="section-header">
        <div>
          <p class="eyebrow">
            Новая переписка
          </p>

          <h1 style="font-size: clamp(34px, 5vw, 56px);">
            Начать чат
          </h1>

          <p>
            Укажите UUID пользователя. Также чат можно
            начать кнопкой 💬 в списке участников сообщества.
          </p>
        </div>

        <a class="btn ghost" href="#/chats">
          Отмена
        </a>
      </section>

      ${state.error ? alertHtml('error', state.error) : ''}

      <form
        id="direct-chat-form"
        class="form-card"
      >
        <div class="forms-grid">
          <div class="form-field full-width">
            <label for="recipient-id">
              UUID собеседника
            </label>

            <input
              id="recipient-id"
              name="recipientId"
              required
              maxlength="36"
              pattern="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
              placeholder="ccd11ba4-3a88-42cb-82f7-19d9e4fdb478"
            />
          </div>

          <div class="form-actions">
            <a class="btn ghost" href="#/chats">
              Отмена
            </a>

            <button class="btn" type="submit">
              Открыть чат
            </button>
          </div>
        </div>
      </form>
    `;
  }

  function renderChatDetails(chatId, chat) {
    const messages =
      state.chatMessages[chatId] || [];

    const companionUsername = String(
      chat?.companionUsername || ''
    );

    const companion =
      companionUsername ||
      chat?.companionEmail ||
      'Собеседник';

    const companionIdentity = `
      <div class="chat-avatar">
        ${escapeHtml(getChatInitials(chat))}
      </div>

      <div>
        <h2>${escapeHtml(companion)}</h2>
        <p>
          ${escapeHtml(
            chat?.companionEmail || 'Личный чат'
          )}
        </p>
      </div>
    `;

    const companionProfile = companionUsername
      ? `
          <a
            class="chat-person chat-person-link"
            href="#/users/${encodeURIComponent(companionUsername)}"
            aria-label="Открыть профиль ${escapeHtml(companionUsername)}"
          >
            ${companionIdentity}
          </a>
        `
      : `
          <div class="chat-person">
            ${companionIdentity}
          </div>
        `;

    return `
      ${flashHtml()}

      ${state.error ? alertHtml('error', state.error) : ''}

      <section class="chat-page-header">
        <a
          class="btn ghost chat-back"
          href="#/chats"
        >
          ← Все чаты
        </a>

        ${companionProfile}

        <button
          class="btn ghost"
          type="button"
          data-reload
        >
          Обновить
        </button>
      </section>

      <section class="chat-window">
        <div class="messages" data-messages>
          ${
            messages.length
              ? messages.map(messageBubble).join('')
              : `
                <div class="chat-empty">
                  <strong>
                    Сообщений пока нет
                  </strong>

                  <span>
                    Поздоровайтесь с собеседником.
                  </span>
                </div>
              `
          }
        </div>

        <form
          id="direct-message-form"
          class="message-form"
          data-chat-id="${escapeHtml(chatId)}"
        >
          <label
            class="sr-only"
            for="message-content"
          >
            Сообщение
          </label>

          <textarea
            id="message-content"
            name="content"
            required
            maxlength="2000"
            rows="1"
            placeholder="Напишите сообщение"
          ></textarea>

          <button class="btn" type="submit">
            Отправить
          </button>
        </form>
      </section>
    `;
  }

  function messageBubble(message) {
    const own =
      String(message.senderId) ===
      String(state.user?.id);

    return `
      <div class="message-row${own ? ' own' : ''}">
        <article class="message-bubble">
          <header class="message-bubble-meta">
            <span class="message-author">
              ${
                own
                  ? 'Вы'
                  : escapeHtml(
                      message.senderUsername ||
                        'Собеседник'
                    )
              }
            </span>

            <time datetime="${escapeHtml(message.sentAt || '')}">
              ${formatChatTime(message.sentAt)}
            </time>
          </header>

          <p>${escapeHtml(message.content || '')}</p>
        </article>
      </div>
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

  function transientNoticeHtml() {
    const notice = state.transientNotice;

    if (!notice) {
      return '';
    }

    const title = notice.type === 'error'
      ? 'Не удалось выполнить действие'
      : 'Готово';

    return `
      <aside
        class="transient-notice ${escapeHtml(notice.type)}"
        role="status"
        aria-live="polite"
      >
        <div>
          <strong>${escapeHtml(title)}</strong>
          <span>${escapeHtml(notice.message)}</span>
        </div>

        <button
          type="button"
          class="transient-notice-close"
          data-dismiss-transient-notice
          aria-label="Закрыть уведомление"
        >×</button>
      </aside>
    `;
  }

  function dismissTransientNotice() {
    if (transientNoticeTimer) {
      clearTimeout(transientNoticeTimer);
      transientNoticeTimer = null;
    }

    state.transientNotice = null;
    renderRoute(false);
  }

  function showTransientNotice(
    type,
    message,
    duration = 5600
  ) {
    if (transientNoticeTimer) {
      clearTimeout(transientNoticeTimer);
    }

    const id = `${Date.now()}-${Math.random()}`;

    state.transientNotice = {
      id,
      type,
      message,
    };

    renderRoute(false);

    transientNoticeTimer = window.setTimeout(() => {
      if (state.transientNotice?.id !== id) {
        return;
      }

      state.transientNotice = null;
      transientNoticeTimer = null;
      renderRoute(false);
    }, duration);
  }

  function publicationErrorMessage(type, error) {
    const message = friendlyError(error);

    if (/already published|уже опубликован/i.test(message)) {
      const duplicateMessages = {
        workout: 'Эта тренировка уже опубликована в ленте.',
        recipe: 'Этот рецепт уже опубликован в ленте.',
        achievement: 'Это достижение уже опубликовано в ленте.',
      };

      return duplicateMessages[type] ||
        'Эта запись уже опубликована в ленте.';
    }

    return message;
  }

  function logoHtml() {
    return `<span class="logo-mark" aria-hidden="true"><span class="logo-shield">T</span></span>`;
  }

  function bindGlobalActions() {
    app.querySelectorAll('[data-auth]').forEach((button) => button.addEventListener('click', () => startAuth(button.dataset.auth)));
    app.querySelectorAll('[data-logout]').forEach((button) => button.addEventListener('click', logout));
    app.querySelectorAll('[data-reload]').forEach((button) => button.addEventListener('click', () => reloadCurrentSection()));
    app.querySelectorAll('[data-dismiss-transient-notice]').forEach((button) => {
      button.addEventListener('click', dismissTransientNotice);
    });

    app.querySelectorAll('[data-accept-achievement]').forEach((button) => {
      button.addEventListener('click', () => {
        showNextAchievementOrClose();
        renderRoute(false);
      });
    });

    app.querySelectorAll('[data-share-achievement]').forEach((button) => {
      button.addEventListener('click', async () => {
        button.disabled = true;

        try {
          await shareAchievement(state.achievementModal);
        } finally {
          button.disabled = false;
        }
      });
    });

    app.querySelectorAll('[data-show-achievement]').forEach((button) => {
      button.addEventListener('click', () => {
        const item = findAchievementById(state.achievements || [], button.dataset.showAchievement);
        if (!item) state.flash = 'Достижение не найдено в данных backend.';
        else showAchievementModal(item);
        renderRoute(false);
      });
    });

    app.querySelectorAll('[data-share-workout]').forEach((button) => {
      button.addEventListener('click', async () => {
        button.disabled = true;

        try {
          await shareEntity(
            'workout',
            button.dataset.shareWorkout
          );
        } finally {
          button.disabled = false;
        }
      });
    });

    app.querySelectorAll('[data-share-recipe]').forEach((button) => {
      button.addEventListener('click', async () => {
        button.disabled = true;

        try {
          await shareEntity(
            'recipe',
            button.dataset.shareRecipe
          );
        } finally {
          button.disabled = false;
        }
      });
    });
    app.querySelectorAll('[data-share-achievement-entry]').forEach((button) => {
      button.addEventListener('click', async () => {
        button.disabled = true;

        try {
          await shareAchievement(
            findAchievementById(
              state.achievements || [],
              button.dataset.shareAchievementEntry
            )
          );
        } finally {
          button.disabled = false;
        }
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

    app.querySelectorAll('[data-delete-recipe]').forEach((button) => {
      button.addEventListener('click', async () => {
        if (!confirm('Удалить этот рецепт?')) return;
        await deleteResource('/api/recipes/' + encodeURIComponent(button.dataset.deleteRecipe), 'Рецепт удален.');
      });
    });

    app.querySelectorAll('[data-join-community]').forEach((button) => {
      button.addEventListener('click', async () => {
        await joinCommunity(
          button.dataset.joinCommunity,
          button
        );
      });
    });

    app.querySelectorAll('[data-leave-community]').forEach((button) => {
      button.addEventListener('click', async () => {
        if (!confirm('Выйти из этого сообщества?')) {
          return;
        }

        await leaveCommunity(
          button.dataset.leaveCommunity,
          button
        );
      });
    });

    app.querySelectorAll('[data-delete-community]').forEach((button) => {
      button.addEventListener('click', async () => {
        if (
          !confirm(
            'Удалить сообщество? Публикации и комментарии также станут недоступны.'
          )
        ) {
          return;
        }

        await deleteCommunity(
          button.dataset.deleteCommunity,
          button
        );
      });
    });

    app.querySelectorAll('[data-delete-comment]').forEach((button) => {
      button.addEventListener('click', async () => {
        if (!confirm('Удалить этот комментарий?')) {
          return;
        }

        await deleteComment(
          button.dataset.postId,
          button.dataset.deleteComment,
          button
        );
      });
    });

    app.querySelectorAll('[data-start-chat]').forEach((button) => {
      button.addEventListener('click', async () => {
        const recipientId = button.dataset.startChat;

        try {
          button.disabled = true;
          state.error = null;

          await startDirectChat(recipientId);
        } catch (error) {
          state.error = friendlyError(error);
          renderRoute(false);
        } finally {
          button.disabled = false;
        }
      });
    });

    app.querySelectorAll('[data-open-public-post]').forEach((card) => {
      const open = () => {
        const targetRoute = `/feed/${card.dataset.openPublicPost}`;
        const currentRoute = normalizedRoute();

        if (currentRoute !== targetRoute) {
          state.publicPostReturnRoute = currentRoute;
        }

        navigate(targetRoute);
      };

      card.addEventListener('click', (event) => {
        if (
          event.target.closest(
            'a, button, input, textarea, select'
          )
        ) {
          return;
        }

        open();
      });

      card.addEventListener('keydown', (event) => {
        if (
          event.target.closest(
            'a, button, input, textarea, select'
          )
        ) {
          return;
        }

        if (event.key !== 'Enter' && event.key !== ' ') {
          return;
        }

        event.preventDefault();
        open();
      });
    });

    app.querySelectorAll('[data-close-public-post]').forEach((control) => {
      control.addEventListener('click', (event) => {
        if (
          control.classList.contains('public-post-overlay') &&
          event.target !== control
        ) {
          return;
        }

        event.preventDefault();
        closePublicPost();
      });
    });

    app.querySelectorAll('[data-public-post-dialog]').forEach((dialog) => {
      dialog.addEventListener('click', (event) => event.stopPropagation());
    });

    app.querySelectorAll('[data-open-public-user-communities]').forEach((button) => {
      button.addEventListener('click', () => {
        state.publicUserCommunityDialogUsername =
          button.dataset.openPublicUserCommunities;
        renderRoute(false);
      });
    });

    app.querySelectorAll('[data-close-public-user-communities]').forEach((control) => {
      control.addEventListener('click', (event) => {
        if (
          control.classList.contains('public-user-community-overlay') &&
          event.target !== control
        ) {
          return;
        }

        event.preventDefault();
        state.publicUserCommunityDialogUsername = null;
        renderRoute(false);
      });
    });

    app.querySelectorAll('[data-public-user-community-dialog]').forEach((dialog) => {
      dialog.addEventListener('click', (event) => event.stopPropagation());
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
    const workoutForm =
      document.getElementById('workout-form');

    if (workoutForm) {
      workoutForm.addEventListener(
        'submit',
        submitWorkout
      );
    }

    const foodForm =
      document.getElementById('food-form');

    if (foodForm) {
      foodForm.addEventListener(
        'submit',
        submitFood
      );
    }

    const recipeForm =
      document.getElementById('recipe-form');

    if (recipeForm) {
      recipeForm.addEventListener(
        'submit',
        submitRecipe
      );
    }

    const postForm =
      document.getElementById('post-form');

    if (postForm) {
      postForm.addEventListener(
        'submit',
        submitPost
      );
    }

    const communityForm =
      document.getElementById('community-form');

    if (communityForm) {
      communityForm.addEventListener(
        'submit',
        submitCommunity
      );
    }

    const communityPostForm =
      document.getElementById(
        'community-post-form'
      );

    if (communityPostForm) {
      communityPostForm.addEventListener(
        'submit',
        submitCommunityPost
      );
    }

    const commentForm =
      document.getElementById('comment-form');

    if (commentForm) {
      commentForm.addEventListener(
        'submit',
        submitComment
      );
    }

    const directChatForm =
      document.getElementById(
        'direct-chat-form'
      );

    if (directChatForm) {
      directChatForm.addEventListener(
        'submit',
        submitDirectChat
      );
    }

    const directMessageForm =
      document.getElementById(
        'direct-message-form'
      );

    if (directMessageForm) {
      directMessageForm.addEventListener(
        'submit',
        submitDirectMessage
      );
    }

    scrollChatToBottom();
  }

  async function reloadCurrentSection() {
    state.error = null;
    const parts = routeParts();
    if (
        parts[0] === 'users' &&
        parts[1]
    ) {
      const username = parts[1];

      delete state.publicUserProfiles[username];
      delete state.publicUserProfileErrors[username];

      await loadPublicUserProfile(
          username,
          true
      );

      return;
    }

    if (parts[0] === 'feed') {
      if (parts[1] && parts[1] !== 'new') {
        const postId = String(parts[1]);
        delete state.publicPostDetails[postId];
        delete state.postComments[postId];
        await loadPublicPost(postId, true);
        return;
      }

      state.feedPosts = null;
      await loadFeed(true);
      return;
    }

    if (parts[0] === 'communities') {
      if (
        !parts[1] ||
        parts[1] === 'all' ||
        parts[1] === 'mine'
      ) {
        state.communities = null;
        state.myCommunities = null;
        await loadCommunities(true);
        return;
      }

      if (parts[1] === 'new') {
        renderRoute(false);
        return;
      }

      const communityId =
        String(parts[1]);
      if (
        parts[2] === 'posts' &&
        parts[3] &&
        parts[3] !== 'new'
      ) {
        await Promise.all([
          loadCommunity(
            communityId,
            true
          ),
          loadComments(
            parts[3],
            true
          ),
        ]);
        return;
      }

      await loadCommunity(
        communityId,
        true
      );
      return;
    }

    if (parts[0] === 'chats') {
      if (!parts[1]) {
        state.chats = null;
        await loadChats(true);
        return;
      }

      if (parts[1] === 'new') {
        renderRoute(false);
        return;
      }
      const chatId =
        String(parts[1]);
      await Promise.all([
        loadChats(true),
        loadChatMessages(
          chatId,
          true
        ),
      ]);
      return;
    }

    await loadDashboard(true);
  }

  async function joinCommunity(
    communityId,
    button = null
  ) {
    const id = String(communityId);

    try {
      if (button) {
        button.disabled = true;
      }

      state.error = null;

      const community = await apiFetch(
        `/api/communities/${encodeURIComponent(id)}/join`,
        {
          method: 'POST',
        }
      );

      state.communityDetails[id] = community;

      upsertCommunityCollections(community);

      state.flash =
        'Вы вступили в сообщество.';

      await loadCommunity(id, true);
    } catch (error) {
      state.error = friendlyError(error);
      renderRoute(false);
    } finally {
      if (button) {
        button.disabled = false;
      }
    }
  }

  async function leaveCommunity(
    communityId,
    button = null
  ) {
    const id = String(communityId);

    try {
      if (button) {
        button.disabled = true;
      }

      state.error = null;

      await apiFetch(
        `/api/communities/${encodeURIComponent(id)}/leave`,
        {
          method: 'DELETE',
        }
      );

      if (state.myCommunities) {
        state.myCommunities =
          state.myCommunities.filter(
            (community) =>
              String(community.id) !== id
          );
      }

      state.flash =
        'Вы вышли из сообщества.';

      await loadCommunity(id, true);
    } catch (error) {
      state.error = friendlyError(error);
      renderRoute(false);
    } finally {
      if (button) {
        button.disabled = false;
      }
    }
  }

  async function deleteCommunity(
    communityId,
    button = null
  ) {
    const id = String(communityId);

    try {
      if (button) {
        button.disabled = true;
      }

      state.error = null;

      await apiFetch(
        `/api/communities/${encodeURIComponent(id)}`,
        {
          method: 'DELETE',
        }
      );

      const deletedPosts =
        state.communityPosts[id] || [];

      deletedPosts.forEach((post) => {
        if (!post?.id) {
          return;
        }

        const postId = String(post.id);

        delete state.postComments[postId];

        state.commentLoadingIds.delete(postId);
      });

      delete state.communityDetails[id];
      delete state.communityMembers[id];
      delete state.communityPosts[id];

      state.communityLoadingIds.delete(id);
      state.communities = null;
      state.myCommunities = null;

      state.flash =
        'Сообщество удалено.';

      navigate('/communities');
    } catch (error) {
      state.error = friendlyError(error);
      renderRoute(false);
    } finally {
      if (button) {
        button.disabled = false;
      }
    }
  }

  async function deleteComment(
    postId,
    commentId,
    button = null
  ) {
    const normalizedPostId =
      String(postId);

    const normalizedCommentId =
      String(commentId);

    try {
      if (button) {
        button.disabled = true;
      }

      state.error = null;

      await apiFetch(
        `/api/posts/${encodeURIComponent(
          normalizedPostId
        )}/comments/${encodeURIComponent(
          normalizedCommentId
        )}`,
        {
          method: 'DELETE',
        }
      );

      state.postComments[
        normalizedPostId
      ] = (
        state.postComments[
          normalizedPostId
        ] || []
      ).filter(
        (comment) =>
          String(comment.id) !==
          normalizedCommentId
      );

      updatePostCommentsCount(
        normalizedPostId,
        state.postComments[normalizedPostId].length
      );

      state.flash =
        'Комментарий удалён.';

      renderRoute(false);
    } catch (error) {
      state.error = friendlyError(error);
      renderRoute(false);
    } finally {
      if (button) {
        button.disabled = false;
      }
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

  async function submitCommunity(event) {
    event.preventDefault();

    const form = event.currentTarget;
    const formData = new FormData(form);
    const communityId = form.dataset.id;

    const payload = {
      communityName: stringValue(
        formData.get('communityName')
      ),
      description: stringValue(
        formData.get('description')
      ),
    };

    try {
      disableForm(form, true);
      state.error = null;

      const community = await apiFetch(
        communityId
          ? `/api/communities/${encodeURIComponent(
              communityId
            )}`
          : '/api/communities',
        {
          method: communityId ? 'PATCH' : 'POST',
          body: JSON.stringify(payload),
        }
      );

      const id = String(community.id);

      state.communityDetails[id] = community;

      upsertCommunityCollections(community);

      state.flash = communityId
        ? 'Сообщество обновлено.'
        : 'Сообщество создано.';

      await loadCommunity(id, true);

      navigate(`/communities/${id}`);
    } catch (error) {
      state.error = friendlyError(error);

      renderProtected(
        renderCommunityForm(
          communityId
            ? state.communityDetails[
                String(communityId)
              ]
            : null
        )
      );
    } finally {
      disableForm(form, false);
    }
  }

  async function submitCommunityPost(event) {
    event.preventDefault();

    const form = event.currentTarget;
    const formData = new FormData(form);

    const communityId =
      form.dataset.communityId;

    const payload = {
      title: stringValue(
        formData.get('title')
      ),
      content: stringValue(
        formData.get('content')
      ),
    };

    try {
      disableForm(form, true);
      state.error = null;

      await apiFetch(
        `/api/communities/${encodeURIComponent(
          communityId
        )}/posts/text`,
        {
          method: 'POST',
          body: JSON.stringify(payload),
        }
      );

      state.flash =
        'Пост опубликован в сообществе.';

      await loadCommunity(
        communityId,
        true
      );

      navigate(
        `/communities/${communityId}`
      );
    } catch (error) {
      state.error = friendlyError(error);

      renderProtected(
        renderCommunityPostForm(
          state.communityDetails[
            String(communityId)
          ]
        )
      );
    } finally {
      disableForm(form, false);
    }
  }

  async function submitComment(event) {
    event.preventDefault();

    const form = event.currentTarget;
    const formData = new FormData(form);

    const postId = form.dataset.postId;

    const content = stringValue(
      formData.get('content')
    );

    try {
      disableForm(form, true);
      state.error = null;

      const comment = await apiFetch(
        `/api/posts/${encodeURIComponent(
          postId
        )}/comments`,
        {
          method: 'POST',
          body: JSON.stringify({
            content,
          }),
        }
      );

      state.postComments[String(postId)] = [
        ...(
          state.postComments[
            String(postId)
          ] || []
        ),
        comment,
      ];

      updatePostCommentsCount(
        postId,
        state.postComments[String(postId)].length
      );

      form.reset();

      renderRoute(false);
      scrollPublicCommentsToBottom();
    } catch (error) {
      state.error = friendlyError(error);
      renderRoute(false);
    } finally {
      disableForm(form, false);
    }
  }

  async function submitDirectChat(event) {
    event.preventDefault();

    const form = event.currentTarget;
    const formData = new FormData(form);

    const recipientId = stringValue(
      formData.get('recipientId')
    );

    try {
      disableForm(form, true);
      state.error = null;

      await startDirectChat(recipientId);
    } catch (error) {
      state.error = friendlyError(error);

      renderProtected(
        renderDirectChatForm()
      );
    } finally {
      disableForm(form, false);
    }
  }

  async function submitDirectMessage(event) {
    event.preventDefault();

    const form = event.currentTarget;
    const formData = new FormData(form);

    const chatId = form.dataset.chatId;

    const content = stringValue(
      formData.get('content')
    );

    try {
      disableForm(form, true);
      state.error = null;

      const message = await apiFetch(
        `/api/direct-chats/${encodeURIComponent(
          chatId
        )}/messages`,
        {
          method: 'POST',
          body: JSON.stringify({
            content,
          }),
        }
      );

      state.chatMessages[String(chatId)] = [
        ...(
          state.chatMessages[
            String(chatId)
          ] || []
        ),
        message,
      ];

      updateChatLastMessage(
        chatId,
        message
      );

      form.reset();

      renderRoute(false);
    } catch (error) {
      state.error = friendlyError(error);
      renderRoute(false);
    } finally {
      disableForm(form, false);
    }
  }

  async function startDirectChat(recipientId) {
    const normalizedRecipientId =
      stringValue(recipientId);

    if (!normalizedRecipientId) {
      throw new Error(
        'Укажите UUID пользователя.'
      );
    }

    const chat = await apiFetch(
      '/api/direct-chats',
      {
        method: 'POST',
        body: JSON.stringify({
          recipientId: normalizedRecipientId,
        }),
      }
    );

    state.chats = sortChats(
      uniqueById([
        chat,
        ...(state.chats || []),
      ])
    );

    state.chatMessages[String(chat.id)] =
      state.chatMessages[String(chat.id)] ||
      [];

    state.flash = 'Личный чат открыт.';

    navigate(`/chats/${chat.id}`);

    await loadChatMessages(
      chat.id,
      true
    );

    return chat;
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
      state.error = null;

      await apiFetch(path, { method: 'POST', body: JSON.stringify({ postTitle }) });
      state.flash = type === 'workout' ? 'Тренировка опубликована в ленте.' : 'Рецепт опубликован в ленте.';
      state.feedPosts = null;
      await loadFeed(true);
      navigate('/feed');
    } catch (error) {
      state.error = null;

      showTransientNotice(
        'error',
        publicationErrorMessage(type, error)
      );
    }
  }

  function renderPublicUserProfile(username) {
    const exactUsername = String(username || '');
    const profile = state.publicUserProfiles[exactUsername];
    const error = state.publicUserProfileErrors[exactUsername];
    const loading = state.publicUserProfileLoadingNames.has(exactUsername);

    if (error && !profile) {
      return (
        pageHeader(
          'Профиль пользователя',
          'Не удалось получить публичные данные.'
        ) +
        alertHtml('error', error)
      );
    }

    if (!profile || loading) {
      return (
        pageHeader(
          'Профиль пользователя',
          'Загружаем публичные публикации и сообщества.'
        ) +
        skeletonGrid()
      );
    }

    const publications = Array.isArray(profile.publications)
      ? profile.publications
      : [];

    const communities = Array.isArray(profile.communities)
      ? profile.communities
      : [];

    const visibleCommunities = communities.slice(0, 4);
    const hiddenCommunitiesCount = Math.max(
      0,
      communities.length - visibleCommunities.length
    );

    const workouts = publications.filter(
      (post) => normalizedPostType(post) === 'WORKOUT'
    );

    const recipes = publications.filter(
      (post) => normalizedPostType(post) === 'RECIPE'
    );

    const achievements = publications.filter(
      (post) => normalizedPostType(post) === 'ACHIEVEMENT'
    );

    const fullName = [
      profile.firstName,
      profile.lastName,
    ]
      .filter(Boolean)
      .join(' ');

    const communitiesDialogOpen =
      state.publicUserCommunityDialogUsername === exactUsername;

    return `
      ${flashHtml()}
      ${state.error ? alertHtml('error', state.error) : ''}

      <section class="public-user-header">
        <div class="public-user-avatar" aria-hidden="true">
          ${escapeHtml(getInitials(profile))}
        </div>

        <div class="public-user-identity">
          <p class="eyebrow">Публичный профиль</p>

          <h1>${escapeHtml(profile.username)}</h1>

          <p class="public-user-full-name">
            ${escapeHtml(fullName || 'Пользователь Т-Здоровья')}
          </p>

          <small>
            В Т-Здоровье с ${formatDateShort(profile.memberSince)}
          </small>
        </div>

        <div class="public-user-actions">
          <button
            class="btn"
            type="button"
            data-start-chat="${escapeHtml(profile.userId)}"
          >
            Написать
          </button>
        </div>

        <div class="public-user-stats" aria-label="Публичная статистика пользователя">
          ${publicUserStatCard('🏃', 'Тренировки', workouts.length)}
          ${publicUserStatCard('🍳', 'Рецепты', recipes.length)}
          ${publicUserStatCard('🏅', 'Достижения', achievements.length)}
          ${publicUserStatCard('👥', 'Сообщества', communities.length)}
        </div>
      </section>

      <section class="public-user-layout">
        <section class="public-user-publications">
          <div class="public-user-panel-heading">
            <div>
              <p class="eyebrow">Активность</p>
              <h2>Публикации</h2>
              <p class="muted">
                Публичные тренировки, рецепты, достижения и текстовые посты.
              </p>
            </div>

            <span class="status-pill" aria-label="Количество публикаций">
              ${publications.length}
            </span>
          </div>

          ${publications.length
            ? `
              <div class="feed-list public-user-feed">
                ${publications.map(postCard).join('')}
              </div>
            `
            : `
              <div class="public-user-empty-section">
                <strong>Публикаций пока нет</strong>
                <span>Пользователь ещё ничего не публиковал.</span>
              </div>
            `}
        </section>

        <aside class="public-user-sidebar">
          <section class="public-user-communities">
            <div class="public-user-panel-heading compact">
              <div>
                <p class="eyebrow">Связи</p>
                <h2>Сообщества</h2>
              </div>

              <span class="status-pill" aria-label="Количество сообществ">
                ${communities.length}
              </span>
            </div>

            ${visibleCommunities.length
              ? `
                <div class="public-user-community-list">
                  ${visibleCommunities.map(publicUserCommunityCard).join('')}
                </div>

                ${hiddenCommunitiesCount
                  ? `
                    <button
                      class="btn ghost public-user-show-all-communities"
                      type="button"
                      data-open-public-user-communities="${escapeHtml(exactUsername)}"
                    >
                      Показать все
                      <span>${communities.length}</span>
                    </button>
                  `
                  : ''}
              `
              : `
                <p class="muted public-user-community-empty">
                  Пользователь пока не состоит в сообществах.
                </p>
              `}
          </section>
        </aside>
      </section>

      ${communitiesDialogOpen
        ? renderPublicUserCommunitiesDialog(profile, communities)
        : ''}
    `;
  }

  function renderPublicUserCommunitiesDialog(profile, communities) {
    return `
      <div
        class="public-user-community-overlay"
        data-close-public-user-communities
      >
        <section
          class="public-user-community-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="public-user-community-dialog-title"
          data-public-user-community-dialog
        >
          <button
            class="public-user-community-dialog-close"
            type="button"
            data-close-public-user-communities
            aria-label="Закрыть список сообществ"
          >×</button>

          <div class="public-user-community-dialog-heading">
            <div>
              <p class="eyebrow">Сообщества пользователя</p>
              <h2 id="public-user-community-dialog-title">
                ${escapeHtml(profile.username)}
              </h2>
            </div>

            <span class="status-pill">${communities.length}</span>
          </div>

          <div class="public-user-community-dialog-list">
            ${communities.map(publicUserCommunityCard).join('')}
          </div>
        </section>
      </div>
    `;
  }

  function normalizedPostType(post) {

    return String(

        post?.type ||

        post?.postType ||

        'TEXT'

    ).toUpperCase();

  }



  function publicUserStatCard(icon, label, count) {
    return `
      <div class="public-user-stat-card">
        <span class="public-user-stat-icon" aria-hidden="true">
          ${escapeHtml(icon)}
        </span>

        <strong>${Number(count || 0)}</strong>
        <span>${escapeHtml(label)}</span>
      </div>
    `;
  }

  function publicUserCommunityCard(community) {
    return `
      <a
        class="public-user-community-card"
        href="#/communities/${encodeURIComponent(community.id)}"
      >
        <span class="public-user-community-title">
          ${escapeHtml(community.communityName)}
        </span>

        <span class="public-user-community-description">
          ${escapeHtml(community.description || 'Без описания')}
        </span>

        <small>
          ${escapeHtml(communityRoleLabel(community.role))}
          · с ${formatDateShort(community.joinedAt)}
        </small>
      </a>
    `;
  }

  function publicUserLink(username) {

    const exactUsername =

        String(username || '');



    if (!exactUsername) {

      return 'Пользователь';

    }



    return `

    <a

      class="public-user-link"

      href="#/users/${encodeURIComponent(

        exactUsername

    )}"

    >

      ${escapeHtml(exactUsername)}

    </a>

  `;

  }

  async function shareAchievement(item) {
    if (!item) {
      state.achievementShareNotice = null;
      showTransientNotice(
        'error',
        'Достижение не найдено в данных backend.'
      );
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
      state.achievementShareNotice = null;
      state.error = null;

      showTransientNotice(
        'error',
        publicationErrorMessage('achievement', error)
      );
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
  function upsertCommunityCollections(community) {
    if (!community?.id) return;

    const id = String(community.id);

    if (state.communities) {
      const communitiesWithoutCurrent = state.communities.filter(
        (item) => String(item.id) !== id
      );

      state.communities = sortByDate(
        uniqueById([community, ...communitiesWithoutCurrent]),
        'createdAt'
      );
    }

    if (state.myCommunities) {
      const myCommunitiesWithoutCurrent = state.myCommunities.filter(
        (item) => String(item.id) !== id
      );

      state.myCommunities = community.currentUserMember
        ? sortByDate(
            [community, ...myCommunitiesWithoutCurrent],
            'createdAt'
          )
        : myCommunitiesWithoutCurrent;
    }
  }

  function uniqueById(items) {
    const itemsMap = new Map();

    (items || []).forEach((item) => {
      if (!item?.id) return;

      const id = String(item.id);

      if (!itemsMap.has(id)) {
        itemsMap.set(id, item);
      }
    });

    return [...itemsMap.values()];
  }

  function sortChats(chats) {
    return [...(chats || [])].sort((firstChat, secondChat) => {
      const firstDate =
        firstChat?.lastMessage?.sentAt ||
        firstChat?.createdAt ||
        0;

      const secondDate =
        secondChat?.lastMessage?.sentAt ||
        secondChat?.createdAt ||
        0;

      return (
        new Date(secondDate).getTime() -
        new Date(firstDate).getTime()
      );
    });
  }

  function sortByDateAscending(items, field) {
    return [...(items || [])].sort((firstItem, secondItem) => {
      const firstDate = firstItem?.[field] || 0;
      const secondDate = secondItem?.[field] || 0;

      return (
        new Date(firstDate).getTime() -
        new Date(secondDate).getTime()
      );
    });
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

  function getMemberInitials(member) {
    const fullName = [
      member?.firstName,
      member?.lastName,
    ]
      .filter(Boolean)
      .join(' ');

    const source =
      fullName ||
      member?.username ||
      'П';

    return source
      .split(/[\s._@-]+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part.charAt(0))
      .join('')
      .toUpperCase();
  }

  function communityRoleLabel(role) {
    const normalizedRole = String(role || '')
      .trim()
      .toUpperCase();

    const labels = {
      OWNER: 'Владелец',
      ADMIN: 'Администратор',
      MODERATOR: 'Модератор',
      MEMBER: 'Участник',
    };

    return labels[normalizedRole] || role || 'Участник';
  }

  function getChatInitials(chat) {
    const source =
      chat?.companionUsername ||
      chat?.companionEmail ||
      'С';

    return String(source)
      .split(/[\s._@-]+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part.charAt(0))
      .join('')
      .toUpperCase();
  }

  function formatChatTime(value) {
    if (!value) {
      return '';
    }

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
      return String(value);
    }

    const now = new Date();

    const sameDay =
      date.getFullYear() === now.getFullYear() &&
      date.getMonth() === now.getMonth() &&
      date.getDate() === now.getDate();

    if (sameDay) {
      return new Intl.DateTimeFormat('ru-RU', {
        hour: '2-digit',
        minute: '2-digit',
      }).format(date);
    }

    const sameYear =
      date.getFullYear() === now.getFullYear();

    if (sameYear) {
      return new Intl.DateTimeFormat('ru-RU', {
        day: '2-digit',
        month: 'short',
        hour: '2-digit',
        minute: '2-digit',
      }).format(date);
    }

    return new Intl.DateTimeFormat('ru-RU', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    }).format(date);
  }

  function updateChatLastMessage(
    chatId,
    message
  ) {
    if (!state.chats) {
      return;
    }

    state.chats = sortChats(
      state.chats.map((chat) =>
        String(chat.id) === String(chatId)
          ? {
              ...chat,
              lastMessage: message,
            }
          : chat
      )
    );
  }

  function scrollChatToBottom() {
    const messagesContainer =
      app.querySelector('[data-messages]');

    if (!messagesContainer) {
      return;
    }

    requestAnimationFrame(() => {
      messagesContainer.scrollTop =
        messagesContainer.scrollHeight;
    });
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
