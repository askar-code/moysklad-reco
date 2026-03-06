## MoySklad Recommendations Backend

Серверное решение для МоегоСклада, показывающее блок «С этим товаром чаще всего покупают» на карточке товара.

### Локальный запуск

- **Зависимости**: Java 17, Maven, PostgreSQL.
- Создать БД и пользователя:

```sql
CREATE DATABASE moysklad_reco;
CREATE USER moysklad_reco WITH ENCRYPTED PASSWORD 'changeme';
GRANT ALL PRIVILEGES ON DATABASE moysklad_reco TO moysklad_reco;
```

- При необходимости скорректировать параметры подключения в `src/main/resources/application.yml`.
- Указать секретный ключ Vendor API через переменную окружения:

```bash
export VENDOR_SECRET_KEY="ваш_secretKey_из_ЛК_разработчика"
```

- Запустить приложение:

```bash
mvn spring-boot:run
```

### Основные эндпоинты

- `PUT /api/moysklad/vendor/1.0/apps/{appUid}/{accountId}` — установка/обновление приложения по Vendor API, сохранение токена доступа к JSON API 1.2.
- `DELETE /api/moysklad/vendor/1.0/apps/{appUid}/{accountId}` — деактивация приложения по Vendor API.
- `POST /api/context/resolve` — обмен `contextKey` на внутренний session token (`Authorization: Bearer ...`) для iframe/widget.
- `GET /api/recommendations?productId=...` — рекомендации «чаще покупают вместе» (требует `Authorization: Bearer <session-token>`).
- `GET /api/settings` / `POST /api/settings` — чтение/сохранение настроек аккаунта (требует `Authorization: Bearer <session-token>`).
- `GET /settings.html` — iframe‑страница настроек решения.
- `GET /widget.html` — iframe‑виджет для карточки товара.

### Контекст и безопасность iframe/widget

- Для iframe и виджетов используйте `contextKey`, который МойСклад добавляет в URL.
- Бэкенд делает `POST /context/{contextKey}` в Vendor API (JWT `HS256`, `sub=appUid`) и выдает внутренний session token.
- Все вызовы `/api/*` (кроме `/api/context/resolve`) защищены этим session token и не принимают `accountId` из query/body как источник истины.

### Тесты

- Запуск тестов:

```bash
mvn test
```

Покрыты базовые сценарии сервиса рекомендаций и фильтра проверки JWT‑подписи Vendor API.

### Подготовка к модерации в каталоге решений

- Собрать финальный артефакт:

```bash
mvn clean package
```

- Поднять backend на публичном HTTPS‑домене (например, в Yandex Cloud MKS, см. ниже).
- В личном кабинете разработчика МоегоСклада:
  - создать Черновик решения;
  - загрузить или вставить XML‑дескриптор из `vendor-descriptor.xml`, указав реальные URL сервиса;
  - получить `appUid`, `secretKey`, сконфигурировать их в окружении приложения;
  - протестировать установку и работу iframe/виджета на тестовом аккаунте.
- После успешных проверок отправить решение на модерацию и дождаться публикации.

### Деплой

В репозитории есть пример манифестов Kubernetes в `deploy/yandex-mks/*.example.yaml`. Перед использованием замените значения-заглушки на свои.

- **1. Собрать и запушить Docker‑образ в registry**

```bash
cd backend
mvn clean package -DskipTests

docker build -t cr.yandex/<registry-id>/moysklad-reco-backend:latest .
docker push cr.yandex/<registry-id>/moysklad-reco-backend:latest
```

- **2. Подготовить namespace и секреты**

```bash
kubectl create namespace moysklad-reco
kubectl config set-context --current --namespace=moysklad-reco
```

- **3. Подготовить PostgreSQL**

Создайте БД `moysklad_reco` и пользователя `moysklad_reco`, затем заполните `deploy/yandex-mks/secrets-example.yaml` и примените секреты:

```bash
kubectl apply -f ../deploy/yandex-mks/secrets-example.yaml
```

- **4. Задеплоить backend**

```bash
kubectl apply -f ../deploy/yandex-mks/backend-deployment.example.yaml
kubectl apply -f ../deploy/yandex-mks/backend-service.yaml
kubectl apply -f ../deploy/yandex-mks/ingress.example.yaml
```

В манифестах нужно заменить как минимум:

- `cr.yandex/<registry-id>/moysklad-reco-backend:latest` — на реальный путь до образа в Container Registry;
- `reco.saas-rating.ru` — на ваш домен, если вы публикуете собственную копию;
- `<your-ingress-class>` и `<your-tls-secret>` — на значения вашего кластера.

- **5. Проверить работу**

```bash
kubectl get pods
kubectl get ingress
```

Откройте `https://your-domain.example/health` или `https://your-domain.example/actuator/health`  
и убедитесь, что сервис отвечает, затем используйте этот URL в `vendor-descriptor.xml`.
