package com.enterprise.iam.common.security.jwt;

import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.interfaces.ECKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;

/** Exact secp256r1 parameter comparison; field size alone is not a curve identity. */
public final class P256Keys {

    private static final ECParameterSpec P_256 = loadP256();

    private P256Keys() {
    }

    public static boolean isP256(ECKey key) {
        if (key == null || key.getParams() == null) {
            return false;
        }
        ECParameterSpec actual = key.getParams();
        return P_256.getCurve().equals(actual.getCurve())
                && P_256.getGenerator().equals(actual.getGenerator())
                && P_256.getOrder().equals(actual.getOrder())
                && P_256.getCofactor() == actual.getCofactor();
    }

    private static ECParameterSpec loadP256() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
