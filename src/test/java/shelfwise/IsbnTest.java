package shelfwise;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import shelfwise.util.Isbn;

class IsbnTest {
    @Test void validIsbn10() { assertTrue(Isbn.isValid10("0439023483")); }
    @Test void validIsbn10WithX() { assertTrue(Isbn.isValid10("080442957X")); }
    @Test void rejectsWrongCheckDigit() { assertFalse(Isbn.isValid10("0439023484")); }
    @Test void rejectsLeadingZeroLost() { assertFalse(Isbn.isValid10("439023483")); }
    @Test void convertsTo13() { assertEquals("9780439023481", Isbn.to13("0439023483")); }
    @Test void converted13IsValid() { assertTrue(Isbn.isValid13(Isbn.to13("080442957X"))); }
    @Test void ignoresHyphens() { assertTrue(Isbn.isValid("978-0-439-02348-1")); }
    @Test void to13RejectsInvalid() { assertThrows(IllegalArgumentException.class, () -> Isbn.to13("123")); }
}
