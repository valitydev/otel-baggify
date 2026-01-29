package dev.vality.otel.baggify.converter;

/**
 * Functional interface for custom baggage value conversion.
 *
 * <p>Implementations transform extracted values into their String representation
 * for storage in OpenTelemetry Baggage.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * public class UserIdConverter implements BaggageValueConverter<UserId> {
 *     @Override
 *     public String convert(UserId value) {
 *         return value != null ? value.getValue() : null;
 *     }
 * }
 *
 * // Usage in annotation
 * @WithBaggage(@BaggageField(
 *     key = "user.id",
 *     path = "#userId",
 *     converter = UserIdConverter.class
 * ))
 * public void process(UserId userId) { ... }
 * }</pre>
 *
 * <h2>Spring Bean Converter</h2>
 * <pre>{@code
 * @Component("customConverter")
 * public class CustomConverter implements BaggageValueConverter<MyType> {
 *     @Autowired
 *     private SomeService service;
 *
 *     @Override
 *     public String convert(MyType value) {
 *         return service.format(value);
 *     }
 * }
 *
 * // Usage
 * @BaggageField(key = "my.key", path = "#value", converterBean = "customConverter")
 * }</pre>
 *
 * @param <T> the type of value to convert
 */
@FunctionalInterface
public interface BaggageValueConverter<T> {

    /**
     * Converts the given value to a String for baggage storage.
     *
     * @param value the value to convert (may be null)
     * @return the string representation, or null if value should be skipped
     */
    String convert(T value);

    /**
     * Default no-operation converter marker class.
     * <p>
     * Used as the default value for {@link dev.vality.otel.baggify.annotation.BaggageField#converter()}
     * to indicate that no custom converter is specified.
     */
    final class NoOp implements BaggageValueConverter<Object> {
        private NoOp() {
            // Prevent instantiation - this is just a marker class
        }

        @Override
        public String convert(Object value) {
            throw new UnsupportedOperationException("NoOp converter should never be invoked");
        }
    }
}

