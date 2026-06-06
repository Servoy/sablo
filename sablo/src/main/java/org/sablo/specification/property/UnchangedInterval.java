/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2018 Servoy BV

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

package org.sablo.specification.property;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.sablo.specification.property.ArrayGranularChangeKeeper.IntervalSequenceModifier;

/**
 * An unchanged (or partially unchanged - see {@link #UnchangedInterval(int, int, String)}) interval. It keeps the new indexes and initial indexes. See {@link ViewportChangeKeeper} for more information.<br/><br/>
 *
 * It can correct it's indexes, split into two/three UnchangedIntervals or remove itself from the ViewportChangeKeeper when an insert, delete or update operation
 * are processed.<br/>
 *
 * @author acostescu
 * @see ViewportChangeKeeper
 */
@SuppressWarnings("nls")
public class UnchangedInterval
{

	private int initialStartIndex;
	private int initialEndIndex;
	private int newStartIndex;
	private int newEndIndex;

	// if it has partial changes/only some columns changed
	private Set<String> changedColumnNames;

	public UnchangedInterval(int initialStartIndex, int initialEndIndex, int newStartIndex, int newEndIndex)
	{
		this.initialStartIndex = initialStartIndex;
		this.initialEndIndex = initialEndIndex;
		this.newStartIndex = newStartIndex;
		this.newEndIndex = newEndIndex;
	}

	/**
	 * Create a new partially changed interval (some parts of data in that row are changed, some are not).<br/>
	 * More columns could be added to "changedColumnName" later.<br/><br/>
	 *
	 * See {@link #UnchangedInterval(int, int, int, int)} for other params.
	 * @param cellNames the names of columns that actually did change (the rest remaining unchanged).
	 */
	public UnchangedInterval(int initialStartIndex, int initialEndIndex, int newStartIndex, int newEndIndex, Set<String> changedColumns)
	{
		this(initialStartIndex, initialEndIndex, newStartIndex, newEndIndex);

		if (changedColumns != null)
		{
			changedColumnNames = new HashSet<>();
			changedColumnNames.addAll(changedColumns);
		}
	}

	public boolean isPartiallyChanged()
	{
		return changedColumnNames != null;
	}

	/**
	 * Applies the given operation to this interval; this can result in a change of interval indexes, a delete of the interval or a split into two intervals.
	 *
	 * @param operation the viewport operation (insert/delete/update) to apply
	 * @param intervalSequenceModifier can be used to delete the interval or split in two (add another new interval after current)
	 *
	 * @return the number of unchanged indexes remaining from this interval after the operation was applied.
	 */
	public int applyOperation(ArrayOperation operation, IntervalSequenceModifier intervalSequenceModifier)
	{
		int remainingUnchangedIndexes;

		switch (operation.type)
		{
			case ArrayOperation.CHANGE :
				remainingUnchangedIndexes = applyChange(operation, intervalSequenceModifier);
				break;
			case ArrayOperation.INSERT :
				remainingUnchangedIndexes = applyInsert(operation, intervalSequenceModifier);
				break;
			case ArrayOperation.DELETE :
				remainingUnchangedIndexes = applyDelete(operation, intervalSequenceModifier);
				break;
			default :
				throw new IllegalArgumentException("ArrayOperation type is not one of the supported values: " + operation.type);
		}

		return remainingUnchangedIndexes;
	}

