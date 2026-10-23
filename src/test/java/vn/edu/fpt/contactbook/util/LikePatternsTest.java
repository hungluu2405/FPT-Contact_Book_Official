package vn.edu.fpt.contactbook.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class LikePatternsTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Nguyễn|%nguyễn%",
            "  SE19 |%se19%",
            "_|%\\_%",
            "50%|%50\\%%",
            "[0]|%\\[0]%",
            "a\\b|%a\\\\b%"
    })
    void escapesWildcards(String keyword, String expected) {
        assertThat(LikePatterns.containsIgnoreCase(keyword)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void blankKeywordMeansNoFilter(String keyword) {
        assertThat(LikePatterns.containsIgnoreCase(keyword)).isNull();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "TOAN10-HK1-2627-|TOAN10-HK1-2627-%",
            "SE_1%|SE\\_1\\%%",
            "A[B|A\\[B%"
    })
    void startsWithKeepsCaseAndEscapes(String prefix, String expected) {
        assertThat(LikePatterns.startsWith(prefix)).isEqualTo(expected);
    }
}
