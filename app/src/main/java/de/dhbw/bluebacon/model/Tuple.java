package de.dhbw.bluebacon.model;

/**
 * Tuple class for coordinates
 * @param <X> Data type of X coordinate
 * @param <Y> Data type of Y coordinate
 */
//TODO: pray this will never be used as keys for hashing especially regarding equality, immutability, etc...
public class Tuple<X, Y> {
    protected final X x;
    protected final Y y;

    public Tuple(X x, Y y) {
        this.x = x;
        this.y = y;
    }

    public X getX() {
        return this.x;
    }

    public Y getY() {
        return this.y;
    }
}
