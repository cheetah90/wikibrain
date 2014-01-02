package org.wikapidia.utils;

import gnu.trove.map.TIntDoubleMap;
import gnu.trove.map.TIntFloatMap;
import org.apache.commons.lang3.ArrayUtils;

import java.util.*;

/**
 * @author Shilad Sen
 */
public class WpCollectionUtils {
    public static <K, V extends Comparable<V>> List<K> sortMapKeys(final Map<K, V> map, boolean reverse) {
        List<K> keys = new ArrayList<K>(map.keySet());
        Collections.sort(keys, new Comparator<K>() {
            @Override
            public int compare(K k1, K k2) {
                return map.get(k1).compareTo(map.get(k2));
            }
        });
        if (reverse) {
            Collections.reverse(keys);
        }
        return keys;
    }

    public static <K, V extends Comparable<V>> List<K> sortMapKeys(final Map<K, V> map) {
        return sortMapKeys(map, false);
    }

    public static int[] sortMapKeys(final TIntFloatMap map, boolean reverse) {
        Integer keys[] = ArrayUtils.toObject(map.keys());
        Arrays.sort(keys, new Comparator<Integer>() {
            @Override
            public int compare(Integer k1, Integer k2) {
                Float v1 = map.get(k1);
                Float v2 = map.get(k2);
                return v1.compareTo(v2);
            }
        });
        if (reverse) {
            ArrayUtils.reverse(keys);
        }
        return ArrayUtils.toPrimitive(keys);
    }
    public static int[] sortMapKeys(final TIntDoubleMap map, boolean reverse) {
        Integer keys[] = ArrayUtils.toObject(map.keys());
        Arrays.sort(keys, new Comparator<Integer>() {
            @Override
            public int compare(Integer k1, Integer k2) {
                Double v1 = map.get(k1);
                Double v2 = map.get(k2);
                return v1.compareTo(v2);
            }
        });
        if (reverse) {
            ArrayUtils.reverse(keys);
        }
        return ArrayUtils.toPrimitive(keys);
    }

    public static <K, V extends Comparable<V>> LinkedHashMap<K, V> sortMap(final Map<K, V> map) {
        return sortMap(map, false);
    }

    public static <K, V extends Comparable<V>> LinkedHashMap<K, V> sortMap(final Map<K, V> map, boolean reverse) {
        LinkedHashMap<K, V> sorted = new LinkedHashMap<K, V>();
        for (K key : sortMapKeys(map, reverse)) {
            sorted.put(key, map.get(key));
        }
        return sorted;
    }
}
