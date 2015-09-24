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
package org.eclipse.che.ide.ext.java.client.refactoring.move.wizard;

import com.google.gwtmockito.GwtMockitoTestRunner;

import org.eclipse.che.api.project.shared.dto.ProjectDescriptor;
import org.eclipse.che.api.promises.client.Function;
import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.app.CurrentProject;
import org.eclipse.che.ide.dto.DtoFactory;
import org.eclipse.che.ide.ext.java.client.navigation.JavaNavigationService;
import org.eclipse.che.ide.ext.java.client.projecttree.nodes.PackageNode;
import org.eclipse.che.ide.ext.java.client.projecttree.nodes.SourceFileNode;
import org.eclipse.che.ide.ext.java.client.refactoring.RefactorInfo;
import org.eclipse.che.ide.ext.java.client.refactoring.RefactoringUpdater;
import org.eclipse.che.ide.ext.java.client.refactoring.preview.PreviewPresenter;
import org.eclipse.che.ide.ext.java.client.refactoring.service.RefactoringServiceClient;
import org.eclipse.che.ide.ext.java.shared.dto.refactoring.ChangeCreationResult;
import org.eclipse.che.ide.ext.java.shared.dto.refactoring.CreateMoveRefactoring;
import org.eclipse.che.ide.ext.java.shared.dto.refactoring.JavaElement;
import org.eclipse.che.ide.ext.java.shared.dto.refactoring.JavaProject;
import org.eclipse.che.ide.ext.java.shared.dto.refactoring.MoveSettings;
import org.eclipse.che.ide.ext.java.shared.dto.refactoring.RefactoringSession;
import org.eclipse.che.ide.ext.java.shared.dto.refactoring.RefactoringStatus;
import org.eclipse.che.ide.ext.java.shared.dto.refactoring.ReorgDestination;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.eclipse.che.ide.ext.java.shared.dto.refactoring.RefactoringStatus.ERROR;
import static org.eclipse.che.ide.ext.java.shared.dto.refactoring.RefactoringStatus.FATAL;
import static org.eclipse.che.ide.ext.java.shared.dto.refactoring.RefactoringStatus.INFO;
import static org.eclipse.che.ide.ext.java.shared.dto.refactoring.RefactoringStatus.OK;
import static org.eclipse.che.ide.ext.java.shared.dto.refactoring.RefactoringStatus.WARNING;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Valeriy Svydenko
 */
@RunWith(GwtMockitoTestRunner.class)
public class MovePresenterTest {
    public static final String DESTINATION  = "destaination";
    public static final String PROJECT_PATH = "projectPAth";

    // constructor params
    @Mock
    private MoveView                 moveView;
    @Mock
    private RefactoringUpdater       refactoringUpdater;
    @Mock
    private AppContext               appContext;
    @Mock
    private PreviewPresenter         previewPresenter;
    @Mock
    private RefactoringServiceClient refactorService;
    @Mock
    private RefactoringSession       session;
    @Mock
    private JavaNavigationService    navigationService;
    @Mock
    private DtoFactory               dtoFactory;

    //local params
    @Mock
    private RefactorInfo               refactorInfo;
    @Mock
    private RefactoringStatus          refactoringStatus;
    @Mock
    private JavaElement                javaElement;
    @Mock
    private CreateMoveRefactoring      moveRefactoring;
    @Mock
    private CurrentProject             currentProject;
    @Mock
    private PackageNode                packageNode;
    @Mock
    private ChangeCreationResult       changeCreationResult;
    @Mock
    private MoveSettings               moveSettings;
    @Mock
    private ReorgDestination           destination;
    @Mock
    private SourceFileNode             sourceFileNode;
    @Mock
    private ProjectDescriptor          projectDescriptor;
    @Mock
    private Promise<String>            sessionPromise;
    @Mock
    private Promise<Void>              moveSettingsPromise;
    @Mock
    private Promise<Object>            changeCreationResultPromise;
    @Mock
    private Promise<List<JavaProject>> projectsPromise;
    @Mock
    private Promise<RefactoringStatus> refactoringStatusPromise;

    @Captor
    private ArgumentCaptor<Operation<String>>                             sessionOperation;
    @Captor
    private ArgumentCaptor<Operation<List<JavaProject>>>                  projectsOperation;
    @Captor
    private ArgumentCaptor<Operation<Object>>                             changeResultOperation;
    @Captor
    private ArgumentCaptor<Function<Void, Promise<ChangeCreationResult>>> changeCreationFunction;
    @Captor
    private ArgumentCaptor<Operation<RefactoringStatus>>                  refactoringStatusOperation;

