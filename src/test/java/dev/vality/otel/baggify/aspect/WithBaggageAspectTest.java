package dev.vality.otel.baggify.aspect;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Service;

import dev.vality.otel.baggify.annotation.BaggageField;
import dev.vality.otel.baggify.annotation.WithBaggage;
import dev.vality.otel.baggify.config.BaggifyAutoConfiguration;
import dev.vality.otel.baggify.converter.BaggageValueConverter;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

@SpringBootTest(classes = WithBaggageAspectTest.TestConfig.class)
@DisplayName("WithBaggageAspect Integration Tests")
class WithBaggageAspectTest {

    @Autowired
    private TestServiceBean testService;

    @Autowired
    private InMemorySpanExporter spanExporter;

    @Autowired
    private Tracer tracer;

    @BeforeEach
    void setUp() {
        spanExporter.reset();
    }

    @AfterEach
    void tearDown() {
        spanExporter.reset();
    }

    @Nested
    @DisplayName("Basic baggage enrichment")
    class BasicBaggageEnrichment {

        @Test
        @DisplayName("Should enrich baggage with simple parameter")
        void shouldEnrichBaggageWithSimpleParameter() {
            AtomicReference<String> capturedBaggage = new AtomicReference<>();

            testService.simpleMethod("test-user-123", () -> {
                capturedBaggage.set(Baggage.current().getEntryValue("user.id"));
            });

            assertThat(capturedBaggage.get()).isEqualTo("test-user-123");
        }

        @Test
        @DisplayName("Should enrich baggage with multiple fields")
        void shouldEnrichBaggageWithMultipleFields() {
            AtomicReference<String> capturedUserId = new AtomicReference<>();
            AtomicReference<String> capturedOrderId = new AtomicReference<>();

            testService.multipleFields("user-1", "order-99", () -> {
                capturedUserId.set(Baggage.current().getEntryValue("user.id"));
                capturedOrderId.set(Baggage.current().getEntryValue("order.id"));
            });

            assertThat(capturedUserId.get()).isEqualTo("user-1");
            assertThat(capturedOrderId.get()).isEqualTo("order-99");
        }

        @Test
        @DisplayName("Should enrich baggage with nested field")
        void shouldEnrichBaggageWithNestedField() {
            AtomicReference<String> capturedEmail = new AtomicReference<>();
            TestRequest request = new TestRequest(new TestUser("john@example.com"));

            testService.nestedField(request, () -> {
                capturedEmail.set(Baggage.current().getEntryValue("user.email"));
            });

            assertThat(capturedEmail.get()).isEqualTo("john@example.com");
        }

        @Test
        @DisplayName("Should enrich baggage using root object expression")
        void shouldEnrichBaggageUsingRootObjectExpression() {
            AtomicReference<String> capturedTenant = new AtomicReference<>();

            testService.rootExpression(() -> {
                capturedTenant.set(Baggage.current().getEntryValue("tenant.id"));
            });

            assertThat(capturedTenant.get()).isEqualTo("tenant-42");
        }
    }

    @Nested
    @DisplayName("Null value handling")
    class NullValueHandling {

        @Test
        @DisplayName("Should skip null values without error")
        void shouldSkipNullValuesWithoutError() {
            AtomicReference<String> capturedBaggage = new AtomicReference<>();

            testService.simpleMethod(null, () -> {
                capturedBaggage.set(Baggage.current().getEntryValue("user.id"));
            });

            assertThat(capturedBaggage.get()).isNull();
        }

        @Test
        @DisplayName("Should skip null nested values without error")
        void shouldSkipNullNestedValuesWithoutError() {
            AtomicReference<String> capturedEmail = new AtomicReference<>();
            TestRequest request = new TestRequest(null);

            testService.nestedField(request, () -> {
                capturedEmail.set(Baggage.current().getEntryValue("user.email"));
            });

            assertThat(capturedEmail.get()).isNull();
        }
    }

    @Nested
    @DisplayName("Context restoration")
    class ContextRestoration {

        @Test
        @DisplayName("Should restore original baggage after method execution")
        void shouldRestoreOriginalBaggageAfterMethodExecution() {
            // Set up initial baggage
            Baggage initialBaggage = Baggage.builder()
                    .put("initial.key", "initial-value")
                    .build();

            AtomicReference<String> insideBaggage = new AtomicReference<>();
            AtomicReference<String> outsideBaggage = new AtomicReference<>();

            try (Scope ignored = initialBaggage.makeCurrent()) {
                testService.simpleMethod("method-value", () -> {
                    insideBaggage.set(Baggage.current().getEntryValue("user.id"));
                });

                // After method - should have original baggage
                outsideBaggage.set(Baggage.current().getEntryValue("user.id"));
            }

            assertThat(insideBaggage.get()).isEqualTo("method-value");
            assertThat(outsideBaggage.get()).isNull();
        }

