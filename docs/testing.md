# Тестирование проекта T-Health

Этот документ описывает структуру тестов, правила именования, запуск unit- и integration-тестов, работу PostgreSQL Testcontainers и формирование отчёта покрытия.

Документ нужно хранить в репозитории по пути:

```text
docs/testing.md
```

В корневой `README.md` желательно добавить ссылку:

```md
## Документация

- [Запуск и структура тестов](docs/testing.md)
```

---

## 1. Текущая структура

Текущая структура организована правильно: сначала тесты разделяются по типу, затем — по тестируемому модулю.

```text
src/test/
├── java/
│   └── ru/innopolis/tbank/thealth/
│       ├── unit/
│       │   └── services/
│       │       ├── PostServiceTest.java
│       │       ├── RecipeServiceTest.java
│       │       ├── UserServiceTest.java
│       │       └── WorkoutServiceTest.java
│       │
│       ├── integration/
│       │   ├── AbstractIntegrationTest.java
│       │   ├── ApplicationContextIT.java
│       │   ├── PostControllerIT.java
│       │   ├── RecipeControllerIT.java
│       │   ├── UserControllerIT.java
│       │   └── WorkoutControllerIT.java
│       │
│       ├── support/
│       │   └── TestFixtures.java
│       │
│       ├── TestcontainersConfiguration.java
│       └── TestTBankHealthApplication.java
│
└── resources/
    └── application-test.properties
```

Не нужно складывать все unit-тесты в один класс, а все integration-тесты — в другой. Один тестовый класс должен отвечать за один сервис, контроллер или отдельный функциональный модуль.

---

## 2. Разница между unit- и integration-тестами

### Unit-тесты

Unit-тест проверяет один класс изолированно.

Для сервисов:

```text
тестируемый Service — настоящий объект
Repository          — Mockito mock
Mapper              — Mockito mock или настоящий объект
внешние клиенты     — Mockito mock
Spring Context      — не запускается
PostgreSQL          — не запускается
Docker              — не требуется
```

Пример назначения:

```text
WorkoutServiceTest
```

проверяет бизнес-логику `WorkoutService`:

- создание тренировки;
- проверку владельца;
- частичное обновление;
- запрет удаления опубликованной тренировки;
- вызов выдачи достижений.

Unit-тесты должны быть быстрыми и не зависеть от порядка запуска других тестов.

### Integration-тесты

Integration-тест проверяет прохождение запроса через несколько настоящих слоёв:

```text
MockMvc
→ Spring Security
→ Validation
→ Controller
→ Service
→ Repository
→ PostgreSQL Testcontainers
```

В integration-тестах используются:

- настоящий Spring Boot Context;
- настоящие контроллеры, сервисы и репозитории;
- настоящая схема PostgreSQL;
- настоящие Flyway-миграции;
- тестовый JWT через `spring-security-test`;
- mock только для внешних систем, например Keycloak Admin Client.

Пример назначения:

```text
WorkoutControllerIT
```

проверяет:

- HTTP endpoint;
- JSON request/response;
- статус ответа;
- Validation;
- Spring Security;
- сохранение данных в настоящей PostgreSQL.

---

## 3. Назначение вспомогательных файлов

### `TestcontainersConfiguration.java`

Создаёт PostgreSQL-контейнер:

```text
@Bean
@ServiceConnection
public PostgreSQLContainer<?> postgresContainer() {
    return new PostgreSQLContainer<>(
        DockerImageName.parse("postgres:16")
    );
}
```

`@ServiceConnection` автоматически передаёт Spring Boot параметры подключения:

- JDBC URL;
- username;
- password;
- driver.

Вручную указывать порт PostgreSQL в тестах не нужно.

### `TestTBankHealthApplication.java`

Это вспомогательный launcher для запуска приложения вместе с Testcontainers:

```text
SpringApplication
    .from(TBankHealthApplication::main)
    .with(TestcontainersConfiguration.class)
    .run(args);
```

Он не является тестом и автоматически командами `test` или `verify` не запускается.

Его можно запустить из IDE, когда нужно поднять приложение с временной PostgreSQL, не используя локальную базу.

### `AbstractIntegrationTest.java`

Общий базовый класс для integration-тестов. Обычно в нём находятся:

- `@SpringBootTest`;
- `@AutoConfigureMockMvc`;
- `@Import(TestcontainersConfiguration.class)`;
- `@ActiveProfiles("test")`;
- `@Transactional`;
- общий `MockMvc`;
- общий `UserRepository`;
- mock внешнего Keycloak-клиента;
- helper для создания JWT;
- helper для сохранения тестового пользователя.

Все `*ControllerIT` наследуются от этого класса.

