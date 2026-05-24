package nus.edu.u.framework.security.password;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nus.edu.u.common.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PasswordPolicyServiceTest {

    private PasswordPolicyService service;

    @BeforeEach
    void setUp() {
        service = new PasswordPolicyService();
    }

    // --- null / empty inputs -------------------------------------------------

    @Test
    void assertNotIdentity_nullPassword_noThrow() {
        assertThatCode(() -> service.assertNotIdentity(null, "alice", "alice@example.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void assertNotIdentity_emptyPassword_noThrow() {
        assertThatCode(() -> service.assertNotIdentity("", "alice", "alice@example.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void assertNotIdentity_nullUsernameAndEmail_noThrow() {
        assertThatCode(() -> service.assertNotIdentity("Zphyr-7Brave-Wolf!", null, null))
                .doesNotThrowAnyException();
    }

    @Test
    void assertNotIdentity_blankUsernameAndEmail_noThrow() {
        assertThatCode(() -> service.assertNotIdentity("Zphyr-7Brave-Wolf!", "", "   "))
                .doesNotThrowAnyException();
    }

    // --- username rules ------------------------------------------------------

    @Test
    void assertNotIdentity_passwordEqualsUsername_throws() {
        assertThatThrownBy(() -> service.assertNotIdentity("alice", "alice", "x@y.z"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("username");
    }

    @Test
    void assertNotIdentity_passwordContainsUsername_throws() {
        // "testuser" (8 chars) is above the 5-char substring threshold
        assertThatThrownBy(
                        () ->
                                service.assertNotIdentity(
                                        "TestUser2026!", "testuser", "test@example.com"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("username");
    }

    @Test
    void assertNotIdentity_passwordContainsShortUsername_noThrow() {
        // "bob" (3 chars) is below the substring threshold → only equality check fires
        // and "bobIsCool2026!" doesn't equal "bob"
        assertThatCode(
                        () ->
                                service.assertNotIdentity(
                                        "bobIsCool2026!", "bob", "someone@example.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void assertNotIdentity_caseInsensitive_throws() {
        // Password matches username when both lowercased
        assertThatThrownBy(() -> service.assertNotIdentity("ALICE", "alice", "x@y.z"))
                .isInstanceOf(ServiceException.class);
    }

    // --- email rules ---------------------------------------------------------

    @Test
    void assertNotIdentity_passwordEqualsFullEmail_throws() {
        assertThatThrownBy(
                        () ->
                                service.assertNotIdentity(
                                        "alice@example.com", "differentuser", "alice@example.com"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("email");
    }

    @Test
    void assertNotIdentity_passwordEqualsEmailLocalPart_throws() {
        // Local-part "alice" is 5 chars; equality fires regardless of length. The username
        // check doesn't fire (differentuser != alice), so the email branch throws.
        assertThatThrownBy(
                        () ->
                                service.assertNotIdentity(
                                        "alice", "differentuser", "alice@example.com"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("email");
    }

    @Test
    void assertNotIdentity_passwordContainsLongLocalPart_throws() {
        assertThatThrownBy(
                        () ->
                                service.assertNotIdentity(
                                        "developer2026!", "u", "developer@example.com"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("email");
    }

    @Test
    void assertNotIdentity_passwordContainsShortLocalPart_noThrow() {
        // local-part "test" (4 chars) is below threshold → contains check skipped
        // and password doesn't equal "test"
        assertThatCode(
                        () ->
                                service.assertNotIdentity(
                                        "Test1example@", "differentuser", "test@example.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void assertNotIdentity_passwordEqualsShortLocalPart_throws() {
        // local-part "bob" (3 chars) below threshold → contains check skipped
        // but equality still fires
        assertThatThrownBy(
                        () -> service.assertNotIdentity("bob", "differentuser", "bob@example.com"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("email");
    }

    // --- happy path ----------------------------------------------------------

    @Test
    void assertNotIdentity_unrelatedStrongPassword_noThrow() {
        assertThatCode(
                        () ->
                                service.assertNotIdentity(
                                        "Zphyr-7Brave-Wolf!",
                                        "alice",
                                        "alice@example.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void assertNotIdentity_emailWithoutAtSign_treatedAsLocalPart() {
        // No '@' → fullEmail and localPart are the same
        assertThatThrownBy(() -> service.assertNotIdentity("notanemail", "u", "notanemail"))
                .isInstanceOf(ServiceException.class);
    }
}
