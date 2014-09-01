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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.sablo.BaseWebObject;
import org.sablo.IChangeListener;

/**
 * This list is able to do handle/keep track browser <-> server granular updates
 *
 * @author acostescu
 */
public class ChangeAwareList<ET, WT> implements List<ET>, ISmartPropertyValue
{

	// TODO this class should keep a kind of pks to avoid a scenario where server and browser get modified at the same time
	// and a granular update ends up doing incorrect modifications (as it's being applied in wrong place); for now we just drop
	// the browser changes when this happens through the 'version' mechanism
	private int version;

	protected IPropertyType<ET> type;
	protected IDataConverterContext dataConverterContext;
	protected List<ET> baseList;

	protected List<IndexChangeListener> changeHandlers = new ArrayList<>();

	// TODO in order to have fine-grained add/remove operations as well in the future we would need a list of change operations that can be add/remove/change instead
	protected IChangeListener changeMonitor;
	protected BaseWebObject component;

	protected Set<Integer> changedIndexes = new HashSet<Integer>();
	protected boolean allChanged;


	public ChangeAwareList(List<ET> baseList, IPropertyType<ET> type, IDataConverterContext dataConverterContext)
	{
		this(baseList, type, dataConverterContext, 1);
	}

	public ChangeAwareList(List<ET> baseList, IPropertyType<ET> type, IDataConverterContext dataConverterContext, int initialVersion)
	{
		this.type = type;
		this.dataConverterContext = dataConverterContext;
		this.baseList = baseList;
		this.version = initialVersion;
	}

	/**
	 * You should not change the contents of the returned Set.
	 */
	public Set<Integer> getChangedIndexes()
	{
		return changedIndexes;
	}

	public boolean haveAddRemoveOperationsHappened()
	{
		return allChanged;
	}

	public void clearChanges()
	{
		allChanged = false;
		changedIndexes.clear();
	}

	public List<ET> getBaseList()
	{
		return baseList;
	}

	public List<WT> getWrappedBaseList()
	{
		List<WT> wrappedBaseList;
		if (baseList instanceof WrapperList< ? , ? >)
		{
			wrappedBaseList = ((WrapperList<ET, WT>)baseList).getBaseList();
		}
		else
		{
			wrappedBaseList = (List<WT>)baseList; // ET == WT in this case; no wrapping
		}
		return wrappedBaseList;
	}

	protected int increaseContentVersion()
	{
		return ++version;
	}

	protected int getListContentVersion()
	{
		return version;
	}

	private void markElementChanged(int i)
	{
		if (changedIndexes.add(Integer.valueOf(i))) changeMonitor.valueChanged();
	}

	protected void markAllChanged()
	{
		boolean alreadyCh = allChanged;
		allChanged = true;
		if (!alreadyCh) changeMonitor.valueChanged();
	}

	protected void attachToBaseObjectIfNeeded(int i, WT el, boolean insert)
	{
		if (changeMonitor != null) attachToBaseObject(i, el, insert);
	}

	@Override
	public void attachToBaseObject(final IChangeListener changeMonitor, BaseWebObject component)
	{
		this.changeMonitor = changeMonitor;
		this.component = component;

		List<WT> wrappedBaseList = getWrappedBaseList();
		int i = 0;
		for (WT el : wrappedBaseList)
		{
			attachToBaseObject(i, el, false);
			i++;
		}
	}

	// called whenever a new element was added or inserted into the array
	protected void attachToBaseObject(final int i, WT el, boolean insert)
	{
		if (el instanceof ISmartPropertyValue)
		{
			ChangeAwareList<ET, WT>.IndexChangeListener changeHandler = new IndexChangeListener(i);
			changeHandlers.add(changeHandler);
			((ISmartPropertyValue)el).attachToBaseObject(changeHandler, component);
		}

		if (insert)
		{
			// an insert happened in array; update change handler indexes if needed
			Iterator<IndexChangeListener> it = changeHandlers.iterator();
			while (it.hasNext())
			{
				IndexChangeListener ch = it.next();
				if (ch.attachedToIdx >= i) ch.attachedToIdx++;
			}
		}
	}

	protected class IndexChangeListener implements IChangeListener
	{

		private int attachedToIdx;

		public IndexChangeListener(int attachedToIdx)
		{
			this.attachedToIdx = attachedToIdx;
		}

		@Override
		public void valueChanged()
		{
			markElementChanged(attachedToIdx);
		}

	}

	@Override
	public void detach()
	{
		List<WT> wrappedBaseList = getWrappedBaseList();
		int i = 0;
		for (WT el : wrappedBaseList)
		{
			detach(i, el, false);
			i++;
		}
	}

	protected void detach(int idx, WT el, boolean remove)
	{
		if (el instanceof ISmartPropertyValue)
		{
			((ISmartPropertyValue)el).detach();
			Iterator<IndexChangeListener> it = changeHandlers.iterator();
			while (it.hasNext())
			{
				IndexChangeListener ch = it.next();
				if (ch.attachedToIdx == idx) it.remove();
				else if (remove && ch.attachedToIdx > idx) ch.attachedToIdx--;
			}
		}
		else if (remove)
		{
			// other change handler indexes might need to be updated
			Iterator<IndexChangeListener> it = changeHandlers.iterator();
			while (it.hasNext())
			{
				IndexChangeListener ch = it.next();
				if (ch.attachedToIdx > idx) ch.attachedToIdx--;
			}
		}
	}

	protected void detachIfNeeded(int idx, WT el, boolean remove)
	{
		if (changeMonitor != null) detach(idx, el, remove);
	}

	@Override
	public int size()
	{
		return baseList.size();
	}

