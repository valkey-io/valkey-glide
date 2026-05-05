/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

import java.util.Objects;

/**
 * Parameters for ZADD command. Provides options for controlling ZADD behavior such as NX (only add
 * new elements), XX (only update existing elements), GT (only update if new score is greater), LT
 * (only update if new score is less), and CH (return number of elements changed instead of added).
 *
 * <p>This class is compatible with Jedis ZAddParams and provides the same builder-style API.
 */
public abstract class AbstractZAddParams<T extends AbstractZAddParams<T>> {

    @SuppressWarnings("unchecked")
    protected final T self() {
        return (T) this;
    }

    private Boolean nx;
    private Boolean xx;
    private Boolean gt;
    private Boolean lt;
    private Boolean ch;

    protected AbstractZAddParams() {}

    /**
     * Only set the key if it does not already exist.
     *
     * @return ZAddParams
     */
    public T nx() {
        this.nx = true;
        return self();
    }

    /**
     * Only set the key if it already exists.
     *
     * @return ZAddParams
     */
    public T xx() {
        this.xx = true;
        return self();
    }

    /**
     * Only update existing elements if the new score is greater than the current score.
     *
     * @return ZAddParams
     */
    public T gt() {
        this.gt = true;
        return self();
    }

    /**
     * Only update existing elements if the new score is less than the current score.
     *
     * @return ZAddParams
     */
    public T lt() {
        this.lt = true;
        return self();
    }

    /**
     * Modify the return value from the number of new elements added to the total number of elements
     * changed.
     *
     * @return ZAddParams
     */
    public T ch() {
        this.ch = true;
        return self();
    }

    public Boolean getNx() {
        return nx;
    }

    public Boolean getXx() {
        return xx;
    }

    public Boolean getGt() {
        return gt;
    }

    public Boolean getLt() {
        return lt;
    }

    public Boolean getCh() {
        return ch;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AbstractZAddParams<?> that = (AbstractZAddParams<?>) o;
        return Objects.equals(nx, that.nx)
                && Objects.equals(xx, that.xx)
                && Objects.equals(gt, that.gt)
                && Objects.equals(lt, that.lt)
                && Objects.equals(ch, that.ch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nx, xx, gt, lt, ch);
    }
}
