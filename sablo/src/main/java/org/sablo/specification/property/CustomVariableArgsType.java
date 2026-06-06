/*
 * Copyright (C) 2015 Servoy BV
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

package org.sablo.specification.property;

import java.util.List;

import org.json.JSONException;
import org.json.JSONWriter;
import org.sablo.specification.PropertyDescription;
import org.sablo.util.ValueReference;
import org.sablo.websocket.utils.JSONUtils;

/**
 *  Type for what in spec files you see like 'mytype...'. Can only be used currently for arguments in case of Rhino (solution or comp.
 *  server side scripting) calls to client-side apis.
 *
 *  This type actually impersonates a client-side custom array - so that we don't need a client side impl. for it as well.
 *
 * @author lvostinar
 *
 */
public class CustomVariableArgsType extends CustomJSONPropertyType<List< ? >>
	implements IAdjustablePropertyType<List< ? >>, IConvertedPropertyType<List< ? >>, IPropertyWithClientSideConversions<List< ? >>
{

	public static final String TYPE_NAME = "var_args"; //$NON-NLS-1$

	public CustomVariableArgsType(PropertyDescription definition)
	{
		super(definition.getType().getName(), definition);
	}

	public String getGenericName()
	{
		return TYPE_NAME;
	}

	@Override
	public List< ? > fromJSON(Object newJSONValue, List< ? > previousSabloValue, PropertyDescription pd, IBrowserConverterContext dataConverterContext,
		ValueReference<Boolean> returnValueAdjustedIncommingValue)
	{
		// not needed for now; this type is currently used just for passing a variable number of parameters to client (component and service apis)
		return null;
	}

	@Override
	public JSONWriter toJSON(JSONWriter writer, String key, List< ? > sabloValue, PropertyDescription pd, IBrowserConverterContext dataConverterContext)
		throws JSONException
	{
		JSONUtils.addKeyIfPresent(writer, key);

		writer.object(); // curly brace :)
		CustomJSONArrayType.sendFullArrayValueExceptCurlyBraces(writer, 1, (List< ? >)sabloValue, JSONUtils.FullValueToJSONConverter.INSTANCE,
			dataConverterContext, getCustomJSONTypeDefinition());
		writer.endObject();

		return writer;
	}

	@Override
	public boolean writeClientSideTypeName(JSONWriter w, String keyToAddTo, PropertyDescription pd)
	{
		CustomJSONArrayType.writeCustomArrayClientSideType(w, keyToAddTo, getCustomJSONTypeDefinition());
		return true;
	}

}
