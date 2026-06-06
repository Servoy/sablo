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

import java.util.List;

import org.sablo.specification.PropertyDescription;

/**
 * This list is able to do the SABLO wrap/unwrap operations that (Sablo) base objects usually do internally.
 * <br/><br/>
 * This is used when the property value is set from java side (in which case the property value
 * will be based on underlying Java List).
 *
 * @author acostescu
 */
public class WrapperList<ExternalT, BaseT> extends ConvertedList<ExternalT, BaseT> implements IWrappedBaseListProvider
{

	protected final IWrapperType<ExternalT, BaseT> type;
	protected final IWrappingContext dataConverterContext;
	protected final PropertyDescription pd;

	public WrapperList(List<ExternalT> external, IWrapperType<ExternalT, BaseT> type, PropertyDescription pd, IWrappingContext dataConverterContext,
		boolean flag) // this last arg is just to disambiguate the between constructors */
	{
		super();
		this.type = type;
		this.pd = pd;
		this.dataConverterContext = dataConverterContext;
		initFromExternal(external);
	}

	public WrapperList(List<BaseT> base, IWrapperType<ExternalT, BaseT> type, PropertyDescription pd, IWrappingContext dataConverterContext)
	{
		super(base);
		this.type = type;
		this.pd = pd;
		this.dataConverterContext = dataConverterContext;
	}

	@Override
	protected ExternalT convertFromBase(int index, BaseT value)
	{
		return type.unwrap(value);
	}

	@Override
	protected BaseT convertToBase(int previousIndexOfThisElement, ExternalT value)
	{
		return type.wrap(value, previousIndexOfThisElement < 0 ? null : baseList.get(previousIndexOfThisElement), pd, dataConverterContext);
	}

	public List<BaseT> getWrappedBaseList()
	{
		return getBaseList();
	}

}
