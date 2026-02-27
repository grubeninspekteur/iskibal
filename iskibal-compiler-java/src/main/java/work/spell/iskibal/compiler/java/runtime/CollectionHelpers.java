package work.spell.iskibal.compiler.java.runtime;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// Runtime helper methods for collection operations in generated rule code.
public final class CollectionHelpers {

    private CollectionHelpers() {
        // Utility class
    }

    /// Accesses an element from a collection by index (for lists) or key (for maps).
    ///
    /// For lists, the index is converted to int. For maps, the key is used directly.
    ///
    /// @param collection
    ///            the collection to access (List or Map)
    /// @param indexOrKey
    ///            the index (for lists) or key (for maps)
    /// @return the element at the given index/key
    @SuppressWarnings("unchecked")
    public static <T> T at(Object collection, Object indexOrKey) {
        if (collection instanceof List<?> list) {
            int index = ((Number) indexOrKey).intValue();
            return (T) list.get(index);
        } else if (collection instanceof Map<?, ?> map) {
            return (T) map.get(indexOrKey);
        }
        throw new IllegalArgumentException("Cannot use 'at:' on " + collection.getClass().getName());
    }

    /// Creates a set containing all integers in the range [start, end] inclusive.
    ///
    /// @param start
    ///            the start of the range (inclusive)
    /// @param end
    ///            the end of the range (inclusive)
    /// @return a set containing all integers from start to end
    public static Set<BigDecimal> range(BigDecimal start, BigDecimal end) {
        Set<BigDecimal> result = new HashSet<>();
        BigDecimal current = start;
        while (current.compareTo(end) <= 0) {
            result.add(current);
            current = current.add(BigDecimal.ONE);
        }
        return result;
    }

    /// Creates a union of multiple sets.
    ///
    /// @param sets
    ///            the sets to union
    /// @return a set containing all elements from all input sets
    @SafeVarargs
    @SuppressWarnings("unchecked")
    public static <T> Set<T> unionSets(Set<? extends T>... sets) {
        Set<T> result = new HashSet<>();
        for (Set<? extends T> set : sets) {
            result.addAll(set);
        }
        return result;
    }
}
