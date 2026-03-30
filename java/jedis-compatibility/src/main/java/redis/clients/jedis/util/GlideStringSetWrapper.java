/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.util;

import glide.api.models.GlideString;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

/**
 * A Set<byte[]> wrapper around Set<GlideString> that avoids the performance degradation of
 * HashSet<byte[]>.
 *
 * <p>byte[] arrays use identity hashCode, causing all entries to hash to the same bucket in a
 * HashSet, degrading performance to O(n). This wrapper keeps data as GlideString internally (which
 * has proper hashCode/equals implementations) and converts to byte[] only when accessed.
 *
 * <p>This is an unmodifiable Set - all mutation operations throw UnsupportedOperationException.
 */
public class GlideStringSetWrapper extends AbstractSet<byte[]> {

    private final Set<GlideString> wrapped;

    public GlideStringSetWrapper(Set<GlideString> glideStringSet) {
        this.wrapped = glideStringSet;
    }

    @Override
    public int size() {
        return wrapped.size();
    }

    @Override
    public boolean isEmpty() {
        return wrapped.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        if (!(o instanceof byte[])) {
            return false;
        }
        byte[] bytes = (byte[]) o;
        return wrapped.contains(GlideString.of(bytes));
    }

    @Override
    public Iterator<byte[]> iterator() {
        return new Iterator<byte[]>() {
            private final Iterator<GlideString> it = wrapped.iterator();

            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public byte[] next() {
                return it.next().getBytes();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException(
                        "GlideStringSetWrapper is unmodifiable - remove not supported");
            }
        };
    }

    @Override
    public Object[] toArray() {
        Object[] result = new Object[wrapped.size()];
        int i = 0;
        for (GlideString gs : wrapped) {
            result[i++] = gs.getBytes();
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T[] toArray(T[] a) {
        int size = wrapped.size();
        if (a.length < size) {
            a = (T[]) java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), size);
        }
        int i = 0;
        for (GlideString gs : wrapped) {
            a[i++] = (T) gs.getBytes();
        }
        if (a.length > size) {
            a[size] = null;
        }
        return a;
    }

    @Override
    public boolean add(byte[] bytes) {
        throw new UnsupportedOperationException(
                "GlideStringSetWrapper is unmodifiable - add not supported");
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException(
                "GlideStringSetWrapper is unmodifiable - remove not supported");
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object o : c) {
            if (!contains(o)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean addAll(Collection<? extends byte[]> c) {
        throw new UnsupportedOperationException(
                "GlideStringSetWrapper is unmodifiable - addAll not supported");
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException(
                "GlideStringSetWrapper is unmodifiable - retainAll not supported");
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException(
                "GlideStringSetWrapper is unmodifiable - removeAll not supported");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException(
                "GlideStringSetWrapper is unmodifiable - clear not supported");
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof Set)) return false;
        Set<?> other = (Set<?>) o;
        if (other.size() != size()) return false;

        try {
            for (Object elem : other) {
                if (elem instanceof byte[]) {
                    if (!contains(elem)) return false;
                } else {
                    return false;
                }
            }
        } catch (ClassCastException | NullPointerException unused) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int h = 0;
        for (GlideString gs : wrapped) {
            // Use Arrays.hashCode for byte[] semantic equality
            h += Arrays.hashCode(gs.getBytes());
        }
        return h;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (GlideString gs : wrapped) {
            if (!first) sb.append(", ");
            sb.append(Arrays.toString(gs.getBytes()));
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }
}
