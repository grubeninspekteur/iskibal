package work.spell.iskibal.compiler.java.runtime;

import java.util.List;
import java.util.Map;

/**
 * Runtime helper methods for collection operations in generated rule code.
 */
public final class CollectionHelpers {

	private CollectionHelpers() {
		// Utility class
	}

	/**
	 * Accesses an element from a collection by index (for lists) or key (for maps).
	 * <p>
	 * For lists, the index is converted to int. For maps, the key is used directly.
	 *
	 * @param collection
	 *            the collection to access (List or Map)
	 * @param indexOrKey
	 *            the index (for lists) or key (for maps)
	 * @return the element at the given index/key
	 */
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
}
