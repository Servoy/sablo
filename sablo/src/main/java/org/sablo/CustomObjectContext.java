package org.sablo;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.sablo.specification.PropertyDescription;
import org.sablo.specification.property.ChangeAwareMap;
import org.sablo.specification.property.IPropertyType;
import org.sablo.util.SabloUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a base web object extension for custom object types - it gives property types using it access to properties
 * on the same level of custom object as well as to properties in parent objects.
 *
 * @see IWebObjectContext
 *
 * @author emera
 * @author acostescu
 *
 * @param <SabloT>
 * @param <SabloWT>
 */
public class CustomObjectContext<SabloT, SabloWT> implements IWebObjectContext
{

	private final PropertyDescription customJSONTypeDefinition;
	private final IWebObjectContext parentWebObjectContext;
	private PropertyChangeSupport propertyChangeSupport;

	private ChangeAwareMap<SabloT, SabloWT> customObjectMap;
	private Map<String, SabloT> tmpMap; // as customObjectMap might not be set yet and still setProperty can get called, we keep those set values in this tmpMap until customObjectMap is set (can happen if a whole custom object value is set at once from Rhino and in the rhino-to-sablo conversion a sub-property calls a setProperty on the context...)

	private static final Logger log = LoggerFactory.getLogger(CustomObjectContext.class.getCanonicalName());

	public CustomObjectContext(PropertyDescription customJSONTypeDefinition, IWebObjectContext underlyingWebObject)
	{
		this.customJSONTypeDefinition = customJSONTypeDefinition;
		this.parentWebObjectContext = underlyingWebObject;
	}

	@Override
	public SabloT getProperty(String propertyName)
	{
		SabloT v;

		PropertyDescription customPD = customJSONTypeDefinition.getProperty(propertyName);
		if (customPD != null)
		{
			if (customObjectMap != null)
			{
				// usual case
				v = customObjectMap.get(propertyName);
			}
			else if (tmpMap != null)
			{
				// customObjectMap was not yet set; see if this value is present in a temp map, and get it from there if
				// temp map is present (can happen if a whole custom object value is set at once from Rhino and in the rhino-to-sablo conversion a sub-property calls a setProperty on the context...)
				v = tmpMap.get(propertyName);
			}
			else v = null;
		}
		else v = (SabloT)parentWebObjectContext.getProperty(propertyName);

		return v;
	}

	/**
	 * @return true if this property was changed.
	 */
	@Override
	public boolean setProperty(String propertyName, Object value)
	{
		PropertyDescription customPD = customJSONTypeDefinition.getProperty(propertyName);
		if (customPD != null)
		{
			if (customObjectMap != null)
			{
				// set it here; normal situation
				SabloT previousVal = customObjectMap.put(propertyName, (SabloT)value);
				return SabloUtils.safeEquals(previousVal, value);
			}
			else
			{
				// customObjectMap was not yet set; keep this value in a temp map, and add it to customObjectMap later when that one
				// is set (can happen if a whole custom object value is set at once from Rhino and in the rhino-to-sablo conversion a sub-property calls a setProperty on the context...)
				if (tmpMap == null) tmpMap = new HashMap<>();
				SabloT previousVal = tmpMap.put(propertyName, (SabloT)value);
				return SabloUtils.safeEquals(previousVal, value);
			}
		}
		else return parentWebObjectContext.setProperty(propertyName, value);
	}

	@Override
	public BaseWebObject getUnderlyingWebObject()
	{
		return parentWebObjectContext instanceof BaseWebObject ? (BaseWebObject)parentWebObjectContext : parentWebObjectContext.getUnderlyingWebObject();
	}

	@Override
	public IWebObjectContext getParentContext()
	{
		return parentWebObjectContext;
	}

