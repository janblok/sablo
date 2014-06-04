package org.sablo.specification.property;


public interface IWrapperType<T,W> extends IClassPropertyType<W>
{
	T unwrap(W value);
	
	W wrap(T value, W previousValue, Object converterContext);
}