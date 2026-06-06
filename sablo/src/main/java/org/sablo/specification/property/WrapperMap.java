/*
 * Copyright (C) 2014 Servoy BV
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

import java.util.Map;

import org.sablo.specification.PropertyDescription;

/**
 * This map is able to do the SABLO wrap/unwrap operations that (Sablo) base objects usually do internally.
 * <br/><br/>
 * This is used when the property value is set from java side (in which case the property value
 * will be based on underlying Java Map).
 *
 * @author acostescu
 */
//TODO these ET and WT are improper - as for object type they can represent multiple types (a different set for each child key), but they help to avoid some bugs at compile-time
public class WrapperMap<ExternalT, BaseT> extends ConvertedMap<ExternalT, BaseT> implements IWrappedBaseMapProvider
{

	protected final Map<String, IWrapperType<ExternalT, BaseT>> types;
	protected final IWrappingContext dataConverterContext;
	protected final PropertyDescription pd;

	public WrapperMap(Map<String, ExternalT> external, Map<String, IWrapperType<ExternalT, BaseT>> types, PropertyDescription pd,
		IWrappingContext dataConverterContext, boolean dummyFlag) // this last arg is just to disambiguate the between constructors */
	{
		super();
		this.pd = pd;
		this.types = types;
		this.dataConverterContext = dataConverterContext;
		initFromExternal(external);
	}

	public WrapperMap(Map<String, BaseT> base, Map<String, IWrapperType<ExternalT, BaseT>> types, PropertyDescription pd, IWrappingContext dataConverterContext)
	{
		super(base);
		this.pd = pd;
		this.types = types;
		this.dataConverterContext = dataConverterContext;
	}

	@Override
	protected ExternalT convertFromBase(String forKey, BaseT value)
	{
		IWrapperType<ExternalT, BaseT> wt = types.get(forKey);
		return wt != null ? wt.unwrap(value) : (ExternalT)value;
	}

	@Override
	protected BaseT convertToBase(String key, boolean ignoreOldValue, ExternalT value)
	{
		IWrapperType<ExternalT, BaseT> wt = types.get(key);
		return wt != null ? wt.wrap(value, key == null ? null : baseMap.get(key), pd, dataConverterContext) : (BaseT)value;
	}

	public Map<String, BaseT> getWrappedBaseMap()
	{
		return getBaseMap();
	}

}
