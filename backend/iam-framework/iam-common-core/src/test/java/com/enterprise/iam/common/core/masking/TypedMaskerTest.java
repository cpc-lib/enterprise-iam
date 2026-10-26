package com.enterprise.iam.common.core.masking;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Typed mask execution per SPEC 38 §5: deterministic typed transforms with
 * validated config, every failure mode failing closed to a full mask.
 */
class TypedMaskerTest {

    @Test
    void phoneKeepsFirstThreeAndLastFour() {
        assertThat(TypedMasker.mask(MaskStrategyType.PHONE, Map.of(), "13812345678"))
                .isEqualTo("138***5678");
    }

    @Test
    void emailKeepsFirstLocalCharAndFullDomain() {
        assertThat(TypedMasker.mask(MaskStrategyType.EMAIL, Map.of(), "alice@example.com"))
                .isEqualTo("a***@example.com");
    }

    @Test
    void genericPartialHonorsKeepStartAndKeepEndConfig() {
        assertThat(TypedMasker.mask(MaskStrategyType.GENERIC_PARTIAL,
                Map.of("keepStart", 2, "keepEnd", 2), "abcdefgh"))
                .isEqualTo("ab***gh");
    }

    @Test
    void genericPartialDefaultsToOneCharEachSide() {
        assertThat(TypedMasker.mask(MaskStrategyType.GENERIC_PARTIAL, Map.of(), "abcdef"))
                .isEqualTo("a***f");
    }

    @Test
    void invalidConfigFailsClosedToFullMask() {
        assertThat(TypedMasker.mask(MaskStrategyType.GENERIC_PARTIAL,
                Map.of("keepStart", "not-a-number"), "abcdef"))
                .isEqualTo("***");
        assertThat(TypedMasker.mask(MaskStrategyType.GENERIC_PARTIAL,
                Map.of("keepStart", -1), "abcdef"))
                .isEqualTo("***");
    }

    @Test
    void nullTypeOrValueFailsClosedToFullMask() {
        assertThat(TypedMasker.mask(null, Map.of(), "13812345678")).isEqualTo("***");
        assertThat(TypedMasker.mask(MaskStrategyType.PHONE, Map.of(), null)).isEqualTo("***");
        assertThat(TypedMasker.mask(MaskStrategyType.PHONE, Map.of(), "")).isEqualTo("***");
    }

    @Test
    void valueTooShortForRequestedEdgesFailsClosed() {
        // A 6-char phone cannot keep 3+4 edge chars without revealing all.
        assertThat(TypedMasker.mask(MaskStrategyType.PHONE, Map.of(), "123456"))
                .isEqualTo("***");
        // A 1-char name has nothing to keep.
        assertThat(TypedMasker.mask(MaskStrategyType.NAME, Map.of(), "x"))
                .isEqualTo("***");
    }

    @Test
    void idCardBankCardNameAndAddressFollowTypeRules() {
        assertThat(TypedMasker.mask(MaskStrategyType.ID_CARD, Map.of(), "110101199001011234"))
                .isEqualTo("1101***1234");
        assertThat(TypedMasker.mask(MaskStrategyType.BANK_CARD, Map.of(), "6222021234567890"))
                .isEqualTo("***7890");
        assertThat(TypedMasker.mask(MaskStrategyType.NAME, Map.of(), "zhangsan"))
                .isEqualTo("z***");
        assertThat(TypedMasker.mask(MaskStrategyType.ADDRESS, Map.of(), "chaoyang-district-beijing"))
                .isEqualTo("chaoya***");
    }
}