	public void setPropertyValues(ChangeAwareMap<SabloT, SabloWT> map)
	{
		if (customObjectMap != null) throw new RuntimeException("customObjectMap was already set; it can't be set again: " + this);
		this.customObjectMap = map;

		// if any properties were set using setProperty in this context before we had customObjectMap, they are stored in tmpMap; not put them where they were meant to be!
		if (tmpMap != null)
		{
			for (Entry<String, SabloT> e : tmpMap.entrySet())
			{
				customObjectMap.put(e.getKey(), e.getValue());
			}
			tmpMap.clear();
			tmpMap = null;
		}
	}

	@Override
	public PropertyDescription getPropertyDescription(String name)
	{
		return customJSONTypeDefinition.getProperty(name) != null ? customJSONTypeDefinition.getProperty(name)
			: parentWebObjectContext.getPropertyDescription(name);
	}

	@Override
	public SabloWT getRawPropertyValue(String name)
	{
		PropertyDescription customPD = customJSONTypeDefinition.getProperty(name);
		if (customPD != null)
		{
			if (customObjectMap != null)
			{
				if (customObjectMap.getBaseMap().containsKey(name)) return (SabloWT)customObjectMap.getBaseMap().get(name);
			}
			else if (tmpMap != null && tmpMap.containsKey(name))
			{
				// tmpMap is not wrap-aware; it doesn't know what the raw value would be for the given sablo value...
				log.error("Trying to get raw property value '" + name +
					"'from tmpMap (before customObjectMap was set); that is not supported at-the-moment. C: " + this, new RuntimeException());
			}

			return getDefaultFromPD(customPD);
		}

		return (SabloWT)parentWebObjectContext.getRawPropertyValue(name);
	}

	protected SabloWT getDefaultFromPD(PropertyDescription customPD)
	{
		return (SabloWT)getUnderlyingWebObject().getDefaultFromPD(customPD); // ng services use sablo default impl. for getDefaultFromPD while ng components use a more specialized impl. - taking into account formElement conversions
	}

	@Override
	public Collection<PropertyDescription> getProperties(IPropertyType< ? > type)
	{
		List<PropertyDescription> properties = new ArrayList<PropertyDescription>();
		properties.addAll(customJSONTypeDefinition.getProperties(type));
		properties.addAll(parentWebObjectContext.getProperties(type));
		return properties;
	}

	public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener)
	{
		if (propertyChangeSupport == null) propertyChangeSupport = new PropertyChangeSupport(this);

		if (propertyName == null)
		{
			// should be registered to all properties

			// all from parent
			parentWebObjectContext.addPropertyChangeListener(null, listener);

			// all from current custom object
			propertyChangeSupport.addPropertyChangeListener(listener);
		}
		else
		{
			// should be registered to only one property
			PropertyDescription customPD = customJSONTypeDefinition.getProperty(propertyName);
			if (customPD == null)
			{
				// this property must be somewhere in parent(s)
				parentWebObjectContext.addPropertyChangeListener(propertyName, listener);
			}
			else
			{
				// this property if from this custom object
				propertyChangeSupport.addPropertyChangeListener(propertyName, listener);
			}
		}
	}

	public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener)
	{
		if (propertyName == null)
		{
			if (propertyChangeSupport != null) propertyChangeSupport.removePropertyChangeListener(listener);
			parentWebObjectContext.removePropertyChangeListener(null, listener);
		}
		else
		{
			PropertyDescription customPD = customJSONTypeDefinition.getProperty(propertyName);
			if (customPD == null) parentWebObjectContext.removePropertyChangeListener(propertyName, listener);
			else if (propertyChangeSupport != null) propertyChangeSupport.removePropertyChangeListener(propertyName, listener);
		}
	}

	public void triggerPropertyChange(String propertyName, Object oldValue, Object newValue)
	{
		if (propertyChangeSupport != null) propertyChangeSupport.firePropertyChange(propertyName, oldValue, newValue);
	}


	public void dispose()
	{
		propertyChangeSupport = null;
	}

	@Override
	public String toString()
	{
		return "Custom object web object extension (" + customJSONTypeDefinition + ") of " + parentWebObjectContext;
	}

}