        @Test
        @DisplayName("Should restore context even when exception is thrown")
        void shouldRestoreContextEvenWhenExceptionIsThrown() {
            AtomicReference<String> afterException = new AtomicReference<>();

            try {
                testService.throwingMethod("user-123");
            } catch (RuntimeException e) {
                // Expected
            }

            // Baggage should be restored (not contain the method's value)
            afterException.set(Baggage.current().getEntryValue("user.id"));
            assertThat(afterException.get()).isNull();
        }
    }

    @Nested
    @DisplayName("Span attributes")
    class SpanAttributes {

        @Test
        @DisplayName("Should add to span attributes by default")
        void shouldAddToSpanAttributesByDefault() {
            Span span = tracer.spanBuilder("test-span").startSpan();
            try (Scope ignored = span.makeCurrent()) {
                // simpleMethod uses default addToSpanAttributes=true
                testService.simpleMethod("user-attr-123", () -> {
                    assertThat(Baggage.current().getEntryValue("user.id")).isEqualTo("user-attr-123");
                });
            } finally {
                span.end();
            }

            List<SpanData> spans = spanExporter.getFinishedSpanItems();
            assertThat(spans).hasSize(1);

            SpanData spanData = spans.get(0);
            assertThat(spanData.getAttributes().asMap())
                    .containsEntry(io.opentelemetry.api.common.AttributeKey.stringKey("user.id"), "user-attr-123");
        }

        @Test
        @DisplayName("Should NOT add to span attributes when disabled on field")
        void shouldNotAddToSpanAttributesWhenDisabled() {
            Span span = tracer.spanBuilder("test-span").startSpan();
            try (Scope ignored = span.makeCurrent()) {
                testService.withoutSpanAttributes("baggage-only-value", () -> {
                    // Value should still be in baggage
                    assertThat(Baggage.current().getEntryValue("baggage.only")).isEqualTo("baggage-only-value");
                });
            } finally {
                span.end();
            }

            List<SpanData> spans = spanExporter.getFinishedSpanItems();
            assertThat(spans).hasSize(1);

            SpanData spanData = spans.get(0);
            // Should NOT contain the attribute
            assertThat(spanData.getAttributes().asMap())
                    .doesNotContainKey(io.opentelemetry.api.common.AttributeKey.stringKey("baggage.only"));
        }

        @Test
        @DisplayName("Should handle mixed span attribute settings per field")
        void shouldHandleMixedSpanAttributeSettings() {
            Span span = tracer.spanBuilder("test-span").startSpan();
            try (Scope ignored = span.makeCurrent()) {
                testService.mixedSpanAttributes("visible", "hidden", () -> {
                    assertThat(Baggage.current().getEntryValue("span.visible")).isEqualTo("visible");
                    assertThat(Baggage.current().getEntryValue("span.hidden")).isEqualTo("hidden");
                });
            } finally {
                span.end();
            }

            List<SpanData> spans = spanExporter.getFinishedSpanItems();
            SpanData spanData = spans.get(0);

            // Only "span.visible" should be in attributes
            assertThat(spanData.getAttributes().asMap())
                    .containsEntry(io.opentelemetry.api.common.AttributeKey.stringKey("span.visible"), "visible")
                    .doesNotContainKey(io.opentelemetry.api.common.AttributeKey.stringKey("span.hidden"));
        }

        @Test
        @DisplayName("Should not fail when no active span exists")
        void shouldNotFailWhenNoActiveSpanExists() {
            AtomicReference<String> capturedBaggage = new AtomicReference<>();

            // No span context - should still work for baggage
            testService.simpleMethod("user-no-span", () -> {
                capturedBaggage.set(Baggage.current().getEntryValue("user.id"));
            });

            assertThat(capturedBaggage.get()).isEqualTo("user-no-span");
        }
    }

    @Nested
    @DisplayName("Custom converters")
    class CustomConverters {

        @Test
        @DisplayName("Should use custom converter class")
        void shouldUseCustomConverterClass() {
            AtomicReference<String> capturedBaggage = new AtomicReference<>();

            testService.withCustomConverter(123, () -> {
                capturedBaggage.set(Baggage.current().getEntryValue("custom.value"));
            });

            assertThat(capturedBaggage.get()).isEqualTo("custom:123");
        }

        @Test
        @DisplayName("Should use converter bean")
        void shouldUseConverterBean() {
            AtomicReference<String> capturedBaggage = new AtomicReference<>();

            testService.withConverterBean("input-value", () -> {
                capturedBaggage.set(Baggage.current().getEntryValue("bean.value"));
            });

            assertThat(capturedBaggage.get()).isEqualTo("bean-converted:input-value");
        }
    }

    @Nested
    @DisplayName("Error resilience - business logic should never break")
    class ErrorResilience {

        @Test
        @DisplayName("Should execute method even with duplicate keys (just log warning)")
        void shouldExecuteMethodEvenWithDuplicateKeys() {
            AtomicReference<Boolean> methodExecuted = new AtomicReference<>(false);

            // Should NOT throw exception, just log warning
            testService.duplicateKeys("a", "b", () -> {
                methodExecuted.set(true);
            });

            assertThat(methodExecuted.get()).isTrue();
        }

