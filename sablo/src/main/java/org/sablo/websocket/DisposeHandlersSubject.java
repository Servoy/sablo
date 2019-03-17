/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2019 Servoy BV

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU Affero General Public License as published by the Free
 Software Foundation; either version 3 of the License, or (at your option) any
 later version.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License along
 with this program; if not, see http://www.gnu.org/licenses or write to the Free
 Software Foundation,Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301
 */
package org.sablo.websocket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author rgansevles
 */
public class DisposeHandlersSubject
{
	private static final Logger log = LoggerFactory.getLogger(DisposeHandlersSubject.class.getCanonicalName());

	private final List<Disposehandler> handlers = Collections.synchronizedList(new ArrayList<Disposehandler>());

	public void addDisposehandler(Disposehandler handler)
	{
		if (!handlers.contains(handler))
		{
			handlers.add(handler);
		}
	}

	public void removeDisposehandler(Disposehandler handler)
	{
		handlers.remove(handler);
	}

	public void clear()
	{
		handlers.clear();
	}

	public void callHandlers()
	{
		for (Object element : handlers.toArray())
		{
			try
			{
				((Disposehandler)element).disposed();
			}
			catch (Throwable e)
			{
				log.error("Error in dispose handler call", e);
			}
		}
	}
}
