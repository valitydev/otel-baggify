package dev.vality.otel.baggify.extractor;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PathValueExtractor")
class PathValueExtractorTest {

    private PathValueExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new PathValueExtractor();
    }

    @Nested
    @DisplayName("Path validation - returns null instead of throwing")
    class PathValidation {

        @Test
        @DisplayName("Should return null for null path")
        void shouldReturnNullForNullPath() throws Exception {
            Method method = TestService.class.getMethod("simpleMethod", String.class);

            Object result = extractor.extractValue(null, method, new Object[]{"value"});

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should return null for blank path")
        void shouldReturnNullForBlankPath() throws Exception {
            Method method = TestService.class.getMethod("simpleMethod", String.class);

            Object result = extractor.extractValue("  ", method, new Object[]{"value"});

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should return null for path without # prefix")
        void shouldReturnNullForPathWithoutHashPrefix() throws Exception {
            Method method = TestService.class.getMethod("simpleMethod", String.class);

            Object result = extractor.extractValue("userId", method, new Object[]{"value"});

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should return null for path with only #")
        void shouldReturnNullForPathWithOnlyHash() throws Exception {
            Method method = TestService.class.getMethod("simpleMethod", String.class);

            Object result = extractor.extractValue("#", method, new Object[]{"value"});

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("Simple parameter extraction")
    class SimpleParameterExtraction {

        @Test
        @DisplayName("Should extract String parameter")
        void shouldExtractStringParameter() throws Exception {
            Method method = TestService.class.getMethod("simpleMethod", String.class);

            Object result = extractor.extractValue("#userId", method, new Object[]{"test-user"});

            assertThat(result).isEqualTo("test-user");
        }

        @Test
        @DisplayName("Should extract Integer parameter")
        void shouldExtractIntegerParameter() throws Exception {
            Method method = TestService.class.getMethod("methodWithInteger", Integer.class);

            Object result = extractor.extractValue("#count", method, new Object[]{42});

            assertThat(result).isEqualTo(42);
        }

        @Test
        @DisplayName("Should return null for null parameter")
        void shouldReturnNullForNullParameter() throws Exception {
            Method method = TestService.class.getMethod("simpleMethod", String.class);

            Object result = extractor.extractValue("#userId", method, new Object[]{null});

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should return null for unknown parameter (no exception)")
        void shouldReturnNullForUnknownParameter() throws Exception {
            Method method = TestService.class.getMethod("simpleMethod", String.class);

            Object result = extractor.extractValue("#unknown", method, new Object[]{"value"});

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("Nested field extraction")
    class NestedFieldExtraction {

        @Test
        @DisplayName("Should extract nested field via getter")
        void shouldExtractNestedFieldViaGetter() throws Exception {
            Method method = TestService.class.getMethod("methodWithRequest", TestRequest.class);
            TestRequest request = new TestRequest("user-123", new TestUser("John", "john@test.com"));

            Object result = extractor.extractValue("#request.userId", method, new Object[]{request});

            assertThat(result).isEqualTo("user-123");
        }

        @Test
        @DisplayName("Should extract deeply nested field")
        void shouldExtractDeeplyNestedField() throws Exception {
            Method method = TestService.class.getMethod("methodWithRequest", TestRequest.class);
            TestRequest request = new TestRequest("user-123", new TestUser("John", "john@test.com"));

            Object result = extractor.extractValue("#request.user.email", method, new Object[]{request});

            assertThat(result).isEqualTo("john@test.com");
        }

        @Test
        @DisplayName("Should return null when intermediate field is null")
        void shouldReturnNullWhenIntermediateFieldIsNull() throws Exception {
            Method method = TestService.class.getMethod("methodWithRequest", TestRequest.class);
            TestRequest request = new TestRequest("user-123", null);

            Object result = extractor.extractValue("#request.user.email", method, new Object[]{request});

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should extract field from record")
        void shouldExtractFieldFromRecord() throws Exception {
            Method method = TestService.class.getMethod("methodWithRecord", TestRecord.class);
            TestRecord record = new TestRecord("record-id", "Record Name");

            Object result = extractor.extractValue("#record.id", method, new Object[]{record});

            assertThat(result).isEqualTo("record-id");
        }

        @Test
        @DisplayName("Should return null for non-existent field (no exception)")
        void shouldReturnNullForNonExistentField() throws Exception {
            Method method = TestService.class.getMethod("methodWithRequest", TestRequest.class);
            TestRequest request = new TestRequest("user-123", new TestUser("John", "john@test.com"));

            Object result = extractor.extractValue("#request.nonExistent", method, new Object[]{request});

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("Multiple parameters")
    class MultipleParameters {

        @Test
        @DisplayName("Should extract correct parameter from multiple")
        void shouldExtractCorrectParameterFromMultiple() throws Exception {
            Method method = TestService.class.getMethod("multipleParams", String.class, Integer.class, String.class);

            Object result = extractor.extractValue("#name", method, new Object[]{"id-1", 42, "John"});

            assertThat(result).isEqualTo("John");
        }
    }

    // Test classes and interfaces

    @SuppressWarnings("unused")
    public static class TestService {
        public void simpleMethod(String userId) {}
        public void methodWithInteger(Integer count) {}
        public void methodWithRequest(TestRequest request) {}
        public void methodWithRecord(TestRecord record) {}
        public void multipleParams(String id, Integer count, String name) {}
    }

    public static class TestRequest {
        private final String userId;
        private final TestUser user;

        public TestRequest(String userId, TestUser user) {
            this.userId = userId;
            this.user = user;
        }

        public String getUserId() {
            return userId;
        }

        public TestUser getUser() {
            return user;
        }
    }

    public static class TestUser {
        private final String name;
        private final String email;

        public TestUser(String name, String email) {
            this.name = name;
            this.email = email;
        }

        public String getName() {
            return name;
        }

        public String getEmail() {
            return email;
        }
    }

    public record TestRecord(String id, String name) {}
}