	@Override
	public boolean isEmpty()
	{
		return baseList.isEmpty();
	}

	@Override
	public boolean contains(Object o)
	{
		return baseList.contains(o);
	}

	@Override
	public Iterator<ET> iterator()
	{
		final Iterator<ET> it = baseList.iterator();
		return new Iterator<ET>()
		{

			int i = -1;

			@Override
			public boolean hasNext()
			{
				return it.hasNext();
			}

			@Override
			public ET next()
			{
				i++;
				return it.next();
			}

			@Override
			public void remove()
			{
				it.remove();
				detachIfNeeded(i, getWrappedBaseList().get(i), true);
				i--;
				markAllChanged();
			}
		};
	}

	@Override
	public Object[] toArray()
	{
		return baseList.toArray();
	}

	@Override
	public <T> T[] toArray(T[] a)
	{
		return baseList.toArray(a);
	}

	@Override
	public boolean add(ET e)
	{
		boolean tmp = baseList.add(e);
		attachToBaseObjectIfNeeded(baseList.size() - 1, getWrappedBaseList().get(baseList.size() - 1), false);
		markAllChanged();
		return tmp;
	}

	@Override
	public boolean remove(Object o)
	{
		int idx = baseList.indexOf(o);
		if (idx >= 0)
		{
			WT oldWrappedValue = getWrappedBaseList().get(idx);
			baseList.remove(idx);
			detachIfNeeded(idx, oldWrappedValue, true);

			markAllChanged();
			return true;
		}
		return false;
	}

	@Override
	public boolean containsAll(Collection< ? > c)
	{
		return baseList.containsAll(c);
	}

	@Override
	public boolean addAll(Collection< ? extends ET> c)
	{
		if (c.size() > 0)
		{
			for (ET el : c)
				add(el);
			return true;
		}
		return false;
	}

	@Override
	public boolean addAll(int index, Collection< ? extends ET> c)
	{
		if (c.size() > 0)
		{
			int j = index;
			for (ET el : c)
				add(j++, el);
			return true;
		}
		return false;
	}

	@Override
	public boolean removeAll(Collection< ? > c)
	{
		boolean changed = false;
		for (Object o : c)
			changed = changed || remove(o);

		return changed;
	}

	@Override
	public boolean retainAll(Collection< ? > c)
	{
		int i;
		boolean changed = false;
		for (i = baseList.size() - 1; i >= 0; i--)
		{
			if (!c.contains(get(i)))
			{
				remove(i);
				changed = true;
			}
		}
		return changed;
	}

	@Override
	public void clear()
	{
		int size = baseList.size();
		for (int i = size; i >= 0; i--)
			remove(i);
		if (size > 0) markAllChanged();
	}

	@Override
	public ET get(int index)
	{
		return baseList.get(index);
	}

	@Override
	public ET set(int index, ET element)
	{
		WT oldWV = getWrappedBaseList().get(index);
		ET tmp = baseList.set(index, element);
		detachIfNeeded(index, oldWV, false);
		attachToBaseObjectIfNeeded(index, getWrappedBaseList().get(index), false);
		markElementChanged(index);
		return tmp;
	}

	@Override
	public void add(int index, ET element)
	{
		baseList.add(index, element);
		attachToBaseObjectIfNeeded(index, getWrappedBaseList().get(index), true);
		markAllChanged();
	}

	@Override
	public ET remove(int index)
	{
		WT oldWV = getWrappedBaseList().get(index);
		ET tmp = baseList.remove(index);
		detachIfNeeded(index, oldWV, true);
		markAllChanged();
		return tmp;
	}

	@Override
	public int indexOf(Object o)
	{
		return baseList.indexOf(o);
	}

	@Override
	public int lastIndexOf(Object o)
	{
		return baseList.lastIndexOf(o);
	}

	@Override
	public ListIterator<ET> listIterator()
	{
		return new ChangeAwareListIterator<ET>(baseList.listIterator());
	}

	@Override
	public ListIterator<ET> listIterator(int index)
	{
		return new ChangeAwareListIterator<ET>(baseList.listIterator(index));
	}

	@Override
	public List<ET> subList(int fromIndex, int toIndex)
	{
		return baseList.subList(fromIndex, toIndex);
	}

	protected class ChangeAwareListIterator<ET> implements ListIterator<ET>
	{

		protected final ListIterator<ET> it;

		public ChangeAwareListIterator(ListIterator<ET> it)
		{
			this.it = it;
		}

		@Override
		public boolean hasNext()
		{
			return it.hasNext();
		}

		@Override
		public ET next()
		{
			return it.next();
		}

		@Override
		public boolean hasPrevious()
		{
			return it.hasPrevious();
		}

		@Override
		public ET previous()
		{
			return it.previous();
		}

		@Override
		public int nextIndex()
		{
			return it.nextIndex();
		}

		@Override
		public int previousIndex()
		{
			return it.previousIndex();
		}

		@Override
		public void remove()
		{
			int i = it.previousIndex() + 1;
			it.remove();
			detachIfNeeded(i, getWrappedBaseList().get(i), true);
			markAllChanged();
		}

		@Override
		public void set(ET e)
		{
			int i = it.previousIndex() + 1;
			WT oldWV = getWrappedBaseList().get(i);
			it.set(e);
			detachIfNeeded(i, oldWV, false);
			attachToBaseObjectIfNeeded(i, getWrappedBaseList().get(i), false);
			markElementChanged(i);
		}

		@Override
		public void add(ET e)
		{
			int i = it.nextIndex();
			it.add(e);
			attachToBaseObjectIfNeeded(i, getWrappedBaseList().get(i), true);
			markAllChanged();
		}
	}

}
