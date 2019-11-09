package org.mitre.jose.jwk;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;

/**
 * @autho northbyte
 */
public class OctetKeyPairKeyMaker {

    public static OctetKeyPair make(Curve crv, KeyUse keyUse, String kid) {
        try {
            return new OctetKeyPairGenerator(crv)
                .keyUse(keyUse)
                .keyID(kid)
                .generate();
        } catch (JOSEException e) {
            e.printStackTrace();
            return null;
        }
    }

}
