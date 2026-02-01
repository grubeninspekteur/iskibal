package work.spell.iskibal.e2e;

import java.util.List;

/**
 * Test fixture class representing a car for navigation tests.
 */
public class Car {

    private final List<Passenger> passengers;

    public Car(List<Passenger> passengers) {
        this.passengers = passengers;
    }

    public List<Passenger> getPassengers() {
        return passengers;
    }
}
