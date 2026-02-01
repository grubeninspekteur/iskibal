package work.spell.iskibal.compiler.common.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Represents a scope in the symbol table. Scopes can be nested with parent
 * lookups.
 */
public final class Scope {

    private final Scope parent;
    private final Map<String, Symbol> symbols = new HashMap<>();

    public Scope() {
        this.parent = null;
    }

    public Scope(Scope parent) {
        this.parent = parent;
    }

    /**
     * Defines a symbol in this scope.
     *
     * @param symbol
     *            the symbol to define
     * @return true if the symbol was added, false if it already exists in this
     *         scope
     */
    public boolean define(Symbol symbol) {
        if (symbols.containsKey(symbol.name())) {
            return false;
        }
        symbols.put(symbol.name(), symbol);
        return true;
    }

    /**
     * Looks up a symbol by name, searching parent scopes if not found locally.
     *
     * @param name
     *            the name to look up
     * @return the symbol if found
     */
    public Optional<Symbol> lookup(String name) {
        Symbol symbol = symbols.get(name);
        if (symbol != null) {
            return Optional.of(symbol);
        }
        if (parent != null) {
            return parent.lookup(name);
        }
        return Optional.empty();
    }

    /**
     * Checks if a symbol exists in this scope only (not parent scopes).
     *
     * @param name
     *            the name to check
     * @return true if the symbol exists in this scope
     */
    public boolean existsLocally(String name) {
        return symbols.containsKey(name);
    }

    /**
     * Creates a new child scope with this scope as parent.
     *
     * @return the new child scope
     */
    public Scope createChild() {
        return new Scope(this);
    }

    /**
     * Returns the parent scope, or null if this is the root scope.
     */
    public Scope getParent() {
        return parent;
    }
}
