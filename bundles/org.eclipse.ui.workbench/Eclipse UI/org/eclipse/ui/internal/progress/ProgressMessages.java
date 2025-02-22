/*******************************************************************************
 * Copyright (c) 2005, 2015 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * IBM - Initial API and implementation
 *******************************************************************************/
package org.eclipse.ui.internal.progress;

import org.eclipse.osgi.util.NLS;

public class ProgressMessages extends NLS {
	private static final String BUNDLE_NAME = "org.eclipse.ui.internal.progress.messages";//$NON-NLS-1$

	public static String PendingUpdateAdapter_PendingLabel;
	public static String JobInfo_DoneMessage;
	public static String JobInfo_DoneNoProgressMessage;
	public static String JobInfo_NoTaskNameDoneMessage;
	public static String JobsViewPreferenceDialog_Note;
	public static String JobInfo_UnknownProgress;
	public static String JobInfo_Waiting;
	public static String JobInfo_Sleeping;
	public static String JobInfo_System;
	public static String JobInfo_Cancelled;
	public static String JobInfo_Cancel_Requested;
	public static String JobInfo_Error;
	public static String JobInfo_Blocked;
	public static String JobInfo_Finished;
	public static String JobInfo_FinishedAt;
	public static String JobErrorDialog_CloseDialogMessage;
	public static String InternalError;
	public static String DeferredTreeContentManager_NotDeferred;
	public static String DeferredTreeContentManager_AddingChildren;
	public static String DeferredTreeContentManager_FetchingName;
	public static String ProgressView_CancelAction;
	public static String ProgressView_ClearAllAction;
	public static String ProgressView_NoOperations;

	public static String NewProgressView_RemoveAllJobsToolTip;
	public static String NewProgressView_CancelJobToolTip;
	public static String NewProgressView_ClearJobToolTip;
	public static String ProgressAnimationItem_tasks;
	public static String ProgressAnimationItem_ok;
	public static String ProgressAnimationItem_error;
	public static String SubTaskInfo_UndefinedTaskName;
	public static String DeferredTreeContentManager_ClearJob;
	public static String ProgressContentProvider_UpdateProgressJob;
	public static String ProgressManager_openJobName;
	public static String ProgressManager_showInDialogName;
	public static String ProgressManager_listenersRefreshRejected;
	public static String ProgressManager_rejectedRefreshException;
	public static String ProgressMonitorJobsDialog_DetailsTitle;
	public static String ProgressMonitorJobsDialog_HideTitle;
	public static String ProgressToolItem_Update;
	public static String AnimationManager_AnimationStart;
	public static String ProgressFloatingWindow_EllipsisValue;
	public static String BlockedJobsDialog_BlockedTitle;
	public static String BlockedJobsDialog_CancelButtonText;
	public static String WorkbenchSiteProgressService_CursorJob;
	public static String ProgressMonitorFocusJobDialog_UserDialogJob;
	public static String ProgressMonitorFocusJobDialog_CLoseDialogJob;
	public static String ProgressMonitorFocusJobDialog_RunInBackgroundButton;

	public static String JobErrorDialog_CloseDialogTitle;
	public static String JobsViewPreferenceDialog_Title;
	public static String JobErrorDialog_DoNotShowAgainMessage;

	static {
		// load message values from bundle file
		NLS.initializeMessages(BUNDLE_NAME, ProgressMessages.class);
	}
}