### `ApplicationContextIT.java`

Минимальный smoke-тест:

```text
@Test
void contextLoads() {
}
```

Он проверяет, что:

- Spring Context запускается;
- конфигурация валидна;
- PostgreSQL-контейнер доступен;
- Flyway-миграции применяются;
- все обязательные Spring beans создаются.

### `TestFixtures.java`

Содержит общие фабрики тестовых объектов:

```text
user(...)
workout(...)
recipe(...)
post(...)
```

Fixtures должны создавать валидные объекты с понятными значениями по умолчанию. Тест может изменить только те поля, которые важны для конкретного сценария.

Не следует помещать в `TestFixtures` проверки, вызовы сервисов или бизнес-логику.

### `application-test.properties`

Содержит настройки профиля `test`.

Он должен:

- отключать лишний SQL-лог;
- включать Flyway;
- запрещать обращения к реальному Keycloak;
- содержать тестовые значения обязательных placeholder;
- не содержать реальные пароли и client secret.

PostgreSQL-подключение в этом файле задавать не нужно: его подставляет `@ServiceConnection`.

---

## 4. Правила именования

### Классы

```text
*Test.java — unit-тесты
*IT.java   — integration-тесты
```

Примеры:

```text
WorkoutServiceTest.java
WorkoutControllerIT.java
```

Maven использует окончания файлов, чтобы разделять наборы тестов:

- Maven Surefire запускает `*Test`;
- Maven Failsafe запускает `*IT`.

### Методы

Рекомендуемый стиль:

```text
method_condition_expectedResult()
```

Примеры:

```text
createWorkout_validRequest_savesWorkout()
getWorkout_foreignOwner_throwsNotFoundException()
createRecipe_negativeCalories_returnsBadRequest()
deletePost_unauthenticatedUser_returnsUnauthorized()
```

Допустим и стиль `should...`:

```text
shouldCreateWorkoutForCurrentUser()
shouldRejectRecipeWithNegativeCalories()
```

Главное — одинаковый стиль во всём проекте.

### Структура метода

Используйте Arrange — Act — Assert:

```text
@Test
void createWorkout_validRequest_savesWorkout() {
    // Arrange
    WorkoutCreateRequest request = ...;
    when(userRepository.findById(USER_ID))
            .thenReturn(Optional.of(user));

    // Act
    WorkoutResponse response =
            workoutService.createWorkout(USER_ID, request);

    // Assert
    assertThat(response).isNotNull();
    verify(workoutRepository).save(any(WorkoutEntity.class));
}
```

---

## 5. Требования для запуска

### Для unit-тестов

Нужны:

- JDK 17 или новее;
- Maven Wrapper из проекта.

Docker не требуется.

Проверка Java:

```bash
java -version
```

### Для integration-тестов

Дополнительно нужен запущенный Docker Desktop или Docker Engine.

Проверка:

```bash
docker info
```

Если команда возвращает информацию о Docker daemon, Testcontainers сможет создать PostgreSQL.

Запускать вручную:

```bash
docker compose up
```

для integration-тестов не требуется. Testcontainers самостоятельно:

1. скачивает образ PostgreSQL при первом запуске;
2. создаёт временный контейнер;
3. передаёт подключение Spring Boot;
4. останавливает и удаляет контейнер после тестов.

Локальная база разработчика при этом не используется.

---

## 6. Команды запуска

Все команды выполняются из корня проекта, где находится `pom.xml`.

### Только unit-тесты

```bash
./mvnw test
```

Полный чистый запуск:

```bash
./mvnw clean test
```

Docker для этой команды не нужен.

### Unit + integration-тесты

```bash
./mvnw verify
```

Рекомендуемый полный запуск перед push или Pull Request:

```bash
./mvnw clean verify
```

Последовательность Maven:

```text
compile
→ test
→ package
→ integration-test
→ verify
```

То есть при `verify` сначала выполняются unit-тесты, а затем integration-тесты.

### Один unit-класс

```bash
./mvnw -Dtest=WorkoutServiceTest test
```

### Один unit-метод

```bash
./mvnw \
  -Dtest=WorkoutServiceTest#createWorkout_validRequest_savesWorkout \
  test
```

### Один integration-класс

```bash
./mvnw -Dit.test=WorkoutControllerIT verify
```

Важно: фаза `verify` всё равно сначала выполнит unit-тесты проекта, после чего запустит указанный integration-класс.

### Один integration-метод

```bash
./mvnw \
  -Dit.test=WorkoutControllerIT#createWorkout_validRequest_returnsCreated \
  verify
```

### Все service unit-тесты

```bash
./mvnw -Dtest='*ServiceTest' test
```

