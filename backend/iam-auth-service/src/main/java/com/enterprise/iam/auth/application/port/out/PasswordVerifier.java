package com.enterprise.iam.auth.application.port.out;

public interface PasswordVerifier {

    boolean verify(char[] rawPassword, String passwordPhc);

    /** Executes the same expensive password function without disclosing identity existence. */
    boolean verifyAgainstDummy(char[] rawPassword);
}
