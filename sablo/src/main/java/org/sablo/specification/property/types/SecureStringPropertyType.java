/*
 * Copyright (C) 2024 Servoy BV
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sablo.specification.property.types;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import org.json.JSONException;
import org.json.JSONWriter;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.property.IBrowserConverterContext;
import org.sablo.specification.property.IPropertyConverterForBrowser;
import org.sablo.util.ValueReference;
import org.sablo.websocket.utils.JSONUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jcompgner
 * @since 2024.3.1
 *
 */
@SuppressWarnings("nls")
public class SecureStringPropertyType extends DefaultPropertyType<String> implements IPropertyConverterForBrowser<String>
{
	private static final Logger log = LoggerFactory.getLogger(SecureStringPropertyType.class.getCanonicalName());

	private static final String CRYPT_METHOD = "AES/GCM/NoPadding";

	private final SecretKey secretString;
	private final GCMParameterSpec gcmParameterSpec;

	public static final SecureStringPropertyType INSTANCE = new SecureStringPropertyType();
	public static final String TYPE_NAME = "securestring";

	protected SecureStringPropertyType()
	{
		super(true);
		SecretKey key = null;
		try
		{
			KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
			keyGenerator.init(256);
			key = keyGenerator.generateKey();
		}
		catch (NoSuchAlgorithmException e)
		{
			log.warn("SecureStringProperty type can't initialize encryption for its properties, fallback to plain text", e);
		}
		secretString = key;

		byte[] iv = new byte[16];
		new SecureRandom().nextBytes(iv);
		gcmParameterSpec = new GCMParameterSpec(128, iv);
	}

	public String getName()
	{
		return TYPE_NAME;
	}

	@Override
	public String fromJSON(Object newJSONValue, String previousSabloValue, PropertyDescription propertyDescription, IBrowserConverterContext context,
		ValueReference<Boolean> returnValueAdjustedIncommingValue)
	{
		if (newJSONValue == null) return null;
		return decryptString(newJSONValue.toString());
	}

	@Override
	public JSONWriter toJSON(JSONWriter writer, String key, String sabloValue, PropertyDescription propertyDescription,
		IBrowserConverterContext dataConverterContext) throws JSONException
	{
		JSONUtils.addKeyIfPresent(writer, key);
		writer.value(encryptString(sabloValue));
		return writer;
	}


	private String encryptString(String value)
	{
		if (value == null || secretString == null) return value;
		try
		{
			Cipher cipher = Cipher.getInstance(CRYPT_METHOD);
			cipher.init(Cipher.ENCRYPT_MODE, secretString, gcmParameterSpec);
			byte[] bytes = cipher.doFinal(value.getBytes());
			return java.util.Base64.getEncoder().encodeToString(bytes);
		}
		catch (Exception e)
		{
			log.error("Error encrypting string " + value, e);
			return null; // this shouldn't really happen, not much todo then just return null (value doesn't make much sense)
		}
	}

	private String decryptString(String value)
	{
		if (value == null || secretString == null) return value;
		try
		{
			Cipher cipher = Cipher.getInstance(CRYPT_METHOD);
			cipher.init(Cipher.DECRYPT_MODE, secretString, gcmParameterSpec);
			return new String(cipher.doFinal(java.util.Base64.getDecoder().decode(value)));
		}
		catch (Exception e)
		{
			log.error("Error decrypting string " + value, e);
			return null; // cant return value because that would mean that the incoming is accepted as is.
		}
	}

}