### Все controller integration-тесты

```bash
./mvnw -Dit.test='*ControllerIT' verify
```

### Собрать проект без integration-тестов

```bash
./mvnw verify -DskipITs
```

### Не запускать никакие тесты

Использовать только в исключительных случаях:

```bash
./mvnw package -DskipTests
```

Перед Pull Request эту команду использовать вместо `verify` нельзя.

---

## 7. Как работает тестовая авторизация

Настоящий Keycloak для integration-тестов не запускается.

В запрос добавляется тестовый JWT:

```text
mockMvc.perform(
    get("/api/users/me")
        .with(jwtFor(USER_ID))
);
```

Helper формирует claims, необходимые приложению:

```text
sub
email
preferred_username
given_name
family_name
```

Spring Security считает запрос аутентифицированным, но не обращается к реальному Keycloak.

Проверка неавторизованного запроса выполняется без `.with(jwtFor(...))`:

```text
mockMvc.perform(get("/api/users/me"))
       .andExpect(status().isUnauthorized());
```

`KeycloakAdminClient` в integration-тестах должен быть mock, потому что это внешняя система. При удалении профиля проверяется не реальный HTTP-запрос, а факт вызова клиента:

```text
verify(keycloakAdminClient).deleteUser(USER_ID);
```

---

## 8. Как работает тестовая PostgreSQL

Для каждого запуска integration-тестов создаётся временная PostgreSQL.

Во время запуска:

1. Testcontainers стартует `postgres:16`;
2. Spring Boot получает параметры подключения через `@ServiceConnection`;
3. Flyway применяет миграции из `src/main/resources/db/migration`;
4. тест выполняет HTTP-запросы через MockMvc;
5. сервисы работают с настоящими JPA repositories;
6. данные проверяются через repository или JSON response.

В `AbstractIntegrationTest` используется `@Transactional`. После каждого теста транзакция откатывается, поэтому данные одного теста не должны влиять на другой.

Несмотря на rollback, каждый тест должен самостоятельно создавать необходимые исходные данные.

Нельзя рассчитывать на:

- порядок выполнения тестов;
- пользователя, созданного другим тестом;
- рецепт или тренировку из другого тестового класса;
- данные локальной базы разработчика.

---

## 9. Что проверять в каждом типе тестов

### Unit-тест сервиса

Проверяйте:

- бизнес-правила;
- проверки владельца;
- обработку отсутствующих сущностей;
- выбор нужных repository methods;
- формирование Entity;
- вызовы mapper;
- вызовы зависимых сервисов;
- исключения;
- отсутствие лишних сохранений.

Не нужно в unit-тесте проверять:

- HTTP status;
- JSON;
- работу `@Valid`;
- Flyway;
- реальную PostgreSQL;
- Spring Security filter chain.

### Integration-тест контроллера

Проверяйте:

- URL и HTTP method;
- JWT-доступ;
- HTTP status;
- request body;
- response JSON;
- Validation;
- обработку исключений;
- запись в PostgreSQL;
- ограничения БД;
- Flyway mapping;
- сценарий отсутствия JWT.

Не нужно дублировать в integration-тестах все внутренние Mockito-проверки unit-тестов.

---

## 10. Рекомендуемый набор сценариев

### Тренировки

Unit:

- успешное создание;
- пользователь не найден;
- получение только собственной тренировки;
- частичное обновление;
- удаление опубликованной тренировки без подтверждения;
- удаление со связанным постом;
- выдача достижений.

Integration:

- `POST /api/workouts` → `201`;
- некорректная длительность → `400`;
- получение собственной тренировки → `200`;
- попытка получить чужую → `404` или `403` согласно контракту;
- запрос без JWT → `401`.

### Рецепты

Unit:

- создание рецепта со всеми полями;
- частичное обновление;
- проверка владельца;
- запрет удаления опубликованного рецепта;
- удаление рецепта вместе с постом.

Integration:

- `POST /api/recipes` → `201`;
- отрицательные КБЖУ → `400`;
- некорректный `imageUrl` → `400`;
- получение собственного рецепта → `200`;
- получение чужого рецепта → ожидаемый статус контракта;
- запрос без JWT → `401`.

### Посты

Unit:

- создание `TEXT`-поста;
- установка `PUBLIC`;
- публикация тренировки;
- публикация рецепта;
- запрет повторной публикации;
- фильтрация ленты;
- удаление только собственного поста.

Integration:

- `POST /api/posts/text` → `201`;
- пустой текст → `400`;
- публикация существующей тренировки;
- повторная публикация → `409`;
- фильтрация `GET /api/posts/feed?type=TEXT`;
- запрос без JWT → `401`.

