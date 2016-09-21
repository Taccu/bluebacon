package de.dhbw.bluebacon.model;

import java.util.Comparator;
/**
 * Tuple class for coordinates
 * @param <X> Data type of X coordinate
 * @param <Y> Data type of Y coordinate
 */
public class Tuple<X, Y> implements Comparator<Tuple>{
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
    
    @Override
    public int compare(Tuple o1, Tuple o2) {
        if(o1.x == o2.x && o1.y == o2.y) {
            return 0;
        }
        return 1;
    }
    
    @Override
    public boolean equals(Object obj) {
        if(obj instanceof Tuple) {
            Tuple<X,Y> asd = (Tuple<X,Y>) obj;
            return asd.x == this.x && asd.y == this.y;
        }
        return false;
    }
}
