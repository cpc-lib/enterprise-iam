package com.enterprise.iam.auth.application.port.out;

/** Password verification and PHC creation boundary used by credential mutations. */
public interface PasswordHasher extends PasswordVerifier {

    String encode(char[] rawPassword);
}
