package io.mosip.resident.interceptor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.apache.commons.codec.binary.Base64;
import org.hibernate.Interceptor;
import org.hibernate.type.Type;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.mosip.commons.khazana.config.LoggerConfiguration;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.resident.constant.ResidentErrorCode;
import io.mosip.resident.entity.ResidentSessionEntity;
import io.mosip.resident.entity.ResidentTransactionEntity;
import io.mosip.resident.exception.ResidentServiceException;
import io.mosip.resident.helper.ObjectStoreHelper;

/**
 * @author Neha Farheen
 *
 */
@Component
public class ResidentEntityInterceptor implements Interceptor, Serializable {
	/**
	The Constant serialVersionUID.
	 */
	@Serial
	private static final long serialVersionUID = 3428378823034671471L;

	private static final String INDIVIDUAL_ID = "individualId";

	private static final String IP_ADDRESS = "ipAddress";

	private static final String HOST = "host";

	@Autowired
	private transient ObjectStoreHelper objectStoreHelper;
	
	@Value("${mosip.resident.keymanager.application-name}")
	private String appId;
	
	@Value("${mosip.resident.keymanager.reference-id}")
	private String refId;

	/** The mosip logger. */
	private static final Logger logger = LoggerConfiguration.logConfig(ResidentEntityInterceptor.class);

	@Override
	public boolean onSave(Object entity, Object id, Object[] state, String[] propertyNames, Type[] types) {
		try {
			if (entity instanceof ResidentTransactionEntity) {
				List<String> propertyNamesList = Arrays.asList(propertyNames);
				encryptDataOnSave(id, state, propertyNamesList, types, (ResidentTransactionEntity) entity);
			} else if (entity instanceof ResidentSessionEntity) {
				List<String> propertyNamesList = Arrays.asList(propertyNames);
				encryptSessionDataOnSave(state, propertyNamesList, (ResidentSessionEntity) entity);
			}
		} catch (ResidentServiceException e) {
			logger.error(ResidentErrorCode.ENCRYPT_DECRYPT_ERROR.getErrorCode(),
					ResidentErrorCode.ENCRYPT_DECRYPT_ERROR.getErrorMessage(), e);
			throw new ResidentServiceException(ResidentErrorCode.ENCRYPT_DECRYPT_ERROR.getErrorCode(),
					ResidentErrorCode.ENCRYPT_DECRYPT_ERROR.getErrorMessage(), e);
		}
		return Interceptor.super.onSave(entity, id, state, propertyNames, types);
	}

	private <T extends ResidentTransactionEntity> void encryptDataOnSave(Object id, Object[] state,
			List<String> propertyNamesList, Type[] types, T uinEntity) throws ResidentServiceException {
		if (Objects.nonNull(uinEntity.getIndividualId())) {
			String idividualId = Base64.encodeBase64String(uinEntity.getIndividualId().getBytes());
			String encryptedData = objectStoreHelper.encryptDecryptData(idividualId, true, appId, refId);
			uinEntity.setIndividualId(encryptedData);
			int indexOfData = propertyNamesList.indexOf(INDIVIDUAL_ID);
			state[indexOfData] = encryptedData;
		}
	}

	/**
	 * Encrypts the session's identifying network attributes (client IP address and
	 * host) before they are persisted so that they are never stored in plaintext
	 * (ref: MOSIP-41105). Reversible keymanager encryption is used (same mechanism
	 * as individualId) so the original values can still be retrieved when genuinely
	 * required.
	 */
	private void encryptSessionDataOnSave(Object[] state, List<String> propertyNamesList,
			ResidentSessionEntity sessionEntity) throws ResidentServiceException {
		if (Objects.nonNull(sessionEntity.getIpAddress())) {
			String encryptedIpAddress = encryptSessionValue(sessionEntity.getIpAddress());
			sessionEntity.setIpAddress(encryptedIpAddress);
			updateState(state, propertyNamesList, IP_ADDRESS, encryptedIpAddress);
		}
		if (Objects.nonNull(sessionEntity.getHost())) {
			String encryptedHost = encryptSessionValue(sessionEntity.getHost());
			sessionEntity.setHost(encryptedHost);
			updateState(state, propertyNamesList, HOST, encryptedHost);
		}
	}

	private String encryptSessionValue(String value) throws ResidentServiceException {
		String encodedValue = Base64.encodeBase64String(value.getBytes());
		return objectStoreHelper.encryptDecryptData(encodedValue, true, appId, refId);
	}

