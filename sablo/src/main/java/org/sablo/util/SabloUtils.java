/*
 * Copyright (C) 2017 Servoy BV
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

package org.sablo.util;

/**
 * Some basic utility methods.
 *
 * @author acostescu
 */
public class SabloUtils
{

	/**
	 * Helpful for referencing an AnnonymusClass instance from itself.<br/>
	 * For example:
	 * <code>
	 *     RecursiveAnnonymusClass<Consumer<Boolean>> rac = new RecursiveAnnonymusClass();
	 *     rac.me = (generateStackOverFlow) -> { if (generateStackOverFlow) rac.me(generateStackOverFlow); };
	 * </code>
	 *
	 * If you would not use "rac" above but try it directly via consumer = ... consumer you would get a compilation error as that needs to be final (can't be assigned like that and used in itself).
	 *
	 * @param <AnnonymusClass>
	 */
	public static class RecursiveAnnonymusClass<AnnonymusClass>
	{
		public AnnonymusClass me;
	}

	/**
	 * Compares two objects via .equals() no matter if they are null or not.
	 *
	 * @param left object
	 * @param right object
	 * @return true if they are equal, false otherwise.
	 */
	public static boolean safeEquals(Object left, Object right)
	{
		if (left == null)
		{
			return right == null;
		}
		return left.equals(right);
	}

}
