# Binance Price Service

Сервис по расписанию забирает среднюю цену торговой пары (по умолчанию **BNBUSDT**) с биржи
Binance ([GET /api/v3/avgPrice](https://developers.binance.com/docs/binance-spot-api-docs/rest-reference/market-data-endpoints)),
сохраняет её в PostgreSQL и отдаёт через REST API.

## Стек

- Java 17, Spring Boot 3.5 (Web, Data JPA, Validation)
- PostgreSQL 16 + Flyway (миграции схемы)
- RestClient (JDK HttpClient) для вызовов Binance
- Тесты: JUnit 5, Mockito, MockRestServiceServer, Testcontainers, WireMock
- Docker Compose для локального запуска

## Запуск

### Docker Compose (рекомендуется)

```bash
docker compose up --build -d
```

Поднимаются два сервиса: `app` (порт 8080) и `postgres`. Первая выборка цены выполняется
сразу после старта, далее — каждые `BINANCE_POLL_INTERVAL` (по умолчанию PT30S).

### Локально

Нужны JDK 17+ и PostgreSQL. Переменные окружения см. в таблице ниже.

```bash
DB_HOST=localhost DB_PORT=5432 DB_NAME=prices DB_USER=prices DB_PASSWORD=prices \
mvn spring-boot:run
```

### Переменные окружения

| Переменная            | По умолчанию                       | Описание                                          |
|-----------------------|------------------------------------|---------------------------------------------------|
| `BINANCE_POLL_INTERVAL` | `PT30S`                          | Интервал опроса, ISO-8601 duration (`PT30S`, `PT1M`) |
| `BINANCE_SYMBOL`      | `BNBUSDT`                          | Торговая пара, которую забирает шедулер            |
| `BINANCE_BASE_URL`    | `https://api.binance.com`          | База API (используется в тестах для подмены)       |
| `DB_HOST` / `DB_PORT` | `localhost` / `5432`               | Адрес PostgreSQL                                   |
| `DB_NAME` / `DB_USER` / `DB_PASSWORD` | `prices` / `prices` / `prices` | Реквизиты БД                        |
| `SERVER_PORT`         | `8080`                             | Порт HTTP-сервера                                  |

## REST API

Во всех эндпоинтах `symbol` необязателен — по умолчанию берётся `BINANCE_SYMBOL`.
Даты принимаются в ISO-8601 с зоной (`2026-10-01T00:00:00Z`), интервал включительный с обеих сторон `[from, to]`.
Ответ `fetchedAt` — ISO-8601 UTC с точностью до секунды (`YYYY-MM-DDTHH:mm:ssZ`).

### 1. Последняя сохранённая запись

```bash
curl 'http://localhost:8080/api/v1/prices/latest'
```
```json
{"symbol":"BNBUSDT","price":612.34567890,"fetchedAt":"2026-10-22T10:15:30Z"}
```

### 2. Средняя цена за период

```bash
curl 'http://localhost:8080/api/v1/prices/average?from=2026-10-20T00:00:00Z&to=2026-10-22T00:00:00Z'
```
```json
{"averagePrice":611.87}
```

### 3. Записи за период с пагинацией

```bash
curl 'http://localhost:8080/api/v1/prices?from=2026-10-20T00:00:00Z&to=2026-10-22T00:00:00Z&page=0&size=20'
```
```json
{
  "items": [{"symbol":"BNBUSDT","price":612.34,"fetchedAt":"2026-10-22T10:15:30Z"}],
  "page": 0,
  "size": 20,
  "totalElements": 135,
  "hasNext": true
}
```

### 4. Тренд за период

```bash
curl 'http://localhost:8080/api/v1/prices/trend?from=2026-10-01T00:00:00Z&to=2026-10-30T00:00:00Z'
```
```json
{"symbol":"BNBUSDT","start_date":"2026-10-01","end_date":"2026-10-30","trend":"UPWARD"}
```

Тренд сравнивает первую и последнюю цену периода: изменение в пределах ±0.1% считается
`FLAT` (рыночный шум), иначе — `UPWARD`/`DOWNWARD`.

### Ошибки

Ответы об ошибках — RFC 7807 (`application/problem+json`):

- `404` — нет сохранённых данных по символу/периоду;
- `400` — некорректные параметры (from > to, неверный формат даты, size > 100);
- `500` — непредвиденная ошибка.

### Логирование

- каждая успешная выборка Binance — `Stored price record: symbol=..., price=..., fetchedAt=...` (INFO);
- каждый HTTP-запрос — `GET /api/v1/prices/latest -> 200 (3 ms)` (INFO);
- сбой Binance — WARN (`Binance poll failed...`), непредвиденные ошибки — ERROR;
- при старте пишется конфигурация опроса: интервал и символ.

## Swagger

Интерактивная документация (springdoc-openapi): **http://localhost:8080/swagger-ui.html**,
спецификация OpenAPI 3 — http://localhost:8080/v3/api-docs.


## Архитектура

```
scheduler/PriceFetchScheduler      — запуск опроса, глушит сбои, чтобы расписание продолжало работать
service/PriceIngestionService      — одна успешная выборка = одна запись в БД (transactional)
client/BinanceClient               — вызов GET /api/v3/avgPrice, все сбои -> BinanceApiException
domain/, repository/               — JPA-сущность PriceRecord + запросы (средняя считается в PostgreSQL)
service/PriceQueryService          — чтение: latest / average / history / trend
api/PriceController + dto/         — REST-слой, конвертация в DTO
```

Отказоустойчивость опроса:

- шедулер работает с **fixed delay** — следующий опрос начинается только после завершения
  предыдущего, одновременных опросов не бывает;
- сбой Binance (в т.ч. `429`/`418` rate limit, таймауты 5с/10с) логируется и пропускается —
  запись не создаётся, значит нет ни дублей, ни потерь; следующий тик пишет новую строку;
- `fetchedAt` берётся из инжектируемого `Clock` (UTC) и обрезается до секунд.

Rate limits Binance: запрос avgPrice имеет weight 2; лимит REQUEST_WEIGHT — 6000 в минуту
на IP. При PT30S сервис тратит ~4 weight в минуту — с большим запасом.

Деньги — только `BigDecimal`: цена приходит строкой и хранится в `numeric`
(точный десятичный тип, масштаб сохраняется таким, каким прислал Binance),
среднее считается `AVG` в PostgreSQL без float-конверсий.

## Схема БД

Миграция `V1__create_price_records.sql`: таблица `price_records(id bigserial, symbol varchar(20),
price numeric(38,18), fetched_at timestamptz)` + индекс `(symbol, fetched_at desc)`.

## Тесты

```bash
mvn verify
```

- Unit: `TrendCalculator`, `TimePeriod`, `PriceIngestionService`, `PriceQueryService`,
  `BinanceClient` (MockRestServiceServer), `PriceFetchScheduler`.
- Integration (`PriceApiIntegrationTest`): реальный PostgreSQL в Testcontainers + Binance,
  подменённый WireMock; проверены все 4 эндпоинта, формат `fetchedAt`, пагинация, тренды,
  404/400 и сценарий «Binance ответил 429 — данных не потеряли и не задублировали».

## Решения и компромиссы

- **Kafka/Rabbit/Redis не используются**: один инстанс, один источник, объём данных мал —
  очередь/кэш не дают выгоды, но добавляют точку отказа (YAGNI). Горизонтальное масштабирование
  шедулера решается Distributed ShedLock/leader-election, когда он понадобится.
- **`avg` считается в PostgreSQL**: точная десятичная арифметика, не тащим все строки в память.
- **`TimePeriod` как value object**: валидация `from <= to` в одном месте, у запросов гарантированно
  корректный интервал.
- **Слой API не знает про Binance**: REST отвечает только из БД, поэтому недоступность биржи
  не влияет на клиентов.
