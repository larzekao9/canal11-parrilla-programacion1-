package canal11.mail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MailParserTest {

    @Test
    void extraeFromYSubjectSimples() {
        MailMessage m = MailParser.parse("1", "From: Juan Perez <Juan@Mail.com>\r\nSubject: LISPER[\"*\"]\r\n\r\ncuerpo ignorado\r\n");
        assertEquals("juan@mail.com", m.from());
        assertEquals("LISPER[\"*\"]", m.subject());
    }

    @Test
    void subjectDobladoEnVariasLineas() {
        MailMessage m = MailParser.parse("1", "From: a@b.com\nSubject: INSPRG[\"Noticiero\",\n \"x\"]\n\nbody");
        assertEquals("INSPRG[\"Noticiero\", \"x\"]", m.subject());
    }

    @Test
    void subjectCodificadoBase64YQuotedPrintable() {
        // "Edición" en UTF-8
        MailMessage b64 = MailParser.parse("1", "From: a@b.com\nSubject: =?UTF-8?B?RWRpY2nDs24=?=\n\n");
        assertEquals("Edición", b64.subject());
        MailMessage qp = MailParser.parse("1", "From: a@b.com\nSubject: =?UTF-8?Q?Edici=C3=B3n_1?=\n\n");
        assertEquals("Edición 1", qp.subject());
    }

    @Test
    void fromSinAngulares() {
        assertEquals("x@y.com", MailParser.parse("1", "From: X@Y.com\nSubject: HELP[]\n\n").from());
    }
}
