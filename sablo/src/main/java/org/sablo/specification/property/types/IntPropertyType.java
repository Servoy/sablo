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
package org.sablo.specification.property.types;


/**
 * @author jcompagner
 *
 */
public class IntPropertyType extends DefaultPropertyType<Integer> {

	public static final IntPropertyType INSTANCE = new IntPropertyType();
	
	private IntPropertyType() {
	}
	
	@Override
	public String getName() {
		return "int";
	}

	@Override
	public Integer defaultValue() {
		return Integer.valueOf(0);
	}
}