	protected int applyChange(ArrayOperation changeOperation, IntervalSequenceModifier intervalSequenceModifier)
	{
		// on partially unchanged intervals, a partial update (that overlaps) will need to merge column names on the intersection
		// inserts/deletes/full updates on a partially unchanged interval (that overlaps) will behave the same as they do for normal UnchangedInterval

		// on normal UnchangedInterval, intersections with "changeOperation" will need to split or shrink the current UnchangedInterval
		// if change intersects current interval then we must restrict the unchanged indexes, maybe even split the interval into multiple ones

		// we could also merge with previous/following interval in case the new (1-3) generated intervals below in this method are partially changed intervals that are
		// identical to previous/following (adjacent intervals) but we don't do that here as it would complicate the code below too much - it already has enough calculations;
		// the merging of any such similar partially changed intervals is done directly at the end when appendEquivalentArrayOperations(...) is called below;
		// it is easier to do it then as well

		int intersectionStart = Math.max(changeOperation.startIndex, newStartIndex);
		int intersectionEnd = Math.min(changeOperation.endIndex, newEndIndex);
		int intersectionSize = intersectionEnd - intersectionStart + 1;
		int unchangedIndexes = 0;

		if (intersectionSize > 0)
		{
			UnchangedInterval newInterval;
			boolean isPartialChange = (changeOperation.cellNames != null);
			boolean needsToBeSplit = true;

			if (intersectionStart == newStartIndex)
			{
				intervalSequenceModifier.discardCurrentInterval();

				// first part of this unchanged interval is gone; or whole interval is gone
				if (isPartialChange)
				{
					// if it's a partial change add it before current unchanged interval

					// if this is also a partially changed interval, merge the columnNames
					Set<String> newIntervalChangedColumns;
					if (changedColumnNames != null)
					{
						newIntervalChangedColumns = new HashSet<>(changedColumnNames);
						newIntervalChangedColumns.addAll(changeOperation.cellNames);

						// it doesn't need to be split if no new columns are changed after the new operation on intersection
						needsToBeSplit = (changedColumnNames.size() < newIntervalChangedColumns.size());
					}
					else newIntervalChangedColumns = changeOperation.cellNames;

					if (needsToBeSplit)
					{
						// it needs to be split into two intervals; create new one before >this<
						newInterval = new UnchangedInterval(initialStartIndex, initialStartIndex + intersectionSize - 1, newStartIndex,
							newStartIndex + intersectionSize - 1, newIntervalChangedColumns);
						intervalSequenceModifier.addOneMoreIntervalAfter(newInterval);
						unchangedIndexes += newInterval.getUnchangedIndexesCount();
					} // else a partial update happened on part or all of >this< partially unchanged interval but with no new columns; nothing to do, just re-add >this< interval below with no adjustments
				}

				if (needsToBeSplit)
				{
					// update indexes of >this< interval and add it back (it was discarded above) if it still exists
					newStartIndex += intersectionSize;
					initialStartIndex += intersectionSize;
				}

				// re-add >this< interval
				if (newEndIndex >= newStartIndex)
				{
					intervalSequenceModifier.addOneMoreIntervalAfter(this);
					unchangedIndexes += getUnchangedIndexesCount();
				}
			}
			else if (intersectionEnd == newEndIndex)
			{
				// last part of this unchanged interval is gone (changed) (but first part remains, otherwise it would have entered previous if)
				if (isPartialChange)
				{
					// if it's a partial change add it after current unchanged interval

					// if this is also a partially changed interval, merge the columnNames
					Set<String> newIntervalChangedColumns;
					if (changedColumnNames != null)
					{
						newIntervalChangedColumns = new HashSet<>(changedColumnNames);
						newIntervalChangedColumns.addAll(changeOperation.cellNames);

						// it doesn't need to be split if no new columns are changed after the new operation on intersection
						needsToBeSplit = (changedColumnNames.size() < newIntervalChangedColumns.size());
					}
					else newIntervalChangedColumns = changeOperation.cellNames;

					if (needsToBeSplit)
					{
						newInterval = new UnchangedInterval(initialEndIndex - intersectionSize + 1, initialEndIndex, intersectionStart,
							intersectionStart + intersectionSize - 1, newIntervalChangedColumns);
						intervalSequenceModifier.addOneMoreIntervalAfter(newInterval);
						unchangedIndexes += newInterval.getUnchangedIndexesCount();
					}
				}

				// update indexes of this interval and remove it if it no longer exists
				if (needsToBeSplit)
				{
					newEndIndex -= intersectionSize;
					initialEndIndex -= intersectionSize;
				}
				unchangedIndexes += getUnchangedIndexesCount();
			}
			else
			{
				// update happened in the middle of this interval; we have to split into multiple unchanged intervals (except for if it has partial changes that are not new)
				int oldInitialEndIndex = initialEndIndex;
				int oldNewEndIndex = newEndIndex;

				if (isPartialChange)
				{
					// if it's a partial change add it after current unchanged interval

					// if this is also a partially changed interval, merge the columnNames
					Set<String> newIntervalChangedColumns;
					if (changedColumnNames != null)
					{
						newIntervalChangedColumns = new HashSet<>(changedColumnNames);
						newIntervalChangedColumns.addAll(changeOperation.cellNames);

						// it doesn't need to be split if no new columns are changed after the new operation on intersection
						needsToBeSplit = (changedColumnNames.size() < newIntervalChangedColumns.size());
					}
					else newIntervalChangedColumns = changeOperation.cellNames;

					if (needsToBeSplit)
					{
						newInterval = new UnchangedInterval(initialEndIndex - (newEndIndex - intersectionStart),
							initialEndIndex - (newEndIndex - intersectionStart + 1) + intersectionSize,
							intersectionStart, intersectionStart + intersectionSize - 1, newIntervalChangedColumns);
						intervalSequenceModifier.addOneMoreIntervalAfter(newInterval);
						unchangedIndexes += newInterval.getUnchangedIndexesCount();
					}
				}

				if (needsToBeSplit)
				{
					// update indexes of this interval to match the first resulting interval (the one before intersection)
					// for example intersection [5 -> 7], end index = 10, initialEndIndex 8 => initialEndIndex must decrease to 2
					initialEndIndex -= newEndIndex - intersectionStart + 1;
					newEndIndex = intersectionStart - 1;

					// create the new unchanged interval for after the intersection
					newInterval = new UnchangedInterval(initialEndIndex + intersectionSize + 1, oldInitialEndIndex, intersectionEnd + 1, oldNewEndIndex,
						changedColumnNames);
					intervalSequenceModifier.addOneMoreIntervalAfter(newInterval);
					unchangedIndexes += newInterval.getUnchangedIndexesCount();
				}
				unchangedIndexes += getUnchangedIndexesCount();
			}
		}
		else
		{
			// else it does not affect at all this unchanged interval
			unchangedIndexes += getUnchangedIndexesCount();
		}

		return unchangedIndexes;
	}

