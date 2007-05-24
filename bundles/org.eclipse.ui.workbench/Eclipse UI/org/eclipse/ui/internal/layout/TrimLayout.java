/*******************************************************************************
 * Copyright (c) 2004, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ui.internal.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.Geometry;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CBanner;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Layout;
import org.eclipse.ui.IWorkbenchPreferenceConstants;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.TrimDragPreferences;

/**
 * Lays out the children of a Composite. One control occupies the center of the
 * composite, and any number of controls may be attached to the top, bottom, and
 * sides. This is a common layout for application windows, which typically have
 * a central work area and a number of small widgets (toolbars, status lines,
 * etc.) attached to the edge.
 * 
 * <p>
 * Unlike most other SWT layouts, this layout does not require layout data to be
 * attached to each child control. Instead, member functions on the Layout are
 * used to control the positioning of controls within the layout.
 * </p>
 * 
 * <p>
 * The interface to this layout is intended to easily support drag-and-drop.
 * Trim widgets can be added, removed, or inserted between other existing
 * widgets and the layout will adjust accordingly. If one side of the layout
 * contains no trim widgets, the central area will expand to reclaim the unused
 * space.
 * </p>
 * 
 * <p>
 * This layout must be told about every widget that it is supposed to arrange.
 * If the composite contains additional widgets, they will not be moved by the
 * layout and may be arranged through other means.
 * </p>
 * 
 * @since 3.0
 */
public class TrimLayout extends Layout implements ICachingLayout, ITrimManager {

	/**
	 * Trim area ID.
	 */
	public static final Integer TOP_ID = new Integer(TOP);

	/**
	 * Trim area ID.
	 */
	public static final Integer BOTTOM_ID = new Integer(BOTTOM);

	/**
	 * Trim area ID.
	 */
	public static final Integer LEFT_ID = new Integer(LEFT);

	/**
	 * Trim area ID.
	 */
	public static final Integer RIGHT_ID = new Integer(RIGHT);

	/**
	 * Trim area ID.
	 */
	public static final Integer NONTRIM_ID = new Integer(NONTRIM);

	/**
	 * IDs for the current trim areas we support.
	 */
	private static final int[] TRIM_ID_INFO = { LEFT, RIGHT, TOP, BOTTOM };

	private SizeCache centerArea = new SizeCache();

	/**
	 * Map of TrimAreas by IDs.
	 */
	private Map fTrimArea = new HashMap();

	/**
	 * Map of TrimDescriptors by IDs.
	 */
	private Map fTrimDescriptors = new HashMap();

	private int marginWidth;

	private int marginHeight;

	private int topSpacing;

	private int bottomSpacing;

	private int leftSpacing;

	private int rightSpacing;

	private int spacing = 3;
	
	private boolean trimLocked;

	private HashMap preferredLocationMap = new HashMap();

	/**
	 * Creates a new (initially empty) trim layout.
	 */
	public TrimLayout() {
		// Determine whether or not the trim is 'locked'
		final IPreferenceStore store = PlatformUI.getPreferenceStore();
        trimLocked = store.getBoolean(IWorkbenchPreferenceConstants.LOCK_TRIM);
		
		createTrimArea(TOP_ID, TOP_ID.toString(), SWT.DEFAULT, SWT.TOP);
		createTrimArea(BOTTOM_ID, BOTTOM_ID.toString(), SWT.DEFAULT, SWT.BOTTOM);
		createTrimArea(LEFT_ID, LEFT_ID.toString(), SWT.DEFAULT, SWT.LEFT);
		createTrimArea(RIGHT_ID, RIGHT_ID.toString(), SWT.DEFAULT, SWT.RIGHT);
	}

	private void createTrimArea(Integer id, String displayName, int trimSize,
			int trimMods) {
		TrimArea top = new TrimArea(id.intValue(), displayName);
		top.setTrimSize(trimSize);
		top.setControlModifiers(trimMods);
		fTrimArea.put(id, top);
	}

	/**
	 * Sets the empty space surrounding the center area. This whitespace is
	 * located between the trim and the central widget.
	 * 
	 * @param left
	 *            pixel width
	 * @param right
	 *            pixel width
	 * @param top
	 *            pixel width
	 * @param bottom
	 *            pixel width
	 */
	public void setSpacing(int left, int right, int top, int bottom) {
		leftSpacing = left;
		rightSpacing = right;
		topSpacing = top;
		bottomSpacing = bottom;
	}

