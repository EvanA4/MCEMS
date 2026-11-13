package net.mcems;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Objects;
import javax.crypto.KeyAgreement;

public final class KeyAgreementService {

    public byte[] deriveSharedSecret(KeyPair ownKeyPair, PublicKey peerPublicKey)
            throws GeneralSecurityException {
        Objects.requireNonNull(ownKeyPair, "ownKeyPair");
        return deriveSharedSecret(ownKeyPair.getPrivate(), peerPublicKey);
    }

    public byte[] deriveSharedSecret(PrivateKey ownPrivateKey,
                                     PublicKey peerPublicKey)
            throws GeneralSecurityException {
        Objects.requireNonNull(ownPrivateKey, "ownPrivateKey");
        Objects.requireNonNull(peerPublicKey, "peerPublicKey");

        KeyAgreement agreement =
                KeyAgreement.getInstance(CryptoConfig.KEY_AGREEMENT_ALGORITHM);
        agreement.init(ownPrivateKey);
        agreement.doPhase(peerPublicKey, true);

        return agreement.generateSecret();
    }
}