	protected int applyInsert(ArrayOperation insertOperation, IntervalSequenceModifier intervalSequenceModifier)
	{
		// insert can happen before, in the middle of or at the end of this unchanged interval
		int unchangedIndexes;

		if (insertOperation.startIndex <= newStartIndex)
		{
			// insert happened before; this interval just needs shifting
			int insertSize = insertOperation.endIndex - insertOperation.startIndex + 1;
			newStartIndex += insertSize;
			newEndIndex += insertSize;

			unchangedIndexes = getUnchangedIndexesCount();
		}
		else if (insertOperation.startIndex <= newEndIndex)
		{
			// the insert splits current interval
			int oldNewEndIndex = newEndIndex;
			int oldInitialEndIndex = initialEndIndex;

			int insertSize = insertOperation.endIndex - insertOperation.startIndex + 1;
			newEndIndex = insertOperation.startIndex - 1;
			initialEndIndex = initialStartIndex + newEndIndex - newStartIndex;
			unchangedIndexes = getUnchangedIndexesCount();

			UnchangedInterval newInterval = new UnchangedInterval(initialEndIndex + 1, oldInitialEndIndex, insertOperation.endIndex + 1,
				oldNewEndIndex + insertSize, changedColumnNames);
			intervalSequenceModifier.addOneMoreIntervalAfter(newInterval);
			unchangedIndexes += newInterval.getUnchangedIndexesCount();
		}
		else
		{
			// else it does not affect at all this unchanged interval
			unchangedIndexes = getUnchangedIndexesCount();
		}

		return unchangedIndexes;
	}

	protected int applyDelete(ArrayOperation deleteOperation, IntervalSequenceModifier intervalSequenceModifier)
	{
		// delete can happen before, around, in the middle of or at the end of this unchanged interval
		int intersectionStart = Math.max(deleteOperation.startIndex, newStartIndex);
		int intersectionEnd = Math.min(deleteOperation.endIndex, newEndIndex);
		int intersectionSize = intersectionEnd - intersectionStart + 1;
		int unchangedIndexes;

		if (deleteOperation.endIndex < newStartIndex)
		{
			// delete happened before; this interval just needs shifting
			int deleteSize = deleteOperation.endIndex - deleteOperation.startIndex + 1;
			newStartIndex -= deleteSize;
			newEndIndex -= deleteSize;

			unchangedIndexes = getUnchangedIndexesCount();
		}
		else if (intersectionSize > 0)
		{
			// the delete deletes something from this interval; delete and interval overlap
			if (intersectionStart == newStartIndex)
			{
				// first part of interval was deleted (or full interval was deleted); adjust indexes and remove interval if necessary
				newEndIndex -= intersectionEnd - deleteOperation.startIndex + 1;
				newStartIndex = deleteOperation.startIndex;
				initialStartIndex += intersectionSize;
				if (newEndIndex < newStartIndex)
				{
					intervalSequenceModifier.discardCurrentInterval();
					unchangedIndexes = 0;
				}
				else unchangedIndexes = getUnchangedIndexesCount();
			}
			else if (intersectionEnd == newEndIndex)
			{
				// last part of interval was deleted; adjust indexes; the whole interval will not be deleted here because then it would have been treated in the if above
				newEndIndex -= intersectionSize;
				initialEndIndex -= intersectionSize;
				unchangedIndexes = getUnchangedIndexesCount();
			}
			else
			{
				int oldInitialEndIndex = initialEndIndex;
				int oldNewEndIndex = newEndIndex;

				// delete is somewhere inside the interval, so this interval needs to be split in two
				newEndIndex = intersectionStart - 1;
				initialEndIndex = initialStartIndex + newEndIndex - newStartIndex;
				unchangedIndexes = getUnchangedIndexesCount();

				UnchangedInterval newInterval = new UnchangedInterval(initialEndIndex + intersectionSize + 1, oldInitialEndIndex, intersectionStart,
					oldNewEndIndex - intersectionSize, changedColumnNames);
				intervalSequenceModifier.addOneMoreIntervalAfter(newInterval);
				unchangedIndexes += newInterval.getUnchangedIndexesCount();
			}
		}
		else
		{
			// else it does not affect at all this unchanged interval
			unchangedIndexes = getUnchangedIndexesCount();
		}

		return unchangedIndexes;
	}

