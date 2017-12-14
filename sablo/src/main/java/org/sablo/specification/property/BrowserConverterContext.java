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

import org.sablo.BaseWebObject;
import org.sablo.specification.WebObjectSpecification.PushToServerEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Context for data converters
 * @author gboros
 */
public class BrowserConverterContext extends WrappingContext implements IBrowserConverterContext
{

	protected static final Logger log = LoggerFactory.getLogger(BrowserConverterContext.class.getCanonicalName());

	/**
	 * Just to save CPU - for cases that don't have a web-object available but need a BrowserConverterContext. (return values of server side java service handlers, special messages, ...)
	 */
	public static final BrowserConverterContext NULL_WEB_OBJECT_WITH_NO_PUSH_TO_SERVER = new BrowserConverterContext(null, PushToServerEnum.reject);

	private final PushToServerEnum parentPropertyPushToServerValue;

	public BrowserConverterContext(BaseWebObject webObject, PushToServerEnum rootPropertyPushToServerValue)
	{
		super(webObject, null); //TODO get the property name somehow
		this.parentPropertyPushToServerValue = rootPropertyPushToServerValue;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ((parentPropertyPushToServerValue == null) ? 0 : parentPropertyPushToServerValue.hashCode());
		result = prime * result + ((webObject == null) ? 0 : webObject.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		BrowserConverterContext other = (BrowserConverterContext)obj;
		if (parentPropertyPushToServerValue == null)
		{
			if (other.parentPropertyPushToServerValue != null) return false;
		}
		else if (!parentPropertyPushToServerValue.equals(other.parentPropertyPushToServerValue)) return false;
		if (webObject == null)
		{
			if (other.webObject != null) return false;
		}
		else if (!webObject.equals(other.webObject)) return false;
		return true;
	}

	@Override
	public PushToServerEnum getPushToServerValue()
	{
		return parentPropertyPushToServerValue;
	}

	public static PushToServerEnum getPushToServerValue(IBrowserConverterContext context)
	{
		if (context == null)
		{
			log.warn("No IBrowserConverterContext present to get \"pushToServer\" value from. Will default to 'reject'. This is where it happened: ",
				new RuntimeException("Just for showing the execution stack"));
			return PushToServerEnum.reject; // should never happen (BrowserConverterContext should always be present); but just in case to avoid a possible NPE breaking more unrelated functionality
		}
		PushToServerEnum v = context.getPushToServerValue();
		if (v == null)
		{
			log.warn("No PushToServerEnum present in an IBrowserConverterContext instance. Will default to 'reject'. This is where it happened: ",
				new RuntimeException("Just for showing the execution stack"));
			return PushToServerEnum.reject; // should never happen (BrowserConverterContext should always be present and with pushToServer value set); but just in case to avoid a possible NPE breaking more unrelated functionality
		}

		return v;
	}
}