	/**
	 * Sets the empty space around the outside of the layout. This whitespace is
	 * located outside the trim widgets.
	 * 
	 * @param marginWidth
	 * @param marginHeight
	 */
	public void setMargins(int marginWidth, int marginHeight) {
		this.marginWidth = marginWidth;
		this.marginHeight = marginHeight;
	}

	/**
	 * Sets the trimSize (pixels) for the given side of the layout. If
	 * SWT.DEFAULT, then the trim size will be computed from child controls.
	 * 
	 * @param areaId
	 *            the area ID
	 * @param size
	 *            in pixels
	 * @see #getAreaIds()
	 */
	public void setTrimSize(int areaId, int size) {
		TrimArea area = (TrimArea) fTrimArea.get(new Integer(areaId));
		if (area != null) {
			area.setTrimSize(size);
		}
	}

	/**
	 * Returns the location of the given trim control. For example, returns
	 * SWT.LEFT if the control is docked on the left, SWT.RIGHT if docked on the
	 * right, etc. Returns SWT.DEFAULT if the given control is not a trim
	 * control.
	 * 
	 * @param trimControl
	 *            control to query
	 * @return The area ID of this control. If the control is not part of our
	 *         trim, return SWT.DEFAULT.
	 * @see #getAreaIds()
	 */
	public int getTrimAreaId(Control trimControl) {
		TrimDescriptor desc = findTrimDescription(trimControl);
		if (desc != null) {
			return desc.getAreaId();
		}
		return SWT.DEFAULT;
	}

	/**
	 * @param control
	 *            new window trim to be added
	 * @param areaId
	 *            the area ID
	 * @see #getAreaIds()
	 * @deprecated
	 */
	public void addTrim(IWindowTrim control, int areaId) {
		addTrim(areaId, control, null);
	}