    private MovePresenter presenter;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() throws Exception {
        List selectedItems = new ArrayList<>();
        selectedItems.add(packageNode);
        selectedItems.add(sourceFileNode);

        when(refactorInfo.getSelectedItems()).thenReturn(selectedItems);
        when(dtoFactory.createDto(JavaElement.class)).thenReturn(javaElement);
        when(dtoFactory.createDto(CreateMoveRefactoring.class)).thenReturn(moveRefactoring);
        when(dtoFactory.createDto(ReorgDestination.class)).thenReturn(destination);
        when(appContext.getCurrentProject()).thenReturn(currentProject);
        when(currentProject.getProjectDescription()).thenReturn(projectDescriptor);
        when(projectDescriptor.getPath()).thenReturn(PROJECT_PATH);
        when(refactorService.createMoveRefactoring(moveRefactoring)).thenReturn(sessionPromise);
        when(refactorService.applyRefactoring(session)).thenReturn(refactoringStatusPromise);
        when(refactorService.setDestination(destination)).thenReturn(refactoringStatusPromise);
        when(navigationService.getProjectsAndPackages(true)).thenReturn(projectsPromise);
        when(dtoFactory.createDto(MoveSettings.class)).thenReturn(moveSettings);
        when(dtoFactory.createDto(RefactoringSession.class)).thenReturn(session);
        when(changeCreationResult.getStatus()).thenReturn(refactoringStatus);

        presenter = new MovePresenter(moveView,
                                      refactoringUpdater,
                                      appContext,
                                      previewPresenter,
                                      refactorService,
                                      navigationService,
                                      dtoFactory);
    }

    @Test
    public void constructorShouldBePerformed() throws Exception {
        verify(moveView).setDelegate(presenter);
    }

    @Test
    public void moveWizardShouldBeShowed() throws Exception {
        List<JavaProject> javaProjects = new ArrayList<>();
        JavaProject javaProject = Mockito.mock(JavaProject.class);
        javaProjects.add(javaProject);

        when(sourceFileNode.getName()).thenReturn("file.name");
        when(javaProject.getPath()).thenReturn(PROJECT_PATH);

        presenter.show(refactorInfo);

        verify(moveView).setEnablePreviewButton(false);
        verify(moveView).setEnableAcceptButton(false);
        verify(moveView).clearErrorLabel();
        verify(moveRefactoring).setProjectPath(PROJECT_PATH);
        verify(refactorService).createMoveRefactoring(moveRefactoring);
        verify(packageNode).getPath();

        verify(sessionPromise).then(sessionOperation.capture());
        sessionOperation.getValue().apply("sessionId");

        verify(projectsPromise).then(projectsOperation.capture());
        projectsOperation.getValue().apply(javaProjects);

        verify(moveView).setTreeOfDestinations(any());
        verify(moveView).show(refactorInfo);
    }

    @Test
    public void showPreviewWindowWhenPreviewButtonClicked() throws Exception {
        when(moveSettings.isUpdateQualifiedNames()).thenReturn(true);
        when(refactorService.setMoveSettings(moveSettings)).thenReturn(moveSettingsPromise);
        when(moveSettingsPromise.thenPromise(any())).thenReturn(changeCreationResultPromise);
        when(changeCreationResult.isCanShowPreviewPage()).thenReturn(true);

        presenter.onPreviewButtonClicked();

        verify(moveSettings).setSessionId(anyString());
        verify(moveSettings).setUpdateReferences(anyBoolean());
        verify(moveSettings).setUpdateQualifiedNames(anyBoolean());
        verify(moveSettings).setFilePatterns(anyString());
        verify(session).setSessionId(anyString());

        verify(moveSettingsPromise).thenPromise(changeCreationFunction.capture());
        changeCreationFunction.getValue().apply(any());
        verify(refactorService).createChange(session);

        verify(changeCreationResultPromise).then(changeResultOperation.capture());
        changeResultOperation.getValue().apply(changeCreationResult);
        verify(previewPresenter).show(anyString(), any());
        verify(moveView).hide();
    }

    @Test
    public void showErrorMessageIfCanNotShowPreviewWindow() throws Exception {
        when(moveSettings.isUpdateQualifiedNames()).thenReturn(true);
        when(refactorService.setMoveSettings(moveSettings)).thenReturn(moveSettingsPromise);
        when(moveSettingsPromise.thenPromise(any())).thenReturn(changeCreationResultPromise);
        when(changeCreationResult.isCanShowPreviewPage()).thenReturn(false);

        presenter.onPreviewButtonClicked();

        verify(moveSettings).setSessionId(anyString());
        verify(moveSettings).setUpdateReferences(anyBoolean());
        verify(moveSettings).setUpdateQualifiedNames(anyBoolean());
        verify(moveSettings).setFilePatterns(anyString());
        verify(session).setSessionId(anyString());

        verify(moveSettingsPromise).thenPromise(changeCreationFunction.capture());
        changeCreationFunction.getValue().apply(any());
        verify(refactorService).createChange(session);

        verify(changeCreationResultPromise).then(changeResultOperation.capture());
        changeResultOperation.getValue().apply(changeCreationResult);
        verify(previewPresenter, never()).show(anyString(), any());
        verify(moveView, never()).hide();
        verify(moveView).showStatusMessage(refactoringStatus);
    }

