package dev.vality.otel.baggify.converter;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.convert.ConversionService;

import dev.vality.otel.baggify.annotation.BaggageField;

@DisplayName("BaggageValueConverterResolver")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BaggageValueConverterResolverTest {

    @Mock
    private BeanFactory beanFactory;

    @Mock
    private ConversionService conversionService;

    private BaggageValueConverterResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new BaggageValueConverterResolver(beanFactory, conversionService);
    }

    @Nested
    @DisplayName("Null handling")
    class NullHandling {

        @Test
        @DisplayName("Should return null for null value")
        void shouldReturnNullForNullValue() {
            BaggageField field = mockBaggageField("key", "#path", BaggageValueConverter.NoOp.class, "");

            String result = resolver.convert(null, field);

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("Converter bean priority")
    class ConverterBeanPriority {

        @Test
        @DisplayName("Should use converter bean when specified")
        @SuppressWarnings("unchecked")
        void shouldUseConverterBeanWhenSpecified() {
            BaggageField field = mockBaggageField("key", "#path", BaggageValueConverter.NoOp.class, "customConverter");
            BaggageValueConverter<Object> mockConverter = mock(BaggageValueConverter.class);
            when(beanFactory.getBean("customConverter", BaggageValueConverter.class)).thenReturn(mockConverter);
            when(mockConverter.convert("test")).thenReturn("converted-by-bean");

            String result = resolver.convert("test", field);

            assertThat(result).isEqualTo("converted-by-bean");
        }
    }

    @Nested
    @DisplayName("Converter class priority")
    class ConverterClassPriority {

        @Test
        @DisplayName("Should use converter class when specified")
        void shouldUseConverterClassWhenSpecified() {
            BaggageField field = mockBaggageField("key", "#path", TestConverter.class, "");

            String result = resolver.convert(42, field);

            assertThat(result).isEqualTo("test-converted:42");
        }

        @Test
        @DisplayName("Should cache converter instances")
        void shouldCacheConverterInstances() {
            // Reset counter before test
            CountingConverter.instanceCount = 0;
            BaggageField field = mockBaggageField("key", "#path", CountingConverter.class, "");

            resolver.convert("value1", field);
            resolver.convert("value2", field);

            // If instances were cached, counter should be 1 (same instance used twice)
            assertThat(CountingConverter.instanceCount).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("ConversionService priority")
    class ConversionServicePriority {

        @Test
        @DisplayName("Should use ConversionService when no custom converter")
        void shouldUseConversionServiceWhenNoCustomConverter() {
            BaggageField field = mockBaggageField("key", "#path", BaggageValueConverter.NoOp.class, "");
            UUID uuid = UUID.randomUUID();
            when(conversionService.canConvert(UUID.class, String.class)).thenReturn(true);
            when(conversionService.convert(uuid, String.class)).thenReturn("uuid-converted");

            String result = resolver.convert(uuid, field);

            assertThat(result).isEqualTo("uuid-converted");
        }
    }

    @Nested
    @DisplayName("String passthrough")
    class StringPassthrough {

        @Test
        @DisplayName("Should return String value as-is when no converter")
        void shouldReturnStringValueAsIs() {
            resolver = new BaggageValueConverterResolver(beanFactory, null); // No ConversionService
            BaggageField field = mockBaggageField("key", "#path", BaggageValueConverter.NoOp.class, "");

            String result = resolver.convert("direct-string", field);

            assertThat(result).isEqualTo("direct-string");
        }
    }

    @Nested
    @DisplayName("ToString fallback")
    class ToStringFallback {

        @Test
        @DisplayName("Should use toString() as last resort")
        void shouldUseToStringAsLastResort() {
            resolver = new BaggageValueConverterResolver(beanFactory, null); // No ConversionService
            BaggageField field = mockBaggageField("key", "#path", BaggageValueConverter.NoOp.class, "");
            TestObject obj = new TestObject("test-value");

            String result = resolver.convert(obj, field);

            assertThat(result).isEqualTo("TestObject[test-value]");
        }
    }

    @Nested
    @DisplayName("Error handling - graceful fallback")
    class ErrorHandling {

        @Test
        @DisplayName("Should fallback to toString when converter has no no-arg constructor")
        void shouldFallbackToToStringWhenConverterHasNoNoArgConstructor() {
            BaggageField field = mockBaggageField("key", "#path", ConverterWithoutNoArgConstructor.class, "");

            // Should NOT throw exception, instead fallback to toString
            String result = resolver.convert("test-value", field);

            assertThat(result).isEqualTo("test-value");
        }

        @Test
        @DisplayName("Should fallback when converter bean not found")
        void shouldFallbackWhenConverterBeanNotFound() {
            BaggageField field = mockBaggageField("key", "#path", BaggageValueConverter.NoOp.class, "nonExistentBean");
            when(beanFactory.getBean("nonExistentBean", BaggageValueConverter.class))
                    .thenThrow(new org.springframework.beans.factory.NoSuchBeanDefinitionException("nonExistentBean"));

            // Should NOT throw exception, instead fallback to toString
            String result = resolver.convert("fallback-value", field);

            assertThat(result).isEqualTo("fallback-value");
        }
    }

    // Helper method to create mock BaggageField with lenient stubbing
    @SuppressWarnings("unchecked")
    private BaggageField mockBaggageField(String key, String path,
                                          Class<? extends BaggageValueConverter<?>> converter,
                                          String converterBean) {
        BaggageField field = mock(BaggageField.class);
        lenient().when(field.key()).thenReturn(key);
        lenient().when(field.path()).thenReturn(path);
        lenient().doReturn(converter).when(field).converter();
        lenient().when(field.converterBean()).thenReturn(converterBean);
        return field;
    }

    // Test converter classes

    public static class TestConverter implements BaggageValueConverter<Object> {
        @Override
        public String convert(Object value) {
            return "test-converted:" + value;
        }
    }

    public static class CountingConverter implements BaggageValueConverter<Object> {
        static int instanceCount = 0;

        public CountingConverter() {
            instanceCount++;
        }

        @Override
        public String convert(Object value) {
            return value.toString();
        }
    }

    public static class ConverterWithoutNoArgConstructor implements BaggageValueConverter<Object> {
        @SuppressWarnings("unused")
        public ConverterWithoutNoArgConstructor(String required) {
        }

        @Override
        public String convert(Object value) {
            return value.toString();
        }
    }

    public static class TestObject {
        private final String value;

        public TestObject(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return "TestObject[" + value + "]";
        }
    }
}
