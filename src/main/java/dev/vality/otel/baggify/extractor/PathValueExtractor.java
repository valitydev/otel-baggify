package dev.vality.otel.baggify.extractor;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * Extracts values from method arguments using path expressions.
 *
 * <p>Supports:
 * <ul>
 *   <li>Direct parameter access: {@code #paramName}</li>
 *   <li>Nested field access via dot-notation: {@code #paramName.field.subfield}</li>
 *   <li>JavaBean getters and record accessors</li>
 * </ul>
 *
 * <p>This class is designed to be fault-tolerant. Invalid paths or
 * missing values result in null return values with warning logs,
 * not exceptions.
 */
@Slf4j
public class PathValueExtractor {

    private static final String PATH_PREFIX = "#";
    private static final String PATH_SEPARATOR = "\\.";

    /**
     * Extracts a value from method arguments using the given path expression.
     * <p>
     * This method never throws exceptions. Invalid paths or extraction
     * failures result in null return values with warning logs.
     *
     * @param path   the path expression (e.g., "#userId" or "#request.user.id")
     * @param method the method being invoked
     * @param args   the method arguments
     * @return the extracted value, or null if not found or path is invalid
     */
    public Object extractValue(String path, Method method, Object[] args) {
        // Validate path
        if (path == null || path.isBlank()) {
            log.warn("Path is null or blank");
            return null;
        }

        if (!path.startsWith(PATH_PREFIX)) {
            log.warn("Path '{}' must start with '#'", path);
            return null;
        }

        if (path.length() <= 1) {
            log.warn("Path '{}' must specify a parameter name after '#'", path);
            return null;
        }

        try {
            String normalizedPath = path.substring(PATH_PREFIX.length());
            String[] pathParts = normalizedPath.split(PATH_SEPARATOR);
            String parameterName = pathParts[0];

            // Find parameter index by name
            int paramIndex = findParameterIndex(method, parameterName);
            if (paramIndex < 0) {
                log.warn("Parameter '{}' not found in method '{}'. " +
                                "Ensure the parameter exists and code is compiled with -parameters flag.",
                        parameterName, method.getName());
                return null;
            }

            if (paramIndex >= args.length) {
                log.warn("Parameter index {} out of bounds for method '{}' with {} arguments",
                        paramIndex, method.getName(), args.length);
                return null;
            }

            Object value = args[paramIndex];

            // Navigate nested path
            for (int i = 1; i < pathParts.length && value != null; i++) {
                value = getFieldValue(value, pathParts[i]);
            }

            return value;

        } catch (Exception e) {
            log.warn("Failed to extract value at path '{}' from method '{}': {}",
                    path, method.getName(), e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("Value extraction error details", e);
            }
            return null;
        }
    }

    /**
     * Builds a map of parameter names to their values for the given method invocation.
     *
     * @param method the method being invoked
     * @param args   the method arguments
     * @return map of parameter name to value
     */
    public Map<String, Object> buildParameterMap(Method method, Object[] args) {
        Map<String, Object> parameterMap = new HashMap<>();
        Parameter[] parameters = method.getParameters();

        for (int i = 0; i < parameters.length && i < args.length; i++) {
            if (parameters[i].isNamePresent()) {
                parameterMap.put(parameters[i].getName(), args[i]);
            }
        }

        return parameterMap;
    }

    private int findParameterIndex(Method method, String parameterName) {
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isNamePresent()
                    && parameters[i].getName().equals(parameterName)) {
                return i;
            }
        }
        return -1;
    }

    private Object getFieldValue(Object target, String fieldName) {
        if (target == null) {
            return null;
        }

        Class<?> clazz = target.getClass();

        // Try getter method (getXxx or isXxx for boolean)
        String capitalizedName = capitalize(fieldName);
        try {
            Method getter = clazz.getMethod("get" + capitalizedName);
            return getter.invoke(target);
        } catch (NoSuchMethodException e) {
            // Try boolean getter
            try {
                Method booleanGetter = clazz.getMethod("is" + capitalizedName);
                return booleanGetter.invoke(target);
            } catch (NoSuchMethodException ex) {
                // Try record accessor (field name as method name)
                try {
                    Method accessor = clazz.getMethod(fieldName);
                    return accessor.invoke(target);
                } catch (NoSuchMethodException exc) {
                    log.trace("No accessor found for field '{}' on class '{}'",
                            fieldName, clazz.getSimpleName());
                    return null;
                } catch (Exception exc) {
                    log.trace("Failed to invoke accessor '{}' on class '{}': {}",
                            fieldName, clazz.getSimpleName(), exc.getMessage());
                    return null;
                }
            } catch (Exception ex) {
                log.trace("Failed to invoke boolean getter for '{}' on class '{}': {}",
                        fieldName, clazz.getSimpleName(), ex.getMessage());
                return null;
            }
        } catch (Exception e) {
            log.trace("Failed to invoke getter for '{}' on class '{}': {}",
                    fieldName, clazz.getSimpleName(), e.getMessage());
            return null;
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}