    @Test
    public void acceptButtonActionShouldShowAnErrorMessage() throws Exception {
        when(moveSettings.isUpdateQualifiedNames()).thenReturn(true);
        when(refactorService.setMoveSettings(moveSettings)).thenReturn(moveSettingsPromise);
        when(moveSettingsPromise.thenPromise(any())).thenReturn(changeCreationResultPromise);
        when(changeCreationResult.isCanShowPreviewPage()).thenReturn(false);
        when(changeCreationResult.getStatus()).thenReturn(refactoringStatus);

        presenter.onAcceptButtonClicked();

        verify(moveSettings).setSessionId(anyString());
        verify(moveSettings).setUpdateReferences(anyBoolean());
        verify(moveSettings).setUpdateQualifiedNames(anyBoolean());
        verify(moveSettings).setFilePatterns(anyString());
        verify(session).setSessionId(anyString());

        verify(moveSettingsPromise).thenPromise(changeCreationFunction.capture());
        changeCreationFunction.getValue().apply(any());
        verify(refactorService).createChange(session);

        verify(changeCreationResultPromise).then(changeResultOperation.capture());
        changeResultOperation.getValue().apply(changeCreationResult);
        verify(moveView).showErrorMessage(refactoringStatus);
    }

    @Test
    public void acceptButtonActionShouldBePerformed() throws Exception {
        when(moveSettings.isUpdateQualifiedNames()).thenReturn(true);
        when(refactorService.setMoveSettings(moveSettings)).thenReturn(moveSettingsPromise);
        when(moveSettingsPromise.thenPromise(any())).thenReturn(changeCreationResultPromise);
        when(changeCreationResult.isCanShowPreviewPage()).thenReturn(true);
        when(changeCreationResult.getStatus()).thenReturn(refactoringStatus);
        when(refactoringStatus.getSeverity()).thenReturn(OK);

        presenter.onAcceptButtonClicked();

        verify(moveSettings).setSessionId(anyString());
        verify(moveSettings).setUpdateReferences(anyBoolean());
        verify(moveSettings).setUpdateQualifiedNames(anyBoolean());
        verify(moveSettings).setFilePatterns(anyString());
        verify(session).setSessionId(anyString());

        verify(moveSettingsPromise).thenPromise(changeCreationFunction.capture());
        changeCreationFunction.getValue().apply(any());
        verify(refactorService).createChange(session);

        verify(changeCreationResultPromise).then(changeResultOperation.capture());
        changeResultOperation.getValue().apply(changeCreationResult);


        verify(refactoringStatusPromise).then(refactoringStatusOperation.capture());
        refactoringStatusOperation.getValue().apply(refactoringStatus);
        verify(moveView).hide();
        verify(refactoringUpdater).refreshProjectTree();
        verify(refactoringUpdater).refreshOpenEditors();

        verify(moveView, never()).showErrorMessage(refactoringStatus);
    }

    @Test
    public void errorLabelShouldBeShowedIfRefactoringStatusIsNotOK() throws Exception {
        when(moveSettings.isUpdateQualifiedNames()).thenReturn(true);
        when(refactorService.setMoveSettings(moveSettings)).thenReturn(moveSettingsPromise);
        when(moveSettingsPromise.thenPromise(any())).thenReturn(changeCreationResultPromise);
        when(changeCreationResult.isCanShowPreviewPage()).thenReturn(true);
        when(changeCreationResult.getStatus()).thenReturn(refactoringStatus);
        when(refactoringStatus.getSeverity()).thenReturn(2);

        presenter.onAcceptButtonClicked();

        verify(moveSettings).setSessionId(anyString());
        verify(moveSettings).setUpdateReferences(anyBoolean());
        verify(moveSettings).setUpdateQualifiedNames(anyBoolean());
        verify(moveSettings).setFilePatterns(anyString());
        verify(session).setSessionId(anyString());

        verify(moveSettingsPromise).thenPromise(changeCreationFunction.capture());
        changeCreationFunction.getValue().apply(any());
        verify(refactorService).createChange(session);

        verify(changeCreationResultPromise).then(changeResultOperation.capture());
        changeResultOperation.getValue().apply(changeCreationResult);


        verify(refactoringStatusPromise).then(refactoringStatusOperation.capture());
        refactoringStatusOperation.getValue().apply(refactoringStatus);
        verify(moveView, never()).hide();
        verify(refactoringUpdater, never()).refreshProjectTree();
        verify(refactoringUpdater, never()).refreshOpenEditors();

        verify(moveView).showErrorMessage(refactoringStatus);
    }

