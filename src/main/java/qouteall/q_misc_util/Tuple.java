package qouteall.q_misc_util;

import java.util.Objects;

/**
 * Replacement for qouteall.q_misc_util.Tuple, which was removed from Minecraft.
 * Same API (getA/getB/setA/setB) so the port can keep existing call sites.
 */
public class Tuple<A, B> {
    private A a;
    private B b;

    public Tuple(A a, B b) {
        this.a = a;
        this.b = b;
    }

    public A getA() {
        return a;
    }

    public void setA(A a) {
        this.a = a;
    }

    public B getB() {
        return b;
    }

    public void setB(B b) {
        this.b = b;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Tuple<?, ?> t && Objects.equals(a, t.a) && Objects.equals(b, t.b);
    }

    @Override
    public int hashCode() {
        return Objects.hash(a, b);
    }
}