	/**
	 * Generates an equivalent set of viewport operations (if any is needed) for this 'unchanged' interval.
	 *
	 * @param equivalentSequenceOfOperations this call will add (if needed) to the equivalentSequenceOfOperations list what is needed to treat the indexes
	 * from this interval (+ this interval in case of {@link PartiallyUnchangedInterval}s).
	 */
	public void appendEquivalentArrayOperations(List<ArrayOperation> equivalentSequenceOfOperations)
	{
		if (isPartiallyChanged())
		{
			ArrayOperation previouslyGeneratedEquivalentOp = equivalentSequenceOfOperations.size() > 0
				? equivalentSequenceOfOperations.get(equivalentSequenceOfOperations.size() - 1) : null;

			if (previouslyGeneratedEquivalentOp != null && previouslyGeneratedEquivalentOp.type == ArrayOperation.CHANGE &&
				previouslyGeneratedEquivalentOp.cellNames != null &&
				changedColumnNames.size() == previouslyGeneratedEquivalentOp.cellNames.size() &&
				changedColumnNames.containsAll(previouslyGeneratedEquivalentOp.cellNames) && getNewStart() == previouslyGeneratedEquivalentOp.endIndex + 1)
			{
				// previous is also a partial change; they have also the same column names and are one-after-the-other (unchanged intervals can make this happen
				// if there is a sequence of ops that adds changed columns to a part of the viewport, then to another consecutive part and end up being the same columns in the end)

				// so it is really a change on multiple indexes with the same columns on each index; we just need to correct the end index of previous op
				equivalentSequenceOfOperations.remove(equivalentSequenceOfOperations.size() - 1);
				equivalentSequenceOfOperations.add(new ArrayOperation(previouslyGeneratedEquivalentOp.startIndex,
					getNewEnd(), previouslyGeneratedEquivalentOp.type, previouslyGeneratedEquivalentOp.cellNames));
			}
			else equivalentSequenceOfOperations.add(new ArrayOperation(getNewStart(), getNewEnd(), ArrayOperation.CHANGE, changedColumnNames));
		} // else nothing to add here; this is just for partially changed intervals; completely unchanged intervals generate no changes of course
	}

	public int getInitialStart()
	{
		return initialStartIndex;
	}

	public int getInitialEnd()
	{
		return initialEndIndex;
	}

	public int getNewStart()
	{
		return newStartIndex;
	}

	public int getNewEnd()
	{
		return newEndIndex;
	}

	public int getUnchangedIndexesCount()
	{
		if (isPartiallyChanged())
		{
			return 0; // this interval is actually partially changed!
		}
		else return initialEndIndex - initialStartIndex + 1;
	}

	@Override
	public String toString()
	{
		return (isPartiallyChanged() ? "Partially-UnchangedInterval [changedColumnNames=" + changedColumnNames + ", " + " initialStartIndex= "
			: "UnchangedInterval [initialStartIndex=") + initialStartIndex + ", initialEndIndex=" + initialEndIndex + ", newStartIndex=" + newStartIndex +
			", newEndIndex=" + newEndIndex + "]";
	}

}