### Профиль

Unit:

- получение существующего профиля;
- создание профиля из JWT;
- выдача welcome-достижения;
- отсутствие обязательного claim;
- обновление профиля;
- конфликт username;
- удаление профиля;
- вызов Keycloak Admin Client.

Integration:

- `GET /api/users/me` создаёт локальный профиль;
- повторный вызов не создаёт дубль;
- `PATCH /api/users/me` обновляет профиль;
- занятый username → `409`;
- `DELETE /api/users/me` удаляет профиль;
- запрос без JWT → `401`.

---

## 11. Отчёт JaCoCo

После:

```bash
./mvnw clean verify
```

HTML-отчёт находится здесь:

```text
target/site/jacoco/index.html
```

На macOS:

```bash
open target/site/jacoco/index.html
```

На Linux:

```bash
xdg-open target/site/jacoco/index.html
```

Каталог `target/` не нужно добавлять в Git.

Чтобы данные покрытия unit- и integration-тестов записывались в один файл и не перезаписывали друг друга, в конфигурации `jacoco-maven-plugin` рекомендуется указать:

```xml
<configuration>
    <append>true</append>
</configuration>
```

Пример:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.13</version>
    <configuration>
        <append>true</append>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>jacoco-report</id>
            <phase>verify</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

Сначала рекомендуется добиться стабильного покрытия выше 50%, а затем включить обязательный `jacoco:check`, чтобы сборка падала при снижении покрытия.

---

## 12. Что должно быть в `pom.xml`

Для текущей схемы необходимы test dependencies:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>

<dependency>
<groupId>org.springframework.security</groupId>
<artifactId>spring-security-test</artifactId>
<scope>test</scope>
</dependency>

<dependency>
<groupId>org.springframework.boot</groupId>
<artifactId>spring-boot-testcontainers</artifactId>
<scope>test</scope>
</dependency>

<dependency>
<groupId>org.testcontainers</groupId>
<artifactId>junit-jupiter</artifactId>
<scope>test</scope>
</dependency>

<dependency>
<groupId>org.testcontainers</groupId>
<artifactId>postgresql</artifactId>
<scope>test</scope>
</dependency>
```

И плагины:

```text
maven-surefire-plugin  — unit-тесты
maven-failsafe-plugin  — integration-тесты
jacoco-maven-plugin    — покрытие
```

Failsafe должен быть привязан к фазам:

```xml
<goals>
    <goal>integration-test</goal>
    <goal>verify</goal>
</goals>
```

---

## 13. Добавление тестов нового модуля

Допустим, добавляется модуль комментариев.

Unit-тест:

```text
src/test/java/ru/innopolis/tbank/thealth/
└── unit/services/CommentServiceTest.java
```

Integration-тест:

```text
src/test/java/ru/innopolis/tbank/thealth/
└── integration/CommentControllerIT.java
```

Общие test fixtures добавляются в:

```text
support/TestFixtures.java
```

Но если `TestFixtures` становится слишком большим, его нужно разделить:

```text
support/fixtures/
├── UserFixtures.java
├── WorkoutFixtures.java
├── RecipeFixtures.java
├── PostFixtures.java
└── CommunityFixtures.java
```

По мере роста проекта допустима ещё более модульная структура:

```text
unit/
├── workout/
├── recipe/
├── post/
└── user/

integration/
├── workout/
├── recipe/
├── post/
└── user/
```

Для текущего объёма существующая структура `unit/services` и `integration` достаточна.

---

## 14. Что коммитить в Git

Коммитить нужно:

```text
src/test/java/**
src/test/resources/application-test.properties
docs/testing.md
pom.xml
```

Не коммитить:

```text
target/
*.log
локальные IDE-файлы
реальные секреты
реальные Keycloak client secret
дампы тестовой PostgreSQL
```

Перед коммитом:

```bash
./mvnw clean verify
git status
git add pom.xml src/test docs/testing.md README.md
git commit -m "test: add unit and integration testing documentation"
```

---

## 15. Типовой порядок работы разработчика

Во время разработки сервиса:

```bash
./mvnw -Dtest=WorkoutServiceTest test
```

После завершения функционала:

```bash
./mvnw test
```

Перед push:

```bash
docker info
./mvnw clean verify
```

После выполнения:

```bash
open target/site/jacoco/index.html
```

Перед Pull Request убедитесь, что:

- все unit-тесты зелёные;
- все integration-тесты зелёные;
- Docker/Testcontainers запускаются с чистого состояния;
- нет обращений к реальному Keycloak;
- тесты не зависят от порядка;
- в штатных отрицательных сценариях API не возвращает `500`;
- `target/` не добавлен в Git.