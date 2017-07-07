package org.sablo;

import java.beans.PropertyChangeListener;

/**
 * Interface to be passed on to various methods of smarter property types in order to give access to slightly modified context.<br/><br/>
 * 
 * For example in case of a nested property you might want access to properties in the same custom object type, not just to root
 * properties in the BaseWebObject.
 *
 * @author emera
 * @author acostescu
 */
public interface IWebObjectContext extends IPropertyDescriptionProvider
{

	Object getProperty(String name);

	boolean setProperty(String propertyName, Object value);

	Object getRawPropertyValue(String name);

	BaseWebObject getUnderlyingWebObject();

	IWebObjectContext getParentContext();

	/**
	 * Listeners registered here will be triggered when the property changes by reference.
	 * If "propertyName" is null, then it will listen to all property changes. Otherwise it will listen for changes only on "propertyName".
	 */
	void addPropertyChangeListener(String propertyName, PropertyChangeListener listener);

	void removePropertyChangeListener(String propertyName, PropertyChangeListener listener);

}
