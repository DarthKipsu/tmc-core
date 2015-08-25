package fi.helsinki.cs.tmc.core.cache;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface KeyValueCache<K, V> extends Cache<Map<K, V>> {

    void put(K key, V value) throws IOException;

    void clearValue(K key) throws IOException;

    V get(K key) throws IOException;

    Set<K> keySet() throws IOException;

    Collection<V> values() throws IOException;
}
