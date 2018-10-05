/*
 * Copyright (C) 2018 Servoy BV
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

/**
 * This interaface is for property types to implemenet that wants to allow property pushes even if the system says
 * that the push is not allowed because of enable of visibilty protection.
 *
 * @author jcompagner
 * @since 8.4
 */
public interface IGranularProtectionChecker<T>
{
	boolean allowPush(Object data, T currentValue);
}
