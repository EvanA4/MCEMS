package net.mcems.client;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;

import net.fabricmc.api.ClientModInitializer;
import net.mcems.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
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

	private final Map<String, UUID> sessionsByPeer = new HashMap<>();

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
	private void handlePayload(NetworkPayloads.Payload payload) {
		try {
			if (payload instanceof NetworkPayloads.OfferPayload offerPayload) {
				HandshakeService.Offer offer = offerPayload.offer();
				String localId = localPlayerId();

				// Only accept offers addressed to this client.
				if (!localId.equals(offer.recipientId())) {
					return;
				}

				HandshakeService.AcceptedHandshake accepted =
						handshakeService.acceptOffer(offer);

				FabricNetworkHandler.sendToServer(
						NetworkPayloads.encodeResponse(accepted.response()));

				installSession(
						offer.senderId(),
						offer.sessionId(),
						accepted.sessionKey());
				Arrays.fill(accepted.sessionKey(), (byte) 0);

				ChatUi.showStatus("Secure session established with " + uuidToName(offer.senderId()) + ".");
				return;
			}

			if (payload instanceof NetworkPayloads.ResponsePayload responsePayload) {
				HandshakeService.Response response = responsePayload.response();

				// The response is addressed to the local initiator.
				if (!localPlayerId().equals(response.recipientId())) {
					return;
				}

				HandshakeService.EstablishedSession established =
						handshakeService.complete(response);

				installSession(
						established.peerId(),
						established.sessionId(),
						established.sessionKey());
				Arrays.fill(established.sessionKey(), (byte) 0);

				ChatUi.showStatus("Secure session established with " + uuidToName(established.peerId()) + ".");
				return;
			}

			if (payload instanceof NetworkPayloads.MessagePayload messagePayload) {
				handleMessage(messagePayload.envelope());
			}
		} catch (Exception e) {
			Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(
				Component.literal(e.toString())
			);

			LOGGER.error("Could not process secure-message payload", e);
			ChatUi.showError("Could not process secure-message payload.");
		}
	}

	private void startHandshake(String playerName) throws Exception {
		String peerId = nameToUUID(playerName);
		String localId = localPlayerId();

		HandshakeService.Offer offer =
				handshakeService.createOffer(localId, peerId);

		byte[] toSend = NetworkPayloads.encodeOffer(offer);
		FabricNetworkHandler.sendToServer(toSend);
	}

	private void sendPrivateMessage(String playerName, String message) throws Exception {
		String peerId = nameToUUID(playerName);
		if (message == null || message.isBlank()) {
			throw new IllegalArgumentException("Message must not be blank.");
		}

		UUID sessionId = sessionsByPeer.get(peerId);
		if (sessionId == null) {
			throw new IllegalStateException("No secure session with " + playerName + ".");
		}

		byte[] key = sessionManager.getKey(sessionId);
		if (key == null) {
			sessionsByPeer.remove(peerId);
			throw new IllegalStateException("Secure session has expired.");
		}

		try {
			long counter = sessionManager.nextOutgoingCounter(sessionId);
			byte[] plaintext = message.getBytes(StandardCharsets.UTF_8);
			byte[] aad = messageAad(
					ProtocolConstants.PROTOCOL_VERSION,
					localPlayerId(),
					peerId,
					sessionId,
					counter);

			MessageCipher.EncryptedPayload encrypted =
					messageCipher.encrypt(key, plaintext, aad);

			MessageEnvelope envelope = new MessageEnvelope(
					ProtocolConstants.PROTOCOL_VERSION,
					localPlayerId(),
					peerId,
					sessionId,
					counter,
					encrypted.nonce(),
					encrypted.ciphertext());

			FabricNetworkHandler.sendToServer(
					NetworkPayloads.encodeMessage(envelope));

			Arrays.fill(plaintext, (byte) 0);
			Arrays.fill(aad, (byte) 0);
		} finally {
			Arrays.fill(key, (byte) 0);
		}
	}

	private void handleMessage(MessageEnvelope envelope) throws Exception {
		String localId = localPlayerId();

		if (envelope.protocolVersion() != ProtocolConstants.PROTOCOL_VERSION
				|| !localId.equals(envelope.recipientId())) {
			return;
		}

		byte[] key = sessionManager.getKey(envelope.sessionId());
		if (key == null) {
			return;
		}

		byte[] plaintext = null;
		byte[] aad = messageAad(
				envelope.protocolVersion(),
				envelope.senderId(),
				envelope.recipientId(),
				envelope.sessionId(),
				envelope.messageCounter());

		try {
			// Authenticate before advancing the replay counter, so an invalid
			// ciphertext cannot consume a legitimate message counter.
			plaintext = messageCipher.decrypt(
					key, envelope.nonce(), envelope.ciphertext(), aad);

			if (!sessionManager.acceptIncoming(
					envelope.sessionId(),
					envelope.senderId(),
					envelope.messageCounter())) {
				return;
			}

			String message = StandardCharsets.UTF_8.newDecoder()
					.decode(ByteBuffer.wrap(plaintext))
					.toString();

			ChatUi.showPrivateMessage(uuidToName(envelope.senderId()), message);
		} finally {
			Arrays.fill(key, (byte) 0);
			Arrays.fill(aad, (byte) 0);
			if (plaintext != null) {
				Arrays.fill(plaintext, (byte) 0);
			}
		}
	}

	private void installSession(String peerId, UUID sessionId, byte[] key) {
		sessionManager.addSession(
				sessionId,
				peerId,
				key,
				System.currentTimeMillis() + 60 * 60 * 1000L);
		sessionsByPeer.put(peerId, sessionId);
	}

	private static byte[] messageAad(
			int version, String sender, String recipient, UUID sessionId, long counter) {
		byte[] senderBytes = sender.getBytes(StandardCharsets.UTF_8);
		byte[] recipientBytes = recipient.getBytes(StandardCharsets.UTF_8);

		return ByteBuffer.allocate(
						4 + 4 + senderBytes.length + 4 + recipientBytes.length + 16 + 8)
				.putInt(version)
				.putInt(senderBytes.length)
				.put(senderBytes)
				.putInt(recipientBytes.length)
				.put(recipientBytes)
				.putLong(sessionId.getMostSignificantBits())
				.putLong(sessionId.getLeastSignificantBits())
				.putLong(counter)
				.array();
	}

	private static String nameToUUID(String playerName) {
		var handler = Minecraft.getInstance().getConnection();

		if (handler == null) {
			throw new IllegalStateException("You must be connected to a server.");
		}

		return handler.getListedOnlinePlayers().stream()
				.map(entry -> entry.getProfile())
				.filter(profile -> profile.name().equalsIgnoreCase(playerName))
				.findFirst()
				.map(profile -> profile.id().toString())
				.orElseThrow(() -> new IllegalArgumentException(playerName + " is not online."));
	}

	private static String uuidToName(String uuid) {
		var handler = Minecraft.getInstance().getConnection();

		if (handler == null) {
			throw new IllegalStateException("You must be connected to a server.");
		}

		var playerId = UUID.fromString(uuid.trim());

		return handler.getListedOnlinePlayers().stream()
				.map(entry -> entry.getProfile())
				.filter(profile -> profile.id().equals(playerId))
				.findFirst()
				.map(profile -> profile.name())
				.orElseThrow(() -> new IllegalArgumentException("Cannot find player with UUID " + uuid + "."));
	}


	private static String localPlayerId() {
		var player = net.minecraft.client.Minecraft.getInstance().player;
		if (player == null) {
			throw new IllegalStateException("You must be in a world to use MCEMS.");
		}
		return player.getStringUUID();
	}

}
