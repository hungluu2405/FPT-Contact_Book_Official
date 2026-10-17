package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TemporaryPasswordGeneratorTest {

    private final TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator();

    @RepeatedTest(200)
    @DisplayName("12 ký tự, có chữ và số, không có ký tự dễ nhầm, qua PasswordPolicy")
    void generatesPolicyCompliantPassword() {
        String password = generator.generate();

        assertThat(password).hasSize(12)
                .matches(".*[A-Za-z].*")
                .matches(".*[0-9].*")
                .doesNotContainPattern("[0Oo1lI]");
        assertThat(PasswordPolicy.validate(null, password, password)).isEmpty();
    }

    @Test
    @DisplayName("Không lặp lại (đủ ngẫu nhiên) qua 1000 lần sinh")
    void passwordsAreNotRepeated() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            seen.add(generator.generate());
        }
        assertThat(seen).hasSize(1000);
    }

    @Test
    void alphabetHasNoAmbiguousCharacters() {
        assertThat(TemporaryPasswordGenerator.LETTERS + TemporaryPasswordGenerator.DIGITS)
                .doesNotContainPattern("[0Oo1lI]");
    }
}
