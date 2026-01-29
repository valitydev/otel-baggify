# otel-baggify

Библиотека для декларативного обогащения OpenTelemetry Baggage через Spring AOP аннотации.

## Назначение

`otel-baggify` предоставляет Spring AOP механизм для извлечения значений из аргументов метода и обогащения OpenTelemetry Baggage на время выполнения метода. Это позволяет прозрачно передавать контекстную информацию через границы сервисов без модификации бизнес-логики.

## Требования

- Java 17+
- Spring Boot 3.x
- OpenTelemetry API 1.35+

## Подключение

```xml
<dependency>
    <groupId>dev.vality</groupId>
    <artifactId>otel-baggify</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Быстрый старт

### Базовое использование

```java
@Service
public class UserService {

    @WithBaggage(@BaggageField(key = "user.id", path = "#userId"))
    public User getUser(String userId) {
        // Baggage содержит "user.id" во время выполнения метода
        return userRepository.findById(userId);
    }
}
```

### Несколько полей

```java
@WithBaggage({
    @BaggageField(key = "user.id", path = "#request.userId"),
    @BaggageField(key = "order.id", path = "#request.orderId"),
    @BaggageField(key = "tenant", path = "#tenantId")
})
public void processOrder(OrderRequest request, String tenantId) {
    // Все значения доступны в Baggage
}
```

### Вложенные поля

```java
@WithBaggage(@BaggageField(key = "customer.email", path = "#order.customer.email"))
public void notifyCustomer(Order order) {
    // Извлекается order.getCustomer().getEmail()
}
```

### Запись в Span Attributes

По умолчанию все поля записываются и в Baggage, и в Span Attributes (`addToSpanAttributes = true`).

```java
@WithSpan
@WithBaggage(@BaggageField(key = "user.id", path = "#userId"))
public void tracedOperation(String userId) {
    // Значение записывается и в Baggage, и в Span Attributes (по умолчанию)
}
```

Можно отключить запись в Span Attributes для отдельных полей:

```java
@WithSpan
@WithBaggage({
    @BaggageField(key = "user.id", path = "#userId"),  // -> Baggage + Span Attributes
    @BaggageField(key = "internal.trace", path = "#traceId", addToSpanAttributes = false)  // -> только Baggage
})
public void tracedOperation(String userId, String traceId) {
    // user.id виден в span attributes, internal.trace — только в baggage
}
```

## API

### @WithBaggage

Основная аннотация, применяется к методам.

| Параметр | Тип | По умолчанию | Описание |
|----------|-----|--------------|----------|
| `value` | `BaggageField[]` | - | Массив полей для извлечения |

### @BaggageField

Описывает одно соответствие между путём к значению и ключом в Baggage.

| Параметр | Тип | По умолчанию | Описание |
|----------|-----|--------------|----------|
| `key` | `String` | - | Имя ключа в Baggage (обязательный) |
| `path` | `String` | - | Путь к значению (обязательный) |
| `addToSpanAttributes` | `boolean` | `true` | Записывать ли в Span Attributes |
| `converter` | `Class<? extends BaggageValueConverter<?>>` | `NoOp` | Класс кастомного конвертера |
| `converterBean` | `String` | `""` | Имя Spring Bean конвертера |

## Синтаксис путей

Путь должен начинаться с `#` и имени параметра метода:

```
#userId                    // Параметр целиком
#request.userId           // request.getUserId()
#order.customer.email     // order.getCustomer().getEmail()
```

> **Примечание:** Работает из коробки со Spring Boot. Если имена параметров не распознаются, добавьте в maven-compiler-plugin опцию `<parameters>true</parameters>`.

## Конвертация значений

Порядок выбора механизма конвертации (от высшего приоритета к низшему):

1. **Кастомный конвертер** (через `converterBean` или `converter`)
2. **Spring ConversionService** (если доступен и может конвертировать)
3. **Строка как есть** (если значение уже `String`)
4. **toString()** (fallback)

### Пример кастомного конвертера

```java
// Класс конвертера
public class UserIdConverter implements BaggageValueConverter<UserId> {
    @Override
    public String convert(UserId value) {
        return value != null ? value.getValue() : null;
    }
}

// Использование
@BaggageField(key = "user.id", path = "#userId", converter = UserIdConverter.class)
```

### Spring Bean конвертер

```java
@Component("maskedEmailConverter")
public class MaskedEmailConverter implements BaggageValueConverter<String> {
    @Override
    public String convert(String email) {
        // Маскирование email для логов
        return email.replaceAll("(?<=.{2}).(?=.*@)", "*");
    }
}

// Использование
@BaggageField(key = "user.email", path = "#email", converterBean = "maskedEmailConverter")
```

## Порядок выполнения аспектов

При совместном использовании с `@WithSpan`, аспект `@WithBaggage` выполняется **после** `@WithSpan`, то есть обогащение Baggage происходит внутри уже активного Span.

Это обеспечивается через Spring AOP ordering: `WithBaggageAspect` имеет порядок `Ordered.LOWEST_PRECEDENCE - 100`.

## Обработка null значений

- Если значение не найдено или равно `null`, запись в Baggage **не выполняется**
- Исключения **не выбрасываются** при отсутствии значения
- При отсутствии активного Span, запись в Span Attributes безопасно пропускается

## Восстановление контекста

После завершения метода (успешного или с исключением) исходный OTEL контекст и Baggage автоматически восстанавливаются без утечек.

## Конфигурация

Библиотека использует Spring Boot Auto Configuration. Все необходимые бины создаются автоматически при наличии OpenTelemetry на classpath.

Для кастомизации можно переопределить бины:

```java
@Configuration
public class CustomBaggifyConfig {

    @Bean
    public PathValueExtractor customPathValueExtractor() {
        // Ваша реализация
    }
    
    @Bean
    public BaggageValueConverterResolver customConverterResolver(
            BeanFactory beanFactory,
            ConversionService conversionService) {
        // Ваша реализация
    }
}
```

## Обработка ошибок

Библиотека **никогда не ломает бизнес-логику**. При любых проблемах с конфигурацией или извлечением значений:

- Метод выполняется как обычно
- В лог пишется предупреждение (WARN)
- Проблемное поле просто пропускается

Примеры ситуаций, которые логируются как предупреждения:

- `key` пустой или `null`
- `path` не начинается с `#`
- Параметр с указанным именем не найден
- Дублирующиеся ключи в одной аннотации
- Ошибка в кастомном конвертере

```
WARN  WithBaggageAspect : Method 'process': @BaggageField path 'userId' for key 'user.id' must start with '#', skipping
WARN  WithBaggageAspect : Method 'process': Duplicate baggage key 'user.id', only first occurrence will be used
```

## Лицензия

Apache License 2.0
