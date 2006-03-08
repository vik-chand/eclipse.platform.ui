/*******************************************************************************
 * Copyright (c) 2005, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jface.internal.databinding.api.beans;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.Collection;

import org.eclipse.jface.internal.databinding.api.BindingException;
import org.eclipse.jface.internal.databinding.api.IDataBindingContext;
import org.eclipse.jface.internal.databinding.api.description.Property;
import org.eclipse.jface.internal.databinding.api.description.TableModelDescription;
import org.eclipse.jface.internal.databinding.api.factories.IObservableFactory;
import org.eclipse.jface.internal.databinding.api.observable.IObservable;
import org.eclipse.jface.internal.databinding.api.observable.list.IObservableList;
import org.eclipse.jface.internal.databinding.api.observable.set.IObservableSet;
import org.eclipse.jface.internal.databinding.api.observable.set.ListToSetAdapter;
import org.eclipse.jface.internal.databinding.nonapi.beans.JavaBeanObservableList;
import org.eclipse.jface.internal.databinding.nonapi.beans.JavaBeanObservableMultiMapping;
import org.eclipse.jface.internal.databinding.nonapi.beans.JavaBeanObservableValue;

/**
 * A factory for creating observable objects for properties of plain Java
 * objects with JavaBeans-style notification.
 * 
 * This factory supports the following description objects:
 * <ul>
 * <li>org.eclipse.jface.databinding.PropertyDescription:
 * <ul>
 * <li>Returns an observable value representing the specified value property of
 * the given object, if {@link Property#isCollectionProperty()} returns false</li>
 * <li>Returns an observable collection representing the specified collection
 * property of the given object, if {@link Property#isCollectionProperty()}
 * returns false</li>
 * </ul>
 * </li>
 * </ul>
 * 
 * @since 1.0
 * 
 */
final public class BeanObservableFactory implements IObservableFactory {

	public IObservable createObservable(IDataBindingContext bindingContext,
			Object description) {
		if (description instanceof Property) {
			Property propertyDescription = (Property) description;
			if (propertyDescription.getObject() != null) {
				Object object = propertyDescription.getObject();
				BeanInfo beanInfo;
				try {
					beanInfo = Introspector.getBeanInfo(object.getClass());
				} catch (IntrospectionException e) {
					// cannot introspect, give up
					return null;
				}
				PropertyDescriptor[] propertyDescriptors = beanInfo
						.getPropertyDescriptors();
				for (int i = 0; i < propertyDescriptors.length; i++) {
					PropertyDescriptor descriptor = propertyDescriptors[i];
					if (descriptor.getName().equals(
							propertyDescription.getPropertyID())) {
						if (descriptor.getPropertyType().isArray()
								|| Collection.class.isAssignableFrom(descriptor
										.getPropertyType())) {
							// If we are a collection them the type must be
							// explicitly specified in order to be edited.
							// There is no way to derive it by name matching
							// (because of different ways of plurals being made,
							// e.g. getFlies() and addFly(Fly aFly) or
							// getHooves() and addHoof(Hoof aHoof)
							Class elementType = descriptor.getPropertyType()
									.isArray() ? descriptor.getPropertyType()
									.getComponentType() : propertyDescription
									.getPropertyType();
							if (elementType == null) {
								// If we don't know the element type, use the
								// polymorphic converter
								// This should usually get us object to string,
								// but won't get us string
								// back to the object. At least this handles the
								// read-only case for
								// tables and handles Combos and Lists.
								elementType = Object.class;
							}
							return new JavaBeanObservableList(object, descriptor, elementType);
						}
						return new JavaBeanObservableValue(object, descriptor);
					}
				}
			}
			// else if (description instanceof ITree)
			// return new JavaBeanObservableTree((ITree) description);
			// else if (description instanceof TreeModelDescription) {
			// Object root = ((TreeModelDescription) description).getRoot();
			// if (root == null || !(root instanceof IObservable)
			// && !(root instanceof Property)) // TODO workaround until the
			// // context's factory is
			// // driven first
			// return new JavaBeanObservableTree(new JavaBeanTree(
			// (TreeModelDescription) description));
		} else if (description instanceof TableModelDescription) {
			TableModelDescription tableModelDescription = (TableModelDescription) description;
			IObservable collectionObservable = bindingContext
					.createObservable(tableModelDescription
							.getCollectionProperty());
			if (collectionObservable == null) {
				return null;
			}
			IObservableSet readableSet;
			if (collectionObservable instanceof IObservableSet) {
				readableSet = (IObservableSet) collectionObservable;
			} else if (collectionObservable instanceof IObservableList) {
				readableSet = new ListToSetAdapter(
						(IObservableList) collectionObservable);
			} else {
				throw new BindingException(
						"collection inside a TableModelDescription needs to be IReadableSet or IReadableList"); //$NON-NLS-1$
			}
			Object[] columnIDs = tableModelDescription.getColumnIDs();
			PropertyDescriptor[] propertyDescriptors = new PropertyDescriptor[columnIDs.length];
			Class elementType = (Class) readableSet.getElementType();
			for (int i = 0; i < columnIDs.length; i++) {
				propertyDescriptors[i] = getPropertyDescriptor(elementType, (String) columnIDs[i]);
			}			
			return new JavaBeanObservableMultiMapping(readableSet, propertyDescriptors);
		}
		return null;
	}

	private PropertyDescriptor getPropertyDescriptor(Class elementType, String propertyName) {
		BeanInfo beanInfo;
		try {
			beanInfo = Introspector.getBeanInfo(elementType);
		} catch (IntrospectionException ex) {
			throw new BindingException("Cannot introspect " + elementType, ex); //$NON-NLS-1$
		}
		PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();
		for (int i = 0; i < propertyDescriptors.length; i++) {
			if(propertyDescriptors[i].getName().equals(propertyName)) {
				return propertyDescriptors[i];
			}
		}
		return null;
	}
}