	/**
	 * 
	 * @param trim
	 *            new window trim to be added
	 * @param areaId
	 *            the area ID
	 * @param beforeMe
	 *            if null, the control will be inserted as the last trim widget
	 *            on this side of the layout. Otherwise, the control will be
	 *            inserted before the given widget.
	 * @see #getAreaIds()
	 * @deprecated
	 */
	public void addTrim(IWindowTrim trim, int areaId, IWindowTrim beforeMe) {
		addTrim(areaId, trim, beforeMe);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.internal.layout.ITrimManager#addTrim(int,
	 *      org.eclipse.ui.internal.IWindowTrim)
	 */
	public void addTrim(int areaId, IWindowTrim trim) {
		// If we're adding trim to the same side that it's
		// already on then don't change its order
		IWindowTrim insertBefore = null;
		List trimDescs = getAreaTrim(areaId);
		for (Iterator trimIter = trimDescs.iterator(); trimIter.hasNext();) {
			IWindowTrim curTrim = (IWindowTrim) trimIter.next();
			if (curTrim.getId().equals(trim.getId())) {
				if (trimIter.hasNext()) {
					insertBefore = (IWindowTrim) trimIter.next();
				}
			}
		}
		
		addTrim(areaId, trim, insertBefore);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.internal.layout.ITrimManager#addTrim(int,
	 *      org.eclipse.ui.internal.IWindowTrim,
	 *      org.eclipse.ui.internal.IWindowTrim)
	 */
	public void addTrim(int areaId, IWindowTrim trim, IWindowTrim beforeMe) {
		TrimArea area = (TrimArea) fTrimArea.get(new Integer(areaId));
		if (area == null) {
			return;
		}

		// remove the trim from the current layout
		removeTrim(trim);

		// Create a new trim descriptor for the new area...
		TrimDescriptor desc = new TrimDescriptor(trim, areaId);

		// If the trim can be relocated then add a docking handle
		boolean isAlreadyAHandle = trim instanceof TrimToolBarBase;
		if (!trimLocked && trim.getValidSides() != SWT.NONE && !isAlreadyAHandle) {
			// Create a 'docking' handle to allow dragging the trim
			Composite dockingHandle = new TrimCommonUIHandle(this, trim, areaId);
			desc.setDockingCache(new SizeCache(dockingHandle));
		}

		// Add the trim control
		SizeCache cache = new SizeCache(trim.getControl());
		trim.getControl().setLayoutData(trim);
		desc.setCache(cache);
		
		// Add a dispose listener so we can clean up if the Client disposes the trim
		trim.getControl().addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				Control control = (Control) e.widget;
				if (control.getLayoutData() instanceof IWindowTrim) {
					IWindowTrim trim = (IWindowTrim) control.getLayoutData();
					removeTrim(trim);
					//forceLayout();
				}
			}
		});

		// Add the new descriptor to the map
		fTrimDescriptors.put(desc.getId(), desc);

		// insert before behaviour, revisited
		if (beforeMe != null) {
			TrimDescriptor beforeDesc = (TrimDescriptor) fTrimDescriptors
					.get(beforeMe.getId());
			if (beforeDesc != null && beforeDesc.getAreaId() == areaId) {
				area.addTrim(desc, beforeDesc);
			} else {
				area.addTrim(desc);
			}
		} else {
			area.addTrim(desc);
		}
	}

	/**
	 * Force a layout of the trim
	 */
	public void forceLayout() {
		removeDisposed();

		// we hack this by calling the LayoutUtil with the
		// first piece of trim that we find...(kludge!!)
		Iterator d = fTrimDescriptors.values().iterator();
		while (d.hasNext()) {
			TrimDescriptor desc = (TrimDescriptor) d.next();
			if (desc.getTrim().getControl() != null) {
				LayoutUtil.resize(desc.getTrim().getControl());
				return;
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.internal.layout.ITrimManager#removeTrim(org.eclipse.ui.internal.IWindowTrim)
	 */
	public void removeTrim(IWindowTrim toRemove) {
		TrimDescriptor desc = (TrimDescriptor) fTrimDescriptors.remove(toRemove
				.getId());
		if (desc == null) {
			return;
		}

		TrimArea area = (TrimArea) fTrimArea.get(new Integer(desc.getAreaId()));
		if (area != null) {
			area.removeTrim(desc);
			desc.getCache().getControl().setLayoutData(null);
		}

		// If we had a trim UI handle then dispose it
		if (desc.getDockingCache() != null) {
			Control ctrl = desc.getDockingCache().getControl();
			
			// KLUDGE!! we'll leak a handle rather than losing the
			// mouse capture (for now...)
			ctrl.setVisible(false);
			//ctrl.dispose();
			desc.setDockingCache(null);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.internal.layout.ITrimManager#getTrim(java.lang.String)
	 */
	public IWindowTrim getTrim(String id) {
		TrimDescriptor desc = (TrimDescriptor) fTrimDescriptors.get(id);
		if (desc != null) {
			return desc.getTrim();
		}
		return null;
	}

	/**
	 * Removes any disposed widgets from this layout. This is still experimental
	 * code.
	 */
	private void removeDisposed() {
		Iterator a = fTrimArea.values().iterator();
		while (a.hasNext()) {
			TrimArea area = (TrimArea) a.next();
			Iterator d = area.getDescriptors().iterator();
			while (d.hasNext()) {
				TrimDescriptor desc = (TrimDescriptor) d.next();
				Control nextControl = desc.getTrim().getControl();
				if (nextControl == null || nextControl.isDisposed()) {
					// Remvoe the trim from the area's list (not the local copy)
					area.removeTrim(desc);
					
					// Remove it from the map
					fTrimDescriptors.remove(desc.getId());
				}
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.swt.widgets.Layout#computeSize(org.eclipse.swt.widgets.Composite,
	 *      int, int, boolean)
	 */
	protected Point computeSize(Composite composite, int wHint, int hHint,
			boolean flushCache) {
		Point result = new Point(wHint, hHint);

		TrimArea top = (TrimArea) fTrimArea.get(TOP_ID);
		TrimArea bottom = (TrimArea) fTrimArea.get(BOTTOM_ID);
		TrimArea left = (TrimArea) fTrimArea.get(LEFT_ID);
		TrimArea right = (TrimArea) fTrimArea.get(RIGHT_ID);

		int horizontalTrim = left.calculateTrimSize(wHint, hHint)
				+ right.calculateTrimSize(wHint, hHint) + (2 * marginWidth)
				+ leftSpacing + rightSpacing;
		int verticalTrim = top.calculateTrimSize(wHint, hHint)
				+ bottom.calculateTrimSize(wHint, hHint) + (2 * marginHeight)
				+ topSpacing + bottomSpacing;

		Point innerSize = centerArea.computeSize(wHint == SWT.DEFAULT ? wHint
				: wHint - horizontalTrim, hHint == SWT.DEFAULT ? hHint : hHint
				- verticalTrim);

		if (wHint == SWT.DEFAULT) {
			result.x = innerSize.x + horizontalTrim;
		} else if (hHint == SWT.DEFAULT) {
			result.y = innerSize.y + verticalTrim;
		}

		/*
		 * We -can't- determine the correct size for the shell because
		 * of it's underlying 'big lie' structure and the fact that most of the
		 * UI elements don't really exist until shown (meaning that
		 * the normal 'computeSize' mechanism won't work).
		 *
		 * See bug 166619 for details but we'll keep returning the
		 * current value (and the code above for legacy reasons...
		 */
		return new Point(0, 0);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.swt.widgets.Layout#layout(org.eclipse.swt.widgets.Composite,
	 *      boolean)
	 */
	protected void layout(Composite composite, boolean flushCache) {
		removeDisposed();

		TrimArea top = (TrimArea) fTrimArea.get(TOP_ID);
		TrimArea bottom = (TrimArea) fTrimArea.get(BOTTOM_ID);
		TrimArea left = (TrimArea) fTrimArea.get(LEFT_ID);
		TrimArea right = (TrimArea) fTrimArea.get(RIGHT_ID);

		Rectangle clientArea = composite.getClientArea();

		clientArea.x += marginWidth;
		clientArea.width -= 2 * marginWidth;
		clientArea.y += marginHeight;
		clientArea.height -= 2 * marginHeight;

		// Get the max of the trim's preferred size for each side
		int trim_top = top.calculateTrimSize(clientArea.width,
				clientArea.height);
		int trim_bottom = bottom.calculateTrimSize(clientArea.width,
				clientArea.height);
		int trim_left = left.calculateTrimSize(clientArea.width,
				clientArea.height);
		int trim_right = right.calculateTrimSize(clientArea.width,
				clientArea.height);

		// Determine the amount of 'useable' space for the trim
		int leftOfLayout = clientArea.x;
		int leftOfCenterPane = leftOfLayout + trim_left + leftSpacing;
		int widthOfCenterPane = clientArea.width - trim_left - trim_right
				- leftSpacing - rightSpacing;
		int rightOfCenterPane = clientArea.x + clientArea.width - trim_right;

		// HACK!! Surgical Fix: Ideally the fix would handle 'wrapping' trim
		// on any side. The following code is in place specifically to
		// handle the CBanner's CoolBar wrapping. The code that calculates 'trim_top'
		// above won't pick up if the CoolBar wraps and uses extra height
		// So we have to re-calc the value based on the actual trim height
		// as laid out.
		
		// Do the layout of the top and update the height to match the actual
		// height used
		int topOfLayout = clientArea.y;
		trim_top = arrange(new Rectangle(leftOfLayout, topOfLayout, clientArea.width,
				trim_top), top.getCaches(), !top.isVertical(), spacing);

		// Now that we have an accurate value for the top's height we can 
		// proceed with the rest of the layout 'normally'.
		int topOfCenterPane = topOfLayout + trim_top + topSpacing;
		int heightOfCenterPane = clientArea.height - trim_top - trim_bottom
				- topSpacing - bottomSpacing;
		int bottomOfCenterPane = clientArea.y + clientArea.height - trim_bottom;

		arrange(new Rectangle(leftOfCenterPane, bottomOfCenterPane,
				widthOfCenterPane, trim_bottom), bottom.getCaches(), !bottom
				.isVertical(), spacing);
		arrange(new Rectangle(leftOfLayout, topOfCenterPane, trim_left,
				clientArea.height - trim_top), left.getCaches(), !left
				.isVertical(), spacing);
		arrange(new Rectangle(rightOfCenterPane, topOfCenterPane, trim_right,
				clientArea.height - trim_top), right.getCaches(), !right
				.isVertical(), spacing);

		if (centerArea.getControl() != null) {
			centerArea.getControl().setBounds(leftOfCenterPane,
					topOfCenterPane, widthOfCenterPane, heightOfCenterPane);
		}
	}

	/**
	 * Arranges all the given controls in a horizontal row that fills the given
	 * rectangle.
	 * 
	 * @param area
	 *            area to be filled by the controls
	 * @param caches
	 *            a list of SizeCaches for controls that will span the rectangle
	 * @param horizontally
	 *            how we are filling the rectangle
	 * @param spacing
	 *            in pixels
	 */
	private static int arrange(Rectangle area, List caches,
			boolean horizontally, int spacing) {
		Point currentPosition = new Point(area.x, area.y);

		List resizable = new ArrayList(caches.size());
		List nonResizable = new ArrayList(caches.size());

		TrimArea.filterResizable(caches, resizable, nonResizable, horizontally);

		int[] sizes = new int[nonResizable.size()];

		int idx = 0;
		int used = 0;
		int hint = Geometry.getDimension(area, !horizontally);

		// Compute the sizes of non-resizable controls
		Iterator iter = nonResizable.iterator();

		while (iter.hasNext()) {
			SizeCache next = (SizeCache) iter.next();

			sizes[idx] = TrimArea.getSize(next, hint, horizontally);
			used += sizes[idx];
			idx++;
		}

		int available = Geometry.getDimension(area, horizontally) - used
				- spacing * (caches.size() - 1);
		idx = 0;
		int remainingResizable = resizable.size();

		iter = caches.iterator();

		while (iter.hasNext()) {
			SizeCache next = (SizeCache) iter.next();
			if (next.getControl().isVisible()) {

				int thisSize;
				if (TrimArea.isResizable(next.getControl(), horizontally)) {
					thisSize = available / remainingResizable;
					available -= thisSize;
					remainingResizable--;
				} else {
					thisSize = sizes[idx];
					idx++;
				}

				if (TrimDragPreferences.showRaggedTrim()) {
					Point prefSize = next.computeSize(SWT.DEFAULT, SWT.DEFAULT);
					if (horizontally) {
						// HACK!! Surgical Fix: Ideally the fix would handle 'wrapping' trim
						// on any side. The following code is in place specifically to
						// handle the CBanner's CoolBar wrapping. We need to  pick up
						// if the CoolBar wraps and uses extra height
						// So we have to re-calc the value based on the actual trim height
						// as laid out.
						if (next.getControl() instanceof CBanner) {
							prefSize = next.getControl().computeSize(thisSize, SWT.DEFAULT);					
							if (prefSize.y > hint)
								hint = prefSize.y;
						}
						
						next.getControl().setBounds(currentPosition.x,
								currentPosition.y, thisSize, prefSize.y);
						currentPosition.x += thisSize + spacing;
					} else {
						next.getControl().setBounds(currentPosition.x,
								currentPosition.y, prefSize.x, thisSize);
						currentPosition.y += thisSize + spacing;
					}
				}
				else {
					if (horizontally) {
						next.getControl().setBounds(currentPosition.x,
								currentPosition.y, thisSize, hint);
						currentPosition.x += thisSize + spacing;
					} else {
						next.getControl().setBounds(currentPosition.x,
								currentPosition.y, hint, thisSize);
						currentPosition.y += thisSize + spacing;
					}
				}
			}
		}
		
		return hint;
	}

	/**
	 * Sets the widget that will occupy the central area of the layout.
	 * Typically, this will be a composite that contains the main widgetry of
	 * the application.
	 * 
	 * @param center
	 *            control that will occupy the center of the layout, or null if
	 *            none
	 */
	public void setCenterControl(Control center) {
		centerArea.setControl(center);
	}

	/**
	 * Returns the control in the center of this layout
	 * 
	 * @return the center area control.
	 */
	public Control getCenterControl() {
		return centerArea.getControl();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.internal.layout.ICachingLayout#flush(org.eclipse.swt.widgets.Control)
	 */
	public void flush(Control dirtyControl) {
		if (dirtyControl == centerArea.getControl()) {
			centerArea.flush();
		} else {
			TrimDescriptor desc = findTrimDescription(dirtyControl);
			if (desc != null) {
				desc.flush();
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.internal.layout.ITrimManager#getAreaIds()
	 */
	public int[] getAreaIds() {
		return (int[]) TRIM_ID_INFO.clone();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.internal.layout.ITrimManager#getAreaTrim(int)
	 */
	public List getAreaTrim(int areaId) {
		TrimArea area = (TrimArea) fTrimArea.get(new Integer(areaId));
		if (area == null) {
			return Collections.EMPTY_LIST;
		}
		return area.getTrims();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.internal.layout.ITrimManager#updateAreaTrim(int,
	 *      java.util.List, boolean)
	 */
	public void updateAreaTrim(int id, List trim, boolean removeExtra) {
		TrimArea area = (TrimArea) fTrimArea.get(new Integer(id));
		if (area == null) {
			return;
		}
		List current = area.getTrims();

		// add back the trim ... this takes care of moving it
		// from one trim area to another.
		Iterator i = trim.iterator();
		while (i.hasNext()) {
			IWindowTrim t = (IWindowTrim) i.next();
			t.dock(id);  // Ensure that the trim is properly oriented
			addTrim(id, t, null);
			current.remove(t);
		}

		if (removeExtra) {
			// if it wasn't removed from the current list, then it's extra
			// trim we don't need.
			i = current.iterator();
			while (i.hasNext()) {
				IWindowTrim t = (IWindowTrim) i.next();
				removeTrim(t);
			}
		}
	}

	/**
	 * Return a trim area rectangle.
	 * 
	 * @param window
	 *            the window that has the trim
	 * @param areaId
	 *            the side it's on
	 * @return the area rectangle.
	 * @since 3.2
	 * @see #getAreaIds()
	 */
	public Rectangle getTrimRect(Composite window, int areaId) {
		Rectangle bb = window.getBounds();

		Rectangle cr = window.getClientArea();
		Rectangle tr = window.computeTrim(cr.x, cr.y, cr.width, cr.height);

		// Place the Client Area 'within' its window
		Geometry.moveRectangle(cr, new Point(bb.x - tr.x, bb.y - tr.y));

		TrimArea top = (TrimArea) fTrimArea.get(TOP_ID);
		TrimArea bottom = (TrimArea) fTrimArea.get(BOTTOM_ID);
		TrimArea left = (TrimArea) fTrimArea.get(LEFT_ID);
		TrimArea right = (TrimArea) fTrimArea.get(RIGHT_ID);

		int trim_top = top.calculateTrimSize(cr.width, cr.height);
		int trim_bottom = bottom.calculateTrimSize(cr.width, cr.height);
		int trim_left = left.calculateTrimSize(cr.width, cr.height);
		int trim_right = right.calculateTrimSize(cr.width, cr.height);

		// Adjust the trim sizes to incorporate the margins for sides that
		// don't currently have trim
		if (trim_top == 0) {
			trim_top = marginHeight;
		}
		if (trim_bottom == 0) {
			trim_bottom = marginHeight;
		}
		if (trim_left == 0) {
			trim_left = marginWidth;
		}
		if (trim_right == 0) {
			trim_right = marginWidth;
		}

		Rectangle trimRect = new Rectangle(0, 0, 0, 0);
		switch (areaId) {
		case TOP:
			trimRect.x = cr.x;
			trimRect.width = cr.width;
			trimRect.y = cr.y;
			trimRect.height = trim_top;
			break;
		case BOTTOM:
			trimRect.x = cr.x;
			trimRect.width = cr.width;
			trimRect.y = (cr.y + cr.height) - trim_bottom;
			trimRect.height = trim_bottom;
			break;
		case LEFT:
			trimRect.x = cr.x;
			trimRect.width = trim_left;
			trimRect.y = cr.y + trim_top;
			trimRect.height = cr.height - (trim_top + trim_bottom);
			break;
		case RIGHT:
			trimRect.x = (cr.x + cr.width) - trim_right;
			trimRect.width = trim_right;
			trimRect.y = cr.y + trim_top;
			trimRect.height = cr.height - (trim_top + trim_bottom);
			break;
		}

		return trimRect;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.internal.layout.ITrimManager#getAllTrim()
	 */
	public List getAllTrim() {
		List trimList = new ArrayList(fTrimDescriptors.size());

		Iterator d = fTrimDescriptors.values().iterator();
		while (d.hasNext()) {
			TrimDescriptor desc = (TrimDescriptor) d.next();
			trimList.add(desc.getTrim());
		}

		return trimList;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.internal.layout.ITrimManager#setTrimVisible(org.eclipse.ui.internal.IWindowTrim,
	 *      boolean)
	 */
	public void setTrimVisible(IWindowTrim trim, boolean visible) {
		TrimDescriptor desc = findTrimDescription(trim.getControl());

		if (desc != null) {
			desc.setVisible(visible);
		}
	}

	/**
	 * Find the trim descriptor for this control.
	 * 
	 * @param trim
	 *            the Control to find.
	 * @return the trim descriptor, or <code>null</code> if not found.
	 * @since 3.2
	 */
	private TrimDescriptor findTrimDescription(Control trim) {
		Iterator d = fTrimDescriptors.values().iterator();
		while (d.hasNext()) {
			TrimDescriptor desc = (TrimDescriptor) d.next();
			if (desc.getTrim().getControl() == trim) {
				return desc;
			}
			if (desc.getDockingCache() != null
					&& desc.getDockingCache().getControl() == trim) {
				return desc;
			}
		}
		return null;
	}

	/**
	 * Return the trim area associated with the given id
	 * 
	 * @param areaId
	 *            The id of the trim area to get
	 * @return The TrimArea or <code>null</code> if the id is not found
	 */
	public TrimArea getTrimArea(int areaId) {
		return (TrimArea) fTrimArea.get(new Integer(areaId));
	}

	/**
	 * Remember the persisted locations for the trim. This allows the code
	 * to site the trim in its preferred (i.e. cached) location on creation
	 * 
	 * @param areaId The id of the trim area being defined
	 * @param preferredLocations A list of trim ID's
	 */
	public void setPreferredLocations(int areaId, List preferredLocations) {
		preferredLocationMap.put(new Integer(areaId), preferredLocations);
	}
	
	/**
	 * If the given id has a cached location return its preferred side
	 * 
	 * @param trimId The id of the trim to be tested
	 * @return The areaId of a cached id or -1 if no cache info exists
	 */
	public int getPreferredArea(String trimId) {
		Iterator keyIter = preferredLocationMap.keySet().iterator();
		while (keyIter.hasNext()) {
			Integer key = (Integer) keyIter.next();
			List areaList = (List) preferredLocationMap.get(key);
			if (areaList.contains(trimId))
				return key.intValue();
		}
		
		return -1;
	}
	
	/**
	 * If the given id has a cached location return an existing trim
	 * element that it should be placed before (if any)
	 * 
	 * @param trimId The id of the trim to be tested
	 * @return The trim to be inserted before or <code>null</code>
	 * if no cached info exists
	 */
	public IWindowTrim getPreferredLocation(String trimId) {
		Iterator keyIter = preferredLocationMap.keySet().iterator();
		while (keyIter.hasNext()) {
			Integer key = (Integer) keyIter.next();
			List areaList = (List) preferredLocationMap.get(key);
			int index = areaList.indexOf(trimId);
			if (index != -1) {
				// OK, find the first 'real' trim after this one
				// This will be used as the 'beforeMe' parameter
				// in the 'addTrim' call
				for (int i = index+1; i < areaList.size(); i++) {
					String id = (String) areaList.get(i);
					IWindowTrim trim = getTrim(id);
					if (trim != null)
						return trim;
				}
			}
		}
		
		return null;
	}

	/**
	 * Disables the controls associated with visible trim elements. This
	 * is only used during long-running WorkbenchWindow operations to prevent users
	 * from changing the environment while the operation (i.e. a long-running editor
	 * 'save') is in progress.
	 * 
	 * The expected life-cycle is to first call this this method to disable any visible
	 * trim (and caching the elements that had to be disabled) followed by a call to
	 * 'enableTrim' passing in the list returned from this method.
	 * 
	 * @param ignoreMe Since the current UI has a disable button in the StatusLine
	 * we allow the caller to designate one piece of trim to ignore
	 * 
	 * @return The list of trim controls that were disabled during this call
	 */
	public List disableTrim(IWindowTrim ignoreMe) {
		List disabledControls = new ArrayList();

		// Disable all the trim -except- for 'ignoreMe'
		List allTrim = getAllTrim();
		for (Iterator trimIter = allTrim.iterator(); trimIter.hasNext();) {
			IWindowTrim trim = (IWindowTrim) trimIter.next();
			if (ignoreMe == trim)
				continue;
			
			Control ctrl = trim.getControl();
			if (ctrl == null || ctrl.isDisposed() || !ctrl.isVisible() || !ctrl.isEnabled())
				continue;
			
			ctrl.setEnabled(false);
			disabledControls.add(ctrl);
		}
		
		return disabledControls;
	}
	
	/**
	 * Enables the controls in the list. This list is expected to be a non-modified
	 * List as returned from a call to 'disableTrim'.
	 * @param disabledControls The list of controls to enable
	 */
	public void enableTrim(List disabledControls) {
		// Simply re-enable any controls in the list
		for (Iterator dcIter = disabledControls.iterator(); dcIter.hasNext();) {
			Control ctrl = (Control) dcIter.next();
			
			if (!ctrl.isDisposed() && !ctrl.isEnabled())
				ctrl.setEnabled(true);
		}
	}
}
