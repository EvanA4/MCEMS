package net.mcems.client;

import java.security.GeneralSecurityException;
import net.fabricmc.api.ClientModInitializer;
import net.mcems.HandshakeService;
import net.mcems.HkdfService;
import net.mcems.KeyAgreementService;
import net.mcems.KeyManager;
import net.mcems.MessageCipher;
import net.mcems.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MCEMSClientClient implements ClientModInitializer {
	private static final Logger LOGGER =
			LoggerFactory.getLogger("mcems-client");

	// Keep these for the lifetime of the client; don't recreate them per command.
	private KeyManager keyManager;
	private HandshakeService handshakeService;
	private SessionManager sessionManager;
	private MessageCipher messageCipher;

	@Override
	public void onInitializeClient() {
		try {
			keyManager = new KeyManager();
			KeyAgreementService keyAgreement = new KeyAgreementService();
			HkdfService hkdf = new HkdfService();

			handshakeService =
					new HandshakeService(keyManager, keyAgreement, hkdf);
			sessionManager = new SessionManager();
			messageCipher = new MessageCipher();

			FabricNetworkHandler.registerPayloadTypes();
			FabricNetworkHandler.registerClientReceiver(
					this::handlePayload,
					error -> LOGGER.error("Secure-message payload failed", error));

			new ChatCommandHandler(
					this::startHandshake,
					this::sendPrivateMessage
			).register();
		} catch (GeneralSecurityException e) {
			LOGGER.error("Could not initialize secure messaging", e);
		}
	}

	private void handlePayload(net.mcems.NetworkPayloads.Payload payload) {
		// Dispatch OfferPayload, ResponsePayload, and MessagePayload here.
		// Validate the sender, recipient, session, and message counter before use.
	}

	private void startHandshake(String playerName) throws Exception {
		// Resolve the server-side player ID, create an offer, encode it,
		// then send it with FabricNetworkHandler.sendToServer(...).
	}

	private void sendPrivateMessage(String playerName, String message)
			throws Exception {
		// Find the established session, get its key/counter, encrypt and encode,
		// then send the payload.
	}
}