        @Test
        @DisplayName("Should execute method even with invalid path")
        void shouldExecuteMethodEvenWithInvalidPath() {
            AtomicReference<Boolean> methodExecuted = new AtomicReference<>(false);

            testService.invalidPath("value", () -> {
                methodExecuted.set(true);
            });

            assertThat(methodExecuted.get()).isTrue();
        }

        @Test
        @DisplayName("Should execute method even when converter fails")
        void shouldExecuteMethodEvenWhenConverterFails() {
            AtomicReference<Boolean> methodExecuted = new AtomicReference<>(false);

            testService.withFailingConverter("value", () -> {
                methodExecuted.set(true);
            });

            assertThat(methodExecuted.get()).isTrue();
        }
    }

    // Test configuration

    @Configuration
    @EnableAspectJAutoProxy
    @Import(BaggifyAutoConfiguration.class)
    static class TestConfig {

        @Bean
        public InMemorySpanExporter spanExporter() {
            return InMemorySpanExporter.create();
        }

        @Bean
        public SdkTracerProvider tracerProvider(InMemorySpanExporter spanExporter) {
            return SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                    .build();
        }

        @Bean
        public OpenTelemetrySdk openTelemetry(SdkTracerProvider tracerProvider) {
            return OpenTelemetrySdk.builder()
                    .setTracerProvider(tracerProvider)
                    .build();
        }

        @Bean
        public Tracer tracer(OpenTelemetrySdk openTelemetry) {
            return openTelemetry.getTracer("test-tracer");
        }

        @Bean
        public TestServiceBean testServiceBean() {
            return new TestServiceBean();
        }

        @Bean("testConverterBean")
        public BaggageValueConverter<Object> testConverterBean() {
            return value -> "bean-converted:" + value;
        }
    }

    // Test service

    @Service
    public static class TestServiceBean {
        private final String defaultTenantId = "tenant-42";

        public String getDefaultTenantId() {
            return defaultTenantId;
        }

        @WithBaggage(@BaggageField(key = "user.id", path = "#userId"))
        public void simpleMethod(String userId, Runnable callback) {
            callback.run();
        }

        @WithBaggage({
                @BaggageField(key = "user.id", path = "#userId"),
                @BaggageField(key = "order.id", path = "#orderId")
        })
        public void multipleFields(String userId, String orderId, Runnable callback) {
            callback.run();
        }

        @WithBaggage(@BaggageField(key = "user.email", path = "#request.user.email"))
        public void nestedField(TestRequest request, Runnable callback) {
            callback.run();
        }

        @WithBaggage(@BaggageField(key = "user.id", path = "#userId"))
        public void throwingMethod(String userId) {
            throw new RuntimeException("Test exception");
        }

        @WithBaggage(@BaggageField(key = "baggage.only", path = "#value", addToSpanAttributes = false))
        public void withoutSpanAttributes(String value, Runnable callback) {
            callback.run();
        }

        @WithBaggage({
                @BaggageField(key = "span.visible", path = "#visible"),
                @BaggageField(key = "span.hidden", path = "#hidden", addToSpanAttributes = false)
        })
        public void mixedSpanAttributes(String visible, String hidden, Runnable callback) {
            callback.run();
        }

        @WithBaggage(@BaggageField(key = "custom.value", path = "#value",
                converter = CustomTestConverter.class))
        public void withCustomConverter(Integer value, Runnable callback) {
            callback.run();
        }

        @WithBaggage(@BaggageField(key = "bean.value", path = "#value",
                converterBean = "testConverterBean"))
        public void withConverterBean(String value, Runnable callback) {
            callback.run();
        }

        @WithBaggage({
                @BaggageField(key = "same.key", path = "#value1"),
                @BaggageField(key = "same.key", path = "#value2")
        })
        public void duplicateKeys(String value1, String value2, Runnable callback) {
            callback.run();
        }

        @WithBaggage(@BaggageField(key = "test.key", path = "invalidPath"))
        public void invalidPath(String value, Runnable callback) {
            callback.run();
        }

        @WithBaggage(@BaggageField(key = "test.key", path = "#value",
                converter = FailingConverter.class))
        public void withFailingConverter(String value, Runnable callback) {
            callback.run();
        }

        @WithBaggage(@BaggageField(key = "tenant.id", path = "getDefaultTenantId()"))
        public void rootExpression(Runnable callback) {
            callback.run();
        }
    }

    // Test classes

    public static class TestRequest {
        private final TestUser user;

        public TestRequest(TestUser user) {
            this.user = user;
        }

        public TestUser getUser() {
            return user;
        }
    }

    public static class TestUser {
        private final String email;

        public TestUser(String email) {
            this.email = email;
        }

        public String getEmail() {
            return email;
        }
    }

    public static class CustomTestConverter implements BaggageValueConverter<Integer> {
        @Override
        public String convert(Integer value) {
            return "custom:" + value;
        }
    }

    public static class FailingConverter implements BaggageValueConverter<Object> {
        @Override
        public String convert(Object value) {
            throw new RuntimeException("Converter failed intentionally");
        }
    }
}
