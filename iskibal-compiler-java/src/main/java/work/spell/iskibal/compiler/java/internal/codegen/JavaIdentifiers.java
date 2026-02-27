package work.spell.iskibal.compiler.java.internal.codegen;

/// Utility methods for working with Java identifiers.
public final class JavaIdentifiers {

    private JavaIdentifiers() {
    }

    /// Sanitizes a name to be a valid Java identifier. Spaces and other invalid
    /// characters are replaced or removed, and the result is converted to camelCase.
    ///
    /// @param name
    ///            the name to sanitize
    /// @return a valid Java identifier
    public static String sanitize(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }

        StringBuilder sb = new StringBuilder();
        boolean capitalizeNext = false;

        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);

            if (sb.isEmpty()) {
                // First character
                if (Character.isJavaIdentifierStart(c)) {
                    sb.append(c);
                } else if (Character.isDigit(c)) {
                    // Can't start with digit, prefix with underscore
                    sb.append('_').append(c);
                }
                // Skip other invalid start characters
            } else {
                // Subsequent characters
                if (Character.isJavaIdentifierPart(c)) {
                    if (capitalizeNext) {
                        sb.append(Character.toUpperCase(c));
                        capitalizeNext = false;
                    } else {
                        sb.append(c);
                    }
                } else if (c == ' ' || c == '-' || c == '_') {
                    // Word separator - capitalize next valid character
                    capitalizeNext = true;
                }
                // Skip other invalid characters
            }
        }

        // If nothing valid was found, return a placeholder
        if (sb.isEmpty()) {
            return "_unnamed";
        }

        return sb.toString();
    }

    /// Capitalizes the first letter of a string (for getter/setter names).
    ///
    /// @param s
    ///            the string to capitalize
    /// @return the capitalized string
    public static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /// Sanitizes a name and capitalizes the first letter, suitable for getter names.
    ///
    /// @param name
    ///            the name to process
    /// @return sanitized and capitalized name
    public static String sanitizeAndCapitalize(String name) {
        return capitalize(sanitize(name));
    }
}
