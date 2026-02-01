package work.spell.iskibal.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import work.spell.iskibal.testing.RuleTestBuilder;
import work.spell.iskibal.testing.RuleTestResult;

/**
 * End-to-end tests for navigation expressions. Tests property access, nested
 * navigation, and null-safe chains.
 */
class NavigationE2ETest {

    @Nested
    @DisplayName("Simple Navigation")
    class SimpleNavigation {

        @Test
        @DisplayName("Navigate to single property")
        void navigateToSingleProperty() throws Exception {
            String source = """
                    facts {
                        passenger: work.spell.iskibal.e2e.Passenger
                    }
                    outputs {
                        result: String := ""
                    }
                    rule NAV1 "Get passenger name"
                    when true
                    then
                        result := passenger.name
                    end
                    """;

            var passenger = new Passenger("Alice", new Passport(true, "US"));
            var result = RuleTestBuilder.forSource(source).withFact(passenger).build();

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<String>getOutput("result")).isEqualTo("Alice");
        }

        @Test
        @DisplayName("Navigate to nested property")
        void navigateToNestedProperty() throws Exception {
            String source = """
                    facts {
                        passenger: work.spell.iskibal.e2e.Passenger
                    }
                    outputs {
                        result: String := ""
                    }
                    rule NAV2 "Get passport country"
                    when true
                    then
                        result := passenger.passport.countryCode
                    end
                    """;

            var passenger = new Passenger("Bob", new Passport(true, "DE"));
            var result = RuleTestBuilder.forSource(source).withFact(passenger).build();

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<String>getOutput("result")).isEqualTo("DE");
        }
    }

    @Nested
    @DisplayName("Null-Safe Navigation")
    class NullSafeNavigation {

        @Test
        @DisplayName("Null-safe navigation returns null for null intermediate")
        void nullSafeNavigationReturnsNullForNullIntermediate() throws Exception {
            String source = """
                    facts {
                        passenger: work.spell.iskibal.e2e.Passenger
                    }
                    outputs {
                        result: String := "default"
                    }
                    rule NAV3 "Get passport country safely"
                    when
                        passenger.passport.countryCode = "US"
                    then
                        result := "american"
                    end
                    """;

            // Passenger with null passport
            var passenger = new Passenger("Charlie", null);
            var result = RuleTestBuilder.forSource(source).withFact(passenger).build();

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            // Should keep default value since condition is not met (null != "US")
            assertThat(rules.<String>getOutput("result")).isEqualTo("default");
        }
    }

    @Nested
    @DisplayName("Navigation Assignment")
    class NavigationAssignment {

        @Test
        @DisplayName("Navigate and assign to property setter")
        void navigateAndAssignToPropertySetter() throws Exception {
            String source = """
                    facts {
                        customer: work.spell.iskibal.e2e.MutableCustomer
                    }
                    outputs {
                        result: String := ""
                    }
                    rule CAT1 "Assign category"
                    when true
                    then
                        customer.category := "VIP"
                        result := customer.category
                    end
                    """;

            var customer = new MutableCustomer("Alice", "Regular");
            var result = RuleTestBuilder.forSource(source).withFact(customer).build();

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<String>getOutput("result")).isEqualTo("VIP");
            assertThat(customer.getCategory()).isEqualTo("VIP");
        }
    }

    @Nested
    @DisplayName("Navigation on Records")
    class NavigationOnRecords {

        @Test
        @DisplayName("Navigate to record component")
        void navigateToRecordComponent() throws Exception {
            String source = """
                    facts {
                        person: work.spell.iskibal.e2e.PersonRecord
                    }
                    outputs {
                        result: String := ""
                    }
                    rule REC1 "Get person name from record"
                    when true
                    then
                        result := person.name
                    end
                    """;

            var person = new PersonRecord("Alice", 30);
            var result = RuleTestBuilder.forSource(source).withFact(person).build();

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<String>getOutput("result")).isEqualTo("Alice");
        }
    }

    @Nested
    @DisplayName("Navigation on Collections")
    class NavigationOnCollections {

        @Test
        @DisplayName("Navigate to collection property and check size using message")
        void navigateToCollectionPropertyCheckSize() throws Exception {
            String source = """
                    facts {
                        car: work.spell.iskibal.e2e.Car
                    }
                    outputs {
                        passengerSize: BigDecimal := 0
                    }
                    rule NAV4 "Get passenger size"
                    when
                        true
                    then
                        passengerSize := car.passengers size
                    end
                    """;

            var passengers = List.of(new Passenger("Alice", new Passport(true, "US")),
                    new Passenger("Bob", new Passport(true, "DE")),
                    new Passenger("Charlie", new Passport(false, "UK")));
            var car = new Car(passengers);

            var result = RuleTestBuilder.forSource(source).withFact(car).build();

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<BigDecimal>getOutput("passengerSize")).isEqualTo(BigDecimal.valueOf(3));
        }
    }

    private void assertResultSuccess(RuleTestResult result) {
        if (!result.isSuccess()) {
            String errorMessage = String.format("Expected success at stage '%s' but got errors: %s", result.getStage(),
                    result.getErrors());
            if (result instanceof RuleTestResult.CompilationFailure cf) {
                errorMessage += "\n\nGenerated source:\n" + cf.generatedSource();
            }
            throw new AssertionError(errorMessage);
        }
    }
}