    @Test
    public void moveDestinationPathChangedWithOKStatus() throws Exception {
        when(refactoringStatus.getSeverity()).thenReturn(OK);

        presenter.setMoveDestinationPath(DESTINATION, PROJECT_PATH);

        verify(refactoringUpdater).setPathToMove(DESTINATION);
        verify(destination).setType(ReorgDestination.DestinationType.PACKAGE);
        verify(destination).setSessionId(anyString());
        verify(destination).setProjectPath(PROJECT_PATH);
        verify(destination).setDestination(DESTINATION);

        verify(refactoringStatusPromise).then(refactoringStatusOperation.capture());
        refactoringStatusOperation.getValue().apply(refactoringStatus);
        verify(moveView).setEnableAcceptButton(true);
        verify(moveView).setEnablePreviewButton(true);
        verify(moveView).clearStatusMessage();
    }

    @Test
    public void moveDestinationPathChangedWithINFOStatus() throws Exception {
        when(refactoringStatus.getSeverity()).thenReturn(INFO);

        presenter.setMoveDestinationPath(DESTINATION, PROJECT_PATH);

        verify(refactoringUpdater).setPathToMove(DESTINATION);
        verify(destination).setType(ReorgDestination.DestinationType.PACKAGE);
        verify(destination).setSessionId(anyString());
        verify(destination).setProjectPath(PROJECT_PATH);
        verify(destination).setDestination(DESTINATION);

        verify(refactoringStatusPromise).then(refactoringStatusOperation.capture());
        refactoringStatusOperation.getValue().apply(refactoringStatus);
        verify(moveView).setEnableAcceptButton(true);
        verify(moveView).setEnablePreviewButton(true);
        verify(moveView).showStatusMessage(refactoringStatus);
    }

    @Test
    public void moveDestinationPathChangedWithWARNINGStatus() throws Exception {
        when(refactoringStatus.getSeverity()).thenReturn(WARNING);

        presenter.setMoveDestinationPath(DESTINATION, PROJECT_PATH);

        verify(refactoringUpdater).setPathToMove(DESTINATION);
        verify(destination).setType(ReorgDestination.DestinationType.PACKAGE);
        verify(destination).setSessionId(anyString());
        verify(destination).setProjectPath(PROJECT_PATH);
        verify(destination).setDestination(DESTINATION);

        verify(refactoringStatusPromise).then(refactoringStatusOperation.capture());
        refactoringStatusOperation.getValue().apply(refactoringStatus);
        verify(moveView).setEnableAcceptButton(true);
        verify(moveView).setEnablePreviewButton(true);
        verify(moveView).showStatusMessage(refactoringStatus);
    }

    @Test
    public void moveDestinationPathChangedWithERRORStatus() throws Exception {
        when(refactoringStatus.getSeverity()).thenReturn(ERROR);

        presenter.setMoveDestinationPath(DESTINATION, PROJECT_PATH);

        verify(refactoringUpdater).setPathToMove(DESTINATION);
        verify(destination).setType(ReorgDestination.DestinationType.PACKAGE);
        verify(destination).setSessionId(anyString());
        verify(destination).setProjectPath(PROJECT_PATH);
        verify(destination).setDestination(DESTINATION);

        verify(refactoringStatusPromise).then(refactoringStatusOperation.capture());
        refactoringStatusOperation.getValue().apply(refactoringStatus);
        verify(moveView).setEnableAcceptButton(true);
        verify(moveView).setEnablePreviewButton(true);

        verify(moveView).showErrorMessage(refactoringStatus);
        verify(moveView).setEnableAcceptButton(false);
        verify(moveView).setEnablePreviewButton(false);
    }

    @Test
    public void moveDestinationPathChangedWithFATALStatus() throws Exception {
        when(refactoringStatus.getSeverity()).thenReturn(FATAL);

        presenter.setMoveDestinationPath(DESTINATION, PROJECT_PATH);

        verify(refactoringUpdater).setPathToMove(DESTINATION);
        verify(destination).setType(ReorgDestination.DestinationType.PACKAGE);
        verify(destination).setSessionId(anyString());
        verify(destination).setProjectPath(PROJECT_PATH);
        verify(destination).setDestination(DESTINATION);

        verify(refactoringStatusPromise).then(refactoringStatusOperation.capture());
        refactoringStatusOperation.getValue().apply(refactoringStatus);
        verify(moveView).setEnableAcceptButton(true);
        verify(moveView).setEnablePreviewButton(true);

        verify(moveView).showErrorMessage(refactoringStatus);
        verify(moveView).setEnableAcceptButton(false);
        verify(moveView).setEnablePreviewButton(false);
    }
}