	private void updateState(Object[] state, List<String> propertyNamesList, String propertyName, String value) {
		int indexOfData = propertyNamesList.indexOf(propertyName);
		if (indexOfData >= 0) {
			state[indexOfData] = value;
		}
	}
	
	@Override
	public boolean onLoad(Object entity, Object id, Object[] state, String[] propertyNames, Type[] types) {
		try {
			if (entity instanceof ResidentTransactionEntity) {
				List<String> propertyNamesList = Arrays.asList(propertyNames);
				int indexOfData = propertyNamesList.indexOf(INDIVIDUAL_ID);
				if (Objects.nonNull(state[indexOfData])) {
					decryptDataOnLoad(id, state, propertyNamesList, types, (ResidentTransactionEntity) entity);
				}
			} else if (entity instanceof ResidentSessionEntity) {
				List<String> propertyNamesList = Arrays.asList(propertyNames);
				decryptSessionDataOnLoad(state, propertyNamesList, (ResidentSessionEntity) entity);
			}
		} catch (ResidentServiceException e) {
			logger.error(ResidentErrorCode.ENCRYPT_DECRYPT_ERROR.getErrorCode(),
					ResidentErrorCode.ENCRYPT_DECRYPT_ERROR.getErrorMessage(), e);
			throw new ResidentServiceException(ResidentErrorCode.ENCRYPT_DECRYPT_ERROR.getErrorCode(),
					ResidentErrorCode.ENCRYPT_DECRYPT_ERROR.getErrorMessage(), e);
		}
		return Interceptor.super.onLoad(entity, id, state, propertyNames, types);
	}

	@Override
	public boolean onFlushDirty(Object entity, Object id, Object[] currentState, Object[] previousState, String[] propertyNames, Type[] types) {
		if(entity instanceof ResidentTransactionEntity) {
			List<String> propertyNamesList = Arrays.asList(propertyNames);
			encryptDataOnSave(id, currentState, propertyNamesList, types, (ResidentTransactionEntity) entity);
		} else if (entity instanceof ResidentSessionEntity) {
			List<String> propertyNamesList = Arrays.asList(propertyNames);
			encryptSessionDataOnSave(currentState, propertyNamesList, (ResidentSessionEntity) entity);
		}
		return Interceptor.super.onFlushDirty(entity, id, currentState, previousState, propertyNames, types);
	}

	private void decryptSessionDataOnLoad(Object[] state, List<String> propertyNamesList,
			ResidentSessionEntity sessionEntity) throws ResidentServiceException {
		int ipIndex = propertyNamesList.indexOf(IP_ADDRESS);
		if (ipIndex >= 0 && Objects.nonNull(state[ipIndex])) {
			String decryptedIpAddress = tryDecryption((String) state[ipIndex], IP_ADDRESS);
			sessionEntity.setIpAddress(decryptedIpAddress);
			state[ipIndex] = decryptedIpAddress;
		}
		int hostIndex = propertyNamesList.indexOf(HOST);
		if (hostIndex >= 0 && Objects.nonNull(state[hostIndex])) {
			String decryptedHost = tryDecryption((String) state[hostIndex], HOST);
			sessionEntity.setHost(decryptedHost);
			state[hostIndex] = decryptedHost;
		}
	}

	private <T extends ResidentTransactionEntity> void decryptDataOnLoad(Object id, Object[] state,
			List<String> propertyNamesList, Type[] types, T uinEntity) throws ResidentServiceException {
		int indexOfData = propertyNamesList.indexOf(INDIVIDUAL_ID);
		if (Objects.nonNull(state[indexOfData])) {
			String individualId = (String) state[indexOfData];
			String decodedIndividualId = tryDecryption(individualId, INDIVIDUAL_ID);
			uinEntity.setIndividualId(decodedIndividualId);
			state[indexOfData] = decodedIndividualId;
		}
	}

	private String tryDecryption(String data, String attributeName) {
		try {
			String decryptedData = objectStoreHelper.encryptDecryptData(data, false, appId, refId);
			String decodedIndividualId = new String(Base64.decodeBase64(decryptedData));
			return decodedIndividualId;
		} catch (ResidentServiceException e) {
			logger.debug(String.format("Unable to decrpt data in interceptor: %s", attributeName));
			return data;
		}
	}
}
