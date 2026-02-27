package work.spell.iskibal.e2e;

import module java.base;

/// Test fixture class representing a car for navigation tests.
public class Car {

    private final List<Passenger> passengers;

    public Car(List<Passenger> passengers) {
        this.passengers = passengers;
    }

    public List<Passenger> getPassengers() {
        return passengers;
    }
}
