/*******************************************************************************
 * Copyright (c) 2012-2015 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.ide.ext.java.client.editor;

import com.google.gwt.dom.client.Style;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Frame;
import com.google.gwt.user.client.ui.Widget;

import org.eclipse.che.ide.api.icon.Icon;
import org.eclipse.che.ide.api.notification.NotificationManager;
import org.eclipse.che.ide.api.text.Position;
import org.eclipse.che.ide.ext.java.shared.dto.Change;
import org.eclipse.che.ide.ext.java.shared.dto.LinkedData;
import org.eclipse.che.ide.ext.java.shared.dto.LinkedModeModel;
import org.eclipse.che.ide.ext.java.shared.dto.LinkedPositionGroup;
import org.eclipse.che.ide.ext.java.shared.dto.ProposalApplyResult;
import org.eclipse.che.ide.ext.java.shared.dto.Region;
import org.eclipse.che.ide.jseditor.client.codeassist.Completion;
import org.eclipse.che.ide.jseditor.client.codeassist.CompletionProposal;
import org.eclipse.che.ide.jseditor.client.codeassist.CompletionProposalExtension;
import org.eclipse.che.ide.jseditor.client.document.EmbeddedDocument;
import org.eclipse.che.ide.jseditor.client.link.HasLinkedMode;
import org.eclipse.che.ide.jseditor.client.link.LinkedMode;
import org.eclipse.che.ide.jseditor.client.link.LinkedModel;
import org.eclipse.che.ide.jseditor.client.link.LinkedModelData;
import org.eclipse.che.ide.jseditor.client.link.LinkedModelGroup;
import org.eclipse.che.ide.jseditor.client.text.LinearRange;
import org.eclipse.che.ide.util.loging.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Evgen Vidolob
 */
public class JavaCompletionProposal implements CompletionProposal, CompletionProposalExtension {

    private final int                  id;
    private final String               display;
    private final Icon                 icon;
    private final JavaCodeAssistClient client;
    private       String               sessionId;
    private HasLinkedMode linkedEditor;
    private NotificationManager notificationManager;

    public JavaCompletionProposal(final int id, final String display, final Icon icon,
                                  final JavaCodeAssistClient client, String sessionId, HasLinkedMode linkedEditor,
                                  NotificationManager notificationManager) {
        this.id = id;
        this.display = display;
        this.icon = icon;
        this.client = client;
        this.sessionId = sessionId;
        this.linkedEditor = linkedEditor;
        this.notificationManager = notificationManager;
    }

    /** {@inheritDoc} */
    @Override
    public Widget getAdditionalProposalInfo() {
        Frame frame = new Frame();
        frame.setSize("100%", "100%");
        frame.getElement().getStyle().setBorderStyle(Style.BorderStyle.NONE);
        frame.getElement().setAttribute("sandbox", ""); // empty value, not null
        frame.getElement().getStyle().setProperty("resize", "both");
        frame.setUrl(client.getProposalDocUrl(id, sessionId));
        return frame;
    }

    /** {@inheritDoc} */
    @Override
    public String getDisplayString() {
        return display;
    }

    /** {@inheritDoc} */
    @Override
    public Icon getIcon() {
        return icon;
    }

    @Override
    public void getCompletion(final CompletionCallback callback) {
        getCompletion(true, callback);
    }

    @Override
    public void getCompletion(boolean insert, final CompletionCallback callback) {
        client.applyProposal(sessionId, id, insert, new AsyncCallback<ProposalApplyResult>() {
            @Override
            public void onFailure(Throwable caught) {
                Log.error(JavaCompletionProposal.class, caught);
                notificationManager.showError(caught.getMessage());
            }

            @Override
            public void onSuccess(ProposalApplyResult result) {
                callback.onCompletion(new CompletionImpl(result.getChanges(), result.getSelection(), result.getLinkedModeModel()));
            }
        });
    }

    private class CompletionImpl implements Completion {

        private final List<Change>    changes;
        private final Region          region;
        private       LinkedModeModel linkedModeModel;
        private       int             cursorOffset;

        private CompletionImpl(final List<Change> changes, final Region region, final LinkedModeModel linkedModeModel) {
            this.changes = changes;
            this.region = region;
            this.linkedModeModel = linkedModeModel;
        }

        /** {@inheritDoc} */
        @Override
        public void apply(final EmbeddedDocument document) {
            cursorOffset = document.getCursorOffset();
            for (final Change change : changes) {
                document.replace(change.getOffset(), change.getLength(), change.getText());
            }
            if (linkedEditor != null && linkedModeModel != null) {
                LinkedMode mode = linkedEditor.getLinkedMode();
                LinkedModel model = linkedEditor.createLinkedModel();
                if(linkedModeModel.getEscapePosition() != 0) {
                    model.setEscapePosition(linkedModeModel.getEscapePosition());
                } else {
                    model.setEscapePosition(cursorOffset);
                }
                List<LinkedModelGroup> groups = new ArrayList<>();
                for (LinkedPositionGroup positionGroup : linkedModeModel.getGroups()) {
                    LinkedModelGroup group = linkedEditor.createLinkedGroup();
                    LinkedData data = positionGroup.getData();
                    if (data != null) {
                        LinkedModelData modelData = linkedEditor.createLinkedModelData();
                        modelData.setType("link");
                        modelData.setValues(data.getValues());
                        group.setData(modelData);
                    }
                    List<Position> positions = new ArrayList<>();
                    for (Region region : positionGroup.getPositions()) {
                        positions.add(new Position(region.getOffset(), region.getLength()));
                    }
                    group.setPositions(positions);
                    groups.add(group);
                }
                model.setGroups(groups);
                mode.enterLinkedMode(model);
            }
        }

        /** {@inheritDoc} */
        @Override
        public LinearRange getSelection(final EmbeddedDocument document) {
            if (region == null) {
                //keep cursor location
                return LinearRange.createWithStart(cursorOffset).andLength(0);
            } else {
                return LinearRange.createWithStart(region.getOffset()).andLength(region.getLength());
            }
        }
    }
}