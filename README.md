# CrptApi

## Задача
Реализовать класс `CrptApi` (Java 11) для работы с API Честного Знака:
- Потокобезопасность и ограничение количества запросов в интервале времени: 
  конструктор `CrptApi(TimeUnit timeUnit, int requestLimit)` задаёт размер окна и лимит.
- Реализован единственный метод: создание документа ввода в оборот товара РФ.
- Документ передаётся как Java-объект, подпись — как строка.
- Реализация в одном файле `CrptApi.java`, вспомогательные классы — внутренние.

## Кратко о решении
- Ограничитель запросов реализован на основе `Semaphore`: каждый запрос "занимает" один токен, 
  а возврат токена происходит через `timeWindowMillis` в отдельном рабочем потоке.
- HTTP-клиент: `java.net.http.HttpClient` (таймауты по 30 секунд).
- Сериализация JSON: Jackson с `SNAKE_CASE` и исключением `null`.
- Конфигурируемая авторизация: заголовок `Authorization` берётся из `Supplier<String>`,
  что позволяет легко подставлять токен в тестах и на проде.

Конструкторы:
- `CrptApi(TimeUnit, int)` — базовый (использует `https://ismp.crpt.ru`, дефолтный клиент и плейсхолдер-токен).
- `CrptApi(TimeUnit, int, String baseUrl)` — базовый с указанием baseUrl.
- `CrptApi(TimeUnit, int, String baseUrl, HttpClient httpClient, Supplier<String> authorizationHeaderSupplier)` —
  полностью настраиваемый (для тестов/продакшена).

## Тесты
В проекте есть три набора тестов:

1) Mockito-юнит тесты (`CrptApiMockitoTest`)
   - Мокаем `HttpClient` и подставляем через расширенный конструктор.
   - Проверяет: HTTP-метод и URL, заголовки, успешный ответ, обработку `non-200` и `IOException`.

2) Локальный интеграционный тест (`CrptApiTest`)
   - Поднимает локальный `HttpServer` и проверяет тело запроса и rate limiting (блокировки ~1с).

3) Интеграционные тесты с реальным API (`CrptApiIntegrationTest`)
   - `realApi_withoutToken_resultsInApiException` — выполняется всегда, ожидает ошибку.
   - `realApi_withToken_maySucceedOrRejectInput` — запускается только если задана переменная окружения `CRPT_TOKEN`.